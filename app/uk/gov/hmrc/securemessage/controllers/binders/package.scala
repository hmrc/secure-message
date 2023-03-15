/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.securemessage.controllers

import org.bson.types.ObjectId
import play.api.libs.json.JsString
import play.api.mvc.{ PathBindable, QueryStringBindable }
import uk.gov.hmrc.common.message.model.Regime
import uk.gov.hmrc.securemessage.models.core.Language.English
import uk.gov.hmrc.securemessage.models.core.{ CustomerEnrolment, FilterTag, Language, MessageFilter }

package object binders {

  implicit def queryStringBindableCustomerEnrolment(
    implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[CustomerEnrolment] =
    new QueryStringBindable[CustomerEnrolment] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, CustomerEnrolment]] =
        stringBinder.bind("enrolment", params) map {
          case Right(customerEnrolment) => CustomerEnrolment.parse(customerEnrolment)
          case _                        => Left("Unable to bind a CustomerEnrolment")
        }

      override def unbind(key: String, customerEnrolment: CustomerEnrolment): String =
        s"${customerEnrolment.key}~${customerEnrolment.name}~${customerEnrolment.value}"
    }

  implicit def queryStringBindableLanguage(
    implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Language] =
    new QueryStringBindable[Language] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Language]] =
        stringBinder.bind("lang", params) map {
          case Right(language) => Right(Language.namesToValuesMap.getOrElse(language, English))
          case _               => Right(English)
        }

      override def unbind(key: String, language: Language): String = language.entryName
    }

  implicit def queryStringBindableTag(
    implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[FilterTag] =
    new QueryStringBindable[FilterTag] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, FilterTag]] =
        stringBinder.bind("tag", params) map {
          case Right(tag) => FilterTag.parse(tag)
          case _          => Left("Unable to bind a Tag")
        }

      override def unbind(key: String, tag: FilterTag): String =
        tag.key + "~" + tag.value
    }

  implicit def messageFilterBinder(
    implicit seqBinder: QueryStringBindable[Seq[String]]): QueryStringBindable[MessageFilter] =
    new QueryStringBindable[MessageFilter] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, MessageFilter]] =
        for {
          taxIdentifiers <- seqBinder.bind("taxIdentifiers", params).orElse(Some(Right(List[String]())))
          regimes        <- seqBinder.bind("regimes", params).orElse(Some(Right(List[String]())))

        } yield {
          (taxIdentifiers, regimes) match {
            case (Right(taxIdentifiers), Right(regimes)) =>
              Right(MessageFilter(taxIdentifiers, regimes.map(JsString(_).as[Regime.Value])))
            case _ => Left("Unable to bind an MessageFilter")
          }
        }

      override def unbind(key: String, messageFilter: MessageFilter): String =
        seqBinder.unbind("taxIdentifiers", messageFilter.taxIdentifiers) + "&" + seqBinder
          .unbind("regime", messageFilter.regimes.map(_.toString))
    }

  implicit def objectIdBinder(implicit stringBinder: PathBindable[String]): PathBindable[ObjectId] =
    new PathBindable[ObjectId] {
      def bind(key: String, value: String): Either[String, ObjectId] = stringBinder.bind(key, value) match {
        case Left(msg) => Left(msg)
        case Right(id) =>
          if (ObjectId.isValid(id)) {
            Right(new ObjectId(id))
          } else Left(s"ID $id was invalid")
      }

      def unbind(key: String, value: ObjectId): String = value.toString
    }
}
