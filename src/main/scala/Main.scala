package org.roland.scala3_zio_template

import org.roland.scala3_zio_template.config.{
  AwsConfig,
  HealthCheckConfig,
  ServerConfig
}
import org.roland.scala3_zio_template.http._
import org.roland.scala3_zio_template.http.health_checks.{
  DatabaseHealthCheck,
  HealthCheckRegistry,
  S3HealthCheck,
  UrlHealthCheck
}
import org.roland.scala3_zio_template.service.DatabaseService

import zio._
import zio.http._
import zio.logging.backend.SLF4J

/** Main application entry point for the ZIO HTTP server.
  *
  * This application provides a production-ready HTTP service with the following
  * features:
  *   - RESTful endpoints for echo, health checks, and graceful shutdown
  *   - PostgreSQL database integration with connection pooling
  *   - Structured JSON logging via SLF4J
  *   - Graceful shutdown coordination with configurable timeout
  *   - Request/response logging with timing metrics
  *
  * The server runs on port 8080 with a 30-second graceful shutdown timeout. All
  * endpoints are defined in the `http` package and registered in the `routes`
  * method.
  *
  * @see
  *   [[http.EchoEndpoint]] for request echo functionality
  * @see
  *   [[http.HealthEndpoint]] for basic health checks
  * @see
  *   [[http.HealthDeepEndpoint]] for comprehensive health checks
  * @see
  *   [[service.DatabaseService]] for database operations
  */
object Main extends ZIOAppDefault:

  /** Bootstrap layer that configures structured JSON logging via SLF4J.
    *
    * Removes default console loggers and replaces them with SLF4J/Logback,
    * which provides structured JSON output configured in logback.xml.
    */
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  /** Builds server configuration from ServerConfig.
    *
    * Configuration includes:
    *   - Configurable port from SERVER_PORT environment variable (default:
    *     8080)
    *   - Keep-alive connections enabled for better performance
    *   - 30-second idle timeout for inactive connections
    *   - 16 KB maximum header size to prevent header attacks
    *   - 30-second graceful shutdown timeout for clean termination
    */
  def buildServerConfig(config: ServerConfig): Server.Config =
    Server
      .Config
      .default
      .port(config.port)
      .keepAlive(true)
      .idleTimeout(30.seconds)
      .maxHeaderSize(16 * 1024)
      .gracefulShutdownTimeout(30.seconds)

  /** Constructs the complete route table for the HTTP server.
    *
    * All application endpoints are registered here and combined into a single
    * Routes object. The shutdown promise is passed to the shutdown endpoint to
    * enable graceful termination.
    *
    * @param shutdownPromise
    *   Promise that will be completed when shutdown is requested via POST
    *   /shutdown
    * @return
    *   Combined routes requiring HealthCheckRegistry dependency
    */
  def routes(
      shutdownPromise: Promise[Nothing, Unit]
  ): Routes[HealthCheckRegistry, Nothing] =
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
    *   1. Logging the shutdown initiation 2. Waiting briefly (1 second) for
    *      in-flight requests to complete 3. Logging successful completion 4.
    *      Preparing for application exit
    *
    * The 1-second delay allows the HTTP server to finish processing any
    * requests that were in flight when shutdown was triggered.
    *
    * @return
    *   ZIO effect that performs shutdown sequence and never fails
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
    *   1. Loads server configuration from environment variables 2. Creates a
    *      shutdown promise for coordination 3. Configures all HTTP routes 4.
    *      Starts the HTTP server on configured port 5. Races server execution
    *      against shutdown signal 6. Handles interrupt signals (Ctrl+C) with
    *      graceful shutdown 7. Provides all required dependencies (Server,
    *      DatabaseService, AwsConfig)
    *
    * The server will continue running until either:
    *   - A POST request is made to /shutdown endpoint
    *   - An interrupt signal (Ctrl+C) is received
    *
    * Both cases trigger graceful shutdown with a 30-second timeout.
    *
    * The application will fail fast at startup if any required configuration is
    * missing (DATABASE_USER, DATABASE_PASSWORD, AWS_ACCESS_KEY_ID,
    * AWS_SECRET_ACCESS_KEY).
    *
    * @return
    *   ZIO effect that runs the complete application
    */
  def run: ZIO[ZIOAppArgs & Scope, Any, Any] =
    (for {
      serverConfig <- ZIO.service[ServerConfig]
      _ <- ZIO.logInfo(s"Starting server on port ${serverConfig.port}...")
      shutdownPromise <- Promise.make[Nothing, Unit]
      allRoutes = routes(shutdownPromise)
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
      // Configuration layers: Load from environment variables with fail-fast error handling.
      // As of the config refactoring (commit bfef69f), all config loading is centralized
      // in config/AppConfig.scala. The mapError transforms Config.Error into RuntimeException
      // for consistent error types across the application.
      ServerConfig.layer.mapError(e => new RuntimeException(e.getMessage)),
      AwsConfig.layer.mapError(e => new RuntimeException(e.getMessage)),
      HealthCheckConfig.layer.mapError(e => new RuntimeException(e.getMessage)),
      // Infrastructure layers: Depend on config
      ZLayer.fromZIO(ZIO.service[ServerConfig].map(buildServerConfig)),
      Server.live,
      DatabaseService.live,
      Client.default,
      Scope.default,
      // Health check layers: Depend on infrastructure
      UrlHealthCheck.layer,
      S3HealthCheck.layer,
      DatabaseHealthCheck.layer,
      // Registry layer: Depends on health checks
      HealthCheckRegistry
        .layer
        .mapError(e => new RuntimeException(e.getMessage))
    )
