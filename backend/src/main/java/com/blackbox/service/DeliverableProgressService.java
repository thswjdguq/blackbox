package com.blackbox.service;

import com.blackbox.dto.DeliverableProgressDtos.RequirementProgress;
import com.blackbox.dto.DeliverableProgressDtos.Response;
import com.blackbox.dto.DeliverableProgressDtos.TaskProgress;
import com.blackbox.entity.Deliverable;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import com.blackbox.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliverableProgressService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final TaskRepository tasks;
    private final ProjectAccessChecker access;
    private final DeliverableService deliverables;

    @Transactional(readOnly = true)
    public Response get(UUID projectId, UUID deliverableId, User user) {
        Project project = access.getProject(projectId);
        access.requireMember(project, user);
        Deliverable deliverable = deliverables.find(project, deliverableId);

        long taskTotal = tasks.countByProjectAndDeliverable(project, deliverable);
        long taskCompleted = tasks.countByProjectAndDeliverableAndStatus(project, deliverable, "DONE");
        long requirementTotal = deliverables.countRequiredRequirements(deliverable);
        long requirementMet = deliverables.countMetRequiredRequirements(deliverable);

        return new Response(
                deliverable.getId(),
                new TaskProgress(taskTotal, taskCompleted, percent(taskCompleted, taskTotal)),
                new RequirementProgress(
                        requirementTotal,
                        requirementMet,
                        percent(requirementMet, requirementTotal),
                        true
                )
        );
    }

    private BigDecimal percent(long completed, long total) {
        if (total == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(completed)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
