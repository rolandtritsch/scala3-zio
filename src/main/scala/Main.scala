package org.roland.scala3_zio_template

import org.roland.scala3_zio_template.http._
import org.roland.scala3_zio_template.service.DatabaseService

import zio._
import zio.http._
import zio.logging.backend.SLF4J

/** Main application entry point for the ZIO HTTP server.
  *
  * This application provides a production-ready HTTP service with the following features:
  *   - RESTful endpoints for echo, health checks, and graceful shutdown
  *   - PostgreSQL database integration with connection pooling
  *   - Structured JSON logging via SLF4J
  *   - Graceful shutdown coordination with configurable timeout
  *   - Request/response logging with timing metrics
  *
  * The server runs on port 8080 with a 30-second graceful shutdown timeout.
  * All endpoints are defined in the `http` package and registered in the `routes` method.
  *
  * @see [[http.EchoEndpoint]] for request echo functionality
  * @see [[http.HealthEndpoint]] for basic health checks
  * @see [[http.HealthDeepEndpoint]] for comprehensive health checks
  * @see [[service.DatabaseService]] for database operations
  */
object Main extends ZIOAppDefault:

  /** Bootstrap layer that configures structured JSON logging via SLF4J.
    *
    * Removes default console loggers and replaces them with SLF4J/Logback,
    * which provides structured JSON output configured in logback.xml.
    */
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  /** Server configuration with production-ready settings.
    *
    * Configuration includes:
    *   - Port 8080 for HTTP traffic
    *   - Keep-alive connections enabled for better performance
    *   - 30-second idle timeout for inactive connections
    *   - 16 KB maximum header size to prevent header attacks
    *   - 30-second graceful shutdown timeout for clean termination
    */
  val serverConfig = Server
    .Config
    .default
    .port(8080)
    .keepAlive(true)
    .idleTimeout(30.seconds)
    .maxHeaderSize(16 * 1024)
    .gracefulShutdownTimeout(30.seconds)

  /** Constructs the complete route table for the HTTP server.
    *
    * All application endpoints are registered here and combined into a single Routes object.
    * The shutdown promise is passed to the shutdown endpoint to enable graceful termination.
    *
    * @param shutdownPromise Promise that will be completed when shutdown is requested via POST /shutdown
    * @return Combined routes requiring DatabaseService dependency
    */
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

  /** Performs graceful shutdown sequence with logging.
    *
    * This method handles the shutdown process by:
    *   1. Logging the shutdown initiation
    *   2. Waiting briefly (1 second) for in-flight requests to complete
    *   3. Logging successful completion
    *   4. Preparing for application exit
    *
    * The 1-second delay allows the HTTP server to finish processing any requests
    * that were in flight when shutdown was triggered.
    *
    * @return ZIO effect that performs shutdown sequence and never fails
    */
  private def performGracefulShutdown: UIO[Unit] =
    ZIO.logInfo("Shutdown signal received, stopping server...") *>
      ZIO.logInfo(
        "Waiting for in-flight requests to complete (30s timeout)..."
      ) *>
      ZIO.sleep(1.second) *>
      ZIO.logInfo("Server shutdown completed successfully") *>
      ZIO.logInfo("Exiting application...")

  /** Main application logic that starts the HTTP server and handles shutdown.
    *
    * This method orchestrates the entire application lifecycle:
    *   1. Creates a shutdown promise for coordination
    *   2. Configures all HTTP routes
    *   3. Starts the HTTP server on port 8080
    *   4. Races server execution against shutdown signal
    *   5. Handles interrupt signals (Ctrl+C) with graceful shutdown
    *   6. Provides all required dependencies (Server, DatabaseService)
    *
    * The server will continue running until either:
    *   - A POST request is made to /shutdown endpoint
    *   - An interrupt signal (Ctrl+C) is received
    *
    * Both cases trigger graceful shutdown with a 30-second timeout.
    *
    * @return ZIO effect that runs the complete application
    */
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
