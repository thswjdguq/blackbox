# Docker 구성 & 배포 가이드

## docker-compose.yml

```yaml
version: "3.9"

services:
  # ─── PostgreSQL ───
  db:
    image: postgres:16-alpine
    container_name: blackbox-db
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME:-blackbox_db}
      POSTGRES_USER: ${DB_USER:-blackbox}
      POSTGRES_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD is required}
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"        # 개발 시 호스트에서 직접 접속용 (프로덕션에서 제거)
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-blackbox}"]
      interval: 5s
      timeout: 5s
      retries: 5

  # ─── Spring Boot Backend ───
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: blackbox-backend
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${DB_NAME:-blackbox_db}
      SPRING_DATASOURCE_USERNAME: ${DB_USER:-blackbox}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}
      FILE_UPLOAD_DIR: /data/uploads
      # 확장 1
      GITHUB_APP_ID: ${GITHUB_APP_ID:-}
      GITHUB_APP_PRIVATE_KEY: ${GITHUB_APP_PRIVATE_KEY:-}
      GITHUB_WEBHOOK_SECRET: ${GITHUB_WEBHOOK_SECRET:-}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID:-}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET:-}
      # 확장 2
      CLAUDE_API_KEY: ${CLAUDE_API_KEY:-}
    volumes:
      - uploads:/data/uploads
    expose:
      - "8080"

  # ─── Next.js Frontend ───
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
      args:
        NEXT_PUBLIC_API_URL: ${NEXT_PUBLIC_API_URL:-/api}
    container_name: blackbox-frontend
    restart: unless-stopped
    depends_on:
      - backend
    expose:
      - "3000"

  # ─── Nginx Reverse Proxy ───
  nginx:
    image: nginx:alpine
    container_name: blackbox-nginx
    restart: unless-stopped
    depends_on:
      - frontend
      - backend
    ports:
      - "80:80"
      # - "443:443"    # SSL 필요 시
    volumes:
      - ./nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
      - uploads:/data/uploads:ro    # 정적 파일 서빙
      # - ./nginx/ssl:/etc/nginx/ssl:ro    # SSL 인증서

volumes:
  pgdata:
    driver: local
  uploads:
    driver: local
```

---

## Dockerfile — Backend (Spring Boot)

```dockerfile
# backend/Dockerfile
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN mkdir -p /data/uploads
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Dockerfile — Frontend (Next.js Standalone)

```dockerfile
# frontend/Dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci
COPY . .
ARG NEXT_PUBLIC_API_URL=/api
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
```

> **주의:** `next.config.js`에 `output: 'standalone'` 필수 설정.

---

## Nginx 설정

```nginx
# nginx/default.conf
upstream frontend {
    server frontend:3000;
}

upstream backend {
    server backend:8080;
}

server {
    listen 80;
    server_name _;

    client_max_body_size 50M;    # 파일 업로드 크기 제한

    # API 요청 → Spring Boot
    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Webhook 요청 (GitHub/Drive)
    location /api/webhooks/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 업로드 파일 정적 서빙 (선택적 최적화)
    location /uploads/ {
        alias /data/uploads/;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # 나머지 → Next.js
    location / {
        proxy_pass http://frontend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## 환경변수 (.env)

```bash
# .env (루트 — docker compose가 자동 로드)

# Database
DB_NAME=blackbox_db
DB_USER=blackbox
DB_PASSWORD=change_me_in_production

# JWT
JWT_SECRET=change_me_a_long_random_string_at_least_32_chars

# Frontend
NEXT_PUBLIC_API_URL=/api

# 확장 1 (빈 값이면 해당 기능 비활성)
GITHUB_APP_ID=
GITHUB_APP_PRIVATE_KEY=
GITHUB_WEBHOOK_SECRET=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=

# 확장 2
CLAUDE_API_KEY=
```

> **⚠️ `.env`는 `.gitignore`에 추가.** `.env.example` 파일을 커밋하여 필요 변수 목록 공유.

---

## 개발 환경 명령어

```bash
# 최초 기동 (빌드 포함)
docker compose up -d --build

# 백엔드만 재빌드 (코드 변경 시)
docker compose up -d --build backend

# 로그 실시간 확인
docker compose logs -f backend
docker compose logs -f db

# DB 직접 접속 (psql)
docker compose exec db psql -U blackbox -d blackbox_db

# 전체 중지
docker compose down

# 전체 삭제 (데이터 포함 — 주의!)
docker compose down -v
```

---

## 로컬 개발 시 Hot Reload

Docker 내부에서 소스 변경을 감지하려면 볼륨 마운트를 추가하거나, **백엔드/프론트엔드는 호스트에서 직접 실행**하고 DB만 Docker로 띄울 수 있다.

```bash
# DB만 Docker로 기동
docker compose up -d db

# 백엔드: 호스트에서 직접 실행 (hot reload)
cd backend && ./gradlew bootRun

# 프론트엔드: 호스트에서 직접 실행 (hot reload)
cd frontend && npm run dev
```

이 경우 백엔드의 DB 접속 URL을 `localhost:5432`로 변경:
```yaml
# application-local.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/blackbox_db
```

```bash
# 프로필 지정 실행
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

---

## 프로덕션 배포 가이드

### 1. 서버 준비
- Docker + Docker Compose 설치된 리눅스 서버 (AWS EC2, 학교 서버 등)
- 80/443 포트 오픈

### 2. 배포
```bash
git clone <repo-url>
cd team-blackbox
cp .env.example .env
# .env 편집하여 실제 비밀번호 설정

docker compose -f docker-compose.yml up -d --build
```

### 3. SSL (선택)
- Let's Encrypt + certbot 컨테이너 추가
- 또는 캡스톤 데모 환경에서는 self-signed 인증서 사용

---

## 프로젝트 디렉토리 구조 (실제 코드)

```
team-blackbox/
├── docker-compose.yml
├── .env.example
├── nginx/
│   └── default.conf
├── backend/
│   ├── Dockerfile
│   ├── build.gradle
│   ├── settings.gradle
│   └── src/
│       └── main/
│           ├── java/com/blackbox/api/
│           └── resources/
│               ├── application.yml
│               ├── application-local.yml
│               └── db/migration/       ← Flyway SQL 파일
│                   ├── V1__init_users.sql
│                   ├── V2__init_projects.sql
│                   └── ...
├── frontend/
│   ├── Dockerfile
│   ├── next.config.js                  ← output: 'standalone'
│   ├── package.json
│   └── app/
└── docs/                               ← 컨텍스트 .md 파일
```
