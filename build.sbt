import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings

enablePlugins(JavaServerAppPackaging)
enablePlugins(DockerPlugin)

val akkaVersion = "2.5.25"
val akkaHttpVersion = "10.1.9"
val circeVersion = "0.12.1"
val monocleVersion = "2.0.0"
val log4jVersion        = "2.11.2"
val log4jApiScalaVersion = "11.0"
val pureConfigVersion    = "0.12.1"
val akkaManagementVersion = "1.0.5"
val catsVersion = "2.0.0"
val scalaTestVersion = "3.1.0"
val mongoPersistencePluginVersion = "2.3.1"
val scalaMongoDriverVersion = "2.7.0"
val matryoshkaV = "0.21.3"

lazy val `akka-sample-cluster-scala` = project
  .in(file("."))
  .settings(multiJvmSettings: _*)
  .settings(
    organization := "net.atos.git.ncf",
    scalaVersion := "2.12.6",
    scalacOptions in Compile ++= Seq(
      "-encoding", "UTF-8",   // source files are in UTF-8
      "-deprecation", // warn about use of deprecated APIs
      "-feature", // warn about misused language features
      "-unchecked", // warn about unchecked type parameters
      "-Xlog-reflective-calls",
      //"-Xlint",
      "-language:higherKinds",// allow higher kinded types without `import scala.language.higherKinds`
      "-Ypartial-unification" // allow the compiler to unify type constructors of different arities
    ),
    javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions in run ++= Seq("-Xms128m", "-Xmx1024m", "-Djava.library.path=./target/native"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka"               %% "akka-actor"                     % akkaVersion,
      "com.typesafe.akka"               %% "akka-remote"                    % akkaVersion,
      "com.typesafe.akka"               %% "akka-cluster"                   % akkaVersion,
      "com.typesafe.akka"               %% "akka-cluster-metrics"           % akkaVersion,
      "com.typesafe.akka"               %% "akka-cluster-tools"             % akkaVersion,
      "com.typesafe.akka"               %% "akka-multi-node-testkit"        % akkaVersion,
      "com.typesafe.akka"               %% "akka-slf4j"                     % akkaVersion,
      "com.typesafe.akka"               %% "akka-stream"                    % akkaVersion,
      "com.typesafe.akka"               %% "akka-persistence"               % akkaVersion,
      "com.typesafe.akka"               %% "akka-discovery"                 % akkaVersion,
      "com.github.scullxbones"          %% "akka-persistence-mongo-scala"   % mongoPersistencePluginVersion,
      "org.mongodb.scala"               %% "mongo-scala-driver"             % scalaMongoDriverVersion,



      "org.scalatest"                   %% "scalatest"                      % scalaTestVersion % Test,

      "com.lightbend.akka.management"   %% "akka-management"                % akkaManagementVersion,
      "com.lightbend.akka.management"   %% "akka-management-cluster-http"   % akkaManagementVersion,
      "com.lightbend.akka.discovery"    %% "akka-discovery-kubernetes-api"  % akkaManagementVersion,
      "com.lightbend.akka.management"   %% "akka-management-cluster-bootstrap" % akkaManagementVersion,


      "io.circe"                        %% "circe-core"                     % circeVersion,
      "io.circe"                        %% "circe-generic"                  % circeVersion,
      "io.circe"                        %% "circe-parser"                   % circeVersion,

      "org.typelevel"                   %% "cats-core"                      % catsVersion,

      "com.github.julien-truffaut"      %%  "monocle-core"                  % monocleVersion,
      "com.github.julien-truffaut"      %%  "monocle-macro"                 % monocleVersion,
      "com.github.julien-truffaut"      %%  "monocle-unsafe"                % monocleVersion,
      "com.github.julien-truffaut"      %%  "monocle-law"                   % monocleVersion % "test",

      "org.apache.logging.log4j"        %  "log4j-api"                      % log4jVersion,
      "org.apache.logging.log4j"        % "log4j-core"                      % log4jVersion,
      "org.apache.logging.log4j"        %  "log4j-slf4j-impl"               % log4jVersion,
      "org.apache.logging.log4j"        %% "log4j-api-scala"                % log4jApiScalaVersion,

      "com.github.pureconfig"           %% "pureconfig"                     % pureConfigVersion,
      "com.github.pureconfig"           %% "pureconfig-akka"                % pureConfigVersion,
      "com.github.pureconfig"           %% "pureconfig-cats"                % pureConfigVersion,
      "com.github.pureconfig"           %% "pureconfig-circe"               % pureConfigVersion,

      "io.kamon"                        % "sigar-loader"                    % "1.6.6-rev002",

      "com.slamdata"                    %% "matryoshka-core"                % matryoshkaV
    ),
    fork in run := true,
    version := "0.1",
    parallelExecution in Test := false,
    licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0"))),
    mainClass in (Compile, run) := Some("sample.cluster.k8s.Runner"),
    discoveredMainClasses in Compile := Seq("sample.cluster.k8s.Runner"),
    dockerExposedPorts := Seq(9000, 8558, 2552),
    dockerBaseImage := "java:openjdk-8",
    maintainer := "tpaesen",
    packageName in Docker := "cluster-vizualizer"
  )

