package com.blackbox.service;

import com.blackbox.dto.*;
import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectAccessChecker accessChecker;
    private final ActivityLogService activityLogService;
    private final NotionService notionService;
    private final AlertService alertService;
    private final DiscordNotificationService discordService;
    private final ScoreService scoreService;
    private final EntityManager entityManager;
    private final DeliverableService deliverableService;
    private final ReviewCommentRepository reviewComments;
    private final DeliverableStatusReader deliverableStatus;

    public TaskService(TaskRepository taskRepository,
                       TaskAssigneeRepository taskAssigneeRepository,
                       ProjectMemberRepository projectMemberRepository,
                       UserRepository userRepository,
                       ProjectAccessChecker accessChecker,
                       ActivityLogService activityLogService,
                       NotionService notionService,
                       AlertService alertService,
                       DiscordNotificationService discordService,
                       ScoreService scoreService,
                       EntityManager entityManager,
                       DeliverableService deliverableService,
                       ReviewCommentRepository reviewComments,
                       DeliverableStatusReader deliverableStatus) {
        this.taskRepository = taskRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.accessChecker = accessChecker;
        this.activityLogService = activityLogService;
        this.notionService = notionService;
        this.alertService = alertService;
        this.discordService = discordService;
        this.scoreService = scoreService;
        this.entityManager = entityManager;
        this.deliverableService = deliverableService;
        this.reviewComments = reviewComments;
        this.deliverableStatus = deliverableStatus;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse createTask(UUID projectId, CreateTaskRequest req, User creator) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireContributor(project, creator);
        requireEditableDeliverable(projectId, req.deliverableId());

        Task task = new Task();
        task.setProject(project);
        task.setTitle(req.title());
        task.setDescription(req.description());
        applyDeliverable(task, project, req.deliverableId(), req.requirementId(), false, false);
        task.setCompletionCriteria(req.completionCriteria() == null ? null : req.completionCriteria().trim());
        task.setStatus(req.status() != null ? req.status() : "TODO");
        task.setPriority(req.priority() != null ? req.priority() : "MEDIUM");
        task.setTag(req.tag());
        task.setDueDate(req.dueDate());
        task.setCreatedBy(creator);
        taskRepository.save(task);
        entityManager.flush();
        entityManager.refresh(task);

        List<TaskAssignee> assignees = setAssigneesInternal(task, req.assigneeIds());

        activityLogService.record(project, creator, "TASK_CREATE",
                "{\"taskId\":\"" + task.getId() + "\",\"title\":\"" + escapeJson(task.getTitle()) + "\",\"priority\":\"" + task.getPriority() + "\"}");

        // 담당자 배정 알림 (생성 시 assignee 있을 때)
        if (!assignees.isEmpty()) {
            discordService.notifyTaskAssigned(task,
                    assignees.stream().map(TaskAssignee::getUser).toList(), project);
        }

        return TaskResponse.from(task, assignees);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks(UUID projectId, String status, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);

        List<Task> tasks = (status != null)
                ? taskRepository.findByProjectAndStatusOrderByCreatedAtDesc(project, status)
                : taskRepository.findByProjectOrderByCreatedAtDesc(project);

        if (tasks.isEmpty()) return List.of();
        // 컬렉션 fetch join은 업무 행을 중복시키므로 담당자를 한 번에 읽어 업무별로 묶는다.
        Map<UUID, List<TaskAssignee>> assignments = taskAssigneeRepository.findByTaskIn(tasks).stream()
                .collect(Collectors.groupingBy(a -> a.getTask().getId()));
        return tasks.stream()
                .map(t -> TaskResponse.from(t, assignments.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID projectId, UUID taskId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        Task task = findTask(taskId, project);
        return TaskResponse.from(task, taskAssigneeRepository.findByTask(task));
    }

    @Transactional
    public TaskResponse updateTask(UUID projectId, UUID taskId, UpdateTaskRequest req, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireContributor(project, user);
        Task task = findTask(taskId, project);

        lockEditableTask(projectId, task, req.deliverableId());
        applyDeliverable(task, project, req.deliverableId(), req.requirementId(),
                Boolean.TRUE.equals(req.clearDeliverable()), Boolean.TRUE.equals(req.clearRequirement()));
        if (req.completionCriteria() != null) task.setCompletionCriteria(req.completionCriteria().trim());

        if (req.title() != null)       task.setTitle(req.title());
        if (req.description() != null) task.setDescription(req.description());
        if (req.priority() != null)    task.setPriority(req.priority());
        if (req.tag() != null)         task.setTag(req.tag());
        if (req.dueDate() != null)     task.setDueDate(req.dueDate());
        taskRepository.save(task);
        entityManager.flush();
        entityManager.refresh(task);

        return TaskResponse.from(task, taskAssigneeRepository.findByTask(task));
    }

    @Transactional
    public void deleteTask(UUID projectId, UUID taskId, User user) {
        Project project = accessChecker.getProject(projectId);
        ProjectMember member = accessChecker.requireContributor(project, user);
        Task task = findTask(taskId, project);

        // 본인 생성 또는 LEADER만 삭제 가능
        boolean isCreator = task.getCreatedBy().getId().equals(user.getId());
        boolean isLeader = "LEADER".equals(member.getRole());
        if (!isCreator && !isLeader) {
            throw new ForbiddenException("태스크를 삭제할 권한이 없습니다");
        }

        lockEditableTask(projectId, task, null);
        taskAssigneeRepository.deleteByTask(task);
        taskRepository.delete(task);
    }

    // ── 상태 변경 ──────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse updateStatus(UUID projectId, UUID taskId,
                                     UpdateTaskStatusRequest req, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireContributor(project, user);
        Task task = findTask(taskId, project);

        lockEditableTask(projectId, task, null);
        String oldStatus = task.getStatus();
        task.setStatus(req.status());

        if ("DONE".equals(req.status()) && !"DONE".equals(oldStatus)) {
            task.setCompletedAt(OffsetDateTime.now());
            taskRepository.save(task);
            entityManager.flush();
            entityManager.refresh(task);
            activityLogService.record(project, user, "TASK_COMPLETE",
                    "{\"taskId\":\"" + task.getId() + "\",\"title\":\"" + escapeJson(task.getTitle()) + "\",\"completedAt\":\"" + task.getCompletedAt() + "\"}");
            alertService.reevaluate(user, project);
            discordService.notifyTaskCompleted(task, user, project);
            UUID projectIdForScore = project.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scoreService.recalculateAsync(projectIdForScore);
                }
            });
        } else {
            if (!"DONE".equals(req.status())) task.setCompletedAt(null);
            taskRepository.save(task);
            entityManager.flush();
            entityManager.refresh(task);
        }

        return TaskResponse.from(task, taskAssigneeRepository.findByTask(task));
    }

    // ── 담당자 배정 ────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse setAssignees(UUID projectId, UUID taskId,
                                     AssignTaskRequest req, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireContributor(project, user);
        Task task = findTask(taskId, project);

        lockEditableTask(projectId, task, null);
        List<TaskAssignee> assignees = setAssigneesInternal(task, req.assigneeIds());
        entityManager.flush();
        entityManager.refresh(task);

        // 담당자 변경 알림 (명시적 배정 액션)
        if (!assignees.isEmpty()) {
            discordService.notifyTaskAssigned(task,
                    assignees.stream().map(TaskAssignee::getUser).toList(), project);
        }

        return TaskResponse.from(task, assignees);
    }

    // ── Notion 동기화 ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public NotionSyncResponse syncToNotion(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);

        List<Task> tasks = taskRepository.findByProjectOrderByCreatedAtDesc(project);

        // 태스크 ID → 담당자 이름 목록
        Map<UUID, List<String>> assigneeMap = new HashMap<>();
        for (Task task : tasks) {
            List<String> names = taskAssigneeRepository.findByTask(task).stream()
                    .map(a -> a.getUser().getName())
                    .toList();
            assigneeMap.put(task.getId(), names);
        }

        String pageUrl = notionService.syncKanban(project.getName(), tasks, assigneeMap);
        return new NotionSyncResponse(pageUrl, tasks.size(),
                tasks.size() + "개 태스크를 Notion에 내보냈습니다");
    }

    // ── internal ──────────────────────────────────────────────────────────

    /**
     * 확정과 쓰기는 같은 제출물 잠금을 사용한다. 이동은 양쪽 ID 순으로 잠가 서로 반대 이동의 교착을 막는다.
     * 그 다음 업무를 잠그고 다시 읽는다. 기다리는 사이 연결이 바뀌었다면 새 제출물 잠금을 뒤늦게
     * 추가하지 않고 409로 재시도를 요청해 잠금 순서와 확정 후 불변성을 함께 유지한다.
     */
    private void lockEditableTask(UUID projectId, Task task, UUID nextDeliverableId) {
        UUID previous = task.getDeliverable() == null ? null : task.getDeliverable().getId();
        java.util.stream.Stream.of(previous, nextDeliverableId).filter(java.util.Objects::nonNull)
                .distinct().sorted().forEach(id -> requireEditableDeliverable(projectId, id));
        try {
            entityManager.refresh(task, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        } catch (jakarta.persistence.EntityNotFoundException missing) {
            throw new NotFoundException("태스크를 찾을 수 없습니다");
        }
        UUID current = task.getDeliverable() == null ? null : task.getDeliverable().getId();
        if (!java.util.Objects.equals(previous, current)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "업무의 제출물 연결이 바뀌었습니다. 새로고침 후 다시 시도해주세요");
        }
    }

    private void requireEditableDeliverable(UUID projectId, UUID deliverableId) {
        if (deliverableId != null && DeliverableStatusReader.isConfirmed(deliverableStatus.lockForWrite(projectId, deliverableId))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    DeliverableStatusReader.CONFIRMED_DETAIL);
        }
    }

    private Task findTask(UUID taskId, Project project) {
        return taskRepository.findByIdAndProject(taskId, project)
                .orElseThrow(() -> new NotFoundException("태스크를 찾을 수 없습니다"));
    }

    private List<TaskAssignee> setAssigneesInternal(Task task, List<UUID> assigneeIds) {
        // 요청자의 쓰기 권한은 호출부에서 확인한다. 후보 자격 오류는 입력 오류이며
        // 기존 배정을 지우기 전에 전부 검증해 실패 요청의 불필요한 쓰기를 피한다.
        List<User> candidates = (assigneeIds == null ? List.<UUID>of() : assigneeIds).stream()
                .distinct()
                .map(uid -> {
                    User candidate = userRepository.findById(uid)
                            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + uid));
                    ProjectMember member = projectMemberRepository.findByProjectAndUser(task.getProject(), candidate)
                            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                                    org.springframework.http.HttpStatus.BAD_REQUEST,
                                    "프로젝트의 팀장·팀원만 담당자로 지정할 수 있습니다"));
                    if (!"LEADER".equals(member.getRole()) && !"MEMBER".equals(member.getRole())) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.BAD_REQUEST,
                                "관찰자는 업무 담당자로 지정할 수 없습니다");
                    }
                    return candidate;
                }).toList();
        taskAssigneeRepository.deleteByTask(task);
        taskAssigneeRepository.flush();

        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return List.of();
        }

        List<TaskAssignee> result = candidates.stream()
                .map(assignee -> {
                    TaskAssignee ta = new TaskAssignee();
                    ta.setTask(task);
                    ta.setUser(assignee);
                    return taskAssigneeRepository.save(ta);
                })
                .toList();

        taskAssigneeRepository.flush();
        return result;
    }

    private void applyDeliverable(Task task, Project project, UUID deliverableId, UUID requirementId,
                                  boolean clearDeliverable, boolean clearRequirement) {
        if ((clearDeliverable && (deliverableId != null || requirementId != null))
                || (clearRequirement && requirementId != null)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "연결과 연결 해제를 동시에 요청할 수 없습니다");
        }
        // V22 복합 외래키 위반에 맡기기 전에 피드백의 원래 제출물 연결을 설명 가능한 409로 보호한다.
        if (task.getId() != null && reviewComments.existsByLinkedTaskId(task.getId())
                && (clearDeliverable || (deliverableId != null && (task.getDeliverable() == null
                || !deliverableId.equals(task.getDeliverable().getId()))))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "피드백에 연결된 업무는 제출물을 변경하거나 연결을 해제할 수 없습니다");
        }
        if (clearDeliverable) {
            task.setDeliverable(null);
            task.setRequirement(null);
            return;
        }
        if (deliverableId != null) {
            Deliverable next = deliverableService.find(project, deliverableId);
            if (task.getDeliverable() == null || !next.getId().equals(task.getDeliverable().getId())) {
                task.setRequirement(null);
            }
            task.setDeliverable(next);
        }
        if (clearRequirement) task.setRequirement(null);
        if (requirementId != null) {
            if (task.getDeliverable() == null) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "요구사항을 연결할 제출물을 선택해주세요");
            }
            task.setRequirement(deliverableService.findRequirement(task.getDeliverable(), requirementId));
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
