package com.financialcoach.categorization;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code financial-coach.categorization.*}. The LLM call uses the Gemini client of the copilot,
 * so its timeout and retries are those of {@code financial-coach.copilot}.
 *
 * @param llmEnabled         false: merchants not in the caches get "Other" (tests, no API key)
 * @param batchSize          merchants per LLM call
 * @param maxCallsPerRequest LLM calls per import or form request; merchants beyond get "Other"
 */
@ConfigurationProperties("financial-coach.categorization")
public record CategorizationProperties(boolean llmEnabled, int batchSize, int maxCallsPerRequest) {
}
