package com.blackbox.service;

import com.blackbox.entity.*;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class MeetingAttendanceService {
    public record Attendance(UUID userId, String name, String email, String status, OffsetDateTime recordedAt) {}
    public record Summary(OffsetDateTime serverTime, OffsetDateTime opensAt, OffsetDateTime lateAfter,
            OffsetDateTime closesAt, List<Attendance> attendees) {}
    private final MeetingRepository meetings;
    private final MeetingAttendeeRepository attendees;
    private final ProjectMemberRepository members;
    private final ProjectAccessChecker access;
    private final ActivityLogService logs;
    private final Clock clock;

    @Autowired
    public MeetingAttendanceService(MeetingRepository meetings, MeetingAttendeeRepository attendees,
            ProjectMemberRepository members, ProjectAccessChecker access, ActivityLogService logs) {
        this(meetings, attendees, members, access, logs, Clock.systemUTC());
    }
    MeetingAttendanceService(MeetingRepository meetings, MeetingAttendeeRepository attendees,
            ProjectMemberRepository members, ProjectAccessChecker access, ActivityLogService logs, Clock clock) {
        this.meetings = meetings; this.attendees = attendees; this.members = members;
        this.access = access; this.logs = logs; this.clock = clock;
    }

    // 가입·역할 변경과 같은 트랜잭션에서 미마감 회의만 등록한다. 지난 회의에 불참을 소급 생성하지 않는다.
    @Transactional
    public void enroll(Project project, User user) {
        var member = members.findByProjectAndUser(project, user).orElse(null);
        if (member == null || "OBSERVER".equals(member.getRole())) return;
        for (Meeting meeting : meetings.findByProjectOrderByMeetingDateDesc(project)) {
            if (!now().isBefore(closes(meeting))) continue;
            if (attendees.findByMeetingAndUser(meeting, user).isEmpty()) {
                var attendee = new MeetingAttendee(); attendee.setMeeting(meeting); attendee.setUser(user);
                attendees.save(attendee);
            }
        }
    }

    @Transactional(readOnly = true)
    public Summary list(UUID projectId, UUID meetingId, User user) {
        Project project = access.getProject(projectId); access.requireMember(project, user);
        Meeting meeting = find(project, meetingId);
        Set<UUID> eligible = new HashSet<>();
        members.findByProject(project).stream().filter(m -> !"OBSERVER".equals(m.getRole()))
                .forEach(m -> eligible.add(m.getUser().getId()));
        OffsetDateTime time = now();
        return new Summary(time, meeting.getMeetingDate().minusMinutes(10),
                meeting.getMeetingDate().plusMinutes(meeting.getAttendanceGraceMinutes()), closes(meeting),
                attendees.findByMeeting(meeting).stream().filter(a -> eligible.contains(a.getUser().getId()))
                        .map(a -> response(a, time)).toList());
    }

    @Transactional
    public Attendance mark(UUID projectId, UUID meetingId, boolean attending, User user) {
        Project project = access.getProject(projectId); access.requireContributor(project, user);
        Meeting meeting = find(project, meetingId);
        OffsetDateTime time = now();
        var attendee = attendees.findForAttendanceUpdate(meeting, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "이 회의의 참석 대상이 아닙니다."));
        // 재전송으로 참석 시각이 바뀌거나 출석 점수가 중복 기록되지 않게 최초 확인을 보존한다.
        if (attendee.getAttendanceStatus() != null) {
            if (attending == attendee.isCheckedIn()) return response(attendee, time);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 기록했습니다. 정정은 팀장에게 요청하세요.");
        }
        if (!time.isBefore(closes(meeting))) throw new ResponseStatusException(HttpStatus.CONFLICT, "참석 확인 시간이 마감되었습니다.");
        if (attending && time.isBefore(meeting.getMeetingDate().minusMinutes(10))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "회의 시작 10분 전부터 참석 여부를 확인할 수 있습니다.");
        }
        String status = attending ? (time.isAfter(meeting.getMeetingDate().plusMinutes(meeting.getAttendanceGraceMinutes())) ? "LATE" : "PRESENT") : "ABSENT";
        write(attendee, status, time, project, user);
        return response(attendee, time);
    }

    @Transactional
    public Attendance correct(UUID projectId, UUID meetingId, UUID userId, String status, User leader) {
        Project project = access.getProject(projectId); access.requireLeader(project, leader);
        Meeting meeting = find(project, meetingId);
        if (!Set.of("PRESENT", "LATE", "ABSENT").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "참석·지각·불참 중 선택하세요.");
        }
        var target = members.findByProject(project).stream().filter(m -> m.getUser().getId().equals(userId) && !"OBSERVER".equals(m.getRole()))
                .findFirst().orElseThrow(() -> new NotFoundException("참석 대상을 찾을 수 없습니다."));
        var attendee = attendees.findForAttendanceUpdate(meeting, target.getUser())
                .orElseThrow(() -> new NotFoundException("참석 대상을 찾을 수 없습니다."));
        if (now().isBefore(meeting.getMeetingDate())) throw new ResponseStatusException(HttpStatus.CONFLICT, "회의 시작 후에 정정할 수 있습니다.");
        write(attendee, status, now(), project, leader);
        return response(attendee, now());
    }

    private void write(MeetingAttendee attendee, String status, OffsetDateTime time, Project project, User actor) {
        // PostgreSQL TIMESTAMPTZ의 마이크로초 정밀도에 맞춰 최초 응답과 재조회 시각을 동일하게 유지한다.
        time = time.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        attendee.setAttendanceStatus(status); attendee.setCheckedIn(!"ABSENT".equals(status));
        attendee.setCheckedAt(time); attendee.setCheckedInDate(time.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate());
        attendees.save(attendee);
        logs.record(project, actor, "MEETING_ATTENDANCE", "{\"meetingId\":\"" + attendee.getMeeting().getId()
                + "\",\"userId\":\"" + attendee.getUser().getId() + "\",\"status\":\"" + status + "\"}");
    }

    private Attendance response(MeetingAttendee attendee, OffsetDateTime time) {
        String status = attendee.getAttendanceStatus();
        // 구 체크인 기록은 별도 상태로 보여준다. 새 기준으로 과거 참석/지각을 재판정하지 않는다.
        if (status == null) status = attendee.isCheckedIn() ? "LEGACY" : (!time.isBefore(closes(attendee.getMeeting())) ? "ABSENT" : "PENDING");
        return new Attendance(attendee.getUser().getId(), attendee.getUser().getName(), attendee.getUser().getEmail(), status, attendee.getCheckedAt());
    }
    private Meeting find(Project project, UUID id) {
        return meetings.findById(id).filter(m -> m.getProject().getId().equals(project.getId()))
                .orElseThrow(() -> new NotFoundException("회의를 찾을 수 없습니다."));
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private OffsetDateTime closes(Meeting meeting) { return meeting.getMeetingDate().plusMinutes(meeting.getAttendanceWindowMinutes()); }
}
