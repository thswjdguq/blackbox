package com.blackbox.service;

import com.blackbox.dto.ReviewCommentDtos.*;
import com.blackbox.entity.*;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class ReviewCommentService {
    private final ReviewCommentRepository comments;
    private final ReviewRoundRepository rounds;
    private final DeliverableRepository deliverables;
    private final ProjectAccessChecker access;
    private final ReviewRoundReader reviews;
    private final ActivityLogService activity;
    private final ObjectMapper json;
    private final ReviewFeedbackNotifications notifications;

    @Transactional(readOnly = true)
    public List<Response> list(UUID projectId, UUID deliverableId, UUID reviewId, User user) {
        access.requireMember(access.getProject(projectId), user);
        reviews.get(projectId, deliverableId, reviewId);
        return comments.findByProjectIdAndDeliverableIdAndReviewIdOrderByCreatedAtAscIdAsc(projectId, deliverableId, reviewId)
                .stream().map(Response::from).toList();
    }

    public Response create(UUID projectId, UUID deliverableId, UUID reviewId, CreateRequest req, User user) {
        Project project = access.getProject(projectId);
        access.requireContributor(project, user);
        var view = writable(projectId, deliverableId, reviewId);
        if (!view.latest()) throw conflict("최신 검토 회차에만 피드백을 작성할 수 있습니다");
        String content = text(req.content(), 2000, true);
        ReviewComment c = new ReviewComment();
        c.setProject(project);
        c.setDeliverable(deliverables.findByIdAndProject(deliverableId, project)
                .orElseThrow(() -> new NotFoundException("제출물을 찾을 수 없습니다")));
        c.setReview(rounds.findById(reviewId).orElseThrow(() -> new NotFoundException("검토 회차를 찾을 수 없습니다")));
        c.setAuthor(user); c.setContent(content);
        c = comments.save(c);
        record(c, user, "FEEDBACK_CREATE", Map.of("commentId", c.getId().toString()));
        notifications.created(c);
        return Response.from(c);
    }

    public Response resolve(UUID projectId, UUID deliverableId, UUID reviewId, UUID commentId, ResolutionRequest req, User user) {
        access.requireContributor(access.getProject(projectId), user);
        writable(projectId, deliverableId, reviewId);
        String reason = text(req.reason(), 1000, false);
        ReviewComment c = find(projectId, deliverableId, reviewId, commentId);
        // 중복 요청도 권한과 잠금을 먼저 검사하지만 최초 해결 기록은 덮어쓰지 않는다.
        if (c.getResolvedAt() == null) {
            c.setResolvedBy(user);
            c.setResolvedAt(java.time.OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            c.setResolutionReason(reason);
            record(c, user, "FEEDBACK_RESOLVE", Map.of("commentId", c.getId().toString()));
            notifications.resolved(c,user);
        }
        return Response.from(c);
    }

    public Response reopen(UUID projectId, UUID deliverableId, UUID reviewId, UUID commentId, User user) {
        access.requireContributor(access.getProject(projectId), user);
        writable(projectId, deliverableId, reviewId);
        ReviewComment c = find(projectId, deliverableId, reviewId, commentId);
        if (c.getResolvedAt() != null) {
            // 다시 열면 현재 해결 정보는 비우되 이전 해결 사유·담당자는 감사 기록에 남긴다.
            Map<String, Object> previous = new LinkedHashMap<>();
            previous.put("commentId", c.getId()); previous.put("resolvedBy", c.getResolvedBy().getId());
            previous.put("resolvedAt", c.getResolvedAt().toString()); previous.put("reason", c.getResolutionReason());
            record(c, user, "FEEDBACK_REOPEN", previous);
            notifications.reopened(c,user);
            c.setResolvedBy(null); c.setResolvedAt(null); c.setResolutionReason(null);
        }
        return Response.from(c);
    }

    private ReviewRoundReader.ReviewRoundView writable(UUID p, UUID d, UUID r) {
        // 승인과 코멘트 명령이 동일 제출물 잠금을 사용해야 검사와 저장 사이의 경쟁을 막는다.
        var view = reviews.lockForWrite(p, d, r);
        if ("APPROVED".equals(view.decision())) throw conflict("승인된 검토 회차는 변경할 수 없습니다");
        if (Set.of("CONFIRMED", "SUBMITTED").contains(view.deliverableStatus())) throw conflict("확정된 제출물은 변경할 수 없습니다");
        return view;
    }

    private ReviewComment find(UUID p, UUID d, UUID r, UUID id) {
        return comments.findByIdAndProjectIdAndDeliverableIdAndReviewId(id, p, d, r)
                .orElseThrow(() -> new NotFoundException("피드백을 찾을 수 없습니다"));
    }
    private static String text(String value, int max, boolean required) {
        String trimmed = value == null ? "" : value.strip();
        if ((required && trimmed.isEmpty()) || trimmed.length() > max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "입력 내용의 길이가 올바르지 않습니다");
        return trimmed.isEmpty() ? null : trimmed;
    }
    private void record(ReviewComment c, User user, String action, Map<String, ?> metadata) {
        try { activity.record(c.getProject(), user, action, json.writeValueAsString(metadata)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("피드백 활동 기록 직렬화 실패", e); }
    }
    private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
}
