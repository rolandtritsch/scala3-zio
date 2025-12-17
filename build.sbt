ThisBuild / scalaVersion := "3.7.4"
ThisBuild / organization := "org.tritsch"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val root = (project in file(".")).settings(
  name := "scala3-zio",
  libraryDependencies ++= Seq(
    "dev.zio" %% "zio" % "2.1.13",
    "dev.zio" %% "zio-streams" % "2.1.13",
    "dev.zio" %% "zio-test" % "2.1.13" % Test,
    "dev.zio" %% "zio-test-sbt" % "2.1.13" % Test
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  scalacOptions ++= Seq(
    "-Wunused:imports"
  ),

  // Scaladoc generation options for API documentation
  Compile / doc / scalacOptions ++= Seq(
    "-project",
    "Scala 3 ZIO Application",
    "-doc-root-content",
    "README.md"
  ),

  // Scoverage configuration
  coverageMinimumStmtTotal := 60,
  coverageFailOnMinimum := true,
  coverageHighlighting := true
)
