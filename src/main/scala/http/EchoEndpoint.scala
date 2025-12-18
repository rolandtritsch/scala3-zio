package http

import zio.http._

object EchoEndpoint:
  val echoEndpointRoute = Method.POST / "echo" -> echoEndpointHandler
  val echoEndpointHandler = handler { (request: Request) =>
    request.body.asString.map(Response.text(_))
  }
