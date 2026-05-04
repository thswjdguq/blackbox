# JWT 인증 개선 제안 — A안 (최소 DB 변경)

## 목적
리프레시 토큰 관련 보안·운영 리스크를 최소한의 DB 변경으로 개선합니다. 목표는 토큰 탈취 시 피해 축소, 서버 측 무효화(단일 토큰) 및 회전(rotation) 지원입니다.

## 현재 상태(참조)
- 토큰 발급/검증/갱신 로직: `AuthController` / `AuthService` → [backend/src/main/java/com/blackbox/controller/AuthController.java](backend/src/main/java/com/blackbox/controller/AuthController.java#L1-L40), [backend/src/main/java/com/blackbox/service/AuthService.java](backend/src/main/java/com/blackbox/service/AuthService.java#L1-L200)  
- JWT 처리: `JwtService` → [backend/src/main/java/com/blackbox/security/JwtService.java](backend/src/main/java/com/blackbox/security/JwtService.java#L1-L200)  
- 인증 필터: `JwtAuthenticationFilter` → [backend/src/main/java/com/blackbox/security/JwtAuthenticationFilter.java](backend/src/main/java/com/blackbox/security/JwtAuthenticationFilter.java#L1-L80)

## 문제점 요약
- 클라이언트에 보관된 리프레시 토큰(로컬스토리지 등)은 XSS에 취약함.  
- 서버에서 토큰 상태를 검증·무효화할 수 없어 탈취 대응 불가.  
- 리프레시 토큰 재사용(탈취 후 재사용) 탐지 불가.  
- 다중 기기(장치별 세션) 미지원(현재 단일 토큰 정책임).  
- 발급 · 재발급 로그 부족으로 사고 분석이 어려움.

## A안 개요 (왜, 무엇을 바꾸는가)
- 왜: DB 변경을 최소화하면서 리프레시 토큰 무효화와 회전을 구현하여 보안성을 빠르게 개선하기 위함.  
- 무엇을 바꿈: `users` 테이블에 단일 컬럼 `refresh_token_hash`(SHA‑256 hex, 64자)를 추가하고, 서버는 리프레시 토큰의 해시를 저장·비교·회전함.

## 기대 효과
- 평문 토큰 DB 저장 제거 → DB 탈취 시 직접 사용 위험 감소.  
- 토큰 회전으로 이전 토큰 즉시 무효화 가능(단일 토큰 정책 하).  
- 비교적 빠른 배포(스키마 변경 최소).

## 상세 적용 방안

### 1) DB 마이그레이션
- 파일 예시: `backend/src/main/resources/db/migration/VXX__add_refresh_token_hash.sql`
```sql
ALTER TABLE users
	ADD COLUMN refresh_token_hash VARCHAR(64);
```

### 2) 엔티티 변경
- `User` 엔티티에 필드 추가:
```java
@Column(name = "refresh_token_hash", length = 64)
private String refreshTokenHash;
```
(파일: [backend/src/main/java/com/blackbox/entity/User.java](backend/src/main/java/com/blackbox/entity/User.java#L1-L200))

### 3) 서버 로직 변경 (`AuthService`)
- 토큰 발급(`issueTokenPair`) 시 생성된 refresh 토큰의 SHA‑256 해시를 `user.refreshTokenHash`에 저장.  
- `/api/auth/refresh`에서 들어온 refresh 토큰의 해시와 DB 값을 비교:
	- 일치: 새 access + refresh 발급 → 새 refresh 해시로 DB 덮어쓰기(회전).  
	- 불일치: 거부(재사용/탈취 의심), 감사 로그 기록 및 401 반환.

간단 핵심 의사코드:
```java
private static String sha256Hex(String input) { ... } // SHA-256 hex 변환

// 발급 시
String refresh = jwtService.generateRefreshToken(email);
user.setRefreshTokenHash(sha256Hex(refresh));
userRepository.save(user);

// refresh 엔드포인트
String incomingHash = sha256Hex(req.refreshToken());
if (!incomingHash.equalsIgnoreCase(user.getRefreshTokenHash())) {
		// 거부 + 로그
		throw new InvalidCredentialsException();
}
// 회전: 새 refresh 발급 및 DB 업데이트
```

### 4) 로그아웃 API(권장)
- `POST /api/auth/logout` 구현: 현재 사용자의 `refresh_token_hash`를 `NULL`로 설정하여 서버 측 무효화.

### 5) 프론트 변경(선택적, 보안 권장)
- 즉시 다중기기 지원이 필요 없으므로 기존 방식(클라이언트에 refresh 반환) 그대로 유지 가능.  
- 권장: 추후 httpOnly Secure 쿠키로 전환(이때는 `withCredentials: true` 및 CORS 설정 점검 필요).

## 테스트 시나리오
1. 로그인 → DB에 `refresh_token_hash` 저장 확인.  
2. access 만료 → `/api/auth/refresh` 성공 및 DB 해시 갱신 확인.  
3. 이전(회전 전) refresh 토큰으로 재요청 → 401/거부.  
4. 로그아웃 → `refresh_token_hash`가 `NULL`인지 확인.  
5. 변조된 토큰 → 401 응답 확인.

## 장단점
- 장점: 빠른 적용, 해시 저장으로 보안 개선, 회전으로 무효화 가능.  
- 단점: 단일 토큰(다중기기 미지원), 클라이언트 측 XSS 위험은 별도 조치 필요.

## 운영·마이그레이션 주의사항
- 기존 사용자가 있으면 최초 배포 후 재로그인 통해 해시가 채워짐. 즉시 강제 전환이 필요하면 별도 사용자 재로그인 정책 필요.  
- `JWT_SECRET` 안전 관리 및 TLS 강제화 필수.  
- CORS/쿠키 변경 시 `SecurityConfig`의 CORS 설정과 프론트의 요청 설정을 검토해야 함 ([backend/src/main/java/com/blackbox/config/SecurityConfig.java](backend/src/main/java/com/blackbox/config/SecurityConfig.java#L1-L80), [frontend/src/lib/api.ts](frontend/src/lib/api.ts)).

## 권장 후속 작업(우선순위)
1. 마이그레이션 및 `AuthService` 변경 적용(단기).  
2. 로그아웃 API와 감사 로깅(`ActivityLogService.record(...)`) 추가.  
3. 중기: 클라이언트 httpOnly cookie 전환 검토.  
4. 장기: 필요 시 `refresh_tokens` 테이블 또는 Redis 도입으로 다중기기/세션 관리 확장.

---

참고 파일
- `AuthService`: [backend/src/main/java/com/blackbox/service/AuthService.java](backend/src/main/java/com/blackbox/service/AuthService.java#L1-L200)  
- `JwtService`: [backend/src/main/java/com/blackbox/security/JwtService.java](backend/src/main/java/com/blackbox/security/JwtService.java#L1-L200)  
- `JwtAuthenticationFilter`: [backend/src/main/java/com/blackbox/security/JwtAuthenticationFilter.java](backend/src/main/java/com/blackbox/security/JwtAuthenticationFilter.java#L1-L80)  
- 프론트 인터셉터: [frontend/src/lib/api.ts](frontend/src/lib/api.ts)
