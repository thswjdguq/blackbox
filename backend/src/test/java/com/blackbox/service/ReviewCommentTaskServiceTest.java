package com.blackbox.service;

import com.blackbox.dto.*;
import com.blackbox.dto.ReviewCommentTaskDtos.Request;
import com.blackbox.entity.*;
import com.blackbox.exception.*;
import com.blackbox.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewCommentTaskServiceTest {
    final ReviewCommentRepository comments=mock(ReviewCommentRepository.class);
    final TaskRepository tasks=mock(TaskRepository.class);
    final ProjectRepository projects=mock(ProjectRepository.class);
    final ProjectMemberRepository members=mock(ProjectMemberRepository.class);
    final ProjectAccessChecker access=new ProjectAccessChecker(projects,members);
    final ReviewRoundReader reviews=mock(ReviewRoundReader.class);
    final TaskService taskService=mock(TaskService.class);
    final ActivityLogService activity=mock(ActivityLogService.class);
    final ReviewCommentTaskService service=new ReviewCommentTaskService(comments,tasks,access,reviews,taskService,activity);
    final Project p=new Project(); final User user=new User(); final ReviewComment c=new ReviewComment(); final Task task=new Task();
    final UUID d=UUID.randomUUID(),r=UUID.randomUUID();
    @BeforeEach void setup() {
        p.setId(UUID.randomUUID()); user.setId(UUID.randomUUID()); c.setId(UUID.randomUUID()); c.setContent("원문 피드백");task.setId(UUID.randomUUID());
        when(projects.findById(p.getId())).thenReturn(Optional.of(p)); grant("MEMBER"); view(null,"IN_REVIEW");
        when(comments.findByIdAndProjectIdAndDeliverableIdAndReviewId(c.getId(),p.getId(),d,r)).thenReturn(Optional.of(c));
        TaskResponse response=mock(TaskResponse.class); when(response.id()).thenReturn(task.getId());
        when(taskService.createTask(eq(p.getId()),any(),eq(user))).thenReturn(response);
        when(tasks.findById(task.getId())).thenReturn(Optional.of(task));
    }
    void grant(String role) {ProjectMember m=new ProjectMember();m.setRole(role);when(members.findByProjectAndUser(p,user)).thenReturn(Optional.of(m));}
    void view(String decision,String status) {when(reviews.lockForWrite(p.getId(),d,r)).thenReturn(new ReviewRoundReader.ReviewRoundView(r,d,p.getId(),1,true,decision,status));}
    ReviewCommentTaskDtos.Response convert() {return service.convert(p.getId(),d,r,c.getId(),new Request("  출처 보완  ","  검증 완료  "),user);}
    @Test void createsTodoWithoutAssigneesThenReturnsExisting() {
        assertTrue(convert().created()); assertSame(task,c.getLinkedTask());
        var captor=ArgumentCaptor.forClass(CreateTaskRequest.class);verify(taskService).createTask(eq(p.getId()),captor.capture(),eq(user));
        var req=captor.getValue();assertEquals("출처 보완",req.title());assertEquals("원문 피드백",req.description());
        assertEquals("TODO",req.status());assertEquals(d,req.deliverableId());assertNull(req.requirementId());assertTrue(req.assigneeIds().isEmpty());assertEquals("검증 완료",req.completionCriteria());
        assertFalse(convert().created());verify(taskService,times(1)).createTask(any(),any(),any());
        verify(activity,times(1)).record(eq(p),eq(user),eq("FEEDBACK_TASK_LINK"),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"CONFIRMED","SUBMITTED"})
    void existingLinkReadableAfterFinalizationButNewLinkBlocked(String status) {
        view(null,status); assertThrows(ResponseStatusException.class,this::convert);
        c.setLinkedTask(task);assertFalse(convert().created());verifyNoInteractions(taskService,activity);
    }
    @Test void approvalAndResolutionBlockNewTask() {
        view("APPROVED","IN_REVIEW");assertThrows(ResponseStatusException.class,this::convert);
        view(null,"DRAFT");c.setResolvedAt(java.time.OffsetDateTime.now());assertThrows(ResponseStatusException.class,this::convert);
        verifyNoInteractions(taskService,activity);
    }
    @Test void observerAndOutsiderBlockedBeforeLock() {
        grant("OBSERVER");assertThrows(ForbiddenException.class,this::convert);
        when(members.findByProjectAndUser(p,user)).thenReturn(Optional.empty());assertThrows(ForbiddenException.class,this::convert);
        verifyNoInteractions(reviews,taskService,activity);
    }
    @Test void foreignCommentRejected() {
        assertThrows(NotFoundException.class,()->service.convert(p.getId(),d,r,UUID.randomUUID(),new Request("업무",null),user));
        verifyNoInteractions(taskService,activity);
    }
    @Test void titleAndCriteriaValidation() {
        for(String title:Arrays.asList(null,"  ","x".repeat(256))) assertThrows(ResponseStatusException.class,()->service.convert(p.getId(),d,r,c.getId(),new Request(title,null),user));
        assertThrows(ResponseStatusException.class,()->service.convert(p.getId(),d,r,c.getId(),new Request("업무","x".repeat(2001)),user));
        verifyNoInteractions(taskService,activity);
    }
}
