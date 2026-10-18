package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.exception.*;
import com.blackbox.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskMessageServiceTest {
    final TaskRepository tasks=mock(TaskRepository.class);
    final TaskAssigneeRepository assignees=mock(TaskAssigneeRepository.class);
    final TaskMessageRepository messages=mock(TaskMessageRepository.class);
    final ProjectMemberRepository members=mock(ProjectMemberRepository.class);
    final ProjectRepository projects=mock(ProjectRepository.class);
    final TaskFileRepository files=mock(TaskFileRepository.class);
    final EntityManager em=mock(EntityManager.class);
    final TaskMessageService service=new TaskMessageService(tasks,assignees,messages,members,files,new ProjectAccessChecker(projects,members),em,mock(NotificationService.class));
    final Project project=new Project(); final Task task=new Task();
    final User author=user("수행자"), peer=user("확인자"), observer=user("관찰자");
    @BeforeEach void setup() {
        project.setId(UUID.randomUUID());task.setId(UUID.randomUUID());task.setTitle("비교표");task.setProject(project);task.setCreatedBy(author);task.setStatus("IN_PROGRESS");task.setUpdatedAt(OffsetDateTime.now());
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(tasks.findByIdAndProject(task.getId(),project)).thenReturn(Optional.of(task));
        var people=List.of(member(author,"MEMBER"),member(peer,"LEADER"),member(observer,"OBSERVER"));
        when(members.findByProject(project)).thenReturn(people);
        people.forEach(m->when(members.findByProjectAndUser(project,m.getUser())).thenReturn(Optional.of(m)));
        when(tasks.findByProjectOrderByCreatedAtDesc(project)).thenReturn(List.of(task));
    }
    @Test void opinionDoesNotChangeStatusOrFreezeTask() {
        var result=service.post(project.getId(),task.getId(),"방향 확인",peer.getId(),null,null,author);
        assertEquals("REQUEST",result.kind());assertEquals(peer.getId(),result.recipientId());
        assertEquals("IN_PROGRESS",task.getStatus());assertNull(task.getCompletionRequestedAt());
    }
    @Test void onlyRecipientReplyMarksAnsweredWithoutCompletingTask() {
        var request=request();when(messages.findByIdAndTask(request.getId(),task)).thenReturn(Optional.of(request));
        service.post(project.getId(),task.getId(),"추가 설명",null,request.getId(),null,author);assertNull(request.getAnsweredAt());
        var reply=service.post(project.getId(),task.getId(),"출처 추가",null,request.getId(),null,peer);
        assertEquals("REPLY",reply.kind());assertNotNull(request.getAnsweredAt());assertEquals("IN_PROGRESS",task.getStatus());
    }
    @Test void observerCannotWriteButCanReadInbox() {
        assertThrows(ForbiddenException.class,()->service.post(project.getId(),task.getId(),"댓글",null,null,null,observer));
        assertTrue(service.inbox(project.getId(),observer).received().isEmpty());verify(messages,never()).saveAndFlush(any());
    }
    @Test void nonMemberAndOtherProjectTaskAreRejected() {
        assertThrows(ForbiddenException.class,()->service.post(project.getId(),task.getId(),"댓글",null,null,null,user("외부")));
        assertThrows(NotFoundException.class,()->service.post(project.getId(),UUID.randomUUID(),"댓글",null,null,null,author));
    }
    @Test void selfObserverAndConflictingTargetsAreRejected() {
        assertThrows(ResponseStatusException.class,()->service.post(project.getId(),task.getId(),"요청",author.getId(),null,null,author));
        assertThrows(ResponseStatusException.class,()->service.post(project.getId(),task.getId(),"요청",observer.getId(),null,null,author));
        assertThrows(ResponseStatusException.class,()->service.post(project.getId(),task.getId(),"요청",peer.getId(),UUID.randomUUID(),null,author));
    }
    @Test void blankAndOversizedBodiesAreRejected() {
        assertThrows(ResponseStatusException.class,()->service.post(project.getId(),task.getId(),"  ",null,null,null,author));
        assertThrows(ResponseStatusException.class,()->service.post(project.getId(),task.getId(),"a".repeat(2001),null,null,null,author));
    }
    @Test void foreignParentAndFileCannotBeLinked() {
        assertThrows(NotFoundException.class,()->service.post(project.getId(),task.getId(),"답변",null,UUID.randomUUID(),null,author));
        assertThrows(NotFoundException.class,()->service.post(project.getId(),task.getId(),"댓글",null,null,UUID.randomUUID(),author));
    }
    @Test void completedTaskAllowsCommentsButNotNewRequests() {
        task.setStatus("DONE");
        assertEquals("COMMENT",service.post(project.getId(),task.getId(),"추가 기록",null,null,null,author).kind());
        assertThrows(ResponseStatusException.class,()->service.post(project.getId(),task.getId(),"새 요청",peer.getId(),null,null,author));
    }
    @Test void inboxSeparatesTargetedOpinionsAndIndependentCompletionReview() {
        var request=request();when(messages.findByProjectAndKindAndAnsweredAtIsNullOrderByCreatedAtDesc(project,"REQUEST")).thenReturn(List.of(request));
        task.setCompletionRequestedAt(OffsetDateTime.now());task.setCompletionRequestedBy(author);
        var assigned=new TaskAssignee();assigned.setTask(task);assigned.setUser(author);
        when(assignees.findByTaskIn(List.of(task))).thenReturn(List.of(assigned));
        assertEquals(2,service.inbox(project.getId(),peer).received().size());
        assertEquals(0,service.inbox(project.getId(),author).received().size());
        assertEquals(2,service.inbox(project.getId(),author).sent().size());
        assertEquals(0,service.inbox(project.getId(),observer).received().size());
    }
    @Test void returnedTaskAppearsForPerformerOnly() {
        task.setCompletionRejectionReason("출처 보완");
        assertEquals(1,service.inbox(project.getId(),author).revisions().size());
        assertTrue(service.inbox(project.getId(),peer).revisions().isEmpty());
    }
    private TaskMessage request() {
        TaskMessage m=new TaskMessage();m.setId(UUID.randomUUID());m.setProject(project);m.setTask(task);m.setAuthor(author);m.setRecipient(peer);m.setKind("REQUEST");m.setBody("의견 요청");m.setCreatedAt(OffsetDateTime.now());return m;
    }
    private ProjectMember member(User u,String role) {var m=new ProjectMember();m.setProject(project);m.setUser(u);m.setRole(role);return m;}
    private static User user(String name) {var u=new User();u.setId(UUID.randomUUID());u.setName(name);return u;}
}
