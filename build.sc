import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule

object app extends ScalaModule with ScalafmtModule {
  def scalaVersion = "3.3.4"

  def mvnDeps = Seq(
    mvn"dev.zio::zio:2.1.13",
    mvn"dev.zio::zio-streams:2.1.13"
  )

  object test extends ScalaTests with ScalafmtModule {
    def mvnDeps = Seq(
      mvn"dev.zio::zio:2.1.13",
      mvn"dev.zio::zio-test:2.1.13",
      mvn"dev.zio::zio-test-sbt:2.1.13"
    )

    def testFramework = "zio.test.sbt.ZTestFramework"
  }
}
