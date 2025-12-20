package org.roland.scala3_zio_template.http.health_checks

import org.roland.scala3_zio_template.config.HealthCheckConfig

import zio._
import zio.http._

/** Health check that validates external URL availability.
  *
  * This check performs an HTTP GET request to a configured URL and verifies
  * that it returns a successful status code (2xx). It's useful for:
  *   - Validating internet connectivity
  *   - Checking external API availability
  *   - Verifying DNS resolution works
  *
  * '''Configuration:'''
  *   - URL is loaded from `HEALTH_CHECK_URL` environment variable
  *   - Default: https://tedn.life
  *
  * '''Timeout:''' 5 seconds to prevent hanging on unresponsive servers
  *
  * '''Dependencies:'''
  *   - [[HealthCheckConfig]]: Configuration containing the URL to check
  *   - [[zio.http.Client]]: HTTP client for making requests
  *   - [[zio.Scope]]: Resource management for the HTTP client
  *
  * @see
  *   [[HealthCheck]] for the core abstraction
  * @see
  *   [[HealthCheckConfig]] for configuration details
  */
trait UrlHealthCheck extends HealthCheck

object UrlHealthCheck:

  /** Live implementation of URL health check.
    *
    * This implementation:
    *   - Reads the target URL from configuration
    *   - Makes an HTTP GET request with 5-second timeout
    *   - Returns healthy if status code is 2xx
    *   - Returns unhealthy for non-2xx status, timeout, or errors
    *   - Logs full error details to server logs
    *   - Returns user-friendly messages in results
    *
    * @param config
    *   Configuration containing the URL to check
    * @param client
    *   HTTP client for making requests
    * @param scope
    *   Resource scope for client lifecycle
    */
  private final class UrlHealthCheckImpl(
      config: HealthCheckConfig,
      client: Client,
      scope: Scope
  ) extends UrlHealthCheck:

    override def name: String = "url"

    override def description: String =
      "Validates external URL availability via HTTP GET request"

    override def check: ZIO[Any, Nothing, HealthCheckResult] =
      val startTime = java.lang.System.currentTimeMillis()

      (client
        .batched(Request.get(config.checkUrl))
        .timeout(5.seconds)
        .map {
          case Some(resp) if resp.status.isSuccess =>
            val duration = java.lang.System.currentTimeMillis() - startTime
            HealthCheckResult(
              name = name,
              status = HealthStatus.Healthy,
              message = s"URL returned status ${resp.status.code}",
              durationMs = duration
            )
          case Some(resp) =>
            val duration = java.lang.System.currentTimeMillis() - startTime
            HealthCheckResult(
              name = name,
              status = HealthStatus.Unhealthy,
              message = s"URL returned non-success status: ${resp.status.code}",
              durationMs = duration
            )
          case None =>
            HealthCheckResult(
              name = name,
              status = HealthStatus.Unhealthy,
              message = "URL request timed out after 5 seconds",
              durationMs = 5000
            )
        })
        .catchAll { error =>
          val duration = java.lang.System.currentTimeMillis() - startTime
          ZIO.logError(s"$name health check failed: ${error.toString}") *>
            ZIO.succeed(
              HealthCheckResult(
                name = name,
                status = HealthStatus.Unhealthy,
                message = "URL check failed: unable to connect",
                durationMs = duration
              )
            )
        }

  /** ZLayer that provides a live UrlHealthCheck implementation.
    *
    * This layer:
    *   - Depends on [[HealthCheckConfig]] for URL configuration
    *   - Depends on [[zio.http.Client]] for HTTP operations
    *   - Depends on [[zio.Scope]] for resource management
    *   - Constructs the check at application startup
    *
    * '''Note:''' This layer only depends on leaf services (config, client,
    * scope), not on other health checks or the registry, to avoid circular
    * dependencies.
    */
  val layer
      : ZLayer[HealthCheckConfig & Client & Scope, Nothing, UrlHealthCheck] =
    ZLayer.fromZIO(
      for
        config <- ZIO.service[HealthCheckConfig]
        client <- ZIO.service[Client]
        scope <- ZIO.service[Scope]
      yield UrlHealthCheckImpl(config, client, scope)
    )
