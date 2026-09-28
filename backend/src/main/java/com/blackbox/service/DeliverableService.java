package com.blackbox.service;

import com.blackbox.dto.DeliverableDtos.*;
import com.blackbox.entity.*;
import com.blackbox.repository.*;
import com.blackbox.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class DeliverableService {
    private final DeliverableRepository deliverables;
    private final DeliverableRequirementRepository requirements;
    private final TaskRepository tasks;
    private final ProjectMemberRepository members;
    private final ProjectAccessChecker access;

    @Transactional(readOnly = true)
    public List<Response> list(UUID projectId, User user) {
        Project project = access.getProject(projectId);
        access.requireMember(project, user);
        return deliverables.findByProjectOrderByDueDateAscCreatedAtAsc(project).stream().map(this::response).toList();
    }

    public Response save(UUID projectId, UUID id, SaveRequest req, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        Deliverable d = id == null ? new Deliverable() : find(project, id);
        d.setProject(project);
        d.setTitle(req.title().trim());
        d.setDescription(req.description());
        d.setDueDate(req.dueDate());
        d.setSubmissionMethod(req.submissionMethod());
        User owner = req.ownerId() == null ? null : members.findByProject(project).stream()
                .filter(m -> m.getUser().getId().equals(req.ownerId()) && !"OBSERVER".equals(m.getRole()))
                .map(ProjectMember::getUser).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "제출 담당자는 프로젝트 팀원이어야 합니다"));
        d.setOwner(owner);
        return response(deliverables.save(d));
    }

    public void delete(UUID projectId, UUID id, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        Deliverable d = find(project, id);
        if (tasks.existsByDeliverable(d)) throw new ResponseStatusException(HttpStatus.CONFLICT, "연결된 업무를 먼저 다른 제출물로 옮기거나 연결 해제해주세요");
        requirements.deleteByDeliverable(d);
        requirements.flush();
        deliverables.delete(d);
    }

    public RequirementResponse saveRequirement(UUID projectId, UUID id, UUID requirementId, RequirementRequest req, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        Deliverable d = find(project, id);
        DeliverableRequirement r = requirementId == null ? new DeliverableRequirement() : findRequirement(d, requirementId);
        r.setDeliverable(d);
        String content = req.content().trim();
        if (!content.equals(r.getContent())) r.clearAssessment();   // 확인했던 문구와 달라지면 충족 체크를 푼다
        r.setContent(content);
        r.setRequired(req.required());
        return RequirementResponse.from(requirements.save(r));
    }

    public void deleteRequirement(UUID projectId, UUID id, UUID requirementId, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        DeliverableRequirement r = findRequirement(find(project, id), requirementId);
        if (tasks.existsByRequirement(r)) throw new ResponseStatusException(HttpStatus.CONFLICT, "이 요구사항에 연결된 업무를 먼저 변경해주세요");
        requirements.delete(r);
    }

    public RequirementResponse assess(UUID projectId, UUID id, UUID requirementId, User user) {
        DeliverableRequirement r = requirementToWrite(projectId, id, requirementId, user);
        r.assess(user);
        return RequirementResponse.from(requirements.save(r));
    }

    public RequirementResponse clearAssessment(UUID projectId, UUID id, UUID requirementId, User user) {
        DeliverableRequirement r = requirementToWrite(projectId, id, requirementId, user);
        r.clearAssessment();
        return RequirementResponse.from(requirements.save(r));
    }

    private DeliverableRequirement requirementToWrite(UUID projectId, UUID id, UUID requirementId, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        return findRequirement(find(project, id), requirementId);
    }

    /** 진척 조회(K-13)가 쓰는 읽기 전용 집계. 요구사항 행 전체를 읽지 않고 개수만 센다. */
    @Transactional(readOnly = true)
    public long countRequiredRequirements(Deliverable deliverable) {
        return requirements.countByDeliverableAndRequiredTrue(deliverable);
    }

    @Transactional(readOnly = true)
    public long countMetRequiredRequirements(Deliverable deliverable) {
        return requirements.countByDeliverableAndRequiredTrueAndAssessedAtIsNotNull(deliverable);
    }

    public Deliverable find(Project project, UUID id) {
        return deliverables.findByIdAndProject(id, project).orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다"));
    }
    public DeliverableRequirement findRequirement(Deliverable d, UUID id) {
        return requirements.findByIdAndDeliverable(id, d).orElseThrow(() -> new NotFoundException("이 제출물의 요구사항을 찾을 수 없습니다"));
    }
    private Response response(Deliverable d) {
        return Response.from(d, requirements.findByDeliverableOrderByCreatedAtAsc(d));
    }
}
