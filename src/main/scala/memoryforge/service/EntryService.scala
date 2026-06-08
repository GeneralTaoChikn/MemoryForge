package memoryforge.service

import memoryforge.domain.*
import memoryforge.repository.EntryRepository
import zio.*

import java.time.Instant
import java.util.UUID

/** Business logic for journal entries. */
final class EntryService(repo: EntryRepository):

  def create(req: CreateEntryRequest): IO[AppError, JournalEntry] =
    for
      _ <- ZIO
        .fail(AppError.InvalidRequest("title must not be empty"))
        .when(req.title.trim.isEmpty)
      _ <- ZIO
        .fail(AppError.InvalidRequest("content must not be empty"))
        .when(req.content.trim.isEmpty)
      now = Instant.now()
      entry = JournalEntry(
        id = UUID.randomUUID(),
        title = req.title.trim,
        content = req.content,
        mood = req.mood.map(_.trim).filter(_.nonEmpty),
        tags = req.tags.getOrElse(Nil).map(_.trim).filter(_.nonEmpty),
        createdAt = now,
        updatedAt = now
      )
      saved <- repo.create(entry)
    yield saved

  def list: IO[AppError, List[JournalEntry]] = repo.getAll

  /** Fetch an entry or fail with EntryNotFound. */
  def get(id: UUID): IO[AppError, JournalEntry] =
    repo.getById(id).flatMap {
      case Some(e) => ZIO.succeed(e)
      case None    => ZIO.fail(AppError.EntryNotFound(id))
    }

  def delete(id: UUID): IO[AppError, Unit] =
    repo.delete(id).flatMap {
      case true  => ZIO.unit
      case false => ZIO.fail(AppError.EntryNotFound(id))
    }

object EntryService:
  val live: ZLayer[EntryRepository, Nothing, EntryService] =
    ZLayer.fromFunction(new EntryService(_))
