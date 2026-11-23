package com.blackbox.service;

import com.blackbox.entity.User;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** A의 현재 주소 인증과 확인 요청 가용 상태를 소비한다. 토큰 발급·SMTP 설정 판정을 복제하지 않는다. */
@Component
public class AccountEmailNotificationVerificationReader implements EmailNotificationVerificationReader {
    private final EntityManager em;
    private final EmailVerificationService verification;

    public AccountEmailNotificationVerificationReader(EntityManager em, EmailVerificationService verification) {
        this.em = em; this.verification = verification;
    }

    @Override @Transactional(readOnly = true)
    public Optional<Status> current(UUID userId) {
        User user = em.find(User.class, userId);
        if (user == null) return Optional.empty();
        // A 확인 API는 JDBC로 readonly 컬럼을 갱신한다. 같은 영속성 문맥의 예전 User로 인증 전 상태를 돌려주지 않는다.
        em.refresh(user);
        var available = verification.availability(user);
        return Optional.of(new Status(user.getEmail(), user.getEmailVerifiedAt(), available.available(), available.reason()));
    }
}
