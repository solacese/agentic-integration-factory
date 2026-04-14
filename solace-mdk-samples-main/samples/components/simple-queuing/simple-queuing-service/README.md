# Simple Queuing Service

A simple message queuing service implemented as a Spring Boot application. This service provides REST APIs for message queuing and polling from known destinations.

## Features

- Create and list destinations (queues)
- Queue messages to specific destinations
- Poll for messages from destinations
- Acknowledge messages after processing
- List all messages in a destination
- Delete all messages from a destination
- In-memory implementation using Java BlockingQueue
- Docker containerization with Jib

## API Documentation

When the service is running, you can access the OpenAPI documentation at:

- Swagger UI: http://localhost:8088/swagger-ui.html
- OpenAPI JSON: http://localhost:8088/api-docs

## Building the Project

### Prerequisites

- JDK 17
- Docker, Podman (optional, for running the container)

### Build Commands

To build the project as a JAR and TAR files:

```bash
./mvnw clean package
```

The Docker image will be created as a tar file at `target/simple-queuing-service-<version>.tar`.

### Running the Application

#### Running the JAR

```bash
java -jar target/simple-queuing-service-1.0.0-SNAPSHOT.jar
```

#### Running the Docker Image

```bash
docker load < target/simple-queuing-service-1.0.0-SNAPSHOT.tar
docker run -p 8088:8088 simple-queuing-service:1.0.0-SNAPSHOT
```

## Examples

### Create a Destination

```bash
curl -X POST http://localhost:8088/destinations \
  -H "Content-Type: application/json" \
  -d '{"name": "my-queue"}'
```

Creating destinations is optional as sending a message auto-creates the destination.

### Send a Message

```bash
curl -X POST http://localhost:8088/destinations/my-queue/messages \
  -H "Content-Type: application/json" \
  -d '{"payload": "greeting", "headers": {"priority": "high"}}'
```

### Poll for a Message

```bash
curl -X GET http://localhost:8088/destinations/my-queue/messages
```

### Acknowledge a Message

```bash
curl -X POST http://localhost:8088/destinations/my-queue/messages/{message-id}/ack
```

### List All Messages in a Destination

```bash
curl -X GET http://localhost:8088/destinations/my-queue/messages/all
```

### List All Destinations

```bash
curl -X GET http://localhost:8088/destinations
```

### Delete All Messages in a Destination

```bash
curl -X DELETE http://localhost:8088/destinations/my-queue/messages/all
```

This endpoint returns the number of messages deleted or 204 No Content if no messages were found.
