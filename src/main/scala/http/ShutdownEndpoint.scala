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
    val baseHandler = Handler.fromZIO {
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

    val handler = Handler.fromFunctionZIO { (request: Request) =>
      val method = request.method.toString
      val path = request.path.encode

      ZIO.scoped {
        for {
          _ <- ZIO.logInfo(s"$method $path - Request started")
          startTime <- Clock.nanoTime
          response <- baseHandler(request)
          endTime <- Clock.nanoTime
          durationMs = (endTime - startTime) / 1_000_000
          status = response.status.code
          _ <- ZIO.logInfo(
            s"$method $path - Request completed (${durationMs}ms, status: $status)"
          )
        } yield response
      }
    }

    Method.POST / "shutdown" -> handler
