package com.solace.samples.binder.simplequeuing.properties;

import org.springframework.cloud.stream.binder.BinderSpecificPropertiesProvider;

public class SimpleQueuingBindingProperties implements BinderSpecificPropertiesProvider {

    private SimpleQueuingConsumerProperties consumer = new SimpleQueuingConsumerProperties();

    private SimpleQueuingProducerProperties producer = new SimpleQueuingProducerProperties();

    @Override
    public Object getConsumer() {
        return consumer;
    }

    public void setConsumer(SimpleQueuingConsumerProperties consumer) {
        this.consumer = consumer;
    }

    @Override
    public Object getProducer() {
        return producer;
    }

    public void setProducer(SimpleQueuingProducerProperties producer) {
        this.producer = producer;
    }
}
