package com.blackbox.dto;

import com.blackbox.entity.*;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ReviewCommentDtos {
    public record CreateRequest(String content) {}
    public record ResolutionRequest(String reason) {}
    public record Person(UUID userId, String name) {
        static Person from(User user) { return new Person(user.getId(), user.getName()); }
    }
    public record Resolution(Person resolvedBy, OffsetDateTime resolvedAt, String reason) {}
    public record Response(UUID id, UUID reviewId, String content, String status, Person author,
                           OffsetDateTime createdAt, UUID linkedTaskId, Resolution resolution) {
        public static Response from(ReviewComment c) {
            return new Response(c.getId(), c.getReview().getId(), c.getContent(), c.status(), Person.from(c.getAuthor()),
                    c.getCreatedAt(), c.getLinkedTask() == null ? null : c.getLinkedTask().getId(),
                    c.getResolvedAt() == null ? null : new Resolution(Person.from(c.getResolvedBy()), c.getResolvedAt(), c.getResolutionReason()));
        }
    }
}
