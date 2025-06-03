import sbt._

object AppDependencies {

  private val bootstrapVer: String = "9.13.0"
  private val mongoVer: String = "2.6.0"

  lazy val compile = Seq(
    "uk.gov.hmrc"        %% "bootstrap-backend-play-30" % bootstrapVer,
    "uk.gov.hmrc"        %% "agent-mtd-identifiers"     % "2.2.0",
    "uk.gov.hmrc.mongo"  %% "hmrc-mongo-play-30"        % mongoVer,
    "uk.gov.hmrc"        %% "crypto-json-play-30"       % "8.2.0"
  )
  
  lazy val test = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play"      % "6.0.1"       % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-30" % mongoVer      % Test,
    "uk.gov.hmrc"            %% "bootstrap-test-play-30"  % bootstrapVer  % Test
  )
}
