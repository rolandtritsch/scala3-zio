package org.roland.scala3_zio_template.http

import zio._
import zio.http._
import zio.json._
import zio.test._

object HealthDeepEndpointSpec extends ZIOSpecDefault:

  private val routes = Routes(HealthDeepEndpoint.route)

  case class ServiceHealthCheck(
      status: String,
      message: String
  )

  object ServiceHealthCheck:
    given JsonDecoder[ServiceHealthCheck] = DeriveJsonDecoder
      .gen[ServiceHealthCheck]

  case class HealthCheckResponse(
      url: ServiceHealthCheck,
      s3: ServiceHealthCheck
  )

  object HealthCheckResponse:
    given JsonDecoder[HealthCheckResponse] = DeriveJsonDecoder
      .gen[HealthCheckResponse]

  def spec = suite("HealthDeepEndpoint")(
    test("should respond with JSON body containing both url and s3 checks") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        result.url.status == "healthy" || result.url.status == "unhealthy",
        result.s3.status == "healthy" || result.s3.status == "unhealthy",
        result.url.message.nonEmpty,
        result.s3.message.nonEmpty
      )
    },
    test("should respond with appropriate status code") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        response.status == Status.Ok || response.status == Status
          .InternalServerError,
        // If 500, at least one check should be unhealthy
        response.status != Status.InternalServerError ||
          result.url.status == "unhealthy" || result.s3.status == "unhealthy"
      )
    },
    test("should include proper status field values") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        Set("healthy", "unhealthy").contains(result.url.status),
        Set("healthy", "unhealthy").contains(result.s3.status)
      )
    },
    test("should include non-empty messages for both checks") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        result.url.message.nonEmpty,
        result.s3.message.nonEmpty
      )
    },
    test("should not respond to POST requests") {
      val request = Request.post(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PUT requests") {
      val request = Request.put(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to DELETE requests") {
      val request = Request.delete(URL.root / "health-deep")

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PATCH requests") {
      val request = Request.patch(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should handle rapid successive requests") {
      val request = Request.get(URL.root / "health-deep")

      for {
        responses <- ZIO.collectAll(
          List.fill(3)(routes(request))
        )
      } yield assertTrue(
        responses.forall(r =>
          r.status == Status.Ok || r.status == Status.InternalServerError
        )
      )
    }
  ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(60.seconds)
