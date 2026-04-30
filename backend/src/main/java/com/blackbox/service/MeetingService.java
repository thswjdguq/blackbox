package com.blackbox.service;

import com.blackbox.dto.*;
import com.blackbox.entity.*;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
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

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final ProjectAccessChecker accessChecker;
    private final ActivityLogService activityLogService;
    private final AlertService alertService;
    private final EntityManager entityManager;

    public MeetingService(MeetingRepository meetingRepository,
                          MeetingAttendeeRepository meetingAttendeeRepository,
                          ProjectMemberRepository projectMemberRepository,
                          TaskRepository taskRepository,
                          TaskService taskService,
                          ProjectAccessChecker accessChecker,
                          ActivityLogService activityLogService,
                          AlertService alertService,
                          EntityManager entityManager) {
        this.meetingRepository = meetingRepository;
        this.meetingAttendeeRepository = meetingAttendeeRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.accessChecker = accessChecker;
        this.activityLogService = activityLogService;
        this.alertService = alertService;
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
        meeting.setCheckinCode(generateUniqueCheckinCode());
        meeting.setCreatedBy(creator);
        meetingRepository.save(meeting);
        entityManager.flush();
        entityManager.refresh(meeting);

        // 모든 프로젝트 멤버를 attendee로 등록 (생성자는 자동 체크인)
        List<ProjectMember> members = projectMemberRepository.findByProject(project);
        for (ProjectMember pm : members) {
            MeetingAttendee attendee = new MeetingAttendee();
            attendee.setMeeting(meeting);
            attendee.setUser(pm.getUser());
            boolean isCreator = pm.getUser().getId().equals(creator.getId());
            attendee.setCheckedIn(isCreator);
            if (isCreator) attendee.setCheckedAt(OffsetDateTime.now());
            meetingAttendeeRepository.save(attendee);
        }

        activityLogService.record(project, creator, "MEETING_ATTEND",
                "{\"meetingId\":\"" + meeting.getId() + "\",\"title\":\"" + escapeJson(meeting.getTitle()) + "\"}");

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
        accessChecker.requireMember(project, user);
        Meeting meeting = findMeeting(meetingId, project);

        if (req.title() != null)       meeting.setTitle(req.title());
        if (req.meetingDate() != null) meeting.setMeetingDate(req.meetingDate());
        if (req.purpose() != null)     meeting.setPurpose(req.purpose());
        if (req.notes() != null)       meeting.setNotes(req.notes());
        if (req.decisions() != null)   meeting.setDecisions(req.decisions());
        meetingRepository.save(meeting);

        return MeetingResponse.from(meeting);
    }

    @Transactional
    public void deleteMeeting(UUID projectId, UUID meetingId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireLeader(project, user);
        meetingRepository.delete(findMeeting(meetingId, project));
    }

    // ── 체크인 코드 재생성 ──────────────────────────────────────────────────

    @Transactional
    public MeetingResponse regenerateCheckinCode(UUID projectId, UUID meetingId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireLeader(project, user);
        Meeting meeting = findMeeting(meetingId, project);
        meeting.setCheckinCode(generateUniqueCheckinCode());
        meetingRepository.save(meeting);
        return MeetingResponse.from(meeting);
    }

    // ── 체크인 ─────────────────────────────────────────────────────────────

    @Transactional
    public AttendeeResponse checkin(CheckinRequest req, User user) {
        Meeting meeting = meetingRepository.findByCheckinCode(req.checkinCode())
                .orElseThrow(() -> new NotFoundException("유효하지 않은 체크인 코드입니다"));

        // 이미 attendee로 등록된 경우 그 레코드를 사용, 없으면 새로 추가
        MeetingAttendee attendee = meetingAttendeeRepository
                .findByMeetingAndUser(meeting, user)
                .orElseGet(() -> {
                    MeetingAttendee a = new MeetingAttendee();
                    a.setMeeting(meeting);
                    a.setUser(user);
                    return a;
                });

        if (attendee.isCheckedInToday()) {
            throw new ForbiddenException("오늘은 이미 체크인되었습니다");
        }

        attendee.setCheckedIn(true);
        attendee.setCheckedAt(OffsetDateTime.now());
        attendee.setCheckedInDate(LocalDate.now());
        meetingAttendeeRepository.save(attendee);

        // 프로젝트 멤버가 아니면 MEMBER로 자동 추가 — 이후 프로젝트 전체 접근 가능
        Project project = meeting.getProject();
        if (!projectMemberRepository.existsByProjectAndUser(project, user)) {
            ProjectMember newMember = new ProjectMember();
            newMember.setProject(project);
            newMember.setUser(user);
            newMember.setRole("MEMBER");
            newMember.setConsentPlatform(true);
            newMember.setConsentGithub(false);
            newMember.setConsentDrive(false);
            newMember.setConsentAiAnalysis(false);
            projectMemberRepository.save(newMember);

            // 기존 미등록 회의들에 attendee로 추가 (체크인 false)
            meetingRepository.findByProjectOrderByMeetingDateDesc(project).forEach(m -> {
                if (meetingAttendeeRepository.findByMeetingAndUser(m, user).isEmpty()) {
                    MeetingAttendee a = new MeetingAttendee();
                    a.setMeeting(m);
                    a.setUser(user);
                    a.setCheckedIn(false);
                    meetingAttendeeRepository.save(a);
                }
            });
        }

        activityLogService.record(project, user, "CHECKIN",
                "{\"meetingId\":\"" + meeting.getId() + "\",\"title\":\"" + escapeJson(meeting.getTitle()) + "\",\"checkedInAt\":\"" + attendee.getCheckedAt() + "\"}");

        alertService.reevaluate(user, project);

        return AttendeeResponse.from(attendee);
    }

    // ── 내 체크인 내역 (비멤버도 조회 가능) ───────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttendeeResponse> myCheckins(User user) {
        return meetingAttendeeRepository.findByUserAndCheckedInTrueOrderByCheckedAtDesc(user)
                .stream().map(AttendeeResponse::from).toList();
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
                req.title(), description, req.priority(), req.tag(), req.dueDate(), req.assigneeIds(), null
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
    public void saveNotionInfo(UUID projectId, UUID meetingId, String pageUrl, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        Meeting meeting = findMeeting(meetingId, project);
        meeting.setNotionPageId(pageUrl);
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

    private String generateUniqueCheckinCode() {
        String code;
        do {
            code = generateCode();
        } while (meetingRepository.existsByCheckinCode(code));
        return code;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
