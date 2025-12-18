ThisBuild / scalaVersion := "3.7.4"
ThisBuild / organization := "org.tritsch"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val root = (project in file(".")).settings(
  name := "scala3-zio-template",
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.4.14",
    "dev.zio" %% "zio-http" % "3.6.0",
    "dev.zio" %% "zio-logging-slf4j2" % "2.3.2",
    "dev.zio" %% "zio-logging" % "2.3.2",
    "dev.zio" %% "zio-streams" % "2.1.13",
    "dev.zio" %% "zio-test-sbt" % "2.1.13" % Test,
    "dev.zio" %% "zio-test" % "2.1.13" % Test,
    "dev.zio" %% "zio" % "2.1.13",
    "net.logstash.logback" % "logstash-logback-encoder" % "7.4"
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  scalacOptions ++= Seq(
    "-Wunused:imports"
  ),

  // Scaladoc generation options for API documentation
  Compile / doc / scalacOptions ++= Seq(
    "-project",
    "Scala3 ZIO Template",
    "-doc-root-content",
    "README.md"
  ),

  // Scoverage configuration
  coverageMinimumStmtTotal := 80,
  coverageFailOnMinimum := true,
  coverageHighlighting := true
)
