package org.roland.scala3_zio_template.http

import org.roland.scala3_zio_template.service.DatabaseService
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.regions.Region

import zio._
import zio.aws.core.config.{AwsConfig => ZioAwsConfig}
import zio.aws.netty.NettyHttpClient
import zio.aws.s3.S3
import zio.aws.s3.model.ListBucketsRequest
import zio.http._
import zio.json._

object HealthDeepEndpoint:

  case class ServiceHealthCheck(
      status: String,
      message: String
  )

  object ServiceHealthCheck:
    given JsonEncoder[ServiceHealthCheck] = DeriveJsonEncoder
      .gen[ServiceHealthCheck]

  case class HealthCheckResponse(
      url: ServiceHealthCheck,
      s3: ServiceHealthCheck,
      database: ServiceHealthCheck
  )

  object HealthCheckResponse:
    given JsonEncoder[HealthCheckResponse] = DeriveJsonEncoder
      .gen[HealthCheckResponse]

  case class AwsConfig(
      accessKeyId: String,
      secretAccessKey: String,
      region: String
  )

  private def loadAwsConfig: IO[String, AwsConfig] =
    (for {
      accessKeyId <- ZIO
        .attempt(java.lang.System.getenv("AWS_ACCESS_KEY_ID"))
        .filterOrFail(Option(_).exists(_.nonEmpty))("Missing AWS_ACCESS_KEY_ID")
      secretAccessKey <- ZIO
        .attempt(java.lang.System.getenv("AWS_SECRET_ACCESS_KEY"))
        .filterOrFail(Option(_).exists(_.nonEmpty))(
          "Missing AWS_SECRET_ACCESS_KEY"
        )
      region <- ZIO
        .attempt(java.lang.System.getenv("AWS_REGION"))
        .filterOrFail(Option(_).exists(_.nonEmpty))("Missing AWS_REGION")
        .orElse(ZIO.succeed("us-east-1"))
    } yield AwsConfig(accessKeyId, secretAccessKey, region))
      .mapError(_.toString)

  private def createS3Layer(config: AwsConfig): ZLayer[Any, Throwable, S3] =
    val credentials = AwsBasicCredentials.create(
      config.accessKeyId,
      config.secretAccessKey
    )

    val commonConfig = ZLayer.succeed(
      zio
        .aws
        .core
        .config
        .CommonAwsConfig(
          region = Some(Region.of(config.region)),
          credentialsProvider = StaticCredentialsProvider.create(credentials),
          endpointOverride = None,
          commonClientConfig = None
        )
    )

    (NettyHttpClient.default ++ commonConfig) >>> ZioAwsConfig
      .configured() >>> S3.live

  private def checkUrl(
      url: String
  ): ZIO[Client & Scope, Nothing, ServiceHealthCheck] =
    Client
      .request(Request.get(url))
      .timeout(5.seconds)
      .map {
        case Some(resp) if resp.status.isSuccess =>
          ServiceHealthCheck(
            status = "healthy",
            message = s"URL returned status ${resp.status.code}"
          )
        case Some(resp) =>
          ServiceHealthCheck(
            status = "unhealthy",
            message = s"URL returned non-success status: ${resp.status.code}"
          )
        case None =>
          ServiceHealthCheck(
            status = "unhealthy",
            message = "URL request timed out after 5 seconds"
          )
      }
      .catchAll { error =>
        ZIO.succeed(
          ServiceHealthCheck(
            status = "unhealthy",
            message = s"URL check failed: ${error.getMessage}"
          )
        )
      }

  private def checkS3(
      s3Layer: ZLayer[Any, Throwable, S3]
  ): ZIO[Any, Nothing, ServiceHealthCheck] =
    (for {
      buckets <- S3.listBuckets(ListBucketsRequest()).runCollect
      bucketCount = buckets.size
    } yield ServiceHealthCheck(
      status = "healthy",
      message = s"Successfully listed $bucketCount bucket(s)"
    )).timeout(10.seconds)
      .map {
        case Some(result) => result
        case None =>
          ServiceHealthCheck(
            status = "unhealthy",
            message = "S3 list-buckets operation timed out after 10 seconds"
          )
      }
      .provideLayer(s3Layer)
      .catchAll { error =>
        val errorMsg = error match {
          case t: Throwable => t.getMessage
          case e            => e.toString
        }
        ZIO.succeed(
          ServiceHealthCheck(
            status = "unhealthy",
            message = s"S3 check failed: $errorMsg"
          )
        )
      }

  private val checkDatabase: ZIO[DatabaseService, Nothing, ServiceHealthCheck] =
    (for {
      service <- ZIO.service[DatabaseService]
      result <- service.healthCheck()
    } yield ServiceHealthCheck(
      status = "healthy",
      message = "Database connection successful"
    )).timeout(5.seconds)
      .map {
        case Some(result) => result
        case None =>
          ServiceHealthCheck(
            status = "unhealthy",
            message = "Database health check timed out after 5 seconds"
          )
      }
      .catchAll { error =>
        val errorMsg = error match {
          case t: Throwable => t.getMessage
          case e            => e.toString
        }
        ZIO.succeed(
          ServiceHealthCheck(
            status = "unhealthy",
            message = s"Database check failed: $errorMsg"
          )
        )
      }

  protected final val handler
      : Handler[DatabaseService, Nothing, Request, Response] =
    Handler.fromFunctionZIO[Request] { _ =>
      loadAwsConfig
        .flatMap { awsConfig =>
          val s3Layer = createS3Layer(awsConfig)

          // Run all three checks in parallel
          val urlCheck = checkUrl("https://tedn.life")
            .provideLayer(Client.default ++ Scope.default)
          val s3Check = checkS3(s3Layer)

          for {
            urlResult <- urlCheck
            s3Result <- s3Check
            dbResult <- checkDatabase
            response = HealthCheckResponse(
              url = urlResult,
              s3 = s3Result,
              database = dbResult
            )
            isHealthy = urlResult.status == "healthy" &&
              s3Result.status == "healthy" &&
              dbResult.status == "healthy"
            statusCode =
              if isHealthy then Status.Ok else Status.InternalServerError
          } yield Response.json(response.toJson).copy(status = statusCode)
        }
        .catchAll { missingConfigError =>
          val errorResponse = HealthCheckResponse(
            url = ServiceHealthCheck(
              "unhealthy",
              "Check skipped due to configuration error"
            ),
            s3 = ServiceHealthCheck(
              "unhealthy",
              s"Configuration error: $missingConfigError"
            ),
            database = ServiceHealthCheck(
              "unhealthy",
              "Check skipped due to configuration error"
            )
          )
          ZIO.succeed(
            Response
              .json(errorResponse.toJson)
              .copy(status = Status.InternalServerError)
          )
        }
    }

  val route: Route[DatabaseService, Nothing] =
    Method.GET / "health-deep" -> handler
