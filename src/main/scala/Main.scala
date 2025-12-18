package org.roland.scala3_zio_template

import zio._
import zio.logging.backend.SLF4J

/** Main application entry point demonstrating ZIO 2 with Scala 3.
  *
  * This is a simple console application that demonstrates structured JSON
  * logging using ZIO's effect system with proper error handling.
  */
object Main extends ZIOAppDefault:

  /** Configure JSON logging to stdout.
    *
    * Removes default loggers and sets up JSON-formatted logging for structured
    * output via SLF4J backend (configured in logback.xml).
    */
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  /** The main program workflow demonstrating structured logging.
    *
    * Executes operations with structured logging including info, debug, and
    * error levels with contextual annotations.
    *
    * @return
    *   A ZIO effect that demonstrates logging and always succeeds
    */
  val program: ZIO[Any, Nothing, Unit] = for {
    _ <- ZIO.logInfo("Application started")
    _ <- ZIO.logInfo("Processing user request") @@ ZIOAspect.annotated(
      "userId",
      "12345"
    )
    _ <- ZIO.logDebug("Debug information") @@ ZIOAspect.annotated(
      "component",
      "main"
    )
    _ <- simulateWork()
    _ <- ZIO.logInfo("Application completed successfully")
  } yield ()

  /** Simulates some work with structured logging.
    *
    * @return
    *   A ZIO effect that simulates work with annotations
    */
  def simulateWork(): ZIO[Any, Nothing, Unit] = for {
    _ <- ZIO.logInfo("Starting work simulation")
    _ <- ZIO.logInfo("Work completed") @@ ZIOAspect.annotated(
      "operation",
      "calculation"
    )
  } yield ()

  /** Runs the main program.
    *
    * Entry point for the ZIO application that executes the program workflow.
    *
    * @return
    *   The program effect to execute
    */
  def run = program
