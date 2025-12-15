import zio._
import zio.test._
import zio.test.TestAspect._

object MainSpec extends ZIOSpecDefault:
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
