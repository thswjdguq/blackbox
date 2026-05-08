package com.blackbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "discord_settings")
@Getter
@Setter
@NoArgsConstructor
public class DiscordSettings {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "webhook_url", columnDefinition = "TEXT")
    private String webhookUrl;

    @Column(name = "notify_task_assign", nullable = false)
    private boolean notifyTaskAssign = true;

    @Column(name = "notify_task_done", nullable = false)
    private boolean notifyTaskDone = true;

    @Column(name = "notify_meeting_create", nullable = false)
    private boolean notifyMeetingCreate = true;

    @Column(name = "notify_meeting_reminder", nullable = false)
    private boolean notifyMeetingReminder = true;

    @Column(name = "notify_alert", nullable = false)
    private boolean notifyAlert = true;

    public static DiscordSettings defaults(UUID projectId) {
        DiscordSettings s = new DiscordSettings();
        s.setProjectId(projectId);
        return s;
    }
}
