package org.roland.scala3_zio_template.http.health_checks

import zio._
import zio.json._

/** Registry that manages and orchestrates multiple health checks.
  *
  * The registry:
  *   - Collects all health check implementations via ZLayer dependency
  *     injection
  *   - Validates check names are unique and properly formatted (kebab-case)
  *   - Runs all checks in parallel for optimal performance
  *   - Aggregates results into a complete health report
  *   - Provides global timeout protection to prevent endpoint hanging
  *
  * '''Startup Validation:''' The registry layer validates all health checks at
  * application startup and fails immediately if:
  *   - Duplicate check names are detected
  *   - Check names don't conform to kebab-case format
  *
  * '''Parallel Execution:''' All checks run concurrently using ZIO fibers.
  * Individual check timeouts are respected, and a global 15-second timeout
  * ensures the endpoint never hangs.
  *
  * '''Empty Registry:''' If no health checks are registered, the endpoint
  * returns HTTP 200 with `overallStatus: healthy` and an empty `checks` array.
  *
  * @param checks
  *   List of all registered health check implementations
  *
  * @see
  *   [[HealthCheck]] for individual health check abstraction
  * @see
  *   [[org.roland.scala3_zio_template.http.HealthDeepEndpoint]] for the HTTP
  *   endpoint
  */
case class HealthCheckRegistry(checks: List[HealthCheck]):

  /** Runs all registered health checks in parallel with global timeout
    * protection.
    *
    * This method:
    *   - Executes all checks concurrently using `ZIO.collectAllPar`
    *   - Continues running all checks even if some fail
    *   - Applies a 15-second global timeout to prevent endpoint hanging
    *   - Returns empty list if no checks are registered
    *
    * The global timeout is a safety mechanism in case individual check timeouts
    * fail. If the timeout triggers, an empty list is returned and the endpoint
    * treats this as an unhealthy state.
    *
    * @return
    *   ZIO effect that always succeeds with a list of health check results
    */
  def runAllChecks: ZIO[Any, Nothing, List[HealthCheckResult]] =
    if checks.isEmpty then ZIO.succeed(List.empty)
    else
      ZIO
        .collectAllPar(checks.map(_.check))
        .timeout(15.seconds)
        .map(_.getOrElse(List.empty))

object HealthCheckRegistry:

  /** ZLayer that constructs the registry from individual health check
    * implementations.
    *
    * This layer:
    *   - Collects all health check services from the ZIO environment
    *   - Validates that all check names are unique
    *   - Validates that all check names conform to kebab-case format
    *   - Constructs the registry with all checks
    *
    * '''Validation Failures:''' The layer fails at application startup if any
    * validation fails, preventing the HTTP endpoint from being exposed with
    * invalid configuration.
    *
    * '''Adding New Checks:''' To register a new health check:
    *   1. Add it to the dependency list: `ZLayer[..existing.. & NewCheck, ...]`
    *      2. Add it to the service collection: `newCheck <-
    *      ZIO.service[NewCheck]` 3. Add it to the checks list:
    *      `List(...existing..., newCheck)`
    *
    * @return
    *   ZLayer that provides a HealthCheckRegistry instance
    */
  val layer: ZLayer[
    UrlHealthCheck & S3HealthCheck & DatabaseHealthCheck,
    Throwable,
    HealthCheckRegistry
  ] =
    ZLayer.fromZIO(
      for
        urlCheck <- ZIO.service[UrlHealthCheck]
        s3Check <- ZIO.service[S3HealthCheck]
        dbCheck <- ZIO.service[DatabaseHealthCheck]

        // Collect all checks
        allChecks = List(urlCheck, s3Check, dbCheck)
        names = allChecks.map(_.name)

        // Validate unique names
        duplicates = names
          .groupBy(identity)
          .filter(_._2.size > 1)
          .keys
          .toList
          .sorted
        _ <- ZIO.when(duplicates.nonEmpty)(
          ZIO.fail(
            new IllegalStateException(
              s"Duplicate health check names: ${duplicates.mkString(", ")}"
            )
          )
        )

        // Validate kebab-case format
        _ <- ZIO.foreachDiscard(allChecks) { check =>
          ZIO
            .fromEither(HealthCheck.validateName(check.name))
            .mapError(msg => new IllegalStateException(msg))
        }
      yield HealthCheckRegistry(allChecks)
    )

/** Complete health check response containing all check results and overall
  * status.
  *
  * This response format supports arbitrary numbers of health checks through an
  * array-based structure, unlike the previous implementation which used named
  * fields.
  *
  * '''Response Structure:'''
  * {{{
  * {
  *   "checks": [
  *     {"name": "url", "status": "healthy", "message": "...", "durationMs": 245},
  *     {"name": "s3", "status": "healthy", "message": "...", "durationMs": 523},
  *     {"name": "database", "status": "healthy", "message": "...", "durationMs": 87}
  *   ],
  *   "overallStatus": "healthy",
  *   "totalDurationMs": 523
  * }
  * }}}
  *
  * @param checks
  *   Results from all executed health checks
  * @param overallStatus
  *   Aggregated status: healthy if all checks passed, unhealthy if any failed
  * @param totalDurationMs
  *   Maximum duration of any check (since checks run in parallel)
  */
case class HealthCheckResponse(
    checks: List[HealthCheckResult],
    overallStatus: HealthStatus,
    totalDurationMs: Long
)

object HealthCheckResponse:
  given JsonEncoder[HealthCheckResponse] = DeriveJsonEncoder
    .gen[HealthCheckResponse]

  /** Constructs a health check response from individual check results.
    *
    * The overall status is healthy only if '''all''' checks report healthy
    * status. If any check is unhealthy or the results list is empty (global
    * timeout), the overall status is unhealthy.
    *
    * The total duration is the maximum duration of any individual check, since
    * all checks run in parallel.
    *
    * @param results
    *   List of health check results (may be empty if global timeout occurred)
    * @return
    *   Complete health check response with aggregated status
    */
  def fromResults(results: List[HealthCheckResult]): HealthCheckResponse =
    val overallStatus =
      if results.nonEmpty && results.forall(_.status == HealthStatus.Healthy)
      then HealthStatus.Healthy
      else HealthStatus.Unhealthy

    val totalDuration =
      if results.isEmpty then 0
      else results.map(_.durationMs).max

    HealthCheckResponse(
      checks = results,
      overallStatus = overallStatus,
      totalDurationMs = totalDuration
    )
