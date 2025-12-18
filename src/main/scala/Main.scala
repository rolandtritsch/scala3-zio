package org.roland.scala3_zio_template

import zio._
import zio.http._
import zio.logging.backend.SLF4J

import org.roland.scala3_zio_template.http._

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
    .gracefulShutdownTimeout(10.seconds)

  val routes = Routes(
    EchoEndpoint.route,
    HealthEndpoint.route,
    HealthDeepEndpoint.route,
    RootEndpoint.route
  )

  private val shutdownHook: UIO[Unit] =
    ZIO.logInfo("Server shutdown initiated, cleaning up resources...") *>
    ZIO.logInfo("Waiting for in-flight requests to complete...") *>
    ZIO.logInfo("Server shutdown completed successfully")

  def run =
    (ZIO.logInfo("Starting server on port 8080...") *>
    Server.serve(routes))
      .ensuring(shutdownHook)
      .provide(
        ZLayer.succeed(serverConfig),
        Server.live
      )
