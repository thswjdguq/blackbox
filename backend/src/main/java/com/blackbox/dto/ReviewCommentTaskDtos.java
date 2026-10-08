package com.blackbox.dto;

import java.util.UUID;

public class ReviewCommentTaskDtos {
    public record Request(String title, String completionCriteria) {}
    public record Response(UUID commentId, UUID taskId, boolean created) {}
}
