package org.roland.scala3_zio_template.service

import zio._
import zio.test._

object DatabaseServiceSpec extends ZIOSpecDefault:

  case class MockDatabaseService(shouldSucceed: Boolean)
      extends DatabaseService:
    override def healthCheck(): ZIO[Any, Throwable, Boolean] =
      if shouldSucceed then ZIO.succeed(true)
      else ZIO.fail(new RuntimeException("Mock database failure"))

    override def execute(sql: String): ZIO[Any, Throwable, Unit] =
      if shouldSucceed then ZIO.unit
      else ZIO.fail(new RuntimeException("Mock execute failure"))

    override def selectOne[T](sql: String): ZIO[Any, Throwable, Option[T]] =
      if shouldSucceed then ZIO.succeed(None)
      else ZIO.fail(new RuntimeException("Mock selectOne failure"))

    override def selectAll[T](sql: String): ZIO[Any, Throwable, List[T]] =
      if shouldSucceed then ZIO.succeed(List.empty)
      else ZIO.fail(new RuntimeException("Mock selectAll failure"))

  def spec = suite("DatabaseService")(
    suite("loadConfig")(
      test("should load config from environment variables") {
        for {
          _ <- TestSystem.putEnv("DATABASE_HOST", "testhost")
          _ <- TestSystem.putEnv("DATABASE_PORT", "5433")
          _ <- TestSystem.putEnv("DATABASE_NAME", "testdb")
          _ <- TestSystem.putEnv("DATABASE_USER", "testuser")
          _ <- TestSystem.putEnv("DATABASE_PASSWORD", "testpass")
          config <- DatabaseService.loadConfig
        } yield assertTrue(
          config.host == "testhost",
          config.port == 5433,
          config.database == "testdb",
          config.username == "testuser",
          config.password == "testpass"
        )
      },
      test("should use defaults for optional values") {
        for {
          _ <- TestSystem.putEnv("DATABASE_USER", "testuser")
          _ <- TestSystem.putEnv("DATABASE_PASSWORD", "testpass")
          config <- DatabaseService.loadConfig
        } yield assertTrue(
          config.host == "localhost",
          config.port == 5432,
          config.database == "postgres"
        )
      },
      test("should fail when DATABASE_USER is missing") {
        for {
          _ <- TestSystem.putEnv("DATABASE_PASSWORD", "testpass")
          result <- DatabaseService.loadConfig.exit
        } yield assertTrue(result.isFailure)
      },
      test("should fail when DATABASE_PASSWORD is missing") {
        for {
          _ <- TestSystem.putEnv("DATABASE_USER", "testuser")
          result <- DatabaseService.loadConfig.exit
        } yield assertTrue(result.isFailure)
      }
    ),
    suite("DatabaseConfig")(
      test("should build correct JDBC URL") {
        val config = DatabaseConfig(
          host = "localhost",
          port = 5432,
          database = "testdb",
          username = "user",
          password = "pass"
        )
        assertTrue(config.jdbcUrl == "jdbc:postgresql://localhost:5432/testdb")
      }
    ),
    suite("MockDatabaseService")(
      test("healthCheck should succeed when configured to succeed") {
        val service = MockDatabaseService(shouldSucceed = true)
        for {
          result <- service.healthCheck()
        } yield assertTrue(result == true)
      },
      test("healthCheck should fail when configured to fail") {
        val service = MockDatabaseService(shouldSucceed = false)
        for {
          result <- service.healthCheck().exit
        } yield assertTrue(result.isFailure)
      },
      test("execute should succeed when configured to succeed") {
        val service = MockDatabaseService(shouldSucceed = true)
        for {
          result <- service.execute("SELECT 1").exit
        } yield assertTrue(result.isSuccess)
      },
      test("execute should fail when configured to fail") {
        val service = MockDatabaseService(shouldSucceed = false)
        for {
          result <- service.execute("SELECT 1").exit
        } yield assertTrue(result.isFailure)
      }
    )
  )
