import zio._
import zio.test.TestAspect._
import zio.test._

/** Test suite for the Main application.
  *
  * Verifies the behavior of the main program workflow using ZIO Test's
  * TestConsole for capturing and asserting console output.
  */
object MainSpec extends ZIOSpecDefault:

  /** Test specification suite for Main.
    *
    * Contains tests that verify the console output behavior of the main
    * program.
    *
    * @return
    *   The test suite containing all Main-related tests
    */
  def spec = suite("Main")(
    test("program should print welcome messages") {
      for {
        _ <- Main.program
        output <- TestConsole.output
      } yield assertTrue(
        output.size == 2,
        output(0) == "Hello from ZIO!\n",
        output(1) == "Welcome to Scala 3 with ZIO 2\n"
      )
    }
  )
