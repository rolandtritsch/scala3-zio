package org.roland.scala3_zio_template.http

import zio.http._

object HealthEndpoint extends Endpoint:
  override protected final val handler = Handler.ok
  override val route =
    Method.POST / "health" -> handler
