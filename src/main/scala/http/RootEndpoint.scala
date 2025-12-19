package org.roland.scala3_zio_template.http

import zio.http._

object RootEndpoint extends Endpoint:
  override protected final val handler = withLogging(
    Handler.text("Hello, Root Endpoint!")
  )
  override val route =
    Method.GET / Root -> handler
