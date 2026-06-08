ThisBuild / scalaVersion := "3.4.2"
ThisBuild / version      := "0.1.0"
ThisBuild / organization := "com.memoryforge"

lazy val zioVersion = "2.1.9"

lazy val root = (project in file("."))
  .settings(
    name := "memoryforge",
    Compile / mainClass := Some("memoryforge.Main"),
    libraryDependencies ++= Seq(
      "dev.zio"        %% "zio"           % zioVersion,
      "dev.zio"        %% "zio-streams"   % zioVersion,
      "dev.zio"        %% "zio-http"      % "3.0.1",
      "dev.zio"        %% "zio-json"      % "0.7.3",
      "com.zaxxer"      % "HikariCP"      % "5.1.0",
      "org.postgresql"  % "postgresql"    % "42.7.4",
      "ch.qos.logback"  % "logback-classic" % "1.5.6"
    ),
    // Fat-jar assembly settings (used by the Docker build)
    assembly / assemblyJarName := "memoryforge.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case "reference.conf"                     => MergeStrategy.concat
      case "module-info.class"                  => MergeStrategy.discard
      case _                                    => MergeStrategy.first
    }
  )
