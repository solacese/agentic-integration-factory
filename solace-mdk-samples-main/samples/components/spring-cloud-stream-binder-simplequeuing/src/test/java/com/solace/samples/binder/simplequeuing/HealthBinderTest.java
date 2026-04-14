package com.solace.samples.binder.simplequeuing;

import com.solace.samples.binder.simplequeuing.app.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class HealthBinderTest extends AbstractSimpleQueuingTest {

    @Test
    void testHealth(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("components.binders.components.simple-queuing").exists(),
                        jsonPath("components.binders.components.simple-queuing.status").value("UP")
                );
    }
}
