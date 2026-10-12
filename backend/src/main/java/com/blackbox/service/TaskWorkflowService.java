package com.blackbox.service;

import com.blackbox.dto.TaskResponse;
import com.blackbox.entity.*;
import com.blackbox.exception.*;
import com.blackbox.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class TaskWorkflowService {
    public record Check(String code, String label, boolean passed) {}
    public record Completion(TaskResponse task, List<Check> checks, boolean canRequest, boolean canApprove, boolean canReview) {}
    private final TaskRepository tasks;
    private final TaskAssigneeRepository assignees;
    private final TaskFileRepository files;
    private final ProjectAccessChecker access;
    private final ActivityLogService logs;
    private final AlertService alerts;
    private final DiscordNotificationService discord;
    private final ScoreService scores;
    private final EntityManager em;
    private final ObjectMapper mapper;
    public TaskWorkflowService(TaskRepository tasks, TaskAssigneeRepository assignees, TaskFileRepository files,
            ProjectAccessChecker access, ActivityLogService logs, AlertService alerts,
            DiscordNotificationService discord, ScoreService scores, EntityManager em, ObjectMapper mapper) {
        this.tasks=tasks; this.assignees=assignees; this.files=files; this.access=access; this.logs=logs;
        this.alerts=alerts; this.discord=discord; this.scores=scores; this.em=em; this.mapper=mapper;
    }

    // 작은 팀의 순서 변경·시작·검토·승인을 프로젝트 단위로 직렬화하여 순환 연결과 검사 이후 변경을 막는다.
    public void lock(Project project) { em.lock(project, LockModeType.PESSIMISTIC_WRITE); }
    public void requireEditable(Task task) {
        if ("DONE".equals(task.getStatus()) || task.getCompletionRequestedAt()!=null) {
            throw conflict("완료 요청 중이거나 승인된 업무입니다. 보완 요청 또는 재개 후 수정하세요.");
        }
    }
    public void configure(Task task, Project project, UUID predecessorId, boolean clear, Boolean attachmentRequired) {
        if (clear && predecessorId!=null) throw bad("선행 업무 지정과 해제를 동시에 요청할 수 없습니다.");
        if (clear) task.setPredecessor(null);
        if (predecessorId!=null) {
            Task predecessor=find(project,predecessorId);
            Set<UUID> visited=new HashSet<>();
            for(Task cursor=predecessor; cursor!=null; cursor=cursor.getPredecessor()) {
                if (Objects.equals(cursor.getId(),task.getId()) || !visited.add(cursor.getId())) throw bad("선행 업무가 순환하도록 연결할 수 없습니다.");
            }
            task.setPredecessor(predecessor);
        }
        if (attachmentRequired!=null) task.setAttachmentRequired(attachmentRequired);
        if ("IN_PROGRESS".equals(task.getStatus())) requirePredecessor(task);
    }
    public void requirePredecessor(Task task) {
        Task before=task.getPredecessor();
        if (before!=null && (!"DONE".equals(before.getStatus()) || before.getCompletionApprovedAt()==null)) {
            throw conflict("선행 업무 '"+before.getTitle()+"'의 결과 확인이 먼저 필요합니다.");
        }
    }

    @Transactional(readOnly=true)
    public Completion view(UUID projectId, UUID taskId, User user) {
        Project project=access.getProject(projectId); ProjectMember member=access.requireMember(project,user);
        Task task=find(project,taskId); List<Check> checks=checks(task);
        boolean passed=checks.stream().allMatch(Check::passed), leader="LEADER".equals(member.getRole());
        boolean eligible=leader || ("MEMBER".equals(member.getRole()) && assignedOrCreator(task,user));
        boolean legacy="DONE".equals(task.getStatus()) && task.getCompletionApprovedAt()==null;
        boolean reviewer=!"OBSERVER".equals(member.getRole()) && independentReviewer(task,user)
                && task.getCompletionRequestedAt()!=null;
        return new Completion(response(task),checks,passed && eligible && task.getCompletionRequestedAt()==null
                && ("IN_PROGRESS".equals(task.getStatus()) || legacy),passed && reviewer,reviewer);
    }

    @Transactional
    public TaskResponse request(UUID projectId,UUID taskId,User user) {
        Project project=access.getProject(projectId); ProjectMember member=access.requireContributor(project,user); lock(project);
        Task task=find(project,taskId);
        if (!"LEADER".equals(member.getRole()) && !assignedOrCreator(task,user)) throw new ForbiddenException("담당자 또는 팀장만 완료를 요청할 수 있습니다.");
        if (task.getCompletionRequestedAt()!=null) return response(task);
        boolean legacy="DONE".equals(task.getStatus()) && task.getCompletionApprovedAt()==null;
        if (!"IN_PROGRESS".equals(task.getStatus()) && !legacy) throw conflict("진행 중인 업무에서 완료를 요청하세요.");
        requireChecks(task);
        task.setStatus("IN_PROGRESS"); task.setCompletedAt(null);
        task.setCompletionRequestedAt(now()); task.setCompletionRequestedBy(user); task.setCompletionRejectionReason(null);
        save(task); record(task,user,"TASK_COMPLETION_REQUEST",null); return response(task);
    }

    @Transactional
    public TaskResponse decide(UUID projectId,UUID taskId,boolean approved,boolean confirmed,String reason,User reviewer) {
        Project project=access.getProject(projectId); access.requireContributor(project,reviewer); lock(project); Task task=find(project,taskId);
        // 결과 확인과 완료를 한 번에 처리한다. 담당자·요청자는 자신의 결과를 승인할 수 없다.
        // 승인 후 요청자가 비워지므로 재전송은 실제 승인자에게만 기존 결과를 반환한다.
        if (approved && "DONE".equals(task.getStatus()) && task.getCompletionApprovedAt()!=null) {
            if (task.getCompletionApprovedBy().getId().equals(reviewer.getId())) return response(task);
            throw conflict("이미 다른 팀원이 확인한 업무입니다.");
        }
        if (!independentReviewer(task,reviewer)) throw new ForbiddenException("담당자와 요청자는 자신의 결과를 확인할 수 없습니다. 다른 팀원에게 확인을 요청하세요.");
        if (task.getCompletionRequestedAt()==null || !"IN_PROGRESS".equals(task.getStatus())) throw conflict("완료 요청이 없습니다.");
        if (approved) {
            if (!confirmed) throw bad("완료 조건 충족 여부를 확인한 후 승인하세요.");
            requireChecks(task);
            task.setCompletionApprovedAt(now()); task.setCompletionApprovedBy(reviewer);
            task.setCompletedAt(task.getCompletionApprovedAt()); task.setStatus("DONE");
            task.setCompletionRequestedAt(null); task.setCompletionRequestedBy(null); task.setCompletionRejectionReason(null);
            save(task); record(task,reviewer,"TASK_COMPLETE",null);
            alerts.reevaluate(reviewer,project); discord.notifyTaskCompleted(task,reviewer,project);
            recalculateAfterCommit(projectId);
        } else {
            String message=reason==null?"":reason.trim(); if(message.isEmpty()||message.length()>2000)throw bad("보완할 내용을 2,000자 이내로 적어주세요.");
            task.setCompletionRequestedAt(null); task.setCompletionRequestedBy(null); task.setCompletionRejectionReason(message);
            save(task); record(task,reviewer,"TASK_COMPLETION_REJECT",message);
        }
        return response(task);
    }

    @Transactional
    public TaskResponse reopen(UUID projectId,UUID taskId,String reason,User leader) {
        Project project=access.getProject(projectId); access.requireLeader(project,leader); lock(project); Task task=find(project,taskId);
        if (!"DONE".equals(task.getStatus())) throw conflict("완료된 업무만 재개할 수 있습니다.");
        if (tasks.existsByPredecessorAndStatusNot(task,"TODO")) throw conflict("이 업무를 선행 조건으로 사용하는 업무가 진행 중입니다. 후속 업무부터 정리하세요.");
        String message=reason==null?"":reason.trim(); if(message.isEmpty()||message.length()>2000)throw bad("재개 사유를 2,000자 이내로 적어주세요.");
        record(task,leader,"TASK_REOPEN",message);
        task.setStatus("IN_PROGRESS"); task.setCompletedAt(null); task.setCompletionApprovedAt(null); task.setCompletionApprovedBy(null);
        task.setCompletionRequestedAt(null); task.setCompletionRequestedBy(null); task.setCompletionRejectionReason(message);
        save(task); recalculateAfterCommit(projectId); return response(task);
    }

    private List<Check> checks(Task task) {
        Task before=task.getPredecessor();
        return List.of(new Check("CRITERIA","완료 조건 작성",task.getCompletionCriteria()!=null&&!task.getCompletionCriteria().isBlank()),
                new Check("PREDECESSOR",before==null?"선행 업무 없음":"선행 업무 승인 완료: "+before.getTitle(),before==null || ("DONE".equals(before.getStatus())&&before.getCompletionApprovedAt()!=null)),
                new Check("ATTACHMENT",task.isAttachmentRequired()?"이 업무에 파일 첨부":"파일 첨부 선택",!task.isAttachmentRequired()||files.existsByTask(task)));
    }
    private void requireChecks(Task task) { checks(task).stream().filter(c->!c.passed()).findFirst().ifPresent(c->{throw conflict(c.label()+" 조건을 충족해야 합니다.");}); }
    private boolean assignedOrCreator(Task task,User user) {
        var list=assignees.findByTask(task);
        return list.isEmpty()?task.getCreatedBy().getId().equals(user.getId()):list.stream().anyMatch(a->a.getUser().getId().equals(user.getId()));
    }
    private boolean independentReviewer(Task task,User user) {
        return !assignedOrCreator(task,user) && (task.getCompletionRequestedBy()==null
                || !task.getCompletionRequestedBy().getId().equals(user.getId()));
    }
    private Task find(Project project,UUID id) { return tasks.findByIdAndProject(id,project).orElseThrow(()->new NotFoundException("업무를 찾을 수 없습니다.")); }
    private TaskResponse response(Task task) { return TaskResponse.from(task,assignees.findByTask(task)); }
    private void save(Task task) { tasks.save(task); em.flush(); em.refresh(task); }
    private void recalculateAfterCommit(UUID projectId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { scores.recalculateAsync(projectId); }
        });
    }
    private OffsetDateTime now() { return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS); }
    private void record(Task task,User user,String event,String reason) {
        Map<String,Object> metadata=new LinkedHashMap<>(); metadata.put("taskId",task.getId()); metadata.put("title",task.getTitle());
        metadata.put("criteria",task.getCompletionCriteria()); metadata.put("attachmentRequired",task.isAttachmentRequired());
        metadata.put("predecessorId",task.getPredecessor()==null?null:task.getPredecessor().getId());
        metadata.put("fileIds",files.findByTaskOrderByCreatedAtDesc(task).stream().map(f->f.getFile().getId()).toList()); metadata.put("reason",reason);
        try { logs.record(task.getProject(),user,event,mapper.writeValueAsString(metadata)); }
        catch(JsonProcessingException e) { throw new IllegalStateException("업무 승인 기록을 저장하지 못했습니다.",e); }
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
