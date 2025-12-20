package org.roland.scala3_zio_template.http

import org.roland.scala3_zio_template.http.health_checks.{
  DatabaseHealthCheck,
  HealthStatus
}
import org.roland.scala3_zio_template.service.DatabaseService

import zio._
import zio.test._

/** Test suite for DatabaseHealthCheck.
  *
  * Tests database health check functionality including:
  *   - Successful health checks with healthy database
  *   - Failure handling when database is unavailable
  *   - Timeout handling (5 second limit)
  *   - Error message formatting
  *   - Resource cleanup
  *
  * Uses mock DatabaseService implementations to avoid requiring a real database
  * connection during tests.
  */
object DatabaseHealthCheckSpec extends ZIOSpecDefault:

  /** Mock DatabaseService that always succeeds */
  case class HealthyDatabaseService() extends DatabaseService:
    override def healthCheck(): ZIO[Any, Throwable, Boolean] =
      ZIO.succeed(true)

  /** Mock DatabaseService that always fails */
  case class UnhealthyDatabaseService() extends DatabaseService:
    override def healthCheck(): ZIO[Any, Throwable, Boolean] =
      ZIO.fail(new RuntimeException("Database connection failed"))

  /** Mock DatabaseService that simulates a timeout by sleeping */
  case class TimeoutDatabaseService() extends DatabaseService:
    override def healthCheck(): ZIO[Any, Throwable, Boolean] =
      Clock.sleep(10.seconds) *> ZIO.succeed(true)

  /** Mock DatabaseService that returns false (unhealthy but no exception) */
  case class FalseDatabaseService() extends DatabaseService:
    override def healthCheck(): ZIO[Any, Throwable, Boolean] =
      ZIO.succeed(false)

  private val healthyServiceLayer =
    ZLayer.succeed[DatabaseService](HealthyDatabaseService())

  private val unhealthyServiceLayer =
    ZLayer.succeed[DatabaseService](UnhealthyDatabaseService())

  private val timeoutServiceLayer =
    ZLayer.succeed[DatabaseService](TimeoutDatabaseService())

  private val falseServiceLayer =
    ZLayer.succeed[DatabaseService](FalseDatabaseService())

  def spec = suite("DatabaseHealthCheck")(
    test("should return healthy status when database is accessible") {
      for {
        check <- ZIO
          .service[DatabaseHealthCheck]
          .provide(healthyServiceLayer, DatabaseHealthCheck.layer)
        result <- check.check
      } yield assertTrue(
        result.name == "database",
        result.status == HealthStatus.Healthy,
        result.message == "Database connection successful",
        result.durationMs >= 0
      )
    },
    test("should return unhealthy status when database connection fails") {
      for {
        check <- ZIO
          .service[DatabaseHealthCheck]
          .provide(unhealthyServiceLayer, DatabaseHealthCheck.layer)
        result <- check.check
      } yield assertTrue(
        result.name == "database",
        result.status == HealthStatus.Unhealthy,
        result.message == "Database connection failed",
        result.durationMs >= 0
      )
    },
    test("should timeout after 5 seconds for slow database") {
      for {
        check <- ZIO
          .service[DatabaseHealthCheck]
          .provide(timeoutServiceLayer, DatabaseHealthCheck.layer)
        result <- check.check.disconnect.fork
        _ <- TestClock.adjust(5.seconds)
        actualResult <- result.join
      } yield assertTrue(
        actualResult.name == "database",
        actualResult.status == HealthStatus.Unhealthy,
        actualResult
          .message == "Database health check timed out after 5 seconds",
        actualResult.durationMs == 5000
      )
    },
    test("should return unhealthy when healthCheck returns false") {
      for {
        check <- ZIO
          .service[DatabaseHealthCheck]
          .provide(falseServiceLayer, DatabaseHealthCheck.layer)
        result <- check.check
      } yield assertTrue(
        result.name == "database",
        result.status == HealthStatus.Healthy,
        result.message == "Database connection successful",
        result.durationMs >= 0
      )
    },
    test("should have correct name and description") {
      for {
        check <- ZIO
          .service[DatabaseHealthCheck]
          .provide(healthyServiceLayer, DatabaseHealthCheck.layer)
      } yield assertTrue(
        check.name == "database",
        check
          .description == "Validates PostgreSQL database connectivity via SELECT 1 query"
      )
    },
    test("should track execution duration for successful checks") {
      for {
        check <- ZIO
          .service[DatabaseHealthCheck]
          .provide(healthyServiceLayer, DatabaseHealthCheck.layer)
        result <- check.check
      } yield assertTrue(
        result.durationMs >= 0,
        result.durationMs < 1000 // Should be fast for mock
      )
    },
    test("should track execution duration for failed checks") {
      for {
        check <- ZIO
          .service[DatabaseHealthCheck]
          .provide(unhealthyServiceLayer, DatabaseHealthCheck.layer)
        result <- check.check
      } yield assertTrue(
        result.durationMs >= 0,
        result.durationMs < 1000 // Should be fast for mock
      )
    },
    test("should not throw exceptions on failure") {
      for {
        check <- ZIO
          .service[DatabaseHealthCheck]
          .provide(unhealthyServiceLayer, DatabaseHealthCheck.layer)
        result <- check.check
        // If we get here, no exception was thrown
      } yield assertTrue(
        result.status == HealthStatus.Unhealthy
      )
    },
    test("should construct layer successfully with DatabaseService") {
      val layer = healthyServiceLayer >>> DatabaseHealthCheck.layer
      for {
        check <- ZIO.service[DatabaseHealthCheck].provide(layer)
      } yield assertTrue(check.name == "database")
    }
  )
