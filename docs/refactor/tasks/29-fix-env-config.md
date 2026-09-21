# Task 29 — env 설정 누락 보완 (FRONTEND_BASE_URL / GOOGLE_REDIRECT_URI / DISCORD_WEBHOOK_URL)

## 1. 한 줄 요약
`application.yml`이 참조하지만 `docker-compose.yml`·`.env.example`에 빠진 환경변수 3종을 보완한다(설정 전달 chore, 코드·동작 변경 0).

## 2. 변경 범위 (HARD LIMIT)

> 실행자는 이 섹션 밖의 파일을 절대 건드리지 않는다.

- **파일 (이외 금지):**
  - `docker-compose.yml`
  - `.env.example`
- **클래스/메서드:** 해당 없음 (자바 코드 변경 0)
- **예상 변경 라인 수:** ~6줄 추가 (삭제·기존행 수정 없음)
- **예상 변경 파일 수:** 2개

## 3. 절대 건드리지 말 것 (Out of Scope)

- `backend/src/main/resources/application.yml` — **읽기만 한다. 절대 수정 금지.** (기본값 비교 근거로만 사용.)
- `docker-compose.yml`의 다른 서비스(db/frontend/nginx) 블록·volumes·networks — 변경 금지.
- `.env.example`의 기존 행 — **이미 있는 `DISCORD_WEBHOOK_URL=`(현 47행) 포함 수정·삭제 금지.** 신규 행만 추가.
- 자바 코드·`application-local.yml`·`.env`(실제 비밀값 파일) — 변경 금지.
- 변수 이름·기본값 임의 변경 금지 (아래 §6에 박힌 문자열 그대로).

## 4. 컨텍스트 / 의도

`application.yml`이 참조하는 env 3종이 배포 설정 문서에서 누락돼, 새 환경에서 컨테이너를 띄울 때 운영자가 어떤 변수를 줘야 하는지 알 수 없다(설정 drift). 값을 전달·문서화만 하고 **현재 동작은 그대로 보존**한다.

- application.yml 참조 위치(읽기 확인용):
  - `frontend-base-url: ${FRONTEND_BASE_URL:}` (현 45행, 기본값 = 빈 문자열)
  - `redirect-uri: ${GOOGLE_REDIRECT_URI:http://localhost/api/auth/google/callback}` (현 56행, **기본값 있음**)
  - `webhook-url: ${DISCORD_WEBHOOK_URL:}` (현 71행, 기본값 = 빈 문자열)
- 현재 상태:
  - `FRONTEND_BASE_URL`, `GOOGLE_REDIRECT_URI` → docker-compose.yml·.env.example **양쪽 모두 누락**
  - `DISCORD_WEBHOOK_URL` → `.env.example`엔 있고 `docker-compose.yml`엔 **누락**

- 관련 INV: 해당 없음
- 관련 Drift ID: **D-SYN-04** (DRIFT_INVENTORY.md, SYNC-04 / D1 확정)
- 관련 PRINCIPLES 섹션: §4(동작 보존), §6(실행자 협업)

### ⚠ 동작 보존 핵심 (GOOGLE_REDIRECT_URI)
`GOOGLE_REDIRECT_URI`는 application.yml에 **기본값**(`http://localhost/api/auth/google/callback`)이 있다.
docker-compose에 `${GOOGLE_REDIRECT_URI:-}`(빈 기본값)로 추가하면, 호스트에 변수가 없을 때 backend 컨테이너에 **빈 문자열**이 주입되어 application.yml 기본값을 덮어써 **동작이 바뀐다**(리다이렉트 URI가 빈값이 됨).
→ docker-compose의 compose-기본값을 **application.yml 기본값과 동일하게** 둬서 보존한다(§6 아래 정확한 문자열 사용).
나머지 두 변수(FRONTEND_BASE_URL·DISCORD_WEBHOOK_URL)는 app 기본값이 빈 문자열이라 `${VAR:-}`로 안전.

## 4-1. 의존 관계

- **선행 Task (먼저 머지):** 없음.
- **후행 Task:** 없음.
- **API surface 변경 여부:** No (설정 파일만). 문서 갱신 면제.

## 5. 적용해야 할 PRINCIPLES (본문 발췌)

> PRINCIPLES.md §4 (동작 보존 원칙):

리팩토링 커밋은 외부에서 본 동작이 동일해야 한다. 환경변수 전달 추가는 미설정 시 기존 기본값과 동일한 결과가 나오도록 해야 한다(기본값 보존).

> PRINCIPLES.md §6 (실행자 협업 규칙):

- 추측 금지. 변경 범위 작업지시서 외 확장 금지.
- 명령·문자열은 작업지시서에 박힌 그대로 사용. 변수 이름·기본값 임의 변경 금지.

## 6. 작업 절차

1. **`docker-compose.yml`** — `backend:` 서비스의 `environment:` 블록에서, `NOTION_CALENDAR_DB_ID: ${NOTION_CALENDAR_DB_ID:-}` (현 54행) **바로 아래**에 다음 3줄을 추가한다(들여쓰기는 기존 항목과 동일하게 6칸):

   ```yaml
      # application.yml 참조 — 설정 보완 (Task 29)
      FRONTEND_BASE_URL: ${FRONTEND_BASE_URL:-}
      GOOGLE_REDIRECT_URI: ${GOOGLE_REDIRECT_URI:-http://localhost/api/auth/google/callback}
      DISCORD_WEBHOOK_URL: ${DISCORD_WEBHOOK_URL:-}
   ```
   - **주의:** `GOOGLE_REDIRECT_URI`의 compose 기본값은 위 문자열 그대로(application.yml과 동일). 빈값(`:-`)으로 바꾸지 말 것 — §4 동작 보존.

2. **`.env.example`** — 다음 2개 신규 행을 추가한다(`DISCORD_WEBHOOK_URL=`은 이미 47행에 있으니 **추가하지 말 것**):
   - `GOOGLE_REDIRECT_URI=`를 Google OAuth 섹션의 `GOOGLE_CLIENT_SECRET=` (현 31행) **바로 아래**에 추가:
     ```
     # OAuth 콜백 URI (비우면 http://localhost/api/auth/google/callback 기본값)
     GOOGLE_REDIRECT_URI=
     ```
   - `FRONTEND_BASE_URL=`을 Frontend 섹션의 `NEXT_PUBLIC_API_URL=...` (현 21행) **바로 아래**에 추가:
     ```
     # 10분 전 알림 URL 생성용 (비우면 비활성, 예: https://yourdomain.com)
     FRONTEND_BASE_URL=
     ```

3. **검증:**
   - `git diff` 로 두 파일에 **추가만** 있고 기존 행 변경·삭제가 없는지 확인.
   - 백엔드 컴파일은 설정 파일과 무관하므로 생략 가능. 단, YAML 문법 깨짐 방지를 위해 `docker-compose.yml`의 들여쓰기(6칸)가 형제 항목과 정확히 일치하는지 눈으로 확인.
   - (선택, 가능하면) `docker compose config -q` 로 compose 파일 문법 유효성 확인.

## 7. Pre-write 프로토콜 적용 여부

- [x] **Skip** — 추가 라인이 §6에 문자열 단위로 박혀 있고, 2개 설정 파일·~6줄. 동작 보존 포인트(GOOGLE_REDIRECT_URI 기본값)도 명시됨.
- [ ] **Required**

## 8. 검수 기준 (Acceptance Criteria)

- [ ] `docker-compose.yml` backend `environment:`에 `FRONTEND_BASE_URL`·`GOOGLE_REDIRECT_URI`·`DISCORD_WEBHOOK_URL` 3행 추가. **`GOOGLE_REDIRECT_URI`의 compose 기본값 = `http://localhost/api/auth/google/callback`** (application.yml과 동일).
- [ ] `.env.example`에 `FRONTEND_BASE_URL=`·`GOOGLE_REDIRECT_URI=` 2행 추가. 기존 `DISCORD_WEBHOOK_URL=`(47행)은 **중복 추가 없음**.
- [ ] 두 파일 모두 **기존 행 0줄 변경·삭제**. diff는 순수 추가만.
- [ ] `application.yml`·자바 코드·다른 compose 서비스 diff 0.
- [ ] (가능 시) `docker compose config -q` 무오류. CI(refactor-guard) 통과.

## 9. PR 정보

- **Branch:** `refactor/29-fix-env-config`
- **PR base:** `refactor/main` (절대 `main` 아님)
- **PR title:** `[refactor-29] env 설정 누락 보완 (FRONTEND_BASE_URL/GOOGLE_REDIRECT_URI/DISCORD_WEBHOOK_URL)`
- **PR body 골격:**
  ```markdown
  ## 작업지시서
  - [Task 29](../docs/refactor/tasks/29-fix-env-config.md)

  ## 변경 요약
  - application.yml 참조하나 누락된 env 3종을 docker-compose.yml(3행)·.env.example(2행)에 보완
  - GOOGLE_REDIRECT_URI는 compose 기본값을 app 기본값과 동일하게 둬 동작 보존
  - Drift D-SYN-04 (SYNC-04) 해소

  ## HARD LIMIT 준수
  - 파일: docker-compose.yml, .env.example (2개)
  - 라인: ~6 추가 (기존 행 변경 0)

  ## 검수 체크리스트 결과
  - [x] 순수 추가 diff (기존 행 무변경)
  - [x] GOOGLE_REDIRECT_URI 기본값 보존으로 동작 불변
  ```
