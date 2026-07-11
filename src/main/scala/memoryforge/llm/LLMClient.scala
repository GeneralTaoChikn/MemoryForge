package memoryforge.llm

import memoryforge.config.{LLMConfig, LLMProvider}
import memoryforge.domain.AppError
import zio.*

trait LLMClient:
  def generate(prompt: String, modelOverride: Option[String] = None): IO[AppError, String]

object LLMClient:
  val live: ZLayer[LLMConfig & OllamaClient & GeminiClient, Nothing, LLMClient] =
    ZLayer.fromZIO {
      for
        config <- ZIO.service[LLMConfig]
        client <- config.provider match
          case LLMProvider.Ollama => ZIO.service[OllamaClient].map(client => client: LLMClient)
          case LLMProvider.Gemini => ZIO.service[GeminiClient].map(client => client: LLMClient)
      yield client
    }
