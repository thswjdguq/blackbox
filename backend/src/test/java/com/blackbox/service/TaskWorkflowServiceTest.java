package com.blackbox.service;

import com.blackbox.dto.*;
import com.blackbox.entity.*;
import com.blackbox.exception.*;
import com.blackbox.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskWorkflowServiceTest {
    final TaskRepository tasks=mock(TaskRepository.class);
    final TaskAssigneeRepository assignees=mock(TaskAssigneeRepository.class);
    final TaskFileRepository files=mock(TaskFileRepository.class);
    final ProjectRepository projects=mock(ProjectRepository.class);
    final ProjectMemberRepository members=mock(ProjectMemberRepository.class);
    final ActivityLogService logs=mock(ActivityLogService.class);
    final EntityManager em=mock(EntityManager.class);
    final ProjectAccessChecker access=new ProjectAccessChecker(projects,members);
    final NotificationService notifications=mock(NotificationService.class);
    final TaskWorkflowService workflow=new TaskWorkflowService(tasks,assignees,files,access,logs,mock(AlertService.class),mock(DiscordNotificationService.class),mock(ScoreService.class),em,new ObjectMapper(),notifications);
    final TaskService service=new TaskService(tasks,assignees,members,mock(UserRepository.class),access,logs,mock(NotionService.class),mock(AlertService.class),mock(DiscordNotificationService.class),mock(ScoreService.class),em,mock(DeliverableService.class),mock(ReviewCommentRepository.class),workflow,mock(NotificationService.class));
    final Project project=new Project(); final User user=new User(); final User leader=new User(); final Task task=new Task();
    final ProjectMember member=new ProjectMember();
    @BeforeEach void setup() {
        project.setId(UUID.randomUUID());user.setId(UUID.randomUUID());user.setName("팀원");leader.setId(UUID.randomUUID());leader.setName("팀장");
        task.setId(UUID.randomUUID());task.setProject(project);task.setCreatedBy(user);task.setTitle("자료 조사");task.setStatus("IN_PROGRESS");task.setCompletionCriteria("출처를 포함한 비교표");
        var peer=new User();peer.setId(UUID.randomUUID());
        when(notifications.contributors(project)).thenReturn(List.of(user,leader,peer));
        member.setRole("MEMBER");var manager=new ProjectMember();manager.setRole("LEADER");
        when(projects.findById(project.getId())).thenReturn(Optional.of(project));
        when(members.findByProjectAndUser(project,user)).thenReturn(Optional.of(member));
        when(members.findByProjectAndUser(project,leader)).thenReturn(Optional.of(manager));
        when(tasks.findByIdAndProject(task.getId(),project)).thenReturn(Optional.of(task));
        var assigned=new TaskAssignee();assigned.setTask(task);assigned.setUser(user);
        when(assignees.findByTask(task)).thenReturn(List.of(assigned));
    }
    @Test void requestRemainsInProgressAndLeaderApprovalCompletes() {
        var requested=workflow.request(project.getId(),task.getId(),user);
        assertEquals("IN_PROGRESS",requested.status());assertNotNull(requested.completionRequestedAt());assertNull(requested.completedAt());
        assertThrows(ForbiddenException.class,()->workflow.decide(project.getId(),task.getId(),true,true,null,user));
        var done=workflow.decide(project.getId(),task.getId(),true,true,null,leader);
        assertEquals("DONE",done.status());assertNotNull(done.completionApprovedAt());assertEquals("팀장",done.completionApprovedByName());assertNull(done.completionRequestedAt());
    }
    @Test void directStatusAndCreationCannotBypassApprovalEvenForLeader() {
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.updateStatus(project.getId(),task.getId(),new UpdateTaskStatusRequest("DONE"),leader)).getStatusCode().value());
        var request=new CreateTaskRequest("직접 완료",null,null,null,null,List.of(),"DONE",null,null,null);
        assertThrows(ResponseStatusException.class,()->service.createTask(project.getId(),request,leader));verify(tasks,never()).save(any());
    }
    @Test void anotherMemberCanReviewAndCompleteWithoutLeaderApproval() {
        var peer=new User();peer.setId(UUID.randomUUID());peer.setName("확인 팀원");
        when(members.findByProjectAndUser(project,peer)).thenReturn(Optional.of(member));
        workflow.request(project.getId(),task.getId(),user);
        assertTrue(workflow.view(project.getId(),task.getId(),peer).canReview());
        assertFalse(workflow.view(project.getId(),task.getId(),user).canReview());
        var done=workflow.decide(project.getId(),task.getId(),true,true,null,peer);
        assertEquals("DONE",done.status());assertEquals("확인 팀원",done.completionApprovedByName());
        assertEquals(done.completionApprovedAt(),workflow.decide(project.getId(),task.getId(),true,true,null,peer).completionApprovedAt());
        assertThrows(ResponseStatusException.class,()->workflow.decide(project.getId(),task.getId(),true,true,null,leader));
    }
    @Test void assignedLeaderCannotApproveOwnWork() {
        var assigned=new TaskAssignee();assigned.setTask(task);assigned.setUser(leader);
        when(assignees.findByTask(task)).thenReturn(List.of(assigned));
        workflow.request(project.getId(),task.getId(),leader);
        assertFalse(workflow.view(project.getId(),task.getId(),leader).canApprove());
        assertThrows(ForbiddenException.class,()->workflow.decide(project.getId(),task.getId(),true,true,null,leader));
        assertEquals("DONE",workflow.decide(project.getId(),task.getId(),true,true,null,user).status());
    }
    @Test void requesterCannotApproveEvenWhenNotAssigned() {
        workflow.request(project.getId(),task.getId(),leader);
        assertThrows(ForbiddenException.class,()->workflow.decide(project.getId(),task.getId(),true,true,null,leader));
    }
    @Test void anotherMemberCanRequestRevisionButObserverCannot() {
        var peer=new User();peer.setId(UUID.randomUUID());peer.setName("확인 팀원");
        var peerMember=new ProjectMember();peerMember.setRole("MEMBER");
        when(members.findByProjectAndUser(project,peer)).thenReturn(Optional.of(peerMember));
        workflow.request(project.getId(),task.getId(),user);
        var result=workflow.decide(project.getId(),task.getId(),false,false,"표의 출처 보완",peer);
        assertNull(result.completionRequestedAt());assertEquals("IN_PROGRESS",result.status());
        workflow.request(project.getId(),task.getId(),user);peerMember.setRole("OBSERVER");
        assertFalse(workflow.view(project.getId(),task.getId(),peer).canReview());
        assertThrows(ForbiddenException.class,()->workflow.decide(project.getId(),task.getId(),true,true,null,peer));
    }
    @Test void todoTaskWithUploadedResultCanRequestAndStartsProgress() {
        task.setStatus("TODO");task.setAttachmentRequired(true);when(files.existsByTask(task)).thenReturn(true);
        assertTrue(workflow.view(project.getId(),task.getId(),user).canRequest());
        var requested=workflow.request(project.getId(),task.getId(),user);
        assertEquals("IN_PROGRESS",requested.status());assertNotNull(requested.completionRequestedAt());
    }
    @Test void optionalCriteriaAllowsRequestNotificationAndApproval() {
        for (String criteria : new String[]{null, "", " "}) {
            task.setCompletionCriteria(criteria);task.setStatus("TODO");
            task.setCompletionRequestedAt(null);task.setCompletionRequestedBy(null);
            task.setCompletionApprovedAt(null);task.setCompletionApprovedBy(null);
            assertTrue(workflow.view(project.getId(),task.getId(),user).canRequest());
            assertNotNull(workflow.request(project.getId(),task.getId(),user).completionRequestedAt());
            assertTrue(workflow.view(project.getId(),task.getId(),leader).canApprove());
            assertEquals("DONE",workflow.decide(project.getId(),task.getId(),true,true,null,leader).status());
        }
        verify(notifications,times(3)).completionRequested(eq(task),eq(user),anyList());
    }
    @Test void requiredFileIsStillCheckedWithoutCriteria() {
        task.setCompletionCriteria(null);task.setAttachmentRequired(true);
        assertFalse(workflow.view(project.getId(),task.getId(),user).canRequest());
        assertThrows(ResponseStatusException.class,()->workflow.request(project.getId(),task.getId(),user));
        when(files.existsByTask(task)).thenReturn(true);assertNotNull(workflow.request(project.getId(),task.getId(),user).completionRequestedAt());
    }
    @Test void pendingTaskCannotChangeDefinitionAssigneesOrStatus() {
        workflow.request(project.getId(),task.getId(),user);
        assertThrows(ResponseStatusException.class,()->service.updateTask(project.getId(),task.getId(),new UpdateTaskRequest("교체",null,null,null,null,null,null,false,false,null),user));
        assertThrows(ResponseStatusException.class,()->service.setAssignees(project.getId(),task.getId(),new AssignTaskRequest(List.of()),user));
        assertThrows(ResponseStatusException.class,()->service.updateStatus(project.getId(),task.getId(),new UpdateTaskStatusRequest("TODO"),user));
    }
    @Test void approveRechecksFileConditionAndRequiresHumanConfirmation() {
        workflow.request(project.getId(),task.getId(),user);
        assertThrows(ResponseStatusException.class,()->workflow.decide(project.getId(),task.getId(),true,false,null,leader));
        task.setAttachmentRequired(true);assertThrows(ResponseStatusException.class,()->workflow.decide(project.getId(),task.getId(),true,true,null,leader));
        assertEquals("IN_PROGRESS",task.getStatus());
    }
    @Test void rejectionRequiresReasonAndAllowsRevisionAndRetry() {
        workflow.request(project.getId(),task.getId(),user);
        assertThrows(ResponseStatusException.class,()->workflow.decide(project.getId(),task.getId(),false,false," ",leader));
        var result=workflow.decide(project.getId(),task.getId(),false,false,"출처 보완\n\"인용\" 확인",leader);
        assertNull(result.completionRequestedAt());assertTrue(result.completionRejectionReason().contains("출처"));workflow.requireEditable(task);
        assertNotNull(workflow.request(project.getId(),task.getId(),user).completionRequestedAt());assertNull(task.getCompletionRejectionReason());
    }
    @Test void requestAndApprovalRetriesDoNotDuplicateEvents() {
        var first=workflow.request(project.getId(),task.getId(),user);
        assertEquals(first.completionRequestedAt(),workflow.request(project.getId(),task.getId(),user).completionRequestedAt());
        var done=workflow.decide(project.getId(),task.getId(),true,true,null,leader);
        assertEquals(done.completionApprovedAt(),workflow.decide(project.getId(),task.getId(),true,true,null,leader).completionApprovedAt());
        verify(logs,times(1)).record(eq(project),eq(user),eq("TASK_COMPLETION_REQUEST"),anyString());
        verify(logs,times(1)).record(eq(project),eq(leader),eq("TASK_COMPLETE"),anyString());
    }
    @Test void predecessorMustBeApprovedBeforeStartAndCompleteRequest() {
        var before=new Task();before.setId(UUID.randomUUID());before.setTitle("조사");before.setStatus("DONE");task.setPredecessor(before);
        assertThrows(ResponseStatusException.class,()->service.updateStatus(project.getId(),task.getId(),new UpdateTaskStatusRequest("IN_PROGRESS"),user));
        assertThrows(ResponseStatusException.class,()->workflow.request(project.getId(),task.getId(),user));
        before.setCompletionApprovedAt(OffsetDateTime.now());assertNotNull(workflow.request(project.getId(),task.getId(),user).completionRequestedAt());
    }
    @Test void selfAndCyclicPredecessorsAreRejected() {
        assertThrows(ResponseStatusException.class,()->workflow.configure(task,project,task.getId(),false,null));
        var before=new Task();before.setId(UUID.randomUUID());before.setPredecessor(task);
        when(tasks.findByIdAndProject(before.getId(),project)).thenReturn(Optional.of(before));
        assertThrows(ResponseStatusException.class,()->workflow.configure(task,project,before.getId(),false,null));
    }
    @Test void foreignPredecessorDoesNotLeakDetails() {
        assertThrows(NotFoundException.class,()->workflow.configure(task,project,UUID.randomUUID(),false,null));
    }
    @Test void observerAndNonMemberCannotRequestOrApprove() {
        member.setRole("OBSERVER");assertThrows(ForbiddenException.class,()->workflow.request(project.getId(),task.getId(),user));
        assertThrows(ForbiddenException.class,()->workflow.decide(project.getId(),task.getId(),true,true,null,user));
        when(members.findByProjectAndUser(project,user)).thenReturn(Optional.empty());assertThrows(ForbiddenException.class,()->workflow.view(project.getId(),task.getId(),user));
    }
    @Test void unassignedOtherMemberCannotRequest() {
        var stranger=new User();stranger.setId(UUID.randomUUID());when(members.findByProjectAndUser(project,stranger)).thenReturn(Optional.of(member));
        assertThrows(ForbiddenException.class,()->workflow.request(project.getId(),task.getId(),stranger));
    }
    @Test void completedTaskCanOnlyReopenWithoutStartedDependents() {
        workflow.request(project.getId(),task.getId(),user);workflow.decide(project.getId(),task.getId(),true,true,null,leader);
        assertThrows(ForbiddenException.class,()->workflow.reopen(project.getId(),task.getId(),"수정",user));
        when(tasks.existsByPredecessorAndStatusNot(task,"TODO")).thenReturn(true);
        assertThrows(ResponseStatusException.class,()->workflow.reopen(project.getId(),task.getId(),"수정",leader));
        when(tasks.existsByPredecessorAndStatusNot(task,"TODO")).thenReturn(false);
        var opened=workflow.reopen(project.getId(),task.getId(),"추가 수정",leader);assertEquals("IN_PROGRESS",opened.status());assertNull(opened.completionApprovedAt());
    }
    @Test void referencedPredecessorCannotBeDeleted() {
        when(tasks.existsByPredecessor(task)).thenReturn(true);assertThrows(ResponseStatusException.class,()->service.deleteTask(project.getId(),task.getId(),user));verify(tasks,never()).delete(any());
    }
    @Test void reviewerDependsOnRequesterAndUnassignedCreatorIsPerformer() {
        when(notifications.contributors(project)).thenReturn(List.of(user,leader));
        when(assignees.findByTask(task)).thenReturn(List.of());
        assertTrue(workflow.view(project.getId(),task.getId(),user).canRequest());
        assertFalse(workflow.view(project.getId(),task.getId(),leader).canRequest());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->workflow.request(project.getId(),task.getId(),leader)).getStatusCode().value());
        assertNull(task.getCompletionRequestedAt());
    }
    @Test void cancelKeepsEvidenceAndAuditsOriginalRequestBeforeRetry() {
        var first=workflow.request(project.getId(),task.getId(),user);
        assertTrue(workflow.view(project.getId(),task.getId(),user).canCancel());
        assertTrue(workflow.view(project.getId(),task.getId(),leader).canCancel());
        var result=workflow.cancel(project.getId(),task.getId(),"담당자 조정",user);
        assertEquals("IN_PROGRESS",result.status()); assertNull(result.completionRequestedAt());
        assertEquals("출처를 포함한 비교표",task.getCompletionCriteria());
        workflow.requireEditable(task);
        verify(logs).record(eq(project),eq(user),eq("TASK_COMPLETION_CANCEL"),argThat(m->m.contains(user.getId().toString())&&m.contains(first.completionRequestedAt().toString())&&m.contains("담당자 조정")));
        verify(files,never()).delete(any()); verify(assignees,never()).delete(any());
        assertNotNull(workflow.request(project.getId(),task.getId(),user).completionRequestedAt());
    }
    @Test void cancellationValidatesPermissionReasonPendingAndConfirmedState() {
        workflow.request(project.getId(),task.getId(),user);
        var peer=new User();peer.setId(UUID.randomUUID());when(members.findByProjectAndUser(project,peer)).thenReturn(Optional.of(member));
        assertFalse(workflow.view(project.getId(),task.getId(),peer).canCancel());
        assertThrows(ForbiddenException.class,()->workflow.cancel(project.getId(),task.getId(),"수정",peer));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->workflow.cancel(project.getId(),task.getId()," ",user)).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->workflow.cancel(project.getId(),task.getId(),"x".repeat(2001),user)).getStatusCode().value());
        member.setRole("OBSERVER");assertThrows(ForbiddenException.class,()->workflow.cancel(project.getId(),task.getId(),"수정",user));member.setRole("MEMBER");
        var deliverable=new Deliverable();deliverable.setId(UUID.randomUUID());task.setDeliverable(deliverable);
        when(em.find(DeliverableConfirmation.class,deliverable.getId())).thenReturn(mock(DeliverableConfirmation.class));
        assertFalse(workflow.view(project.getId(),task.getId(),leader).canCancel());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->workflow.cancel(project.getId(),task.getId(),"수정",leader)).getStatusCode().value());
        task.setDeliverable(null);workflow.cancel(project.getId(),task.getId(),"수정",leader);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->workflow.cancel(project.getId(),task.getId(),"수정",leader)).getStatusCode().value());
        assertThrows(NotFoundException.class,()->workflow.cancel(project.getId(),UUID.randomUUID(),"수정",leader));
    }
}
