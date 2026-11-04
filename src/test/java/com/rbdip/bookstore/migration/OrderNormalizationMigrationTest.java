package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OrderNormalizationMigrationTest {

    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void initializeLegacyDatabase() {
        migrateTo("1");
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        jdbc.update("""
                INSERT INTO orders (customer_full_name, customer_address, customer_phone)
                VALUES ('Anna', NULL, NULL), ('Anna', NULL, NULL), ('Anna', 'Nevsky 10', NULL)
                """);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_name, product_price, quantity)
                VALUES (1, 'Refactoring', 40.00, 2)
                """);
    }

    @Test
    void preservesExistingOrdersAndResolvesProductsByNameAndPrice() {
        jdbc.update("""
                INSERT INTO products (name, price) VALUES ('Refactoring', 35.00), ('Refactoring', 40.00)
                """);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_name, product_price, quantity)
                VALUES (2, 'Refactoring', 35.00, 1)
                """);

        migrateTo("2");

        var customerIds = jdbc.queryForList("SELECT customer_id FROM orders ORDER BY id", Long.class);
        assertThat(customerIds).hasSize(3);
        assertThat(customerIds.get(0)).isEqualTo(customerIds.get(1)).isNotEqualTo(customerIds.get(2));
        assertThat(jdbc.queryForList("""
                SELECT c.full_name FROM orders o JOIN customers c ON c.id = o.customer_id ORDER BY o.id
                """, String.class)).containsExactly("Anna", "Anna", "Anna");
        assertThat(jdbc.queryForList("""
                SELECT c.address FROM orders o JOIN customers c ON c.id = o.customer_id ORDER BY o.id
                """, String.class)).containsExactly(null, null, "Nevsky 10");
        assertThat(jdbc.queryForList("SELECT product_id FROM order_items ORDER BY order_id", Long.class))
                .containsExactly(2L, 1L);
        assertThat(jdbc.queryForList("SELECT quantity FROM order_items ORDER BY order_id", Integer.class))
                .containsExactly(2, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void rollsBackWhenTheOriginalProductCannotBeIdentified(int matches) {
        for (int i = 0; i < matches; i++) {
            jdbc.update("INSERT INTO products (name, price) VALUES ('Refactoring', 40.00)");
        }

        assertThatThrownBy(() -> migrateTo("2"))
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("Cannot identify a unique catalog product for order_items.id=1");

        assertThat(jdbc.queryForObject("SELECT product_name FROM order_items WHERE id = 1", String.class))
                .isEqualTo("Refactoring");
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.customers') IS NULL", Boolean.class))
                .isTrue();
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(version)
                .load()
                .migrate();
    }
}
