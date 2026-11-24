package com.financialcoach.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialcoach.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** With the production setting the app starts with the Gemini classifier (it is never called here). */
@SpringBootTest(properties = "financial-coach.categorization.llm-enabled=true")
@Import(TestcontainersConfiguration.class)
class CategorizationConfigTest {

    @Autowired
    private MerchantClassifier classifier;

    @Test
    void usesTheLlmWhenEnabled() {
        assertThat(classifier).isInstanceOf(GeminiMerchantClassifier.class);
    }
}
