package http

import zio.http._

object RootEndpoint:
  val rootEndpointRoute =
    Method.POST / Root -> rootEndpointHandler
  val rootEndpointHandler = Handler.text("Hello, Root Endpoint!")
