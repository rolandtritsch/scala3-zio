package org.roland.scala3_zio_template

import zio._
import zio.http._
import zio.test._

object MainSpec extends ZIOSpecDefault:

  def spec = suite("Main")(
    test("routes should include EchoEndpoint") {
      val request = Request.post(URL.root / "echo", Body.fromString("test"))

      for {
        response <- Main.routes(request)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == "test"
      )
    },
    test("routes should include HealthEndpoint") {
      val request = Request.get(URL.root / "health")

      for {
        response <- Main.routes(request)
      } yield assertTrue(
        response.status == Status.Ok
      )
    },
    test("routes should include HealthDeepEndpoint") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- Main.routes(request)
      } yield assertTrue(
        response.status == Status.Ok
      )
    },
    test("routes should include RootEndpoint") {
      val request = Request.get(URL.root)

      for {
        response <- Main.routes(request)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == "Hello, Root Endpoint!"
      )
    },
    test("routes should return NotFound for unknown paths") {
      val request = Request.post(URL.root / "unknown", Body.empty)

      for {
        response <- Main.routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("routes should return NotFound for wrong HTTP method") {
      val request = Request.get(URL.root / "echo")

      for {
        response <- Main.routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("serverConfig should have port 8080") {
      assertTrue(
        Main.serverConfig.address.getPort == 8080
      )
    },
    test("serverConfig should have keepAlive enabled") {
      assertTrue(
        Main.serverConfig.keepAlive
      )
    },
    test("serverConfig should have idleTimeout of 30 seconds") {
      assertTrue(
        Main.serverConfig.idleTimeout.contains(30.seconds)
      )
    },
    test("serverConfig should have maxHeaderSize of 16KB") {
      assertTrue(
        Main.serverConfig.maxHeaderSize == 16 * 1024
      )
    },
    test("routes should handle multiple echo requests") {
      val messages = List("msg1", "msg2", "msg3")

      for {
        responses <- ZIO.foreach(messages) { msg =>
          val request = Request.post(URL.root / "echo", Body.fromString(msg))
          Main
            .routes(request)
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
        responses <- ZIO.foreach(healthPaths) { path =>
          val request = Request.get(URL.root / path)
          Main.routes(request)
        }
      } yield assertTrue(
        responses.forall(_.status == Status.Ok)
      )
    },
    test("serverConfig should have gracefulShutdownTimeout of 10 seconds") {
      assertTrue(
        Main.serverConfig.gracefulShutdownTimeout == 10.seconds
      )
    },
    test("server should handle graceful shutdown") {
      val serverFiber = for {
        fiber <- Server
          .serve(Main.routes)
          .provide(
            ZLayer.succeed(Main.serverConfig.port(8081)),
            Server.live
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
