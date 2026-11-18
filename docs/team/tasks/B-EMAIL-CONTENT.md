# B-EMAIL-CONTENT · SMTP와 분리한 메일 내용·수신 정책

- 담당 B / 검토 A / 최초 기준 main `035be3f2`, 최종 응답 검증 기준 `465adeee` / 2026-11-18 갱신.
- K-41 0.4 1~6장 AGREED(#197 A·C 승인·병합), 7~12장 후속 DRAFT. C가 #198에서 확인한 필수 응답과 #202의 소비 형식에 맞춘다. 공통 응답은 SMTP/새 SQL과 독립적으로 검토하며 실제 메일 발송은 활성화하지 않는다.
- 허용: 신규 NotificationPresentation/Factory와 EmailNotificationComposer/Policy/Transport, B 소유 NotificationDtos/NotificationService의 추가 응답 필드, 대응 단위·DB/HTTP 테스트·개인 기록. A/C 파일·설정·의존성·DB 스키마 변경 금지.
- A 리뷰 반영: 종류별 문장·대상·상대 경로를 공통 생성기로 옮기고 메일은 같은 결과에 메일 안내만 더한다. C가 확인한 message/target/path를 알림 목록에 추가하고 기존 필드를 보존한다. Jakarta Mail 주소 검증은 A 의존성 PR 뒤 연결한다.
- 목표: 기본 꺼짐과 명시적인 수신 허용 목록, 현재 프로젝트 기여자/행위자 제외, 고정 출처 원문 링크, 제목 줄바꿈/한글·이모지 처리. 주소·본문을 로그 문자열에 노출하지 않는다.
- 구현: 알림 목록에 message/target/path만 추가하고 기존 필드·권한·읽음·페이지·targetAvailable을 유지한다. label은 내부 제목 구성용이며 HTTP에 추가하지 않는다. 검토 댓글 anchor는 path에 포함한다.
- 검사: 관련 49/49(단위24 + 실제 알림 DB16 + PostgreSQL/인증 MockMvc HTTP6 + SMTP 설정 컨텍스트3), 실패·오류·건너뜀0. V39의 REVIEW_REQUESTED 저장·응답도 포함한다. 실제 SMTP/Gmail·브라우저 검증으로 보고하지 않는다. 세부 기록은 `B-EMAIL-CONTENT-20261118.md`를 따른다.
