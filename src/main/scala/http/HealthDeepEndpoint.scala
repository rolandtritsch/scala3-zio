package org.roland.scala3_zio_template.http

import zio.http._

object HealthDeepEndpoint extends Endpoint:
  override protected final val handler = Handler.ok
  override val route =
    Method.POST / "health-deep" -> handler
