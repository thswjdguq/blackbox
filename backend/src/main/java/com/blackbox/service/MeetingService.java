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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MeetingService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskService taskService;
    private final ProjectAccessChecker accessChecker;
    private final ActivityLogService activityLogService;
    private final EntityManager entityManager;

    public MeetingService(MeetingRepository meetingRepository,
                          MeetingAttendeeRepository meetingAttendeeRepository,
                          ProjectMemberRepository projectMemberRepository,
                          TaskService taskService,
                          ProjectAccessChecker accessChecker,
                          ActivityLogService activityLogService,
                          EntityManager entityManager) {
        this.meetingRepository = meetingRepository;
        this.meetingAttendeeRepository = meetingAttendeeRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.taskService = taskService;
        this.accessChecker = accessChecker;
        this.activityLogService = activityLogService;
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

        // 모든 프로젝트 멤버를 attendee로 등록 (checked_in=false)
        List<ProjectMember> members = projectMemberRepository.findByProject(project);
        for (ProjectMember pm : members) {
            MeetingAttendee attendee = new MeetingAttendee();
            attendee.setMeeting(meeting);
            attendee.setUser(pm.getUser());
            attendee.setCheckedIn(false);
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
        accessChecker.requireLeader(project, user);
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

        if (attendee.isCheckedIn()) {
            throw new ForbiddenException("이미 체크인되었습니다");
        }

        attendee.setCheckedIn(true);
        attendee.setCheckedAt(OffsetDateTime.now());
        meetingAttendeeRepository.save(attendee);

        activityLogService.record(meeting.getProject(), user, "CHECKIN",
                "{\"meetingId\":\"" + meeting.getId() + "\",\"title\":\"" + escapeJson(meeting.getTitle()) + "\",\"checkedInAt\":\"" + attendee.getCheckedAt() + "\"}");

        return AttendeeResponse.from(attendee);
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
                req.title(), description, req.priority(), req.tag(), req.dueDate(), req.assigneeIds()
        );
        return taskService.createTask(projectId, enriched, user);
    }

    // ── 컨트롤러용 엔티티 직접 반환 (Notion 내보내기 등) ────────────────────

    @Transactional(readOnly = true)
    public Meeting getRawMeeting(UUID projectId, UUID meetingId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        return findMeeting(meetingId, project);
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
