package memoryforge.repository

import memoryforge.db.Database
import memoryforge.domain.{AppError, JournalEntry}
import zio.*

import java.sql.{Connection, ResultSet, Timestamp}
import java.time.Instant
import java.util.UUID

/** Persistence for journal entries. */
final class EntryRepository(db: Database):

  def create(entry: JournalEntry): IO[AppError, JournalEntry] =
    db.transact { conn =>
      val ps = conn.prepareStatement(
        """INSERT INTO entries (id, title, content, mood, tags, created_at, updated_at)
          |VALUES (?, ?, ?, ?, ?, ?, ?)""".stripMargin
      )
      try
        ps.setObject(1, entry.id)
        ps.setString(2, entry.title)
        ps.setString(3, entry.content)
        ps.setString(4, entry.mood.orNull)
        ps.setString(5, EntryRepository.encodeTags(entry.tags))
        ps.setTimestamp(6, Timestamp.from(entry.createdAt))
        ps.setTimestamp(7, Timestamp.from(entry.updatedAt))
        ps.executeUpdate()
        entry
      finally ps.close()
    }

  def getAll: IO[AppError, List[JournalEntry]] =
    db.transact { conn =>
      val ps = conn.prepareStatement("SELECT * FROM entries ORDER BY created_at DESC")
      try
        val rs = ps.executeQuery()
        EntryRepository.readAll(rs)
      finally ps.close()
    }

  def getById(id: UUID): IO[AppError, Option[JournalEntry]] =
    db.transact { conn =>
      val ps = conn.prepareStatement("SELECT * FROM entries WHERE id = ?")
      try
        ps.setObject(1, id)
        val rs = ps.executeQuery()
        EntryRepository.readAll(rs).headOption
      finally ps.close()
    }

  /** Returns true if a row was deleted, false if the id did not exist. */
  def delete(id: UUID): IO[AppError, Boolean] =
    db.transact { conn =>
      val ps = conn.prepareStatement("DELETE FROM entries WHERE id = ?")
      try
        ps.setObject(1, id)
        ps.executeUpdate() > 0
      finally ps.close()
    }

object EntryRepository:
  val live: ZLayer[Database, Nothing, EntryRepository] =
    ZLayer.fromFunction(new EntryRepository(_))

  private def encodeTags(tags: List[String]): String =
    tags.map(_.trim).filter(_.nonEmpty).mkString(",")

  private def decodeTags(s: String): List[String] =
    Option(s).map(_.split(",").toList.map(_.trim).filter(_.nonEmpty)).getOrElse(Nil)

  private def readAll(rs: ResultSet): List[JournalEntry] =
    val buf = scala.collection.mutable.ListBuffer.empty[JournalEntry]
    while rs.next() do
      buf += JournalEntry(
        id = rs.getObject("id", classOf[UUID]),
        title = rs.getString("title"),
        content = rs.getString("content"),
        mood = Option(rs.getString("mood")),
        tags = decodeTags(rs.getString("tags")),
        createdAt = rs.getTimestamp("created_at").toInstant,
        updatedAt = rs.getTimestamp("updated_at").toInstant
      )
    buf.toList
