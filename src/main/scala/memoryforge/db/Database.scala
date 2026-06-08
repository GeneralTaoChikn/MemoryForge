package memoryforge.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import memoryforge.config.DbConfig
import memoryforge.domain.AppError
import zio.*

import java.sql.Connection
import javax.sql.DataSource

/** Thin wrapper around a Hikari [[DataSource]].
  *
  * Exposes a `transact` helper that borrows a connection, runs the given
  * function on the blocking thread pool, and always closes the connection.
  * Any JDBC exception is mapped to [[AppError.DatabaseError]].
  */
final class Database(dataSource: DataSource):

  /** Run a function that uses a JDBC connection. */
  def transact[A](f: Connection => A): IO[AppError, A] =
    ZIO
      .acquireReleaseWith(
        ZIO.attemptBlocking(dataSource.getConnection)
      )(conn => ZIO.attemptBlocking(conn.close()).orDie) { conn =>
        ZIO.attemptBlocking(f(conn))
      }
      .mapError(t => AppError.DatabaseError(s"Database error: ${t.getMessage}", t))

  /** Create tables if they do not exist. Safe to run on every startup. */
  def initSchema: IO[AppError, Unit] =
    transact { conn =>
      val stmt = conn.createStatement()
      try
        stmt.execute(Database.createEntriesTable)
        stmt.execute(Database.createStoriesTable)
        stmt.execute(Database.createStoriesIndex)
        stmt.execute(Database.createEntriesIndex)
      finally stmt.close()
    }.unit

object Database:
  private val createEntriesTable =
    """CREATE TABLE IF NOT EXISTS entries (
      |  id         UUID PRIMARY KEY,
      |  title      TEXT        NOT NULL,
      |  content    TEXT        NOT NULL,
      |  mood       TEXT,
      |  tags       TEXT,
      |  created_at TIMESTAMPTZ NOT NULL,
      |  updated_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val createStoriesTable =
    """CREATE TABLE IF NOT EXISTS stories (
      |  id         UUID PRIMARY KEY,
      |  entry_id   UUID        NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
      |  title      TEXT,
      |  genre      TEXT,
      |  style      TEXT,
      |  summary    TEXT,
      |  story      TEXT,
      |  created_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val createStoriesIndex =
    "CREATE INDEX IF NOT EXISTS idx_stories_entry_id ON stories(entry_id)"

  private val createEntriesIndex =
    "CREATE INDEX IF NOT EXISTS idx_entries_created_at ON entries(created_at DESC)"

  /** Builds the Hikari pool. Retries a few times so the backend can wait for
    * Postgres to become available (useful under docker-compose).
    */
  val live: ZLayer[DbConfig, AppError, Database] =
    ZLayer.scoped {
      for
        cfg <- ZIO.service[DbConfig]
        ds <- ZIO.acquireRelease(
          ZIO
            .attemptBlocking {
              val hikari = new HikariConfig()
              hikari.setJdbcUrl(cfg.url)
              hikari.setUsername(cfg.user)
              hikari.setPassword(cfg.password)
              hikari.setMaximumPoolSize(cfg.poolSize)
              hikari.setDriverClassName("org.postgresql.Driver")
              hikari.setPoolName("memoryforge-pool")
              new HikariDataSource(hikari)
            }
            .mapError(t => AppError.DatabaseError(s"Failed to create pool: ${t.getMessage}", t))
        )(ds => ZIO.attemptBlocking(ds.close()).orDie)
        db = new Database(ds)
        _ <- db.initSchema
          .tapError(e => ZIO.logError(s"Schema init failed: ${e.message}"))
          .retry(Schedule.recurs(10) && Schedule.spaced(2.seconds))
      yield db
    }
