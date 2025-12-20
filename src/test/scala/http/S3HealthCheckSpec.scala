package org.roland.scala3_zio_template.http

import org.roland.scala3_zio_template.config.AwsConfig
import org.roland.scala3_zio_template.http.health_checks.{
  HealthStatus,
  S3HealthCheck
}

import zio._
import zio.test._

/** Test suite for S3HealthCheck.
  *
  * Tests S3 health check functionality including:
  *   - Configuration and layer construction
  *   - Name and description properties
  *   - Check result structure (integration with real S3 tested separately)
  *
  * Note: These tests verify the structure and configuration of the S3 health
  * check. Full integration testing with real AWS S3 would require valid
  * credentials and network access, which is beyond the scope of unit tests.
  */
object S3HealthCheckSpec extends ZIOSpecDefault:

  private val testAwsConfig = AwsConfig(
    accessKeyId = "AKIATEST123456",
    secretAccessKey = "testsecretkey123456789",
    region = "us-east-1"
  )

  private val testAwsConfigLayer = ZLayer.succeed(testAwsConfig)

  def spec = suite("S3HealthCheck")(
    test("should construct layer successfully with AwsConfig") {
      for {
        check <- ZIO
          .service[S3HealthCheck]
          .provide(testAwsConfigLayer, S3HealthCheck.layer)
      } yield assertTrue(check.name == "s3")
    },
    test("should have correct name") {
      for {
        check <- ZIO
          .service[S3HealthCheck]
          .provide(testAwsConfigLayer, S3HealthCheck.layer)
      } yield assertTrue(check.name == "s3")
    },
    test("should have correct description") {
      for {
        check <- ZIO
          .service[S3HealthCheck]
          .provide(testAwsConfigLayer, S3HealthCheck.layer)
      } yield assertTrue(
        check.description == "Validates AWS S3 connectivity by listing buckets"
      )
    },
    test("should work with different AWS regions") {
      val configs = List(
        AwsConfig("KEY", "SECRET", "us-east-1"),
        AwsConfig("KEY", "SECRET", "us-west-2"),
        AwsConfig("KEY", "SECRET", "eu-west-1")
      )

      ZIO
        .foreach(configs) { config =>
          for {
            check <- ZIO
              .service[S3HealthCheck]
              .provide(ZLayer.succeed(config), S3HealthCheck.layer)
          } yield check.name == "s3"
        }
        .map(results => assertTrue(results.forall(_ == true)))
    },
    test("check returns a HealthCheckResult with correct structure") {
      for {
        check <- ZIO
          .service[S3HealthCheck]
          .provide(testAwsConfigLayer, S3HealthCheck.layer)
        result <- check.check
      } yield assertTrue(
        result.name == "s3",
        result.status == HealthStatus
          .Healthy || result.status == HealthStatus.Unhealthy,
        result.message.nonEmpty,
        result.durationMs >= 0
      )
    }
  )
