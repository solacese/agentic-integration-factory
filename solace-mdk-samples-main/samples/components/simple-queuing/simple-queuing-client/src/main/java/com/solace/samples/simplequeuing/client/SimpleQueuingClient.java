package com.solace.samples.simplequeuing.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Client for interacting with the Simple Queuing Service API.
 * Thread-safe singleton implementation that can be used concurrently from multiple bindings.
 * <p>
 * Since this is a sample project, this module has no tests but an actual implementation would include unit and integration tests.
 */
public class SimpleQueuingClient {
    private static final Logger logger = LoggerFactory.getLogger(SimpleQueuingClient.class);
    
    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ExecutorService executorService;

    /**
     * Create a new SimpleQueuingServiceClient instance.
     *
     * @param baseUrl Base URL of the queuing service
     * @param restTemplateBuilder Builder for creating RestTemplate
     */
    public SimpleQueuingClient(String baseUrl, RestTemplateBuilder restTemplateBuilder) {
        this(baseUrl, restTemplateBuilder.build());
    }

    public SimpleQueuingClient(String baseUrl, RestTemplate restTemplate) {
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
        this.executorService = Executors.newCachedThreadPool(r -> new Thread(r, "simple-queuing-async-client"));
        logger.info("SimpleQueuingServiceClient initialized with base URL: {}", baseUrl);
    }

    /**
     * Simulates an asynchronous message queuing operation.
     * The actual queueMessage() call is synchronous but wrapped in a CompletableFuture to simulate async behavior.
     *
     * @param destination The destination name
     * @param messageRequest The message request containing payload and headers
     * @return A CompletableFuture that will complete with the message response
     */
    public CompletableFuture<MessageResponse> queueMessageAsync(String destination, MessageRequest messageRequest) {
        return CompletableFuture.supplyAsync(() -> queueMessage(destination, messageRequest), executorService);
    }
    
    /**
     * Queue a message on the specified destination.
     *
     * @param destination The destination name
     * @param messageRequest The message request containing payload and headers
     * @return The message response containing the message ID
     * @throws RestClientException if there's an error communicating with the service
     */
    public MessageResponse queueMessage(String destination, MessageRequest messageRequest) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<MessageRequest> requestEntity = new HttpEntity<>(messageRequest, headers);
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/destinations/{destination}/messages")
                    .buildAndExpand(destination)
                    .toUriString();
            
            ResponseEntity<MessageResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST, 
                    requestEntity, 
                    MessageResponse.class);
            
            if (response.getBody() != null) {
                logger.debug("Successfully queued message on destination: {}, message ID: {}", 
                        destination, response.getBody().getId());
                return response.getBody();
            } else {
                logger.warn("Queued message but received null response from server for destination: {}", destination);
                throw new RestClientException("Received null response when queuing message");
            }
        } catch (Exception e) {
            throw new RestClientException("Error queuing message to destination %s".formatted(destination), e);
        }
    }
    
    /**
     * Poll for a message from the specified destination.
     *
     * @param destination The destination name
     * @return Optional containing the message response if available
     */
    public Optional<MessageResponse> pollMessage(String destination) {
        try {
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/destinations/{destination}/messages")
                    .buildAndExpand(destination)
                    .toUriString();
            
            ResponseEntity<MessageResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    MessageResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Successfully polled message from destination: {}, message ID: {}", 
                        destination, response.getBody().getId());
                return Optional.of(response.getBody());
            } else {
                logger.debug("No messages available on destination: {}", destination);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.error("Unexpected error polling message from destination {}", destination, e);
            return Optional.empty();
        }
    }
    
    /**
     * Acknowledge a message on the specified destination.
     *
     * @param destination The destination name
     * @param messageId The message ID to acknowledge
     * @return The acknowledged message response
     * @throws RestClientException if there's an error communicating with the service
     */
    public MessageResponse acknowledgeMessage(String destination, String messageId) {
        try {
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/destinations/{destination}/messages/{id}/ack")
                    .buildAndExpand(destination, messageId)
                    .toUriString();
            
            ResponseEntity<MessageResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    null,
                    MessageResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Successfully acknowledged message on destination: {}, message ID: {}", 
                        destination, messageId);
                return response.getBody();
            } else {
                logger.warn("Acknowledged message but received null response from server for message ID: {}", messageId);
                throw new RestClientException("Received null response when acknowledging message");
            }
        } catch (Exception e) {
            throw new RestClientException("Error acknowledging message %s on destination %s".formatted(messageId, destination), e);
        }
    }
    
    /**
     * List all available destinations.
     *
     * @return List of destinations
     */
    public List<Destination> listDestinations() {
        try {
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/destinations")
                    .toUriString();
            
            ResponseEntity<List<Destination>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    });
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Successfully retrieved {} destinations", response.getBody().size());
                return response.getBody();
            } else {
                logger.debug("No destinations available");
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("Unexpected error listing destinations", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Create a new destination.
     *
     * @param destinationRequest The destination request
     * @return The created destination
     * @throws RestClientException if there's an error communicating with the service
     */
    public Destination createDestination(DestinationRequest destinationRequest) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<DestinationRequest> requestEntity = new HttpEntity<>(destinationRequest, headers);
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/destinations")
                    .toUriString();
            
            ResponseEntity<Destination> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST, 
                    requestEntity, 
                    Destination.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Successfully created destination: {}", destinationRequest.getName());
                return response.getBody();
            } else {
                logger.warn("Created destination but received null response from server for destination: {}", 
                        destinationRequest.getName());
                throw new RestClientException("Received null response when creating destination %s".formatted(destinationRequest.getName()));
            }
        } catch (Exception e) {
            throw new RestClientException("Error creating destination: %s".formatted(destinationRequest.getName()), e);
        }
    }
    
    /**
     * Delete a destination.
     *
     * @param destination The name of the destination to delete
     * @return true if the destination was successfully deleted, false if it didn't exist
     * @throws RestClientException if there's an error communicating with the service
     */
    public boolean deleteDestination(String destination) {
        try {
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/destinations/{destination}")
                    .buildAndExpand(destination)
                    .toUriString();
            
            ResponseEntity<Void> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    null,
                    Void.class);
            
            if (response.getStatusCode().value() == 204) {
                logger.debug("Successfully deleted destination: {}", destination);
                return true;
            } else {
                logger.warn("Unexpected response code {} when deleting destination: {}", response.getStatusCode().value(), destination);
                return false;
            }
        } catch (Exception e) {
            throw new RestClientException("Error deleting destination: %s".formatted(destination), e);
        }
    }
    
    /**
     * Check if the Simple Queuing Service is available.
     *
     * @return true if the service is available, false otherwise
     */
    public boolean isHealthy() {
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/actuator/health")
                .toUriString();

        ResponseEntity<HealthResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                HealthResponse.class);

        if (response.getStatusCode().is2xxSuccessful()
                && response.getBody() != null
                && "UP".equalsIgnoreCase(response.getBody().getStatus())) {
            return true;
        } else {
            throw new IllegalStateException("Simple Queuing Service is not healthy Code:%d State:%s".formatted(
                    response.getStatusCode().value(), (response.getBody() != null ? response.getBody().getStatus() : "No response body")));
        }
    }
    
    /**
     * Delete all messages from the specified destination.
     *
     * @param destination The destination name
     * @return The number of messages deleted
     * @throws RestClientException if there's an error communicating with the service
     */
    public int deleteAllMessages(String destination) {
        try {
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/destinations/{destination}/messages/all")
                    .buildAndExpand(destination)
                    .toUriString();
            
            ResponseEntity<DeleteMessagesResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    null,
                    DeleteMessagesResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                if (response.getStatusCode().value() == 204) {
                    // No content means no messages were in the queue to delete
                    logger.debug("No messages were in the destination to delete: {}", destination);
                    return 0;
                } else if (response.getBody() != null) {
                    // 200 OK with count of deleted messages
                    logger.debug("Successfully deleted {} messages from destination: {}", 
                            response.getBody().getCount(), destination);
                    return response.getBody().getCount();
                } else {
                    logger.warn("Deleted messages but received null response from server for destination: {}", destination);
                    return 0;
                }
            } else {
                logger.warn("Unexpected response code when deleting messages: {}", response.getStatusCode().value());
                return 0;
            }
        } catch (Exception e) {
            throw new RestClientException("Error emptying destination %s".formatted(destination), e);
        }
    }
    
    /**
     * Reject a message on the specified destination, moving it to the dead letter queue.
     *
     * @param destination The destination name
     * @param messageId The message ID to reject
     * @return The rejected message response
     * @throws RestClientException if there's an error communicating with the service
     */
    public MessageResponse rejectMessage(String destination, String messageId) {
        try {
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/destinations/{destination}/messages/{id}/reject")
                    .buildAndExpand(destination, messageId)
                    .toUriString();
            
            ResponseEntity<MessageResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    null,
                    MessageResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Successfully rejected message on destination: {}, message ID: {}", 
                        destination, messageId);
                return response.getBody();
            } else {
                logger.warn("Rejected message but received null response from server for message ID: {}", messageId);
                throw new RestClientException("Received null response when rejecting message");
            }
        } catch (Exception e) {
            throw new RestClientException("Unexpected error rejecting message %s on destination %s".formatted(messageId, destination), e);
        }
    }
    
    /**
     * Get all rejected messages from the dead letter queue.
     *
     * @return List of all rejected messages
     */
    public List<MessageResponse> getRejectedMessages() {
        try {
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/destinations/DLQ/messages/all")
                    .toUriString();
            
            ResponseEntity<List<MessageResponse>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    });
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Successfully retrieved {} rejected messages from DLQ", response.getBody().size());
                return response.getBody();
            } else {
                logger.debug("No rejected messages available in DLQ");
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("Unexpected error retrieving rejected messages", e);
            return Collections.emptyList();
        }
    }

    /**
     * Close the client and release resources.
     */
    public void close() {
        logger.info("Shutting down SimpleQueuingServiceClient executor service");
        executorService.shutdown();
        try {
            // Wait a bit for existing tasks to terminate
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("Executor did not terminate in the specified time, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Executor shutdown interrupted", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public static class HealthResponse {
        private String status;

        public HealthResponse(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
    
    /**
     * Data Transfer Object for message requests.
     */
    public static class MessageRequest {
        private String payload;
        private Map<String, Object> headers;
        
        public MessageRequest() {
        }
        
        public MessageRequest(String payload, Map<String, Object> headers) {
            this.payload = payload;
            this.headers = headers;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, Object> headers) {
            this.headers = headers;
        }
    }

    public static class MessageResponse {
        private String id;
        private String destination;
        private String payload;
        private Map<String, Object> headers;
        
        public MessageResponse() {
        }
        
        public MessageResponse(String id, String destination, String payload, Map<String, Object> headers) {
            this.id = id;
            this.destination = destination;
            this.payload = payload;
            this.headers = headers;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, Object> headers) {
            this.headers = headers;
        }
    }

    public static class Destination {
        private String name;
        private Integer messageCount;
        
        public Destination() {
        }
        
        public Destination(String name, Integer messageCount) {
            this.name = name;
            this.messageCount = messageCount;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getMessageCount() {
            return messageCount;
        }

        public void setMessageCount(Integer messageCount) {
            this.messageCount = messageCount;
        }
    }

    public static class DestinationRequest {
        private String name;
        
        public DestinationRequest() {
        }
        
        public DestinationRequest(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
    
    /**
     * Data Transfer Object for delete messages response.
     */
    public static class DeleteMessagesResponse {
        private int count;
        
        public DeleteMessagesResponse() {
        }
        
        public DeleteMessagesResponse(int count) {
            this.count = count;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
