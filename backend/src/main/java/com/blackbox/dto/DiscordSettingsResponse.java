package com.blackbox.dto;

import com.blackbox.entity.DiscordSettings;

public record DiscordSettingsResponse(
        String  webhookUrl,
        boolean notifyTaskAssign,
        boolean notifyTaskDone,
        boolean notifyMeetingCreate,
        boolean notifyMeetingReminder,
        boolean notifyAlert
) {
    public static DiscordSettingsResponse from(DiscordSettings s) {
        return new DiscordSettingsResponse(
                s.getWebhookUrl(),
                s.isNotifyTaskAssign(),
                s.isNotifyTaskDone(),
                s.isNotifyMeetingCreate(),
                s.isNotifyMeetingReminder(),
                s.isNotifyAlert()
        );
    }
}
