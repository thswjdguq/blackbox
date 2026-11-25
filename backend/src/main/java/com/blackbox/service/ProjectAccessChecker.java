package com.blackbox.service;

import com.blackbox.entity.Project;
import com.blackbox.entity.ProjectMember;
import com.blackbox.entity.User;
import com.blackbox.exception.CodedException;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.repository.ProjectMemberRepository;
import com.blackbox.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class ProjectAccessChecker {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectAccessChecker(ProjectRepository projectRepository,
                                ProjectMemberRepository projectMemberRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    /** 프로젝트 조회 (없으면 404) */
    public Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("프로젝트를 찾을 수 없습니다"));
    }

    /** 멤버 조회 (없으면 403) */
    public ProjectMember requireMember(Project project, User user) {
        return projectMemberRepository.findByProjectAndUser(project, user)
                .orElseThrow(() -> new ForbiddenException("프로젝트 멤버가 아닙니다"));
    }

    /** 관찰자는 읽기만 허용한다. */
    public static boolean canContribute(ProjectMember member) {
        return "LEADER".equals(member.getRole()) || "MEMBER".equals(member.getRole());
    }

    /** 관찰자는 읽기만 허용한다. 보관된 프로젝트는 쓰기를 막는다(K-60) */
    public ProjectMember requireContributor(Project project, User user) {
        ProjectMember member = requireMember(project, user);
        if (!"LEADER".equals(member.getRole()) && !"MEMBER".equals(member.getRole())) {
            throw new ForbiddenException("관찰자는 프로젝트를 수정할 수 없습니다");
        }
        requireNotArchived(project);
        return member;
    }

    /** LEADER 권한 확인. 보관된 프로젝트는 쓰기를 막는다(K-60). 보관·해제는 requireLeaderEvenIfArchived를 쓴다 */
    public ProjectMember requireLeader(Project project, User user) {
        ProjectMember member = requireLeaderEvenIfArchived(project, user);
        requireNotArchived(project);
        return member;
    }

    /** 보관·해제처럼 보관 중에도 팀장이 해야 하는 명령에만 쓴다 */
    public ProjectMember requireLeaderEvenIfArchived(Project project, User user) {
        ProjectMember member = requireMember(project, user);
        if (!"LEADER".equals(member.getRole())) {
            throw new ForbiddenException("리더만 수행할 수 있는 작업입니다");
        }
        return member;
    }

    /** 보관 중 안내와 409 detail에 같이 쓰는 문장(K-60). 화면은 이 문장을 그대로 보인다 */
    public static final String ARCHIVED_NOTICE = "보관된 프로젝트입니다. 팀장이 보관을 해제하면 다시 사용할 수 있습니다.";

    /** 보관된 프로젝트면 409 PROJECT_ARCHIVED. 회원·역할 검사 다음에 불러 회원이 아닌 사람에게 보관 여부를 알리지 않는다 */
    public void requireNotArchived(Project project) {
        if (project.isArchived())
            throw new CodedException(HttpStatus.CONFLICT, "PROJECT_ARCHIVED", ARCHIVED_NOTICE, Map.of());
    }
}
