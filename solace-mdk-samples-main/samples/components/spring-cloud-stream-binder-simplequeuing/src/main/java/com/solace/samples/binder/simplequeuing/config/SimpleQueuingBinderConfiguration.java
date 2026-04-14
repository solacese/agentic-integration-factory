package com.solace.samples.binder.simplequeuing.config;

import com.solace.samples.binder.simplequeuing.SimpleQueuingBinder;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingConnectionProperties;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingExtendedBindingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.stream.binder.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration class for the Simple Queuing binder.
 * Creates and configures the binder and its dependencies.
 */
@Configuration
@ConditionalOnMissingBean(Binder.class)
@EnableConfigurationProperties({SimpleQueuingExtendedBindingProperties.class, SimpleQueuingConnectionProperties.class})
@Import(SimpleQueuingBinderHealthConfiguration.class)
public class SimpleQueuingBinderConfiguration {

    /**
     * Creates the Simple Queuing binder.
     *
     * @param connectionProperties The binder properties
     * @param extendedBindingProperties The extended binding properties
     * @param restTemplateBuilder Builder for creating RestTemplate instances
     * @return The Simple Queuing binder
     */
    @Bean
    SimpleQueuingBinder simpleQueuingBinder(SimpleQueuingConnectionProperties connectionProperties,
                                            SimpleQueuingExtendedBindingProperties extendedBindingProperties,
                                            RestTemplateBuilder restTemplateBuilder) {
        return new SimpleQueuingBinder(connectionProperties, extendedBindingProperties, restTemplateBuilder);
    }

}
