package com.recallai;

import com.recallai.ai.FakeClaudeClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the SDK-backed Claude client so integration tests never need a key or network. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeAiConfiguration {

    /** Exposed as the concrete type so tests can script it; @Primary wins over the SDK client. */
    @Bean
    @Primary
    FakeClaudeClient fakeClaudeClient() {
        return new FakeClaudeClient();
    }
}
