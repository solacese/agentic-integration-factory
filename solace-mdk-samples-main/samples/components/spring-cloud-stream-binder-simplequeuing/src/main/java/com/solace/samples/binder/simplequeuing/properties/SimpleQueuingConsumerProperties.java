package com.solace.samples.binder.simplequeuing.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Simple Queuing message consumers.
 */
@SuppressWarnings("ConfigurationProperties")
@ConfigurationProperties(SimpleQueuingExtendedBindingProperties.DEFAULTS_PREFIX + ".consumer")
public class SimpleQueuingConsumerProperties {
    /**
     * How frequently to poll for messages in milliseconds.
     */
    private Long pollingInterval = 5000L;

    public Long getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(Long pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

}
