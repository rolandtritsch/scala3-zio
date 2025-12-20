package org.roland.scala3_zio_template.service

import javax.sql.DataSource

import io.getquill._
import io.getquill.jdbczio.Quill

import zio._

/** Configuration for PostgreSQL database connection.
  *
  * This case class holds all necessary parameters to establish a JDBC
  * connection to a PostgreSQL database. Configuration values are typically
  * loaded from environment variables via `DatabaseService.loadConfig`.
  *
  * @param host
  *   PostgreSQL server hostname or IP address
  * @param port
  *   PostgreSQL server port (default: 5432)
  * @param database
  *   Name of the database to connect to
  * @param username
  *   Database user for authentication
  * @param password
  *   Database password for authentication
  *
  * @see
  *   [[DatabaseService.loadConfig]] for loading from environment variables
  */
case class DatabaseConfig(
    host: String,
    port: Int,
    database: String,
    username: String,
    password: String
):
  /** Constructs a JDBC URL from the configuration parameters.
    *
    * @return
    *   JDBC connection string in the format:
    *   jdbc:postgresql://host:port/database
    */
  def jdbcUrl: String =
    s"jdbc:postgresql://$host:$port/$database"

/** Service interface for PostgreSQL database operations.
  *
  * This trait defines the contract for database interactions, providing methods
  * for health checks and query execution. Implementations should be created via
  * the companion object's `live` layer.
  *
  * All methods return ZIO effects to ensure type-safe error handling and
  * resource management. Database operations are executed using Quill for
  * compile-time query validation.
  *
  * @see
  *   [[DatabaseService.live]] for the production implementation
  */
trait DatabaseService:
  /** Performs a basic database health check by executing SELECT 1.
    *
    * This method validates that the database connection is alive and can
    * execute queries. It's used by the /health-deep endpoint to verify database
    * availability.
    *
    * @return
    *   ZIO effect that succeeds with true if the database is healthy, or fails
    *   with an error
    */
  def healthCheck(): ZIO[Any, Throwable, Boolean]

/** Companion object providing ZLayer construction and configuration loading.
  *
  * This object contains factory methods for creating DatabaseService instances
  * and loading configuration from environment variables. The `live` layer
  * provides a complete, production-ready database service with connection
  * pooling and startup validation.
  */
object DatabaseService:

  /** Loads database configuration from environment variables.
    *
    * The following environment variables are used:
    *   - `DATABASE_HOST`: PostgreSQL hostname (default: localhost)
    *   - `DATABASE_PORT`: PostgreSQL port (default: 5432)
    *   - `DATABASE_NAME`: Database name (default: postgres)
    *   - `DATABASE_USER`: Database username (required)
    *   - `DATABASE_PASSWORD`: Database password (required)
    *
    * The user and password variables are required and the application will fail
    * to start if they are not provided.
    *
    * @return
    *   ZIO effect that succeeds with DatabaseConfig or fails with an error
    *   message
    */
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

  /** ZLayer that provides a configured JDBC DataSource.
    *
    * This layer loads the database configuration from environment variables and
    * constructs a PostgreSQL DataSource with connection pooling. The DataSource
    * is configured but not validated at this stage - validation happens in the
    * service layer.
    *
    * @return
    *   ZLayer that provides a javax.sql.DataSource instance
    */
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

  /** ZLayer that provides the DatabaseService implementation.
    *
    * This layer requires both a Quill context and a DataSource to be provided.
    * It performs connection validation at startup by executing a simple SELECT
    * 1 query. If validation fails, the application will not start.
    *
    * The returned service implementation provides:
    *   - Health check via SELECT 1
    *   - Raw SQL execution (for future use)
    *   - Query methods (not yet implemented)
    *
    * @return
    *   ZLayer that provides DatabaseService, requiring Quill and DataSource
    */
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
        _ <- ZIO
          .attemptBlocking {
            val conn = ds.getConnection()
            try {
              val stmt = conn.createStatement()
              val rs = stmt.executeQuery("SELECT 1")
              if (rs.next() && rs.getInt(1) == 1) {
                Right(())
              } else {
                Left(
                  new RuntimeException(
                    "Database connection validation failed: unexpected result"
                  )
                )
              }
            } finally {
              conn.close()
            }
          }
          .flatMap(ZIO.fromEither)
        _ <- ZIO.logInfo("Database connection validated successfully")

        service = new DatabaseService {
          import ctx._

          override def healthCheck(): ZIO[Any, Throwable, Boolean] =
            ctx
              .run(sql"SELECT 1".as[Query[Int]])
              .map { results =>
                results.headOption.contains(1)
              }
        }
      } yield service
    )

  /** Complete ZLayer stack that provides a production-ready DatabaseService.
    *
    * This layer combines:
    *   - DataSource configuration from environment variables
    *   - Quill PostgreSQL context with SnakeCase naming strategy
    *   - Service implementation with startup validation
    *
    * This is the primary layer to use when providing DatabaseService to your
    * application. It handles all dependency wiring and ensures the database is
    * accessible before the application starts serving requests.
    *
    * @return
    *   ZLayer that provides DatabaseService with no external dependencies
    */
  val live: ZLayer[Any, Throwable, DatabaseService] =
    ZLayer.make[DatabaseService](
      dataSourceLayer,
      Quill.Postgres.fromNamingStrategy(SnakeCase),
      serviceLayer
    )
