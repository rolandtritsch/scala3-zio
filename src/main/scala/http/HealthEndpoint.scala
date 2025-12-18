package http

import zio.http._

object HealthEndpoint:
  val healthEndpointRoute =
    Method.POST / "health" -> healthEndpointHandler
  val healthEndpointHandler = Handler.ok
