package org.roland.scala3_zio_template.http

import org.roland.scala3_zio_template.http.health_checks.{
  HealthCheck,
  HealthCheckRegistry,
  HealthCheckResponse,
  HealthCheckResult,
  HealthStatus
}

import zio._
import zio.test._

/** Test suite for HealthCheckRegistry.
  *
  * Tests the health check registry functionality including:
  *   - Running multiple health checks in parallel
  *   - Aggregating results into overall status
  *   - Handling timeouts (global 15-second timeout)
  *   - Empty registry handling
  *   - Response construction from results
  */
object HealthCheckRegistrySpec extends ZIOSpecDefault:

  /** Mock health check that always returns healthy */
  case class AlwaysHealthyCheck(checkName: String) extends HealthCheck:
    override def name: String = checkName
    override def description: String = s"Mock healthy check: $checkName"
    override def check: ZIO[Any, Nothing, HealthCheckResult] =
      ZIO.succeed(
        HealthCheckResult(
          name = checkName,
          status = HealthStatus.Healthy,
          message = s"$checkName is healthy",
          durationMs = 100
        )
      )

  /** Mock health check that always returns unhealthy */
  case class AlwaysUnhealthyCheck(checkName: String) extends HealthCheck:
    override def name: String = checkName
    override def description: String = s"Mock unhealthy check: $checkName"
    override def check: ZIO[Any, Nothing, HealthCheckResult] =
      ZIO.succeed(
        HealthCheckResult(
          name = checkName,
          status = HealthStatus.Unhealthy,
          message = s"$checkName is unhealthy",
          durationMs = 150
        )
      )

  /** Mock health check that sleeps to test timeouts */
  case class SlowCheck(checkName: String, sleepDuration: Duration)
      extends HealthCheck:
    override def name: String = checkName
    override def description: String = s"Mock slow check: $checkName"
    override def check: ZIO[Any, Nothing, HealthCheckResult] =
      Clock.sleep(sleepDuration) *> ZIO.succeed(
        HealthCheckResult(
          name = checkName,
          status = HealthStatus.Healthy,
          message = s"$checkName completed",
          durationMs = sleepDuration.toMillis
        )
      )

  def spec = suite("HealthCheckRegistry")(
    suite("runAllChecks")(
      test("should run all checks in parallel and collect results") {
        val registry = HealthCheckRegistry(
          List(
            AlwaysHealthyCheck("check-1"),
            AlwaysHealthyCheck("check-2"),
            AlwaysHealthyCheck("check-3")
          )
        )

        for {
          results <- registry.runAllChecks
        } yield assertTrue(
          results.size == 3,
          results.forall(_.status == HealthStatus.Healthy),
          results.map(_.name).toSet == Set("check-1", "check-2", "check-3")
        )
      },
      test("should return empty list when no checks registered") {
        val registry = HealthCheckRegistry(List.empty)

        for {
          results <- registry.runAllChecks
        } yield assertTrue(results.isEmpty)
      },
      test("should continue running all checks even if some fail") {
        val registry = HealthCheckRegistry(
          List(
            AlwaysHealthyCheck("healthy-check"),
            AlwaysUnhealthyCheck("unhealthy-check"),
            AlwaysHealthyCheck("another-healthy-check")
          )
        )

        for {
          results <- registry.runAllChecks
        } yield assertTrue(
          results.size == 3,
          results.count(_.status == HealthStatus.Healthy) == 2,
          results.count(_.status == HealthStatus.Unhealthy) == 1
        )
      },
      test("should apply global 15-second timeout") {
        val registry = HealthCheckRegistry(
          List(
            SlowCheck("slow-check", 20.seconds)
          )
        )

        for {
          startTime <- Clock
            .currentTime(java.util.concurrent.TimeUnit.MILLISECONDS)
          fiber <- registry.runAllChecks.disconnect.fork
          _ <- TestClock.adjust(15.seconds)
          results <- fiber.join
          endTime <- Clock
            .currentTime(java.util.concurrent.TimeUnit.MILLISECONDS)
          duration = endTime - startTime
        } yield assertTrue(
          results.isEmpty, // Timeout returns empty list
          duration <= 15000
        )
      },
      test("should complete before timeout for fast checks") {
        val registry = HealthCheckRegistry(
          List(
            AlwaysHealthyCheck("fast-check-1"),
            AlwaysHealthyCheck("fast-check-2")
          )
        )

        for {
          results <- registry.runAllChecks
        } yield assertTrue(
          results.size == 2,
          results.forall(_.status == HealthStatus.Healthy)
        )
      }
    ),
    suite("HealthCheckResponse")(
      test("fromResults should aggregate healthy status when all checks pass") {
        val results = List(
          HealthCheckResult("check-1", HealthStatus.Healthy, "OK", 100),
          HealthCheckResult("check-2", HealthStatus.Healthy, "OK", 150),
          HealthCheckResult("check-3", HealthStatus.Healthy, "OK", 200)
        )

        val response = HealthCheckResponse.fromResults(results)

        assertTrue(
          response.overallStatus == HealthStatus.Healthy,
          response.checks.size == 3,
          response.totalDurationMs == 200 // Max duration
        )
      },
      test(
        "fromResults should aggregate unhealthy status when any check fails"
      ) {
        val results = List(
          HealthCheckResult("check-1", HealthStatus.Healthy, "OK", 100),
          HealthCheckResult("check-2", HealthStatus.Unhealthy, "Failed", 150),
          HealthCheckResult("check-3", HealthStatus.Healthy, "OK", 120)
        )

        val response = HealthCheckResponse.fromResults(results)

        assertTrue(
          response.overallStatus == HealthStatus.Unhealthy,
          response.checks.size == 3,
          response.totalDurationMs == 150 // Max duration
        )
      },
      test("fromResults should handle empty results list") {
        val response = HealthCheckResponse.fromResults(List.empty)

        assertTrue(
          response.overallStatus == HealthStatus.Unhealthy,
          response.checks.isEmpty,
          response.totalDurationMs == 0
        )
      },
      test("fromResults should calculate max duration correctly") {
        val results = List(
          HealthCheckResult("check-1", HealthStatus.Healthy, "OK", 500),
          HealthCheckResult("check-2", HealthStatus.Healthy, "OK", 200),
          HealthCheckResult("check-3", HealthStatus.Healthy, "OK", 1000),
          HealthCheckResult("check-4", HealthStatus.Healthy, "OK", 300)
        )

        val response = HealthCheckResponse.fromResults(results)

        assertTrue(
          response.totalDurationMs == 1000
        )
      },
      test("fromResults should preserve all check results") {
        val results = List(
          HealthCheckResult("url", HealthStatus.Healthy, "URL OK", 245),
          HealthCheckResult("s3", HealthStatus.Healthy, "S3 OK", 523),
          HealthCheckResult("database", HealthStatus.Unhealthy, "DB Error", 87)
        )

        val response = HealthCheckResponse.fromResults(results)

        assertTrue(
          response.checks.size == 3,
          response.checks.exists(_.name == "url"),
          response.checks.exists(_.name == "s3"),
          response.checks.exists(_.name == "database"),
          response.overallStatus == HealthStatus.Unhealthy
        )
      }
    )
  )
