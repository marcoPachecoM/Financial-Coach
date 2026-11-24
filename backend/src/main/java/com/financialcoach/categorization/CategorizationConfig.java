package com.financialcoach.categorization;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Picks the merchant classifier: the LLM, or none when it is disabled. */
@Configuration
@EnableConfigurationProperties(CategorizationProperties.class)
class CategorizationConfig {

    @Bean
    @ConditionalOnProperty(name = "financial-coach.categorization.llm-enabled", havingValue = "true")
    MerchantClassifier geminiMerchantClassifier(@Qualifier("google-genai") ChatClient chatClient,
                                                ObjectMapper objectMapper, CategorizationProperties properties) {
        return new GeminiMerchantClassifier(chatClient, objectMapper, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "financial-coach.categorization.llm-enabled", havingValue = "false",
            matchIfMissing = true)
    MerchantClassifier disabledMerchantClassifier() {
        return new DisabledMerchantClassifier();
    }
}
