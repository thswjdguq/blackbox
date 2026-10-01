// 검토 회차(K-10)·피드백(K-11)·피드백 업무 전환(K-12) 타입
//
// K-11·K-12 는 v0.1 DRAFT(#59·#60) 응답 형식을 따른다.
// K-10 계약 파일은 아직 없다. 회차 필드는 A-10 결정안(#58)과 CONTRACTS 3장, C 가 #58 에 요청한 필드 기준이며
// K-10 초안이 나오면 이 파일과 lib/api/review.ts 만 맞추면 되도록 화면은 이 타입만 쓴다.

export interface UserRef {
  userId: string;
  name: string;
}

// ── K-10 검토 회차 ────────────────────────────────────────────────────────

/** 회차 결정. null 이면 아직 결정 전 (CONTRACTS 3장 3항) */
export type ReviewDecision = "APPROVED" | "CHANGES_REQUESTED";

/** 회차가 가리키는 파일 버전 요약 (A-10 결정 (다): 회차가 파일 버전을 직접 가리킨다) */
export interface ReviewFile {
  fileId: string;
  fileName: string;
  version: number;
  fileHash: string;
  /** 이 사람은 이 회차를 승인할 수 없다 (CONTRACTS 3장 3항) */
  uploaderId: string;
  uploaderName: string;
}

export interface ReviewRound {
  id: string;
  roundNo: number;
  /** null 이면 파일 없는 중간 검토 */
  file: ReviewFile | null;
  decision: ReviewDecision | null;
  decidedBy: UserRef | null;
  decidedAt: string | null;
  openedBy: UserRef;
  openedAt: string;
  /**
   * 이 회차의 승인이 지금 확정 근거인가. 같은 이름의 더 높은 버전이 올라왔거나
   * 더 늦은 회차가 열리면 false (CONTRACTS 3장 3항, K-20 과 같은 기준을 서버가 계산)
   */
  isCurrentBasis: boolean;
}

export type DeliverableStatus = "DRAFT" | "IN_REVIEW" | "CONFIRMED" | "SUBMITTED";

export interface ReviewSummary {
  deliverableStatus: DeliverableStatus;
  /** 제출물 전체 회차의 OPEN + REFLECTION_PENDING 합계. 0 일 때만 승인할 수 있다 */
  unresolvedCount: number;
  /** roundNo 오름차순 */
  rounds: ReviewRound[];
}

export interface OpenRoundPayload {
  /** 생략하면 파일 없는 중간 검토 */
  fileId?: string;
}

// ── K-11 피드백 ───────────────────────────────────────────────────────────

/** OPEN → (연결 업무 DONE) REFLECTION_PENDING → RESOLVED. 해결은 반영 판정이 아니다 (CONTRACTS 3장 6항) */
export type CommentStatus = "OPEN" | "REFLECTION_PENDING" | "RESOLVED";

export interface ReviewComment {
  id: string;
  reviewId: string;
  content: string;
  status: CommentStatus;
  author: UserRef;
  createdAt: string;
  linkedTaskId: string | null;
  resolution: {
    resolvedBy: UserRef;
    resolvedAt: string;
    reason: string;
  } | null;
}

// ── K-12 피드백 → 업무 ─────────────────────────────────────────────────────

export interface CommentTaskResult {
  commentId: string;
  taskId: string;
  /** false 면 이미 연결된 업무를 돌려준 것 (재시도) */
  created: boolean;
}
