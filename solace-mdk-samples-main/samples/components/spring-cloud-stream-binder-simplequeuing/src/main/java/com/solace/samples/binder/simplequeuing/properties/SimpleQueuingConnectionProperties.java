package com.solace.samples.binder.simplequeuing.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("simple-queuing")
@Validated
public class SimpleQueuingConnectionProperties {

    @NotNull
    @Pattern(regexp = "^http://[a-zA-Z0-9.-]+:\\d+$", message = "Base URL must be in the format 'http://hostname:port'")
    private String baseUrl;
    private int port;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
