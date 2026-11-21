package com.financialcoach.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialcoach.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CategorizationServiceTest {

    @Autowired
    private CategorizationService categorization;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedCache() {
        jdbc.update("""
                INSERT INTO merchant_category_cache (merchant_name, category, source)
                VALUES ('la tiendita', 'Food', 'LLM'), ('oxxo centro', 'Snacks', 'RULE')
                """);
    }

    @Test
    void usesTheCachedCategory() {
        assertThat(categorization.categorize("La Tiendita"))
                .isEqualTo(new CategoryAssignment(Category.FOOD, CategorySource.LLM));
    }

    @Test
    void normalizesBeforeLookingUp() {
        assertThat(categorization.categorize("  OXXO #8812 - Centro "))
                .isEqualTo(new CategoryAssignment(Category.SNACKS, CategorySource.RULE));
    }

    @Test
    void fallsBackToTheDefaultCategory() {
        assertThat(categorization.categorize("Unknown merchant"))
                .isEqualTo(new CategoryAssignment(Category.OTHER, CategorySource.RULE));
    }

    @Test
    void namesWithoutLettersGetTheDefaultCategory() {
        assertThat(categorization.categorize("12345"))
                .isEqualTo(new CategoryAssignment(Category.OTHER, CategorySource.RULE));
    }
}
