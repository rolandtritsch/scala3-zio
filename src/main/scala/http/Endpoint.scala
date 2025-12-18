package org.roland.scala3_zio_template.http

import zio.http._

abstract class Endpoint:
  val route: Route[Any, Nothing]
  protected val handler: Handler[Any, Nothing, Request, Response]
