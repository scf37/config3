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
      resolvers += "Scf37" at "https://dl.bintray.com/scf37/maven/",

      crossScalaVersions := Seq("2.12.10", "2.13.1"),
      libraryDependencies += "com.typesafe" % "config" % "1.3.2",
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test",

      scalacOptions := compilerOptions,

      resourceGenerators in Compile += buildProperties,

      publishSettings
    )

lazy val publishSettings = Seq(
      organization := "me.scf37",
      publishMavenStyle := true,
      description := "Scala configuration library",
      sources in (Compile, doc) := Seq.empty,
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