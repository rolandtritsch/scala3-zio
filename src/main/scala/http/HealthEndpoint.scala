package org.roland.scala3_zio_template.http

import zio.http._

/** Basic health check endpoint for liveness probes.
  *
  * This endpoint always returns HTTP 200 OK when the server is running. It
  * performs no external checks and is intended for Kubernetes liveness probes
  * or load balancer health checks that only need to verify the process is
  * alive.
  *
  * '''Endpoint:''' GET /health
  *
  * '''Response:''' HTTP 200 OK
  *
  * For comprehensive health checks that validate external dependencies
  * (database, S3, etc.), use [[HealthDeepEndpoint]] instead.
  *
  * @see
  *   [[HealthDeepEndpoint]] for comprehensive health checks
  */
object HealthEndpoint extends Endpoint:
  override protected final val handler = withLogging(Handler.ok)
  override val route =
    Method.GET / "health" -> handler
