/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import com.google.inject.Inject
import play.api.Configuration
import play.api.libs.json.JsString
import play.api.mvc.{AbstractController, ControllerComponents}
import com.iheart.playSwagger.SwaggerSpecGenerator

class ApiSpecs @Inject()(cc: ControllerComponents, config: Configuration) extends AbstractController(cc) {
  implicit val cl = getClass.getClassLoader

  val domainPackage = "YOUR.DOMAIN.PACKAGE"
  val otherDomainPackage = "YOUR.OtherDOMAIN.PACKAGE"
  lazy val generator = SwaggerSpecGenerator(false, domainPackage, otherDomainPackage)

  // Get's host configuration.
  val host = config.get[String]("swagger.host")

  lazy val swagger = Action { request =>
    generator.generate("app.routes").map(_ + ("host" -> JsString(host))).fold(
      e => InternalServerError("Couldn't generate swagger."),
      s => Ok(s))
  }

  def specs = swagger
}
