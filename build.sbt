ThisBuild / scalaVersion := "3.7.4"
ThisBuild / organization := "org.tritsch"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val root = (project in file(".")).settings(
  name := "scala3-zio-template",
  run / fork := true,
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.4.14",
    "dev.zio" %% "zio-http" % "3.6.0",
    "dev.zio" %% "zio-logging-slf4j2" % "2.3.2",
    "dev.zio" %% "zio-logging" % "2.3.2",
    "dev.zio" %% "zio-streams" % "2.1.13",
    "dev.zio" %% "zio-test-sbt" % "2.1.13" % Test,
    "dev.zio" %% "zio-test" % "2.1.13" % Test,
    "dev.zio" %% "zio" % "2.1.13",
    "net.logstash.logback" % "logstash-logback-encoder" % "7.4",
    "dev.zio" %% "zio-aws-s3" % "7.40.10.1",
    "dev.zio" %% "zio-aws-netty" % "7.40.10.1"
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
  coverageMinimumStmtTotal := 70,
  coverageFailOnMinimum := true,
  coverageHighlighting := true,

  // Assembly configuration for creating fat JARs
  assembly / assemblyJarName := "scala3-zio-template.jar",
  assembly / mainClass := Some("org.roland.scala3_zio_template.Main"),
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case PathList("module-info.class") => MergeStrategy.discard
    case PathList("io", "netty", xs @ _*) =>
      MergeStrategy.first // Netty version conflicts - use first found
    case PathList("scala", "annotation", xs @ _*) =>
      MergeStrategy.first // Scala annotation conflicts - prefer standard library
    case "application.conf"     => MergeStrategy.concat
    case "reference.conf"       => MergeStrategy.concat
    case PathList("logback.xml") => MergeStrategy.first
    case x if x.endsWith(".proto") => MergeStrategy.first
    case x if x.contains("io.netty.versions.properties") =>
      MergeStrategy.first
    case _ => MergeStrategy.deduplicate
  }
)
