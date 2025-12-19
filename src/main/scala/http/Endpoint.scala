package org.roland.scala3_zio_template.http

import zio._
import zio.http._

abstract class Endpoint:
  val route: Route[Any, Nothing]
  protected val handler: Handler[Any, Nothing, Request, Response]

  /** Wraps a handler with request logging (start, end, duration, status)
    *
    * @param handler
    *   The handler to wrap with logging
    * @return
    *   A new handler that logs request start/end with timing and status
    */
  protected def withLogging(
      handler: Handler[Any, Nothing, Request, Response]
  ): Handler[Any, Nothing, Request, Response] =
    Handler.fromFunctionZIO { (request: Request) =>
      val method = request.method.toString
      val path = request.path.encode

      ZIO.scoped {
        for {
          _ <- ZIO.logInfo(s"$method $path - Request started")
          startTime <- Clock.nanoTime
          response <- handler(request)
          endTime <- Clock.nanoTime
          durationMs = (endTime - startTime) / 1_000_000
          status = response.status.code
          _ <- ZIO.logInfo(
            s"$method $path - Request completed (${durationMs}ms, status: $status)"
          )
        } yield response
      }
    }
