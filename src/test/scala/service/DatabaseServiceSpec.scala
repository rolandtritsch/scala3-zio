package org.roland.scala3_zio_template.service

import org.roland.scala3_zio_template.config.DatabaseConfig

import zio._
import zio.test._

/** Test suite for DatabaseService and DatabaseConfig.
  *
  * As of the config refactoring (commit bfef69f), tests provide configuration
  * via direct case class instantiation rather than environment variables. This
  * approach ensures:
  *   - Test isolation (each test has its own config)
  *   - Determinism (tests don't depend on environment)
  *   - Simplicity (no need to mock environment variables)
  *   - Type safety (configuration is validated at compile time)
  *
  * For integration tests requiring a real database connection, use
  * ZLayer.succeed(DatabaseConfig(...)) to provide the test configuration.
  */
object DatabaseServiceSpec extends ZIOSpecDefault:

  case class MockDatabaseService(shouldSucceed: Boolean)
      extends DatabaseService:
    override def healthCheck(): ZIO[Any, Throwable, Boolean] =
      if shouldSucceed then ZIO.succeed(true)
      else ZIO.fail(new RuntimeException("Mock database failure"))

  def spec = suite("DatabaseService")(
    suite("DatabaseConfig")(
      test("should build correct JDBC URL with default port") {
        val config = DatabaseConfig(
          host = "localhost",
          port = 5432,
          name = "testdb",
          user = "user",
          password = "pass"
        )
        assertTrue(config.jdbcUrl == "jdbc:postgresql://localhost:5432/testdb")
      },
      test("should build correct JDBC URL with custom port") {
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
      test("should preserve all configuration fields") {
        val config = DatabaseConfig(
          host = "testhost",
          port = 9999,
          name = "testname",
          user = "testuser",
          password = "testpass"
        )
        assertTrue(
          config.host == "testhost",
          config.port == 9999,
          config.name == "testname",
          config.user == "testuser",
          config.password == "testpass"
        )
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
      test("healthCheck should return boolean on success") {
        val service = MockDatabaseService(shouldSucceed = true)
        for {
          result <- service.healthCheck()
        } yield assertTrue(result match {
          case _: Boolean => true; case _ => false
        })
      },
      test("healthCheck should throw on failure") {
        val service = MockDatabaseService(shouldSucceed = false)
        for {
          result <- service.healthCheck().exit
        } yield assertTrue(result.isFailure)
      }
    ),
    suite("DatabaseService interface")(
      test("MockDatabaseService implements DatabaseService trait") {
        val service: DatabaseService = MockDatabaseService(shouldSucceed = true)
        assertTrue(service match {
          case _: DatabaseService => true; case _ => false
        })
      },
      test("healthCheck returns ZIO effect") {
        val service = MockDatabaseService(shouldSucceed = true)
        val effect = service.healthCheck()
        assertTrue(effect match {
          case _: ZIO[Any, Throwable, Boolean] => true; case _ => false
        })
      }
    )
  )
