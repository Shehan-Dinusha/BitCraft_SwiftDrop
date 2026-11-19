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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies V8, which removes the four demo couriers seeded by V2, on a fresh
 * install and on an existing database where V2 has already been applied.
 */
@Testcontainers(disabledWithoutDocker = true)
class DemoCourierCleanupMigrationIT {

    private static final List<String> DEMO_COURIERS = List.of(
            "11111111-1111-1111-1111-111111111111",
            "22222222-2222-2222-2222-222222222222",
            "33333333-3333-3333-3333-333333333333",
            "44444444-4444-4444-4444-444444444444");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void startFromEmptySchema() {
        flyway(null).clean();
    }

    @Test
    @DisplayName("Fresh install: V2 seeds the demo couriers and V8 removes all four")
    void freshInstallEndsWithoutDemoCouriers() throws SQLException {
        flyway(null).migrate();

        assertThat(migrationApplied("8")).as("V8 recorded as applied (later migrations may follow)").isTrue();
        assertThat(remainingDemoCouriers()).isEmpty();
    }

    @Test
    @DisplayName("Existing database: unreferenced demo couriers are removed; referenced and real data are kept")
    void existingDatabaseKeepsReferencedDemoCourierAndRealData() throws SQLException {
        flyway("7").migrate();
        assertThat(migrationApplied("7")).as("dev V7 (customer_id) already applied").isTrue();
        assertThat(remainingDemoCouriers()).as("V2 applied the demo seed").hasSize(4);

        UUID realCourier = UUID.randomUUID();
        UUID demoReferencedOrder = UUID.randomUUID();
        UUID realOrder = UUID.randomUUID();
        try (Connection connection = connection()) {
            execute(connection, "INSERT INTO courier_shifts (courier_id, courier_name, current_district) VALUES (?, 'Real Courier', 'COLOMBO')",
                    realCourier);
            execute(connection, "INSERT INTO dispatch_assignments (order_id, courier_id, status) VALUES (?, ?, 'ASSIGNED')",
                    demoReferencedOrder, UUID.fromString(DEMO_COURIERS.get(1)));
            execute(connection, "INSERT INTO dispatch_assignments (order_id, courier_id, status) VALUES (?, ?, 'ASSIGNED')",
                    realOrder, realCourier);
        }

        flyway(null).migrate();

        assertThat(migrationApplied("8")).as("V8 recorded as applied (later migrations may follow)").isTrue();
        assertThat(remainingDemoCouriers())
                .as("only the demo courier still referenced by an assignment is kept")
                .containsExactly(DEMO_COURIERS.get(1));
        assertThat(count("SELECT count(*) FROM courier_shifts WHERE courier_id = ?", realCourier)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM dispatch_assignments WHERE order_id = ? AND courier_id = ?",
                demoReferencedOrder, UUID.fromString(DEMO_COURIERS.get(1)))).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM dispatch_assignments WHERE order_id = ? AND courier_id = ?",
                realOrder, realCourier)).isEqualTo(1);
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

    private List<String> remainingDemoCouriers() throws SQLException {
        List<String> remaining = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT courier_id::text FROM courier_shifts WHERE courier_id::text = ANY (?) ORDER BY courier_id")) {
            statement.setArray(1, connection.createArrayOf("text", DEMO_COURIERS.toArray()));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    remaining.add(rows.getString(1));
                }
            }
        }
        return remaining;
    }

    private boolean migrationApplied(String version) throws SQLException {
        return count("SELECT count(*) FROM flyway_schema_history WHERE version = ? AND success", version) == 1;
    }

    private int count(String sql, Object... parameters) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = prepare(connection, sql, parameters);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private void execute(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, parameters)) {
            statement.executeUpdate();
        }
    }

    private PreparedStatement prepare(Connection connection, String sql, Object... parameters) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
        return statement;
    }
}
