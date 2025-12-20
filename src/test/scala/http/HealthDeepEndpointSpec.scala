package org.roland.scala3_zio_template.http

import org.roland.scala3_zio_template.http.health_checks.{
  HealthCheck,
  HealthCheckRegistry,
  HealthCheckResult,
  HealthStatus
}

import zio._
import zio.http._
import zio.json._
import zio.test._

/** Test suite for HealthDeepEndpoint.
  *
  * This test suite verifies the refactored health check endpoint that uses the
  * pluggable HealthCheckRegistry architecture. Tests provide mock health checks
  * via ZLayer.succeed to enable isolated, deterministic testing.
  *
  * The new response format uses an array-based structure:
  * {{{
  * {
  *   "checks": [
  *     {"name": "url", "status": "healthy", "message": "...", "durationMs": 245},
  *     {"name": "s3", "status": "healthy", "message": "...", "durationMs": 523},
  *     {"name": "database", "status": "healthy", "message": "...", "durationMs": 87}
  *   ],
  *   "overallStatus": "healthy",
  *   "totalDurationMs": 523
  * }
  * }}}
  */
object HealthDeepEndpointSpec extends ZIOSpecDefault:

  /** Mock health check implementation for testing.
    *
    * @param checkName
    *   Name of the check (e.g., "url", "s3", "database")
    * @param checkStatus
    *   Status to return (Healthy or Unhealthy)
    * @param checkMessage
    *   Message to include in the result
    * @param checkDuration
    *   Duration in milliseconds (for timing tests)
    */
  case class MockHealthCheck(
      checkName: String,
      checkStatus: HealthStatus,
      checkMessage: String,
      checkDuration: Long = 100
  ) extends HealthCheck:
    override def name: String = checkName
    override def description: String = s"Mock health check for $checkName"
    override def check: ZIO[Any, Nothing, HealthCheckResult] =
      ZIO.succeed(
        HealthCheckResult(
          name = checkName,
          status = checkStatus,
          message = checkMessage,
          durationMs = checkDuration
        )
      )

  /** Creates a mock registry with all healthy checks */
  private def mockHealthyRegistry = ZLayer.succeed[HealthCheckRegistry](
    HealthCheckRegistry(
      List(
        MockHealthCheck(
          "url",
          HealthStatus.Healthy,
          "URL returned status 200",
          245
        ),
        MockHealthCheck(
          "s3",
          HealthStatus.Healthy,
          "Successfully listed 5 bucket(s)",
          523
        ),
        MockHealthCheck(
          "database",
          HealthStatus.Healthy,
          "Database connection successful",
          87
        )
      )
    )
  )

  /** Creates a mock registry with one unhealthy check (S3) */
  private def mockUnhealthyRegistry = ZLayer.succeed[HealthCheckRegistry](
    HealthCheckRegistry(
      List(
        MockHealthCheck("url", HealthStatus.Healthy, "URL returned status 200"),
        MockHealthCheck(
          "s3",
          HealthStatus.Unhealthy,
          "S3 check failed: check credentials"
        ),
        MockHealthCheck(
          "database",
          HealthStatus.Healthy,
          "Database connection successful"
        )
      )
    )
  )

  private def routes = Routes(HealthDeepEndpoint.route)

  // JSON decoders for the new response format
  case class HealthCheckResultJson(
      name: String,
      status: String,
      message: String,
      durationMs: Long
  )

  object HealthCheckResultJson:
    given JsonDecoder[HealthCheckResultJson] = DeriveJsonDecoder
      .gen[HealthCheckResultJson]

  case class HealthCheckResponse(
      checks: List[HealthCheckResultJson],
      overallStatus: String,
      totalDurationMs: Long
  )

  object HealthCheckResponse:
    given JsonDecoder[HealthCheckResponse] = DeriveJsonDecoder
      .gen[HealthCheckResponse]

  def spec = suite("HealthDeepEndpoint")(
    test(
      "should respond with JSON body containing checks array with url, s3, and database"
    ) {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        result.checks.size == 3,
        result.checks.exists(_.name == "url"),
        result.checks.exists(_.name == "s3"),
        result.checks.exists(_.name == "database"),
        result
          .checks
          .forall(c => c.status == "healthy" || c.status == "unhealthy"),
        result.checks.forall(_.message.nonEmpty),
        result.checks.forall(_.durationMs >= 0)
      )
    },
    test("should include overallStatus field") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        result.overallStatus == "healthy" || result.overallStatus == "unhealthy"
      )
    },
    test("should include totalDurationMs field") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        result.totalDurationMs >= 0,
        // Total duration should be max of individual durations since checks run in parallel
        result.totalDurationMs == result.checks.map(_.durationMs).max
      )
    },
    test("should return HTTP 200 when all checks are healthy") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        response.status == Status.Ok,
        result.overallStatus == "healthy",
        result.checks.forall(_.status == "healthy")
      )
    },
    test("should return HTTP 500 when any check is unhealthy") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockUnhealthyRegistry, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        response.status == Status.InternalServerError,
        result.overallStatus == "unhealthy",
        result.checks.exists(_.status == "unhealthy")
      )
    },
    test("should include proper status field values (healthy or unhealthy)") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        result
          .checks
          .forall(c => Set("healthy", "unhealthy").contains(c.status)),
        Set("healthy", "unhealthy").contains(result.overallStatus)
      )
    },
    test("should include non-empty messages for all checks") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        result.checks.forall(_.message.nonEmpty)
      )
    },
    test("should not respond to POST requests") {
      val request = Request.post(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PUT requests") {
      val request = Request.put(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to DELETE requests") {
      val request = Request.delete(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PATCH requests") {
      val request = Request.patch(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should handle rapid successive requests") {
      val request = Request.get(URL.root / "health-deep")

      for {
        responses <- ZIO.collectAll(
          List.fill(3)(
            routes(request)
              .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
          )
        )
      } yield assertTrue(
        responses.forall(r =>
          r.status == Status.Ok || r.status == Status.InternalServerError
        )
      )
    }
  ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(60.seconds)
