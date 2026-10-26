package com.blackbox.repository;

import com.blackbox.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 실제 V22 스키마에서 저장·재조회·미해결 범위·연결 업무 삭제 정책을 검증한다. */
@DataJpaTest @ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReviewCommentRepositoryTest {
    @Autowired EntityManager em;
    @Autowired ReviewCommentRepository comments;

    @Test void countsAllRoundsAndKeepsResolutionAfterTaskDeletion() {
        User u=new User(); u.setEmail(UUID.randomUUID()+"@example.com"); u.setName("검토자"); u.setRole("STUDENT"); u.setPasswordHash("dummy"); em.persist(u);
        Project p=new Project(); p.setName("feedback-db-"+UUID.randomUUID()); p.setCreatedBy(u); em.persist(p);
        Deliverable d=new Deliverable(); d.setProject(p); d.setTitle("중간 보고서"); d.setDueDate(LocalDate.of(2026,10,18)); em.persist(d);
        ReviewRound first=round(p,d,u,1), second=round(p,d,u,2);
        ReviewComment open=comment(p,d,u,first), pending=comment(p,d,u,second), resolved=comment(p,d,u,second);
        Task task=new Task(); task.setProject(p); task.setDeliverable(d); task.setTitle("출처 반영"); task.setStatus("DONE"); task.setPriority("MEDIUM"); task.setCreatedBy(u);
        // 완료된 업무 fixture도 승인 후 완료 정책을 따른다. 삭제 뒤 피드백 보존을 검증하는 테스트다.
        task.setCompletionApprovedAt(java.time.OffsetDateTime.now()); task.setCompletionApprovedBy(u);
        em.persist(task);
        pending.setLinkedTask(task);
        resolved.setResolvedBy(u); resolved.setResolvedAt(java.time.OffsetDateTime.now()); resolved.setResolutionReason("반영 완료");
        em.flush(); em.clear();
        assertEquals(2,comments.countByProjectIdAndDeliverableIdAndResolvedAtIsNull(p.getId(),d.getId()));
        assertEquals(0,comments.countByProjectIdAndDeliverableIdAndResolvedAtIsNull(UUID.randomUUID(),d.getId()));
        var found=comments.findByProjectIdAndDeliverableIdAndReviewIdOrderByCreatedAtAscIdAsc(p.getId(),d.getId(),second.getId());
        assertEquals(2,found.size());
        assertTrue(found.stream().anyMatch(c->"REFLECTION_PENDING".equals(c.status())));
        assertTrue(comments.findByIdAndProjectIdAndDeliverableIdAndReviewId(open.getId(),p.getId(),d.getId(),second.getId()).isEmpty());
        // PostgreSQL의 부분 SET NULL을 확인하기 위해 DB에서 삭제하고 영속 컨텍스트를 다시 읽는다.
        em.createNativeQuery("delete from tasks where id = :id").setParameter("id",task.getId()).executeUpdate();
        em.clear();
        var after=comments.findById(pending.getId()).orElseThrow();
        assertNull(after.getLinkedTask()); assertEquals("OPEN",after.status());
        assertEquals("반영 완료",comments.findById(resolved.getId()).orElseThrow().getResolutionReason());
    }
    private ReviewRound round(Project p, Deliverable d, User u, int no) {
        ReviewRound r=new ReviewRound(); r.setProject(p); r.setDeliverable(d); r.setOpenedBy(u); r.setRoundNo(no); em.persist(r); return r;
    }
    private ReviewComment comment(Project p, Deliverable d, User u, ReviewRound r) {
        ReviewComment c=new ReviewComment(); c.setProject(p); c.setDeliverable(d); c.setReview(r); c.setAuthor(u); c.setContent("출처 확인"); em.persist(c); return c;
    }
}
