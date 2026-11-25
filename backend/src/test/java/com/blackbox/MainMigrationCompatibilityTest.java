package com.blackbox;

import tools.jackson.databind.ObjectMapper;
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
                .locations("classpath:db/migration")
                // 이름 규칙(V<번호>__<설명>.sql)을 어긴 파일은 Flyway가 조용히 건너뛴다. 건너뛰지 말고 실패하게 한다
                .validateMigrationNaming(true);
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
        // 최신 버전을 숫자로 적어 두지 않는다. Flyway가 읽은 마이그레이션 파일 중 가장 높은 버전까지 적용됐는지 본다
        var resolved = f.info().all();
        assertEquals(resolved[resolved.length - 1].getVersion(), f.info().current().getVersion());
        assertEquals(0, f.info().pending().length);
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

    /** V39(K-41 4장): 검토 요청 알림 종류와 이메일 발송 대기의 제약 */
    @Test void emailOutboxConstraintsAndReviewRequestedType() throws Exception {
        flyway(null).migrate();
        String actor = "11111111-1111-4111-8111-111111111111", recipient = "11111111-1111-4111-8111-222222222222",
                project = "22222222-2222-4222-8222-222222222222", task = "33333333-3333-4333-8333-333333333333",
                deliverable = "44444444-4444-4444-8444-444444444444", review = "55555555-5555-4555-8555-555555555555";
        try (var c = connection()) {
            execute(c, "INSERT INTO users(id,email,name,password_hash) VALUES ('" + actor + "','actor@example.invalid','요청자','dummy'),('" + recipient + "','recipient@example.invalid','받는 사람','dummy')");
            execute(c, "INSERT INTO projects(id,name,created_by) VALUES ('" + project + "','메일 검사','" + actor + "')");
            String notification = "INSERT INTO user_notifications(project_id,recipient_id,actor_id,actor_name,type,event_key,task_title,deliverable_id,review_id,created_at) VALUES ('"
                    + project + "','" + recipient + "','" + actor + "','요청자','%s','%s','최종 보고서','" + deliverable + "','" + review + "',now())";
            execute(c, notification.formatted("REVIEW_REQUESTED", "review-requested:" + review));
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, notification.formatted("REVIEW_UNKNOWN", "unknown"))).getSQLState());

            String outbox = "INSERT INTO notification_email_outbox(project_id,recipient_id,actor_id,actor_name,event_type,event_key,task_id,deliverable_id,review_id,target_title,available_at,created_at,updated_at) VALUES ('"
                    + project + "','%s','" + actor + "','요청자','%s','%s',%s,'대상',now(),now()-interval '1 hour',now()-interval '1 hour')";
            String taskIds = "'" + task + "',NULL,NULL", reviewIds = "NULL,'" + deliverable + "','" + review + "'";
            execute(c, outbox.formatted(recipient, "TASK_COMPLETION_REQUESTED", "completion:" + task, taskIds));
            assertEquals("PENDING|0|true", scalar(c, "SELECT status||'|'||attempt_count||'|'||(id IS NOT NULL) FROM notification_email_outbox"));
            execute(c, outbox.formatted(recipient, "REVIEW_REQUESTED", "review-requested:" + review, reviewIds));
            // 같은 사건·같은 받는 사람은 한 번만 예약한다
            assertEquals("23505", assertThrows(SQLException.class, () -> execute(c, outbox.formatted(recipient, "TASK_COMPLETION_REQUESTED", "completion:" + task, taskIds))).getSQLState());
            // 종류와 대상 id가 맞지 않거나, 자기 자신에게 보내는 대기는 막는다
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, outbox.formatted(recipient, "REVIEW_REQUESTED", "bad-shape", taskIds))).getSQLState());
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, outbox.formatted(actor, "TASK_COMPLETION_REQUESTED", "self", taskIds))).getSQLState());
            // 처리 중이면 가져간 시각이, 보냄이면 보낸 시각이 있어야 한다
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, "UPDATE notification_email_outbox SET status='PROCESSING'")).getSQLState());
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, "UPDATE notification_email_outbox SET status='SENT'")).getSQLState());
            execute(c, "UPDATE notification_email_outbox SET status='PROCESSING',claimed_at=now(),attempt_count=attempt_count+1 WHERE event_type='TASK_COMPLETION_REQUESTED'");
            assertEquals("t", scalar(c, "SELECT updated_at > created_at FROM notification_email_outbox WHERE event_type='TASK_COMPLETION_REQUESTED'"));
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, "UPDATE notification_email_outbox SET attempt_count=-1")).getSQLState());
        }
    }

    /** V40(K-41 0.5 9·10장): 기존 계정은 미확인으로 시작하고, 확인 토큰·수신 설정의 제약을 지킨다 */
    @Test void emailVerificationTokensAndNotificationPreferences() throws Exception {
        flyway("39").migrate();
        String member = "11111111-1111-4111-8111-111111111111";
        try (var c = connection()) {
            execute(c, "INSERT INTO users(id,email,name,password_hash) VALUES ('" + member + "','member@example.invalid','기존 회원','dummy')");
        }
        assertEquals(1, flyway("40").migrate().migrationsExecuted);   // V41이 생겨도 V40까지만 올려 본다
        try (var c = connection()) {
            assertNull(scalar(c, "SELECT email_verified_at FROM users"), "기존 계정은 미확인");
            String token = "INSERT INTO email_verification_tokens(user_id,email,token_hash,created_at,expires_at,used_at) VALUES ('"
                    + member + "','member@example.invalid','%s',now(),%s,%s)";
            execute(c, token.formatted("a".repeat(64), "now()+interval '15 minutes'", "NULL"));
            // 같은 해시는 두 번 없고, 만료는 발급보다 뒤이며, 사용 시각은 발급 이후다
            assertEquals("23505", assertThrows(SQLException.class, () -> execute(c, token.formatted("a".repeat(64), "now()+interval '15 minutes'", "NULL"))).getSQLState());
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, token.formatted("b".repeat(64), "now()", "NULL"))).getSQLState());
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, token.formatted("c".repeat(64), "now()+interval '15 minutes'", "now()-interval '1 minute'"))).getSQLState());

            // 수신 설정은 켜면 켠 시각이 있어야 하고, 바꾸면 updated_at이 갱신된다
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c,
                    "INSERT INTO email_notification_preferences(user_id,enabled,notice_version) VALUES ('" + member + "',true,'v1')")).getSQLState());
            execute(c, "INSERT INTO email_notification_preferences(user_id,enabled,notice_version,updated_at) VALUES ('" + member + "',false,'v1',now()-interval '1 hour')");
            execute(c, "UPDATE email_notification_preferences SET enabled=true, enabled_at=now()");
            assertEquals("t", scalar(c, "SELECT updated_at > now()-interval '1 minute' FROM email_notification_preferences"));
        }
    }

    /** V41(K-60): 기존 프로젝트는 보관되지 않은 상태로 시작하고, 보관 시각과 보관한 사람은 함께 있거나 함께 없다 */
    @Test void projectArchiveColumns() throws Exception {
        flyway("40").migrate();
        String leader = "11111111-1111-4111-8111-111111111111", project = "22222222-2222-4222-8222-222222222222";
        try (var c = connection()) {
            execute(c, "INSERT INTO users(id,email,name,password_hash) VALUES ('" + leader + "','archive@example.invalid','팀장','dummy')");
            execute(c, "INSERT INTO projects(id,name,created_by) VALUES ('" + project + "','기존 프로젝트','" + leader + "')");
        }
        assertEquals(1, flyway("41").migrate().migrationsExecuted);   // V42가 생겨도 V41까지만 올려 본다
        try (var c = connection()) {
            assertNull(scalar(c, "SELECT archived_at FROM projects"), "기존 프로젝트는 보관되지 않은 상태");
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, "UPDATE projects SET archived_at=now()")).getSQLState());
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, "UPDATE projects SET archived_by='" + leader + "'")).getSQLState());
            execute(c, "UPDATE projects SET archived_at=now(), archived_by='" + leader + "'");
            execute(c, "UPDATE projects SET archived_at=NULL, archived_by=NULL");
        }
    }
}
