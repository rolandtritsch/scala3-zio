package org.roland.scala3_zio_template

import org.roland.scala3_zio_template.http._
import org.roland.scala3_zio_template.service.DatabaseService

import zio._
import zio.http._
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val serverConfig = Server
    .Config
    .default
    .port(8080)
    .keepAlive(true)
    .idleTimeout(30.seconds)
    .maxHeaderSize(16 * 1024)
    .gracefulShutdownTimeout(30.seconds)

  def routes(
      shutdownPromise: Promise[Nothing, Unit]
  ): Routes[DatabaseService, Nothing] =
    Routes(
      EchoEndpoint.route,
      HealthEndpoint.route,
      HealthDeepEndpoint.route,
      RootEndpoint.route,
      ShutdownEndpoint.route(shutdownPromise)
    )

  private def performGracefulShutdown: UIO[Unit] =
    ZIO.logInfo("Shutdown signal received, stopping server...") *>
      ZIO.logInfo(
        "Waiting for in-flight requests to complete (30s timeout)..."
      ) *>
      ZIO.sleep(1.second) *>
      ZIO.logInfo("Server shutdown completed successfully") *>
      ZIO.logInfo("Exiting application...")

  def run =
    (for {
      shutdownPromise <- Promise.make[Nothing, Unit]
      allRoutes = routes(shutdownPromise)
      _ <- ZIO.logInfo("Starting server on port 8080...")
      server <- Server
        .serve(allRoutes)
        .race(shutdownPromise.await *> performGracefulShutdown)
        .onInterrupt(
          ZIO.logInfo(
            "Interrupt received, shutting down..."
          ) *> performGracefulShutdown
        )
        .fork
      _ <- server.join
    } yield ()).provide(
      ZLayer.succeed(serverConfig),
      Server.live,
      DatabaseService.live
    )
