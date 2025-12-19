package org.roland.scala3_zio_template.http

import zio._
import zio.http._

/** Base class for HTTP endpoint implementations.
  *
  * This abstract class provides common functionality for all HTTP endpoints,
  * including automatic request logging with timing information. Concrete
  * endpoint implementations should extend this class and define their route
  * and handler.
  *
  * The `withLogging` method can be used to wrap any handler with automatic
  * logging of request start, completion, duration, and HTTP status code.
  *
  * @see [[withLogging]] for request logging functionality
  */
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
