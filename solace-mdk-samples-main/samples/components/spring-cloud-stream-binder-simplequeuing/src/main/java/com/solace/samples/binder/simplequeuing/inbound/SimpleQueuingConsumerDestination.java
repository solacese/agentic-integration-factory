package com.solace.samples.binder.simplequeuing.inbound;

import org.springframework.cloud.stream.provisioning.ConsumerDestination;

/**
 * Implementation of ConsumerDestination for Simple Queuing binder.
 */
public record SimpleQueuingConsumerDestination(String name) implements ConsumerDestination {
    @Override
    public String getName() {
        return name();
    }
}
