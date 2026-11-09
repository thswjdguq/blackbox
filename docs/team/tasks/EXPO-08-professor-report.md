# EXPO-08 교수자 열람용 기여 활동 PDF

2026-10-19 사용자 요청. 기준 shinguexpo cdae9636. 기존 미커밋 문서와 빌드 부산물 보존.

- ReportService PDF를 프로젝트 요약, 팀원별 역할·활동 근거, 집계 기준, 상세 기록 부록으로 재구성한다.
- 점수 계산·저장·권한·DB 스키마는 변경하지 않는다. 네 활동 항목 유무를 기여율/100점으로 오인하지 않게 설명한다.
- 업무·파일·대화·검토·최종 제출 기록을 읽어 근거를 보완한다. 해시는 파일 식별 참고 부록으로 이동한다.
- 합성 PDF를 렌더링하여 한글·표·페이지 나눔을 확인하고 관련 테스트를 수행한다.
- PDF/증거 ZIP에 같은 보고서를 사용한다. 원격 push·배포·실제 기록 삭제는 수행하지 않는다.

## 완료
- [인계 및 검증 기록](../handoffs/EXPO-08-professor-report-20261019.md).
- ReportService/ReportEvidenceReader/EvidencePackageService와 해당 PDF 안내를 수정했다. 집계 산식·DB·API 경로는 유지했다.
- 전체 백엔드 286/286 후 최종 보고서 관련 5/5, 프론트 타입·production 빌드, PDF 페이지 이미지 검수 통과.
- 검수용 합성 PDF: output/pdf/team-contribution-report-example.pdf. 로컬 시연에 적용하며 공개 서버 배포는 포함하지 않는다.
