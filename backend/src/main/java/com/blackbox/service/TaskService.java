package com.blackbox.service;

import com.blackbox.dto.*;
import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final EntityManager entityManager;

    public TaskService(TaskRepository taskRepository,
                       TaskAssigneeRepository taskAssigneeRepository,
                       ProjectMemberRepository projectMemberRepository,
                       UserRepository userRepository,
                       ProjectAccessChecker accessChecker,
                       ActivityLogService activityLogService,
                       NotionService notionService,
                       AlertService alertService,
                       EntityManager entityManager) {
        this.taskRepository = taskRepository;
        this.taskAssigneeRepository = taskAssigneeRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
        this.accessChecker = accessChecker;
        this.activityLogService = activityLogService;
        this.notionService = notionService;
        this.alertService = alertService;
        this.entityManager = entityManager;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse createTask(UUID projectId, CreateTaskRequest req, User creator) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, creator);

        Task task = new Task();
        task.setProject(project);
        task.setTitle(req.title());
        task.setDescription(req.description());
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

        return TaskResponse.from(task, assignees);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks(UUID projectId, String status, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);

        List<Task> tasks = (status != null)
                ? taskRepository.findByProjectAndStatusOrderByCreatedAtDesc(project, status)
                : taskRepository.findByProjectOrderByCreatedAtDesc(project);

        return tasks.stream()
                .map(t -> TaskResponse.from(t, taskAssigneeRepository.findByTask(t)))
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
        accessChecker.requireMember(project, user);
        Task task = findTask(taskId, project);

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
        ProjectMember member = accessChecker.requireMember(project, user);
        Task task = findTask(taskId, project);

        // 본인 생성 또는 LEADER만 삭제 가능
        boolean isCreator = task.getCreatedBy().getId().equals(user.getId());
        boolean isLeader = "LEADER".equals(member.getRole());
        if (!isCreator && !isLeader) {
            throw new ForbiddenException("태스크를 삭제할 권한이 없습니다");
        }

        taskAssigneeRepository.deleteByTask(task);
        taskRepository.delete(task);
    }

    // ── 상태 변경 ──────────────────────────────────────────────────────────

    @Transactional
    public TaskResponse updateStatus(UUID projectId, UUID taskId,
                                     UpdateTaskStatusRequest req, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        Task task = findTask(taskId, project);

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
        accessChecker.requireMember(project, user);
        Task task = findTask(taskId, project);

        List<TaskAssignee> assignees = setAssigneesInternal(task, req.assigneeIds());
        entityManager.flush();
        entityManager.refresh(task);
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

    private Task findTask(UUID taskId, Project project) {
        return taskRepository.findByIdAndProject(taskId, project)
                .orElseThrow(() -> new NotFoundException("태스크를 찾을 수 없습니다"));
    }

    private List<TaskAssignee> setAssigneesInternal(Task task, List<UUID> assigneeIds) {
        taskAssigneeRepository.deleteByTask(task);
        taskAssigneeRepository.flush();

        if (assigneeIds == null || assigneeIds.isEmpty()) {
            return List.of();
        }

        List<TaskAssignee> result = assigneeIds.stream()
                .distinct()
                .map(uid -> {
                    User assignee = userRepository.findById(uid)
                            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다: " + uid));
                    TaskAssignee ta = new TaskAssignee();
                    ta.setTask(task);
                    ta.setUser(assignee);
                    return taskAssigneeRepository.save(ta);
                })
                .toList();

        taskAssigneeRepository.flush();
        return result;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
