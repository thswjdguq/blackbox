package com.blackbox.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** K-54 응답. 모든 키를 항상 내보내고 해당 없는 값은 null로 둔다. */
public final class NotificationDtos {
    private NotificationDtos() {}

    public record Item(UUID id, String type, UUID projectId, String actorName, UUID taskId, String taskTitle,
                       UUID fileId, String fileName, Integer fileVersion, UUID messageId,
                       OffsetDateTime createdAt, OffsetDateTime readAt, boolean targetAvailable) {}

    public record Page(List<Item> items, String nextCursor, long unreadCount) {}

    public record UnreadCount(long unreadCount) {}

    public record ReadResult(UUID id, OffsetDateTime readAt) {}
}
