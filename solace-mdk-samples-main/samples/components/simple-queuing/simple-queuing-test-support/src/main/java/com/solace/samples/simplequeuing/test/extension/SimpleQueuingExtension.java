package com.solace.samples.simplequeuing.test.extension;

import com.solace.samples.simplequeuing.client.SimpleQueuingClient;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * JUnit 5 extension that starts the SimpleQueuing service in a container and provides a preconfigured client for testing.
 * <p>
 * Usage:
 * 1. Add the extension to your test class: @ExtendWith(SimpleQueuingExtension.class)
 * 2. Add a parameter of type SimpleQueuingClient to your test methods. The injected client is preconfigured and ready to use.
 * 3. No need to clean up the container; testcontainers' Ryuk container terminates the container on JVM exit.
 *
 */
public class SimpleQueuingExtension implements ParameterResolver {

    private static final ExtensionContext.Namespace EXTENSION_NAMESPACE = ExtensionContext.Namespace.create(SimpleQueuingExtension.class);
    private static final String CONTAINER_KEY = "SIMPLE_QUEUING_CONTAINER_KEY";
    private static final String CLIENT_KEY = "SIMPLE_QUEUING_CLIENT_KEY";

    private static final String SIMPLE_QUEUING_IMAGE = "simple-queuing-service:latest";
    private static final int SIMPLE_QUEUING_SERVICE_PORT = 8088;

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().isAssignableFrom(SimpleQueuingClient.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (parameterContext.getParameter().getType().isAssignableFrom(SimpleQueuingClient.class)) {
            return getClient(extensionContext);
        } else {
            throw new ParameterResolutionException("Unsupported parameter type: " + parameterContext.getParameter().getType());
        }
    }

    private SimpleQueuingClient getClient(ExtensionContext extensionContext) {
        // Using extension context root since the client has the same lifecycle as the container.
        return extensionContext.getRoot().getStore(EXTENSION_NAMESPACE)
                .getOrComputeIfAbsent(CLIENT_KEY, key -> createClient(extensionContext), SimpleQueuingClient.class);
    }

    private SimpleQueuingClient createClient(ExtensionContext extensionContext) {
        GenericContainer<?> container = getContainer(extensionContext);
        String queuingServiceBaseURL = "http://%s:%d".formatted(
                container.getHost(),
                container.getMappedPort(SIMPLE_QUEUING_SERVICE_PORT)
        );
        return new SimpleQueuingClient(queuingServiceBaseURL, new RestTemplate());
    }

    private GenericContainer<?> getContainer(ExtensionContext extensionContext) {
        // Using extension context root since container is created once and reused across test classes.
        return extensionContext.getRoot().getStore(EXTENSION_NAMESPACE)
                .getOrComputeIfAbsent(CONTAINER_KEY, key -> createContainer(), GenericContainer.class);
    }

    private GenericContainer<?> createContainer() {
        GenericContainer<?> container = new GenericContainer<>(SIMPLE_QUEUING_IMAGE)
                .withExposedPorts(SIMPLE_QUEUING_SERVICE_PORT)
                .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));
        container.start();

        return container;
    }

}
