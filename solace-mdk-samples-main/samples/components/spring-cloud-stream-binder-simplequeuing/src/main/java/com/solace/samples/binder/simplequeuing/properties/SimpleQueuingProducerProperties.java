package com.solace.samples.binder.simplequeuing.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Simple Queuing message producers.
 */
@SuppressWarnings("ConfigurationProperties") // only used to generate auto-config metadata file
@ConfigurationProperties(SimpleQueuingExtendedBindingProperties.DEFAULTS_PREFIX + ".producer")
public class SimpleQueuingProducerProperties {

}
