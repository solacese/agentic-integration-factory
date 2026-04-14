package com.solace.samples.binder.simplequeuing;

import com.solace.samples.binder.simplequeuing.app.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("multibinder")
@AutoConfigureMockMvc
class MultiBinderTest extends AbstractSimpleQueuingTest {

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.stream.binders.simple-queuing-1.environment.simple-queuing.base-url",
                () -> simpleQueuingClient.getBaseUrl());
        registry.add("spring.cloud.stream.binders.simple-queuing-2.environment.simple-queuing.base-url",
                () -> simpleQueuingClient.getBaseUrl());
    }

    @Test
    void checkHealthWithMultipleBinders(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("components.binders.components.simple-queuing-1").exists(),
                        jsonPath("components.binders.components.simple-queuing-1.status").value("UP"),
                        jsonPath("components.binders.components.simple-queuing-2").exists(),
                        jsonPath("components.binders.components.simple-queuing-2.status").value("UP")
                );
    }
}
