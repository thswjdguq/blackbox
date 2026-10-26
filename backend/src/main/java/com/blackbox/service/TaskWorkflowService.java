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
    public record Completion(TaskResponse task, List<Check> checks, boolean canRequest, boolean canApprove, boolean canReview, boolean canCancel) {}
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
    private final NotificationService notifications;
    public TaskWorkflowService(TaskRepository tasks, TaskAssigneeRepository assignees, TaskFileRepository files,
            ProjectAccessChecker access, ActivityLogService logs, AlertService alerts,
            DiscordNotificationService discord, ScoreService scores, EntityManager em, ObjectMapper mapper,
            NotificationService notifications) {
        this.tasks=tasks; this.assignees=assignees; this.files=files; this.access=access; this.logs=logs;
        this.alerts=alerts; this.discord=discord; this.scores=scores; this.em=em; this.mapper=mapper;
        this.notifications=notifications;
    }

    // 작은 팀의 순서 변경·시작·검토·승인을 프로젝트 단위로 직렬화하여 순환 연결과 검사 이후 변경을 막는다.
    public void lock(Project project) { em.lock(project, LockModeType.PESSIMISTIC_WRITE); }
    public void requireEditable(Task task) {
        UUID previous = task.getDeliverable()==null ? null : task.getDeliverable().getId();
        requireDeliverableEditable(task.getDeliverable());
        // 제출물 잠금 대기 중 다른 쓰기 경로에서 업무 연결이 바뀌었을 수 있다.
        // 새 제출물 잠금을 뒤늦게 추가하지 않고 재시도를 요청한다(main B-22 보존).
        try {
            em.refresh(task,LockModeType.PESSIMISTIC_WRITE);
        } catch (EntityNotFoundException missing) {
            throw new NotFoundException("태스크를 찾을 수 없습니다");
        }
        UUID current = task.getDeliverable()==null ? null : task.getDeliverable().getId();
        if (!Objects.equals(previous,current))
            throw conflict("업무의 제출물 연결이 바뀌었습니다. 새로고침 후 다시 시도해주세요");
        if ("DONE".equals(task.getStatus()) || task.getCompletionRequestedAt()!=null) {
            throw conflict("완료 요청 중이거나 승인된 업무입니다. 보완 요청 또는 재개 후 수정하세요.");
        }
    }
    /** 호출자는 프로젝트 잠금을 먼저 잡는다. 최종 확정과 같은 제출물 행을 잠가 검사 이후 확정을 막는다. */
    public void requireDeliverableEditable(Deliverable deliverable) {
        if (deliverable==null) return;
        em.lock(deliverable,LockModeType.PESSIMISTIC_WRITE);
        if (em.find(DeliverableConfirmation.class,deliverable.getId())!=null)
            throw conflict("최종 확정된 제출물의 업무는 변경할 수 없습니다.");
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
        Task task=find(project,taskId); List<Check> checks=new ArrayList<>(checks(task));
        checks.add(new Check("REVIEWER","담당자와 요청자를 제외한 확인 가능한 팀원",hasReviewer(task,user)));
        boolean passed=checks.stream().allMatch(Check::passed), leader="LEADER".equals(member.getRole());
        boolean eligible=leader || ("MEMBER".equals(member.getRole()) && assignedOrCreator(task,user));
        boolean legacy="DONE".equals(task.getStatus()) && task.getCompletionApprovedAt()==null;
        boolean editable=!confirmed(task);
        boolean reviewer=editable && !"OBSERVER".equals(member.getRole()) && independentReviewer(task,user)
                && task.getCompletionRequestedAt()!=null;
        return new Completion(response(task),checks,editable && passed && eligible && task.getCompletionRequestedAt()==null
                && (startable(task) || legacy),passed && reviewer,reviewer,
                !"OBSERVER".equals(member.getRole()) && pending(task) && editable
                && (leader || Objects.equals(task.getCompletionRequestedBy().getId(),user.getId())));
    }

    @Transactional
    public TaskResponse request(UUID projectId,UUID taskId,User user) {
        Project project=access.getProject(projectId); ProjectMember member=access.requireContributor(project,user); lock(project);
        Task task=find(project,taskId);
        if (!"LEADER".equals(member.getRole()) && !assignedOrCreator(task,user)) throw new ForbiddenException("담당자 또는 팀장만 완료를 요청할 수 있습니다.");
        requireDeliverableEditable(task.getDeliverable());
        if (task.getCompletionRequestedAt()!=null) return response(task);
        boolean legacy="DONE".equals(task.getStatus()) && task.getCompletionApprovedAt()==null;
        if (!startable(task) && !legacy) throw conflict("진행 중인 업무에서 완료를 요청하세요.");
        requireChecks(task);
        if (!hasReviewer(task,user)) throw conflict("담당자와 요청자를 제외한 확인 가능한 팀원이 없습니다. 담당자를 조정해 주세요.");
        task.setStatus("IN_PROGRESS"); task.setCompletedAt(null);
        task.setCompletionRequestedAt(now()); task.setCompletionRequestedBy(user); task.setCompletionRejectionReason(null);
        save(task); record(task,user,"TASK_COMPLETION_REQUEST",null);
        // 받는 사람은 확인 버튼을 실제로 쓸 수 있는 사람(view의 canReview와 같은 판정)으로 한정한다.
        notifications.completionRequested(task,user,notifications.contributors(project).stream().filter(u->independentReviewer(task,u)).toList());
        return response(task);
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
        requireDeliverableEditable(task.getDeliverable());
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
            OffsetDateTime requestedAt=task.getCompletionRequestedAt(); User requester=task.getCompletionRequestedBy();
            task.setCompletionRequestedAt(null); task.setCompletionRequestedBy(null); task.setCompletionRejectionReason(message);
            save(task); record(task,reviewer,"TASK_COMPLETION_REJECT",message);
            List<User> recipients=new ArrayList<>(performers(task)); recipients.add(requester);
            notifications.revisionRequested(task,requestedAt,reviewer,recipients);
        }
        return response(task);
    }

    @Transactional
    public TaskResponse cancel(UUID projectId,UUID taskId,String reason,User user) {
        Project project=access.getProject(projectId); ProjectMember member=access.requireContributor(project,user); lock(project);
        Task task=find(project,taskId);
        if (!pending(task)) throw conflict("취소할 완료 확인 요청이 없습니다.");
        if (!"LEADER".equals(member.getRole()) && (task.getCompletionRequestedBy()==null
                || !Objects.equals(task.getCompletionRequestedBy().getId(),user.getId())))
            throw new ForbiddenException("요청자 또는 팀장만 완료 확인 요청을 취소할 수 있습니다.");
        if (task.getDeliverable()!=null) em.lock(task.getDeliverable(),LockModeType.PESSIMISTIC_WRITE);
        if (confirmed(task)) throw conflict("최종 확정된 제출물의 업무는 요청을 취소할 수 없습니다.");
        String message=reason==null?"":reason.trim();
        if(message.isEmpty()||message.length()>2000) throw bad("취소 사유를 2,000자 이내로 적어주세요.");
        record(task,user,"TASK_COMPLETION_CANCEL",message);
        task.setCompletionRequestedAt(null); task.setCompletionRequestedBy(null);
        save(task); return response(task);
    }
    // 결과 파일을 먼저 올린 '할 일' 업무도 조건(선행 업무 포함)을 충족하면 바로 확인 요청할 수 있다. 요청 시 진행 중으로 바뀐다.
    private boolean startable(Task task) { return "IN_PROGRESS".equals(task.getStatus()) || "TODO".equals(task.getStatus()); }
    private boolean pending(Task task) {
        return "IN_PROGRESS".equals(task.getStatus()) && task.getCompletionRequestedAt()!=null
                && task.getCompletionRequestedBy()!=null && task.getCompletionApprovedAt()==null;
    }
    private boolean confirmed(Task task) {
        return task.getDeliverable()!=null && em.find(DeliverableConfirmation.class,task.getDeliverable().getId())!=null;
    }
    private boolean hasReviewer(Task task,User actor) {
        User requester=task.getCompletionRequestedBy()==null?actor:task.getCompletionRequestedBy();
        return notifications.contributors(task.getProject()).stream()
                .anyMatch(u->!Objects.equals(u.getId(),requester.getId()) && independentReviewer(task,u));
    }

    @Transactional
    public TaskResponse reopen(UUID projectId,UUID taskId,String reason,User leader) {
        Project project=access.getProject(projectId); access.requireLeader(project,leader); lock(project); Task task=find(project,taskId);
        requireDeliverableEditable(task.getDeliverable());
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
        // 완료 기준은 팀원에게 전달하는 선택 메모이며 결과 확인 요청을 막지 않는다.
        return List.of(new Check("PREDECESSOR",before==null?"선행 업무 없음":"선행 업무 승인 완료: "+before.getTitle(),before==null || ("DONE".equals(before.getStatus())&&before.getCompletionApprovedAt()!=null)),
                new Check("ATTACHMENT",task.isAttachmentRequired()?"이 업무에 파일 첨부":"파일 첨부 선택",!task.isAttachmentRequired()||files.existsByTask(task)));
    }
    private void requireChecks(Task task) { checks(task).stream().filter(c->!c.passed()).findFirst().ifPresent(c->{throw conflict(c.label()+" 조건을 충족해야 합니다.");}); }
    private boolean assignedOrCreator(Task task,User user) {
        var list=assignees.findByTask(task);
        return list.isEmpty()?task.getCreatedBy().getId().equals(user.getId()):list.stream().anyMatch(a->a.getUser().getId().equals(user.getId()));
    }
    private List<User> performers(Task task) {
        var list=assignees.findByTask(task);
        return list.isEmpty()?List.of(task.getCreatedBy()):list.stream().map(TaskAssignee::getUser).toList();
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
        metadata.put("requesterId",task.getCompletionRequestedBy()==null?null:task.getCompletionRequestedBy().getId());
        metadata.put("requestedAt",task.getCompletionRequestedAt()==null?null:task.getCompletionRequestedAt().toString());
        metadata.put("predecessorId",task.getPredecessor()==null?null:task.getPredecessor().getId());
        metadata.put("fileIds",files.findByTaskOrderByCreatedAtDesc(task).stream().map(f->f.getFile().getId()).toList()); metadata.put("reason",reason);
        try { logs.record(task.getProject(),user,event,mapper.writeValueAsString(metadata)); }
        catch(JsonProcessingException e) { throw new IllegalStateException("업무 승인 기록을 저장하지 못했습니다.",e); }
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
}
