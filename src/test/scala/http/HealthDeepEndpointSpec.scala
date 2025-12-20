package org.roland.scala3_zio_template.http

import org.roland.scala3_zio_template.config.AwsConfig
import org.roland.scala3_zio_template.service.DatabaseService

import zio._
import zio.http._
import zio.json._
import zio.test._

/** Test suite for HealthDeepEndpoint.
  *
  * This test demonstrates the config refactoring approach (commit bfef69f)
  * where tests provide configuration via ZLayer.succeed rather than environment
  * variables. Mock services and configurations are created directly and
  * provided via ZLayers:
  *   - mockDbLayer: Provides a mock DatabaseService for testing
  *   - mockAwsLayer: Provides a mock AwsConfig for testing S3 integration
  *
  * This approach ensures test isolation, determinism, and simplicity. Tests can
  * provide different configurations for different test cases without affecting
  * each other or depending on the environment.
  */
object HealthDeepEndpointSpec extends ZIOSpecDefault:

  case class MockDatabaseService(shouldSucceed: Boolean)
      extends DatabaseService:
    override def healthCheck(): ZIO[Any, Throwable, Boolean] =
      if shouldSucceed then ZIO.succeed(true)
      else ZIO.fail(new RuntimeException("Mock database failure"))

  // Mock layers demonstrating the config refactoring approach
  private val mockDbLayer = ZLayer
    .succeed[DatabaseService](MockDatabaseService(shouldSucceed = true))

  private val mockAwsLayer = ZLayer.succeed[AwsConfig](
    AwsConfig("test-access-key", "test-secret-key", "us-east-1")
  )

  private def routes = Routes(HealthDeepEndpoint.route)

  case class ServiceHealthCheck(
      status: String,
      message: String
  )

  object ServiceHealthCheck:
    given JsonDecoder[ServiceHealthCheck] = DeriveJsonDecoder
      .gen[ServiceHealthCheck]

  case class HealthCheckResponse(
      url: ServiceHealthCheck,
      s3: ServiceHealthCheck,
      database: ServiceHealthCheck
  )

  object HealthCheckResponse:
    given JsonDecoder[HealthCheckResponse] = DeriveJsonDecoder
      .gen[HealthCheckResponse]

  def spec = suite("HealthDeepEndpoint")(
    test(
      "should respond with JSON body containing url, s3, and database checks"
    ) {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockDbLayer, mockAwsLayer, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        result.url.status == "healthy" || result.url.status == "unhealthy",
        result.s3.status == "healthy" || result.s3.status == "unhealthy",
        result.database.status == "healthy" || result
          .database
          .status == "unhealthy",
        result.url.message.nonEmpty,
        result.s3.message.nonEmpty,
        result.database.message.nonEmpty
      )
    },
    test("should respond with appropriate status code") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockDbLayer, mockAwsLayer, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        response.status == Status.Ok || response.status == Status
          .InternalServerError,
        // If 500, at least one check should be unhealthy
        response.status != Status.InternalServerError ||
          result.url.status == "unhealthy" || result
            .s3
            .status == "unhealthy" || result.database.status == "unhealthy"
      )
    },
    test("should include proper status field values") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockDbLayer, mockAwsLayer, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        Set("healthy", "unhealthy").contains(result.url.status),
        Set("healthy", "unhealthy").contains(result.s3.status),
        Set("healthy", "unhealthy").contains(result.database.status)
      )
    },
    test("should include non-empty messages for all checks") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockDbLayer, mockAwsLayer, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        result.url.message.nonEmpty,
        result.s3.message.nonEmpty,
        result.database.message.nonEmpty
      )
    },
    test("should include database check in response") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockDbLayer, mockAwsLayer, ZLayer.succeed(Scope.global))
        body <- response.body.asString
        result <- ZIO.fromEither(body.fromJson[HealthCheckResponse])
      } yield assertTrue(
        result.database.status == "healthy" || result
          .database
          .status == "unhealthy",
        result.database.message.nonEmpty
      )
    },
    test("should not respond to POST requests") {
      val request = Request.post(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
          .provide(mockDbLayer, mockAwsLayer, ZLayer.succeed(Scope.global))
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PUT requests") {
      val request = Request.put(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
          .provide(mockDbLayer, mockAwsLayer, ZLayer.succeed(Scope.global))
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to DELETE requests") {
      val request = Request.delete(URL.root / "health-deep")

      for {
        response <- routes(request)
          .provide(mockDbLayer, mockAwsLayer, ZLayer.succeed(Scope.global))
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PATCH requests") {
      val request = Request.patch(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
          .provide(mockDbLayer, mockAwsLayer, ZLayer.succeed(Scope.global))
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
              .provide(mockDbLayer, mockAwsLayer, ZLayer.succeed(Scope.global))
          )
        )
      } yield assertTrue(
        responses.forall(r =>
          r.status == Status.Ok || r.status == Status.InternalServerError
        )
      )
    }
  ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(60.seconds)
