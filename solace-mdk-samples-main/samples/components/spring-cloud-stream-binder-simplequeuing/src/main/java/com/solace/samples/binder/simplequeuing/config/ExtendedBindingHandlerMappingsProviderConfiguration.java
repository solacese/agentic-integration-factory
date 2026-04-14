package com.solace.samples.binder.simplequeuing.config;

import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.cloud.stream.config.BindingHandlerAdvise;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ExtendedBindingHandlerMappingsProviderConfiguration {

	@Bean
	public BindingHandlerAdvise.MappingsProvider simpleQueuingExtendedPropertiesDefaultMappingsProvider() {
		return () -> {
			Map<ConfigurationPropertyName, ConfigurationPropertyName> mappings = new HashMap<>();
			mappings.put(ConfigurationPropertyName.of("spring.cloud.stream.simple-queuing.bindings"),
					ConfigurationPropertyName.of("spring.cloud.stream.simple-queuing.default"));
			return mappings;
		};
	}
}
