package org.roland.scala3_zio_template.http

import zio.http._

object RootEndpoint extends Endpoint:
  override protected final val handler = Handler.text("Hello, Root Endpoint!")
  override val route =
    Method.POST / Root -> handler
