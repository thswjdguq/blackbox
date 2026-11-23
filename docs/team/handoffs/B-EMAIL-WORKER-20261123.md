# B · 인증 필수 자동 발송 처리

- 기준 main `611d9c4b`, 최신 main의 #220은 화면 스타일 2개 파일뿐이며 백엔드 검증 소스 차이는 없다.
- K-41 0.5/#221 검토 대기, B-EMAIL-WORKER. 이 변경은 Draft이며 일반 사용자 SMTP 연결 완료가 아니다.

## 구현

EmailDeliveryAuthorization은 현재 계정 주소·수신 의향·마지막 enabledAt·현재 주소 verifiedAt을 읽는 내부 계약이다. 실제 구현을 제공하지 않으며, 인증 성공을 반환하는 임시 운영 bean도 없다. 인증/설정 조회가 없으면 메일을 예약하지 않고 worker도 대기를 획득하지 않는다. 앱 알림은 기존대로 유지한다.

메일 예약 시와 전송 전 모두 인증/수신 설정 시각이 사건 생성 시각보다 늦지 않은지 검사한다. 미인증·수신 off·다른 현재 주소·새 인증 또는 재활성화로 옛 요청을 되살리는 경우를 제외한다. worker는 기존 Task/Review 판정과 공통 composer를 사용한다. 조회에 쓰는 UserNotification 값은 저장하거나 재발행하지 않는다.

10초 간격 처리에서 한 번에 한 건씩 최대25건을 획득한다. SMTP 중에는 저장소 트랜잭션/행 잠금을 유지하지 않는다. 발송 직전 남은 lease가30초 이하이면 보내지 않고 회수 대상으로 남긴다. ACCEPTED/SENT, 일시 실패·결과 불명/재시도, 인증·영구 실패/FAILED, 조건 소멸/SKIPPED를 기존 저장소의 fencing·최대3회·지연과 연결한다. SMTP 결과 저장 실패는 lease 회수 대상으로 남는다. 정확히 한 번 배달을 보장하지 않는다.

## 검사

Java25·PostgreSQL16 전용 DB `bb_b_email_worker_fix_20261123`, 관련 **133/133 통과**, 실패·오류·건너뜀0.

- worker 단위18: 미인증/수신 off/없는 인증 조회·transport, 늦은 인증/재활성화, 현재 권한/주소 변경, 전송 직전 철회, lease 여유, 결과5종, 재진입·최대25건.
- worker DB8: 실제 TaskWorkflowService.request→업무·앱 알림·대기 커밋→현재 업무 판정→결과 저장. 인증 조회와 transport는 대역이다.
- 원자 예약 DB6: 기존5 + 허용된 미인증 주소는 앱 알림만 받고 인증 후 옛 사건은 소급 예약하지 않는 검사.
- 기존 업무·검토 판정/서비스, 피드백·앱 알림 HTTP, 메일 문장/정책/설정, lease 회귀101. 기존106과 범위가 겹치므로 합산하지 않는다.

첫 실행은133 중1 실패했다. 테스트가 Task의 요청 시각을 나노초로 직접 저장하면서 실제 서비스의 마이크로초 정규화·flush/refresh를 생략한 것이 원인이었다. 실제 TaskWorkflowService.request를 호출하는 fixture로 고친 뒤 새 DB에서133건을 한 번에 통과했다. 운영 요청 서비스의 시간 처리 규칙은 바꾸지 않았다. 최초 worker 단위 검사도 검토 경로에 회차 id가 직접 포함된다는 잘못된 기대1건을 기존 공통 경로(`/deliverables/{id}?tab=review`) 기준으로 수정했다.

## 미검증·선행 작업

- 실제 인증 API·토큰/SQL·설정 API·가입/프로필/확인 화면은 미구현이다. A·C #221 검토와 각 담당 구현을 기다린다. SQL 번호를 선점하거나 기존 migration을 수정하지 않았다.
- EmailDeliveryAuthorization의 실제 조회 구현과 A EmailNotificationTransport 구현이 없다. 이 PR만 적용해도 실제 이메일을 보낼 수 있는 상태가 아니다.
- 단위/DB의 transport 대역 ACCEPTED는 Gmail·Mailpit 수신 성공을 뜻하지 않는다. 실제 SMTP·메일함·브라우저 인증 흐름·운영 적용은 수행하지 않았다.
- 실제 환경 파일·자격증명은 읽지 않았다. 로컬 main 서버를 이 Draft로 바꾸거나 재시작하지 않았다.
- 후속: A/C 계약 확인→A 인증/SQL/SMTP→B 설정 API·실제 인증 읽기 연결→C 화면→격리 SMTP/인증 전체 흐름→사용자의 실제 수신/링크 확인. 그 전 실제 발송 비활성 유지.
