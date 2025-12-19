package org.roland.scala3_zio_template.service

import io.getquill._
import io.getquill.jdbczio.Quill
import zio._

import javax.sql.DataSource

case class DatabaseConfig(
    host: String,
    port: Int,
    database: String,
    username: String,
    password: String
):
  def jdbcUrl: String =
    s"jdbc:postgresql://$host:$port/$database"

trait DatabaseService:
  def healthCheck(): ZIO[Any, Throwable, Boolean]
  def execute(sql: String): ZIO[Any, Throwable, Unit]
  def selectOne[T](sql: String): ZIO[Any, Throwable, Option[T]]
  def selectAll[T](sql: String): ZIO[Any, Throwable, List[T]]

object DatabaseService:

  def loadConfig: IO[String, DatabaseConfig] =
    (for {
      host <- ZIO
        .attempt(java.lang.System.getenv("DATABASE_HOST"))
        .filterOrFail(Option(_).exists(_.nonEmpty))("Missing DATABASE_HOST")
        .orElse(ZIO.succeed("localhost"))

      port <- ZIO
        .attempt(java.lang.System.getenv("DATABASE_PORT"))
        .flatMap(p => ZIO.attempt(p.toInt))
        .catchAll(_ => ZIO.succeed(5432))

      database <- ZIO
        .attempt(java.lang.System.getenv("DATABASE_NAME"))
        .filterOrFail(Option(_).exists(_.nonEmpty))("Missing DATABASE_NAME")
        .orElse(ZIO.succeed("postgres"))

      username <- ZIO
        .attempt(java.lang.System.getenv("DATABASE_USER"))
        .filterOrFail(Option(_).exists(_.nonEmpty))("Missing DATABASE_USER")

      password <- ZIO
        .attempt(java.lang.System.getenv("DATABASE_PASSWORD"))
        .filterOrFail(Option(_).exists(_.nonEmpty))(
          "Missing DATABASE_PASSWORD"
        )

    } yield DatabaseConfig(host, port, database, username, password))
      .mapError(_.toString)

  val dataSourceLayer: ZLayer[Any, Throwable, DataSource] =
    ZLayer.fromZIO(
      for {
        config <- loadConfig.mapError(e =>
          new RuntimeException(s"Failed to load database config: $e")
        )
        _ <- ZIO.logInfo(
          s"Configuring DataSource for ${config.host}:${config.port}/${config.database}"
        )
      } yield {
        val ds = new org.postgresql.ds.PGSimpleDataSource()
        ds.setServerNames(Array(config.host))
        ds.setPortNumbers(Array(config.port))
        ds.setDatabaseName(config.database)
        ds.setUser(config.username)
        ds.setPassword(config.password)
        ds
      }
    )

  private val serviceLayer: ZLayer[
    Quill.Postgres[SnakeCase.type] & DataSource,
    Throwable,
    DatabaseService
  ] =
    ZLayer.fromZIO(
      for {
        ctx <- ZIO.service[Quill.Postgres[SnakeCase.type]]
        ds <- ZIO.service[DataSource]

        // Validate connection at startup
        _ <- ZIO.attemptBlocking {
          val conn = ds.getConnection()
          try {
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery("SELECT 1")
            if (rs.next() && rs.getInt(1) == 1) {
              ()
            } else {
              throw new RuntimeException(
                "Database connection validation failed: unexpected result"
              )
            }
          } finally {
            conn.close()
          }
        }
        _ <- ZIO.logInfo("Database connection validated successfully")

        service = new DatabaseService {
          import ctx._

          override def healthCheck(): ZIO[Any, Throwable, Boolean] =
            ctx
              .run(sql"SELECT 1".as[Query[Int]])
              .map { results =>
                results.headOption.contains(1)
              }

          override def execute(sql: String): ZIO[Any, Throwable, Unit] =
            ctx.run(infix"#$sql".as[Action[Unit]]).unit

          override def selectOne[T](
              sql: String
          ): ZIO[Any, Throwable, Option[T]] =
            ZIO.fail(
              new UnsupportedOperationException(
                "selectOne with raw SQL not yet implemented - use healthCheck for now"
              )
            )

          override def selectAll[T](
              sql: String
          ): ZIO[Any, Throwable, List[T]] =
            ZIO.fail(
              new UnsupportedOperationException(
                "selectAll with raw SQL not yet implemented"
              )
            )
        }
      } yield service
    )

  val live: ZLayer[Any, Throwable, DatabaseService] =
    ZLayer.make[DatabaseService](
      dataSourceLayer,
      Quill.Postgres.fromNamingStrategy(SnakeCase),
      serviceLayer
    )
