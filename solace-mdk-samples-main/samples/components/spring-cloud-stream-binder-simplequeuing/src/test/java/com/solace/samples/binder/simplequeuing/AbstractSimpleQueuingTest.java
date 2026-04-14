package com.solace.samples.binder.simplequeuing;

import com.solace.samples.simplequeuing.client.SimpleQueuingClient;
import com.solace.samples.simplequeuing.test.extension.SimpleQueuingExtension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Abstract base class for SimpleQueuing tests that provides common functionality:
 * - Managing the SimpleQueuingClient instance
 * - Creating/deleting destinations
 * - Configuring dynamic properties
 * <p
 * Note: Most of the functionality in this class could be provided by the SimpleQueuingExtension by implementing corresponding callbacks.
 */
@ExtendWith(SimpleQueuingExtension.class)
@DirtiesContext
public abstract class AbstractSimpleQueuingTest {

    protected static SimpleQueuingClient simpleQueuingClient;

    protected static final String SOURCE_DESTINATION = "source-destination";
    protected static final String TARGET_DESTINATION = "target-destination";
    protected static final String DYNAMIC_DESTINATION = "dynamic-destination";

    @BeforeAll
    static void init(SimpleQueuingClient simpleQueuingClient) {
        AbstractSimpleQueuingTest.simpleQueuingClient = simpleQueuingClient;
        createDestination(SOURCE_DESTINATION);
        createDestination(TARGET_DESTINATION);
        createDestination(DYNAMIC_DESTINATION);
    }

    /**
     * Configures the TestApplication so that it can connect to the Simple Queuing service.
     */
    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("simple-queuing.base-url", () -> simpleQueuingClient.getBaseUrl());
    }

    @AfterEach
    void cleanup() {
        simpleQueuingClient.deleteAllMessages(SOURCE_DESTINATION);
        simpleQueuingClient.deleteAllMessages(TARGET_DESTINATION);
        simpleQueuingClient.deleteAllMessages(DYNAMIC_DESTINATION);
    }

    @AfterAll
    static void cleanupDestinations() {
        simpleQueuingClient.deleteDestination(SOURCE_DESTINATION);
        simpleQueuingClient.deleteDestination(TARGET_DESTINATION);
        simpleQueuingClient.deleteDestination(DYNAMIC_DESTINATION);
    }

    /**
     * Creates a destination with the specified name
     * 
     * @param destinationName the name of the destination to create
     */
    protected static void createDestination(String destinationName) {
        simpleQueuingClient.createDestination(new SimpleQueuingClient.DestinationRequest(destinationName));
    }

}
