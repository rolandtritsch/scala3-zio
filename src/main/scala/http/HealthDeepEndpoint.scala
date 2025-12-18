package http

import zio.http._

object HealthDeepEndpoint:
  val healthDeepEndpointRoute =
    Method.POST / "health-deep" -> healthDeepEndpointHandler
  val healthDeepEndpointHandler = Handler.ok
