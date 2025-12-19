package org.roland.scala3_zio_template.http

import zio._
import zio.http._

/** HTTP endpoint that triggers graceful server shutdown.
  *
  * This endpoint accepts POST requests at `/shutdown` and initiates a graceful
  * shutdown sequence. When called:
  *   1. Immediately returns HTTP 202 Accepted 2. Signals the server to begin
  *      shutdown via the provided Promise 3. Allows in-flight requests to
  *      complete (30-second timeout) 4. Cleanly terminates the application
  *
  * '''Endpoint:''' POST /shutdown
  *
  * '''Response:''' HTTP 202 Accepted with "Shutdown initiated" message
  *
  * '''Security Note:''' This endpoint has no authentication. In production, you
  * should protect it with authentication/authorization or restrict access via
  * network policies.
  *
  * @see
  *   [[Main.performGracefulShutdown]] for the shutdown implementation
  */
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
