package org.roland.scala3_zio_template.http

import zio.http._

object RootEndpoint:
  val rootEndpointRoute =
    Method.POST / Root -> rootEndpointHandler
  val rootEndpointHandler = Handler.text("Hello, Root Endpoint!")
