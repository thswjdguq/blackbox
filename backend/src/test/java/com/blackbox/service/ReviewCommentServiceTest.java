package com.blackbox.service;

import com.blackbox.dto.ReviewCommentDtos.*;
import com.blackbox.entity.*;
import com.blackbox.exception.*;
import com.blackbox.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewCommentServiceTest {
    final ProjectRepository projects = mock(ProjectRepository.class);
    final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    final ProjectAccessChecker access = new ProjectAccessChecker(projects, members);
    final ReviewCommentRepository comments = mock(ReviewCommentRepository.class);
    final ReviewRoundRepository rounds = mock(ReviewRoundRepository.class);
    final DeliverableRepository deliverables = mock(DeliverableRepository.class);
    final ReviewRoundReader reviews = mock(ReviewRoundReader.class);
    final ActivityLogService activity = mock(ActivityLogService.class);
    final ReviewCommentService service = new ReviewCommentService(comments, rounds, deliverables, access, reviews, activity, new ObjectMapper());
    final Project p = new Project(); final User user = new User(); final Deliverable d = new Deliverable();
    final ReviewRound r = new ReviewRound(); final ReviewComment c = new ReviewComment();

    @BeforeEach void setup() {
        p.setId(UUID.randomUUID()); user.setId(UUID.randomUUID()); user.setName("검토자");
        d.setId(UUID.randomUUID()); d.setProject(p); r.setId(UUID.randomUUID()); r.setDeliverable(d); r.setProject(p);
        c.setId(UUID.randomUUID()); c.setProject(p); c.setDeliverable(d); c.setReview(r); c.setAuthor(user); c.setContent("출처 확인");
        when(projects.findById(p.getId())).thenReturn(Optional.of(p));
        grant("MEMBER");
        when(deliverables.findByIdAndProject(d.getId(), p)).thenReturn(Optional.of(d));
        when(rounds.findById(r.getId())).thenReturn(Optional.of(r));
        when(comments.findByIdAndProjectIdAndDeliverableIdAndReviewId(c.getId(),p.getId(),d.getId(),r.getId())).thenReturn(Optional.of(c));
        when(comments.save(any())).thenAnswer(inv -> {ReviewComment saved=inv.getArgument(0); saved.setId(UUID.randomUUID()); return saved;});
        view(true, null, "IN_REVIEW");
    }
    void grant(String role) {
        ProjectMember m = new ProjectMember(); m.setRole(role);
        when(members.findByProjectAndUser(p, user)).thenReturn(Optional.of(m));
    }
    void view(boolean latest, String decision, String status) {
        var v = new ReviewRoundReader.ReviewRoundView(r.getId(),d.getId(),p.getId(),1,latest,decision,status);
        when(reviews.lockForWrite(p.getId(),d.getId(),r.getId())).thenReturn(v);
        when(reviews.get(p.getId(),d.getId(),r.getId())).thenReturn(v);
    }
    Response resolve(String reason) {return service.resolve(p.getId(),d.getId(),r.getId(),c.getId(),new ResolutionRequest(reason),user);}

    @ParameterizedTest @ValueSource(strings={"LEADER","MEMBER","OBSERVER"})
    void rolesCanRead(String role) {
        grant(role); when(comments.findByProjectIdAndDeliverableIdAndReviewIdOrderByCreatedAtAscIdAsc(p.getId(),d.getId(),r.getId())).thenReturn(List.of(c));
        assertEquals("OPEN", service.list(p.getId(),d.getId(),r.getId(),user).get(0).status());
    }
    @Test void observerAndOutsiderCannotWrite() {
        grant("OBSERVER"); assertThrows(ForbiddenException.class, () -> resolve(null));
        when(members.findByProjectAndUser(p,user)).thenReturn(Optional.empty());
        assertThrows(ForbiddenException.class, () -> service.list(p.getId(),d.getId(),r.getId(),user));
        verifyNoInteractions(reviews, activity);
    }
    @Test void createTrimsAndRecords() {
        var response=service.create(p.getId(),d.getId(),r.getId(),new CreateRequest("  출처 추가  "),user);
        assertEquals("출처 추가",response.content()); assertNull(response.resolution());
        verify(activity).record(eq(p),eq(user),eq("FEEDBACK_CREATE"),anyString());
    }
    @Test void invalidContentRejected() {
        for(String text : Arrays.asList(null,"  ","x".repeat(2001))) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create(p.getId(),d.getId(),r.getId(),new CreateRequest(text),user)).getStatusCode().value());
        }
        verify(comments, never()).save(any());
    }
    @Test void pastRoundRejectsCreateButAllowsResolution() {
        view(false,"CHANGES_REQUESTED","IN_REVIEW");
        assertThrows(ResponseStatusException.class, () -> service.create(p.getId(),d.getId(),r.getId(),new CreateRequest("추가"),user));
        assertEquals("RESOLVED",resolve(null).status());
    }
    @ParameterizedTest @ValueSource(strings={"CONFIRMED","SUBMITTED"})
    void finalizedBlocksWrites(String status) {
        view(true,null,status); assertEquals(409,assertThrows(ResponseStatusException.class,()->resolve(null)).getStatusCode().value());
        assertNull(c.getResolvedAt());
    }
    @Test void approvedRoundBlocksEvenDuplicateResolution() {
        resolve("완료"); view(true,"APPROVED","IN_REVIEW");
        assertThrows(ResponseStatusException.class,()->resolve("중복"));
    }
    @Test void resolveIsIdempotentAndReopenPreservesHistory() {
        assertEquals("RESOLVED",resolve("  반영  ").status()); var time=c.getResolvedAt();
        resolve("덮어쓰기"); assertEquals(time,c.getResolvedAt()); assertEquals("반영",c.getResolutionReason());
        assertEquals("OPEN",service.reopen(p.getId(),d.getId(),r.getId(),c.getId(),user).status());
        assertNull(c.getResolvedAt()); assertNull(c.getResolutionReason());
        verify(activity).record(eq(p),eq(user),eq("FEEDBACK_REOPEN"),contains("반영"));
        service.reopen(p.getId(),d.getId(),r.getId(),c.getId(),user);
        verify(activity,times(1)).record(eq(p),eq(user),eq("FEEDBACK_REOPEN"),anyString());
    }
    @Test void taskCompletionDoesNotResolveAutomatically() {
        Task task=new Task(); task.setStatus("DONE"); c.setLinkedTask(task);
        assertEquals("REFLECTION_PENDING",c.status()); task.setStatus("IN_PROGRESS"); assertEquals("OPEN",c.status());
        assertEquals("RESOLVED",resolve("업무는 별도 진행").status()); assertEquals("IN_PROGRESS",task.getStatus());
    }
    @Test void foreignCommentAndRoundRejected() {
        UUID foreign=UUID.randomUUID();
        assertThrows(NotFoundException.class,()->service.reopen(p.getId(),d.getId(),r.getId(),foreign,user));
        when(reviews.lockForWrite(p.getId(),d.getId(),r.getId())).thenThrow(new NotFoundException("검토 회차를 찾을 수 없습니다"));
        assertThrows(NotFoundException.class,()->resolve(null));
    }
    @Test void reasonLimitAndEmptyReason() {
        assertThrows(ResponseStatusException.class,()->resolve("x".repeat(1001)));
        assertNull(resolve("  ").resolution().reason());
    }
    @Test void counterChecksDeliverableScopeAndAllRounds() {
        var counter=new ReviewCommentCounter(comments,deliverables,access);
        when(comments.countByProjectIdAndDeliverableIdAndResolvedAtIsNull(p.getId(),d.getId())).thenReturn(3L);
        assertEquals(3,counter.countUnresolvedComments(p.getId(),d.getId()));
        assertThrows(NotFoundException.class,()->counter.countUnresolvedComments(p.getId(),UUID.randomUUID()));
    }
}
