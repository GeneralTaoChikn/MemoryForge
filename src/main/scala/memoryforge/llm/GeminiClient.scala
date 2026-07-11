package memoryforge.llm

import memoryforge.config.GeminiConfig
import memoryforge.domain.AppError
import zio.*
import zio.http.*
import zio.json.*

private final case class GeminiSchemaProperty(`type`: String)
private object GeminiSchemaProperty:
  given JsonEncoder[GeminiSchemaProperty] = DeriveJsonEncoder.gen[GeminiSchemaProperty]

private final case class GeminiJsonSchema(
    `type`: String,
    properties: Map[String, GeminiSchemaProperty],
    required: List[String]
)
private object GeminiJsonSchema:
  given JsonEncoder[GeminiJsonSchema] = DeriveJsonEncoder.gen[GeminiJsonSchema]

private final case class GeminiResponseFormat(
    `type`: String,
    mime_type: String,
    schema: GeminiJsonSchema
)
private object GeminiResponseFormat:
  given JsonEncoder[GeminiResponseFormat] = DeriveJsonEncoder.gen[GeminiResponseFormat]

private final case class GeminiInteractionRequest(
    model: String,
    input: String,
    response_format: GeminiResponseFormat
)
private object GeminiInteractionRequest:
  given JsonEncoder[GeminiInteractionRequest] = DeriveJsonEncoder.gen[GeminiInteractionRequest]

private final case class GeminiInteractionResponse(output_text: Option[String])
private object GeminiInteractionResponse:
  given JsonDecoder[GeminiInteractionResponse] = DeriveJsonDecoder.gen[GeminiInteractionResponse]

final class GeminiClient(config: GeminiConfig, client: Client) extends LLMClient:

  private val storySchema =
    GeminiJsonSchema(
      `type` = "object",
      properties = Map(
        "title"   -> GeminiSchemaProperty("string"),
        "genre"   -> GeminiSchemaProperty("string"),
        "style"   -> GeminiSchemaProperty("string"),
        "summary" -> GeminiSchemaProperty("string"),
        "story"   -> GeminiSchemaProperty("string")
      ),
      required = List("title", "genre", "style", "summary", "story")
    )

  def generate(prompt: String, modelOverride: Option[String] = None): IO[AppError, String] =
    val model = modelOverride.filter(_.trim.nonEmpty).getOrElse(config.model)
    val payload = GeminiInteractionRequest(
      model = model,
      input = prompt,
      response_format = GeminiResponseFormat(
        `type` = "text",
        mime_type = "application/json",
        schema = storySchema
      )
    ).toJson

    val call =
      for
        apiKey <- ZIO
          .fromOption(config.apiKey.filter(_.trim.nonEmpty))
          .mapError(_ => AppError.LLMUnavailable("GEMINI_API_KEY must be set when LLM_PROVIDER=gemini"))
        url = s"${config.baseUrl}/v1beta/interactions"
        requestUrl <- ZIO
          .fromEither(URL.decode(url))
          .mapError(e => AppError.LLMUnavailable(s"Invalid Gemini URL '$url': ${e.getMessage}"))
        response <- Client
          .batched(
            Request
              .post(requestUrl, Body.fromString(payload))
              .addHeader(Header.ContentType(MediaType.application.json))
              .addHeader(Header.Custom("x-goog-api-key", apiKey))
          )
          .provideEnvironment(ZEnvironment(client))
          .mapError(t => AppError.LLMUnavailable(s"Could not reach Gemini at ${config.baseUrl}: ${t.getMessage}"))
        bodyStr <- response.body.asString
          .mapError(t => AppError.LLMUnavailable(s"Failed to read Gemini response: ${t.getMessage}"))
        _ <- ZIO
          .fail(AppError.LLMUnavailable(s"Gemini returned ${response.status.code}: $bodyStr"))
          .when(response.status.code >= 400)
        parsed <- ZIO
          .fromEither(bodyStr.fromJson[GeminiInteractionResponse])
          .mapError(err => AppError.InvalidLLMResponse(s"Could not parse Gemini envelope: $err", bodyStr))
        text <- ZIO
          .fromOption(parsed.output_text.filter(_.trim.nonEmpty))
          .mapError(_ => AppError.InvalidLLMResponse("Gemini response did not contain generated text", bodyStr))
      yield text

    call.timeoutFail(
      AppError.LLMTimeout(s"Gemini did not respond within ${config.timeout.toSeconds}s")
    )(config.timeout)

object GeminiClient:
  val live: ZLayer[GeminiConfig & Client, Nothing, GeminiClient] =
    ZLayer.fromFunction(new GeminiClient(_, _))
