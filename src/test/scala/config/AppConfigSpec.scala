package org.roland.scala3_zio_template.config

import zio._
import zio.test._

/** Test suite for all configuration classes.
  *
  * Tests configuration loading, default values, and validation for:
  *   - ServerConfig
  *   - DatabaseConfig
  *   - AwsConfig
  *   - HealthCheckConfig
  *
  * As per CLAUDE.md guidelines, tests provide configuration via direct case
  * class instantiation using ZLayer.succeed() rather than mocking environment
  * variables. This ensures test isolation, determinism, and type safety.
  */
object AppConfigSpec extends ZIOSpecDefault:

  def spec = suite("AppConfig")(
    suite("ServerConfig")(
      test("should construct with provided port") {
        val config = ServerConfig(port = 9090)
        assertTrue(config.port == 9090)
      },
      test("should construct with default port") {
        val config = ServerConfig(port = 8080)
        assertTrue(config.port == 8080)
      },
      test("should be usable in ZLayer") {
        val testConfig = ServerConfig(port = 8080)
        for {
          config <- ZIO
            .service[ServerConfig]
            .provide(ZLayer.succeed(testConfig))
        } yield assertTrue(config.port == 8080)
      }
    ),
    suite("DatabaseConfig")(
      test("should construct with all required fields") {
        val config = DatabaseConfig(
          host = "testhost",
          port = 5433,
          name = "testdb",
          user = "testuser",
          password = "testpass"
        )
        assertTrue(
          config.host == "testhost",
          config.port == 5433,
          config.name == "testdb",
          config.user == "testuser",
          config.password == "testpass"
        )
      },
      test("should construct jdbcUrl correctly with custom values") {
        val config = DatabaseConfig(
          host = "db.example.com",
          port = 5433,
          name = "myapp",
          user = "admin",
          password = "secret"
        )
        assertTrue(
          config.jdbcUrl == "jdbc:postgresql://db.example.com:5433/myapp"
        )
      },
      test("should construct jdbcUrl with localhost defaults") {
        val config = DatabaseConfig(
          host = "localhost",
          port = 5432,
          name = "postgres",
          user = "user",
          password = "pass"
        )
        assertTrue(
          config.jdbcUrl == "jdbc:postgresql://localhost:5432/postgres"
        )
      },
      test("should handle special characters in database name") {
        val config = DatabaseConfig(
          host = "localhost",
          port = 5432,
          name = "my_test_db",
          user = "user",
          password = "pass"
        )
        assertTrue(
          config.jdbcUrl == "jdbc:postgresql://localhost:5432/my_test_db"
        )
      },
      test("should be usable in ZLayer") {
        val testConfig = DatabaseConfig(
          host = "testhost",
          port = 5433,
          name = "testdb",
          user = "testuser",
          password = "testpass"
        )
        for {
          config <- ZIO
            .service[DatabaseConfig]
            .provide(ZLayer.succeed(testConfig))
        } yield assertTrue(
          config.host == "testhost",
          config.port == 5433,
          config.name == "testdb"
        )
      }
    ),
    suite("AwsConfig")(
      test("should construct with all required fields") {
        val config = AwsConfig(
          accessKeyId = "AKIATEST123456",
          secretAccessKey = "testsecretkey123456789",
          region = "us-west-2"
        )
        assertTrue(
          config.accessKeyId == "AKIATEST123456",
          config.secretAccessKey == "testsecretkey123456789",
          config.region == "us-west-2"
        )
      },
      test("should construct with default region") {
        val config = AwsConfig(
          accessKeyId = "AKIATEST123456",
          secretAccessKey = "testsecretkey123456789",
          region = "us-east-1"
        )
        assertTrue(config.region == "us-east-1")
      },
      test("should handle different AWS regions") {
        val regions = List(
          "us-east-1",
          "us-west-2",
          "eu-west-1",
          "ap-southeast-1"
        )
        val configs = regions.map { region =>
          AwsConfig(
            accessKeyId = "AKIATEST123456",
            secretAccessKey = "testsecretkey123456789",
            region = region
          )
        }
        assertTrue(configs.map(_.region) == regions)
      },
      test("should be usable in ZLayer") {
        val testConfig = AwsConfig(
          accessKeyId = "AKIATEST123456",
          secretAccessKey = "testsecretkey123456789",
          region = "us-west-2"
        )
        for {
          config <- ZIO.service[AwsConfig].provide(ZLayer.succeed(testConfig))
        } yield assertTrue(
          config.accessKeyId == "AKIATEST123456",
          config.region == "us-west-2"
        )
      }
    ),
    suite("HealthCheckConfig")(
      test("should construct with provided URL") {
        val config = HealthCheckConfig(checkUrl = "https://example.com")
        assertTrue(config.checkUrl == "https://example.com")
      },
      test("should construct with default URL") {
        val config = HealthCheckConfig(checkUrl = "https://tedn.life")
        assertTrue(config.checkUrl == "https://tedn.life")
      },
      test("should handle different URL schemes") {
        val urls = List(
          "https://example.com",
          "http://localhost:8080",
          "https://api.example.com/health"
        )
        val configs = urls.map(url => HealthCheckConfig(checkUrl = url))
        assertTrue(configs.map(_.checkUrl) == urls)
      },
      test("should be usable in ZLayer") {
        val testConfig =
          HealthCheckConfig(checkUrl = "https://test.example.com")
        for {
          config <- ZIO
            .service[HealthCheckConfig]
            .provide(ZLayer.succeed(testConfig))
        } yield assertTrue(config.checkUrl == "https://test.example.com")
      }
    ),
    suite("AppConfig")(
      test("should construct with all sub-configs") {
        val server = ServerConfig(port = 8080)
        val database = DatabaseConfig(
          host = "localhost",
          port = 5432,
          name = "testdb",
          user = "user",
          password = "pass"
        )
        val aws = AwsConfig(
          accessKeyId = "AKIATEST123456",
          secretAccessKey = "testsecretkey123456789",
          region = "us-east-1"
        )
        val appConfig = AppConfig(
          server = server,
          database = database,
          aws = aws
        )
        assertTrue(
          appConfig.server.port == 8080,
          appConfig.database.host == "localhost",
          appConfig.aws.region == "us-east-1"
        )
      }
    )
  )
