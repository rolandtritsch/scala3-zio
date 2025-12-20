package org.roland.scala3_zio_template.http.health_checks

import org.roland.scala3_zio_template.service.DatabaseService

import zio._

/** Health check that validates PostgreSQL database connectivity.
  *
  * This check performs a simple database query (SELECT 1) to verify that:
  *   - Database is reachable
  *   - Credentials are valid
  *   - Connection pool has available connections
  *
  * '''Configuration:'''
  *   - Database credentials loaded from `DATABASE_*` environment variables
  *   - See [[org.roland.scala3_zio_template.config.DatabaseConfig]] for details
  *
  * '''Timeout:''' 5 seconds to detect connection issues quickly
  *
  * '''Dependencies:'''
  *   - [[DatabaseService]]: Service that manages database connection pool
  *
  * @see
  *   [[HealthCheck]] for the core abstraction
  * @see
  *   [[org.roland.scala3_zio_template.config.DatabaseConfig]] for configuration
  *   details
  * @see
  *   [[DatabaseService]] for database service implementation
  */
trait DatabaseHealthCheck extends HealthCheck

object DatabaseHealthCheck:

  /** Live implementation of database health check.
    *
    * This implementation:
    *   - Executes DatabaseService.healthCheck() method
    *   - Applies 5-second timeout to detect connection issues
    *   - Returns healthy if query succeeds
    *   - Returns unhealthy for connection errors or timeout
    *   - Logs full error details to server logs
    *   - Returns user-friendly messages in results
    *
    * @param service
    *   Database service that provides connection pool and query execution
    */
  private final class DatabaseHealthCheckImpl(service: DatabaseService)
      extends DatabaseHealthCheck:

    override def name: String = "database"

    override def description: String =
      "Validates PostgreSQL database connectivity via SELECT 1 query"

    override def check: ZIO[Any, Nothing, HealthCheckResult] =
      val startTime = java.lang.System.currentTimeMillis()

      (for {
        _ <- service.healthCheck()
        duration = java.lang.System.currentTimeMillis() - startTime
      } yield HealthCheckResult(
        name = name,
        status = HealthStatus.Healthy,
        message = "Database connection successful",
        durationMs = duration
      )).timeout(5.seconds)
        .map {
          case Some(result) => result
          case None =>
            HealthCheckResult(
              name = name,
              status = HealthStatus.Unhealthy,
              message = "Database health check timed out after 5 seconds",
              durationMs = 5000
            )
        }
        .catchAll { error =>
          val duration = java.lang.System.currentTimeMillis() - startTime
          ZIO.logError(s"$name health check failed: ${error.getMessage}") *>
            ZIO.succeed(
              HealthCheckResult(
                name = name,
                status = HealthStatus.Unhealthy,
                message = "Database connection failed",
                durationMs = duration
              )
            )
        }

  /** ZLayer that provides a live DatabaseHealthCheck implementation.
    *
    * This layer:
    *   - Depends on [[DatabaseService]] for query execution
    *   - Constructs the check at application startup
    *
    * '''Note:''' This layer only depends on leaf services (DatabaseService),
    * not on other health checks or the registry, to avoid circular
    * dependencies.
    */
  val layer: ZLayer[DatabaseService, Nothing, DatabaseHealthCheck] =
    ZLayer.fromZIO(
      for service <- ZIO.service[DatabaseService]
      yield DatabaseHealthCheckImpl(service)
    )
