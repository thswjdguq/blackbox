package com.blackbox.service;

import com.blackbox.dto.ReviewCommentTaskDtos.Request;
import com.blackbox.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.doThrow;

/** 실제 DB에 업무·TASK_CREATE까지 저장한 뒤 마지막 활동 기록 실패의 전체 롤백을 확인한다. */
@SpringBootTest @ActiveProfiles("test")
class ReviewCommentTaskRollbackTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ReviewCommentTaskService service;
    @SpyBean ActivityLogService activity;

    @Test void failedLinkAuditRollsBackTaskLinkAndEarlierActivity() {
        var tx=new TransactionTemplate(transactions);
        record Seed(Project p,Deliverable d,ReviewRound r,ReviewComment c,User u) {}
        Seed seed=tx.execute(status->{
            User u=new User();u.setEmail(UUID.randomUUID()+"@example.com");u.setName("롤백검사");u.setRole("STUDENT");u.setPasswordHash("dummy");em.persist(u);
            Project p=new Project();p.setName("rollback-"+UUID.randomUUID());p.setCreatedBy(u);em.persist(p);
            ProjectMember m=new ProjectMember();m.setProject(p);m.setUser(u);m.setRole("LEADER");em.persist(m);
            Deliverable d=new Deliverable();d.setProject(p);d.setTitle("롤백 제출물");d.setDueDate(LocalDate.of(2026,10,18));em.persist(d);
            ReviewRound r=new ReviewRound();r.setProject(p);r.setDeliverable(d);r.setOpenedBy(u);r.setRoundNo(1);em.persist(r);
            ReviewComment c=new ReviewComment();c.setProject(p);c.setDeliverable(d);c.setReview(r);c.setAuthor(u);c.setContent("롤백 피드백");em.persist(c);
            return new Seed(p,d,r,c,u);
        });
        doThrow(new IllegalStateException("검사용 감사 기록 실패")).when(activity)
                .record(any(),any(),eq("FEEDBACK_TASK_LINK"),anyString());
        assertThrows(IllegalStateException.class,()->service.convert(seed.p().getId(),seed.d().getId(),seed.r().getId(),seed.c().getId(),new Request("업무 생성",null),seed.u()));
        tx.executeWithoutResult(status->{
            assertEquals(0L,((Number)em.createNativeQuery("select count(*) from tasks where project_id=:p").setParameter("p",seed.p().getId()).getSingleResult()).longValue());
            assertEquals(0L,((Number)em.createNativeQuery("select count(*) from activity_logs where project_id=:p").setParameter("p",seed.p().getId()).getSingleResult()).longValue());
            assertNull(em.find(ReviewComment.class,seed.c().getId()).getLinkedTask());
        });
    }
}
