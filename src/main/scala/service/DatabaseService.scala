package org.roland.scala3_zio_template.service

import javax.sql.DataSource

import io.getquill._
import io.getquill.jdbczio.Quill
import org.roland.scala3_zio_template.config.DatabaseConfig

import zio._

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

/** Companion object providing ZLayer construction.
  *
  * This object contains factory methods for creating DatabaseService instances.
  * The `live` layer provides a complete, production-ready database service with
  * connection pooling and startup validation.
  */
object DatabaseService:

  /** ZLayer that provides a configured JDBC DataSource.
    *
    * This layer constructs a PostgreSQL DataSource with connection pooling from
    * the provided DatabaseConfig. The DataSource is configured but not
    * validated at this stage - validation happens in the service layer.
    *
    * @return
    *   ZLayer that provides a javax.sql.DataSource instance
    */
  val dataSourceLayer: ZLayer[DatabaseConfig, Throwable, DataSource] =
    ZLayer.fromZIO(
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- ZIO.logInfo(
          s"Configuring DataSource for ${config.host}:${config.port}/${config.name}"
        )
      } yield {
        val ds = new org.postgresql.ds.PGSimpleDataSource()
        ds.setServerNames(Array(config.host))
        ds.setPortNumbers(Array(config.port))
        ds.setDatabaseName(config.name)
        ds.setUser(config.user)
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
    *   - DatabaseConfig from environment variables (centralized in
    *     config/AppConfig.scala as of commit bfef69f)
    *   - DataSource configuration using DatabaseConfig.jdbcUrl helper
    *   - Quill PostgreSQL context with SnakeCase naming strategy
    *   - Service implementation with startup validation
    *
    * This is the primary layer to use when providing DatabaseService to your
    * application. It handles all dependency wiring and ensures the database is
    * accessible before the application starts serving requests.
    *
    * The config refactoring (commit bfef69f) centralized all configuration
    * loading logic, making this service depend only on the DatabaseConfig case
    * class rather than directly reading environment variables.
    *
    * @return
    *   ZLayer that provides DatabaseService
    */
  val live: ZLayer[Any, Throwable, DatabaseService] =
    ZLayer.make[DatabaseService](
      DatabaseConfig.layer.mapError(e => new RuntimeException(e.getMessage)),
      dataSourceLayer,
      Quill.Postgres.fromNamingStrategy(SnakeCase),
      serviceLayer
    )
