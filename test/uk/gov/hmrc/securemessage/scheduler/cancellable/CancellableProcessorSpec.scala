/*
 * Copyright 2025 HM Revenue & Customs
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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer

import scala.concurrent.{ ExecutionContext, Future }

class CancellableProcessorSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit val system: ActorSystem = ActorSystem("test-system")
  implicit val mat: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher

  "processItems" should {

    "process items sequentially until pullItem returns None" in {

      val processor = new TestProcessor()

      val result = processor.processItems.futureValue
      result shouldBe 6
    }

    "stop early when isCancelled becomes true" in {

      val processor = new TestProcessor() {

        private var pulled = false

        override def pullItem(implicit ec: ExecutionContext): Future[Option[Int]] =
          if (!pulled) {
            pulled = true
            Future.successful(Some(5))
          } else {
            Future.successful(Some(999))
          }

        override def isCancelled: Boolean = pulled
      }

      val result = processor.processItems.futureValue
      result shouldBe 5
    }
  }
}

class TestProcessor extends CancellableProcessor[Int, Int] {

  override def unprocessedState: Int = 0

  private val items =
    Iterator(Some(1), Some(2), Some(3), None)

  override def pullItem(implicit ec: ExecutionContext): Future[Option[Int]] =
    Future.successful(items.next())

  override def processItem(state: Int, item: Int)(implicit ec: ExecutionContext): Future[Int] =
    Future.successful(state + item)

  private var cancelled = false

  override def cancel(): Boolean = {
    cancelled = true
    true
  }

  override def isCancelled: Boolean = cancelled
}
