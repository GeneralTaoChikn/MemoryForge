package memoryforge.config

import zio.*

/** Application configuration, read from environment variables with sensible
  * local-development defaults.
  */
final case class AppConfig(
    serverPort: Int,
    db: DbConfig,
    ollama: OllamaConfig
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
        ollamaUrl <- env("OLLAMA_URL", "http://localhost:11434")
        model     <- env("OLLAMA_MODEL", "llama3.1")
        timeoutS  <- envInt("OLLAMA_TIMEOUT_SECONDS", 120)
      yield AppConfig(
        serverPort = port,
        db = DbConfig(dbUrl, dbUser, dbPass, poolSize),
        ollama = OllamaConfig(ollamaUrl, model, timeoutS.seconds)
      )
    }
