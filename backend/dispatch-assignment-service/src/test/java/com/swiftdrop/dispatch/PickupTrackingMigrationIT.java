package com.swiftdrop.dispatch;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies V8, which adds the nullable picked_up_at column used to block post-pickup reassignment. */
@Testcontainers(disabledWithoutDocker = true)
class PickupTrackingMigrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void startFromEmptySchema() {
        flyway(null).clean();
    }

    @Test
    @DisplayName("Fresh install: V8 adds picked_up_at as a nullable timestamptz column")
    void freshInstallAddsNullablePickedUpAt() throws SQLException {
        flyway(null).migrate();

        assertThat(migrationApplied("8")).isTrue();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT data_type, is_nullable, column_default FROM information_schema.columns
                     WHERE table_name = 'dispatch_assignments' AND column_name = 'picked_up_at'
                     """);
             ResultSet column = statement.executeQuery()) {
            assertThat(column.next()).as("picked_up_at exists").isTrue();
            assertThat(column.getString("data_type")).isEqualTo("timestamp with time zone");
            assertThat(column.getString("is_nullable")).isEqualTo("YES");
            assertThat(column.getString("column_default")).isNull();
        }
    }

    @Test
    @DisplayName("Existing database: V8 keeps assignments intact and leaves picked_up_at NULL (no backfill)")
    void existingAssignmentsKeepTheirDataWithNullPickup() throws SQLException {
        flyway("7").migrate();
        UUID orderId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        try (Connection connection = connection();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO dispatch_assignments (order_id, courier_id, status) VALUES (?, ?, 'ASSIGNED')")) {
            insert.setObject(1, orderId);
            insert.setObject(2, courierId);
            insert.executeUpdate();
        }

        flyway(null).migrate();

        assertThat(migrationApplied("8")).isTrue();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT courier_id, status, version, picked_up_at FROM dispatch_assignments WHERE order_id = ?")) {
            statement.setObject(1, orderId);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject("courier_id", UUID.class)).isEqualTo(courierId);
                assertThat(row.getString("status")).isEqualTo("ASSIGNED");
                assertThat(row.getLong("version")).isZero();
                assertThat(row.getObject("picked_up_at")).isNull();
            }
        }
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private boolean migrationApplied(String version) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM flyway_schema_history WHERE version = ? AND success")) {
            statement.setString(1, version);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1) == 1;
            }
        }
    }
}
