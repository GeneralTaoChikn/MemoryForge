package memoryforge.llm

import memoryforge.domain.{GenerationMode, JournalEntry}

/** Prompt templates for each generation mode.
  *
  * Every prompt asks the model to return a single JSON object with the fields:
  * {{{ { "title", "genre", "style", "summary", "story" } }}}
  * so the result can be parsed into `LLMStoryResponse`. Keep edits here — the
  * rest of the app does not need to change when you tweak wording.
  */
object PromptTemplates:

  private val jsonContract =
    """Return ONLY a single valid JSON object (no markdown, no code fences, no commentary)
      |with exactly these string fields:
      |{
      |  "title":   "a short catchy title",
      |  "genre":   "the genre",
      |  "style":   "the writing style",
      |  "summary": "1-2 sentence summary",
      |  "story":   "the full generated text"
      |}""".stripMargin

  /** Renders a journal entry as context for the model. */
  private def entryContext(entry: JournalEntry): String =
    val tags = if entry.tags.isEmpty then "none" else entry.tags.mkString(", ")
    val mood = entry.mood.getOrElse("unspecified")
    s"""Journal entry:
       |- Title: ${entry.title}
       |- Mood: $mood
       |- Tags: $tags
       |- Content:
       |${entry.content}""".stripMargin

  private def instructionFor(mode: GenerationMode): String =
    mode match
      case GenerationMode.ReflectiveSummary =>
        """You are a thoughtful journaling companion. Write a warm, reflective summary of the
          |entry below. Highlight feelings, lessons, and gentle encouragement. Put the reflection
          |in the "story" field and a one-line takeaway in "summary".""".stripMargin
      case GenerationMode.FunnyStory =>
        """You are a witty comedy writer. Turn the journal entry below into a funny, light-hearted
          |short story. Exaggerate for comedic effect while keeping the core events recognizable.""".stripMargin
      case GenerationMode.DramaticStory =>
        """You are a dramatic fiction author. Turn the journal entry below into an emotionally
          |charged, cinematic short story with tension, stakes, and a satisfying beat.""".stripMargin
      case GenerationMode.AnimeScene =>
        """You are an anime screenwriter. Rewrite the journal entry below as a vivid anime-style
          |scene with dramatic inner monologue, dynamic action lines, and stylized dialogue.""".stripMargin
      case GenerationMode.BrainRot =>
        """You are a chronically-online meme lord. Rewrite the journal entry below as an absurd
          |brain-rot meme version full of internet slang, ironic hyperbole, and chaotic energy.
          |Keep it short and unhinged but still loosely about the original events.""".stripMargin
      case GenerationMode.ThemeAnalysis =>
        """You are an insightful analyst. Identify the recurring themes, emotional patterns, and
          |possible character arc suggested by the journal entry below. Put a list of themes and
          |analysis in the "story" field and the single most dominant theme in "summary".""".stripMargin

  /** Builds the full prompt for a given mode + entry. */
  def build(mode: GenerationMode, entry: JournalEntry): String =
    s"""${instructionFor(mode)}
       |
       |${entryContext(entry)}
       |
       |$jsonContract""".stripMargin
