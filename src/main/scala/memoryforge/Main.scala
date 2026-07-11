package memoryforge

import memoryforge.config.*
import memoryforge.db.Database
import memoryforge.llm.{GeminiClient, LLMClient, OllamaClient}
import memoryforge.repository.{EntryRepository, StoryRepository}
import memoryforge.routes.HttpApi
import memoryforge.service.{EntryService, StoryService}
import zio.*
import zio.http.*

object Main extends ZIOAppDefault:

  // Projections of AppConfig into the smaller configs each layer needs.
  private val dbConfigLayer: ZLayer[AppConfig, Nothing, DbConfig] =
    ZLayer.fromFunction((c: AppConfig) => c.db)

  private val ollamaConfigLayer: ZLayer[AppConfig, Nothing, OllamaConfig] =
    ZLayer.fromFunction((c: AppConfig) => c.ollama)

  private val llmConfigLayer: ZLayer[AppConfig, Nothing, LLMConfig] =
    ZLayer.fromFunction((c: AppConfig) => c.llm)

  private val geminiConfigLayer: ZLayer[AppConfig, Nothing, GeminiConfig] =
    ZLayer.fromFunction((c: AppConfig) => c.gemini)

  private val serverConfigLayer: ZLayer[AppConfig, Nothing, Server.Config] =
    ZLayer.fromFunction((c: AppConfig) => Server.Config.default.port(c.serverPort))

  private val program: ZIO[AppConfig & HttpApi & Server, Throwable, Unit] =
    for
      cfg <- ZIO.service[AppConfig]
      api <- ZIO.service[HttpApi]
      _   <- ZIO.logInfo(s"MemoryForge backend listening on http://0.0.0.0:${cfg.serverPort}")
      _ <- cfg.llm.provider match
        case LLMProvider.Ollama =>
          ZIO.logInfo(s"Using Ollama at ${cfg.ollama.baseUrl} (model: ${cfg.ollama.model})")
        case LLMProvider.Gemini =>
          ZIO.logInfo(s"Using Gemini at ${cfg.gemini.baseUrl} (model: ${cfg.gemini.model})")
      _   <- Server.serve(api.routes)
    yield ()

  override def run: ZIO[Any, Throwable, Unit] =
    program.provide(
      AppConfig.live,
      llmConfigLayer,
      dbConfigLayer,
      ollamaConfigLayer,
      geminiConfigLayer,
      serverConfigLayer,
      Database.live,
      EntryRepository.live,
      StoryRepository.live,
      EntryService.live,
      OllamaClient.live,
      GeminiClient.live,
      LLMClient.live,
      StoryService.live,
      HttpApi.live,
      Client.default,
      Server.live
    )
