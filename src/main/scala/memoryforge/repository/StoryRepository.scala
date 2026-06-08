package memoryforge.repository

import memoryforge.db.Database
import memoryforge.domain.{AppError, GeneratedStory}
import zio.*

import java.sql.{ResultSet, Timestamp}
import java.util.UUID

/** Persistence for generated stories. */
final class StoryRepository(db: Database):

  def create(story: GeneratedStory): IO[AppError, GeneratedStory] =
    db.transact { conn =>
      val ps = conn.prepareStatement(
        """INSERT INTO stories (id, entry_id, title, genre, style, summary, story, created_at)
          |VALUES (?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
      )
      try
        ps.setObject(1, story.id)
        ps.setObject(2, story.entryId)
        ps.setString(3, story.title)
        ps.setString(4, story.genre)
        ps.setString(5, story.style)
        ps.setString(6, story.summary)
        ps.setString(7, story.story)
        ps.setTimestamp(8, Timestamp.from(story.createdAt))
        ps.executeUpdate()
        story
      finally ps.close()
    }

  def getAll: IO[AppError, List[GeneratedStory]] =
    db.transact { conn =>
      val ps = conn.prepareStatement("SELECT * FROM stories ORDER BY created_at DESC")
      try StoryRepository.readAll(ps.executeQuery())
      finally ps.close()
    }

  def getByEntry(entryId: UUID): IO[AppError, List[GeneratedStory]] =
    db.transact { conn =>
      val ps = conn.prepareStatement(
        "SELECT * FROM stories WHERE entry_id = ? ORDER BY created_at DESC"
      )
      try
        ps.setObject(1, entryId)
        StoryRepository.readAll(ps.executeQuery())
      finally ps.close()
    }

object StoryRepository:
  val live: ZLayer[Database, Nothing, StoryRepository] =
    ZLayer.fromFunction(new StoryRepository(_))

  private def readAll(rs: ResultSet): List[GeneratedStory] =
    val buf = scala.collection.mutable.ListBuffer.empty[GeneratedStory]
    while rs.next() do
      buf += GeneratedStory(
        id = rs.getObject("id", classOf[UUID]),
        entryId = rs.getObject("entry_id", classOf[UUID]),
        title = rs.getString("title"),
        genre = rs.getString("genre"),
        style = rs.getString("style"),
        summary = rs.getString("summary"),
        story = rs.getString("story"),
        createdAt = rs.getTimestamp("created_at").toInstant
      )
    buf.toList
