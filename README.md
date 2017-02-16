# http-rpc-client

A library for calling RPC over HTTP

Usage
---------------------
resttest-app is a dummy HTTP server. To run simultaneous copies of the server on a single machine just
run multiple instances on different ports using the command:

```bash
mvn -Djetty.port=8887 jetty:run
```

httpclient-app is the client app. 

TODO
---------------------
- Implement "The Circut breaker". 
 - check if a server is up (and break if not), probing and updating the current state of the server (autorecovery),  predictable fault handling
- Load balancing strategy
 - how to distribute load, multiple possibilities - round robin, weighted based on response times
 - interaction between the Circut breaker and load balancer
- Conection pool handling
- error handling
- timeouts (request and connect)
- retries 

Behaviour
---------------------
Client differentiates between idempotent calls and non-idempotent

- Idempotent calls
 - Server Down failiures:
  - calls are directed to the running servers from the server list
  - autorecovery when servers are up again based on an HTTP query
  - no runnig server available - call throws an exception
 - Service endpoint HTTP errors:
  - 4XX (bad requests) an exception os thrown
  - 5XX (server errors) the request is retried on another host and then an exception is thrown
  - if a critical amount of 5XX responses are received a Circuit breaker will trip for the particular endpoint. The breaker closes after a specified amount of time
 - Service timout
  - the call will be retried on another host
- Non-Idempotent calls
 - Server Down failiures:
  - calls are directed to the running servers from the server list
  - autorecovery when servers are up again based on an HTTP query
  - no runnig server available - call throws an exception
 - Service endpoint HTTP errors:
  - 4XX (bad requests) or 5XX (server errors) an exception is thrown
  - if a critical amount of 5XX responses are received a Circuit breaker will trip for the particular endpoint. The breaker closes after a specified amount of time
 - Service timout
  - and exception will be thrown
 




