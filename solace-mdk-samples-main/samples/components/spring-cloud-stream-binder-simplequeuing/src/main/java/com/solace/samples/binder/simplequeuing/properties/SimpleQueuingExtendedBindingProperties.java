package com.solace.samples.binder.simplequeuing.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.stream.binder.AbstractExtendedBindingProperties;
import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * Extended binding properties for Simple Queuing binder.
 */
@ConfigurationProperties("spring.cloud.stream.simple-queuing")
@Validated
public class SimpleQueuingExtendedBindingProperties extends AbstractExtendedBindingProperties<SimpleQueuingConsumerProperties, SimpleQueuingProducerProperties, SimpleQueuingBindingProperties> {

    protected static final String DEFAULTS_PREFIX = "spring.cloud.stream.simple-queuing.default";

    @Override
    public String getDefaultsPrefix() {
        return DEFAULTS_PREFIX;
    }

    @Override
    public Map<String, SimpleQueuingBindingProperties> getBindings() {
        return super.doGetBindings();
    }

    @Override
    public Class<? extends BinderSpecificPropertiesProvider> getExtendedPropertiesEntryClass() {
        return SimpleQueuingBindingProperties.class;
    }
}
