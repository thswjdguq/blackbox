package com.blackbox.agent;

import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 실제 모델 테스트가 쓰는 준비 자료가 서비스로 만들 수 없는 끝난 업무를 만들지 않는지 본다(키 없이 CI에서 돈다).
 * 실제 서비스는 담당자·업무를 만든 사람·요청자의 승인을 거절한다(TaskWorkflowService.independentReviewer)
 */
class AgentTestDataTest {
    final EntityManager em = mock(EntityManager.class);
    final AgentTestData data = new AgentTestData(em, mock(PlatformTransactionManager.class), mock(JdbcTemplate.class));
    final Project project = new Project();
    final User leader = user(), member = user();
    final LocalDate today = LocalDate.now();

    @Test void doneTaskRefusesTheAssigneeOrCreatorAsApprover() {
        assertThrows(IllegalArgumentException.class, () -> data.doneTask(project, leader, "주제 선정", today, null, null, leader, leader));
        assertThrows(IllegalArgumentException.class, () -> data.doneTask(project, leader, "초안", today, null, null, member, member));
        assertThrows(IllegalArgumentException.class, () -> data.doneTask(project, leader, "초안", today, null, null, member, leader));
        assertThrows(IllegalArgumentException.class, () -> data.doneTask(project, leader, "초안", today, null, null, member, null));
        verifyNoInteractions(em);
    }

    @Test void plainTaskCannotBeDone() {
        assertThrows(IllegalArgumentException.class, () -> data.task(project, leader, "주제 선정", "DONE", today, null, null, member));
        verifyNoInteractions(em);
    }

    private static User user() { User u = new User(); u.setId(UUID.randomUUID()); return u; }
}
