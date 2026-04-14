package com.solace.samples.binder.simplequeuing.outbound;

import com.solace.connector.core.io.header.ConnectorBinderHeaders;
import com.solace.connector.core.io.outbound.PublishAcknowledgmentCallback;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingConnectionProperties;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingProducerProperties;
import com.solace.samples.simplequeuing.client.SimpleQueuingClient;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.stream.binder.BinderHeaders;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.context.Lifecycle;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MessageHandler that sends messages to the Simple Queuing service.
 */
public class SimpleQueuingOutboundMessageHandler implements MessageHandler, Lifecycle {
    private static final Logger logger = LoggerFactory.getLogger(SimpleQueuingOutboundMessageHandler.class);

    private final SimpleQueuingProducerDestination destination;
    @Nullable private final MessageChannel errorChannel;
    private final ExtendedProducerProperties<SimpleQueuingProducerProperties> producerProperties;
    private final SimpleQueuingConnectionProperties connectionProperties;
    private final RestTemplateBuilder restTemplateBuilder;
    private SimpleQueuingClient simpleQueuingClient;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * Create a new outbound message handler.
     *
     * @param destination The producer destination
     * @param errorChannel The error channel for failed sends
     * @param producerProperties The producer properties
     * @param connectionProperties The connection properties
     * @param restTemplateBuilder Builder for creating RestTemplate
     */
    public SimpleQueuingOutboundMessageHandler(
            SimpleQueuingProducerDestination destination,
            @Nullable MessageChannel errorChannel,
            ExtendedProducerProperties<SimpleQueuingProducerProperties> producerProperties,
            SimpleQueuingConnectionProperties connectionProperties,
            RestTemplateBuilder restTemplateBuilder) {
        this.destination = destination;
        this.errorChannel = errorChannel;
        this.producerProperties = producerProperties;
        this.connectionProperties = connectionProperties;
        this.restTemplateBuilder = restTemplateBuilder;
    }

    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        PublishAcknowledgmentCallback publishAckCallback = Objects.requireNonNull(
            message.getHeaders().get(ConnectorBinderHeaders.PUBLISH_ACKNOWLEDGMENT_CALLBACK,
                PublishAcknowledgmentCallback.class),
            () -> "required %s header is missing, cannot do asynchronous publishing"
                .formatted(ConnectorBinderHeaders.PUBLISH_ACKNOWLEDGMENT_CALLBACK));

        String dynamicDestination = message.getHeaders().get(BinderHeaders.TARGET_DESTINATION, String.class);
        String actualDestination = dynamicDestination != null ? dynamicDestination : destination.getName();

        try {
            SimpleQueuingClient.MessageRequest request = new SimpleQueuingClient.MessageRequest((String) message.getPayload(), filterHeaders(message.getHeaders()));
            logger.debug("Sending message to destination: {}", actualDestination);
            simpleQueuingClient.queueMessageAsync(actualDestination, request)
                .thenAccept(response -> {
                    logger.debug("Successfully sent message to {}, ID: {}", response.getDestination(), response.getId());
                    publishAckCallback.onPublishSuccess();
                })
                .exceptionally(e -> {
                    logger.error("Error sending message to {}", destination.getName(), e);
                    if (errorChannel != null) {
                        errorChannel.send(new ErrorMessage(e, message));
                    }
                    publishAckCallback.onPublishFailure(e);
                    return null;
                });
        } catch (Exception e) {
            // This will only handle exceptions that occur before the CompletableFuture is created.
            // i.e. These are errors from preparing the message for sending, but before sending occurs.

            if (errorChannel != null) {
                logger.debug("Error preparing message for {}", destination.getName(), e);
                errorChannel.send(new ErrorMessage(e, message));
            }

            throw new MessagingException(message, "Failed to prepare message for " + destination.getName(), e);
        }
    }

    public static Map<String, Object> filterHeaders(MessageHeaders headers) {
        Map<String, Object> filteredHeaders = new HashMap<>(headers);
        filteredHeaders.remove(ConnectorBinderHeaders.PUBLISH_ACKNOWLEDGMENT_CALLBACK);
        filteredHeaders.remove(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK);
        filteredHeaders.remove("id");
        filteredHeaders.remove("timestamp");
        filteredHeaders.remove("target-protocol");
        return filteredHeaders;
    }

    @Override
    public void start() {
        if (simpleQueuingClient == null) {
            simpleQueuingClient = new SimpleQueuingClient(connectionProperties.getBaseUrl(), restTemplateBuilder);
        }
        logger.debug("[destination: {}] Outbound message handler started", destination.getName());
        isRunning.set(true);
    }

    @Override
    public void stop() {
        if (simpleQueuingClient != null) {
            simpleQueuingClient.close();
            simpleQueuingClient = null;
            logger.debug("[destination: {}] Outbound message handler stopped", destination.getName());
        } else {
            logger.warn("[destination: {}] Outbound message handler was not started, nothing to stop", destination.getName());
        }
        isRunning.set(false);
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }
}
