ThisBuild / scalaVersion := "3.7.4"
ThisBuild / organization := "org.tritsch"
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// Main project settings
lazy val root = (project in file(".")).settings(
  name := "scala3-zio-template",
  run / fork := true,
  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.4.14",
    "dev.zio" %% "zio-aws-netty" % "7.40.10.1",
    "dev.zio" %% "zio-aws-s3" % "7.40.10.1",
    "dev.zio" %% "zio-config-magnolia" % "4.0.2",
    "dev.zio" %% "zio-config-typesafe" % "4.0.2",
    "dev.zio" %% "zio-config" % "4.0.2",
    "dev.zio" %% "zio-http" % "3.6.0",
    "dev.zio" %% "zio-logging-slf4j2" % "2.3.2",
    "dev.zio" %% "zio-logging" % "2.3.2",
    "dev.zio" %% "zio-streams" % "2.1.13",
    "dev.zio" %% "zio-test-sbt" % "2.1.13" % Test,
    "dev.zio" %% "zio-test" % "2.1.13" % Test,
    "dev.zio" %% "zio" % "2.1.13",
    "io.getquill" %% "quill-jdbc-zio" % "4.8.6",
    "net.logstash.logback" % "logstash-logback-encoder" % "7.4",
    "org.postgresql" % "postgresql" % "42.7.4"
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  scalacOptions ++= Seq(
    "-Wunused:imports",
    "-deprecation"
  ),

  // Scaladoc generation options for API documentation
  Compile / doc / scalacOptions ++= Seq(
    "-project",
    "Scala3 ZIO Template",
    "-doc-root-content",
    "README.md"
  ),

  // Scoverage configuration
  coverageMinimumStmtTotal := 50,
  coverageFailOnMinimum := true,
  coverageHighlighting := true,

  // Assembly configuration for creating fat JARs
  assembly / assemblyJarName := "scala3-zio-template.jar",
  assembly / mainClass := Some("org.roland.scala3_zio_template.Main"),
  assembly / assemblyMergeStrategy := {
    case PathList("io", "getquill", xs @ _*)        => MergeStrategy.first
    case PathList("io", "netty", xs @ _*)           => MergeStrategy.first
    case PathList("logback.xml")                    => MergeStrategy.first
    case PathList("META-INF", xs @ _*)              => MergeStrategy.discard
    case PathList("module-info.class")              => MergeStrategy.discard
    case PathList("org", "jline", xs @ _*)          => MergeStrategy.first
    case PathList("scala", "annotation", xs @ _*)   => MergeStrategy.first
    case PathList("scala", "tools", "asm", xs @ _*) => MergeStrategy.first
    case "application.conf"                         => MergeStrategy.concat
    case "compiler.properties"                      => MergeStrategy.first
    case "reference.conf"                           => MergeStrategy.concat
    case "rootdoc.txt"                              => MergeStrategy.first
    case x if x.contains("io.netty.versions.properties") =>
      MergeStrategy.first
    case x if x.endsWith(".caps")  => MergeStrategy.first
    case x if x.endsWith(".class") => MergeStrategy.first
    case x if x.endsWith(".proto") => MergeStrategy.first
    case x if x.endsWith(".tasty") => MergeStrategy.first
    case _                         => MergeStrategy.deduplicate
  }
)
