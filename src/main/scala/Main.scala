package org.roland.scala3_zio_template

import org.roland.scala3_zio_template.http._

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

  def routes(shutdownPromise: Promise[Nothing, Unit]): Routes[Any, Nothing] =
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

  // $COVERAGE-OFF$ JVM shutdown hooks cannot be tested in unit tests
  private def registerShutdownHook(
      shutdownPromise: Promise[Nothing, Unit]
  ): UIO[Unit] =
    ZIO.logInfo("Registering JVM shutdown hook for SIGTERM/SIGINT...") *>
      ZIO.succeed {
        java
          .lang
          .Runtime
          .getRuntime
          .addShutdownHook(
            new Thread(new Runnable {
              def run(): Unit = {
                // Complete the promise using fire-and-forget
                // This avoids blocking on the runtime during JVM shutdown
                zio
                  .Unsafe
                  .unsafe { implicit u =>
                    zio.Runtime.default.unsafe.fork(shutdownPromise.succeed(()))
                    ()
                  }
              }
            })
          )
      }
  // $COVERAGE-ON$

  def run =
    (for {
      shutdownPromise <- Promise.make[Nothing, Unit]
      // Only register shutdown hook in production (not when running via sbt)
      // In dev mode (sbt run), CTRL-C interrupts the fiber directly
      _ <- ZIO
        .succeed(java.lang.System.getProperty("sun.java.command", ""))
        .flatMap { (cmd: String) =>
          if (cmd.contains("sbt.Run")) {
            ZIO.logInfo(
              "Running in SBT mode - CTRL-C will interrupt fiber directly"
            )
          } else {
            registerShutdownHook(shutdownPromise)
          }
        }
      allRoutes = routes(shutdownPromise)
      _ <- ZIO.logInfo("Starting server on port 8080...")
      _ <- Server
        .serve(allRoutes)
        .race(shutdownPromise.await *> ZIO.logInfo("Shutdown initiated..."))
    } yield ())
      .ensuring(performGracefulShutdown)
      .provide(
        ZLayer.succeed(serverConfig),
        Server.live
      )
