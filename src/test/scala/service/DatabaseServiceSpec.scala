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
      test("should build correct JDBC URL") {
        val config = DatabaseConfig(
          host = "localhost",
          port = 5432,
          name = "testdb",
          user = "user",
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
      }
    )
  )
