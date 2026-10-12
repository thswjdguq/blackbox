package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import jakarta.persistence.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskMessageService {
    public record Message(UUID id, UUID authorId, String authorName, String body, String kind,
                          UUID recipientId, String recipientName, UUID parentId, UUID fileId,
                          String fileName, Integer fileVersion, OffsetDateTime answeredAt, OffsetDateTime createdAt) {}
    public record Thread(List<Message> messages, boolean hasMore) {}
    public record InboxItem(String kind, UUID taskId, String taskTitle, UUID messageId, String body,
                            String fromName, OffsetDateTime createdAt) {}
    public record Inbox(List<InboxItem> received, List<InboxItem> sent, List<InboxItem> revisions) {}

    private final TaskRepository tasks;
    private final TaskAssigneeRepository assignees;
    private final TaskMessageRepository messages;
    private final ProjectMemberRepository members;
    private final TaskFileRepository links;
    private final ProjectAccessChecker access;
    private final EntityManager em;

    public TaskMessageService(TaskRepository tasks, TaskAssigneeRepository assignees, TaskMessageRepository messages,
                              ProjectMemberRepository members, TaskFileRepository links, ProjectAccessChecker access, EntityManager em) {
        this.tasks=tasks; this.assignees=assignees; this.messages=messages; this.members=members;
        this.links=links; this.access=access; this.em=em;
    }

    @Transactional(readOnly=true)
    public Thread list(UUID projectId, UUID taskId, int page, User user) {
        Project project=access.getProject(projectId); access.requireMember(project,user);
        if (page<0 || page>1000) throw bad("올바른 대화 페이지를 지정하세요.");
        var slice=messages.findByTaskOrderByCreatedAtDescIdDesc(find(project,taskId), PageRequest.of(page,50));
        var result=new ArrayList<>(slice.getContent().stream().map(this::response).toList());
        Collections.reverse(result);
        return new Thread(result,slice.hasNext());
    }

    @Transactional(readOnly=true)
    public Message get(UUID projectId, UUID taskId, UUID messageId, User user) {
        Project project=access.getProject(projectId); access.requireMember(project,user);
        return response(messages.findByIdAndTask(messageId,find(project,taskId))
                .orElseThrow(() -> new NotFoundException("대화를 찾을 수 없습니다.")));
    }

    @Transactional
    public Message post(UUID projectId, UUID taskId, String body, UUID recipientId, UUID parentId, UUID fileId, User user) {
        Project project=access.getProject(projectId); access.requireContributor(project,user);
        // 요청 답변과 업무 종료를 같은 프로젝트 잠금 안에서 검사한다. 의견 요청은 업무를 동결하지 않는다.
        em.lock(project,LockModeType.PESSIMISTIC_WRITE);
        Task task=find(project,taskId);
        String text=body==null?"":body.trim();
        if (text.isEmpty() || text.length()>2000) throw bad("내용을 2,000자 이내로 작성하세요.");
        if (recipientId!=null && parentId!=null) throw bad("의견 요청과 답글을 동시에 지정할 수 없습니다.");
        TaskMessage message=new TaskMessage();
        message.setProject(project); message.setTask(task); message.setAuthor(user); message.setBody(text);
        message.setCreatedAt(OffsetDateTime.now()); message.setKind("COMMENT");
        if (recipientId!=null) {
            if ("DONE".equals(task.getStatus())) throw conflict("완료된 업무에는 댓글을 남기거나 업무를 재개한 뒤 의견을 요청하세요.");
            if (recipientId.equals(user.getId())) throw bad("의견을 요청할 다른 팀원을 선택하세요.");
            User recipient=members.findByProject(project).stream()
                    .filter(m -> m.getUser().getId().equals(recipientId) && !"OBSERVER".equals(m.getRole()))
                    .map(ProjectMember::getUser).findFirst().orElseThrow(() -> bad("프로젝트의 팀장 또는 팀원을 선택하세요."));
            message.setRecipient(recipient); message.setKind("REQUEST");
        }
        if (parentId!=null) {
            TaskMessage parent=messages.findByIdAndTask(parentId,task).orElseThrow(() -> new NotFoundException("의견 요청을 찾을 수 없습니다."));
            if (!"REQUEST".equals(parent.getKind())) throw bad("의견 요청에 답글을 남겨주세요.");
            message.setParent(parent); message.setKind("REPLY");
            // 대화에는 누구나 참여할 수 있지만 지정 수신자가 답한 경우에만 '응답 완료'로 표시한다.
            if (parent.getRecipient().getId().equals(user.getId()) && parent.getAnsweredAt()==null) {
                parent.setAnsweredAt(message.getCreatedAt()); messages.save(parent);
            }
        }
        if (fileId!=null) {
            FileVault file=links.findByTaskOrderByCreatedAtDesc(task).stream().map(TaskFile::getFile)
                    .filter(f -> f.getId().equals(fileId)).findFirst().orElseThrow(() -> new NotFoundException("업무에 연결된 자료를 찾을 수 없습니다."));
            message.setFile(file);
        }
        messages.saveAndFlush(message);
        return response(message);
    }

    /** 서버가 실제 담당자·요청자 경계로 완료 확인 대상을 고른다. 화면은 이름으로 권한을 추정하지 않는다. */
    @Transactional(readOnly=true)
    public Inbox inbox(UUID projectId, User user) {
        Project project=access.getProject(projectId); ProjectMember member=access.requireMember(project,user);
        var received=new ArrayList<InboxItem>(); var sent=new ArrayList<InboxItem>(); var revisions=new ArrayList<InboxItem>();
        for (TaskMessage m:messages.findByProjectAndKindAndAnsweredAtIsNullOrderByCreatedAtDesc(project,"REQUEST")) {
            InboxItem item=new InboxItem("OPINION",m.getTask().getId(),m.getTask().getTitle(),m.getId(),m.getBody(),m.getAuthor().getName(),m.getCreatedAt());
            if (m.getRecipient().getId().equals(user.getId())) received.add(item);
            if (m.getAuthor().getId().equals(user.getId())) sent.add(item);
        }
        if (!"OBSERVER".equals(member.getRole())) {
            var list=tasks.findByProjectOrderByCreatedAtDesc(project);
            Map<UUID,List<TaskAssignee>> assignments=list.isEmpty()?Map.of():assignees.findByTaskIn(list).stream()
                    .collect(Collectors.groupingBy(a -> a.getTask().getId()));
            for (Task t:list) {
                var people=assignments.getOrDefault(t.getId(),List.of());
                boolean performer=people.isEmpty()?t.getCreatedBy().getId().equals(user.getId()):people.stream().anyMatch(a->a.getUser().getId().equals(user.getId()));
                if (t.getCompletionRequestedAt()!=null) {
                    boolean requester=t.getCompletionRequestedBy().getId().equals(user.getId());
                    InboxItem item=new InboxItem("COMPLETION",t.getId(),t.getTitle(),null,t.getCompletionCriteria(),t.getCompletionRequestedBy().getName(),t.getCompletionRequestedAt());
                    if (!performer && !requester) received.add(item);
                    if (performer || requester) sent.add(item);
                } else if (performer && !"DONE".equals(t.getStatus()) && t.getCompletionRejectionReason()!=null) {
                    revisions.add(new InboxItem("REVISION",t.getId(),t.getTitle(),null,t.getCompletionRejectionReason(),null,t.getUpdatedAt()));
                }
            }
        }
        Comparator<InboxItem> latest=Comparator.comparing(InboxItem::createdAt,Comparator.nullsLast(Comparator.reverseOrder()));
        received.sort(latest); sent.sort(latest); revisions.sort(latest);
        return new Inbox(received,sent,revisions);
    }

    private Task find(Project project, UUID id) { return tasks.findByIdAndProject(id,project).orElseThrow(() -> new NotFoundException("업무를 찾을 수 없습니다.")); }
    private Message response(TaskMessage m) {
        return new Message(m.getId(),m.getAuthor().getId(),m.getAuthor().getName(),m.getBody(),m.getKind(),
                m.getRecipient()==null?null:m.getRecipient().getId(),m.getRecipient()==null?null:m.getRecipient().getName(),
                m.getParent()==null?null:m.getParent().getId(),m.getFile()==null?null:m.getFile().getId(),
                m.getFile()==null?null:m.getFile().getFileName(),m.getFile()==null?null:m.getFile().getVersion(),m.getAnsweredAt(),m.getCreatedAt());
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
