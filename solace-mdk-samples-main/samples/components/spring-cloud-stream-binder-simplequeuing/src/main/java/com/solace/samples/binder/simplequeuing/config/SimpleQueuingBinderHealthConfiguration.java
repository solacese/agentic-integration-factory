package com.solace.samples.binder.simplequeuing.config;

import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingConnectionProperties;
import com.solace.samples.simplequeuing.client.SimpleQueuingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Simple Queuing binder health check.
 * Provides health indicators for the Simple Queuing service connection.
 */
@Configuration
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnEnabledHealthIndicator("binders")
@EnableConfigurationProperties({SimpleQueuingConnectionProperties.class})
public class SimpleQueuingBinderHealthConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SimpleQueuingBinderHealthConfiguration.class);

    /**
     * Creates an instance of SimpleQueuingServiceClient.
     *
     * @param connectionProperties The binder properties containing base URL
     * @param restTemplateBuilder Builder for creating RestTemplate
     * @return The SimpleQueuingServiceClient instance
     */
    @Bean
    SimpleQueuingClient simpleQueuingServiceClient(SimpleQueuingConnectionProperties connectionProperties,
                                                   RestTemplateBuilder restTemplateBuilder) {
        return new SimpleQueuingClient(connectionProperties.getBaseUrl(), restTemplateBuilder);
    }

    /**
     * Creates the health indicator for the Simple Queuing binder.
     *
     * @return The health indicator bean
     */
    @Bean
    public HealthIndicator simpleQueuingBinderHealthIndicator(SimpleQueuingClient client) {
        return () -> {
            Health.Builder builder = Health.unknown();
            
            try {
                client.isHealthy();
                builder.up();
                logger.debug("Simple Queuing binder is UP");
            } catch (Exception e) {
                builder.down().withDetail("message", e.getMessage());
                logger.info("Simple Queuing binder is DOWN: {}", e.getMessage());
            }
            
            return builder.build();
        };
    }
}
