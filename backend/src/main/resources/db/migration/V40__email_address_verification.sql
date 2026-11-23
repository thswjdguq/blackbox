-- K-41 0.5 9·10장 이메일 주소 확인과 알림 수신 설정. 번호는 #221 검토에서 A가 예약한 V40이다.
-- 업무 메일은 수신 동의(enabled)와 현재 주소 확인(email_verified_at)이 모두 있어야 예약·발송한다. 가입 흐름은 바꾸지 않는다.

-- 1. 현재 계정 주소를 확인한 시각. NULL이면 미확인이다. 기존 계정은 모두 미확인으로 시작한다
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- 2. 주소 확인 토큰. 원문은 메일에만 있고 여기에는 SHA-256만 둔다. 한 번 쓰면 used_at이 찬다.
--    email은 발급할 때의 주소다. 확인할 때 현재 주소와 다르면 옛 주소의 토큰으로 보고 거절한다
CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL,
    token_hash  CHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    CONSTRAINT email_verification_tokens_expiry CHECK (expires_at > created_at),
    CONSTRAINT email_verification_tokens_used_after_created CHECK (used_at IS NULL OR used_at >= created_at)
);
-- 요청 제한: 계정별 최근 요청과 서비스 전체 하루 발송 수를 센다
CREATE INDEX idx_email_verification_tokens_user_created ON email_verification_tokens (user_id, created_at);
CREATE INDEX idx_email_verification_tokens_created ON email_verification_tokens (created_at);

-- 3. 업무 메일 수신 설정(B 소유, K-41 8·10장). 행이 없으면 꺼짐이다(기존 회원 기본 off).
--    enabled_at은 마지막으로 켠 시각이다. 이 시각 이후에 생긴 알림만 메일로 보낸다(꺼졌다 켜도 과거 알림을 되살리지 않는다)
CREATE TABLE email_notification_preferences (
    user_id        UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    enabled        BOOLEAN NOT NULL DEFAULT false,
    notice_version VARCHAR(40) NOT NULL,
    enabled_at     TIMESTAMPTZ,
    disabled_at    TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT email_notification_preferences_enabled_at CHECK (NOT enabled OR enabled_at IS NOT NULL)
);

CREATE TRIGGER trg_email_notification_preferences_updated_at
    BEFORE UPDATE ON email_notification_preferences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
