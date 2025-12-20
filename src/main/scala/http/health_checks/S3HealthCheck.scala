package org.roland.scala3_zio_template.http.health_checks

import org.roland.scala3_zio_template.config.AwsConfig
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

/** Health check that validates AWS S3 connectivity.
  *
  * This check performs an S3 ListBuckets operation to verify that:
  *   - AWS credentials are valid
  *   - AWS S3 service is reachable
  *   - The application has necessary IAM permissions
  *
  * '''Required IAM Permission:''' `s3:ListAllMyBuckets`
  *
  * '''Configuration:'''
  *   - Credentials loaded from `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
  *   - Region loaded from `AWS_REGION` (default: us-east-1)
  *
  * '''Timeout:''' 10 seconds to account for S3 API latency
  *
  * '''Dependencies:'''
  *   - [[AwsConfig]]: AWS credentials and region configuration
  *
  * @see
  *   [[HealthCheck]] for the core abstraction
  * @see
  *   [[AwsConfig]] for configuration details
  */
trait S3HealthCheck extends HealthCheck

object S3HealthCheck:

  /** Live implementation of S3 health check.
    *
    * This implementation:
    *   - Creates an S3 client with configured credentials
    *   - Lists S3 buckets with 10-second timeout
    *   - Returns healthy if bucket listing succeeds
    *   - Returns unhealthy for credential errors, timeouts, or AWS errors
    *   - Logs full error details to server logs
    *   - Returns user-friendly messages in results
    *
    * @param config
    *   AWS configuration containing credentials and region
    */
  private final class S3HealthCheckImpl(config: AwsConfig)
      extends S3HealthCheck:

    override def name: String = "s3"

    override def description: String =
      "Validates AWS S3 connectivity by listing buckets"

    /** Creates a ZLayer for AWS S3 client with the given configuration.
      *
      * This layer sets up the S3 client with:
      *   - Static credentials from the config
      *   - Configured AWS region
      *   - Netty HTTP client for async operations
      *
      * @return
      *   ZLayer that provides an S3 client instance
      */
    private def createS3Layer: ZLayer[Any, Throwable, S3] =
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

    override def check: ZIO[Any, Nothing, HealthCheckResult] =
      val startTime = java.lang.System.currentTimeMillis()

      (for {
        buckets <- S3.listBuckets(ListBucketsRequest()).runCollect
        bucketCount = buckets.size
        duration = java.lang.System.currentTimeMillis() - startTime
      } yield HealthCheckResult(
        name = name,
        status = HealthStatus.Healthy,
        message = s"Successfully listed $bucketCount bucket(s)",
        durationMs = duration
      )).timeout(10.seconds)
        .map {
          case Some(result) => result
          case None =>
            HealthCheckResult(
              name = name,
              status = HealthStatus.Unhealthy,
              message = "S3 list-buckets operation timed out after 10 seconds",
              durationMs = 10000
            )
        }
        .provideLayer(createS3Layer)
        .catchAll { error =>
          val duration = java.lang.System.currentTimeMillis() - startTime
          val errorMsg = error match
            case e: AwsError  => e.toString
            case t: Throwable => t.getMessage

          ZIO.logError(s"$name health check failed: $errorMsg") *>
            ZIO.succeed(
              HealthCheckResult(
                name = name,
                status = HealthStatus.Unhealthy,
                message = "S3 connection failed: check credentials",
                durationMs = duration
              )
            )
        }

  /** ZLayer that provides a live S3HealthCheck implementation.
    *
    * This layer:
    *   - Depends on [[AwsConfig]] for credentials and region
    *   - Constructs the check at application startup
    *
    * '''Note:''' This layer only depends on leaf services (config), not on
    * other health checks or the registry, to avoid circular dependencies.
    */
  val layer: ZLayer[AwsConfig, Nothing, S3HealthCheck] =
    ZLayer.fromZIO(
      for config <- ZIO.service[AwsConfig]
      yield S3HealthCheckImpl(config)
    )
