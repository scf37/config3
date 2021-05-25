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
      crossScalaVersions := Seq("2.12.10", "2.13.1", "3.0.0"),
      libraryDependencies += "com.typesafe" % "config" % "1.4.1",
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" % "test",

      scalacOptions := compilerOptions,

      Compile / resourceGenerators += buildProperties,

      publishSettings
    )

lazy val publishSettings = Seq(
      organization := "me.scf37",
      publishMavenStyle := true,
      description := "Scala configuration library",
      Compile / doc / sources := Seq.empty,
      scmInfo := Some(
            ScmInfo(
                  url("https://github.com/scf37/config3"),
                  "git@github.com:scf37/config3.git"
            )
      ),
      licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
      homepage := Some(url("https://github.com/scf37/config3")),
      developers := List(
            Developer("scf37", "Sergey Alaev", "scf370@gmail.com", url("https://github.com/scf37")),
      )
)