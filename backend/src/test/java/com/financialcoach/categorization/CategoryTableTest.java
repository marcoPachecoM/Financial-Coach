package com.financialcoach.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialcoach.TestcontainersConfiguration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The Category enum and the categories table (V3 migration) must list the same values.
 * If a migration adds a category, this test fails until the enum is updated too (and vice versa).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CategoryTableTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void enumMatchesCategoriesTable() {
        List<String> table = jdbc.queryForList("SELECT name FROM categories", String.class);
        List<String> labels = Arrays.stream(Category.values()).map(Category::label).toList();

        assertThat(labels).containsExactlyInAnyOrderElementsOf(table);
    }
}
