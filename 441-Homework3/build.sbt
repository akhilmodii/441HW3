name := "441-Homework3"

version := "0.1"

scalaVersion := "2.12.8"


//assemblyMergeStrategy in assembly := {
//  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
//  case PathList("reference.conf", xs @ _*) => MergeStrategy.concat
//  case x => MergeStrategy.first
//}
//
//libraryDependencies += "org.scala-lang" % "scala-actors" % "2.11.7"
//
//libraryDependencies ++= Seq(
//
//  "com.typesafe" % "config" % "1.3.2",
//
//  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
//
//  "ch.qos.logback" % "logback-classic" % "1.2.3",
//
//  "org.scalactic" %% "scalactic" % "3.0.8",
//
//  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
//
//  "org.scala-lang.modules" %% "scala-xml" % "1.1.1",
//
//  // Akka dependency for akka actors
//  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.0",
//
//  // Akka dependency for creating and managing clusters
//  "com.typesafe.akka" %% "akka-cluster-typed" % "2.6.0",
//
//  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.0" % Test,
//
//  "com.typesafe.akka" %% "akka-remote" % "2.6.0",
//
//  "com.typesafe.akka" %% "akka-http" % "10.1.11",
//
//  "com.typesafe.akka" %% "akka-stream" % "2.6.0",
//
//  "com.typesafe.akka" %% "akka-stream-testkit" % "2.6.0",
//
//  "com.typesafe.akka" %% "akka-http-testkit" % "10.1.11"
//
//)
//
//
//mainClass in(Compile, run) := Some("com.akka.WebService")
//mainClass in assembly := Some("com.akka.WebService")
//
//enablePlugins(DockerPlugin)


lazy val akkaVersion = "2.5.26"
lazy val akkaManagement = "1.0.5"

fork in Test := true

//libraryDependencies += "com.typesafe" % "config" % "1.4.1"

//val AkkaManagementVersion = "1.0.9"
libraryDependencies += "com.lightbend.akka.management" %% "akka-management" % akkaManagement

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster"                                   % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools"                             % akkaVersion,
  "com.lightbend.akka.management" %% "akka-management"                    % akkaManagement,
  "com.lightbend.akka.management" %% "akka-management-cluster-http"       % akkaManagement,

  "com.typesafe.akka" %% "akka-slf4j"                                     % akkaVersion,
  "ch.qos.logback"    %  "logback-classic"                                % "1.2.3",

  // test dependencies
  "com.typesafe.akka" %% "akka-testkit"                                   % akkaVersion        % "test",
  "org.scalatest"     %% "scalatest"                                      % "3.0.1"            % "test",
  "commons-io"        %  "commons-io"                                     % "2.4"              % "test")


mainClass in (Compile, run) := Some("Main")