package com.blackbox.agent.web;

import com.blackbox.agent.model.AgentModelException;
import com.blackbox.agent.proposal.ProposalResponse;
import com.blackbox.agent.proposal.ProposalService;
import com.blackbox.agent.run.AgentRunLauncher;
import com.blackbox.agent.run.AgentRunner;
import com.blackbox.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

/** K-30 2장의 상태 조회와 실행. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/agent")
public class AgentController {
    private final AgentStatusReader status;
    private final AgentRunner runner;
    private final AgentRunLauncher launcher;
    private final ProposalService proposals;

    @GetMapping("/status")
    public AgentStatusReader.Status status(@PathVariable UUID projectId, @AuthenticationPrincipal User user) {
        return status.read(projectId, user);
    }

    /** 채택으로 만들어진 기록의 id. 모델이 꺼져 있어도 동작한다 */
    @GetMapping("/origins")
    public ProposalResponse.Origins origins(@PathVariable UUID projectId, @AuthenticationPrincipal User user) {
        return proposals.origins(projectId, user);
    }

    public record RunRequest(String skill, JsonNode input) {}

    /** 스트림이 시작되기 전의 실패(권한, 입력, 상한)는 HTTP 오류로 나가고, 그 뒤는 이벤트로 나간다 */
    @PostMapping("/runs")
    public ResponseEntity<SseEmitter> run(@PathVariable UUID projectId, @RequestBody RunRequest request,
                                          @AuthenticationPrincipal User user) throws IOException {
        AgentRunner.Prepared prepared = runner.prepare(projectId, user, request.skill(), request.input());

        // 스트림은 실행이 done이나 error를 알릴 때 닫힌다. 시간 상한은 실행 쪽이 지키므로 서블릿의 시간 제한은 두지 않는다
        SseEmitter emitter = new SseEmitter(0L);
        // 실행 스레드보다 먼저 한 줄을 넣어 둔다. 응답의 헤더가 요청 스레드에서 나가게 하려는 것이다.
        // 이것이 없으면 실행 스레드의 첫 이벤트와 요청 스레드의 마무리가 헤더를 동시에 써서 응답이 깨질 때가 있다
        emitter.send(SseEmitter.event().comment("start"));
        launcher.launch(prepared, new SseAgentEvents(emitter));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")   // nginx가 응답을 모아 두지 않게 한다
                .body(emitter);
    }

    @ExceptionHandler(AgentModelException.class)
    public ProblemDetail unavailable(AgentModelException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}
