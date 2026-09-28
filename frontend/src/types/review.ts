// ──────────────────────────────────────────────────────────────────────────
// 검토 도메인 타입 (K-10 / K-11 계약 기반)
//
// 상태 기계: DRAFT → IN_REVIEW → CONFIRMED → SUBMITTED (CONTRACTS §3-7)
//   - IN_REVIEW → DRAFT 보완 전환 허용, 이전 회차 기록 유지
//   - CONFIRMED 이후 파일·요구사항·코멘트 수정 차단 (K-20 담당)
//
// C-20: K-10/K-11 계약 미합의 — USE_MOCK_REVIEW=true 로 fixture 반환
//       계약 AGREED + A-10 서버 병합 후 false 로 전환
// ──────────────────────────────────────────────────────────────────────────

// ── 제출물 상태 ───────────────────────────────────────────────────────────

/**
 * 제출물의 라이프사이클 상태.
 * C-20 에서 UI 배지로 표시하고, C-30(확정·제출)에서 전환 명령을 추가한다.
 */
export type DeliverableStatus = "DRAFT" | "IN_REVIEW" | "CONFIRMED" | "SUBMITTED";

// ── 검토 코멘트 (K-11) ────────────────────────────────────────────────────

/**
 * 검토 회차 안의 피드백 코멘트 한 건.
 * resolved=false 인 코멘트가 남아 있으면 수정본 안내 배너를 표시한다.
 * 피드백→업무 전환(K-12)은 linkedTaskId 로 연결된다.
 */
export interface ReviewComment {
  id: string;
  /** 속한 회차 ID */
  reviewId: string;
  authorId: string;
  authorName: string;
  content: string;
  /** false=미해결(수정 필요), true=해결됨 */
  resolved: boolean;
  /** K-12 피드백→업무 전환 시 채워진다. 없으면 null */
  linkedTaskId: string | null;
  createdAt: string;
}

// ── 검토 회차 (K-10) ──────────────────────────────────────────────────────

/**
 * OPEN=현재 검토 진행 중, CLOSED=회차 종료(수정본 제출 후 다음 회차로 넘어감).
 * OPEN 회차에서만 코멘트 작성·해결 토글이 가능하다.
 */
export type ReviewRoundStatus = "OPEN" | "CLOSED";

/**
 * 검토 회차 한 건 (K-10 §2).
 * fileVersionLabel: 짧은 해시(화면 표시용). 전체 해시는 Hash Vault 에서 검증.
 * fileVersionId: 회차 생성 시점의 파일 버전 ID. 이후 최신 파일이 바뀌어도 유지.
 */
export interface ReviewRound {
  id: string;
  deliverableId: string;
  roundNumber: number;
  /** 검토 대상 파일 버전 ID. 파일 없이 검토 요청 시 null */
  fileVersionId: string | null;
  /** 짧은 해시 표시용 (예: "a1b2c3d"). null 이면 화면에 "파일 없음" */
  fileVersionLabel: string | null;
  status: ReviewRoundStatus;
  createdAt: string;
  comments: ReviewComment[];
}

// ── 요청 페이로드 ─────────────────────────────────────────────────────────

/**
 * 검토 요청 — K-10 §중간 검토의 파일 생략 허용 여부는 계약에서 명시.
 * fileVersionId 생략 시 서버가 파일 없는 회차로 생성한다.
 */
export interface RequestReviewPayload {
  fileVersionId?: string;
}

/** 코멘트 작성 */
export interface AddCommentPayload {
  content: string;
}

/**
 * 해결 토글 — CONTRACTS §3-6:
 * 업무 DONE 후 피드백은 "반영 확인 대기" 상태.
 * 검토자가 confirmed 하고 재오픈 가능 여부는 K-11 에서 고정.
 */
export interface ResolveCommentPayload {
  resolved: boolean;
}
