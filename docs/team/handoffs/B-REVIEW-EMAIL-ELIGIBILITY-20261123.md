# B · 검토 요청 이메일의 발송 직전 재확인

- 기준 main `45be1a96` / 2026-11-23 / K-41 0.4 1~6장 AGREED.
- 선행 #202·#208은 병합됐다. A의 기존 조회 계약·공통 업로더 판정을 읽기만 사용하며 A/C 파일·API·SQL·설정·의존성·#214/#217/#218의 미병합 파일은 변경/복사하지 않는다.

## 변경

ReviewRequestEmailEligibility는 현재 메일 설정·허용 목록·현재 기여자·행위자 제외를 EmailNotificationPolicy로 확인한다. ReviewRoundReader.get으로 프로젝트/제출물/회차의 관계, 최신·미결정·IN_REVIEW 상태를 확인한다. 회차를 연 사람도 스냅숏의 행위자와 같아야 한다. A 리뷰를 반영해 생산자 event_key 형식 비교와 A 조회가 보장하는 관계의 중복 비교는 제거했다. 최초45건 기록 이후의 수정 검증은 후속 리뷰 handoff에 별도 기록한다.

파일 업로더 제외는 #208에서 후보 계산과 decide()가 공유한 A의 ReviewRoundService.isRoundFileUploader를 직접 사용한다. B가 결정 권한을 따로 구현하지 않는다. 파일 없는 중간 검토에서는 같은 판정대로 해당 팀원을 임의로 제외하지 않는다.

반환값은 현재 계정 주소 또는 빈 결과이며 주소·입력 엔티티를 저장하지 않는다. 별도 읽기 전용 TransactionTemplate 안에서 조회하고 NotFound/Forbidden은 롤백 뒤 바깥에서 처리한다. DB 연결 장애는 자격 없음으로 숨기지 않는다. SMTP·worker·스케줄러는 등록하지 않는다.

## 검사

Java25.0.3 / PostgreSQL16 전용 DB `bb_b_review_email_20261123`. 관련 **45/45 통과**, 실패·오류·건너뜀0.

| 범위 | 건수 |
| --- | --- |
| 신규 실제 DB · ReviewRequestEmailEligibilityDatabaseTest | 7 |
| 신규 단위 · ReviewRequestEmailEligibilityTest | 4 |
| 기존 ReviewRoundServiceTest | 13 |
| 실제 DB·인증 MockMvc · FeedbackLoopHttpTest / NotificationPresentationHttpTest | 10 |
| 기존 메일 내용/정책 · EmailNotificationContentTest | 8 |
| 발송 없는 설정 · MailSettingsTest | 3 |

실제 DB에서 독립 팀원 허용, 행위자/파일 업로더/관찰자/외부인 제외, 주소/설정 변경, 관찰자 전환·탈퇴, 새 회차 이후 옛 사건 거절, 승인/보완 결정 후 제외, 없는 회차/제출물·다른 프로젝트·다른 행위자/key 제외, 파일 없는 중간 검토를 확인했다. 조회 뒤 검토 시각 불변·앱 알림/발송 대기 추가0건도 확인했다. 자료는 커밋된 합성 프로젝트이며 전용 DB에 남긴다.

CONFIRMED/SUBMITTED 상태는 A의 조회 응답 대역을 사용하는 단위 검사다. 이번에 실제 확정/제출 명령을 실행한 검사는 아니다. 비활성/빈 목록의 트랜잭션/저장소 호출0, 지원하지 않는 유형/대상 모양, 생산자 조회 중 DB 장애 전파도 단위 검사했다.

처음 실행 준비에서 Docker가 꺼져 있어 Gradle 테스트를 시작하지 못했다. Docker와 전용 DB를 준비한 뒤 실제 실행한45건은 모두 통과했다. 운영/시연 DB·AWS·실제 SMTP·유료 AI는 사용하지 않았다. 로컬 전체 backend는 미실행이며 최종 PR CI는 별도 기록한다.

## 다음

A는 공유 판정과 조회 계약 소비, 별도 읽기 트랜잭션, 대상 관계·사건 일치 조건을 검토한다. #214의 예약 저장, #217의 획득/lease, #218의 업무 요청 재확인과 이 검토 요청 재확인을 후속 worker/SMTP 어댑터에 연결해야 한다. 현재 메일 발송 기능 전체가 완성되거나 운영에 적용된 상태가 아니다. 기본 발송 비활성은 유지한다. 전송 시작 뒤 철회나 정확히 한 번 배달도 보장하지 않는다.
