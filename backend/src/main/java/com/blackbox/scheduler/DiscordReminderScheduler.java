package com.blackbox.scheduler;

import com.blackbox.entity.Meeting;
import com.blackbox.repository.MeetingRepository;
import com.blackbox.service.DiscordNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
public class DiscordReminderScheduler {

    private final MeetingRepository meetingRepository;
    private final DiscordNotificationService discordService;
    private final String frontendBaseUrl;

    public DiscordReminderScheduler(MeetingRepository meetingRepository,
                                    DiscordNotificationService discordService,
                                    @Value("${app.frontend-base-url:}") String frontendBaseUrl) {
        this.meetingRepository = meetingRepository;
        this.discordService    = discordService;
        this.frontendBaseUrl   = frontendBaseUrl;
    }

    /** 매 분마다 실행 — 10분 후 시작하는 미알림 회의를 찾아 Discord 알림 발송 */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void sendMeetingReminders() {
        OffsetDateTime now  = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = now.plusMinutes(9);
        OffsetDateTime to   = now.plusMinutes(11);

        List<Meeting> upcoming = meetingRepository.findUpcomingUnnotified(from, to);
        for (Meeting meeting : upcoming) {
            try {
                discordService.notifyMeetingReminder(meeting, meeting.getProject(), frontendBaseUrl);
                meeting.setDiscordNotified(true);
                meetingRepository.save(meeting);
                log.info("Discord 10분 전 알림 발송: meetingId={}", meeting.getId());
            } catch (Exception e) {
                log.warn("Discord 10분 전 알림 실패: meetingId={}, {}", meeting.getId(), e.getMessage());
            }
        }
    }
}
