package uk.gov.hmrc.agentsubscription.controllers

import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.{JsError, JsSuccess, JsValue}
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.agentsubscription.auth.AuthActions
import uk.gov.hmrc.agentsubscription.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentsubscription.repository.SubscriptionJourneyRepository
import uk.gov.hmrc.play.microservice.controller.BaseController
import play.api.libs.json.Json.toJson
import uk.gov.hmrc.agentsubscription.model.subscriptionJourneyRepositoryModel.SubscriptionJourneyRecord

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


class TaskListController(implicit
                         metrics: Metrics,
                         microserviceAuthConnector: MicroserviceAuthConnector,
                         subscriptionJourneyRepository: SubscriptionJourneyRepository)
  extends AuthActions(metrics, microserviceAuthConnector) with BaseController {

  private def localWithJsonBody(f: SubscriptionJourneyRecord => Future[Result], request: JsValue): Future[Result] =
    Try(request.validate[SubscriptionJourneyRecord]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs))         => Future successful BadRequest(s"Invalid payload: $errs")
      case Failure(e) => Future successful BadRequest(s"could not parse body due to ${e.getMessage}")
    }

  def getSubscriptionJourneyRecord(internalId: String): Action[AnyContent] = authorisedWithAgentAffinity {implicit request =>
    subscriptionJourneyRepository.find(internalId).map {
      case Some(record) => Ok(toJson(record))
      case None => NotFound("record not found for this internal id")
    }
  }

  def createSubscriptionJourneyRecord: Action[JsValue] = authorisedWithAgentAffinity { implicit request =>
    val invitationJson: Option[JsValue] = request.body.asJson
    localWithJsonBody( subscriptionjourneyRecord =>
      subscriptionJourneyRepository.create(subscriptionjourneyRecord).map(_ => Ok()), invitationJson.get)
  }

  def removeSubscriptionJourneyRecord(internalId: String) = authorisedWithAgentAffinity {implicit request =>
    subscriptionJourneyRepository.delete(internalId).map(_ => Ok)
  }

  def updateSubscriptionJourneyRecord(internalId: String) = authorisedWithAgentAffinity {implicit request =>
    val invitationJson: Option[JsValue] = request.body.asJson
    localWithJsonBody( subscriptionJourneyRecord =>
      subscriptionJourneyRepository.update(internalId, subscriptionJourneyRecord).map(_ => Ok()), invitationJson.get)
  }
}
