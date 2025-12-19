package org.roland.scala3_zio_template.http

import zio._
import zio.http._

object ShutdownEndpoint:

  /** Creates a shutdown route that triggers graceful server shutdown
    *
    * @param shutdownPromise
    *   Promise to complete when shutdown is requested
    * @return
    *   Route that handles POST /shutdown requests
    */
  def route(shutdownPromise: Promise[Nothing, Unit]): Route[Any, Nothing] =
    val handler = Handler.fromZIO {
      ZIO
        .logInfo("Shutdown endpoint called, initiating graceful shutdown...") *>
        shutdownPromise.succeed(()).forkDaemon *>
        ZIO.succeed(
          Response
            .status(Status.Accepted)
            .addHeader(Header.ContentType(MediaType.text.plain))
            .copy(body = Body.fromString("Shutdown initiated"))
        )
    }

    Method.POST / "shutdown" -> handler
