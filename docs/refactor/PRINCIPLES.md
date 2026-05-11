# 리팩토링 원칙 (PRINCIPLES)

> 이 문서는 `refactor/main` 브랜치 전체 리팩토링의 헌법이다.
> 모든 작업지시서(`docs/refactor/tasks/*.md`)는 이 원칙을 따른다.
> 충돌 시 이 문서가 우선이다.

---

## 0. 대전제

- **실행자는 GitHub Copilot, 계획자는 Claude.** 사용자는 두 에이전트를 지휘하는 팀장.
- **리팩토링은 "이해하는 코드만" 한다.** 모호한 부분은 유예한다(§7).
- **`md/gc.md`의 INV-01~07은 절대 위반 불가**한 불변 규칙이다. 본 문서에서 중복 정의하지 않고 참조한다.

## 1. 목적과 범위

**무엇을 한다:**
- 코드 구조/아키텍처 개선 (도메인 경계, 레이어 책임)
- 코드 품질/가독성 향상 (네이밍, 중복 제거, 함수 분리, 매직 값 제거)
- 문서-코드 drift 정리 (§3)
- 발견되는 버그/UX 이슈 동반 수정 (별도 커밋)

**무엇을 하지 않는다:**
- 신규 기능 추가
- 외부 의존성 큰 폭 교체 (Spring Boot, Next.js 메이저 업그레이드 등)
- 운영 환경 인프라 변경 (Docker Compose 구조 등)
- INV(불변 규칙) 자체의 변경

## 2. 문서의 권위 등급

문서는 한 덩어리가 아니다. 등급에 따라 코드와 충돌할 때 처리가 다르다.

| 등급 | 출처 | 본질 | 코드와 충돌 시 |
|---|---|---|---|
| **Spec(의도)** | `md/gc.md` INV-01~07, `md/TeamBlackbox_기획서_v3.md`, `md/claude.md`의 "주요 규칙" | 동작해야 하는 방식 | **코드가 버그.** 리팩토링 대상 |
| **State(상태)** | `md/todo.md` 진행 상태, `md/handover_log.md` | 현재 만들어진 것에 대한 주장 | **문서 stale.** Phase 6에서 갱신 |
| **Convention(스타일)** | `md/frontend-design.md`, 코드 컨벤션 | 권장 스타일 | 적용 비용 vs 가치로 판단(§3 D4) |

`files/` 폴더의 문서들은 outdated 중복본이다(MySQL/Vercel 등 폐기 정보). **인용 금지, Phase 0에서 정리**한다.

## 3. Drift 처리 규칙

### Phase 1에서 `docs/refactor/DRIFT_INVENTORY.md` 산출

`md/gc.md`의 SYNC-01~05 / CODE-01~03 검사를 실제 실행하여 다음 표를 채운다.

```
| ID | 출처 | 문서 진술 | 코드 실제 | 등급 | 결정안(Claude) | 사용자 결정 | Task ID |
```

### 결정 타입과 처리

- **D1. 코드 수정** — Spec 위반. 별도 커밋, 작업지시서에 INV ID/근거 명시
- **D2. 문서 수정** — State 드리프트. Phase 6 문서화 작업에 묶음
- **D3. 의도적 차이로 기록** — 둘 다 맞음. `docs/refactor/DECISIONS.md`에 사유 기록
- **D4. 사용자 확인 필요** — 모호. 별도 질문 묶음으로 일괄 확인

### 워크플로우
Claude가 등급 분류 + 결정 1차안 제시 → 사용자가 검토·확정 → Copilot 작업지시서에 반영.

## 4. 동작 보존 원칙

- 리팩토링 커밋은 **외부에서 본 동작이 동일**해야 한다.
- 동작 변경이 의도적이라면 별도 커밋(`fix:` 또는 `feat:`)으로 분리한다.
- 같은 PR 안에서 `refactor:` 커밋과 `fix:` 커밋이 섞이는 것은 OK. 한 커밋에 두 종류가 섞이는 것은 금지.
- 행동 보존이 의심되면 **유예**(§7)한다.

## 5. 형상관리

### 브랜치 전략 (3계층)

```
main                              ← 리팩토링 완료 + 팀원 승인 후에만 머지
 └─ refactor/main                 ← 리팩토링 통합 브랜치 (현재)
     └─ refactor/<task-id>-<short-name>   ← Task 단위 작업 브랜치
        예: refactor/00-cleanup-files-folder
            refactor/12-auth-module-split
```

### 작업 흐름

1. Claude가 `docs/refactor/tasks/NN-xxx.md` 작성
2. Copilot/사용자가 `refactor/main`에서 `refactor/<task-id>` 분기
3. Copilot 실행 → 커밋
4. PR 생성: **base는 항상 `refactor/main`**, 제목에 task ID(예: `[refactor-12] auth 모듈 분리`)
5. 사용자/Claude 리뷰 → 승인 → squash merge
6. 작업 브랜치 삭제

### 금지 사항

- `main`에 push / PR / merge **(팀원 최종 승인 전까지 절대 금지)**
- `refactor/main`에 직접 commit / force push
- 한 커밋에 여러 종류 변경 섞기 (rename + 로직 변경 등)
- INV-05 위반(외부 managed 서비스 추가)
- 훅 우회(`--no-verify` 등)

### 커밋 메시지 규칙

- 한 줄 요약 + 필요 시 본문 "왜"
- 타입 prefix: `refactor:` / `rename:` / `move:` / `fix:` / `feat:` / `docs:` / `chore:`
- 한 커밋에는 한 타입만
- 본문에 관련 INV ID 또는 Drift ID 기재(있을 경우)

## 6. Copilot 협업 규칙

Copilot이 자주 저지르는 실수를 차단하기 위한 규칙. 모든 작업지시서에 자동 포함된다.

1. **추측 금지** — 모르는 동작은 빈 함수로 두지 말고 작업지시서에 `[CONFIRM]` 태그로 질문 적기
2. **임포트 자동 정리 신뢰 금지** — IDE의 organize imports 결과를 수동 확인
3. **테스트 동시 수정** — 테스트가 깨지면 코드를 다시 보라(테스트가 옳을 가능성)
4. **DB 스키마 변경 금지** — 새 Flyway V18+ 추가만 가능. 기존 V*.sql 수정 절대 금지
5. **DROP COLUMN은 2단계** — ① 코드에서 사용 제거 PR ② 다음 PR에서 컬럼 삭제
6. **외부 API 키/시크릿 하드코딩 금지** — 항상 환경변수
7. **Lombok 자동 생성 의존 시 동작 확인** — `@Data` 등이 만든 메서드가 실제로 호출 가능한지
8. **frontend `: any` 도입 금지** — 모르면 작업지시서에 질문
9. **변경 범위 작업지시서 외 확장 금지** — 옆 파일이 더러워도 작업지시서에 없으면 건드리지 말 것

## 7. 의도가 모호할 때 — 우선순위

코드만 보고 의도를 추측하지 않는다. 다음 순서로 확인한다.

1. **`git log` + PR 설명** — 가장 직접적
2. **`md/handover_log.md`의 Technical Decisions** — 이미 기록된 의도
3. **사용자(팀장)에게 질문** — 캡스톤 본인 코드, 답할 수 있음
4. **그래도 모호** → **리팩토링 유예**. 작업지시서에서 해당 부분 제외하고 `docs/refactor/DEFERRED.md`에 기록

## 8. 백엔드 코드 컨벤션 (Spring Boot)

> 추측한 컨벤션이 아니라, 현재 코드에서 관찰된 패턴을 명시한다.

- **레이어 책임**: Controller(얇음, 인증·검증·DTO 변환) → Service(비즈니스 로직, `@Transactional`, `activityLogService.log()` 호출) → Repository(JPA)
- **DTO**: `dto/` 패키지, Java `record`, `Request`/`Response` 접미사
- **Entity**: `entity/` 패키지, JPA, Lombok `@Getter` 등 사용. 절대 Controller에서 직접 반환 금지
- **권한 검증**: `ProjectAccessChecker` 사용 (위치: `service/` 하위 추정 — 인벤토리에서 확인)
- **JWT/Security**: `security/` 패키지. 변경 시 INV-04(OBSERVER 권한 범위) 재검증 필수
- **외부 HTTP**: `WebClient`(Spring WebFlux). 에러 핸들러 필수(`md/handover_log.md` §9.1 참조)
- **검증**: `@Valid` + DTO 어노테이션
- **예외 처리**: `GlobalExceptionHandler` 통일. `IllegalStateException` 등 미처리 예외가 Security 필터를 거쳐 403으로 잘못 변환되지 않게 주의
- **AI 서비스**: Claude → OpenAI 폴백 패턴 유지(`md/handover_log.md` §9.1)

## 9. 프론트엔드 코드 컨벤션 (Next.js App Router)

- **App Router 기준**: `src/app/`. 페이지·라우트는 여기.
- **컴포넌트**: 도메인별(`components/kanban/` 등) 또는 공통(`components/Sidebar.tsx`)
- **라이브러리**: `src/lib/` (`api.ts`, `store/authStore.ts`, `db.ts`)
- **타입**: `src/types/` (백엔드 DTO와 1:1 매핑)
- **API 호출**: 항상 `lib/api.ts`의 axios 인스턴스 경유 (인터셉터로 토큰·401 처리)
- **상태**: Zustand. store는 `lib/store/`
- **디자인**: `md/frontend-design.md`의 토큰·컴포넌트 규칙 준수 (`bb-*` CSS 변수 + Tailwind)
- **타입 안전**: `: any` 금지(gc.md CODE-01). 모르면 `[CONFIRM]`
- **토큰 저장**: 현재 `localStorage` 사용(MVP 단계). 변경 결정 전까지 유지

## 10. 문서화 원칙 (Phase 6용)

Phase 6에서 Copilot에게 문서 작업을 시킬 때 강제하는 규칙.

- **실제 코드를 읽고 쓴다.** 옛 구조나 추측 금지
- **"무엇을 했다"가 아니라 "지금 어떻게 동작한다"**로 서술. 변경 이력은 git/PR 담당
- **예시 코드는 실제 파일에서 가져온다.** 컴파일되지 않는 가짜 예시 금지
- **마이그레이션 노트만 따로** — 기존 팀원이 알아야 할 변화는 별도 섹션
- **`files/` 폴더의 outdated 문서는 삭제** 또는 `_DEPRECATED` 접미사
- **루트에 `README.md`와 `CLAUDE.md` 신규 작성** (현재 없음)
- **`md/claude.md`와의 정본 일관성 유지** — `md/`가 정본 위치

## 11. 검수

각 PR 머지 전 사용자 또는 Claude가 `docs/refactor/REVIEW_CHECKLIST.md`로 검수한다. 통과 못 하면 머지 금지.

## 12. 운영 구조 (방어선 3겹)

이 PRINCIPLES.md는 **사람·Claude가 보는 정본**이다. Copilot이 자동으로 읽는다고 가정하지 않는다.

| 겹 | 파일 | 역할 |
|---|---|---|
| 1차 | `.github/copilot-instructions.md` | Copilot 자동 로드. 절대 규칙 30~50줄. |
| 2차 | `docs/refactor/tasks/NN-xxx.md` | 작업지시서. PRINCIPLES 관련 섹션을 **본문 발췌**로 인라인 박음 (anchor 참조 ❌). |
| 3차 | `.github/workflows/refactor-guard.yml` | CI lint. INV-02·05·Flyway 보존은 fail. `: any`·Entity 노출 등은 warn. |

**작업지시서 작성 규칙:** PRINCIPLES.md를 변경하면 1차(`.github/copilot-instructions.md`)도 함께 점검. 2차 작업지시서는 매번 새로 발췌.

**작업 묶음:** 한 번에 1~3개 Task의 작업지시서를 묶어서 사용자 검토 → 확정 → Copilot 순차 실행.

## 13. 저수준 모델 대응 원칙

GitHub Copilot의 베이스 모델은 모드/요금제에 따라 mini/haiku 급(GPT-4o-mini, Claude Haiku 등)이 사용된다. 우리 작업지시서는 이 등급에서도 안전하게 동작해야 한다.

### 알려진 5개 실패 패턴
1. **Lost-in-the-middle** — 200줄 넘는 prompt에서 가운데 내용을 약식 처리
2. **환각 (Hallucination)** — 명령 실행 결과·파일 내용을 만들어냄
3. **경합 제약 무시** — HARD LIMIT vs Out of Scope vs Pre-write가 동시 등장하면 1개만 따름
4. **친절 충동** — "이 김에 정리"하며 범위 넘어 수정
5. **절차 단축** — Pre-write 같은 "기다림" 단계를 무시하고 바로 코드 출력

### 작업지시서 설계 룰 (필수)

- **단일 Task ≤ 3 파일 변경** (예외 시 분할 또는 §C에 사유 명기)
- **단일 작업지시서 본문 ≤ 200줄** (코드 패턴 인라인 발췌 제외)
- **HARD LIMIT는 §2** (상위 위치 강제 — 모델 주의력 분배상)
- **핵심 규칙은 3회 반복** — §3 Out of Scope + §6 절차 + §8 acceptance
- **모호 형용사 금지** — "적절히", "신중히", "충분히" 사용 시 모델은 자체 해석
- **명령은 복사-실행 가능** — 변수 치환 필요 없는 완성 형태
- **사용자 환경 단일 명시** — WSL/PowerShell 양쪽 분기 금지 (한 환경 단정. 본 프로젝트는 WSL Ubuntu 기준)

### 명령 실행 + 출력 캡처 Task의 패턴 ("오케스트레이터 + 사용자 실행")

**환각 위험이 본질적이라 Copilot에게 직접 시키지 않는다.** 다음 구조 강제:

- Copilot은 "다음 명령 실행 후 결과를 붙여주세요" 안내만
- 실제 실행과 출력 복사는 사용자
- Copilot은 받은 출력을 문서로 정리

해당 Task는 작업지시서 §6에서 이 패턴을 명시. 적용 대상: 빌드 로그, 테스트 결과, 마이그레이션 이력 등 실제 출력 캡처.

### 모드·모델 권장
- **Copilot Edits 모드 우선** — UI가 파일별 approve로 친절 충동 차단
- **모델 선택 가능 시** — 코드 변경 Task는 Sonnet 또는 GPT-4o 이상 권장. 단순 문서 Task는 어떤 모델이든 OK
- **Chat free-text는 단순 질의에만**

### Claude 예외 실행
정적 문서 일회 생성이고 콘텐츠가 작업지시서 본문에 완전히 정의된 경우 한정으로, Claude가 직접 Write를 수행할 수 있다. 작업지시서에 "Copilot 비사용 — Claude가 직접 작성" 명시. 남용 금지(코드 작업에는 절대 적용 안 함).

## 14. 참조

- `md/gc.md` — INV-01~07, SYNC-01~05, CODE-01~03 (이 문서가 의존하는 헌법)
- `md/claude.md` — 프로젝트 컨텍스트 정본
- `md/handover_log.md` — 최근 기술적 결정과 버그 픽스 이력
- `md/TeamBlackbox_기획서_v3.md` — 기획 의도
