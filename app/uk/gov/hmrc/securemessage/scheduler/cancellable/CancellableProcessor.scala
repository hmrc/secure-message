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

package uk.gov.hmrc.securemessage.scheduler.cancellable

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.{ ExecutionContext, Future }

trait CancellableProcessor[S, I] extends CancellableOperation {

  def unprocessedState: S

  def pullItem(implicit ec: ExecutionContext): Future[Option[I]]

  def processItem(state: S, item: I)(implicit ec: ExecutionContext): Future[S]

  def processItems(implicit ec: ExecutionContext, mat: Materializer): Future[S] = {
    def pullItemUnlessCancelled(pull: => Future[Option[I]]): Future[Option[I]] =
      if (isCancelled) Future.successful(None) else pull

    Source
      .unfoldAsync[NotUsed, Option[I]](NotUsed) { _ =>
        pullItemUnlessCancelled(pullItem).map(x => x.map(y => (NotUsed, Some(y))))
      }
      .runFoldAsync(unprocessedState) { (st: S, item: Option[I]) =>
        item match {
          case Some(i) =>
            processItem(st, i)
          case _ =>
            Future.successful(st)
        }
      }
  }
}
