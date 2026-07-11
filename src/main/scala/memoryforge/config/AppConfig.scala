package memoryforge.config

import zio.*

/** Application configuration, read from environment variables with sensible
  * local-development defaults.
  */
final case class AppConfig(
    serverPort: Int,
    db: DbConfig,
    llm: LLMConfig,
    ollama: OllamaConfig,
    gemini: GeminiConfig
)

final case class DbConfig(
    url: String,
    user: String,
    password: String,
    poolSize: Int
)

final case class OllamaConfig(
    baseUrl: String,
    model: String,
    timeout: Duration
)

enum LLMProvider:
  case Ollama, Gemini

object LLMProvider:
  def fromString(value: String): LLMProvider =
    value.trim.toLowerCase match
      case "gemini" => Gemini
      case _        => Ollama

final case class LLMConfig(provider: LLMProvider)

final case class GeminiConfig(
    apiKey: Option[String],
    baseUrl: String,
    model: String,
    timeout: Duration
)

object AppConfig:
  private def env(name: String, default: String): UIO[String] =
    System.env(name).map(_.getOrElse(default)).orElse(ZIO.succeed(default))

  private def envInt(name: String, default: Int): UIO[Int] =
    env(name, default.toString).map(s => s.toIntOption.getOrElse(default))

  val live: ZLayer[Any, Nothing, AppConfig] =
    ZLayer.fromZIO {
      for
        port      <- envInt("SERVER_PORT", 8080)
        dbUrl     <- env("DB_URL", "jdbc:postgresql://localhost:5432/memoryforge")
        dbUser    <- env("DB_USER", "memoryforge")
        dbPass    <- env("DB_PASSWORD", "memoryforge")
        poolSize  <- envInt("DB_POOL_SIZE", 10)
        provider  <- env("LLM_PROVIDER", "ollama")
        ollamaUrl <- env("OLLAMA_URL", "http://localhost:11434")
        ollamaModel <- env("OLLAMA_MODEL", "llama3.1")
        ollamaTimeoutS <- envInt("OLLAMA_TIMEOUT_SECONDS", 120)
        geminiApiKey <- System.env("GEMINI_API_KEY").orElse(ZIO.succeed(None))
        geminiUrl <- env("GEMINI_URL", "https://generativelanguage.googleapis.com")
        geminiModel <- env("GEMINI_MODEL", "gemini-3.5-flash")
        geminiTimeoutS <- envInt("GEMINI_TIMEOUT_SECONDS", 120)
      yield AppConfig(
        serverPort = port,
        db = DbConfig(dbUrl, dbUser, dbPass, poolSize),
        llm = LLMConfig(LLMProvider.fromString(provider)),
        ollama = OllamaConfig(ollamaUrl, ollamaModel, ollamaTimeoutS.seconds),
        gemini = GeminiConfig(geminiApiKey, geminiUrl, geminiModel, geminiTimeoutS.seconds)
      )
    }
