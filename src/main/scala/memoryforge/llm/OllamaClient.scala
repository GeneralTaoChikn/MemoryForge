package memoryforge.llm

import memoryforge.config.OllamaConfig
import memoryforge.domain.AppError
import zio.*
import zio.http.*
import zio.json.*

/** Request payload for Ollama's /api/generate endpoint. */
private final case class OllamaGenerateRequest(
    model: String,
    prompt: String,
    stream: Boolean,
    format: String
)
private object OllamaGenerateRequest:
  given JsonEncoder[OllamaGenerateRequest] = DeriveJsonEncoder.gen[OllamaGenerateRequest]

/** Relevant part of Ollama's response (extra fields are ignored). */
private final case class OllamaGenerateResponse(response: String)
private object OllamaGenerateResponse:
  given JsonDecoder[OllamaGenerateResponse] = DeriveJsonDecoder.gen[OllamaGenerateResponse]

/** HTTP client for a locally running Ollama instance. */
final class OllamaClient(config: OllamaConfig, client: Client):

  /** Calls Ollama and returns the raw text the model produced (the `response`
    * field). Asks for JSON-formatted output via `format = "json"`.
    *
    * @param prompt        the full prompt to send
    * @param modelOverride optional model name, otherwise the configured default
    */
  def generate(prompt: String, modelOverride: Option[String] = None): IO[AppError, String] =
    val model   = modelOverride.filter(_.trim.nonEmpty).getOrElse(config.model)
    val payload = OllamaGenerateRequest(model, prompt, stream = false, format = "json").toJson
    val url     = s"${config.baseUrl}/api/generate"

    val call =
      for
        request <- ZIO
          .fromEither(URL.decode(url))
          .mapError(e => AppError.OllamaUnavailable(s"Invalid Ollama URL '$url': ${e.getMessage}"))
        response <- Client
          .batched(
            Request
              .post(request, Body.fromString(payload))
              .addHeader(Header.ContentType(MediaType.application.json))
          )
          .provideEnvironment(ZEnvironment(client))
          .mapError(t => AppError.OllamaUnavailable(s"Could not reach Ollama at $url: ${t.getMessage}"))
        bodyStr <- response.body.asString
          .mapError(t => AppError.OllamaUnavailable(s"Failed to read Ollama response: ${t.getMessage}"))
        _ <- ZIO
          .fail(AppError.OllamaUnavailable(s"Ollama returned ${response.status.code}: $bodyStr"))
          .when(response.status.code >= 400)
        parsed <- ZIO
          .fromEither(bodyStr.fromJson[OllamaGenerateResponse])
          .mapError(err => AppError.InvalidLLMResponse(s"Could not parse Ollama envelope: $err", bodyStr))
      yield parsed.response

    call.timeoutFail(
      AppError.LLMTimeout(s"Ollama did not respond within ${config.timeout.toSeconds}s")
    )(config.timeout)

object OllamaClient:
  val live: ZLayer[OllamaConfig & Client, Nothing, OllamaClient] =
    ZLayer.fromFunction(new OllamaClient(_, _))
