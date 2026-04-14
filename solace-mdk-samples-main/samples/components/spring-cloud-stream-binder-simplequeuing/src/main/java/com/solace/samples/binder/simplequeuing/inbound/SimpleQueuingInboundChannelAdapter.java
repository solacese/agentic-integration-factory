package com.solace.samples.binder.simplequeuing.inbound;

import com.solace.samples.binder.simplequeuing.inbound.acknowledge.SimpleQueuingAcknowledgmentCallback;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingConnectionProperties;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingConsumerProperties;
import com.solace.samples.simplequeuing.client.SimpleQueuingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.integration.IntegrationMessageHeaderAccessor;
import org.springframework.integration.acks.AckUtils;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MessageProducer that connects to the Simple Queuing service, polls for messages,
 * and converts them to Spring Integration messages.
 */
public class SimpleQueuingInboundChannelAdapter extends MessageProducerSupport {
    private static final Logger logger = LoggerFactory.getLogger(SimpleQueuingInboundChannelAdapter.class);

    private final SimpleQueuingConnectionProperties connectionProperties;
    private final SimpleQueuingConsumerDestination destination;
    private final ExtendedConsumerProperties<SimpleQueuingConsumerProperties> consumerProperties;
    private final RestTemplateBuilder restTemplateBuilder;
    private SimpleQueuingClient simpleQueuingClient;
    private ExecutorService executorService;
    private volatile boolean running = false;

    /**
     * Creates a new inbound channel adapter for consuming messages from Simple Queuing service.
     *
     * @param connectionProperties The binder properties
     * @param destination The consumer destination
     * @param properties The consumer properties
     * @param restTemplateBuilder The RestTemplateBuilder instance used to create the client
     */
    public SimpleQueuingInboundChannelAdapter(SimpleQueuingConnectionProperties connectionProperties, SimpleQueuingConsumerDestination destination,
                                              ExtendedConsumerProperties<SimpleQueuingConsumerProperties> properties,
                                              RestTemplateBuilder restTemplateBuilder) {
        this.connectionProperties = connectionProperties;
        this.destination = destination;
        this.consumerProperties = properties;
        this.restTemplateBuilder = restTemplateBuilder;
    }

    @Override
    protected void doStart() {
        if (isRunning()) {
            logger.warn("Nothing to do. SimpleQueuingInboundChannelAdapter is already running");
            return;
        }

        simpleQueuingClient = new SimpleQueuingClient(connectionProperties.getBaseUrl(), restTemplateBuilder);

        logger.info("[destination: {}] Starting Simple Queuing inbound channel adapter", destination.getName());

        //TODO: Only concurrency=1 supported by the Simple Queuing service for now
        executorService = Executors.newFixedThreadPool(
                consumerProperties.getConcurrency(),
                new CustomizableThreadFactory("simple-queuing-consumer-%s".formatted(consumerProperties.getBindingName()))
        );

        for (int i = 0; i < consumerProperties.getConcurrency(); i++) {
            executorService.submit(new PollingWorker());
        }

        running = true;
    }

    @Override
    protected void doStop() {
        logger.info("[destination: {}] Stopping Simple Queuing inbound channel adapter", destination.getName());

        running = false;

        // Shutdown the executor service
        if (executorService != null) {
            executorService.shutdown();
            try {
                // Wait for tasks to complete or timeout
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                    if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                        logger.warn("Executor did not terminate");
                    }
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (simpleQueuingClient != null) {
            simpleQueuingClient.close();
            simpleQueuingClient = null;
        }
    }

    /**
     * The polling worker that actually fetches messages from the Simple Queuing service.
     */
    private class PollingWorker implements Runnable {
        @Override
        public void run() {
            logger.info("[destination: {}] Starting polling worker", destination.getName());
            SimpleQueuingConsumerProperties consumerProps = consumerProperties.getExtension();
            Long pollingInterval = consumerProps.getPollingInterval();

            try {
                while (running && !Thread.currentThread().isInterrupted())  {
                    try {
                        simpleQueuingClient.pollMessage(destination.getName()).ifPresent(restMessage -> {
                            SimpleQueuingAcknowledgmentCallback acknowledgmentCallback =
                                    new SimpleQueuingAcknowledgmentCallback(restMessage.getDestination(), restMessage.getId(), simpleQueuingClient);
                            Message<?> message;
                            try {
                                // Convert the REST message to a Spring Integration message with AcknowledgmentCallback
                                MessageBuilder<?> builder = MessageBuilder.withPayload(restMessage.getPayload()).copyHeaders(restMessage.getHeaders());
                                builder.setHeader(IntegrationMessageHeaderAccessor.ACKNOWLEDGMENT_CALLBACK, acknowledgmentCallback);
                                message = builder.build();
                            } catch (Exception e) {
                                logger.warn("Error converting message {} to a spring message", restMessage.getId(), e);
                                if (sendErrorMessageIfNecessary(null, e)) {
                                    AckUtils.autoAck(acknowledgmentCallback);
                                } else {
                                    // Poison messages should be rejected
                                    AckUtils.reject(acknowledgmentCallback);
                                }
                                return;
                            }

                            try {
                                sendMessage(message);
                                // Auto-ack if needed
                                AckUtils.autoAck(acknowledgmentCallback);
                            } catch (Exception e) {
                                // Requeue messages which fail processing (in our sample, this means sending to a DLQ)
                                AckUtils.requeue(acknowledgmentCallback);
                            }
                        });

                        // Wait between polls based on configured value
                        logger.debug("Polling interval: {} ms", pollingInterval);
                        Thread.sleep(pollingInterval);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        logger.warn("An unexpected processing error was caught", e);
                    }
                }
            } finally {
                logger.info("[destination: {}] Polling worker stopped (interrupted: {})", destination.getName(), Thread.currentThread().isInterrupted());
            }
        }
    }
}
