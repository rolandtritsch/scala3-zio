package org.roland.scala3_zio_template.http

import org.roland.scala3_zio_template.service.DatabaseService
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.regions.Region

import zio._
import zio.aws.core.AwsError
import zio.aws.core.config.{AwsConfig => ZioAwsConfig}
import zio.aws.netty.NettyHttpClient
import zio.aws.s3.S3
import zio.aws.s3.model.ListBucketsRequest
import zio.http._
import zio.json._

/** Comprehensive health check endpoint that validates all external
  * dependencies.
  *
  * This endpoint performs deep health checks on:
  *   - External URL availability (https://tedn.life)
  *   - AWS S3 connectivity and credentials
  *   - PostgreSQL database connectivity
  *
  * Each check runs with a timeout and returns a detailed status. The endpoint
  * returns HTTP 200 only if all checks pass, otherwise HTTP 500.
  *
  * '''Endpoint:''' GET /health-deep
  *
  * '''Response Format:'''
  * {{{
  * {
  *   "url": {"status": "healthy", "message": "URL returned status 200"},
  *   "s3": {"status": "healthy", "message": "Successfully listed 5 bucket(s)"},
  *   "database": {"status": "healthy", "message": "Database connection successful"}
  * }
  * }}}
  *
  * '''Configuration Required:'''
  *   - `AWS_ACCESS_KEY_ID` - AWS access key (optional but required for S3
  *     check)
  *   - `AWS_SECRET_ACCESS_KEY` - AWS secret key (optional but required for S3
  *     check)
  *   - `AWS_REGION` - AWS region (default: us-east-1)
  *   - Database credentials (always required, see
  *     [[org.roland.scala3_zio_template.service.DatabaseService]])
  *
  * If AWS credentials are missing, the endpoint returns HTTP 500 with error
  * details but the application continues running.
  *
  * @see
  *   [[HealthEndpoint]] for basic liveness checks without external dependencies
  */
object HealthDeepEndpoint:

  /** Health check result for a single service.
    *
    * @param status
    *   Either "healthy" or "unhealthy"
    * @param message
    *   Descriptive message about the check result
    */
  case class ServiceHealthCheck(
      status: String,
      message: String
  )

  object ServiceHealthCheck:
    given JsonEncoder[ServiceHealthCheck] = DeriveJsonEncoder
      .gen[ServiceHealthCheck]

  /** Complete health check response containing all service statuses.
    *
    * @param url
    *   Health status of external URL check
    * @param s3
    *   Health status of AWS S3 connectivity check
    * @param database
    *   Health status of PostgreSQL database check
    */
  case class HealthCheckResponse(
      url: ServiceHealthCheck,
      s3: ServiceHealthCheck,
      database: ServiceHealthCheck
  )

  object HealthCheckResponse:
    given JsonEncoder[HealthCheckResponse] = DeriveJsonEncoder
      .gen[HealthCheckResponse]

  /** AWS configuration loaded from environment variables.
    *
    * @param accessKeyId
    *   AWS access key ID
    * @param secretAccessKey
    *   AWS secret access key
    * @param region
    *   AWS region (default: us-east-1)
    */
  case class AwsConfig(
      accessKeyId: String,
      secretAccessKey: String,
      region: String
  )

  /** Loads AWS configuration from environment variables.
    *
    * Required environment variables:
    *   - AWS_ACCESS_KEY_ID
    *   - AWS_SECRET_ACCESS_KEY
    *   - AWS_REGION (optional, defaults to us-east-1)
    *
    * @return
    *   ZIO effect that succeeds with AwsConfig or fails with error message
    */
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

  /** Creates a ZLayer for AWS S3 client with the given configuration.
    *
    * This layer sets up the S3 client with:
    *   - Static credentials from the config
    *   - Configured AWS region
    *   - Netty HTTP client for async operations
    *
    * @param config
    *   AWS configuration containing credentials and region
    * @return
    *   ZLayer that provides an S3 client instance
    */
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

  /** Performs an HTTP health check on the specified URL.
    *
    * The check succeeds if the URL returns a successful HTTP status (2xx).
    * Includes a 5-second timeout to prevent hanging on unresponsive servers.
    *
    * @param url
    *   The URL to check (e.g., "https://tedn.life")
    * @return
    *   ZIO effect that always succeeds with a ServiceHealthCheck result
    */
  private def checkUrl(
      url: String
  ): ZIO[Client & Scope, Nothing, ServiceHealthCheck] =
    Client
      .batched(Request.get(url))
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

  /** Performs a health check on AWS S3 by listing buckets.
    *
    * The check succeeds if the S3 client can authenticate and list buckets.
    * Includes a 10-second timeout to prevent hanging on network issues.
    *
    * @param s3Layer
    *   ZLayer providing the configured S3 client
    * @return
    *   ZIO effect that always succeeds with a ServiceHealthCheck result
    */
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
        val errorMsg = error match
          case e: AwsError  => e.toString
          case t: Throwable => t.getMessage
        ZIO.succeed(
          ServiceHealthCheck(
            status = "unhealthy",
            message = s"S3 check failed: $errorMsg"
          )
        )
      }

  /** Performs a health check on the PostgreSQL database.
    *
    * The check succeeds if the database can execute a simple SELECT 1 query.
    * Includes a 5-second timeout to prevent hanging on connection issues.
    *
    * @return
    *   ZIO effect that always succeeds with a ServiceHealthCheck result,
    *   requires DatabaseService
    */
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
        ZIO.succeed(
          ServiceHealthCheck(
            status = "unhealthy",
            message = s"Database check failed: ${error.getMessage}"
          )
        )
      }

  /** HTTP handler that performs all health checks and returns a combined
    * response.
    *
    * This handler:
    *   1. Loads AWS configuration from environment variables 2. Runs URL, S3,
    *      and database checks in sequence 3. Combines results into a JSON
    *      response 4. Returns HTTP 200 if all checks pass, HTTP 500 if any fail
    *
    * If AWS configuration is missing, returns HTTP 500 with configuration
    * error.
    */
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

  /** Route definition mapping GET /health-deep to the health check handler.
    *
    * This route requires DatabaseService to be provided in the environment.
    */
  val route: Route[DatabaseService, Nothing] =
    Method.GET / "health-deep" -> handler
