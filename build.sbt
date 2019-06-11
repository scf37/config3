lazy val compilerOptions = Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-deprecation",
      "-language:implicitConversions",
      "-language:higherKinds",
      "-unchecked",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Xlint",
      "-language:_"/*,
  "-Xfatal-warnings"*/
)


val config3 = project.in(file("."))
    .settings(
      name := "config3",
      organization := "me.scf37.config3",
      resolvers += "Scf37" at "https://dl.bintray.com/scf37/maven/",

      crossScalaVersions := Seq("2.12.8", "2.13.0"),
      libraryDependencies += "com.typesafe" % "config" % "1.3.2",
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test",

      scalacOptions := compilerOptions,

      releaseTagComment := s"[ci skip]Releasing ${(version in ThisBuild).value}",
      releaseCommitMessage := s"[ci skip]Setting version to ${(version in ThisBuild).value}",
      resourceGenerators in Compile += buildProperties,

      bintrayOmitLicense := true,

      bintrayVcsUrl := Some("git@github.com:scf37/config3.git")

    )