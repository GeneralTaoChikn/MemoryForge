package memoryforge.domain

import zio.json.*

import java.time.Instant
import java.util.UUID

/** A journal entry written by the user. */
final case class JournalEntry(
    id: UUID,
    title: String,
    content: String,
    mood: Option[String],
    tags: List[String],
    createdAt: Instant,
    updatedAt: Instant
)

object JournalEntry:
  given JsonCodec[JournalEntry] = DeriveJsonCodec.gen[JournalEntry]

/** Request body for creating a new entry. */
final case class CreateEntryRequest(
    title: String,
    content: String,
    mood: Option[String] = None,
    tags: Option[List[String]] = None
)

object CreateEntryRequest:
  given JsonCodec[CreateEntryRequest] = DeriveJsonCodec.gen[CreateEntryRequest]

/** A story / summary / analysis produced by the LLM for an entry. */
final case class GeneratedStory(
    id: UUID,
    entryId: UUID,
    title: String,
    genre: String,
    style: String,
    summary: String,
    story: String,
    createdAt: Instant
)

object GeneratedStory:
  given JsonCodec[GeneratedStory] = DeriveJsonCodec.gen[GeneratedStory]

/** Optional request body for the generate endpoints. Lets the caller override
  * the generation mode and the configured provider's model.
  */
final case class GenerateRequest(
    mode: Option[String] = None,
    model: Option[String] = None
)

object GenerateRequest:
  given JsonCodec[GenerateRequest] = DeriveJsonCodec.gen[GenerateRequest]

/** The structured shape we ask the LLM to return. All fields optional so a
  * partial response can still be salvaged.
  */
final case class LLMStoryResponse(
    title: Option[String] = None,
    genre: Option[String] = None,
    style: Option[String] = None,
    summary: Option[String] = None,
    story: Option[String] = None
)

object LLMStoryResponse:
  given JsonCodec[LLMStoryResponse] = DeriveJsonCodec.gen[LLMStoryResponse]
