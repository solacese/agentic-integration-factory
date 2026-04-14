# Samples

This directory contains sample implementations for the Solace Micro-Integration Development Kit (MDK).

## Overview

The samples demonstrate how to build Micro-Integrations that connect Solace PubSub+ Event Broker with other technologies. These samples showcase best practices for developing with the Solace MDK.

## Project Structure

```
samples/
├── components/                           
│   ├── simple-queuing/                           # Simple Queuing components
│   │   ├── simple-queuing-service/               # A simple queuing service used as the 3rd-party vendor technology
│   │   ├── simple-queuing-client/                # Client library for the Simple Queuing service
│   │   └── simple-queuing-test-support/          # Test support utilities
│   └── spring-cloud-stream-binder-simplequeuing/ # Spring Cloud Stream binder for the Simple Queuing service
├── docker-compose/                               
│   ├── compose.yaml                              # Docker compose configuration to run the whole solution
│   └── mi-config/                                # Configuration for Micro-Integration
└── micro-integration/                            # A Micro-Integration sample implementation
```

## Architecture

```
┌─────────────────┐      ┌─────────────────────┐      ┌─────────────────┐
│                 │      │                     │      │                 │
│  Solace PubSub+ │◄────►│  Micro-Integration  │◄────►│ Simple Queuing  │
│  Event Broker   │      │                     │      │   Service       │
│                 │      │                     │      │                 │
└─────────────────┘      └─────────────────────┘      └─────────────────┘
```

The architecture demonstrates a Micro-Integration acting as a bridge between Solace PubSub+ and the Simple Queuing Service. Messages flow bidirectionally between the systems, with the Micro-Integration handling protocol translation, message transformation, and delivery assurance.

## Building and Running

### Prerequisites

- Java 17 
- Container Engine (docker, podman)

### Build and Run

> **Note**: While running the maven builds, look for the build success output at the end of the logs. For example:
> ```
> [INFO] ------------------------------------------------------------------------
> [INFO] BUILD SUCCESS
> [INFO] ------------------------------------------------------------------------
> [INFO] Total time:  1.234 s
> [INFO] Finished at: 2025-10-01T12:34:56Z
> [INFO] ------------------------------------------------------------------------
> ```
> If you see this output, it means the build was successful, and you can proceed to run the solution.

1. **Build the required components**:

```bash
cd ./samples/components && ../mvnw clean install
```

> **Note**: The build creates a Simple Queuing service docker image on the local docker daemon. To use a different engine provide -Djib.dockerClient.executable=<path-to-alternative-engine>.

2. **Build the Micro-Integration**:

```bash
cd ../micro-integration && ../mvnw clean package jib:buildTar
docker load --input ./target/jib-output/microintegration-sample-*-image.tar
```

3. **Run the complete solution**:

```bash
cd ../docker-compose && docker compose up
```

After successful startup, you should see log output confirming all services are running. The services will be available at:
- Solace PubSub+ management: http://localhost:8080 (admin/admin)
- Micro-Integration health endpoint: http://localhost:8090/actuator/health (user/pass)

## Testing the Micro-Integration

### Test messaging from SimpleQueuing to Solace:

1. Send a message to the Simple Queuing Service:

```bash
curl -X POST http://localhost:8088/destinations/source-destination/messages \
  -H "Content-Type: application/json" \
  -d '{"payload":"{\"message\": \"Hello from Simple Queuing!\"}", "headers":{"myHeader": "value"}}'
```

> **Note**: In above example, the payload is a JSON document since the sample Micro-Integration is configured to expect JSON payloads. 

2. Verify the message was bridged to Solace on the `Solace/Queue/1` queue:
   - Connect to the Solace broker management UI at http://localhost:8080
   - Navigate to the Queues section and find `Solace/Queue/1` and confirm a message is on the queue
   - Navigate to the `Try Me!` tab and consume the message from the queue.

### Test messaging from Solace to SimpleQueuing:

1. Send a message to Solace:

```bash
curl -X POST http://localhost:9000/QUEUE/Solace/Queue/0 \
  -H "Content-Type: application/json" \
  -H "Solace-User-Property-myHeader: value" \
  -d '{"message": "Hello from SOLACE!"}'
```

2. Verify the message was bridged to the Simple Queuing service's `target-destination`:

```bash
curl -X GET http://localhost:8088/destinations/target-destination/messages
```

Expected response should contain the message sent to Solace with the `myHeader` property. To preserve other properties, transforms need to be added to the Micro-Integration configuration. 

### Simple Queuing Service API

For convenience, we've provided an IntelliJ HTTP Client file that describes the available endpoints and allows users to quickly send requests to the Simple Queuing Service. You can find this file at: [/samples/components/simple-queuing/simple-queuing-service.http](/samples/components/simple-queuing/simple-queuing-service.http)

If you're using IntelliJ IDEA, you can directly execute these requests from within the IDE. If you're not using IntelliJ, you can easily convert these requests to curl commands or other formats using your preferred AI assistant.


## Further Resources

- [Spring Cloud Stream Documentation](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/)
