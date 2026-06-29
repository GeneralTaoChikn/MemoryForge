package memoryforge.routes

import memoryforge.domain.*
import memoryforge.domain.AppError.*
import memoryforge.service.{EntryService, StoryService}
import zio.*
import zio.http.*
import zio.json.*

import java.util.UUID

private final case class ErrorBody(error: String, message: String)
private object ErrorBody:
  given JsonCodec[ErrorBody] = DeriveJsonCodec.gen[ErrorBody]

/** HTTP routes layer. Translates requests into service calls and domain errors
  * into HTTP responses.
  */
final class HttpApi(entries: EntryService, storyService: StoryService):

  // Permissive CORS so a separately-served frontend (any origin) can call the API.
  private val cors = Middleware.cors(Middleware.CorsConfig())

  // The bundled single-page frontend, loaded once from the classpath
  // (src/main/resources/static/index.html) so it ships inside the fat jar.
  private val indexResponse: Response =
    Option(getClass.getResourceAsStream("/static/index.html")) match
      case Some(in) =>
        try
          val html = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
          Response(
            status = Status.Ok,
            headers = Headers(Header.ContentType(MediaType.text.html)),
            body = Body.fromString(html)
          )
        finally in.close()
      case None =>
        Response.status(Status.NotFound)

  val routes: Routes[Any, Response] =
    (Routes(
      // Serve the bundled frontend at the root and /index.html
      Method.GET / "" -> handler { (_: Request) =>
        indexResponse
      },
      Method.GET / "index.html" -> handler { (_: Request) =>
        indexResponse
      },

      // Health check
      Method.GET / "health" -> handler { (_: Request) =>
        Response.json("""{"status":"ok"}""")
      },



      // POST /entries
      Method.POST / "entries" -> handler { (req: Request) =>
        complete {
          for
            body  <- parseBody[CreateEntryRequest](req)
            entry <- entries.create(body)
          yield jsonResponse(Status.Created, entry)
        }
      },

      // GET /entries
      Method.GET / "entries" -> handler { (_: Request) =>
        complete(entries.list.map(es => jsonResponse(Status.Ok, es)))
      },

      // GET /entries/{id}
      Method.GET / "entries" / string("id") -> handler { (id: String, _: Request) =>
        complete {
          for
            uuid  <- parseUuid(id)
            entry <- entries.get(uuid)
          yield jsonResponse(Status.Ok, entry)
        }
      },

      // DELETE /entries/{id}
      Method.DELETE / "entries" / string("id") -> handler { (id: String, _: Request) =>
        complete {
          for
            uuid <- parseUuid(id)
            _    <- entries.delete(uuid)
          yield Response.status(Status.NoContent)
        }
      },

      // POST /entries/{id}/generate-story  (mode chosen via body, default funny)
      Method.POST / "entries" / string("id") / "generate-story" -> handler {
        (id: String, req: Request) => complete(generate(id, req, None))
      },

      // POST /entries/{id}/summarize  (reflective journal summary)
      Method.POST / "entries" / string("id") / "summarize" -> handler {
        (id: String, req: Request) =>
          complete(generate(id, req, Some(GenerationMode.ReflectiveSummary)))
      },

      // POST /entries/{id}/analyze-themes  (recurring theme analysis)
      Method.POST / "entries" / string("id") / "analyze-themes" -> handler {
        (id: String, req: Request) =>
          complete(generate(id, req, Some(GenerationMode.ThemeAnalysis)))
      },

      // GET /stories
      Method.GET / "stories" -> handler { (_: Request) =>
        complete(storyService.listAll.map(ss => jsonResponse(Status.Ok, ss)))
      }
    )) @@ cors

  // --- generation helper -----------------------------------------------------


  private def generate(
      id: String,
      req: Request,
      forcedMode: Option[GenerationMode]
  ): IO[AppError, Response] =
    for
      uuid <- parseUuid(id)
      body <- parseOptionalBody[GenerateRequest](req)
      mode = forcedMode.getOrElse(
        body.mode.flatMap(GenerationMode.fromKey).getOrElse(GenerationMode.default)
      )
      story <- storyService.generate(uuid, mode, body.model)
    yield jsonResponse(Status.Created, story)

  // --- helpers ---------------------------------------------------------------

  private def parseUuid(s: String): IO[AppError, UUID] =
    ZIO.attempt(UUID.fromString(s)).mapError(_ => InvalidRequest(s"Invalid UUID: $s"))

  private def parseBody[A: JsonDecoder](req: Request): IO[AppError, A] =
    req.body.asString
      .mapError(t => InvalidRequest(s"Could not read request body: ${t.getMessage}"))
      .flatMap(s => ZIO.fromEither(s.fromJson[A]).mapError(e => InvalidRequest(s"Invalid JSON body: $e")))

  /** Like parseBody but tolerates an empty body, returning the default value. */
  private def parseOptionalBody[A: JsonDecoder](req: Request)(using d: GenerateRequestDefault[A]): IO[AppError, A] =
    req.body.asString
      .mapError(t => InvalidRequest(s"Could not read request body: ${t.getMessage}"))
      .flatMap { s =>
        if s.trim.isEmpty then ZIO.succeed(d.value)
        else ZIO.fromEither(s.fromJson[A]).mapError(e => InvalidRequest(s"Invalid JSON body: $e"))
      }

  private def jsonResponse[A: JsonEncoder](status: Status, a: A): Response =
    Response.json(a.toJson).status(status)

  private def complete(io: IO[AppError, Response]): UIO[Response] =
    io.catchAll { e =>
      ZIO.logWarning(s"Request failed: ${e.message}").as(errorResponse(e))
    }

  private def errorResponse(e: AppError): Response =
    val (status, code) = e match
      case _: EntryNotFound      => (Status.NotFound, "entry_not_found")
      case _: InvalidRequest     => (Status.BadRequest, "invalid_request")
      case _: DatabaseError      => (Status.ServiceUnavailable, "database_error")
      case _: OllamaUnavailable  => (Status.BadGateway, "ollama_unavailable")
      case _: LLMTimeout         => (Status.GatewayTimeout, "llm_timeout")
      case _: InvalidLLMResponse => (Status.BadGateway, "invalid_llm_response")
    Response.json(ErrorBody(code, e.message).toJson).status(status)

object HttpApi:
  val live: ZLayer[EntryService & StoryService, Nothing, HttpApi] =
    ZLayer.fromFunction(new HttpApi(_, _))

/** Tiny type class providing a default value for empty-body parsing. */
trait GenerateRequestDefault[A]:
  def value: A
object GenerateRequestDefault:
  given GenerateRequestDefault[GenerateRequest] with
    def value: GenerateRequest = GenerateRequest()
