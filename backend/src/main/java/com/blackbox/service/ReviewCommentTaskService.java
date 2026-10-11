package com.blackbox.service;

import com.blackbox.dto.CreateTaskRequest;
import com.blackbox.dto.ReviewCommentTaskDtos.*;
import com.blackbox.entity.*;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class ReviewCommentTaskService {
    private final ReviewCommentRepository comments;
    private final TaskRepository tasks;
    private final ProjectAccessChecker access;
    private final ReviewRoundReader reviews;
    private final TaskService taskService;
    private final ActivityLogService activity;

    @Transactional
    public Response convert(UUID projectId, UUID deliverableId, UUID reviewId, UUID commentId, Request request, User user) {
        Project project=access.getProject(projectId);
        access.requireContributor(project,user);
        String title=text(request.title(),255,true), criteria=text(request.completionCriteria(),2000,false);
        var view=reviews.lockForWrite(projectId,deliverableId,reviewId);
        ReviewComment c=comments.findByIdAndProjectIdAndDeliverableIdAndReviewId(commentId,projectId,deliverableId,reviewId)
                .orElseThrow(()->new NotFoundException("피드백을 찾을 수 없습니다"));
        // 기존 연결은 읽기 응답이므로 승인·확정 뒤에도 재시도를 허용한다(K-12/K-20).
        if(c.getLinkedTask()!=null) return new Response(c.getId(),c.getLinkedTask().getId(),false);
        if("APPROVED".equals(view.decision()) || Set.of("CONFIRMED","SUBMITTED").contains(view.deliverableStatus()))
            throw conflict("승인되거나 확정된 제출물에 새 업무를 연결할 수 없습니다");
        if(c.getResolvedAt()!=null) throw conflict("해결된 피드백은 업무로 전환할 수 없습니다");
        // 기존 업무 생성 규칙을 재사용하고 외부 알림 없이 담당자 미설정 TODO로 생성한다.
        var task=taskService.createTask(projectId,new CreateTaskRequest(title,c.getContent(),"MEDIUM",null,null,
                List.of(),"TODO",deliverableId,null,criteria),user);
        c.setLinkedTask(tasks.findById(task.id()).orElseThrow(()->new NotFoundException("업무를 찾을 수 없습니다")));
        activity.record(project,user,"FEEDBACK_TASK_LINK","{\"commentId\":\""+c.getId()+"\",\"taskId\":\""+task.id()+"\"}");
        return new Response(c.getId(),task.id(),true);
    }
    private static String text(String value,int max,boolean required) {
        String normalized=value==null?"":value.strip();
        if((required&&normalized.isEmpty())||normalized.length()>max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"입력 내용의 길이가 올바르지 않습니다");
        return normalized.isEmpty()?null:normalized;
    }
    private static ResponseStatusException conflict(String message) {return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
