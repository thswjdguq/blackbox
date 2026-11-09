package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.repository.ReviewCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** 검토 원문·반영 기록과 동일 트랜잭션에서 알린다. 새 파일은 반영 완료로 판정하지 않는다. */
@Service @RequiredArgsConstructor
public class ReviewFeedbackNotifications {
    private final NotificationService notifications;
    private final ReviewCommentRepository comments;

    public static List<User> recipients(ReviewRound review) {
        var result=new ArrayList<User>();
        if (review.getDeliverable().getOwner()!=null) result.add(review.getDeliverable().getOwner());
        if (review.getFile()!=null) result.add(review.getFile().getUploader());
        if (result.isEmpty()) result.add(review.getOpenedBy());
        return result;
    }
    public void created(ReviewComment c) {
        notifications.reviewEvent("REVIEW_FEEDBACK_RECEIVED","REVIEW_COMMENT:"+c.getId(),c.getReview(),c.getId(),c.getAuthor(),recipients(c.getReview()));
    }
    public void resolved(ReviewComment c, User actor) {
        notifications.reviewEvent("REVIEW_FEEDBACK_RESOLVED","REVIEW_RESOLVED:"+c.getId()+":"+NotificationService.micros(c.getResolvedAt()),
                c.getReview(),c.getId(),actor,List.of(c.getAuthor()));
    }
    public void reopened(ReviewComment c, User actor) {
        var targets=new ArrayList<>(recipients(c.getReview())); targets.add(c.getAuthor());
        notifications.reviewEvent("REVIEW_FEEDBACK_REOPENED","REVIEW_REOPENED:"+c.getId()+":"+NotificationService.micros(c.getResolvedAt()),
                c.getReview(),c.getId(),actor,targets);
    }
    public void versionPosted(ReviewRound r, User actor) {
        var authors=comments.findByDeliverableId(r.getDeliverable().getId()).stream().map(ReviewComment::getAuthor).toList();
        notifications.reviewEvent("REVIEW_VERSION_POSTED","REVIEW_VERSION:"+r.getId(),r,null,actor,authors);
    }
}
