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
 * The CategoryGroup enum and the category_groups table (V3, renamed in V7) must list the same values.
 * If a migration adds a group, this test fails until the enum is updated too (and vice versa).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CategoryGroupTableTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void enumMatchesCategoryGroupsTable() {
        List<String> table = jdbc.queryForList("SELECT name FROM category_groups", String.class);
        List<String> labels = Arrays.stream(CategoryGroup.values()).map(CategoryGroup::label).toList();

        assertThat(labels).containsExactlyInAnyOrderElementsOf(table);
    }
}
