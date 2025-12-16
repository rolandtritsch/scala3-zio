import zio._

object Main extends ZIOAppDefault:
  val program: ZIO[Any, Nothing, Unit] = for {
    _ <- Console
      .printLine("Hello from ZIO!")
      .catchAll(err => ZIO.succeed(println(s"Error: $err")))
    _ <- Console
      .printLine("Welcome to Scala 3 with ZIO 2")
      .catchAll(err => ZIO.succeed(println(s"Error: $err")))
  } yield ()

  def run = program
