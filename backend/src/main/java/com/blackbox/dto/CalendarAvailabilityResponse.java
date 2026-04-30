package com.blackbox.dto;

import java.util.List;

public record CalendarAvailabilityResponse(
        List<BusySlot> busySlots,
        List<FreeSlot> freeSlots,
        List<MemberStatus> memberStatuses
) {
    public record BusySlot(String start, String end, String memberName) {}
    public record FreeSlot(String start, String end, int durationMinutes) {}
    public record MemberStatus(String userId, String name, boolean connected) {}
}
