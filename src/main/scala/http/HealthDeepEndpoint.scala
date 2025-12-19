package org.roland.scala3_zio_template.http

import zio._
import zio.http._
import zio.json._

object HealthDeepEndpoint extends Endpoint:

  case class HealthCheckResult(
      success: Boolean,
      statusCode: Option[Int],
      error: Option[String]
  )

  object HealthCheckResult:
    given JsonEncoder[HealthCheckResult] = DeriveJsonEncoder.gen[HealthCheckResult]

  private def checkUrl(url: String): ZIO[Client & Scope, Nothing, HealthCheckResult] =
    Client
      .request(Request.get(url))
      .timeout(5.seconds)
      .map {
        case Some(resp) =>
          HealthCheckResult(
            success = resp.status.isSuccess,
            statusCode = Some(resp.status.code),
            error = if resp.status.isSuccess then None
            else Some(s"Received non-success status: ${resp.status}")
          )
        case None =>
          HealthCheckResult(
            success = false,
            statusCode = None,
            error = Some("Request timed out after 5 seconds")
          )
      }
      .catchAll { error =>
        ZIO.succeed(
          HealthCheckResult(
            success = false,
            statusCode = None,
            error = Some(s"Request failed: ${error.getMessage}")
          )
        )
      }

  override protected final val handler = withLogging:
    Handler.fromFunctionZIO[Request] { _ =>
      checkUrl("https://tedn.life")
        .provideLayer(Client.default ++ Scope.default)
        .flatMap { result =>
          if result.success then ZIO.succeed(Response.json(result.toJson))
          else
            ZIO.succeed(
              Response.json(result.toJson).copy(status = Status.InternalServerError)
            )
        }
        .catchAllCause { cause =>
          ZIO.succeed(
            Response
              .json(
                HealthCheckResult(
                  success = false,
                  statusCode = None,
                  error = Some(s"Unexpected error: ${cause.prettyPrint}")
                ).toJson
              )
              .copy(status = Status.InternalServerError)
          )
        }
    }

  override val route =
    Method.GET / "health-deep" -> handler
