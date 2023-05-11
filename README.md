# secure-message

Micro-service responsible for providing a secure communication channel between HMRC and it's customers.

## API

An OpenAPI 3.0 schema is available for the endpoints on this service which can be explored via a Swagger UI. The schema
and UI URLs are as follows:

**Development**

- Schema: https://www.development.tax.service.gov.uk/secure-messaging/api/schema.json
- Swagger
  UI: https://www.development.tax.service.gov.uk/secure-messaging/docs/swagger-ui/index.html?url=/secure-messaging/api/schema.json

**QA**

- Schema: https://www.qa.tax.service.gov.uk/secure-messaging/api/schema.json
- Swagger
  UI: https://www.qa.tax.service.gov.uk/secure-messaging/docs/swagger-ui/index.html?url=/secure-messaging/api/schema.json

**Staging**

- Schema: https://www.staging.tax.service.gov.uk/secure-messaging/api/schema.json
- Swagger
  UI: https://www.staging.tax.service.gov.uk/secure-messaging/docs/swagger-ui/index.html?url=/secure-messaging/api/schema.json

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

If your build fails due to poor test coverage, *DO NOT* lower the test coverage threshold, instead inspect the generated
report located here on your local repo: `/target/scala-2.12/scoverage-report/index.html`

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
    "client" : "CDCM",
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

## API

| Path                                    | Supported Methods | Description                                                                                                        |
|-----------------------------------------|-------------------|--------------------------------------------------------------------------------------------------------------------|
| ```/v4/message```                       | POST              | Add a new v4 message [More...](#post-messages)                                                                        |
| ```/messages```                         | GET               | List of messages for user [More...](#get-messages)                                                                 |
| ```/messages/:encodedId```              | GET               | Get message by id [More...](#get-messagesid)                                                                       |
| ```/messages/:id/content```             | GET               | **** Get message content by id [More...](#get-messagesid-content)                                                  |
| ```/messages/:id/read-time```           | POST              | Sets the time a given message was read [More...](#post-messagesidread-time)                                        |
| ```/message/system/:id/send-alert```    | GET               | Returns true if the message identified in the request has not been read, [More...](#get-messagesystemidsend-alert) |

## Admin API

| Path                                   | Supported Methods | Description                                                                                                                  |
|----------------------------------------|-------------------|------------------------------------------------------------------------------------------------------------------------------|
| ```/admin/message/brake/gmc/batches``` | GET               | Add rescindments for the given message search criteria [More...](#post-adminmessageadd-rescindments)                         |
| ```/admin/message/brake/accept```      | POST              | Trigger the send rescindments alert scheduled job to execute now [More...](#post-adminsend-rescindments-alerts)              |
| ```/admin/message/brake/reject```      | POST              | Add an extra alert to be sent to the user associated with the given message ids [More...](#post-adminmessageadd-extra-alert) |
| ```/admin/message/brake/random```      | POST              | Trigger the send extra alerts scheduled job to execute now [More...](#post-adminsend-extra-alerts)                           |

## Endpoints

### POST /v4/messages

Create a new v4 message. This is for messages that contain html content in the body.

An example message creation request:

```json
{
  "externalRef": {
    "id": "abcd1234",
    "source": "mdtp"
  },
  "recipient": {
    "regime": "sdil",
    "taxIdentifier": {
      "name": "HMRC-OBTDS-ORG",
      "value": "XZSD00000100024"
    },
    "name": {
      "line1": "Line1",
      "line2": "Line2",
      "line3": "Line3"
    },
    "email": "test@test.com"
  },
  "tags": {
    "notificationType": "Direct Debit"
  },
  "alertDetails": {
    "data": {
      "key1": "value 1",
      "key2": "value2"
    }
  },
  "details": {
    "formId": "SA300",
    "issueDate": "2017-02-13",
    "batchId": "1234567",
    "sourceData": "<base64 encoded source data>",
    "properties": [
      {
        "property": {
          "name": "printedVariant",
          "value": "false"
        }
      }
    ]
  },
  "content": [
    {
      "lang": "en",
      "subject": "Reminder to file a Self Assessment return",
      "body": "Message content - 4254101384174917141"
    },
    {
      "lang": "cy",
      "subject": "Nodyn atgoffa i ffeilio ffurflen Hunanasesiad",
      "body": "Cynnwys - 4254101384174917141"
    }
  ],
  "messageType": "sdAlertMessage",
  "validFrom": "2020-05-04",
  "alertQueue": "DEFAULT"
}
```

The full schema for this can be found [SchemaV4](./conf/message.schema.v4.json)

Responds with status code:

- 201 if the message is created successfully
- 400 (Bad Request) if the body is not as per the above definition
- 400 (Bad Request) if the tax identifier is not supported
- 409 (Conflict) if the message hash is a duplicate of an existing message

### GET /messages

List of messages metadata for the user. Accepts optional parameters:

| Name         | Description                                   |
|--------------|-----------------------------------------------|
| enrolmentKey | User's enrolment key                          |
| enrolment    | Customer enrolment                            |
| messageFilter| Filter with taxIdentifiers & regimes          |
| language     | For English or Welsh messages                 |

Example response:

```json
[
  {
    "messageType": "letter",
    "id": "bGV0dGVyLzYwOWE1YmQ1MDEwMDAwNmMxODAwMjcyZA==",
    "subject": "Test have subjects11",
    "issueDate": "2021-04-26T00:00:00.000+0000",
    "senderName": "HMRC",
    "unreadMessages": false,
    "count": 1
  }
]
```

### GET /messages/:encodedId

Returns message by id.

Example response for message that has been read:

```json
{
  "id": "57bac7e90b0000490000b7cf",
  "subject": "Test subject",
  "body": {
    "type": "print-suppression-notification",
    "form": "SA251",
    "suppressedAt": "2016-08-22",
    "detailsId": "C0123456781234568"
  },
  "contentParameters": {
    "data": {
      "templateData1": "data1",
      "templateData2": "data2"
    },
    "templateId": "testAlertTemplate"
  },
  "validFrom": "2016-08-22",
  "readTime": "2014-05-02T17:17:45.618Z",
  "sentInError": false,
  "renderUrl": {
    "service": "sa-message-renderer",
    "url": "/message/url"
  }
}
```

Example response for message that is unread:

```json
{
  "id": "57bac7e90b0000490000b7cf",
  "subject": "Test subject",
  "body": {
    "type": "print-suppression-notification",
    "form": "SA251",
    "suppressedAt": "2016-08-22",
    "detailsId": "C0123456781234568"
  },
  "contentParameters": {
    "data": {
      "templateData1": "data1",
      "templateData2": "data2"
    },
    "templateId": "testAlertTemplate"
  },
  "validFrom": "2016-08-22",
  "markAsReadUrl": {
    "service": "message",
    "url": "/messages/57bac7e90b0000490000b7cf/read-time"
  },
  "renderUrl": {
    "service": "sa-message-renderer",
    "url": "/message/url"
  },
  "sentInError": false
}
```

### POST /messages/:id/read-time

Sets the time a given message was read. This operation is idempotent.

Responds with status code:

- 200 if the message read time is updated with the current request or has already been set.
- 404 if a message with the provided id does not exist for the tax ids inferred from the request.

### GET /message/system/:id/send-alert

Returns true if the message identified in the request has not been read, false otherwise.

Example response:

```json
{
  "sendAlert": true
}
```

Responds with status code:

- 200 if a valid message id is provided
- 400 if a malformed message id is specified by the URI
- 404 if a message with the specified message id does not exist

## Admin Endpoints

### GET /admin/message/brake/gmc/batches

Pulls the message batches which are in deferred status

Example response:

```json
{
  "batchId": "1234567",
  "formId": "SA316",
  "issueDate": "2017-03-16",
  "templateId": "testAlert",
  "count": 1
}
```

### POST /admin/message/brake/accept

Accepts the message in message brake to be delivered

Example request:

```json
{
  "batchId": "1234567",
  "formId": "SA316",
  "issueDate": "2017-03-16",
  "templateId": "testAlert",
  "reasonText": "reason to accept"
}
```

Responds with status code:

- 200 if a valid message is accepted
- 404 if a message with the specified details does not exist

### POST /admin/message/brake/reject

Rejects the message in message brake waiting to be delivered

Example request:

```json
{
  "batchId": "1234567",
  "formId": "SA316",
  "issueDate": "2017-03-16",
  "templateId": "testAlert",
  "reasonText": "reason to reject"
}
```

Responds with status code:

- 200 if a valid message is rejected
- 404 if a message with the specified details does not exist

### POST /admin/message/brake/random

Pulls the message with deferred status waiting to be delivered

Example request:

```json
{
  "batchId": "1234567",
  "formId": "SA316",
  "issueDate": "2017-03-16",
  "templateId": "testAlert"
}
```

Example response:

```json
{
  "subject": "Reminder to file a Self Assessment return",
  "welshSubject": "Nodyn atgoffa i ffeilio ffurflen Hunanasesiad",
  "content": "TWVzc2FnZSBjb250ZW50IC0gNDI1NDEwMTM4NDE3NDkxNzE0MQ==",
  "welshContent": "Q3lubnd5cyAtIDQyNTQxMDEzODQxNzQ5MTcxNDE=",
  "externalRefId": "abcd1234",
  "messageType": "sdAlertMessage",
  "issueDate": "2020-05-04",
  "taxIdentifierName": "HMRC-OBTDS-ORG"
}
```

## License

This code is open source software licensed under
the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").


