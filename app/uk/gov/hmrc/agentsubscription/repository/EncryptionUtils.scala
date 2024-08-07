package uk.gov.hmrc.agentsubscription.repository

import play.api.libs.json.{JsSuccess, JsValue}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter}

object EncryptionUtils {
  def decrypt(fieldName: String, json: JsValue)(implicit crypto: Encrypter with Decrypter): String =
    (json \ fieldName).validate[String] match {
      case JsSuccess(value, _) => crypto.decrypt(Crypted(value)).value
      case _                   => throw new RuntimeException(s"Failed to decrypt $fieldName")
    }

  def decryptOpt(fieldName: String, json: JsValue)(implicit crypto: Encrypter with Decrypter): Option[String] =
    (json \ fieldName).validateOpt[String] match {
      case JsSuccess(value, _) => value.map { f: String => crypto.decrypt(Crypted(f)).value }
      case _                   => throw new RuntimeException(s"Failed to decrypt $fieldName")
    }

  def maybeDecrypt(fieldName: String, isEncrypted: Option[Boolean], json: JsValue)(implicit crypto: Encrypter with Decrypter): String = {
    isEncrypted match {
      case Some(true) => decrypt(fieldName, json)
      case _          => (json \ fieldName).as[String]
    }
  }

  def maybeDecryptOpt(fieldName: String, isEncrypted: Option[Boolean], json: JsValue)(implicit crypto: Encrypter with Decrypter): Option[String] = {
    isEncrypted match {
      case Some(true) => decryptOpt(fieldName, json)
      case _          => (json \ fieldName).asOpt[String]
    }
  }
}