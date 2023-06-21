import play.sbt.PlayImport.ws
import sbt._

object AppDependencies {

  private val bootstrapVer: String = "7.19.0"
  private val mongoVer: String = "0.74.0"

  lazy val compile = Seq(
    ws,
    "uk.gov.hmrc"        %% "bootstrap-backend-play-28" % bootstrapVer,
    "uk.gov.hmrc"        %% "agent-mtd-identifiers"     % "1.10.0",
    "com.github.blemale" %% "scaffeine"                 % "4.0.1",
    "uk.gov.hmrc"        %% "agent-kenshoo-monitoring"  % "5.3.0",
    "uk.gov.hmrc.mongo"  %% "hmrc-mongo-play-28"        % mongoVer
  )
  
  lazy val test = Seq(
    "org.scalatestplus.play" %% "scalatestplus-play"      % "5.1.0"    % "test, it",
    "org.scalatestplus"      %% "mockito-3-12"            % "3.2.10.0" % "test, it",
    "com.github.tomakehurst" %  "wiremock-jre8"           % "2.26.1"   % "test, it",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28" % mongoVer   % "test, it",
    "com.vladsch.flexmark"   %  "flexmark-all"            % "0.35.10"  % "test, it"
  )

}
