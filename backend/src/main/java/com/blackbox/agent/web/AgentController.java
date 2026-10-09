package com.blackbox.agent.web;

import com.blackbox.agent.model.AgentModelException;
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

import java.util.UUID;

/** K-30 2장의 상태 조회와 실행. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/agent")
public class AgentController {
    private final AgentStatusReader status;
    private final AgentRunner runner;
    private final AgentRunLauncher launcher;

    @GetMapping("/status")
    public AgentStatusReader.Status status(@PathVariable UUID projectId, @AuthenticationPrincipal User user) {
        return status.read(projectId, user);
    }

    public record RunRequest(String skill, JsonNode input) {}

    /** 스트림이 시작되기 전의 실패(권한, 입력, 상한)는 HTTP 오류로 나가고, 그 뒤는 이벤트로 나간다 */
    @PostMapping("/runs")
    public ResponseEntity<SseEmitter> run(@PathVariable UUID projectId, @RequestBody RunRequest request,
                                          @AuthenticationPrincipal User user) {
        AgentRunner.Prepared prepared = runner.prepare(projectId, user, request.skill(), request.input());

        // 스트림은 실행이 done이나 error를 알릴 때 닫힌다. 시간 상한은 실행 쪽이 지키므로 서블릿의 시간 제한은 두지 않는다
        SseEmitter emitter = new SseEmitter(0L);
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
