package com.financialcoach.copilot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("financial-coach.copilot")
record CopilotProperties(
        @DefaultValue("google-genai") String provider,
        /** How the model writes every amount; same symbol as the frontend, no currency name. */
        @DefaultValue("$1,234.56") String amountFormat) {
}
