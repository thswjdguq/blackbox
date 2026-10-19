package com.blackbox.service;

import com.blackbox.dto.FileUploadResponse;
import com.blackbox.dto.NotificationDtos;
import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.UserNotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * 실제 PostgreSQL(V30 포함 전체 migration)에서 업무 서비스 → 알림 저장 → 조회·읽음을 이어서 검사한다.
 * 파일 저장소·점수·외부 알림만 mock이고, 권한 판정과 업무 상태 전환은 실제 서비스를 쓴다.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({NotificationService.class, ProjectAccessChecker.class, TaskWorkflowService.class, TaskMessageService.class,
        TaskFileService.class, JacksonAutoConfiguration.class})
class NotificationDatabaseTest {
    @Autowired EntityManager em;
    @Autowired NotificationService notifications;
    @Autowired UserNotificationRepository repo;
    @Autowired TaskWorkflowService workflow;
    @Autowired TaskMessageService messages;
    @Autowired TaskFileService taskFiles;
    @Autowired PlatformTransactionManager txManager;
    @Autowired DataSource dataSource;
    @MockBean ActivityLogService activity;
    @MockBean AlertService alerts;
    @MockBean DiscordNotificationService discord;
    @MockBean ScoreService scores;
    @MockBean FileVaultService storage;
    @MockBean ReviewRoundService reviews;

    /** 팀장 민지, 팀원 현우(담당)·유나·지호, 관찰자, 다른 프로젝트 사용자. */
    class Team {
        final User leader = user("민지"), worker = user("현우"), peer = user("유나"), peer2 = user("지호"),
                observer = user("관찰자"), outsider = user("외부");
        final Project project = project("엑스포 팀", leader), other = project("다른 팀", outsider);
        final Task task;
        Team() {
            member(project, leader, "LEADER"); member(project, worker, "MEMBER"); member(project, peer, "MEMBER");
            member(project, peer2, "MEMBER"); member(project, observer, "OBSERVER"); member(other, outsider, "LEADER");
            task = new Task(); task.setProject(project); task.setCreatedBy(leader); task.setTitle("현장 조사 정리");
            task.setStatus("IN_PROGRESS"); task.setPriority("MEDIUM"); task.setCompletionCriteria("사진 포함 정리본");
            em.persist(task);
            TaskAssignee a = new TaskAssignee(); a.setTask(task); a.setUser(worker); em.persist(a);
            em.flush();
        }
        UUID pid() { return project.getId(); }
    }

    @Test void uploadNotifiesContributorsExceptUploaderAndReadIsPerUser() throws Exception {
        Team t = new Team();
        FileVault v = upload(t, t.worker, "현장조사.pdf");

        assertEquals(1, count(t, t.leader)); assertEquals(1, count(t, t.peer)); assertEquals(1, count(t, t.peer2));
        assertEquals(0, count(t, t.worker), "본인 제외");
        assertEquals(0, count(t, t.observer), "관찰자 제외");

        NotificationDtos.Item item = notifications.list(t.pid(), null, null, false, t.leader).items().get(0);
        assertEquals("TASK_FILE_UPLOADED", item.type());
        assertEquals("현우", item.actorName());
        assertEquals(v.getId(), item.fileId());
        assertEquals(1, item.fileVersion());
        assertNull(item.messageId());
        assertTrue(item.targetAvailable());
        assertEquals(ZoneOffset.UTC, item.createdAt().getOffset());

        var read = notifications.markRead(t.pid(), item.id(), t.leader);
        var again = notifications.markRead(t.pid(), item.id(), t.leader);
        assertEquals(read.readAt(), again.readAt(), "재요청은 처음 읽은 시각 유지");
        assertEquals(0, notifications.unread(t.pid(), t.leader).unreadCount());
        assertEquals(1, notifications.unread(t.pid(), t.peer).unreadCount(), "다른 팀원의 읽음 상태는 그대로");

        // 같은 파일 버전 이벤트를 다시 처리해도 사람마다 한 건이다.
        notifications.fileUploaded(t.task, v, t.worker);
        assertEquals(1, count(t, t.peer));
        // 새 버전은 새 알림이다.
        upload(t, t.worker, "현장조사.pdf");
        assertEquals(2, count(t, t.peer));
    }

    @Test void completionRequestRevisionAndReRequestFlow() {
        Team t = new Team();
        workflow.request(t.pid(), t.task.getId(), t.worker);
        // 확인 가능한 사람 = 담당자도 요청자도 아닌 팀장·팀원
        assertEquals(1, count(t, t.leader, "TASK_COMPLETION_REQUESTED"));
        assertEquals(1, count(t, t.peer, "TASK_COMPLETION_REQUESTED"));
        assertEquals(0, count(t, t.worker, "TASK_COMPLETION_REQUESTED"));
        assertEquals(0, count(t, t.observer, "TASK_COMPLETION_REQUESTED"));

        // 이미 대기 중인 요청을 다시 눌러도 새 알림이 생기지 않는다.
        workflow.request(t.pid(), t.task.getId(), t.worker);
        assertEquals(1, count(t, t.peer, "TASK_COMPLETION_REQUESTED"));

        workflow.decide(t.pid(), t.task.getId(), false, false, "사진 출처를 추가해 주세요", t.peer);
        assertEquals(1, count(t, t.worker, "TASK_REVISION_REQUESTED"), "수행자이자 요청자에게 한 건");
        assertEquals(0, count(t, t.peer, "TASK_REVISION_REQUESTED"), "결정자 본인 제외");
        assertEquals(0, count(t, t.leader, "TASK_REVISION_REQUESTED"));

        // 보완 후 다시 요청하면 새 요청 알림이 생긴다.
        workflow.request(t.pid(), t.task.getId(), t.worker);
        assertEquals(2, count(t, t.peer, "TASK_COMPLETION_REQUESTED"));
    }

    @Test void failedRequestCreatesNothing() {
        Team t = new Team();
        // 관찰자는 완료 요청을 할 수 없다. 거절된 요청에서는 알림이 생기지 않는다.
        assertThrows(ForbiddenException.class, () -> workflow.request(t.pid(), t.task.getId(), t.observer));
        // 담당자가 아닌 팀원도 요청할 수 없다.
        assertThrows(ForbiddenException.class, () -> workflow.request(t.pid(), t.task.getId(), t.peer));
        // 보완 사유 없이 결정하면 400.
        workflow.request(t.pid(), t.task.getId(), t.worker);
        assertThrows(ResponseStatusException.class, () -> workflow.decide(t.pid(), t.task.getId(), false, false, " ", t.peer));
        assertEquals(0, count(t, t.worker, "TASK_REVISION_REQUESTED"));
        assertEquals(3, repo.findAll().stream().filter(n -> n.getProjectId().equals(t.pid())).count(), "확인 요청 알림 3건(팀장·유나·지호)만");
    }

    @Test void opinionRequestAndReplies() {
        Team t = new Team();
        var request = messages.post(t.pid(), t.task.getId(), "비교표 확인 부탁해요", t.peer.getId(), null, null, t.worker);
        assertEquals(1, count(t, t.peer, "TASK_OPINION_REQUESTED"));
        assertEquals(0, count(t, t.leader, "TASK_OPINION_REQUESTED"), "지정 수신자만");
        assertEquals(request.id(), notifications.list(t.pid(), null, null, false, t.peer).items().get(0).messageId());

        messages.post(t.pid(), t.task.getId(), "출처 하나만 보완하면 됩니다", null, request.id(), null, t.peer);
        assertEquals(1, count(t, t.worker, "TASK_OPINION_REPLIED"));
        // 다른 팀원의 답글도 원 요청자에게 간다. 요청자 본인의 답글은 알리지 않는다.
        messages.post(t.pid(), t.task.getId(), "저도 봤어요", null, request.id(), null, t.leader);
        messages.post(t.pid(), t.task.getId(), "반영할게요", null, request.id(), null, t.worker);
        assertEquals(2, count(t, t.worker, "TASK_OPINION_REPLIED"));
        assertEquals(0, count(t, t.peer, "TASK_OPINION_REPLIED"));
        // 일반 댓글도 본인·관찰자를 제외한 팀원 3명에게 알린다(EXPO-06).
        messages.post(t.pid(), t.task.getId(), "메모", null, null, null, t.leader);
        assertEquals(6, repo.findAll().stream().filter(n -> n.getProjectId().equals(t.pid())).count());
        assertEquals(0, count(t,t.observer));
    }

    @Test void projectBoundaryAndMembership() throws Exception {
        Team t = new Team();
        upload(t, t.worker, "a.pdf");
        UUID leaderNotification = notifications.list(t.pid(), null, null, false, t.leader).items().get(0).id();
        // 남의 알림, 다른 프로젝트 경로로 읽기 → 404
        assertThrows(NotFoundException.class, () -> notifications.markRead(t.pid(), leaderNotification, t.peer));
        assertThrows(ForbiddenException.class, () -> notifications.markRead(t.other.getId(), leaderNotification, t.leader));
        member(t.other, t.leader, "MEMBER");
        assertThrows(NotFoundException.class, () -> notifications.markRead(t.other.getId(), leaderNotification, t.leader));
        assertTrue(notifications.list(t.other.getId(), null, null, false, t.leader).items().isEmpty());
        // 프로젝트 비구성원은 403
        assertThrows(ForbiddenException.class, () -> notifications.list(t.pid(), null, null, false, t.outsider));
        // 팀에서 빠진 사용자는 남은 알림도 볼 수 없다.
        em.createQuery("DELETE FROM ProjectMember m WHERE m.project = :p AND m.user = :u")
                .setParameter("p", t.project).setParameter("u", t.peer).executeUpdate();
        assertThrows(ForbiddenException.class, () -> notifications.list(t.pid(), null, null, false, t.peer));
        // 관찰자는 자기 목록 조회 가능(비어 있음)
        assertTrue(notifications.list(t.pid(), null, null, false, t.observer).items().isEmpty());
    }

    @Test void cursorPagesWithoutGapsOrDuplicatesOnTiedTimestamps() {
        Team t = new Team();
        OffsetDateTime same = OffsetDateTime.of(2026, 10, 15, 1, 0, 0, 0, ZoneOffset.UTC);
        for (int i = 0; i < 7; i++)
            repo.insertIgnoringDuplicate(t.pid(), t.leader.getId(), t.worker.getId(), "현우", "TASK_FILE_UPLOADED",
                    "TEST:" + i, t.task.getId(), "현장 조사 정리", null, null, null, null, i < 5 ? same : same.minusSeconds(i));
        List<UUID> seen = new ArrayList<>(); String cursor = null; int pages = 0;
        do {
            var page = notifications.list(t.pid(), 2, cursor, false, t.leader);
            page.items().forEach(i -> seen.add(i.id()));
            assertEquals(7, page.unreadCount(), "unreadCount는 페이지와 무관");
            cursor = page.nextCursor(); pages++;
        } while (cursor != null);
        assertEquals(4, pages);
        assertEquals(7, seen.size()); assertEquals(7, new HashSet<>(seen).size());
        // PostgreSQL uuid 정렬은 부호 없는 바이트 순서다. Java UUID.compareTo(부호 있는 long)와 달라 문자열로 비교한다.
        var expected = repo.findAll().stream().filter(n -> n.getRecipientId().equals(t.leader.getId()) && n.getProjectId().equals(t.pid()))
                .sorted(Comparator.comparing(UserNotification::getCreatedAt).reversed()
                        .thenComparing(n -> n.getId().toString(), Comparator.reverseOrder()))
                .map(UserNotification::getId).toList();
        assertEquals(expected, seen);

        var unread = notifications.list(t.pid(), 50, null, true, t.leader);
        notifications.markRead(t.pid(), unread.items().get(0).id(), t.leader);
        assertEquals(6, notifications.list(t.pid(), 50, null, true, t.leader).items().size());
        assertTrue(notifications.list(t.pid(), 50, null, false, t.peer).items().isEmpty());
        assertNull(notifications.list(t.pid(), 50, null, false, t.peer).nextCursor());
    }

    @Test void deletedTaskKeepsNotificationAsUnavailable() throws Exception {
        Team t = new Team();
        upload(t, t.worker, "a.pdf");
        em.createNativeQuery("DELETE FROM task_files WHERE task_id = :id").setParameter("id", t.task.getId()).executeUpdate();
        em.createNativeQuery("DELETE FROM task_assignees WHERE task_id = :id").setParameter("id", t.task.getId()).executeUpdate();
        em.createNativeQuery("DELETE FROM tasks WHERE id = :id").setParameter("id", t.task.getId()).executeUpdate();
        em.clear();
        var item = notifications.list(t.pid(), null, null, false, t.leader).items().get(0);
        assertEquals(t.task.getId(), item.taskId());
        assertEquals("현장 조사 정리", item.taskTitle());
        assertFalse(item.targetAvailable());
    }

    /** 업무 트랜잭션이 롤백되면 알림도 없고, 커밋 전에는 다른 연결에서 보이지 않는다. */
    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void notificationsFollowTheBusinessTransaction() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Team t = tx.execute(s -> new Team());
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            uploadUnchecked(t, t.worker, "rollback.pdf");
            assertEquals(3, countViaOtherConnection(t.pid(), null), "같은 트랜잭션 안에서는 저장됨");
            assertEquals(0, countViaOtherConnection(t.pid(), t.peer.getId()), "커밋 전 다른 연결에는 보이지 않음");
            throw new IllegalStateException("업무 처리 후반 실패");
        }));
        assertEquals(0, countViaOtherConnection(t.pid(), t.peer.getId()), "롤백 후 알림 없음");
        tx.executeWithoutResult(s -> uploadUnchecked(t, t.worker, "commit.pdf"));
        assertEquals(1, countViaOtherConnection(t.pid(), t.peer.getId()), "커밋 후 보임");
    }

    // ── fixture ───────────────────────────────────────────────────────────

    private final Map<String, Integer> versions = new HashMap<>();

    private FileVault upload(Team t, User by, String name) throws Exception {
        FileVault v = new FileVault(); v.setProject(t.project); v.setUploader(by); v.setFileName(name);
        v.setFileHash("0".repeat(64)); v.setFileSize(4L); v.setStoragePath("test-only/" + UUID.randomUUID()); v.setUploadedAt(OffsetDateTime.now());
        v.setVersion(versions.merge(t.pid() + name, 1, Integer::sum));
        em.persist(v); em.flush();
        MockMultipartFile file = new MockMultipartFile("file", name, "application/pdf", new byte[]{1, 2, 3, 4});
        when(storage.upload(eq(t.pid()), any(), any())).thenReturn(
                new FileUploadResponse(v.getId(), t.pid(), name, null, 4L, v.getVersion(), false, null, null));
        taskFiles.upload(t.pid(), t.task.getId(), file, by);
        return v;
    }

    private void uploadUnchecked(Team t, User by, String name) {
        try { upload(t, by, name); } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** recipient가 있으면 별도 JDBC 연결로 센다(커밋된 행만 보임). null이면 현재 트랜잭션 안에서 프로젝트 전체 수. */
    private long countViaOtherConnection(UUID projectId, UUID recipient) {
        if (recipient == null) return repo.findAll().stream().filter(n -> n.getProjectId().equals(projectId)).count();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM user_notifications WHERE project_id = ? AND recipient_id = ?")) {
            ps.setObject(1, projectId); ps.setObject(2, recipient);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private long count(Team t, User u) { return count(t, u, null); }
    private long count(Team t, User u, String type) {
        em.flush();
        return repo.findAll().stream().filter(n -> n.getProjectId().equals(t.pid()) && n.getRecipientId().equals(u.getId())
                && (type == null || type.equals(n.getType()))).count();
    }

    private User user(String name) {
        User u = new User(); u.setName(name); u.setEmail(UUID.randomUUID() + "@b31.example.invalid");
        u.setPasswordHash("test-only-not-used-for-authentication"); u.setRole("STUDENT"); em.persist(u); return u;
    }
    private Project project(String name, User by) {
        Project p = new Project(); p.setName(name); p.setCreatedBy(by); em.persist(p); return p;
    }
    private void member(Project p, User u, String role) {
        ProjectMember m = new ProjectMember(); m.setProject(p); m.setUser(u); m.setRole(role); em.persist(m); em.flush();
    }
}
