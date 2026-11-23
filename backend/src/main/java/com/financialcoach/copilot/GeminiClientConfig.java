package com.financialcoach.copilot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

/**
 * Gemini-only: replaces the {@link Client} that Spring AI auto-configures (it backs off when a
 * Client bean exists) to fail fast. By default the Google SDK retries 503/429 answers for a long
 * time, and spring.ai.retry.* does not reach those retries, so a busy model left the request
 * hanging for a minute. Delete this class when switching to another provider.
 */
@Configuration
class GeminiClientConfig {

    @Bean
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey, CopilotProperties properties) {
        HttpOptions httpOptions = HttpOptions.builder()
                .timeout(Math.toIntExact(properties.timeout().toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(properties.attempts()).build())
                .build();
        return Client.builder().apiKey(apiKey).httpOptions(httpOptions).build();
    }
}
