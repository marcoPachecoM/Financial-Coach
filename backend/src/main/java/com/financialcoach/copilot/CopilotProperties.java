package com.financialcoach.copilot;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("financial-coach.copilot")
record CopilotProperties(
        @DefaultValue("google-genai") String provider,
        /** How the model writes every amount; same symbol as the frontend, no currency name. */
        @DefaultValue("$1,234.56") String amountFormat,
        /** Time limit of each call to the LLM (a question with tools makes several calls). */
        @DefaultValue("20s") Duration timeout,
        /** Tries per LLM call, the first one included: 2 means one retry on 503/429. */
        @DefaultValue("2") int attempts,
        /** Messages of a conversation sent back to the model (user and assistant); more costs more tokens. */
        @DefaultValue("10") int memoryMessages,
        /** Conversations kept in memory; the least recently used is forgotten first. */
        @DefaultValue("1000") int memoryConversations,
        /** Chat messages per user and minute: stops scripts and accidental loops. */
        @DefaultValue("10") int requestsPerMinute,
        /** Chat messages per user and day (resets at local midnight): caps what the LLM can cost. */
        @DefaultValue("100") int dailyLimit) {
}
