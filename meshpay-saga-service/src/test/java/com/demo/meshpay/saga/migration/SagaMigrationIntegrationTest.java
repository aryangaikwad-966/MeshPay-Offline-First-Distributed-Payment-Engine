package com.demo.meshpay.saga.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class SagaMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void freshAndUnversionedNonEmptySchemasMigrateAndPreserveSagaRows() throws Exception {
        Flyway flyway = migrate("saga_migration");
        assertEquals(1, sqlMigrations(flyway));
        try (Connection connection = connection("saga_migration"); Statement statement = connection.createStatement()) {
            var tables = new java.util.HashSet<String>();
            try (ResultSet rs = statement.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema='saga_migration'")) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            assertTrue(tables.containsAll(Set.of("saga_state", "saga_transitions", "processed_events", "saga_command_outbox", "flyway_schema_history")));
            statement.executeUpdate("INSERT INTO saga_state(saga_id,packet_hash,state,created_at) VALUES ('saga-existing','hash-existing','SAGA_COMPLETED',CURRENT_TIMESTAMP)");
            statement.execute("DROP TABLE saga_migration.flyway_schema_history");
        }

        Flyway rebased = migrate("saga_migration");
        assertEquals(1, sqlMigrations(rebased));
        try (Connection connection = connection("saga_migration"); Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM saga_state WHERE saga_id='saga-existing'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getLong(1));
            }
            assertTrue(exists(statement, "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='saga_migration' AND indexname='idx_saga_outbox_ready')"));
        }
    }

    private static Flyway migrate(String schema) {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true).baselineVersion("0").load();
        flyway.migrate();
        return flyway;
    }

    private static long sqlMigrations(Flyway flyway) {
        return java.util.Arrays.stream(flyway.info().applied())
                .filter(info -> "SQL".equals(info.getType().name())).count();
    }

    private static Connection connection(String schema) throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static boolean exists(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) { assertTrue(rs.next()); return rs.getBoolean(1); }
    }
}
