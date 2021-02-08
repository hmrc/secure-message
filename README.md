# secure-message
Micro-service responsible for providing a secure communication channel between HMRC and it's customers.

## API

An OpenAPI 3.0 schema is available for the endpoints on this service which can be explored via a Swagger UI. The schema and UI URLs are as follows:

**Development**
- Schema: https://www.development.tax.service.gov.uk/secure-messaging/api/schema.json
- Swagger UI: https://www.development.tax.service.gov.uk/secure-messaging/docs/swagger-ui/index.html?url=/secure-messaging/api/schema.json

**QA**
- Schema: https://www.qa.tax.service.gov.uk/secure-messaging/api/schema.json
- Swagger UI: https://www.qa.tax.service.gov.uk/secure-messaging/docs/swagger-ui/index.html?url=/secure-messaging/api/schema.json

**Staging**
- Schema: https://www.staging.tax.service.gov.uk/secure-messaging/api/schema.json
- Swagger UI: https://www.staging.tax.service.gov.uk/secure-messaging/docs/swagger-ui/index.html?url=/secure-messaging/api/schema.json

## Run the project locally 

`sbt run "9051 -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes"`

## Run the tests and sbt fmt before raising a PR

Ensure you have service-manager python environment setup:

`source ../servicemanager/bin/activate`

Format:

`sbt fmt`

Then run the tests and coverage report:

`sbt clean coverage test coverageReport`

If your build fails due to poor test coverage, *DO NOT* lower the test coverage threshold, instead inspect the generated report located here on your local repo: `/target/scala-2.12/scoverage-report/index.html`

Then run the integration tests:

`sbt it:test`

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").




