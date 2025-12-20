package org.roland.scala3_zio_template.service

import org.roland.scala3_zio_template.config.DatabaseConfig

import zio._
import zio.test._

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
