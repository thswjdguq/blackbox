package com.blackbox.controller;

import com.blackbox.dto.*;
import com.blackbox.entity.*;
import com.blackbox.security.JwtService;
import com.blackbox.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static com.blackbox.repository.ReviewFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 DB, 실제 서비스·권한·JSON 직렬화. 테스트 트랜잭션은 롤백한다. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class FeedbackLoopHttpTest {
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired TaskMessageService messages;
    @Autowired NotificationService notifications;
    @Autowired ReviewCommentService comments;
    @Autowired ReviewRoundService reviews;
    User author, worker, observer, outsider;
    Project project;
    Task task;
    Deliverable deliverable;
    ReviewRound review;

    @BeforeEach void setup() {
        author=user(em,"피드백 작성자"); worker=user(em,"담당자"); observer=user(em,"관찰자"); outsider=user(em,"외부인");
        project=project(em,author);
        member(em,project,author,"LEADER"); member(em,project,worker,"MEMBER"); member(em,project,observer,"OBSERVER");
        task=new Task();task.setProject(project);task.setCreatedBy(worker);task.setTitle("조사 결과");task.setStatus("IN_PROGRESS");task.setPriority("MEDIUM");em.persist(task);
        deliverable=deliverable(em,project,"최종 보고서");deliverable.setOwner(worker);
        review=round(em,deliverable,1,file(em,project,worker,"보고서.pdf",1,'a'),worker,null);
        em.flush();
    }
    @Test void ordinaryCommentNotifiesAndReplyRemainsInInbox() throws Exception {
        var comment=messages.post(project.getId(),task.getId(),"출처를 보완해주세요",null,null,null,author);
        assertEquals(1,notifications.unread(project.getId(),worker).unreadCount());
        assertEquals(0,notifications.unread(project.getId(),author).unreadCount());
        assertEquals(0,notifications.unread(project.getId(),observer).unreadCount());
        assertEquals(comment.id(),messages.inbox(project.getId(),worker).received().get(0).messageId());
        messages.post(project.getId(),task.getId(),"출처를 추가했습니다",null,comment.id(),null,worker);
        var sent=messages.inbox(project.getId(),author).sent().get(0);
        assertEquals("RESPONDED",sent.status());assertEquals("출처를 추가했습니다",sent.response());
        assertEquals(1,notifications.unread(project.getId(),author).unreadCount());
        mvc.perform(get("/api/projects/{id}/feedback",project.getId()).header("Authorization",token(author)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sent[0].response").value("출처를 추가했습니다"));
    }
    @Test void requestedFeedbackStaysAfterAnswerAndObserverHasNoInbox() {
        var request=messages.post(project.getId(),task.getId(),"검토 요청",worker.getId(),null,null,author);
        messages.post(project.getId(),task.getId(),"확인했습니다",null,request.id(),null,worker);
        assertEquals("ANSWERED",messages.inbox(project.getId(),author).sent().get(0).status());
        assertEquals(1,messages.inbox(project.getId(),worker).received().size());
        assertTrue(messages.inbox(project.getId(),observer).received().isEmpty());
    }
    @Test void reviewFeedbackResolutionAndNewVersionNotifyOriginalAuthor() throws Exception {
        var c=comments.create(project.getId(),deliverable.getId(),review.getId(),new ReviewCommentDtos.CreateRequest("근거를 추가해주세요"),author);
        assertEquals(1,notifications.unread(project.getId(),worker).unreadCount());
        assertEquals("REVIEW",messages.inbox(project.getId(),worker).received().get(0).kind());
        comments.resolve(project.getId(),deliverable.getId(),review.getId(),c.id(),new ReviewCommentDtos.ResolutionRequest("3쪽에 근거 추가"),worker);
        comments.resolve(project.getId(),deliverable.getId(),review.getId(),c.id(),new ReviewCommentDtos.ResolutionRequest("중복"),worker);
        assertEquals(1,notifications.unread(project.getId(),author).unreadCount());
        assertEquals("3쪽에 근거 추가",messages.inbox(project.getId(),author).sent().get(0).response());
        var next=file(em,project,worker,"보고서.pdf",2,'b');em.flush();
        reviews.open(project.getId(),deliverable.getId(),new ReviewDtos.OpenRequest(next.getId()),worker);
        assertEquals(2,notifications.unread(project.getId(),author).unreadCount());
        mvc.perform(get("/api/projects/{id}/notifications",project.getId()).header("Authorization",token(author)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].deliverableId").value(deliverable.getId().toString()))
                .andExpect(jsonPath("$.items[0].type").value("REVIEW_VERSION_POSTED"))
                .andExpect(jsonPath("$.items[0].targetAvailable").value(true));
        assertEquals(0,notifications.unread(project.getId(),observer).unreadCount());
    }
    @Test void outsidersCannotReadFeedbackOrNotifications() throws Exception {
        mvc.perform(get("/api/projects/{id}/feedback",project.getId()).header("Authorization",token(outsider))).andExpect(status().isForbidden());
        mvc.perform(get("/api/projects/{id}/notifications",project.getId()).header("Authorization",token(outsider))).andExpect(status().isForbidden());
    }
    private String token(User u) {return "Bearer "+jwt.generateAccessToken(u.getEmail());}
}
