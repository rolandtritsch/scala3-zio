//| mvnDeps: ["com.goyeau::mill-scalafix::0.6.0"]

import mill._
import mill.scalalib._
import mill.scalalib.scalafmt.ScalafmtModule
import com.goyeau.mill.scalafix.ScalafixModule

object app extends ScalaModule with ScalafmtModule with ScalafixModule {
  def scalaVersion = "3.3.4"

  def scalacOptions = Seq("-Wunused:imports")

  def mvnDeps = Seq(
    mvn"dev.zio::zio:2.1.13",
    mvn"dev.zio::zio-streams:2.1.13"
  )

  /** Scaladoc generation options for API documentation. */
  def scaladocOptions = Seq(
    "-project", "Scala 3 ZIO Application",
    "-doc-root-content", "README.md"
  )

  object test extends ScalaTests with ScalafmtModule with ScalafixModule {
    def mvnDeps = Seq(
      mvn"dev.zio::zio:2.1.13",
      mvn"dev.zio::zio-test:2.1.13",
      mvn"dev.zio::zio-test-sbt:2.1.13"
    )

    def testFramework = "zio.test.sbt.ZTestFramework"
  }
}
