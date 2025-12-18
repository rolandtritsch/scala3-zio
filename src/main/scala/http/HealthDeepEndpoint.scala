package org.roland.scala3_zio_template.http

import zio.http._

object HealthDeepEndpoint:
  val healthDeepEndpointRoute =
    Method.POST / "health-deep" -> healthDeepEndpointHandler
  val healthDeepEndpointHandler = Handler.ok
