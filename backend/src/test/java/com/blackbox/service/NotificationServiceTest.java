package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** mock 저장소 단위 테스트. 고유 제약·cursor 질의·롤백의 실제 동작은 NotificationDatabaseTest가 맡는다. */
class NotificationServiceTest {
    final UserNotificationRepository repo = mock(UserNotificationRepository.class);
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final NotificationService service = new NotificationService(repo, members, new ProjectAccessChecker(projects, members));
    final Project project = new Project();
    final User leader = user("팀장"), actor = user("현우"), peer = user("유나"), observer = user("관찰자"), outsider = user("외부");
    final Task task = new Task();

    @BeforeEach void setup() {
        project.setId(UUID.randomUUID());
        task.setId(UUID.randomUUID()); task.setProject(project); task.setTitle("현장 조사"); task.setCreatedBy(actor);
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(members.findByProject(project)).thenReturn(List.of(member(leader, "LEADER"), member(actor, "MEMBER"),
                member(peer, "MEMBER"), member(observer, "OBSERVER")));
    }

    @Test void uploadGoesToCurrentContributorsExceptActor() {
        FileVault file = new FileVault(); file.setId(UUID.randomUUID()); file.setFileName("조사.pdf"); file.setVersion(2);
        service.fileUploaded(task, file, actor);
        assertEquals(Set.of(leader.getId(), peer.getId()), recipients());
        verify(repo, times(2)).insertIgnoringDuplicate(eq(project.getId()), any(), eq(actor.getId()), eq("현우"),
                eq("TASK_FILE_UPLOADED"), eq("FILE:" + file.getId()), eq(task.getId()), eq("현장 조사"), isNull(),
                eq(file.getId()), eq("조사.pdf"), eq(2), any());
    }

    @Test void candidatesOutsideTeamObserverActorAndDuplicatesAreDropped() {
        task.setCompletionRequestedAt(OffsetDateTime.now());
        service.completionRequested(task, actor, List.of(peer, peer, observer, outsider, actor));
        assertEquals(Set.of(peer.getId()), recipients());
        verify(repo, times(1)).insertIgnoringDuplicate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void requestKeysDifferPerRequestTimeSoReRequestIsNewEvent() {
        OffsetDateTime first = OffsetDateTime.of(2026, 10, 15, 1, 0, 0, 123_456_000, ZoneOffset.UTC);
        task.setCompletionRequestedAt(first); service.completionRequested(task, actor, List.of(peer));
        task.setCompletionRequestedAt(first.plusMinutes(5)); service.completionRequested(task, actor, List.of(peer));
        var keys = new ArrayList<String>();
        verify(repo, times(2)).insertIgnoringDuplicate(any(), any(), any(), any(), any(), argThat(k -> keys.add(k)), any(), any(), any(), any(), any(), any(), any());
        assertEquals(2, new HashSet<>(keys).size());
    }

    @Test void replyToOwnRequestNotifiesNobody() {
        TaskMessage request = new TaskMessage(); request.setId(UUID.randomUUID()); request.setAuthor(actor);
        TaskMessage reply = new TaskMessage(); reply.setId(UUID.randomUUID()); reply.setTask(task); reply.setAuthor(actor); reply.setParent(request);
        service.opinionReplied(reply);
        verifyNoInteractions(repo);
    }

    @Test void cursorRoundTripsAndRejectsTampering() {
        UserNotification n = new UserNotification(); n.setId(UUID.randomUUID());
        n.setCreatedAt(OffsetDateTime.of(2026, 10, 15, 1, 2, 3, 456_789_000, ZoneOffset.UTC));
        var cursor = NotificationService.decode(NotificationService.encode(n));
        assertEquals(n.getId(), cursor.id());
        assertEquals(n.getCreatedAt().toInstant(), cursor.createdAt().toInstant());
        for (String bad : List.of("", "%%%", Base64.getUrlEncoder().encodeToString("abc_def".getBytes()), "MTIz"))
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.list(project.getId(), null, bad, false, leaderLogin())).getStatusCode().value());
    }

    @Test void limitOutsideOneToFiftyIs400() {
        for (int limit : new int[]{0, 51, -1})
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.list(project.getId(), limit, null, false, leaderLogin())).getStatusCode().value());
    }

    @Test void nonMemberIs403AndForeignNotificationIs404() {
        when(members.findByProjectAndUser(project, outsider)).thenReturn(Optional.empty());
        assertThrows(ForbiddenException.class, () -> service.list(project.getId(), null, null, false, outsider));
        UUID someone = UUID.randomUUID();
        when(repo.findByIdAndRecipientIdAndProjectId(someone, leader.getId(), project.getId())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.markRead(project.getId(), someone, leaderLogin()));
    }

    @Test void readKeepsFirstReadTime() {
        OffsetDateTime first = OffsetDateTime.of(2026, 10, 15, 0, 0, 0, 0, ZoneOffset.ofHours(9));
        UserNotification n = new UserNotification(); n.setId(UUID.randomUUID()); n.setReadAt(first);
        when(repo.findByIdAndRecipientIdAndProjectId(n.getId(), leader.getId(), project.getId())).thenReturn(Optional.of(n));
        var result = service.markRead(project.getId(), n.getId(), leaderLogin());
        assertEquals(first.toInstant(), result.readAt().toInstant());
        assertEquals(ZoneOffset.UTC, result.readAt().getOffset());
        verify(repo, never()).save(any());
    }

    private User leaderLogin() {
        when(members.findByProjectAndUser(project, leader)).thenReturn(Optional.of(member(leader, "LEADER")));
        return leader;
    }

    private Set<UUID> recipients() {
        var ids = new HashSet<UUID>();
        verify(repo, atLeast(0)).insertIgnoringDuplicate(any(), argThat(id -> ids.add(id)), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        return ids;
    }

    private static User user(String name) { User u = new User(); u.setId(UUID.randomUUID()); u.setName(name); return u; }
    private ProjectMember member(User u, String role) {
        ProjectMember m = new ProjectMember(); m.setProject(project); m.setUser(u); m.setRole(role); return m;
    }
}
