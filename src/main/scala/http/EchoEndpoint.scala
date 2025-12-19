package org.roland.scala3_zio_template.http

import zio.http._

/** HTTP endpoint that echoes back the request body.
  *
  * This endpoint accepts POST requests at `/echo` and returns the request body
  * as plain text in the response. It's useful for testing and debugging HTTP clients.
  *
  * '''Example usage:'''
  * {{{
  *   curl -X POST http://localhost:8080/echo -d "Hello, World!"
  *   # Response: Hello, World!
  * }}}
  *
  * The endpoint includes automatic request logging via the `withLogging` wrapper,
  * which logs request start, completion time, and HTTP status.
  */
object EchoEndpoint extends Endpoint:
  override protected final val handler = withLogging(
    Handler.fromFunctionZIO { (request: Request) =>
      request.body.asString.orDie.map(Response.text(_))
    }
  )
  override val route = Method.POST / "echo" -> handler
