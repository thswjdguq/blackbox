package com.blackbox.controller;

import com.blackbox.entity.User;
import com.blackbox.service.EmailVerificationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 로그인한 본인의 이메일 주소 확인(K-41 0.5 9장). 가입·로그인 흐름과는 별개다 */
@RestController
@RequestMapping("/api/auth/email-verification")
public class EmailVerificationController {
    private final EmailVerificationService verification;

    public EmailVerificationController(EmailVerificationService verification) { this.verification = verification; }

    /** 202 발송 요청(수신 완료가 아니다), 204 이미 확인한 주소, 429 재요청 제한, 503 사용 불가 */
    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@AuthenticationPrincipal User user) {
        return verification.request(user) == EmailVerificationService.Outcome.REQUESTED
                ? ResponseEntity.accepted().body(Map.of("message", EmailVerificationService.REQUESTED))
                : ResponseEntity.noContent().build();
    }

    public record ConfirmRequest(String token) {}

    /** 204 확인, 400 사용할 수 없는 링크, 410 만료, 409 주소 변경 */
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@AuthenticationPrincipal User user, @RequestBody(required = false) ConfirmRequest body) {
        verification.confirm(user, body == null ? null : body.token());
        return ResponseEntity.noContent().build();
    }

    /** detail은 화면이 그대로 보여 줄 서버 문장이다. 429에는 다시 요청할 수 있을 때까지의 초를 Retry-After로 싣는다 */
    @ExceptionHandler(EmailVerificationService.Rejected.class)
    ResponseEntity<ProblemDetail> rejected(EmailVerificationService.Rejected e) {
        var response = ResponseEntity.status(e.status());
        if (e.retryAfterSeconds() != null) response.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfterSeconds()));
        return response.body(ProblemDetail.forStatusAndDetail(e.status(), e.getMessage()));
    }
}
