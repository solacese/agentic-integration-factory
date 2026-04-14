package com.solace.samples.binder.simplequeuing.outbound;

import org.springframework.cloud.stream.provisioning.ProducerDestination;

/**
 * Implementation of ProducerDestination for Simple Queuing binder.
 */
public record SimpleQueuingProducerDestination(String name) implements ProducerDestination {
    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getNameForPartition(int partition) {
        return name();
    }
}
