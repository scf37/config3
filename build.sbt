val config3 = project.in(file("."))
    .settings(
      name := "config3",
      organization := "me.scf37.config3",
      resolvers += "Scf37" at "https://dl.bintray.com/scf37/maven/",

      scalaVersion := "2.12.7",
      libraryDependencies += "com.typesafe" % "config" % "1.3.2",
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",

      releaseTagComment := s"[ci skip]Releasing ${(version in ThisBuild).value}",
      releaseCommitMessage := s"[ci skip]Setting version to ${(version in ThisBuild).value}",
      resourceGenerators in Compile += buildProperties,

      bintrayOmitLicense := true,

      bintrayVcsUrl := Some("git@github.com:scf37/config3.git")

    )