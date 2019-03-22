publishMavenStyle := false
//
//bintrayRepository := (if (isSnapshot.value) "sbt-plugin-snapshots" else "sbt-plugins")
//
//bintrayOrganization in bintray := None
//
//bintrayReleaseOnPublish := isSnapshot.value
enablePlugins(JavaAppPackaging)

licenses += (("BSD 3-Clause", url("https://github.com/rtimush/sbt-updates/blob/master/LICENSE")))

/*
 publish and publishLocal Tasks are reasonable for developmnet.

 publishTo can go to Maven and there is a seperate JFrog/Bintray plugin to publish too


 I would like to disable Scaladoc in the publishLocal.
 */




licenses := List(
    ("BSD", url("https://www.apache.org/licenses/LICENSE-2.0"))
  )
homepage := Some(url("https://github.com/odenzoorg/rippled-wstest"))



credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
publishMavenStyle := false
// Resolver.url("raisercostin ivy resolver", url("http://dl.bintray.com/raisercostin/maven"))(Resolver.ivyStylePatterns)

// Not publish sources and/or Javadoc
//packagedArtifacts in publish ~= { m =>
//  val classifiersToExclude = Set(
//    Artifact.SourceClassifier,
//    Artifact.DocClassifier
//  )
//  m.filter { case (art, _) =>
//    art.classifier.forall(c => !classifiersToExclude.contains(c))
//  }
//}

// The plain URL for browsder:
// https://bintray.com/odenzoorg/odenzooss/rippled-wsmodels


//publishArtifact in Test := true // to add the tests JAR
publishArtifact in Test := false
publishMavenStyle := false
bintrayOrganization := Some("odenzoorg")
bintrayRepository := "odenzooss"
bintrayPackage := "rippled-wstest"
bintrayReleaseOnPublish in ThisBuild := false
