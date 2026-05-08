package com.blackbox.dto;

public record UpdateDiscordSettingsRequest(
        String  webhookUrl,
        Boolean notifyTaskAssign,
        Boolean notifyTaskDone,
        Boolean notifyMeetingCreate,
        Boolean notifyMeetingReminder,
        Boolean notifyAlert
) {}
