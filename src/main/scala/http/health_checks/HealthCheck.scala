package org.roland.scala3_zio_template.http.health_checks

import zio._
import zio.json._

/** Core abstraction for pluggable health checks.
  *
  * Health checks validate the availability and correctness of external
  * dependencies (databases, APIs, cloud services, etc.). Each health check runs
  * independently and returns a structured result.
  *
  * '''Design Principles:'''
  *   - '''Never Fail''': All checks return `ZIO[Any, Nothing,
  *     HealthCheckResult]` to prevent cascading failures
  *   - '''Timeout Protection''': Each check implements its own timeout to
  *     prevent hanging
  *   - '''User-Friendly Messages''': Results contain actionable, non-technical
  *     messages
  *   - '''Kebab-Case Names''': Check names must be lowercase with hyphens
  *     (e.g., "database", "s3", "external-api")
  *
  * '''Example Implementation:'''
  * {{{
  * case class RedisHealthCheckImpl(client: RedisClient) extends HealthCheck:
  *   override def name: String = "redis"
  *   override def description: String = "Redis connectivity check"
  *
  *   override def check: ZIO[Any, Nothing, HealthCheckResult] =
  *     val startTime = System.currentTimeMillis()
  *     client.ping()
  *       .timeout(5.seconds)
  *       .map {
  *         case Some(_) =>
  *           val duration = System.currentTimeMillis() - startTime
  *           HealthCheckResult(name, HealthStatus.Healthy, "Redis is responding", duration)
  *         case None =>
  *           HealthCheckResult(name, HealthStatus.Unhealthy, "Redis ping timed out", 5000)
  *       }
  *       .catchAll { err =>
  *         ZIO.logError(s"Redis health check failed: $err") *>
  *         ZIO.succeed(
  *           HealthCheckResult(name, HealthStatus.Unhealthy, "Redis connection failed", 0)
  *         )
  *       }
  * }}}
  *
  * @see
  *   [[HealthCheckRegistry]] for orchestration and parallel execution
  * @see
  *   [[org.roland.scala3_zio_template.http.HealthDeepEndpoint]] for the HTTP
  *   endpoint
  */
trait HealthCheck:
  /** Unique identifier for this health check.
    *
    * '''MUST''' be kebab-case (lowercase letters, numbers, and hyphens only).
    * Examples: "database", "s3", "external-api", "redis-cache"
    *
    * Names are validated at application startup using
    * [[HealthCheck.validateName]]. Invalid names cause immediate failure with a
    * clear error message.
    *
    * @return
    *   The kebab-case identifier for this check
    */
  def name: String

  /** Human-readable description of what this check validates.
    *
    * Used for documentation and logging. Should be concise (1-2 sentences) and
    * describe the external dependency being checked.
    *
    * @return
    *   Description of the health check
    */
  def description: String

  /** Executes the health check and returns a result.
    *
    * This method:
    *   - '''Never fails''': Returns `ZIO[Any, Nothing, HealthCheckResult]`
    *   - '''Always times out''': Implements its own timeout to prevent hanging
    *   - '''Logs errors''': Writes detailed error information to server logs
    *   - '''Returns user-friendly messages''': Result messages are actionable
    *     and non-technical
    *
    * '''Error Handling Pattern:'''
    * {{{
    * check
    *   .timeout(5.seconds)
    *   .map {
    *     case Some(result) => HealthCheckResult(...)  // Success case
    *     case None => HealthCheckResult(..., Unhealthy, "Check timed out", ...)
    *   }
    *   .catchAll { err =>
    *     ZIO.logError(s"$name health check failed: $err") *>
    *     ZIO.succeed(HealthCheckResult(..., Unhealthy, "Service unavailable", ...))
    *   }
    * }}}
    *
    * @return
    *   ZIO effect that always succeeds with a HealthCheckResult
    */
  def check: ZIO[Any, Nothing, HealthCheckResult]

/** Result of executing a single health check.
  *
  * Contains all information about the check outcome including status, message,
  * and timing information for observability.
  *
  * @param name
  *   The kebab-case identifier of the check (matches [[HealthCheck.name]])
  * @param status
  *   Whether the check passed ([[HealthStatus.Healthy]]) or failed
  *   ([[HealthStatus.Unhealthy]])
  * @param message
  *   User-friendly description of the result (e.g., "Database connection
  *   successful" or "S3 bucket listing failed")
  * @param durationMs
  *   How long the check took in milliseconds (for performance monitoring)
  */
case class HealthCheckResult(
    name: String,
    status: HealthStatus,
    message: String,
    durationMs: Long
)

object HealthCheckResult:
  given JsonEncoder[HealthCheckResult] = DeriveJsonEncoder
    .gen[HealthCheckResult]

/** Status of a health check execution.
  *
  * Only two states exist to keep the model simple:
  *   - [[Healthy]]: Check passed, dependency is available
  *   - [[Unhealthy]]: Check failed, dependency is unavailable or degraded
  */
enum HealthStatus:
  /** Health check passed, dependency is functioning correctly */
  case Healthy

  /** Health check failed, dependency is unavailable or experiencing issues */
  case Unhealthy

object HealthStatus:
  /** JSON encoder that outputs lowercase status names ("healthy" or
    * "unhealthy").
    *
    * This matches the existing API contract from the previous implementation.
    */
  given JsonEncoder[HealthStatus] =
    JsonEncoder.string.contramap(_.toString.toLowerCase)

object HealthCheck:
  /** Regular expression for kebab-case validation.
    *
    * Valid patterns:
    *   - Must start with a lowercase letter
    *   - Can contain lowercase letters, numbers, and hyphens
    *   - Cannot start or end with a hyphen
    *   - Cannot have consecutive hyphens
    *
    * Examples:
    *   - Valid: "database", "s3", "external-api", "redis-cache-1"
    *   - Invalid: "Database", "s3_bucket", "api-", "-redis", "my--check"
    */
  private val kebabCaseRegex = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$".r

  /** Validates that a health check name conforms to kebab-case requirements.
    *
    * This validation is performed at application startup by
    * [[HealthCheckRegistry]] to ensure all check names are properly formatted
    * before the HTTP endpoint is exposed.
    *
    * @param name
    *   The health check name to validate
    * @return
    *   Right(name) if valid, Left(error message) if invalid
    */
  def validateName(name: String): Either[String, String] =
    if kebabCaseRegex.matches(name) then Right(name)
    else
      Left(
        s"Health check name '$name' must be kebab-case (lowercase, hyphens only)"
      )
