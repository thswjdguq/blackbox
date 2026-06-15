-- 구 숫자 점수 방식으로 생성된 레거시 알림 일괄 해제
UPDATE alerts
SET resolved_at   = NOW(),
    resolve_reason = '시스템 업그레이드: 참여 여부 방식으로 전환 (구 점수 기반 알림 자동 해제)'
WHERE resolved_at IS NULL;
