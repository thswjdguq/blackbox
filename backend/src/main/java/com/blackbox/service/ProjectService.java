package com.blackbox.service;

import com.blackbox.dto.*;
import com.blackbox.entity.Project;
import com.blackbox.entity.ProjectMember;
import com.blackbox.entity.User;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.ProjectMemberRepository;
import com.blackbox.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessChecker accessChecker;
    private final EntityManager entityManager;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMemberRepository projectMemberRepository,
                          ProjectAccessChecker accessChecker,
                          EntityManager entityManager) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.accessChecker = accessChecker;
        this.entityManager = entityManager;
    }

    // ── 내 프로젝트 목록 ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectResponse> listMyProjects(User user) {
        return projectMemberRepository.findByUser(user).stream()
                .map(m -> {
                    Project p = m.getProject();
                    long count = projectMemberRepository.countByProject(p);
                    return ProjectResponse.from(p, count);
                })
                .collect(Collectors.toList());
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest req, User creator) {
        Project project = new Project();
        project.setName(req.name());
        project.setDescription(req.description());
        project.setCourseName(req.courseName());
        project.setSemester(req.semester());
        project.setStartDate(req.startDate());
        project.setEndDate(req.endDate());
        project.setInviteCode(generateUniqueInviteCode());
        project.setCreatedBy(creator);
        projectRepository.save(project);
        entityManager.flush();
        entityManager.refresh(project);

        ProjectMember leaderMember = new ProjectMember();
        leaderMember.setProject(project);
        leaderMember.setUser(creator);
        leaderMember.setRole("LEADER");
        projectMemberRepository.save(leaderMember);

        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest req, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireLeader(project, user);

        if (req.name() != null)        project.setName(req.name());
        if (req.description() != null) project.setDescription(req.description());
        if (req.courseName() != null)  project.setCourseName(req.courseName());
        if (req.semester() != null)    project.setSemester(req.semester());
        if (req.startDate() != null)   project.setStartDate(req.startDate());
        if (req.endDate() != null)     project.setEndDate(req.endDate());
        projectRepository.save(project);
        entityManager.flush();
        entityManager.refresh(project);
        return ProjectResponse.from(project);
    }

    @Transactional
    public void deleteProject(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireLeader(project, user);
        projectRepository.delete(project);
    }

    // ── 초대 코드 ──────────────────────────────────────────────────────────

    @Transactional
    public ProjectResponse regenerateInviteCode(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireLeader(project, user);
        project.setInviteCode(generateUniqueInviteCode());
        projectRepository.save(project);
        entityManager.flush();
        entityManager.refresh(project);
        return ProjectResponse.from(project);
    }

    @Transactional
    public MemberResponse joinProject(JoinProjectRequest req, User user) {
        Project project = projectRepository.findByInviteCode(req.inviteCode())
                .orElseThrow(() -> new NotFoundException("유효하지 않은 초대 코드입니다"));

        if (projectMemberRepository.existsByProjectAndUser(project, user)) {
            throw new ForbiddenException("이미 참여 중인 프로젝트입니다");
        }

        ProjectMember member = new ProjectMember();
        member.setProject(project);
        member.setUser(user);
        member.setRole("MEMBER");
        projectMemberRepository.save(member);
        entityManager.flush();
        entityManager.refresh(member);
        return MemberResponse.from(member);
    }

    // ── 멤버 관리 ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireMember(project, user);
        return projectMemberRepository.findByProject(project).stream()
                .map(MemberResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public MemberResponse updateMemberRole(UUID projectId, UUID memberId,
                                           UpdateMemberRoleRequest req, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireLeader(project, user);

        ProjectMember target = projectMemberRepository.findById(memberId)
                .filter(m -> m.getProject().getId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다"));

        // 리더를 다른 역할로 변경할 때 리더가 1명 남아있는지 확인
        if ("LEADER".equals(target.getRole()) && !"LEADER".equals(req.role())) {
            long leaderCount = projectMemberRepository.countByProjectAndRole(project, "LEADER");
            if (leaderCount <= 1) {
                throw new ForbiddenException("프로젝트에 리더가 최소 1명은 있어야 합니다");
            }
        }

        target.setRole(req.role());
        projectMemberRepository.save(target);
        return MemberResponse.from(target);
    }

    @Transactional
    public void kickMember(UUID projectId, UUID memberId, User user) {
        Project project = accessChecker.getProject(projectId);
        accessChecker.requireLeader(project, user);

        ProjectMember target = projectMemberRepository.findById(memberId)
                .filter(m -> m.getProject().getId().equals(projectId))
                .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다"));

        if (target.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("자기 자신을 추방할 수 없습니다");
        }

        projectMemberRepository.delete(target);
    }

    @Transactional
    public void leaveProject(UUID projectId, User user) {
        Project project = accessChecker.getProject(projectId);
        ProjectMember member = accessChecker.requireMember(project, user);

        if ("LEADER".equals(member.getRole())) {
            long leaderCount = projectMemberRepository.countByProjectAndRole(project, "LEADER");
            if (leaderCount <= 1) {
                throw new ForbiddenException("리더가 혼자일 때는 탈퇴할 수 없습니다. 다른 멤버에게 리더를 위임하세요");
            }
        }

        projectMemberRepository.delete(member);
    }

    // ── 데이터 수집 동의 ────────────────────────────────────────────────────

    @Transactional
    public MemberResponse recordConsent(UUID projectId, ConsentRequest req, User user) {
        Project project = accessChecker.getProject(projectId);
        ProjectMember member = accessChecker.requireMember(project, user);

        member.setConsentPlatform(req.consentPlatform());
        member.setConsentGithub(req.consentGithub());
        member.setConsentDrive(req.consentDrive());
        member.setConsentAiAnalysis(req.consentAiAnalysis());
        member.setConsentedAt(OffsetDateTime.now());
        projectMemberRepository.save(member);
        return MemberResponse.from(member);
    }

    // ── 초대 코드 생성 ─────────────────────────────────────────────────────

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = generateCode();
        } while (projectRepository.existsByInviteCode(code));
        return code;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
