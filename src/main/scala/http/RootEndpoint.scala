package org.roland.scala3_zio_template.http

import zio.http._

/** Root endpoint that provides a simple welcome message.
  *
  * This endpoint responds to GET requests at the root path (/) with a
  * plain text greeting. It serves as a basic sanity check that the server
  * is running and responding to requests.
  *
  * '''Endpoint:''' GET /
  *
  * '''Response:''' "Hello, Root Endpoint!" (HTTP 200 OK)
  */
object RootEndpoint extends Endpoint:
  override protected final val handler = withLogging(
    Handler.text("Hello, Root Endpoint!")
  )
  override val route =
    Method.GET / Root -> handler
