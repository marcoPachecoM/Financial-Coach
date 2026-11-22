package com.financialcoach.copilot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("financial-coach.copilot")
record CopilotProperties(@DefaultValue("google-genai") String provider) {
}
