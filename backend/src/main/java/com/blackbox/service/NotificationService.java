package com.blackbox.service;

import com.blackbox.dto.NotificationDtos;
import com.blackbox.entity.*;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.ProjectMemberRepository;
import com.blackbox.repository.UserNotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * K-54 개인 업무 알림. 처리할 일 목록은 기존 피드백함(/feedback)이 계속 맡고, 여기서는 "새 소식"만 사람별로 남긴다.
 * 발행 메서드는 호출한 업무 서비스의 트랜잭션 안에서 실행된다. 업무 변경이 롤백되면 알림도 남지 않고,
 * 커밋 전에는 다른 요청에서 보이지 않는다. 받는 사람 판정은 호출한 서비스가 넘긴 후보를 그대로 쓰되
 * 이 시점의 팀장·팀원이 아니거나 행위자 본인이면 뺀다.
 */
@Service
public class NotificationService {
    static final String FILE_UPLOADED = "TASK_FILE_UPLOADED";
    static final String COMPLETION_REQUESTED = "TASK_COMPLETION_REQUESTED";
    static final String REVISION_REQUESTED = "TASK_REVISION_REQUESTED";
    static final String OPINION_REQUESTED = "TASK_OPINION_REQUESTED";
    static final String OPINION_REPLIED = "TASK_OPINION_REPLIED";
    static final String ASSIGNED = "TASK_ASSIGNED";
    private static final int DEFAULT_LIMIT = 20, MAX_LIMIT = 50;

    private final UserNotificationRepository notifications;
    private final ProjectMemberRepository members;
    private final ProjectAccessChecker access;

    public NotificationService(UserNotificationRepository notifications, ProjectMemberRepository members,
                               ProjectAccessChecker access) {
        this.notifications = notifications; this.members = members; this.access = access;
    }

    // ── 발행 ──────────────────────────────────────────────────────────────

    /** 지금 업무를 쓸 수 있는 팀장·팀원. 관찰자는 새 업무 알림을 받지 않는다. */
    public List<User> contributors(Project project) {
        return members.findByProject(project).stream().filter(ProjectAccessChecker::canContribute)
                .map(ProjectMember::getUser).toList();
    }

    /**
     * 새로 추가된 담당자에게만 호출한다. 키에 DB의 배정 시각을 넣어 해제 후 재배정은 새 알림이 되고,
     * 같은 배정의 재처리는 한 건으로 남는다. 담당자 행이 이미 저장·flush된 뒤 호출해야 한다.
     */
    public void assigned(Task task, Collection<User> newAssignees, User actor) {
        for (User u : newAssignees) {
            notifications.assignedAtMicros(task.getId(), u.getId()).ifPresent(at ->
                    publish(ASSIGNED, "ASSIGNED:" + task.getId() + ":" + u.getId() + ":" + at, task, actor, null, null, List.of(u)));
        }
    }

    /** 파일 버전 id가 이벤트 키다. 새 버전 업로드마다 새 알림, 같은 버전 재처리는 한 건. */
    public void fileUploaded(Task task, FileVault file, User actor) {
        publish(FILE_UPLOADED, "FILE:" + file.getId(), task, actor, null, file, contributors(task.getProject()));
    }

    /** 요청 시각이 요청 한 번을 구분한다. 보완 후 다시 요청하면 시각이 달라져 새 알림이 된다. */
    public void completionRequested(Task task, User actor, Collection<User> reviewers) {
        publish(COMPLETION_REQUESTED, "COMPLETION:" + task.getId() + ":" + micros(task.getCompletionRequestedAt()),
                task, actor, null, null, reviewers);
    }

    /** 보완 결정은 대기 중인 요청 하나에 한 번만 가능하므로 그 요청 시각을 키로 쓴다. */
    public void revisionRequested(Task task, OffsetDateTime rejectedRequestAt, User actor, Collection<User> recipients) {
        publish(REVISION_REQUESTED, "REVISION:" + task.getId() + ":" + micros(rejectedRequestAt),
                task, actor, null, null, recipients);
    }

    public void opinionRequested(TaskMessage request) {
        publish(OPINION_REQUESTED, "OPINION:" + request.getId(), request.getTask(), request.getAuthor(),
                request.getId(), null, List.of(request.getRecipient()));
    }

    public void opinionReplied(TaskMessage reply) {
        publish(OPINION_REPLIED, "REPLY:" + reply.getId(), reply.getTask(), reply.getAuthor(),
                reply.getId(), null, List.of(reply.getParent().getAuthor()));
    }

    private void publish(String type, String eventKey, Task task, User actor, UUID messageId, FileVault file,
                         Collection<User> candidates) {
        Project project = task.getProject();
        Set<UUID> current = new HashSet<>();
        for (User u : contributors(project)) current.add(u.getId());
        Set<UUID> recipients = new LinkedHashSet<>();
        for (User u : candidates) {
            if (u != null && current.contains(u.getId()) && !u.getId().equals(actor.getId())) recipients.add(u.getId());
        }
        // 한 이벤트의 알림은 같은 시각을 공유한다. 목록 정렬은 id로 동률을 가른다.
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        for (UUID recipient : recipients) {
            notifications.insertIgnoringDuplicate(project.getId(), recipient, actor.getId(), actor.getName(), type,
                    eventKey, task.getId(), task.getTitle(), messageId,
                    file == null ? null : file.getId(), file == null ? null : file.getFileName(),
                    file == null ? null : file.getVersion(), now);
        }
    }

    // ── 조회·읽음 ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public NotificationDtos.Page list(UUID projectId, Integer limit, String cursor, Boolean unreadOnly, User user) {
        Project project = access.getProject(projectId); access.requireMember(project, user);
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) throw bad("limit은 1~50 사이로 지정하세요.");
        boolean unread = Boolean.TRUE.equals(unreadOnly);
        List<UserNotification> rows;
        if (cursor == null) {
            rows = notifications.firstPage(user.getId(), projectId, unread, size + 1);
        } else {
            Cursor position = decode(cursor);
            rows = notifications.pageAfter(user.getId(), projectId, unread, position.createdAt(), position.id(), size + 1);
        }
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);
        Set<UUID> taskIds = new HashSet<>();
        for (UserNotification n : rows) taskIds.add(n.getTaskId());
        Set<UUID> existing = taskIds.isEmpty() ? Set.of() : new HashSet<>(notifications.existingTaskIds(projectId, taskIds));
        List<NotificationDtos.Item> items = rows.stream().map(n -> item(n, existing.contains(n.getTaskId()))).toList();
        String next = hasMore ? encode(rows.get(rows.size() - 1)) : null;
        return new NotificationDtos.Page(items, next, unreadCount(projectId, user.getId()));
    }

    @Transactional(readOnly = true)
    public NotificationDtos.UnreadCount unread(UUID projectId, User user) {
        Project project = access.getProject(projectId); access.requireMember(project, user);
        return new NotificationDtos.UnreadCount(unreadCount(projectId, user.getId()));
    }

    /** 읽음은 받는 사람 본인의 상태라 관찰자도 할 수 있다. 다시 요청해도 처음 읽은 시각을 유지한다. */
    @Transactional
    public NotificationDtos.ReadResult markRead(UUID projectId, UUID notificationId, User user) {
        Project project = access.getProject(projectId); access.requireMember(project, user);
        // 남의 알림·다른 프로젝트 알림은 존재 여부를 알리지 않도록 없는 알림과 같은 404로 응답한다.
        UserNotification n = notifications.findByIdAndRecipientIdAndProjectId(notificationId, user.getId(), projectId)
                .orElseThrow(() -> new NotFoundException("알림을 찾을 수 없습니다."));
        if (n.getReadAt() != null) return new NotificationDtos.ReadResult(n.getId(), utc(n.getReadAt()));
        notifications.markReadIfUnread(n.getId(), OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        // 동시 요청이 먼저 채웠다면 그 시각을 돌려준다.
        UserNotification saved = notifications.findByIdAndRecipientIdAndProjectId(notificationId, user.getId(), projectId)
                .orElseThrow(() -> new NotFoundException("알림을 찾을 수 없습니다."));
        return new NotificationDtos.ReadResult(saved.getId(), utc(saved.getReadAt()));
    }

    private long unreadCount(UUID projectId, UUID userId) {
        return notifications.countByRecipientIdAndProjectIdAndReadAtIsNull(userId, projectId);
    }

    private NotificationDtos.Item item(UserNotification n, boolean taskExists) {
        return new NotificationDtos.Item(n.getId(), n.getType(), n.getProjectId(), n.getActorName(), n.getTaskId(),
                n.getTaskTitle(), n.getFileId(), n.getFileName(), n.getFileVersion(), n.getMessageId(),
                utc(n.getCreatedAt()), utc(n.getReadAt()), taskExists);
    }

    // ── cursor: 정렬 위치(created_at, id)만 담는다. 권한 판정에는 쓰지 않는다 ───────

    record Cursor(OffsetDateTime createdAt, UUID id) {}

    static String encode(UserNotification last) {
        String raw = micros(last.getCreatedAt()) + "_" + last.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int split = raw.indexOf('_');
            long value = Long.parseLong(raw.substring(0, split));
            Instant at = Instant.ofEpochSecond(Math.floorDiv(value, 1_000_000L), Math.floorMod(value, 1_000_000L) * 1_000L);
            return new Cursor(at.atOffset(ZoneOffset.UTC), UUID.fromString(raw.substring(split + 1)));
        } catch (RuntimeException e) {
            throw bad("올바르지 않은 cursor입니다.");
        }
    }

    static long micros(OffsetDateTime at) {
        Instant i = at.toInstant();
        return Math.addExact(Math.multiplyExact(i.getEpochSecond(), 1_000_000L), i.getNano() / 1_000L);
    }

    private static OffsetDateTime utc(OffsetDateTime at) { return at == null ? null : at.withOffsetSameInstant(ZoneOffset.UTC); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
