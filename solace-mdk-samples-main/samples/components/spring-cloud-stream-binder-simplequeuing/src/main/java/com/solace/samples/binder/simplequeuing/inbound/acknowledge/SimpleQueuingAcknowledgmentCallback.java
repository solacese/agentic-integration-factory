package com.solace.samples.binder.simplequeuing.inbound.acknowledge;

import com.solace.samples.simplequeuing.client.SimpleQueuingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.acks.AcknowledgmentCallback;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AcknowledgmentCallback to acknowledge messages to the Simple Queuing service.
 */
public class SimpleQueuingAcknowledgmentCallback implements AcknowledgmentCallback {
    private static final Logger logger = LoggerFactory.getLogger(SimpleQueuingAcknowledgmentCallback.class);
    
    private boolean autoAckEnabled = true;
    private final String destination;
    private final String messageId;
    private final SimpleQueuingClient simpleQueuingClient;
    private final AtomicBoolean acknowledged = new AtomicBoolean(false);

    /**
     * Create a new acknowledgment callback for a REST message.
     *
     * @param destination         Destination name the message was consumed from
     * @param messageId           ID of the message to acknowledge
     * @param simpleQueuingClient Client to ack/nack message on the Simple Queuing Service
     */
    public SimpleQueuingAcknowledgmentCallback(String destination, String messageId, SimpleQueuingClient simpleQueuingClient) {
        this.destination = destination;
        this.messageId = messageId;
        this.simpleQueuingClient = simpleQueuingClient;
    }
    
    @Override
    public void acknowledge(Status status) {
        if (isAcknowledged()) {
            logger.debug("Message {} is already acknowledged", messageId);
            return;
        }

        switch (status) {
            case ACCEPT:
                logger.debug("Acknowledging message {}", messageId);
                simpleQueuingClient.acknowledgeMessage(destination, messageId);
                break;
            case REJECT, REQUEUE:
                // Both REJECT and REQUEUE send the message to a DLQ in this sample.
                logger.info("Rejecting message {}", messageId);
                simpleQueuingClient.rejectMessage(destination, messageId);
                break;
            default:
                logger.warn("Unknown acknowledgment status {} for message {}", status, messageId);
                return;
        }

        acknowledged.set(true);
    }

    @Override
    public boolean isAcknowledged() {
        return acknowledged.get();
    }

    @Override
    public void noAutoAck() {
        autoAckEnabled = false;
    }

    @Override
    public boolean isAutoAck() {
        return autoAckEnabled;
    }

}
