# agent-subscription

[![Build Status](https://travis-ci.org/hmrc/agent-subscription.svg)](https://travis-ci.org/hmrc/agent-subscription) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-subscription/images/download.svg) ](https://bintray.com/hmrc/releases/agent-subscription/_latestVersion)

This is a backend microservice for agent-subscription-frontend. It allows for an agent to proceed through the subscription
journey to gain an HMRC-AS-AGENT enrolment, this will allow them to access the functionality of Agent Services and easily 
interact with their clients. The domain is Subscriptions to Agent Services following the ROSM (Register Once Subscribe Many) pattern.

## Running the tests

    sbt test it:test

## Running the app locally

    sm2 --start AGENT_ONBOARDING
    sm2 --stop AGENT_SUBSCRIPTION
    sbt run

## APIs

### Check Agent Services Subscription Status for a Taxpayer

    GET /agent-subscription/registration/:utr/postcode/:postcode

This API checks whether a given taxpayer is subscribed to agent services.
It checks information from the ETMP BPR (Business Partner Record) for the taxpayer with SA or CT UTR `utr` and postcode `postcode`.
It also checks whether any user/group has been allocated the HMRC-AS-AGENT for the Arn present in the BPR.

Possible responses:

#### Not Found
HTTP status 404 with no body will be returned if no business partner found for given known facts (UTR and postcode)

#### Bad Request
Return 400 if the UTR or postcode are invalid

#### OK
If a business partner was found for given known facts then a 200 OK response will be returned with a JSON body structured as follows:

```json
    {
      "isSubscribedToAgentServices": true,
      "isSubscribedToETMP": true,
      "taxpayerName": "AgencyName", //optional
      "address": {
        "addressLine1": "Line1"
        "addressLine2": "Line2" //optional
        "addressLine3": "Line3" //optional
        "addressLine4": "Line4" //optional
        "postcode": "<postcode of the agency's registered taxpayer address>",
        "countryCode": "GB"
      },
      "emailAddress": "agency@example.org" //optional
    }
```

The `isSubscribedToAgentServices` flag will be true if the following holds:
- BPR's postcode matches the `postcode` in the url
- BPR's `isAsAgent` flag is true
- HMRC-AS-AGENT enrolment has already been allocated to a group for the same AgentReferenceNumber in the BPR

The `isSubscribedToETMP` flag will be true if BPR's `isAsAgent` flag is true.

Notes: 
1. The Agents team have implemented this through necessity however we believe this should be part of the Business Registration service.    
2. It is anticipated that additional information will be added to the json response.

### Subscribe Registered Taxpayer to Agent Services

    POST /agent-subscription/subscription
    
This API allows for an agent to subscribe to Agent Services using their details    
    
Request body:

```json
    {
      "utr": "<SA or CT UTR>",
      "knownFacts": {
            "postcode": "<postcode of the agency's registered taxpayer address (NOT their agency address)>"
      }
      "agency": {
        "name": "AgencyName"
        "address": {
          "addressLine1": "Line1"
          "addressLine2": "Line2" //optional
          "addressLine3": "Line3" //optional
          "addressLine4": "Line4" //optional
          "postcode": "<postcode of the agency's agency address>",
          "countryCode": "GB"
        }
        "telephone": "1234" // optional
        "email": a@a.com,
        "amlsDetails": {   //optional
            "utr":"4000000009",
            "supervisoryBody":"supervisory",
            "membershipNumber":"12345",
            "membershipExpiresOn":"2019-11-11"
        }
      }
    }
```

Possible responses:

#### Forbidden
Response 403 if DES returns no registration that matches the user details.

#### Conflict
Response 409 if the enrolment is already allocated to someone else

#### InternalServerError
Response 500 if there is an illegal state

#### Ok
Response: 201 Created with

    Location: /agent-subscription/subscription/:arn
```json
    {
      "arn": <the Agency Registration Number for this agency, as returned to us by DES/ETMP>
    }
```

### Subscribe Partially-Subscribed Registered Taxpayer to Agent Services

    PUT /agent-subscription/subscription
    
This API allows for an agent to fully subscribe to Agent Services using their details when they are already subscribed to ETMP 
and they have not completed the enrollment to HMRC-AS-AGENT.  
    
Request body:

```json
    {
      "utr": "<SA or CT UTR>",
      "knownFacts": {
        "postcode": "<postcode of the agency's registered taxpayer address (NOT their agency address)>"
      }
    }
```

Possible responses:

#### Forbidden
Response 403 if DES returns no registration that matches the user details.

#### Conflict
Response 409 if the enrolment is already allocated to someone else

#### InternalServerError
Response 500 if there is an illegal state

#### Ok
Response: 200 OK

    Location: /agent-subscription/subscription/:arn
```json
    {
      "arn": <the Agency Registration Number for this agency, as returned to us by DES/ETMP>
    }
```

### Register and Subscribe Overseas Agent to Agent Services

    PUT /agent-subscription/overseas-subscription

This API will register an overseas agent organisation and subscribe them
to Agent Services. The application details are retrieved for the logged
in user.

If the application is in the "accepted" state, the following main steps
are followed and the ARN is returned in the response if successful:
1. updates the application status to "attempting_registration"
2. registers the organisation in ETMP (creates a Business Partner record)
3. updates the application status to "registered"
4. subscribes them to Agent Services in ETMP, obtaining a new Agent Reference Number
5. enrols the currently logged in agent to HMRC-AS-AGENT with their new Agent Reference Number.
6. updates the application status to "complete"

If the application is in the "attempting_registration" state, no further
progress will be made. It is assumed that the call to Register them in
ETMP has previously failed and ETMP must be contacted to resolve the
failure.

If the application is in the "registered" state, then steps 4 through 6
are re-attempted and the ARN is returned in the response if successful.

If the application is in the "complete" state, then step 5 is
re-attempted (which would fix any enrolment that may have been removed),
and the ARN is returned in the response if successful.

Possible responses:

#### Unauthorized
Response 401 if there is no logged in user

#### Forbidden
Response 403 if:
- the logged in user is not an Agent
- the application status is 'attempting_registration'

#### Conflict
Response 409 if the HMRC-AS-AGENT enrolment is already allocated to a group

#### InternalServerError
Response 500 if there is an illegal state

#### Ok
Response: 201 Created with

    Location: /agent-subscription/subscription/:arn
```json
    {
      "arn": <the Agency Registration Number for this agency, as returned to us by DES/ETMP>
    }
```

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
