import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := """uk\.gov\.hmrc\.BuildInfo;.*\.Routes;.*\.RoutesPrefix;.*Filters?;MicroserviceAuditConnector;Module;GraphiteStartUp;.*\.Reverse[^.]*""",
    ScoverageKeys.coverageMinimumStmtTotal := 80.00,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}

lazy val wartRemoverSettings = {
  val wartRemoverWarning = {
    val warningWarts = Seq(
      Wart.JavaSerializable,
      Wart.StringPlusAny,
      Wart.AsInstanceOf,
      Wart.IsInstanceOf,
      Wart.Any
    )
    Compile / compile / wartremoverWarnings ++= warningWarts
  }

  val wartRemoverError = {
    // Error
    val errorWarts = Seq(
      Wart.ArrayEquals,
      Wart.AnyVal,
      Wart.EitherProjectionPartial,
      Wart.Enumeration,
      Wart.ExplicitImplicitTypes,
      Wart.FinalVal,
      Wart.JavaConversions,
      Wart.JavaSerializable,
      Wart.LeakingSealed,
      Wart.MutableDataStructures,
      Wart.Null,
      Wart.OptionPartial,
      Wart.Recursion,
      Wart.Return,
      Wart.TraversableOps,
      Wart.TryPartial,
      Wart.Var,
      Wart.While)

    Compile / compile / wartremoverErrors ++= errorWarts
  }

  Seq(
    wartRemoverError,
    wartRemoverWarning,
    Test / compile / wartremoverErrors --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference),
    wartremoverExcluded ++=
    (Compile / routes).value ++
    (baseDirectory.value / "it").get ++
    (baseDirectory.value / "test").get ++
    Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala")
  )
}


lazy val compileDeps = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-backend-play-28" % "7.7.0",
  "uk.gov.hmrc" %% "agent-mtd-identifiers" % "0.47.0-play-28",
  "uk.gov.hmrc" %% "domain" % "8.1.0-play-28",
  "com.github.blemale" %% "scaffeine" % "4.0.1",
  "uk.gov.hmrc" %% "agent-kenshoo-monitoring" % "4.8.0-play-28",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "8.1.0-play-28"
)

def testDeps(scope: String) = Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % scope,
  "org.scalatestplus" %% "mockito-3-12" % "3.2.10.0" % scope,
  "com.github.tomakehurst" % "wiremock-jre8" % "2.26.1" % scope,
  "uk.gov.hmrc" %% "reactivemongo-test" % "5.1.0-play-28" % scope,
  "com.vladsch.flexmark" % "flexmark-all" % "0.35.10" % scope
)

def tmpMacWorkaround(): Seq[ModuleID] =
  if (sys.props.get("os.name").fold(false)(_.toLowerCase.contains("mac")))
    Seq("org.reactivemongo" % "reactivemongo-shaded-native" % "0.18.6-osx-x86-64" % "runtime,test,it")
  else Seq()

lazy val root = Project("agent-subscription", file("."))
  .settings(
    name := "agent-subscription",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.12.15",
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Xlint:-missing-interpolator,_",
      "-Yno-adapted-args",
      "-Ywarn-value-discard",
      "-Ywarn-dead-code",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
      "-P:silencer:pathFilters=views;routes;TestStorage"),
    PlayKeys.playDefaultPort := 9436,
    resolvers ++= Seq(
      Resolver.typesafeRepo("releases"),
    ),
    resolvers += "HMRC-local-artefacts-maven" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases-local",
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    libraryDependencies ++= tmpMacWorkaround ++ compileDeps ++ testDeps("test") ++ testDeps("it"),
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.8" cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % "1.7.8" % Provided cross CrossVersion.full
    ),
    publishingSettings,
    scoverageSettings,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    routesImport ++= Seq(
      "uk.gov.hmrc.agentsubscription.model.AuthProviderId",
      "java.util.UUID",
      "uk.gov.hmrc.agentmtdidentifiers.model.Utr",
      "uk.gov.hmrc.agentsubscription.binders.UrlBinders._"
    )
  )
  .configs(IntegrationTest)
  .settings(
    majorVersion := 0,
    IntegrationTest / Keys.fork := false,
    Defaults.itSettings,
    IntegrationTest / unmanagedSourceDirectories += baseDirectory(_ / "it").value,
    IntegrationTest / parallelExecution := false
  )
  .settings(wartRemoverSettings: _*)
  .enablePlugins(PlayScala, SbtDistributablesPlugin)