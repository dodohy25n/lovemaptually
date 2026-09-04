package com.lovemaptually.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "users",
            "relation_groups",
            "group_members",
            "invite_codes",
            "places",
            "reviews",
            "group_places",
            "tags",
            "review_tags",
            "place_tags",
            "user_tags",
            "unmatched_tag_logs",
            "place_similarity",
            "recommendation_requests",
            "recommendations",
            "monthly_reports",
            "subscriptions"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void migrationCreatesSeventeenDomainTablesAndApprovedTagStatus() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement()) {
            Set<String> tables = new HashSet<>();
            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                      AND table_name <> 'flyway_schema_history'
                    """)) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString(1));
                }
            }
            assertThat(tables).isEqualTo(EXPECTED_TABLES);

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT is_nullable, column_default
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'reviews'
                      AND column_name = 'tag_status'
                    """)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("is_nullable")).isEqualTo("NO");
                assertThat(resultSet.getString("column_default")).contains("PENDING");
            }
        }
    }
}
