# agent-subscription

[![Build Status](https://travis-ci.org/hmrc/agent-subscription.svg)](https://travis-ci.org/hmrc/agent-subscription) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-subscription/images/download.svg) ](https://bintray.com/hmrc/releases/agent-subscription/_latestVersion)

This is a backend microservice for agent-subscription-frontend. It allows for an agent to proceed through the subscription
journey to gain an HMRC-AS-AGENT enrolment, this will allow them to access the functionality of Agent Services and easily 
interact with their clients. The domain is Subscriptions to Agent Services following the ROSM (Register Once Subscribe Many) pattern.

## Running the tests

    sbt test it:test

## Running the app locally

    sm --start AGENT_MTD -f
    sm --stop AGENT_SUBSCRIPTION
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

    {
      "isSubscribedToAgentServices": true
    }

The `isSubscribedToAgentServices` flag will be true if the following holds:
- BPR's postcode matches the `postcode` in the url
- BPR's `isAsAgent` flag is true
- HMRC-AS-AGENT enrolment has already been allocated to a group for the same AgentReferenceNumber in the BPR

Notes: 
1. The Agents team have implemented this through necessity however we believe this should be part of the Business Registration service.    
2. This endpoint is currently not secured by auth. If the end point is rehomed then this should be reconsidered.
3. It is anticipated that additional information will be added to the json response.


### Subscribe Registered Taxpayer to Agent Services

    POST /agent-subscription/subscription
    
This API allows for an agent to subscribe to Agent Services using their details    
    
Request body:

    {
      "utr": "<SA or CT UTR>",
      "knownFacts": {
            "postcode": "<postcode of the agency's registered taxpayer address (NOT their agency address)>"
      }
      "agency": {
        "name": "AgencyName"
        "address": {
          "addressLine1": "Line1"
          "addressLine2": "Line2"
          "addressLine3": "Line3"
          "addressLine4": "Line4"
          "postcode": "<postcode of the agency's agency address>",
          "countryCode": "GB"
        }
        "telephone": 1234
        "email": a@a.com
      }
    }

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
    {
      "arn": <the Agency Registration Number for this agency, as returned to us by DES/ETMP>
    }
    
### Subscribe Partially-Subscribed Registered Taxpayer to Agent Services

    PUT /agent-subscription/subscription
    
This API allows for an agent to fully subscribe to Agent Services using their details when they are already subscribed to ETMP 
and they have not completed the enrollment to HMRC-AS-AGENT.  
    
Request body:

    {
      "utr": "<SA or CT UTR>",
      "knownFacts": {
        "postcode": "<postcode of the agency's registered taxpayer address (NOT their agency address)>"
      }
    }

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
    {
      "arn": <the Agency Registration Number for this agency, as returned to us by DES/ETMP>
    }

### License


This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
