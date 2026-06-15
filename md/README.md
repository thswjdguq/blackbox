# Team Blackbox
> 팀 프로젝트 기여도 자동 증빙 플랫폼

## 프로젝트 소개
팀 프로젝트에서 "누가 뭘 얼마나 했는지"를
말이 아닌 데이터로 증명하는 EdTech SaaS 플랫폼

## 주요 기능
- 🔐 Hash Vault: 파일 업로드 시 SHA-256 해시 자동 고정
- 🤖 AI 회의록: Claude API로 회의 내용 자동 요약 + 액션아이템 추출
- 📊 기여도 분석: FULL/PARTIAL/NONE 참여 여부 자동 판정
- 📅 AI 일정 추천: Google Calendar 연동 + 최적 회의 시간 추천
- 🔔 Discord 알림: 태스크/회의/경보 실시간 알림
- 📋 Notion 연동: 회의록/칸반 자동 동기화
- 📄 무결성 PDF: SHA-256 해시 포함 기여도 리포트 발급

## 기술 스택
| 영역 | 기술 |
|------|------|
| Frontend | Next.js 14, TypeScript, Tailwind CSS |
| Backend | Java 17, Spring Boot 3.x, Spring Security |
| Database | PostgreSQL 16, Flyway V1~V18 |
| 인프라 | Docker Compose, Nginx, HTTPS/SSL |
| 외부 API | Claude API, Notion API, Google Calendar API |

## 실행 방법
1. 레포 클론
git clone https://github.com/thswjdguq/blackbox.git
cd blackbox

2. 환경변수 설정
cp .env.example .env
# .env 파일에 API 키 입력

3. 전체 스택 실행
docker compose up -d

4. 접속
https://localhost

## 팀원
| 이름 | 역할 |
|------|------|
| 송병철 | 백엔드 (인증/태스크/인프라) |
| 송승준 | 백엔드 (회의록/Hash Vault/Notion) |
| 손정협 | 백엔드 (AI/Score Engine/Calendar) |
| 손정효 | 프론트엔드 전체 |
