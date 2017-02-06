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