package memoryforge.service

import memoryforge.domain.*
import memoryforge.llm.{OllamaClient, PromptTemplates}
import memoryforge.repository.StoryRepository
import zio.*
import zio.json.*

import java.time.Instant
import java.util.UUID

/** Orchestrates LLM generation: build prompt -> call Ollama -> parse -> store. */
final class StoryService(
    entries: EntryService,
    stories: StoryRepository,
    ollama: OllamaClient
):

  /** Generate a story/summary/analysis for an entry using the given mode. */
  def generate(
      entryId: UUID,
      mode: GenerationMode,
      modelOverride: Option[String] = None
  ): IO[AppError, GeneratedStory] =
    for
      entry  <- entries.get(entryId)
      prompt  = PromptTemplates.build(mode, entry)
      raw    <- ollama.generate(prompt, modelOverride)
      story   = StoryService.toStory(entryId, mode, raw)
      saved  <- stories.create(story)
      _      <- ZIO.logInfo(s"Generated '${mode.key}' story ${saved.id} for entry $entryId")
    yield saved

  def listAll: IO[AppError, List[GeneratedStory]]               = stories.getAll
  def listForEntry(entryId: UUID): IO[AppError, List[GeneratedStory]] = stories.getByEntry(entryId)

object StoryService:
  val live: ZLayer[EntryService & StoryRepository & OllamaClient, Nothing, StoryService] =
    ZLayer.fromFunction(new StoryService(_, _, _))

  /** Strip markdown code fences the model sometimes adds despite instructions. */
  private def stripFences(s: String): String =
    val t = s.trim
    if t.startsWith("```") then
      t.stripPrefix("```json").stripPrefix("```").stripSuffix("```").trim
    else t

  /** Turn the raw model output into a GeneratedStory.
    *
    * Tries to parse structured JSON first. If that fails, gracefully falls back
    * to storing the raw text in the `story` field so nothing is ever lost.
    */
  private[service] def toStory(entryId: UUID, mode: GenerationMode, raw: String): GeneratedStory =
    val now     = Instant.now()
    val cleaned = stripFences(raw)

    cleaned.fromJson[LLMStoryResponse] match
      case Right(p) if p.story.exists(_.trim.nonEmpty) || p.summary.exists(_.trim.nonEmpty) =>
        val body = p.story.filter(_.trim.nonEmpty).orElse(p.summary).getOrElse(raw)
        GeneratedStory(
          id = UUID.randomUUID(),
          entryId = entryId,
          title = p.title.filter(_.trim.nonEmpty).getOrElse(s"Untitled ${mode.defaultGenre}"),
          genre = p.genre.filter(_.trim.nonEmpty).getOrElse(mode.defaultGenre),
          style = p.style.filter(_.trim.nonEmpty).getOrElse(mode.defaultStyle),
          summary = p.summary.filter(_.trim.nonEmpty).getOrElse(summarize(body)),
          story = body,
          createdAt = now
        )
      case _ =>
        // Invalid or empty JSON -> keep the raw text rather than failing.
        GeneratedStory(
          id = UUID.randomUUID(),
          entryId = entryId,
          title = s"Untitled ${mode.defaultGenre} (raw)",
          genre = mode.defaultGenre,
          style = mode.defaultStyle,
          summary = summarize(raw),
          story = raw,
          createdAt = now
        )

  private def summarize(text: String): String =
    val flat = text.trim.replaceAll("\\s+", " ")
    if flat.length <= 160 then flat else flat.take(157) + "..."
