package com.solace.samples.binder.simplequeuing;

import com.solace.samples.binder.simplequeuing.inbound.SimpleQueuingConsumerDestination;
import com.solace.samples.binder.simplequeuing.inbound.SimpleQueuingInboundChannelAdapter;
import com.solace.samples.binder.simplequeuing.outbound.SimpleQueuingOutboundMessageHandler;
import com.solace.samples.binder.simplequeuing.outbound.SimpleQueuingProducerDestination;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingConnectionProperties;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingConsumerProperties;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingExtendedBindingProperties;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingProducerProperties;
import com.solace.samples.binder.simplequeuing.provisioning.SimpleQueuingBinderProvisioner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.stream.binder.AbstractMessageChannelBinder;
import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.ExtendedPropertiesBinder;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.integration.core.MessageProducer;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

/**
 * The core {@link org.springframework.cloud.stream.binder.Binder} implementation.
 * Creates producers and consumers for message channels to integrate with the Simple Queuing service.
 */
public class SimpleQueuingBinder extends AbstractMessageChannelBinder<ExtendedConsumerProperties<SimpleQueuingConsumerProperties>,
        ExtendedProducerProperties<SimpleQueuingProducerProperties>, SimpleQueuingBinderProvisioner>
        implements ExtendedPropertiesBinder<MessageChannel, SimpleQueuingConsumerProperties, SimpleQueuingProducerProperties> {

    private final SimpleQueuingConnectionProperties connectionProperties;
    private final SimpleQueuingExtendedBindingProperties extendedBindingProperties;
    private final RestTemplateBuilder restTemplateBuilder;

    /**
     * Create a new Simple Queuing binder.
     *
     * @param connectionProperties The Simple Queuing connection properties
     * @param extendedBindingProperties The extended binding properties
     * @param restTemplateBuilder Builder for creating RestTemplate instances
     */
    public SimpleQueuingBinder(SimpleQueuingConnectionProperties connectionProperties,
                               SimpleQueuingExtendedBindingProperties extendedBindingProperties,
                               RestTemplateBuilder restTemplateBuilder) {
        super(null, new SimpleQueuingBinderProvisioner());
        this.connectionProperties = connectionProperties;
        this.extendedBindingProperties = extendedBindingProperties;
        this.restTemplateBuilder = restTemplateBuilder;
    }

    @Override
    protected MessageHandler createProducerMessageHandler(ProducerDestination destination,
                                                         ExtendedProducerProperties<SimpleQueuingProducerProperties> producerProperties,
                                                         MessageChannel errorChannel) {
        return new SimpleQueuingOutboundMessageHandler((SimpleQueuingProducerDestination) destination, errorChannel, producerProperties, connectionProperties, restTemplateBuilder);
    }

    @Override
    protected MessageProducer createConsumerEndpoint(ConsumerDestination destination,
                                                    String group,
                                                    ExtendedConsumerProperties<SimpleQueuingConsumerProperties> consumerProperties) {
        SimpleQueuingConsumerDestination restDestination = (SimpleQueuingConsumerDestination) destination;
        SimpleQueuingInboundChannelAdapter channelAdapter = new SimpleQueuingInboundChannelAdapter(connectionProperties, restDestination, consumerProperties, restTemplateBuilder);

        ErrorInfrastructure errorInfrastructure = registerErrorInfrastructure(destination, group, consumerProperties);
        channelAdapter.setErrorChannel(errorInfrastructure.getErrorChannel());

        return channelAdapter;
    }

    @Override
    public SimpleQueuingConsumerProperties getExtendedConsumerProperties(String channelName) {
        return this.extendedBindingProperties.getExtendedConsumerProperties(channelName);
    }

    @Override
    public SimpleQueuingProducerProperties getExtendedProducerProperties(String channelName) {
        return this.extendedBindingProperties.getExtendedProducerProperties(channelName);
    }

    @Override
    public String getDefaultsPrefix() {
        return this.extendedBindingProperties.getDefaultsPrefix();
    }

    @Override
    public Class<? extends BinderSpecificPropertiesProvider> getExtendedPropertiesEntryClass() {
        return this.extendedBindingProperties.getExtendedPropertiesEntryClass();
    }

    @Override
    public String getBinderIdentity() {
        return "simple-queuing-" + super.getBinderIdentity();
    }
}
