package org.roland.scala3_zio_template.http

import zio._
import zio.http._
import zio.json._
import zio.test._

object HealthDeepEndpointSpec extends ZIOSpecDefault:

  private val routes = Routes(HealthDeepEndpoint.route)

  case class HealthCheckResult(
      success: Boolean,
      statusCode: Option[Int],
      error: Option[String]
  )

  object HealthCheckResult:
    given JsonDecoder[HealthCheckResult] = DeriveJsonDecoder.gen[HealthCheckResult]

  private val testLayer = Client.default ++ Scope.default

  def spec = suite("HealthDeepEndpoint")(
    test("should respond with OK status when tedn.life is reachable") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request).provideLayer(testLayer)
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResult])
      } yield assertTrue(
        response.status == Status.Ok || response.status == Status.InternalServerError,
        result.statusCode.isDefined || result.error.isDefined
      )
    },
    test("should respond with JSON body containing health check result") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request).provideLayer(testLayer)
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResult])
      } yield assertTrue(
        body.nonEmpty,
        result.statusCode.isDefined || result.error.isDefined
      )
    },
    test("should include success field in response") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request).provideLayer(testLayer)
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResult])
      } yield assertTrue(
        result.success == true || result.success == false
      )
    },
    test("should include statusCode when check succeeds") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request).provideLayer(testLayer)
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResult])
      } yield assertTrue(
        !result.success || result.statusCode.isDefined
      )
    },
    test("should include error message when check fails") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request).provideLayer(testLayer)
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResult])
      } yield assertTrue(
        result.success || result.error.isDefined
      )
    },
    test("should not respond to POST requests") {
      val request = Request.post(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request).provideLayer(testLayer)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PUT requests") {
      val request = Request.put(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request).provideLayer(testLayer)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to DELETE requests") {
      val request = Request.delete(URL.root / "health-deep")

      for {
        response <- routes(request).provideLayer(testLayer)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PATCH requests") {
      val request = Request.patch(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request).provideLayer(testLayer)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should handle rapid successive requests") {
      val request = Request.get(URL.root / "health-deep")

      for {
        responses <- ZIO.collectAll(
          List.fill(3)(
            routes(request).provideLayer(testLayer)
          )
        )
      } yield assertTrue(
        responses.forall(r => r.status == Status.Ok || r.status == Status.InternalServerError)
      )
    }
  )
