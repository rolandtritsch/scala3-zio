package org.roland.scala3_zio_template.http

import zio.http._

object HealthEndpoint:
  val healthEndpointRoute =
    Method.POST / "health" -> healthEndpointHandler
  val healthEndpointHandler = Handler.ok
