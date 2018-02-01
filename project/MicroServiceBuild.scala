import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "agent-subscription"

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {
  import play.core.PlayVersion
  import play.sbt.PlayImport._

  private val microserviceBootstrapVersion = "6.15.0"
  private val domainVersion = "5.1.0"
  private val hmrcTestVersion = "2.3.0"
  private val scalaTestVersion = "2.2.6"
  private val pegdownVersion = "1.6.0"
  private val wiremockVersion = "2.13.0"
  private val scalaTestPlusVersion = "1.5.1"
  private val mockitoVersion = "2.13.0"
  private val mtdIdentifiersVersion = "0.10.0"
  private val agentKenshooMonitoringVersion = "2.4.0"


  val compile = Seq(

    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "domain" % domainVersion,
    "uk.gov.hmrc" %% "agent-mtd-identifiers" % mtdIdentifiersVersion,
    "uk.gov.hmrc" %% "agent-kenshoo-monitoring" % agentKenshooMonitoringVersion,
    "uk.gov.hmrc" %% "play-reactivemongo" % "5.2.0",
    "uk.gov.hmrc" %% "auth-client" % "2.5.0"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % scalaTestVersion % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "org.mockito" % "mockito-core" % mockitoVersion % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope
      )
    }.test
  }

  object IntegrationTest {
    def apply() = new TestDependencies {

      override lazy val scope: String = "it"

      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % scalaTestVersion % scope,
        "org.scalatestplus.play" %% "scalatestplus-play" % scalaTestPlusVersion % scope,
        "org.pegdown" % "pegdown" % pegdownVersion % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "org.mockito" % "mockito-core" % mockitoVersion % scope,
        "com.github.tomakehurst" % "wiremock" % wiremockVersion % scope,
        "uk.gov.hmrc" %% "reactivemongo-test" % "2.0.0" % scope
      )
    }.test
  }

  def apply() = compile ++ Test() ++ IntegrationTest()
}
