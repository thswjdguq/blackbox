package com.blackbox.agent.web;

import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.AgentProposalRepository;
import com.blackbox.agent.DeliverableStage;
import com.blackbox.agent.DeliverableStages;
import com.blackbox.agent.model.AgentModel;
import com.blackbox.agent.run.AgentRunner;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import com.blackbox.service.ProjectAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 도우미 창이 처음 그릴 것을 모은다(K-30 3장의 상태 객체). */
@Service
@RequiredArgsConstructor
public class AgentStatusReader {
    private final ProjectAccessChecker access;
    private final AgentModel model;
    private final AgentRunner runner;
    private final DeliverableStages stages;
    private final AgentProposalRepository proposals;

    @Transactional(readOnly = true)
    public Status read(UUID projectId, User user) {
        Project project = access.getProject(projectId);
        boolean contributor = ProjectAccessChecker.canContribute(access.requireMember(project, user));
        boolean available = model.available();
        return new Status(available, available ? null : AgentRunner.UNAVAILABLE,
                runner.skills().stream().map(s -> new Skill(s.name(), s.label(), s.allows(contributor))).toList(),
                stages.read(projectId, user).stream()
                        .map(s -> new Stage(s.deliverable().id(), s.deliverable().title(), s.deliverable().dueDate(), s.stage())).toList(),
                proposals.countByProjectAndStatus(project, AgentProposal.Status.PENDING));
    }

    public record Status(boolean available, String unavailableReason, List<Skill> skills, List<Stage> stages,
                         long pendingProposalCount) {}
    public record Skill(String skill, String label, boolean allowed) {}
    public record Stage(UUID deliverableId, String title, LocalDate dueDate, DeliverableStage stage) {}
}
