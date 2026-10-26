package com.blackbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 별도 실제 DB schema에서 빈 DB와 기존 main V25 업그레이드를 검사한다. */
class MainMigrationCompatibilityTest {
    private final String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:25432/bb_pr143_migration");
    private final String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "blackbox_test");
    private final String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "blackbox_test");
    private final String schema = "pr143_" + UUID.randomUUID().toString().replace("-", "");
    private Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration");
        if (target != null) config.target(MigrationVersion.fromVersion(target));
        return config.load();
    }
    private Connection connection() throws SQLException {
        var connection = DriverManager.getConnection(url, user, password);
        connection.setSchema(schema);
        return connection;
    }
    private void execute(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement()) { statement.execute(sql); }
    }
    private String scalar(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement(); var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next()); return rows.getString(1);
        }
    }
    private Map<?, ?> row(Connection c, String table) throws Exception {
        return new ObjectMapper().readValue(scalar(c, "SELECT to_jsonb(t)::text FROM " + table + " t"), Map.class);
    }
    @Test void emptyDatabaseMigratesAndRepeatedMigrationDoesNothing() throws Exception {
        var f = flyway(null); f.migrate(); f.validate();
        assertEquals("31.4", f.info().current().getVersion().toString());
        assertEquals(0, f.migrate().migrationsExecuted);
        try (var c = connection()) {
            assertEquals("1", scalar(c, "SELECT count(*) FROM flyway_schema_history WHERE version='25' AND script='V25__agent_proposals.sql'"));
            assertEquals("1", scalar(c, "SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name='agent_runs'"));
        }
    }
    @Test void mainV25DataSurvivesAndLegacyCompletionRemainsEditable() throws Exception {
        flyway("25").migrate();
        try (var c = connection()) {
            execute(c, "INSERT INTO users(id,email,name,password_hash) VALUES ('11111111-1111-4111-8111-111111111111','migration@example.invalid','합성 사용자','dummy')");
            execute(c, "INSERT INTO projects(id,name,created_by) VALUES ('22222222-2222-4222-8222-222222222222','이전 main','11111111-1111-4111-8111-111111111111')");
            execute(c, "INSERT INTO tasks(id,project_id,title,status,created_by,completed_at) VALUES ('33333333-3333-4333-8333-333333333333','22222222-2222-4222-8222-222222222222','이전 완료','DONE','11111111-1111-4111-8111-111111111111',now())");
            execute(c, "INSERT INTO file_vault(project_id,uploader_id,file_name,file_hash,file_size,storage_path) VALUES ('22222222-2222-4222-8222-222222222222','11111111-1111-4111-8111-111111111111','이전.pdf',repeat('a',64),12,'/synthetic/file')");
            execute(c, "INSERT INTO agent_runs(id,project_id,requested_by,skill) VALUES ('44444444-4444-4444-8444-444444444444','22222222-2222-4222-8222-222222222222','11111111-1111-4111-8111-111111111111','PLAN_FROM_BRIEF')");
            execute(c, "INSERT INTO agent_proposals(project_id,run_id,kind,title,rationale,content,original) VALUES ('22222222-2222-4222-8222-222222222222','44444444-4444-4444-8444-444444444444','DELIVERABLE_PLAN','이전 제안','근거','{}','{}')");
            var before = new LinkedHashMap<String, Map<?, ?>>();
            for (String table : List.of("users", "projects", "tasks", "file_vault", "agent_runs", "agent_proposals")) before.put(table, row(c, table));
            var f = flyway(null); f.migrate(); f.validate();
            for (var entry : before.entrySet()) {
                var after = row(c, entry.getKey());
                for (var field : entry.getValue().entrySet()) assertEquals(field.getValue(), after.get(field.getKey()), entry.getKey() + "." + field.getKey());
            }
            assertEquals(0, f.migrate().migrationsExecuted);
            execute(c, "UPDATE tasks SET title='이전 완료 제목 수정' WHERE id='33333333-3333-4333-8333-333333333333'");
            assertNull(scalar(c, "SELECT completion_approved_at FROM tasks"));
            execute(c, "UPDATE tasks SET status='IN_PROGRESS' WHERE id='33333333-3333-4333-8333-333333333333'");
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, "UPDATE tasks SET status='DONE' WHERE id='33333333-3333-4333-8333-333333333333'")).getSQLState());
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, "INSERT INTO tasks(project_id,title,status,created_by) VALUES ('22222222-2222-4222-8222-222222222222','우회 완료','DONE','11111111-1111-4111-8111-111111111111')")).getSQLState());
            execute(c, "UPDATE tasks SET status='DONE',completion_approved_at=now(),completion_approved_by='11111111-1111-4111-8111-111111111111' WHERE id='33333333-3333-4333-8333-333333333333'");
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, "UPDATE tasks SET completion_approved_at=NULL,completion_approved_by=NULL")).getSQLState());
        }
    }
}
