package com.blackbox.service;

import com.blackbox.dto.ReviewCommentDtos.CreateRequest;
import com.blackbox.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.blackbox.scheduler.ScoreScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

/** 활동 기록을 실제 DB에 쓴 다음 실패시켜 코멘트와 감사 기록이 함께 롤백되는지 확인한다. */
@SpringBootTest @ActiveProfiles("test")
class ReviewCommentRollbackTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ReviewCommentService service;
    @SpyBean ActivityLogService activity;
    @MockBean ScoreScheduler scheduler;
    private UUID projectId, userId;

    @AfterEach void removeFixture() {
        if (projectId == null) return;
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            // 이번 테스트가 커밋한 준비 자료만 지운다. 실패한 검사도 정리되고 기존 DB 자료는 보존한다.
            for (String table : java.util.List.of("review_comments", "activity_logs", "review_rounds",
                    "deliverables", "project_members")) {
                em.createNativeQuery("delete from " + table + " where project_id=:p").setParameter("p", projectId).executeUpdate();
            }
            em.createNativeQuery("delete from projects where id=:p").setParameter("p", projectId).executeUpdate();
            em.createNativeQuery("delete from users where id=:u").setParameter("u", userId).executeUpdate();
            assertEquals(0L, ((Number) em.createNativeQuery("select count(*) from projects where id=:p")
                    .setParameter("p", projectId).getSingleResult()).longValue());
        });
    }

    @Test void failedAuditRollsBackCommentAndActivity() {
        var tx=new TransactionTemplate(transactions);
        record Seed(UUID project,UUID deliverable,UUID review,User user) {}
        Seed seed=tx.execute(status->{
            User u=new User();u.setEmail(UUID.randomUUID()+"@example.com");u.setName("피드백롤백");u.setRole("STUDENT");u.setPasswordHash("dummy");em.persist(u);
            Project p=new Project();p.setName("comment-rollback-"+UUID.randomUUID());p.setCreatedBy(u);em.persist(p);
            ProjectMember m=new ProjectMember();m.setProject(p);m.setUser(u);m.setRole("LEADER");em.persist(m);
            Deliverable d=new Deliverable();d.setProject(p);d.setTitle("검사 제출물");d.setDueDate(LocalDate.of(2026,10,18));em.persist(d);
            ReviewRound r=new ReviewRound();r.setProject(p);r.setDeliverable(d);r.setOpenedBy(u);r.setRoundNo(1);em.persist(r);
            return new Seed(p.getId(),d.getId(),r.getId(),u);
        });
        projectId=seed.project(); userId=seed.user().getId();
        doAnswer(invocation->{
            invocation.callRealMethod();
            em.flush();
            throw new IllegalStateException("검사용 저장 후 실패");
        }).when(activity).record(any(),any(),eq("FEEDBACK_CREATE"),anyString());
        assertThrows(IllegalStateException.class,()->service.create(seed.project(),seed.deliverable(),seed.review(),new CreateRequest("저장 롤백"),seed.user()));
        tx.executeWithoutResult(status->{
            assertEquals(0L,count("review_comments",seed.project()));
            assertEquals(0L,count("activity_logs",seed.project()));
        });
    }
    private long count(String table,UUID project) {
        // table은 테스트 코드의 고정 문자열이며 외부 입력을 SQL에 넣지 않는다.
        return ((Number)em.createNativeQuery("select count(*) from "+table+" where project_id=:p")
                .setParameter("p",project).getSingleResult()).longValue();
    }
}
