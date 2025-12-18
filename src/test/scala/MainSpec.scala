package org.roland.scala3_zio_template

import zio._
import zio.test._

object MainSpec extends ZIOSpecDefault:

  def spec = suite("Main")(
    test("program should log application lifecycle messages") {
      for {
        _ <- Main.program
        logOutput <- ZTestLogger.logOutput
      } yield assertTrue(
        logOutput.exists(_.message() == "Application started"),
        logOutput.exists(_.message() == "Processing user request"),
        logOutput.exists(_.message() == "Starting work simulation"),
        logOutput.exists(_.message() == "Work completed"),
        logOutput.exists(_.message() == "Application completed successfully")
      )
    },
    test("program should annotate logs with context") {
      for {
        _ <- Main.program
        logOutput <- ZTestLogger.logOutput
      } yield {
        val userRequestLog =
          logOutput.find(_.message() == "Processing user request")
        assertTrue(
          userRequestLog.isDefined,
          userRequestLog.get.annotations.get("userId").contains("12345")
        )
      }
    },
    test("simulateWork should log work simulation lifecycle") {
      for {
        _ <- Main.simulateWork()
        logOutput <- ZTestLogger.logOutput
      } yield assertTrue(
        logOutput.exists(_.message() == "Starting work simulation"),
        logOutput.exists(_.message() == "Work completed")
      )
    },
    test("simulateWork should annotate with operation context") {
      for {
        _ <- Main.simulateWork()
        logOutput <- ZTestLogger.logOutput
      } yield {
        val workCompletedLog =
          logOutput.find(_.message() == "Work completed")
        assertTrue(
          workCompletedLog.isDefined,
          workCompletedLog
            .get
            .annotations
            .get("operation")
            .contains("calculation")
        )
      }
    }
  )
