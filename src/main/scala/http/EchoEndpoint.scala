package org.roland.scala3_zio_template.http

import zio.http._

object EchoEndpoint extends Endpoint:
  override protected final val handler = withLogging(
    Handler.fromFunctionZIO { (request: Request) =>
      request.body.asString.orDie.map(Response.text(_))
    }
  )
  override val route = Method.POST / "echo" -> handler
