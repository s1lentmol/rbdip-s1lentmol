package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
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
class CustomerNameMigrationTest {

    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void initializeDatabase() {
        migrateTo("2");
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ivan Petrov", "Иван Петров Сидоров", "Anna", "Anna  Petrov", "Anna ", " Anna"})
    void preservesExistingNamesAndCustomerIdentity(String name) {
        Long id = jdbc.queryForObject("INSERT INTO customers (full_name) VALUES (?) RETURNING id", Long.class, name);

        migrateTo("4");

        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE id = ?", String.class, id))
                .isEqualTo(name);
        assertThat(reconstructedName(id)).isEqualTo(name);

        migrateTo("5");

        assertThat(reconstructedName(id)).isEqualTo(name);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'customers' AND column_name = 'full_name'
                """, Integer.class)).isZero();
    }

    @Test
    void keepsOldAndNewWritersCompatibleUntilContract() {
        migrateTo("4");
        Long oldId = jdbc.queryForObject(
                "INSERT INTO customers (full_name) VALUES ('Ivan Petrov') RETURNING id", Long.class);
        Long newId = jdbc.queryForObject("""
                INSERT INTO customers (first_name, last_name) VALUES ('Anna', 'Sidorova') RETURNING id
                """, Long.class);

        assertThat(reconstructedName(oldId)).isEqualTo("Ivan Petrov");
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE id = ?", String.class, newId))
                .isEqualTo("Anna Sidorova");

        jdbc.update("UPDATE customers SET full_name = 'Ivan Sidorov' WHERE id = ?", oldId);
        jdbc.update("UPDATE customers SET last_name = 'Petrova' WHERE id = ?", newId);

        assertThat(reconstructedName(oldId)).isEqualTo("Ivan Sidorov");
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE id = ?", String.class, newId))
                .isEqualTo("Anna Petrova");
    }

    private String reconstructedName(Long id) {
        return jdbc.queryForObject("""
                SELECT first_name || CASE WHEN last_name = '' THEN '' ELSE ' ' || last_name END
                FROM customers WHERE id = ?
                """, String.class, id);
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(version).load().migrate();
    }
}
