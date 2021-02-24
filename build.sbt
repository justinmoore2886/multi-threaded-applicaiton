val UNH_CS_Repository = "UNH/CS repository" at "http://cs.unh.edu/~charpov/lib"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
val scalactic = "org.scalactic" %% "scalactic" % "3.0.5"
val UNH_CS = "edu.unh.cs" % "classutils" % "1.3.4"

lazy val root = (project in file(".")).
  settings(
    name := "Inverted-Index",
    version := "1.0.0",
    scalaVersion := "2.12.8",

    resolvers += UNH_CS_Repository,

    libraryDependencies ++= Seq(
      scalaTest % Test,
      UNH_CS % Test,
      scalactic
    ),

    crossPaths := false,

    Test / logBuffered := false,
    Test / fork := true,
    Test / parallelExecution := false,
    Test / javaOptions += "-Xmx8G",

    scalacOptions ++= Seq(
      "-deprecation", // Emit warning and location for usages of deprecated APIs.
      "-encoding", "utf-8", // Specify character encoding used by source files.
      "-explaintypes", // Explain type errors in more detail.
      "-feature", // Emit warning and location for usages of features that should be imported explicitly.
      "-unchecked", // Enable additional warnings where generated code depends on assumptions.
      "-Xfuture" // Turn on future language features.
    )
  )