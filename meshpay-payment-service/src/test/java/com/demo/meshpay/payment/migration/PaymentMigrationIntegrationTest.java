package com.demo.meshpay.payment.migration;

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
class PaymentMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String[] LOCATIONS = {
            "classpath:db/migration", "classpath:db/migration-v2", "classpath:db/migration-v3"
    };

    @Test
    void freshDatabaseGetsCompleteSchemaAndLifecycleConstraints() throws Exception {
        Flyway flyway = migrate("fresh_payment");
        assertEquals(4, sqlMigrations(flyway));

        try (Connection connection = connection("fresh_payment"); Statement statement = connection.createStatement()) {
            Set<String> tables;
            try (ResultSet rs = statement.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema='fresh_payment'")) {
                var found = new java.util.HashSet<String>();
                while (rs.next()) found.add(rs.getString(1));
                tables = found;
            }
            assertTrue(tables.containsAll(Set.of("transactions", "accounts", "outbox", "packet_attempts",
                    "payment_lifecycle_events", "processed_commands", "flyway_schema_history")));
            assertTrue(scalarBoolean(statement, "SELECT EXISTS (SELECT 1 FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='fresh_payment' AND t.relname='transactions' AND c.conname='transactions_status_check')"));
            assertTrue(scalarBoolean(statement, "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='fresh_payment' AND indexname='idx_transaction_sender_nonce')"));
        }
    }

    @Test
    void unversionedNonEmptyDatabaseIsBaselinedThenMigratedWithoutLosingRows() throws Exception {
        migrate("legacy_payment");
        try (Connection connection = connection("legacy_payment"); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO transactions(packet_hash,packet_id,sender_vpa,receiver_vpa,nonce,currency,amount,signed_at,settled_at,synchronized_at,bridge_node_id,hop_count,status) VALUES ('hash-existing','packet-existing','a@meshpay.local','b@meshpay.local','nonce-existing','USD',12.50,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'bridge',0,'SETTLED')");
            statement.execute("DROP TABLE legacy_payment.flyway_schema_history");
        }

        Flyway rebased = migrate("legacy_payment");
        assertEquals(4, sqlMigrations(rebased));
        try (Connection connection = connection("legacy_payment"); Statement statement = connection.createStatement()) {
            assertEquals(1, scalarLong(statement, "SELECT count(*) FROM transactions WHERE packet_id='packet-existing'"));
            statement.executeUpdate("UPDATE transactions SET settled_at=NULL WHERE packet_id='packet-existing'");
            assertEquals(1, scalarLong(statement, "SELECT count(*) FROM transactions WHERE packet_id='packet-existing' AND settled_at IS NULL"));
            assertTrue(scalarBoolean(statement, "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname='legacy_payment' AND indexname='idx_transaction_sender_nonce')"));
        }
    }

    private static Flyway migrate(String schema) {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations(LOCATIONS)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
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

    private static boolean scalarBoolean(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) { assertTrue(rs.next()); return rs.getBoolean(1); }
    }

    private static long scalarLong(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) { assertTrue(rs.next()); return rs.getLong(1); }
    }
}
