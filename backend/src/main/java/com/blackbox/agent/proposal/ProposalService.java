package com.blackbox.agent.proposal;

import com.blackbox.agent.AgentOriginReader;
import com.blackbox.agent.AgentProperties;
import com.blackbox.agent.AgentProposal;
import com.blackbox.agent.AgentProposalRepository;
import com.blackbox.entity.Project;
import com.blackbox.entity.User;
import com.blackbox.exception.ForbiddenException;
import com.blackbox.exception.NotFoundException;
import com.blackbox.service.ProjectAccessChecker;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 제안 카드의 조회·수정·채택·거절(K-30 2장). 에이전트는 카드를 만들 뿐이고, 기록은 사람이 여기서 채택할 때 만들어진다. */
@Service
@RequiredArgsConstructor
public class ProposalService implements AgentOriginReader {
    public static final String OBSERVER_REFUSED = "관찰자는 제안을 만들거나 채택할 수 없습니다";
    private static final String DECIDED = "이미 결정된 제안입니다";

    private final AgentProposalRepository proposals;
    private final ProposalKinds kinds;
    private final ProjectAccessChecker access;
    private final AgentProperties properties;

    /** status가 없으면 모든 상태다. 최신이 먼저다 */
    @Transactional(readOnly = true)
    public List<ProposalResponse> list(UUID projectId, User user, String status) {
        Project project = access.getProject(projectId);
        access.requireMember(project, user);
        Limit limit = Limit.of(properties.getProposalListLimit());
        List<AgentProposal> found = status == null
                ? proposals.findByProjectOrderByCreatedAtDesc(project, limit)
                : proposals.findByProjectAndStatusOrderByCreatedAtDesc(project, status(status), limit);
        return found.stream().map(ProposalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProposalResponse get(UUID projectId, User user, UUID proposalId) {
        Project project = access.getProject(projectId);
        access.requireMember(project, user);
        return ProposalResponse.from(proposals.findByIdAndProject(proposalId, project).orElseThrow(ProposalService::notFound));
    }

    /** 사람이 고친 내용도 모델이 만든 것과 같은 검증을 지나고, 그 종류가 아는 칸만 남는다 */
    @Transactional
    public ProposalResponse edit(UUID projectId, User user, UUID proposalId, JsonNode content) {
        AgentProposal proposal = lockForDecision(projectId, user, proposalId);
        if (!proposal.isPending()) throw new ResponseStatusException(HttpStatus.CONFLICT, DECIDED);
        if (content == null || !content.isObject()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content가 필요합니다");
        proposal.edit(checked(projectId, user, proposal.getKind(), content, false));
        return ProposalResponse.from(proposal);
    }

    /** 카드의 지금 내용대로 기록을 만든다. 전부 만들어지거나 하나도 만들어지지 않는다. 이미 채택된 카드는 그대로 돌려준다 */
    @Transactional
    public ProposalResponse accept(UUID projectId, User user, UUID proposalId) {
        AgentProposal proposal = lockForDecision(projectId, user, proposalId);
        if (proposal.getStatus() == AgentProposal.Status.REJECTED) throw new ResponseStatusException(HttpStatus.CONFLICT, DECIDED);
        if (proposal.isPending()) {
            JsonNode content = checked(projectId, user, proposal.getKind(), proposal.getContent(), true);
            proposal.accept(user, kinds.get(proposal.getKind()).accept(projectId, user, content), OffsetDateTime.now());
        }
        return ProposalResponse.from(proposal);
    }

    @Transactional
    public ProposalResponse reject(UUID projectId, User user, UUID proposalId) {
        AgentProposal proposal = lockForDecision(projectId, user, proposalId);
        if (proposal.getStatus() == AgentProposal.Status.ACCEPTED) throw new ResponseStatusException(HttpStatus.CONFLICT, DECIDED);
        if (proposal.isPending()) proposal.reject(user, OffsetDateTime.now());
        return ProposalResponse.from(proposal);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean createdFromProposal(UUID projectId, String targetType, UUID targetId) {
        return proposals.existsResult(projectId, targetType, targetId);
    }

    /** 카드 행을 잠근 뒤 상태를 본다. 동시에 두 번 눌러도 한 번만 만들어진다 */
    private AgentProposal lockForDecision(UUID projectId, User user, UUID proposalId) {
        Project project = access.getProject(projectId);
        if (!ProjectAccessChecker.canContribute(access.requireMember(project, user))) throw new ForbiddenException(OBSERVER_REFUSED);
        return proposals.lockByIdAndProject(proposalId, project).orElseThrow(ProposalService::notFound);
    }

    private JsonNode checked(UUID projectId, User user, String kind, JsonNode content, boolean forAccept) {
        ProposalKind.Checked checked = kinds.get(kind).check(projectId, user, content, forAccept);
        if (!checked.problems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제안의 내용을 확인해주세요 (" + String.join(", ", checked.problems()) + ")");
        }
        return checked.content();
    }

    private static AgentProposal.Status status(String value) {
        try {
            return AgentProposal.Status.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status 값이 올바르지 않습니다");
        }
    }

    // 다른 프로젝트의 카드와 없는 카드를 구분하지 않는다
    private static NotFoundException notFound() { return new NotFoundException("제안을 찾을 수 없습니다"); }
}
