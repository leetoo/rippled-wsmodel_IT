import sbt.Keys.resolvers
import MyCompileOptions._

ThisBuild / organization := "com.odenzo"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / version := "0.0.1"

name := "rippled-wsmodel_IT"

scalacOptions ++= Seq("-feature",
                      "-deprecation",
                      "-unchecked",
                      "-language:postfixOps",
                      "-language:higherKinds",
                      "-Ypartial-unification")


lazy val wsmodels = RootProject(file("../rippled-wsmodels"))
lazy val signing  = RootProject(file("../ripple-local-signing"))

lazy val integrationTests = (project in file("."))
  .dependsOn(wsmodels)
//  .dependsOn(signing)
  .settings(
    commonSettings,
    scalacOptions ++= opts ++ warnings ++ linters,
    libraryDependencies ++= libs,
  )


lazy val commonSettings = Seq(
  libraryDependencies ++= libs ++ lib_circe ++ lib_cats ++ lib_akka ++ lib_monocle,
  resolvers ++= Seq(
    Resolver.bintrayIvyRepo("odenzo","rippled-wsmodels"),
    Resolver.defaultLocal,                      // Usual I pulishLocal to Ivy not maven
    Resolver.jcenterRepo,                       // This is JFrogs Maven Repository for reading
  )
)
val devSettings = Seq(
  Test / logBuffered := true,
  Test / parallelExecution := false,
)

/**
  * Approach to the build, which was formerly a ScalaJS and Scala cross build.
  * Have source library in Scala, with associated unit testing (ScalaTest)
  * Have an integration testing module, uses Akka/AkkaHttp and a dummy Ripple Server.
  * Integration testing scope is "it"
  *
  */
//
//import sbt.errorssummary.Plugin.autoImport._
//reporterConfig := reporterConfig.value.withColors(true)
//reporterConfig := reporterConfig.value.withShortenPaths(true)
//reporterConfig := reporterConfig.value.withColumnNumbers(true)
/** These are the base libraries used JVM
  * In addition it needs to use the library provided by rippled-utils multiproject module.
  * */
val libs = {
  Seq(
    "org.scalatest"              %% "scalatest"      % "3.0.7" % Test,
    "org.scalacheck"             %% "scalacheck"     % "1.14.0" % Test,
    "com.typesafe"               % "config"          % "1.3.3", //  https://github.com/typesafehub/config
    "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.2",
    "ch.qos.logback"             % "logback-classic" % "1.2.3",
  )
}

/** JSON Libs == Circe and Associated Support Libs */
val lib_circe = {
  val circeVersion = "0.11.1"

  Seq(
    "io.circe" %% "circe-core"           % circeVersion,
    "io.circe" %% "circe-generic"        % circeVersion,
    "io.circe" %% "circe-java8"          % circeVersion,
    "io.circe" %% "circe-parser"         % circeVersion,
    "io.circe" %% "circe-generic-extras" % circeVersion,
    "com.beachape" %% "enumeratum"       % "1.5.13",
    "com.beachape" %% "enumeratum-circe" % "1.5.21",
  )

}

/** Akka Actors and HTTP */
val lib_akka = {

  val akkaVersion     = "2.5.21"
  val akkaHttpVersion = "10.1.8"

  Seq(
    "com.typesafe.akka" %% "akka-actor"        % akkaVersion     ,
    "com.typesafe.akka" %% "akka-actor-typed"  % akkaVersion     ,
    "com.typesafe.akka" %% "akka-persistence"  % akkaVersion     ,
    "com.typesafe.akka" %% "akka-slf4j"        % akkaVersion     ,
    "com.typesafe.akka" %% "akka-stream"       % akkaVersion     ,
    "com.typesafe.akka" %% "akka-http-core"    % akkaHttpVersion ,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
    "com.typesafe.akka" %% "akka-testkit"      % akkaVersion     % Test
  )

}

val lib_cats = {
  val catsVersion = "1.6.0"
  Seq(
    "org.typelevel" %% "cats-core"   % catsVersion, // Cats is pulled in via Circe for now
    "org.typelevel" %% "cats-effect" % "1.2.0" withSources () withJavadoc ()
  )
}

val lib_monocle = {
  val monocleVersion = "1.5.0" // 1.5.0-cats based on cats 1.0.x

   Seq(
                               "com.github.julien-truffaut" %% "monocle-core" % monocleVersion,
                               "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion,
                               "com.github.julien-truffaut" %% "monocle-law" % monocleVersion % "test"
                               )
}