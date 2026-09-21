# Deferred Items (유예 항목)

> 이번 리팩토링 범위 **밖**으로 의도적으로 미룬 항목. PRINCIPLES §7(모호/범위초과 시 유예) 적용.
> 대부분 "기획서엔 있으나 MVP에 없는 기능 갭" — 구현하면 **새 기능 개발**이라 리팩토링이 아니다(PRINCIPLES §1).
> **처리 방침(사용자 결정, DEC-DRIFT-001):** 차후 **고도화 단계에서 팀 전원이 함께** 결정·구현.
> 출처: Phase 1 Drift 인벤토리(`DRIFT_INVENTORY.md`) Task 13 분류.

---

## DEFER-01 — 점수체계 0~150 정규화 미적용 (기획서 괴리)

- **Drift ID:** D-INV-06, D-SYN-05-A, D-SYN-05-B, D-SYN-05-C
- **기획서(Spec):** 기여도 = Σ(항목별점수×가중치), 정규화 = `min(150, (개인/팀평균)×100)`. 가중치 0.30/0.25/0.20/0.25. 경보 임계값(편차>40% FREE_RIDE, 60%+ OVERLOAD).
- **현 구현:** `ScoreService`가 boolean participation(FULL/PARTIAL/NONE)으로만 동작. `git_score`등 컬럼·가중치 미사용, 0~150 정규화·`Math.min(150)` 없음. 경보도 boolean 기준.
- **유예 사유:** 점수엔진 전면 재설계 = 큰 기능 작업. "어느 설계가 맞는가"(기획서 수식 vs MVP boolean)는 제품 방향 결정.
- **차후:** 팀 전원이 (a) 기획서 수식으로 구현 vs (b) boolean 설계 채택+기획서 갱신 결정.

## DEFER-02 — consent 4단계 온보딩 UI 부재

- **Drift ID:** D-SYN-05-D, (연계) D-INV-07
- **기획서(Spec):** 동의 4단계(플랫폼/GitHub/Drive/AI분석) 온보딩 UI.
- **현 구현:** 백엔드 `ConsentRequest`(4필드)·`POST /{projectId}/consent` 존재하나 **프론트 온보딩 UI 없음**. consent 가드(INV-07)도 미적용.
- **유예 사유:** UI 신규 제작 + 동의 강제 로직 = 새 기능. 개인정보 동의 흐름이라 팀·정책 합의 필요.
- **차후:** 팀 전원이 온보딩 UI + consent 가드(activity_logs 기록 차단) 함께 설계·구현.

## DEFER-03 — OBSERVER 권한 강제 부재

- **Drift ID:** D-INV-04
- **기획서(Spec):** role=OBSERVER는 변경 API 중 PUT /weights만 호출 가능; `ProjectAccessChecker`가 강제.
- **현 구현:** `requireLeader()/requireMember()`만 존재, OBSERVER 전용 체크 없음.
- **유예 사유:** 접근제어 동작 신규 추가 = 보안 동작 변경. OBSERVER 역할의 실제 사용 범위를 팀이 확정해야 함(현재 역할이 실사용되는지 포함).
- **차후:** 팀 전원이 OBSERVER 권한 정책 확정 후 가드 구현.

---

## 재진입 조건

- 리팩토링(Phase 0~6) 완료 + main 통합 후, **고도화(post-refactor enhancement) 단계** 착수 시 본 문서를 입력으로 팀 회의.
- 각 항목은 그때 별도 기능 작업지시서/이슈로 전환.
