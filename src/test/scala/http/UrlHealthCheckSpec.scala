package org.roland.scala3_zio_template.http

import org.roland.scala3_zio_template.config.HealthCheckConfig
import org.roland.scala3_zio_template.http.health_checks.UrlHealthCheck

import zio._
import zio.http._
import zio.test._

/** Test suite for UrlHealthCheck.
  *
  * Tests URL health check configuration and layer construction. Full
  * integration testing with real HTTP requests is performed via endpoint tests.
  *
  * These tests verify:
  *   - Layer construction with required dependencies
  *   - Name and description properties
  *   - Configuration handling
  */
object UrlHealthCheckSpec extends ZIOSpecDefault:

  private val testConfig =
    HealthCheckConfig(checkUrl = "https://test.example.com")
  private val testConfigLayer = ZLayer.succeed(testConfig)

  def spec = suite("UrlHealthCheck")(
    test("should construct layer successfully with required dependencies") {
      for {
        check <- ZIO
          .service[UrlHealthCheck]
          .provide(
            testConfigLayer,
            Client.default,
            ZLayer.succeed(Scope.global),
            UrlHealthCheck.layer
          )
      } yield assertTrue(check.name == "url")
    },
    test("should have correct name") {
      for {
        check <- ZIO
          .service[UrlHealthCheck]
          .provide(
            testConfigLayer,
            Client.default,
            ZLayer.succeed(Scope.global),
            UrlHealthCheck.layer
          )
      } yield assertTrue(check.name == "url")
    },
    test("should have correct description") {
      for {
        check <- ZIO
          .service[UrlHealthCheck]
          .provide(
            testConfigLayer,
            Client.default,
            ZLayer.succeed(Scope.global),
            UrlHealthCheck.layer
          )
      } yield assertTrue(
        check
          .description == "Validates external URL availability via HTTP GET request"
      )
    },
    test("should work with different URLs") {
      val configs = List(
        HealthCheckConfig("https://example.com"),
        HealthCheckConfig("http://localhost:8080"),
        HealthCheckConfig("https://api.example.com/health")
      )

      ZIO
        .foreach(configs) { config =>
          for {
            check <- ZIO
              .service[UrlHealthCheck]
              .provide(
                ZLayer.succeed(config),
                Client.default,
                ZLayer.succeed(Scope.global),
                UrlHealthCheck.layer
              )
          } yield check.name == "url"
        }
        .map(results => assertTrue(results.forall(_ == true)))
    }
  )
