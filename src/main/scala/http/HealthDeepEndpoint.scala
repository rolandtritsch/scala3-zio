package org.roland.scala3_zio_template.http

import zio.http._

object HealthDeepEndpoint extends Endpoint:
  override protected final val handler = withLogging(Handler.ok)
  override val route =
    Method.GET / "health-deep" -> handler
