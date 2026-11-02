# 개발 가이드

## 전체 스택 실행

**사전 요구:** Docker · Docker Compose.

```bash
# 1) 환경 변수 준비
cp .env.example .env
#   DB_PASSWORD · JWT_SECRET(32자 이상) 등을 채운다.
#   외부 연동(Google/Notion/Discord/AI) 키는 비워두면 해당 기능만 비활성.

# 2) 전체 스택 기동 (db · backend · frontend · nginx)
docker compose up -d --build

# 3) 접속 (로컬은 self-signed 인증서)
#   https://localhost
```


## 개발 환경

- 컨테이너를 다시 빌드하지 않고 개발하려면(코드 수정이 바로 반영된다) 터미널 세 개에서:

  ```bash
  docker compose -f docker-compose.db-only.yml up -d                  # DB만
  cd backend && ./gradlew bootRun                                     # SPRING_DATASOURCE_USERNAME·PASSWORD, JWT_SECRET을 환경 변수로 준다
  cd frontend && NEXT_DEV_BACKEND=http://localhost:8080 npm run dev   # 이 값이 없으면 화면의 /api 호출이 실패한다
  ```

  PowerShell에서는 `$env:NEXT_DEV_BACKEND='http://localhost:8080'; npm run dev`처럼 준다.
  백엔드는 CI와 같은 JDK 17로 실행한다. 기본 Java가 다른 버전이면 `JAVA_HOME`을 JDK 17로 지정한다. DB 사용자와 비밀번호는 `docker-compose.db-only.yml`에 적힌 값을 그대로 준다. 화면은 환경 변수를 주는 대신 `frontend/.env.example`을 `frontend/.env.local`로 복사해도 된다.
  파일 업로드까지 쓰려면 백엔드에 `FILE_UPLOAD_DIR`도 준다. 기본값 `/data/uploads`는 컨테이너 안의 경로라 로컬에서는 업로드가 500으로 실패한다. 저장소 밖의 폴더를 지정한다.
- 두 브랜치를 함께 띄우려면(예: main으로 개발하면서 시연 브랜치를 확인) 브랜치를 옆 폴더에 받아 `docker-compose.second.yml`을 붙인다. 폴더 이름이 Compose의 프로젝트 이름이 되어 이미지, DB, 업로드 파일이 따로 만들어지고, 이 파일이 컨테이너 이름과 포트를 바꾼다.

  ```bash
  git worktree add ../blackbox-expo origin/shinguexpo
  cd ../blackbox-expo && cp ../blackbox/.env .
  docker compose -f docker-compose.yml -f ../blackbox/docker-compose.second.yml up -d --build    # https://localhost:8443
  ```

  한 폴더에서 `git switch`로 브랜치만 바꿔 띄우지 않는다. `up -d`는 이미지가 이미 있으면 다시 빌드하지 않아 이전 브랜치의 코드가 그대로 뜨고, DB에는 두 브랜치의 마이그레이션이 섞인다. 코드를 고친 뒤에는 `up -d --build`로 띄운다.
- 헬스 체크: `GET /api/health`.

## 환경 변수

`.env.example`이 정본 목록이다. 핵심:

| 변수 | 용도 |
|---|---|
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | PostgreSQL |
| `JWT_SECRET` | JWT 서명 키 (최소 32자) |
| `JWT_EXPIRATION_MS` / `JWT_REFRESH_EXPIRATION_MS` | 토큰 만료 |
| `APP_CORS_ORIGINS` | CORS 허용 origin (로컬은 `*`) |
| `NEXT_PUBLIC_API_URL` | Docker 빌드에 넘기는 값. 현재 화면 코드는 읽지 않는다(화면은 `/api`로 요청) |

**선택 연동(비우면 비활성):** `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`·`GOOGLE_REDIRECT_URI`, `OPENAI_API_KEY`·`LLM_BASE_URL`·`LLM_MODEL`·`SPRING_AI_MODEL_CHAT`, `GEMINI_API_KEY`·`GEMINI_MODEL`, `CLAUDE_API_KEY`, `NOTION_API_KEY`·`NOTION_PARENT_PAGE_ID`·`NOTION_CALENDAR_DB_ID`, `DISCORD_WEBHOOK_URL`, `FRONTEND_BASE_URL`.

`GITHUB_APP_ID`·`GITHUB_APP_PRIVATE_KEY`·`GITHUB_WEBHOOK_SECRET`은 `.env.example`에 있지만 지금 코드에서 쓰는 곳이 없다.

## 프로젝트 구조

```
blackbox/
├── backend/      Spring Boot (controller · service · dto · entity · repository · security)
│   └── src/main/resources/db/migration/   Flyway V1~V35
├── frontend/     Next.js App Router (src/app · components · lib · types)
├── nginx/        리버스 프록시 + SSL 설정
├── docs/         아키텍처·운영 문서
├── md/           프로젝트 컨텍스트(claude.md) · 기획서 · 핸드오버 로그
└── docker-compose.yml
```
