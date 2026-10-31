package com.blackbox.service;

import com.blackbox.dto.*;
import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MeetingService {


    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final ProjectAccessChecker accessChecker;
    private final ActivityLogService activityLogService;
    private final AlertService alertService;
    private final DiscordNotificationService discordService;
    private final NotionService notionService;
    private final EntityManager entityManager;

    public MeetingService(MeetingRepository meetingRepository,
                          MeetingAttendeeRepository meetingAttendeeRepository,
                          ProjectMemberRepository projectMemberRepository,
                          TaskRepository taskRepository,
                          TaskService taskService,
                          ProjectAccessChecker accessChecker,
                          ActivityLogService activityLogService,
                          AlertService alertService,
                          DiscordNotificationService discordService,
                          NotionService notionService,
                          EntityManager entityManager) {
        this.meetingRepository = meetingRepository;
        this.meetingAttendeeRepository = meetingAttendeeRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.accessChecker = accessChecker;
        this.activityLogService = activityLogService;
        this.alertService = alertService;
        this.discordService = discordService;
        this.notionService = notionService;
        this.entityManager = entityManager;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public MeetingResponse createMeeting(UUID projectId, CreateMeetingRequest req, User creator) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireLeader(project, creator);

        Meeting meeting = new Meeting();
        meeting.setProject(project);
        meeting.setTitle(req.title());
        meeting.setMeetingDate(req.meetingDate());
        meeting.setPurpose(req.purpose());
        configureAttendance(meeting, req.attendanceGraceMinutes(), req.attendanceWindowMinutes());
        meeting.setCreatedBy(creator);
        meetingRepository.save(meeting);
        entityManager.flush();
        entityManager.refresh(meeting);

        // 팀장·팀원을 참석 대상에 등록한다. 회의 생성은 실제 참석 기록이 아니다.
        List<ProjectMember> members = projectMemberRepository.findByProject(project);
        for (ProjectMember pm : members) {
            if ("OBSERVER".equals(pm.getRole())) continue;
            MeetingAttendee attendee = new MeetingAttendee();
            attendee.setMeeting(meeting);
            attendee.setUser(pm.getUser());
            attendee.setCheckedIn(false);
            meetingAttendeeRepository.save(attendee);
        }

        activityLogService.record(project, creator, "MEETING_CREATE",
                "{\"meetingId\":\"" + meeting.getId() + "\",\"title\":\"" + escapeJson(meeting.getTitle()) + "\"}");

        // 회의 생성 Discord 알림
        List<String> attendeeNames = members.stream()
                .map(pm -> pm.getUser().getName()).toList();
        discordService.notifyMeetingCreated(meeting, attendeeNames, project);

        return MeetingResponse.from(meeting);
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> listMeetings(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        return meetingRepository.findByProjectOrderByMeetingDateDesc(project).stream()
                .map(MeetingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(UUID projectId, UUID meetingId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        return MeetingResponse.from(findMeeting(meetingId, project));
    }

    @Transactional
    public MeetingResponse updateMeeting(UUID projectId, UUID meetingId,
                                         UpdateMeetingRequest req, User user) {
        Project project = accessChecker.getProject(projectId);
        // 회의록 편집 API도 관찰자의 읽기 전용 권한을 유지한다.
        accessChecker.requireContributor(project, user);
        Meeting meeting = findMeeting(meetingId, project);

        if (req.meetingDate() != null || req.attendanceGraceMinutes() != null || req.attendanceWindowMinutes() != null) {
            accessChecker.requireLeader(project, user);
            if (meetingAttendeeRepository.findByMeeting(meeting).stream().anyMatch(a -> a.getAttendanceStatus() != null || a.isCheckedIn())) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "?? ??? ?? ??? ????? ??? ??? ? ????.");
            }
            configureAttendance(meeting, req.attendanceGraceMinutes(), req.attendanceWindowMinutes());
        }
        if (req.title() != null)       meeting.setTitle(req.title());
        if (req.meetingDate() != null) meeting.setMeetingDate(req.meetingDate());
        if (req.purpose() != null)     meeting.setPurpose(req.purpose());

        // notes / decisions: null = 변경 없음 (skip)
        //                    빈 문자열("") = null로 저장 (내용 제거 의도)
        //                    실제 텍스트 = 그대로 저장
        if (req.notes() != null)
            meeting.setNotes(req.notes().isBlank() ? null : req.notes());
        if (req.decisions() != null)
            meeting.setDecisions(req.decisions().isBlank() ? null : req.decisions());

        meetingRepository.save(meeting);

        return MeetingResponse.from(meeting);
    }

    @Transactional
    public void deleteMeeting(UUID projectId, UUID meetingId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireLeader(project, user);
        Meeting meeting = findMeeting(meetingId, project);

        // Notion 페이지 아카이브 (실패해도 DB 삭제는 진행)
        String notionPageId = meeting.getNotionPageId();
        if (notionPageId != null && !notionPageId.isBlank()
                && !notionPageId.startsWith("http")
                && notionService.isConfigured()) {
            try {
                notionService.archivePage(notionPageId);
            } catch (Exception e) {
                // 로그는 NotionService 내부에서 처리
            }
        }

        meetingRepository.delete(meeting);
    }

    // ── 참석자 목록 ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttendeeResponse> listAttendees(UUID projectId, UUID meetingId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        Meeting meeting = findMeeting(meetingId, project);
        return meetingAttendeeRepository.findByMeeting(meeting).stream()
                .map(AttendeeResponse::from)
                .toList();
    }

    // ── 액션 아이템 → 태스크 생성 ──────────────────────────────────────────

    @Transactional
    public TaskResponse createActionItem(UUID projectId, UUID meetingId,
                                         CreateTaskRequest req, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        Meeting meeting = findMeeting(meetingId, project);

        // 회의 컨텍스트를 description에 append
        String description = (req.description() != null ? req.description() : "")
                + "\n[Action item from meeting: " + (meeting.getTitle() != null ? meeting.getTitle() : meeting.getId()) + "]";

        CreateTaskRequest enriched = new CreateTaskRequest(
                req.title(), description, req.priority(), req.tag(), req.dueDate(), req.assigneeIds(), null,
                req.deliverableId(), req.requirementId(), req.completionCriteria()
        );
        return taskService.createTask(projectId, enriched, user);
    }

    // ── AI 요약 저장 ──────────────────────────────────────────────────────

    @Transactional
    public void saveAiSummary(UUID projectId, UUID meetingId, String summary, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        Meeting meeting = findMeeting(meetingId, project);
        meeting.setAiSummary(summary);
        meetingRepository.save(meeting);
    }

    // ── Notion 동기화 정보 저장 ───────────────────────────────────────────

    @Transactional
    public void saveNotionInfo(UUID projectId, UUID meetingId, String pageId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        Meeting meeting = findMeeting(meetingId, project);
        meeting.setNotionPageId(pageId);  // URL이 아닌 실제 Notion page ID 저장
        meeting.setNotionSyncedAt(OffsetDateTime.now());
        meetingRepository.save(meeting);
    }

    // ── 컨트롤러용 엔티티 직접 반환 (Notion 내보내기 등) ────────────────────

    @Transactional(readOnly = true)
    public Meeting getRawMeeting(UUID projectId, UUID meetingId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        return findMeeting(meetingId, project);
    }

    // ── 회의 시간 추천 ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MeetingRecommendResponse recommendMeetingTime(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);

        List<Meeting> meetings = meetingRepository.findByProjectOrderByMeetingDateDesc(project);
        List<Task> tasks = taskRepository.findByProjectOrderByCreatedAtDesc(project);

        // 미완료 태스크 중 가장 빠른 마감일
        LocalDate nearestDeadline = tasks.stream()
                .filter(t -> t.getDueDate() != null && !"DONE".equals(t.getStatus()))
                .map(Task::getDueDate)
                .filter(d -> !d.isBefore(LocalDate.now()))
                .min(Comparator.naturalOrder())
                .orElse(null);

        OffsetDateTime suggested;
        String reason;

        if (meetings.isEmpty()) {
            suggested = nextWeekday(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
            reason = "아직 회의 이력이 없습니다. 빠른 시일 내 첫 팀 미팅을 권장합니다.";
        } else {
            Meeting last = meetings.get(0);
            OffsetDateTime lastDate = last.getMeetingDate() != null
                    ? last.getMeetingDate() : OffsetDateTime.now(ZoneOffset.UTC).minusDays(7);

            long intervalDays = 7;
            if (meetings.size() >= 2) {
                Meeting prev = meetings.get(1);
                OffsetDateTime prevDate = prev.getMeetingDate() != null
                        ? prev.getMeetingDate() : lastDate.minusDays(7);
                long diff = Math.abs(ChronoUnit.DAYS.between(prevDate, lastDate));
                intervalDays = Math.max(3, Math.min(diff, 14));
            }

            suggested = nextWeekday(lastDate.plusDays(intervalDays));
            reason = String.format("최근 회의 주기 (~%d일) 기준 다음 권장 시간입니다.", intervalDays);
        }

        // 마감일이 더 촉박하면 조정
        if (nearestDeadline != null) {
            OffsetDateTime deadlineDt = nearestDeadline.minusDays(2)
                    .atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
            if (deadlineDt.isAfter(OffsetDateTime.now(ZoneOffset.UTC))
                    && deadlineDt.isBefore(suggested)) {
                suggested = nextWeekday(deadlineDt);
                reason = String.format(
                        "태스크 마감일(%s)이 다가옵니다. 마감 전 점검 회의를 권장합니다.", nearestDeadline);
            }
        }

        // 현재보다 과거면 내일로
        if (!suggested.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            suggested = nextWeekday(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        }

        return new MeetingRecommendResponse(suggested.toString(), reason);
    }

    private OffsetDateTime nextWeekday(OffsetDateTime dt) {
        while (dt.getDayOfWeek() == DayOfWeek.SATURDAY || dt.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dt = dt.plusDays(1);
        }
        return dt.withHour(14).withMinute(0).withSecond(0).withNano(0);
    }

    // ── internal ──────────────────────────────────────────────────────────

    private Meeting findMeeting(UUID meetingId, Project project) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException("회의를 찾을 수 없습니다"));
        if (!meeting.getProject().getId().equals(project.getId())) {
            throw new NotFoundException("회의를 찾을 수 없습니다");
        }
        return meeting;
    }

    private void configureAttendance(Meeting meeting, Integer grace, Integer window) {
        int g = grace == null ? meeting.getAttendanceGraceMinutes() : grace;
        int w = window == null ? meeting.getAttendanceWindowMinutes() : window;
        if (g < 0 || g > 120 || w <= g || w > 480) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "?? ??? 0~120?, ?? ??? ? ?? 480? ??? ?????.");
        }
        meeting.setAttendanceGraceMinutes(g); meeting.setAttendanceWindowMinutes(w);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
