package com.recallai.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recallai.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class AiStatusTest extends AbstractIntegrationTest {

    @Test
    void statusReportsWhetherGenerationIsAvailableWithoutExposingConfiguration() throws Exception {
        String token = registerUser("status@example.com");

        // The test profile has no API key and demo mode off.
        mockMvc.perform(get("/api/ai/status").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.demoMode").value(false))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.apiKey").doesNotExist());

        mockMvc.perform(get("/api/ai/status")).andExpect(status().isUnauthorized());
    }
}
