package memoryforge.domain

import java.util.UUID

/** Domain-level errors. Each maps to an HTTP status in the routes layer. */
sealed trait AppError extends Throwable:
  def message: String
  override def getMessage: String = message

object AppError:
  /** 404 - the requested entry does not exist. */
  final case class EntryNotFound(id: UUID) extends AppError:
    val message = s"Entry not found: $id"

  /** 400 - the request body could not be parsed / was invalid. */
  final case class InvalidRequest(message: String) extends AppError

  /** 503 - the database could not be reached or a query failed. */
  final case class DatabaseError(message: String, cause: Throwable) extends AppError

  /** 502 - Ollama could not be reached. */
  final case class OllamaUnavailable(message: String) extends AppError

  /** 502 - the configured LLM provider could not be reached or used. */
  final case class LLMUnavailable(message: String) extends AppError

  /** 504 - the LLM took too long to respond. */
  final case class LLMTimeout(message: String) extends AppError

  /** 502 - the LLM returned a response we could not use at all. */
  final case class InvalidLLMResponse(message: String, raw: String) extends AppError
