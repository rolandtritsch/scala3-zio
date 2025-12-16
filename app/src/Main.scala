import zio._

/** Main application entry point demonstrating ZIO 2 with Scala 3.
  *
  * This is a simple console application that prints welcome messages using ZIO's
  * effect system with proper error handling.
  */
object Main extends ZIOAppDefault:

  /** The main program workflow that prints welcome messages to the console.
    *
    * Executes two console operations sequentially with error handling for each.
    * All errors are caught and handled gracefully without failing the program.
    *
    * @return A ZIO effect that prints messages and always succeeds
    */
  val program: ZIO[Any, Nothing, Unit] = for {
    _ <- Console
      .printLine("Hello from ZIO!")
      .catchAll(err => ZIO.succeed(println(s"Error: $err")))
    _ <- Console
      .printLine("Welcome to Scala 3 with ZIO 2")
      .catchAll(err => ZIO.succeed(println(s"Error: $err")))
  } yield ()

  /** Runs the main program.
    *
    * Entry point for the ZIO application that executes the program workflow.
    *
    * @return The program effect to execute
    */
  def run = program
