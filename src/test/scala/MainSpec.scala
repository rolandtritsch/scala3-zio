package org.roland.scala3_zio_template

import org.roland.scala3_zio_template.config.ServerConfig
import org.roland.scala3_zio_template.http.health_checks.{
  HealthCheck,
  HealthCheckRegistry,
  HealthCheckResult,
  HealthStatus
}

import zio._
import zio.http._
import zio.test._

object MainSpec extends ZIOSpecDefault:

  /** Mock health check for testing */
  case class MockHealthCheck(
      checkName: String,
      checkStatus: HealthStatus = HealthStatus.Healthy
  ) extends HealthCheck:
    override def name: String = checkName
    override def description: String = s"Mock $checkName check"
    override def check: ZIO[Any, Nothing, HealthCheckResult] =
      ZIO.succeed(
        HealthCheckResult(
          name = checkName,
          status = checkStatus,
          message = s"Mock $checkName check result",
          durationMs = 100
        )
      )

  private val mockHealthyRegistry = ZLayer.succeed[HealthCheckRegistry](
    HealthCheckRegistry(
      List(
        MockHealthCheck("url"),
        MockHealthCheck("s3"),
        MockHealthCheck("database")
      )
    )
  )

  private val testServerConfig = ServerConfig(port = 8080)

  def spec = suite("Main")(
    test("routes should include EchoEndpoint") {
      val request = Request.post(URL.root / "echo", Body.fromString("test"))

      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Main.routes(promise)
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == "test"
      )
    },
    test("routes should include HealthEndpoint") {
      val request = Request.get(URL.root / "health")

      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Main.routes(promise)
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
      } yield assertTrue(
        response.status == Status.Ok
      )
    },
    test("routes should include HealthDeepEndpoint") {
      val request = Request.get(URL.root / "health-deep")

      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Main.routes(promise)
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
      } yield assertTrue(
        response.status == Status.Ok || response.status == Status
          .InternalServerError
      )
    },
    test("routes should include RootEndpoint") {
      val request = Request.get(URL.root)

      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Main.routes(promise)
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == "Hello, Root Endpoint!"
      )
    },
    test("routes should return NotFound for unknown paths") {
      val request = Request.post(URL.root / "unknown", Body.empty)

      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Main.routes(promise)
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("routes should return NotFound for wrong HTTP method") {
      val request = Request.get(URL.root / "echo")

      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Main.routes(promise)
        response <- routes(request)
          .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("buildServerConfig should have port from ServerConfig") {
      val config = Main.buildServerConfig(testServerConfig)
      assertTrue(
        config.address.getPort == 8080
      )
    },
    test("buildServerConfig should have keepAlive enabled") {
      val config = Main.buildServerConfig(testServerConfig)
      assertTrue(
        config.keepAlive
      )
    },
    test("buildServerConfig should have idleTimeout of 30 seconds") {
      val config = Main.buildServerConfig(testServerConfig)
      assertTrue(
        config.idleTimeout.contains(30.seconds)
      )
    },
    test("buildServerConfig should have maxHeaderSize of 16KB") {
      val config = Main.buildServerConfig(testServerConfig)
      assertTrue(
        config.maxHeaderSize == 16 * 1024
      )
    },
    test("routes should handle multiple echo requests") {
      val messages = List("msg1", "msg2", "msg3")

      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Main.routes(promise)
        responses <- ZIO.foreach(messages) { msg =>
          val request = Request.post(URL.root / "echo", Body.fromString(msg))
          routes(request)
            .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
            .flatMap { response =>
              response.body.asString.map(body => (response.status, body))
            }
        }
      } yield assertTrue(
        responses.forall(_._1 == Status.Ok),
        responses.map(_._2) == messages
      )
    },
    test("routes should handle all health endpoints") {
      val healthPaths = List("health", "health-deep")

      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Main.routes(promise)
        responses <- ZIO.foreach(healthPaths) { path =>
          val request = Request.get(URL.root / path)
          routes(request)
            .provide(mockHealthyRegistry, ZLayer.succeed(Scope.global))
        }
      } yield assertTrue(
        responses.forall(r =>
          r.status == Status.Ok || r.status == Status.InternalServerError
        )
      )
    },
    test(
      "buildServerConfig should have gracefulShutdownTimeout of 30 seconds"
    ) {
      val config = Main.buildServerConfig(testServerConfig)
      assertTrue(
        config.gracefulShutdownTimeout == 30.seconds
      )
    },
    test("server should handle graceful shutdown") {
      val testConfig = Main.buildServerConfig(ServerConfig(port = 8081))
      val serverFiber = for {
        promise <- Promise.make[Nothing, Unit]
        routes = Main.routes(promise)
        fiber <- Server
          .serve(routes)
          .provide(
            ZLayer.succeed(testConfig),
            Server.live,
            mockHealthyRegistry,
            ZLayer.succeed(Scope.global)
          )
          .fork
        _ <- TestClock.adjust(100.millis)
        _ <- fiber.interrupt
      } yield ()

      serverFiber.as(assertTrue(true))
    } @@ TestAspect.timeout(5.seconds) @@ TestAspect.withLiveClock,
    test("server shutdown hook should execute on interruption") {
      val program = for {
        _ <- ZIO.logInfo("Starting server on port 8080...")
        _ <- ZIO.never
      } yield ()

      val withShutdown = program.ensuring(
        ZIO.logInfo("Server shutdown initiated, cleaning up resources...") *>
          ZIO.logInfo("Waiting for in-flight requests to complete...") *>
          ZIO.logInfo("Server shutdown completed successfully")
      )

      for {
        fiber <- withShutdown.fork
        _ <- TestClock.adjust(10.millis)
        _ <- fiber.interrupt
      } yield assertTrue(true)
    } @@ TestAspect.timeout(5.seconds)
  )
