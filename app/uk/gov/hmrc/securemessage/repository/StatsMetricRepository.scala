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

package uk.gov.hmrc.securemessage.repository

import org.mongodb.scala.model.Sorts.ascending
import org.mongodb.scala.model._
import org.mongodb.scala.result.UpdateResult
import play.api.libs.json._
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.metrix.MetricSource
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

final case class StatsCount(name: String, total: Int, count: Int)

object StatsCount {
  val format = Json.format[StatsCount]
}

@Singleton
class StatsMetricRepository @Inject() (
  mongo: MongoComponent
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[StatsCount](
      mongo,
      "stats_metric",
      StatsCount.format,
      Seq(
        IndexModel(ascending("name"), IndexOptions().name("stats_metric_key_idx").unique(true).background(true))
      )
    ) with MetricSource {

  override def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]] =
    collection
      .find(
        Filters.empty()
      )
      .toFuture()
      .map(
        _.flatMap(metric => Map(s"${metric.name}.total" -> metric.total, s"${metric.name}.count" -> metric.count)).toMap
      )

  def incrementReads(taxIdName: String, form: String)(implicit ec: ExecutionContext): Future[Unit] =
    increment(s"stats.$taxIdName.$form.read", 1)

  def incrementUpdate(taxIdName: String, form: String, metricNameSuffix: String)(implicit
    ec: ExecutionContext
  ): Future[Unit] =
    increment(s"stats.$taxIdName.$form.$metricNameSuffix", 1)

  def incrementCreated(taxIdName: String, form: String)(implicit ec: ExecutionContext): Future[Unit] =
    increment(s"stats.$taxIdName.$form.created", 1)

  def incrementDuplicate(refSource: String)(implicit ec: ExecutionContext): Future[Unit] =
    increment(s"stats.$refSource.duplicates", 1)

  def incrementSourceDataDeletions(deleted: Int)(implicit ec: ExecutionContext): Future[Unit] =
    increment(s"stats.source.data.deletions", deleted)

  private def increment(metricName: String, count: Int)(implicit ec: ExecutionContext): Future[Unit] =
    collection
      .updateMany(
        Filters.equal("name", metricName),
        Updates.combine(Updates.inc("count", count), Updates.inc("total", count)),
        UpdateOptions().upsert(true)
      )
      .toFuture()
      .map(_ => ())

  def reset: Future[Option[UpdateResult]] =
    collection
      .updateMany(
        Filters.empty(),
        Updates.set("count", 0)
      )
      .headOption()
}
