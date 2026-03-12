package com.example.aidocassistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AiDocAssistantApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts without errors.
        // This catches missing beans, circular dependencies, and misconfiguration early.
    }
}
