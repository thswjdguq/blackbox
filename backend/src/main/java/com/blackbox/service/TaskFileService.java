package com.blackbox.service;

import com.blackbox.dto.FileHistoryResponse;
import com.blackbox.dto.ReviewDtos;
import com.blackbox.entity.*;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class TaskFileService {
    public record Resource(UUID taskId, String taskTitle, FileHistoryResponse file) {}
    private final TaskRepository tasks;
    private final TaskFileRepository links;
    private final FileVaultRepository files;
    private final DeliverableRepository deliverables;
    private final ProjectAccessChecker access;
    private final FileVaultService storage;
    private final ReviewRoundService reviews;
    private final TaskWorkflowService workflow;
    private final NotificationService notifications;
    public TaskFileService(TaskRepository tasks, TaskFileRepository links, FileVaultRepository files,
            DeliverableRepository deliverables, ProjectAccessChecker access, FileVaultService storage, ReviewRoundService reviews,
            TaskWorkflowService workflow, NotificationService notifications) {
        this.tasks = tasks; this.links = links; this.files = files; this.deliverables = deliverables;
        this.access = access; this.storage = storage; this.reviews = reviews; this.workflow = workflow;
        this.notifications = notifications;
    }
    @Transactional(readOnly = true)
    public List<Resource> list(UUID projectId, UUID taskId, User user) {
        Project project = access.getProject(projectId); access.requireMember(project, user);
        return links.findByTaskOrderByCreatedAtDesc(task(project, taskId)).stream().map(this::response).toList();
    }
    @Transactional(readOnly = true)
    public List<Resource> listDeliverable(UUID projectId, UUID deliverableId, User user) {
        Project project = access.getProject(projectId); access.requireMember(project, user);
        Deliverable deliverable = deliverables.findByIdAndProject(deliverableId, project)
                .orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다."));
        return links.findByTask_DeliverableOrderByCreatedAtDesc(deliverable).stream().map(this::response).toList();
    }
    @Transactional
    public Resource upload(UUID projectId, UUID taskId, MultipartFile file, User user) throws IOException {
        Project project = access.getProject(projectId); access.requireContributor(project, user);
        workflow.lock(project);
        Task task = task(project, taskId); workflow.requireEditable(task); workflow.requirePredecessor(task);
        // 기존 파일 저장·해시·버전 정책을 그대로 사용한다. 관계와 파일 메타데이터는 같은 트랜잭션에 저장한다.
        var uploaded = storage.upload(projectId, file, user);
        FileVault stored = files.findByIdAndProject(uploaded.id(), project)
                .orElseThrow(() -> new NotFoundException("등록한 파일을 찾을 수 없습니다."));
        TaskFile link = new TaskFile(); link.setProject(project); link.setTask(task); link.setFile(stored);
        link.setLinkedBy(user); link.setCreatedAt(OffsetDateTime.now()); links.save(link);
        notifications.fileUploaded(task, stored, user);
        return response(link);
    }
    @Transactional
    public ReviewDtos.Round requestReview(UUID projectId, UUID taskId, UUID fileId, User user) {
        Project project = access.getProject(projectId); access.requireContributor(project, user);
        Task task = task(project, taskId);
        FileVault file = files.findByIdAndProject(fileId, project).orElseThrow(() -> new NotFoundException("자료를 찾을 수 없습니다."));
        if (links.findByTaskAndFile(task, file).isEmpty()) throw new NotFoundException("업무에 연결된 자료를 찾을 수 없습니다.");
        if (task.getDeliverable() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "업무를 제출물에 먼저 연결하세요.");
        // 검토는 기존 제출물 회차를 연다. 업무 완료와 제출물 승인 의미를 합치지 않는다.
        return reviews.open(projectId, task.getDeliverable().getId(), new ReviewDtos.OpenRequest(fileId), user);
    }
    private Task task(Project project, UUID id) {
        return tasks.findByIdAndProject(id, project).orElseThrow(() -> new NotFoundException("업무를 찾을 수 없습니다."));
    }
    private Resource response(TaskFile link) { return new Resource(link.getTask().getId(), link.getTask().getTitle(), FileHistoryResponse.from(link.getFile())); }
}
