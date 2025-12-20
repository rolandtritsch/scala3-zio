package org.roland.scala3_zio_template.http

import org.roland.scala3_zio_template.http.health_checks.{
  HealthCheckRegistry,
  HealthCheckResponse,
  HealthStatus
}

import zio._
import zio.http._
import zio.json._

/** Comprehensive health check endpoint that validates all external
  * dependencies.
  *
  * This endpoint performs deep health checks on all registered health checks
  * including:
  *   - External URL availability
  *   - AWS S3 connectivity and credentials
  *   - PostgreSQL database connectivity
  *
  * All checks run in parallel for optimal performance. The endpoint returns
  * HTTP 200 only if all checks pass, otherwise HTTP 500.
  *
  * '''Endpoint:''' GET /health-deep
  *
  * '''Response Format:'''
  * {{{
  * {
  *   "checks": [
  *     {"name": "url", "status": "healthy", "message": "URL returned status 200", "durationMs": 245},
  *     {"name": "s3", "status": "healthy", "message": "Successfully listed 5 bucket(s)", "durationMs": 523},
  *     {"name": "database", "status": "healthy", "message": "Database connection successful", "durationMs": 87}
  *   ],
  *   "overallStatus": "healthy",
  *   "totalDurationMs": 523
  * }
  * }}}
  *
  * '''Configuration Required:'''
  *   - `HEALTH_CHECK_URL` - External URL to check (default: https://tedn.life)
  *   - `AWS_ACCESS_KEY_ID` - AWS access key (required)
  *   - `AWS_SECRET_ACCESS_KEY` - AWS secret key (required)
  *   - `AWS_REGION` - AWS region (default: us-east-1)
  *   - Database credentials (required, see
  *     [[org.roland.scala3_zio_template.service.DatabaseService]])
  *
  * '''Adding New Health Checks:'''
  *
  * To add a new health check (e.g., Redis):
  *   1. Create a new check implementation in `http/health_checks/` 2. Register
  *      it in [[HealthCheckRegistry.layer]] 3. Wire dependencies in
  *      `Main.scala`
  *
  * No changes needed to this endpoint - it automatically picks up new checks
  * from the registry.
  *
  * @see
  *   [[HealthEndpoint]] for basic liveness checks without external dependencies
  * @see
  *   [[HealthCheckRegistry]] for health check orchestration
  * @see
  *   [[org.roland.scala3_zio_template.http.health_checks.HealthCheck]] for the
  *   core health check abstraction
  */
object HealthDeepEndpoint:

  /** HTTP handler that performs all health checks and returns a combined
    * response.
    *
    * This handler:
    *   1. Retrieves the health check registry from the environment 2. Runs all
    *      registered health checks in parallel 3. Aggregates results into a
    *      JSON response 4. Returns HTTP 200 if all checks pass, HTTP 500 if any
    *      fail 5. Logs request start and completion for observability
    *
    * The handler uses [[HealthCheckRegistry]] to orchestrate all checks,
    * ensuring they run concurrently for optimal performance.
    */
  protected final val handler
      : Handler[HealthCheckRegistry, Nothing, Request, Response] =
    Handler.fromFunctionZIO[Request] { _ =>
      for {
        _ <- ZIO.logInfo("HealthDeepEndpoint: Request received")
        registry <- ZIO.service[HealthCheckRegistry]
        results <- registry.runAllChecks
        response = HealthCheckResponse.fromResults(results)
        statusCode =
          if response.overallStatus == HealthStatus.Healthy then Status.Ok
          else Status.InternalServerError
        _ <- ZIO.logInfo(
          s"HealthDeepEndpoint: Completed with status ${response.overallStatus}"
        )
      } yield Response.json(response.toJson).copy(status = statusCode)
    }

  /** Route definition mapping GET /health-deep to the health check handler.
    *
    * This route requires [[HealthCheckRegistry]] to be provided in the
    * environment. The registry automatically discovers and orchestrates all
    * registered health checks.
    */
  val route: Route[HealthCheckRegistry, Nothing] =
    Method.GET / "health-deep" -> handler
