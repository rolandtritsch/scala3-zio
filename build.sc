import mill._, scalalib._

object app extends RootModule with ScalaModule {
  def scalaVersion = "3.3.4"
  
  def ivyDeps = Agg(
    ivy"dev.zio::zio:2.1.13",
    ivy"dev.zio::zio-streams:2.1.13"
  )

  object test extends ScalaTests with TestModule.Utest {
    def ivyDeps = Agg(
      ivy"dev.zio::zio-test:2.1.13",
      ivy"dev.zio::zio-test-sbt:2.1.13"
    )
  }
}
