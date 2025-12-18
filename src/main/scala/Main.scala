package org.roland.scala3_zio_template

import zio._
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val program: ZIO[Any, Nothing, Unit] = for {
    _ <- ZIO.logInfo("Server starting ...")
    _ <- simulateWork()
    _ <- ZIO.logInfo("Server stopped!")
  } yield ()

  def simulateWork(): ZIO[Any, Nothing, Unit] = for {
  } yield ()

  def run = program
