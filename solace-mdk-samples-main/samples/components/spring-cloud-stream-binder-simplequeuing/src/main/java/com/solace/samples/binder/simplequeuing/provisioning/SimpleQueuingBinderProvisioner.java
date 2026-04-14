package com.solace.samples.binder.simplequeuing.provisioning;

import com.solace.samples.binder.simplequeuing.inbound.SimpleQueuingConsumerDestination;
import com.solace.samples.binder.simplequeuing.outbound.SimpleQueuingProducerDestination;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingConsumerProperties;
import com.solace.samples.binder.simplequeuing.properties.SimpleQueuingProducerProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.binder.ExtendedConsumerProperties;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.provisioning.ConsumerDestination;
import org.springframework.cloud.stream.provisioning.ProducerDestination;
import org.springframework.cloud.stream.provisioning.ProvisioningException;
import org.springframework.cloud.stream.provisioning.ProvisioningProvider;

/**
 * Provisioner for creating REST consumer and producer destinations.
 */
public class SimpleQueuingBinderProvisioner implements ProvisioningProvider<ExtendedConsumerProperties<SimpleQueuingConsumerProperties>,
        ExtendedProducerProperties<SimpleQueuingProducerProperties>> {

    private static final Logger logger = LoggerFactory.getLogger(SimpleQueuingBinderProvisioner.class);


    @Override
    public ProducerDestination provisionProducerDestination(String name,
                                                            ExtendedProducerProperties<SimpleQueuingProducerProperties> properties) throws ProvisioningException {
        logger.info("Creating producer destination: {}", name);

        return new SimpleQueuingProducerDestination(name);
    }

    @Override
    public ConsumerDestination provisionConsumerDestination(String name,
                                                            String group,
                                                            ExtendedConsumerProperties<SimpleQueuingConsumerProperties> properties) throws ProvisioningException {
        logger.info("Creating consumer destination: {} ", name);

        return new SimpleQueuingConsumerDestination(name);
    }

}
