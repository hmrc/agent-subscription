/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentsubscription.repository

import com.google.inject.ImplementedBy
import com.mongodb.client.model.ReplaceOptions
import org.mongodb.scala.model.Filters.{equal, or}
import org.mongodb.scala.model.Indexes._
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import uk.gov.hmrc.agentsubscription.config.AppConfig
import uk.gov.hmrc.agentsubscription.connectors.BusinessAddress
import uk.gov.hmrc.agentsubscription.model.{AuthProviderId, ContactEmailData, ContactTradingAddressData, ContactTradingNameData}
import uk.gov.hmrc.agentsubscription.model.subscriptionJourney.{BusinessDetails, ContactTelephoneData, SubscriptionJourneyRecord}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Named, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubscriptionJourneyRepositoryImpl])
trait SubscriptionJourneyRepository {

  def upsert(authProviderId: AuthProviderId, record: SubscriptionJourneyRecord): Future[Option[UpsertType]]
  def updateOnUtr(utr: String, record: SubscriptionJourneyRecord): Future[Option[Long]]
  def findByAuthId(authProviderId: AuthProviderId): Future[Option[SubscriptionJourneyRecord]]
  def findByContinueId(continueId: String): Future[Option[SubscriptionJourneyRecord]]
  def findByUtr(utr: String): Future[Option[SubscriptionJourneyRecord]]
  def delete(utr: String): Future[Option[Long]]

  def encryptBusinessDetails(businessDetails: BusinessDetails): BusinessDetails
  def encryptContactEmailData(contactEmailData: ContactEmailData): ContactEmailData
  def encryptContactTradingNameData(contactTradingNameData: ContactTradingNameData): ContactTradingNameData
  def encryptContactTelephoneData(contactTelephoneData: ContactTelephoneData): ContactTelephoneData
  def encryptContactTradingAddressData(contactTradingAddressData: ContactTradingAddressData): ContactTradingAddressData
  def encryptBusinessAddress(businessAddress: BusinessAddress): BusinessAddress

  def encryptRecord(record: SubscriptionJourneyRecord): SubscriptionJourneyRecord
}

@Singleton
class SubscriptionJourneyRepositoryImpl @Inject() (
  mongo: MongoComponent,
  @Named("aes") crypto: Encrypter with Decrypter
)(implicit
  ec: ExecutionContext,
  appConfig: AppConfig
) extends PlayMongoRepository[SubscriptionJourneyRecord](
      mongoComponent = mongo,
      collectionName = "subscription-journey",
      domainFormat = SubscriptionJourneyRecord.subscriptionJourneyFormat(crypto),
      indexes = Seq(
        IndexModel(
          ascending("authProviderId"),
          IndexOptions().unique(true).name("primaryAuthId")
        ),
        IndexModel(
          ascending("userMappings.authProviderId"),
          IndexOptions().unique(true).sparse(true).name("mappedAuthId")
        ),
        IndexModel(
          ascending("cleanCredsAuthProviderId"),
          IndexOptions().unique(true).sparse(true).name("cleanCredsAuthProviderId")
        ),
        IndexModel(
          ascending("businessDetails.utr"),
          IndexOptions().unique(true).name("utr")
        ),
        IndexModel(
          ascending("continueId"),
          IndexOptions().unique(true).sparse(true).name("continueId")
        ),
        IndexModel(
          ascending("lastModifiedDate"),
          IndexOptions()
            .unique(false)
            .name("lastModifiedDateTtl")
            .expireAfter(appConfig.mongodbSubscriptionJourneyTTL, TimeUnit.SECONDS)
        )
      ),
      replaceIndexes = true,
      extraCodecs = Seq(
        Codecs.playFormatCodec(AuthProviderId.format)
      )
    ) with SubscriptionJourneyRepository {

  private def replaceOptions(upsert: Boolean) = new ReplaceOptions().upsert(upsert)

  def encryptBusinessDetails(businessDetails: BusinessDetails): BusinessDetails =
    businessDetails.copy(
      nino = businessDetails.nino.map { n: String =>
        crypto.encrypt(PlainText(n)).value
      },
      utr = crypto.encrypt(PlainText(businessDetails.utr)).value,
      postcode = crypto.encrypt(PlainText(businessDetails.postcode)).value,
      registration = businessDetails.registration.map { f =>
        f.copy(
          taxpayerName = f.taxpayerName.map { f: String => crypto.encrypt(PlainText(f)).value },
          emailAddress = f.emailAddress.map { f: String => crypto.encrypt(PlainText(f)).value },
          address = encryptBusinessAddress(f.address),
          primaryPhoneNumber = f.primaryPhoneNumber.map { f: String => crypto.encrypt(PlainText(f)).value }
        )
      }
    )

  private def decryptBusinessDetails(businessDetails: BusinessDetails): BusinessDetails =
    businessDetails.copy(
      nino = businessDetails.nino.map { n: String =>
        crypto.decrypt(Crypted(n)).value
      },
      utr = crypto.decrypt(Crypted(businessDetails.utr)).value,
      postcode = crypto.decrypt(Crypted(businessDetails.postcode)).value,
      registration = businessDetails.registration.map { f =>
        f.copy(
          taxpayerName = f.taxpayerName.map { f: String => crypto.decrypt(Crypted(f)).value },
          emailAddress = f.emailAddress.map { f: String => crypto.decrypt(Crypted(f)).value },
          address = decryptBusinessAddress(f.address),
          primaryPhoneNumber = f.primaryPhoneNumber.map { f: String => crypto.decrypt(Crypted(f)).value }
        )
      }
    )

  def encryptContactEmailData(contactEmailData: ContactEmailData): ContactEmailData =
    contactEmailData.copy(
      contactEmail = contactEmailData.contactEmail.map { f: String => crypto.encrypt(PlainText(f)).value }
    )

  private def decryptContactEmailData(contactEmailData: ContactEmailData): ContactEmailData =
    contactEmailData.copy(
      contactEmail = contactEmailData.contactEmail.map { f: String => crypto.decrypt(Crypted(f)).value }
    )

  def encryptContactTradingNameData(contactTradingNameData: ContactTradingNameData): ContactTradingNameData =
    contactTradingNameData.copy(
      contactTradingName = contactTradingNameData.contactTradingName.map { f: String =>
        crypto.encrypt(PlainText(f)).value
      }
    )

  private def decryptContactTradingNameData(contactTradingNameData: ContactTradingNameData): ContactTradingNameData =
    contactTradingNameData.copy(
      contactTradingName = contactTradingNameData.contactTradingName.map { f: String =>
        crypto.decrypt(Crypted(f)).value
      }
    )

  def encryptContactTelephoneData(contactTelephoneData: ContactTelephoneData): ContactTelephoneData =
    contactTelephoneData.copy(
      telephoneNumber = contactTelephoneData.telephoneNumber.map { f: String => crypto.encrypt(PlainText(f)).value }
    )

  private def decryptContactTelephoneData(contactTelephoneData: ContactTelephoneData): ContactTelephoneData =
    contactTelephoneData.copy(
      telephoneNumber = contactTelephoneData.telephoneNumber.map { f: String => crypto.decrypt(Crypted(f)).value }
    )

  def encryptContactTradingAddressData(
    contactTradingAddressData: ContactTradingAddressData
  ): ContactTradingAddressData =
    contactTradingAddressData.copy(
      contactTradingAddress = contactTradingAddressData.contactTradingAddress.map(encryptBusinessAddress)
    )

  private def decryptContactTradingAddressData(
    contactTradingAddressData: ContactTradingAddressData
  ): ContactTradingAddressData =
    contactTradingAddressData.copy(
      contactTradingAddress = contactTradingAddressData.contactTradingAddress.map(decryptBusinessAddress)
    )

  def encryptBusinessAddress(businessAddress: BusinessAddress): BusinessAddress =
    businessAddress.copy(
      addressLine1 = crypto.encrypt(PlainText(businessAddress.addressLine1)).value,
      addressLine2 = businessAddress.addressLine2.map { f: String => crypto.encrypt(PlainText(f)).value },
      addressLine3 = businessAddress.addressLine3.map { f: String => crypto.encrypt(PlainText(f)).value },
      addressLine4 = businessAddress.addressLine4.map { f: String => crypto.encrypt(PlainText(f)).value },
      postalCode = businessAddress.postalCode.map { f: String => crypto.encrypt(PlainText(f)).value },
      countryCode = crypto.encrypt(PlainText(businessAddress.countryCode)).value
    )

  private def decryptBusinessAddress(businessAddress: BusinessAddress): BusinessAddress =
    businessAddress.copy(
      addressLine1 = crypto.decrypt(Crypted(businessAddress.addressLine1)).value,
      addressLine2 = businessAddress.addressLine2.map { f: String => crypto.decrypt(Crypted(f)).value },
      addressLine3 = businessAddress.addressLine3.map { f: String => crypto.decrypt(Crypted(f)).value },
      addressLine4 = businessAddress.addressLine4.map { f: String => crypto.decrypt(Crypted(f)).value },
      postalCode = businessAddress.postalCode.map { f: String => crypto.decrypt(Crypted(f)).value },
      countryCode = crypto.decrypt(Crypted(businessAddress.countryCode)).value
    )

  def encryptRecord(record: SubscriptionJourneyRecord): SubscriptionJourneyRecord =
    record.copy(
      encrypted = Some(true),
      businessDetails = encryptBusinessDetails(record.businessDetails),
      contactEmailData = record.contactEmailData.map(encryptContactEmailData),
      contactTradingNameData = record.contactTradingNameData.map(encryptContactTradingNameData),
      contactTelephoneData = record.contactTelephoneData.map(encryptContactTelephoneData),
      contactTradingAddressData = record.contactTradingAddressData.map(encryptContactTradingAddressData),
      verifiedEmails = record.verifiedEmails.map { f: String => crypto.encrypt(PlainText(f)).value }
    )

  private def decryptRecord(record: SubscriptionJourneyRecord): SubscriptionJourneyRecord =
    if (record.encrypted.contains(true)) {
      record.copy(
        businessDetails = decryptBusinessDetails(record.businessDetails),
        contactEmailData = record.contactEmailData.map(decryptContactEmailData),
        contactTradingNameData = record.contactTradingNameData.map(decryptContactTradingNameData),
        contactTelephoneData = record.contactTelephoneData.map(decryptContactTelephoneData),
        contactTradingAddressData = record.contactTradingAddressData.map(decryptContactTradingAddressData),
        verifiedEmails = record.verifiedEmails.map { f: String => crypto.decrypt(Crypted(f)).value }
      )
    } else record

  def upsert(authProviderId: AuthProviderId, record: SubscriptionJourneyRecord): Future[Option[UpsertType]] =
    collection
      .replaceOne(
        equal("authProviderId", authProviderId.id),
        encryptRecord(record),
        replaceOptions(true)
      )
      .headOption()
      .map(
        _.map(result =>
          result.getModifiedCount match {
            case 0L => RecordInserted(result.getUpsertedId.asObjectId().getValue.toString)
            case 1L => RecordUpdated
            case x  => throw new RuntimeException(s"Update modified count should not have been $x")
          }
        )
      )

  def updateOnUtr(utr: String, record: SubscriptionJourneyRecord): Future[Option[Long]] =
    collection
      .replaceOne(
        equal(
          "businessDetails.utr",
          if (record.encrypted.contains(true)) crypto.encrypt(PlainText(utr)).value else utr
        ),
        encryptRecord(record)
      )
      .toFutureOption()
      .map(_.map(_.getModifiedCount))

  def findByAuthId(
    authProviderId: AuthProviderId
  ): Future[Option[SubscriptionJourneyRecord]] =
    collection
      .find(
        or(
          equal("authProviderId", authProviderId),
          equal("cleanCredsAuthProviderId", authProviderId),
          equal("userMappings.authProviderId", authProviderId)
        )
      )
      .headOption()
      .map(_.map(decryptRecord))

  def findByContinueId(continueId: String): Future[Option[SubscriptionJourneyRecord]] =
    collection
      .find(equal("continueId", continueId))
      .headOption()
      .map(_.map(decryptRecord))

  def findByUtr(utr: String): Future[Option[SubscriptionJourneyRecord]] =
    collection
      .find(
        or(
          equal("businessDetails.utr", crypto.encrypt(PlainText(utr)).value),
          equal("businessDetails.utr", utr)
        )
      )
      .headOption()
      .map(_.map(decryptRecord))

  def delete(utr: String): Future[Option[Long]] =
    collection
      .deleteOne(
        or(
          equal("businessDetails.utr", utr),
          equal("businessDetails.utr", crypto.encrypt(PlainText(utr)).value)
        )
      )
      .toFutureOption()
      .map(_.map(_.getDeletedCount))
}
