package com.solace.samples.binder.simplequeuing;

import com.solace.connector.core.io.header.ConnectorBinderHeaders;
import com.solace.connector.core.io.outbound.PublishAcknowledgmentCallback;
import com.solace.samples.binder.simplequeuing.app.TestApplication;
import com.solace.samples.simplequeuing.client.SimpleQueuingClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.stream.binder.BinderHeaders;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Binder integration tests that use a TestApplication to test the consumer and producer bindings.
 * These tests create an application context and an instance of the Simple Queuing service running in a container.
 * <p>
 * Note that an alternate testing strategy could be to use the Spring Cloud Stream Binder Test framework.
 * This removes the need to run a full application context but requires more upfront setup.
 * For some examples, see the <a href="https://github.com/SolaceProducts/solace-spring-cloud/tree/master/solace-spring-cloud-stream-binder/solace-spring-cloud-stream-binder">Solace Spring Cloud Stream Binder tests</a> and the <a href="https://github.com/spring-cloud/spring-cloud-stream/tree/main/binders/rabbit-binder/spring-cloud-stream-binder-rabbit">RabbitMQ Binder tests</a>.
 */
@SpringBootTest(classes = TestApplication.class)
@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class SimpleQueuingMessagingTest extends AbstractSimpleQueuingTest {

    @Autowired
    private TestApplication application;

    @Autowired
    private StreamBridge streamBridge;

    private final String outputBindingName = "testSupplier-out-0";

    @BeforeEach
    void testCleanup() {
        application.clearReceivedMessages();
        application.clearErrorChannelMessages();
    }

    /**
     * Test that the consumer binding can successfully poll messages from the SimpleQueuingService.
     * <p>
     * A message is queued on the SimpleQueuingService, then the consumer binding is expected to receive it from the source-destination.
     * This test uses TestApplication.receiveMessage() to confirm the consumer binding received and processed the message successfully.
     */
    @Test
    void testConsumerBindingSuccessfulProcess() throws InterruptedException {
        String testPayload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
        Message<String> message = MessageBuilder.withPayload(testPayload)
            .setHeader("test-header", "1234")
            .build();
        simpleQueuingClient.queueMessage(SOURCE_DESTINATION, new SimpleQueuingClient.MessageRequest(testPayload, message.getHeaders()));

        // Verify the message was received by the consumer binding and properly processed
        Message<?> receivedMessage = application.receiveMessage(10, TimeUnit.SECONDS);
        assertThat(receivedMessage)
                .isNotNull()
                .satisfies(msg -> {
                    assertThat(msg.getPayload()).isEqualTo(testPayload);
                    assertThat(msg.getHeaders()).containsEntry("test-header", "1234");
                });

        //Ensures that the message was acknowledged and not re-delivered
        assertThat(application.receiveMessage(1, TimeUnit.SECONDS)).as("Message was unexpectedly redelivered").isNull();
    }

    /**
     * Test that the consumer binding can handle a failed process scenario.
     * <p>
     * A message is queued on the SimpleQueuingService with a header that indicates it should be rejected.
     * The test verifies that the message was rejected and sent to the DLQ.
     */
    @Test
    void testConsumerBindingFailedProcess() {
        String testPayload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
        Message<String> message = MessageBuilder.withPayload(testPayload)
                .setHeader("reject_me", "true") // TestApplication REJECTs messages with this header
                .build();
        SimpleQueuingClient.MessageResponse response = simpleQueuingClient.queueMessage(SOURCE_DESTINATION, new SimpleQueuingClient.MessageRequest(testPayload, message.getHeaders()));

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(simpleQueuingClient.getRejectedMessages()).anyMatch(msg -> msg.getId().equals(response.getId()));
                });
    }

    /**
     * Test that the producer binding can successfully push messages to the SimpleQueuingService.
     * <p>
     * A spring message is sent to the producer binding which is expected to convert and publish it to the SimpleQueuingService.
     * The test retrieves the message from the SimpleQueuingService to confirm the message was successfully published.
     */
    @Test
    void testProducerBindingSuccessfulPublish(@Mock PublishAcknowledgmentCallback ackCallback) {
        String testPayload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
        Message<String> testMessage = MessageBuilder.withPayload(testPayload)
            .setHeader("test-header", "abcd")
            .setHeader(ConnectorBinderHeaders.PUBLISH_ACKNOWLEDGMENT_CALLBACK, ackCallback)
            .build();

        // Send the message through the StreamBridge to the producer binding
        boolean sent = streamBridge.send(outputBindingName, testMessage);
        assertTrue(sent, "Message should have been accepted by StreamBridge");

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<SimpleQueuingClient.MessageResponse> messageResponse = simpleQueuingClient.pollMessage(TARGET_DESTINATION);
                    assertThat(messageResponse)
                            .isPresent()
                            .hasValueSatisfying(resp -> {
                                assertThat(resp.getPayload()).isEqualTo(testPayload);
                                assertThat(resp.getHeaders())
                                        .containsEntry("test-header", "abcd")
                                        .doesNotContainKeys("id", "timestamp", "target-protocol"); // Spring Cloud Stream adds these headers but the binder is expected to filter them out
                            });
                });


        verify(ackCallback, times(1)).onPublishSuccess();
    }

    /**
     * Test that the producer binding can handle a failed publish scenario.
     * <p>
     * An invalid destination is used to trigger a failure in the publish operation.
     * The test verifies:
     *   - that the PublishAcknowledgmentCallback is invoked with an error.
     *   - that the producer binding published to the error channel.
     */
    @Test
    void testProducerBindingFailedPublish(@Mock PublishAcknowledgmentCallback ackCallback) throws InterruptedException {

        String invalidDestination = "invalid-destination";

        String testPayload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
        Message<String> testMessage =
                MessageBuilder.withPayload(testPayload)
                        .setHeader(ConnectorBinderHeaders.PUBLISH_ACKNOWLEDGMENT_CALLBACK, ackCallback)
                        .setHeader(BinderHeaders.TARGET_DESTINATION, invalidDestination)
                        .build();

        boolean sent = streamBridge.send(outputBindingName, testMessage);
        assertTrue(sent, "Message should have been accepted by StreamBridge");

        verify(ackCallback, timeout(5000).times(1)).onPublishFailure(any());

        ErrorMessage errorMessage = application.readMessageReceivedOnErrorChannel(10, TimeUnit.SECONDS);
        assertThat(errorMessage).satisfies(errorMsg -> {
            assertThat(errorMsg).isNotNull();
            assertThat(errorMsg.getOriginalMessage().getPayload()).isEqualTo(testPayload);
        });
    }

    /**
     * Test that the producer binding can successfully push messages to a dynamic destination.
     * The presence of the scst_targetDestination header indicates that the message should be sent to a dynamic destination.
     */
    @Test
    void testDynamicDestinations(@Mock PublishAcknowledgmentCallback ackCallback) {
        String testPayload = "{\"payload\": \"%s\"}".formatted(UUID.randomUUID().toString());
        Message<String> testMessage =
                MessageBuilder.withPayload(testPayload)
                        .setHeader(ConnectorBinderHeaders.PUBLISH_ACKNOWLEDGMENT_CALLBACK, ackCallback)
                        .setHeader(BinderHeaders.TARGET_DESTINATION, DYNAMIC_DESTINATION)
                        .build();

        boolean sent = streamBridge.send(outputBindingName, testMessage);
        assertTrue(sent, "Message should have been accepted by StreamBridge");

        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<SimpleQueuingClient.MessageResponse> messageResponse = simpleQueuingClient.pollMessage(DYNAMIC_DESTINATION);
                    assertThat(messageResponse)
                            .isPresent()
                            .satisfies(resp -> assertThat(resp.get().getPayload()).isEqualTo(testPayload));
                });

        // Verify that no message was sent to the configured target destination
        assertThat(simpleQueuingClient.pollMessage(TARGET_DESTINATION)).isNotPresent();

        verify(ackCallback, times(1)).onPublishSuccess();
    }

    /**
     * Test that the default binding configuration works as expected.
     * <p>
     * This test verifies that the polling interval is set according to the configured binding default.
     */
    @Test
    void testDefaultBindingConfiguration(CapturedOutput capturedOutput) {
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(capturedOutput).contains("Polling interval: 1000 ms"));

    }

    /**
     * Test that the consumer binding correctly publishes to the error channel on exception.
     */
    @Test
    void testConsumerBindingPublishesToErrorChannelOnErrors() throws InterruptedException {
        String payload = UUID.randomUUID().toString();
        MessageHeaderAccessor accessor = new MessageHeaderAccessor();
        accessor.setHeader("cause_error", "true");
        SimpleQueuingClient.MessageResponse response = simpleQueuingClient.queueMessage(SOURCE_DESTINATION, new SimpleQueuingClient.MessageRequest(payload, accessor.getMessageHeaders()));

        // Validate the consumer binding sent the message to the error channel
        ErrorMessage errorMessage = application.readMessageReceivedOnErrorChannel(10, TimeUnit.SECONDS);
        assertThat(errorMessage).satisfies(errorMsg -> {
            assertThat(errorMsg).isNotNull();
            assertThat(errorMsg.getOriginalMessage().getPayload()).isEqualTo(payload);
        });

        // Validate the message was REQUEUed, which in our sample, means it was sent to the DLQ
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertThat(simpleQueuingClient.getRejectedMessages()).anyMatch(msg -> msg.getId().equals(response.getId()));
                });
    }
}
