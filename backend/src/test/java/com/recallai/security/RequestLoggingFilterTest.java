package com.recallai.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recallai.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class RequestLoggingFilterTest extends AbstractIntegrationTest {

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        String generated = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestLoggingFilter.REQUEST_ID_HEADER))
                .andReturn().getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(generated).hasSize(36);
    }

    @Test
    void wellFormedIncomingRequestIdIsHonouredAndGarbageIsReplaced() throws Exception {
        String token = registerUser("trace@example.com");

        mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(RequestLoggingFilter.REQUEST_ID_HEADER, "trace-abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestLoggingFilter.REQUEST_ID_HEADER, "trace-abc-123"));

        String replaced = mockMvc.perform(get("/api/decks").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .header(RequestLoggingFilter.REQUEST_ID_HEADER, "<script>alert(1)</script>\r\nX: y"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertThat(replaced).hasSize(36).doesNotContain("<");
    }

    @Test
    void resolveRequestIdAcceptsOnlySafeValues() {
        assertThat(RequestLoggingFilter.resolveRequestId("abc12345")).isEqualTo("abc12345");
        assertThat(RequestLoggingFilter.resolveRequestId("short")).hasSize(36);
        assertThat(RequestLoggingFilter.resolveRequestId(null)).hasSize(36);
        assertThat(RequestLoggingFilter.resolveRequestId("a".repeat(65))).hasSize(36);
    }
}
