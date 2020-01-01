resolvers += Resolver.url("plugins", url("https://dl.bintray.com/scf37/sbt-plugins"))(Resolver.ivyStylePatterns)
addSbtPlugin("me.scf37.buildprops" % "sbt-build-properties" % "1.0.7")

addSbtPlugin("com.geirsson" % "sbt-ci-release" % "1.4.31")