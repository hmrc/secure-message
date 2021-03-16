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

Ensure you have service-manager python environment setup:

`source ../servicemanager/bin/activate`

`sm --start DC_TWSM_ALL`

`sm --stop SECURE_MESSAGE`

`sbt "run 9051 -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes"`

## Run the tests and sbt fmt before raising a PR

Ensure you have service-manager python environment setup:

`source ../servicemanager/bin/activate`

Format:

`sbt fmt`

Then run the tests and coverage report:

`sbt clean coverage test coverageReport`

If your build fails due to poor test coverage, *DO NOT* lower the test coverage threshold, instead inspect the generated report located here on your local repo: `/target/scala-2.12/scoverage-report/index.html`

Then run the integration tests:
NOTE: for integration tests to work make sure to execute command: `smserver` in a separate terminal window.

`sbt it:test`

## Swagger schema

Available locally here: http://localhost:9051/assets/schema.json

## Swagger-UI

Available locally here: http://localhost:9051/docs/swagger-ui/index.html?url=/assets/schema.json

## Sample data

```json
{
    "_id" : ObjectId("6021481d59f23de1fe8389db"),
    "client" : "cdcm",
    "conversationId" : "D-80542-20201120",
    "status" : "open",
    "subject" : "D-80542-20201120",
    "language" : "en",
    "participants" : [ 
        {
            "id" : 1,
            "participantType" : "system",
            "identifier" : {
                "name" : "CDCM",
                "value" : "D-80542-20201120"
            },
            "name" : "CDS Exports Team"
        }, 
        {
            "id" : 2,
            "participantType" : "customer",
            "identifier" : {
                "name" : "EORINumber",
                "value" : "GB1234567890",
                "enrolment" : "HMRC-CUS-ORG"
            }
        }
    ],
    "messages" : [ 
        {
            "senderId" : 1,
            "created" : "2021-02-08T14:18:05.986+0000",
            "readBy" : [],
            "content" : "QmxhaCBibGFoIGJsYWg="
        }
    ]
}
```

For more examples of resources which can be inserted in your local mongo database `secure-message`, collection `conversation`, can be found [here](test/resources/model/core)

Alternatively you can create messages locally by using this swagger endpoint:

http://localhost:9051/docs/swagger-ui/index.html?url=/assets/schema.json#/app/createConversation

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").



