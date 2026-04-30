package com.blackbox.dto;

import java.util.List;
import java.util.UUID;

public record CalendarConnectionStatusResponse(
        boolean connected,
        List<MemberCalendarStatus> members
) {
    public record MemberCalendarStatus(UUID userId, String name, String email, boolean connected) {}
}
