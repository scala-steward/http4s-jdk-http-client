import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

lazy val `http4s-jdk-http-client` = project.in(file("."))
  .settings(commonSettings, releaseSettings, skipOnPublishSettings)
  .aggregate(core, docs)

lazy val core = project.in(file("core"))
  .settings(commonSettings, releaseSettings, mimaSettings)
  .settings(
    name := "http4s-jdk-http-client"
  )

lazy val docs = project.in(file("docs"))
  .settings(commonSettings, skipOnPublishSettings, micrositeSettings)
  .dependsOn(core)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(TutPlugin)

lazy val contributors = Seq(
  "ChristopherDavenport"  -> "Christopher Davenport",
  "rossabaker"            -> "Ross A. Baker",
)

val catsV = "1.6.0"
val catsEffectV = "1.2.0"
val fs2V = "1.0.4"
val http4sV = "0.20.0"
val reactiveStreamsV = "1.0.2"

val specs2V = "4.5.1"

val kindProjectorV = "0.10.0"
val betterMonadicForV = "0.3.0"

// General Settings
lazy val commonSettings = Seq(
  organization := "org.http4s",

  scalaVersion := "2.12.8",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.12"),
  scalacOptions += "-Yrangepos",

  scalacOptions in (Compile, doc) ++= Seq(
      "-groups",
      "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
      "-doc-source-url", "https://github.com/http4s/http4s-jdk-http-client/blob/v" + version.value + "€{FILE_PATH}.scala"
  ),

  addCompilerPlugin("org.typelevel" % "kind-projector" % kindProjectorV cross CrossVersion.binary),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForV),
  libraryDependencies ++= Seq(
    "org.typelevel"               %% "cats-core"                      % catsV,
    "org.typelevel"               %% "cats-effect"                    % catsEffectV,
    "co.fs2"                      %% "fs2-core"                       % fs2V,
    "co.fs2"                      %% "fs2-io"                         % fs2V,
    "co.fs2"                      %% "fs2-reactive-streams"           % fs2V,
    "org.http4s"                  %% "http4s-client"                  % http4sV,
    "org.reactivestreams"         %  "reactive-streams-flow-adapters" % reactiveStreamsV,
    
    "org.http4s"                  %% "http4s-testing"                 % http4sV       % Test,
    "org.specs2"                  %% "specs2-core"                    % specs2V       % Test,
    "org.specs2"                  %% "specs2-scalacheck"              % specs2V       % Test
  )
)

lazy val releaseSettings = {
  import ReleaseTransformations._
  Seq(
    releaseCrossBuild := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      // For non cross-build projects, use releaseStepCommand("publishSigned")
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges
    ),
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    credentials ++= (
      for {
        username <- Option(System.getenv().get("SONATYPE_USERNAME"))
        password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
      } yield
        Credentials(
          "Sonatype Nexus Repository Manager",
          "oss.sonatype.org",
          username,
          password
        )
    ).toSeq,
    publishArtifact in Test := false,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/http4s/http4s-jdk-http-client"),
        "git@github.com:http4s/http4s-jdk-http-client.git"
      )
    ),
    homepage := Some(url("https://github.com/http4s/http4s-jdk-http-client")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    publishMavenStyle := true,
    pomIncludeRepository := { _ =>
      false
    },
    pomExtra := {
      <developers>
        {for ((username, name) <- contributors) yield
        <developer>
          <id>{username}</id>
          <name>{name}</name>
          <url>http://github.com/{username}</url>
        </developer>
        }
      </developers>
    }
  )
}

lazy val mimaSettings = {
  import sbtrelease.Version

  def semverBinCompatVersions(major: Int, minor: Int, patch: Int): Set[(Int, Int, Int)] = {
    val majorVersions: List[Int] =
      if (major == 0 && minor == 0) List.empty[Int] // If 0.0.x do not check MiMa
      else List(major)
    val minorVersions : List[Int] =
      if (major >= 1) Range(0, minor).inclusive.toList
      else List(minor)
    def patchVersions(currentMinVersion: Int): List[Int] = 
      if (minor == 0 && patch == 0) List.empty[Int]
      else if (currentMinVersion != minor) List(0)
      else Range(0, patch - 1).inclusive.toList

    val versions = for {
      maj <- majorVersions
      min <- minorVersions
      pat <- patchVersions(min)
    } yield (maj, min, pat)
    versions.toSet
  }

  def mimaVersions(version: String): Set[String] = {
    Version(version) match {
      case Some(Version(major, Seq(minor, patch), _)) =>
        semverBinCompatVersions(major.toInt, minor.toInt, patch.toInt)
          .map{case (maj, min, pat) => maj.toString + "." + min.toString + "." + pat.toString}
      case _ =>
        Set.empty[String]
    }
  }
  // Safety Net For Exclusions
  lazy val excludedVersions: Set[String] = Set()

  // Safety Net for Inclusions
  lazy val extraVersions: Set[String] = Set()

  Seq(
    mimaFailOnProblem := mimaVersions(version.value).toList.headOption.isDefined,
    mimaPreviousArtifacts := (mimaVersions(version.value) ++ extraVersions)
      .filterNot(excludedVersions.contains(_))
      .map{v => 
        val moduleN = moduleName.value + "_" + scalaBinaryVersion.value.toString
        organization.value % moduleN % v
      },
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
      import com.typesafe.tools.mima.core.ProblemFilters._
      Seq()
    }
  )
}

lazy val micrositeSettings = {
  import microsites._
  Seq(
    micrositeName := "http4s-jdk-http-client",
    micrositeDescription := "JDK 11+ http client implementation for http4s clients",
    micrositeAuthor := "http4s",
    micrositeGithubOwner := "http4s",
    micrositeGithubRepo := "http4s-jdk-http-client",
    micrositeBaseUrl := "/http4s-jdk-http-client",
    micrositeDocumentationUrl := "https://www.javadoc.io/doc/org.http4s/http4s-jdk-http-client_2.12",
    micrositeFooterText := None,
    micrositeHighlightTheme := "atom-one-light",
    micrositePalette := Map(
      "brand-primary" -> "#3e5b95",
      "brand-secondary" -> "#294066",
      "brand-tertiary" -> "#2d5799",
      "gray-dark" -> "#49494B",
      "gray" -> "#7B7B7E",
      "gray-light" -> "#E5E5E6",
      "gray-lighter" -> "#F4F3F4",
      "white-color" -> "#FFFFFF"
    ),
    fork in tut := true,
    scalacOptions in Tut --= Seq(
      "-Xfatal-warnings",
      "-Ywarn-unused-import",
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Ywarn-unused:imports",
      "-Xlint:-missing-interpolator,_"
    ),
    libraryDependencies += "com.47deg" %% "github4s" % "0.20.1",
    micrositePushSiteWith := GitHub4s,
    micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
    micrositeExtraMdFiles := Map(
        file("CHANGELOG.md")        -> ExtraMdFileConfig("changelog.md", "page", Map("title" -> "changelog", "section" -> "changelog", "position" -> "100")),
        file("CODE_OF_CONDUCT.md")  -> ExtraMdFileConfig("code-of-conduct.md",   "page", Map("title" -> "code of conduct",   "section" -> "code of conduct",   "position" -> "101")),
        file("LICENSE")             -> ExtraMdFileConfig("license.md",   "page", Map("title" -> "license",   "section" -> "license",   "position" -> "102"))
    )
  )
}

lazy val skipOnPublishSettings = Seq(
  skip in publish := true,
  publish := (()),
  publishLocal := (()),
  publishArtifact := false,
  publishTo := None
)