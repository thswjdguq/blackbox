// 검토 회차(K-10)·피드백(K-11)·피드백 업무 전환(K-12) 타입
//
// K-10 v0.2(#69), K-11 v0.3(#59), K-12 v0.1(#60) DRAFT 응답 형식을 따른다.
// 계약이 바뀌면 이 파일과 lib/api/review.ts 를 먼저 맞추고 화면이 따라간다.

export interface UserRef {
  userId: string;
  name: string;
}

// ── K-10 검토 회차 ────────────────────────────────────────────────────────

export type ReviewDecisionResult = "APPROVED" | "CHANGES_REQUESTED";

/** 회차가 가리키는 파일 버전 (K-10 3장). 이 파일을 올린 사람은 이 회차를 결정할 수 없다 */
export interface ReviewFile {
  fileId: string;
  fileName: string;
  version: number;
  /** SHA-256 앞 7자. 화면 표시용 */
  shortHash: string;
  uploaderId: string;
  uploaderName: string;
}

/** 회차마다 한 번. 바꾸려면 새 회차를 연다 */
export interface ReviewDecision {
  result: ReviewDecisionResult;
  decidedBy: UserRef;
  decidedAt: string;
}

export interface ReviewRound {
  id: string;
  roundNo: number;
  /** 가장 최근 회차. 새 코멘트와 결정은 최신 회차에만 된다 */
  latest: boolean;
  /** null 이면 파일 없는 중간 검토 */
  file: ReviewFile | null;
  openedBy: UserRef;
  openedAt: string;
  /** null 이면 결정 전 */
  decision: ReviewDecision | null;
  /**
   * 이 회차의 승인이 지금 확정 근거인가 (K-10 3장). 서버가 계산한다.
   * 승인 + 최신 회차 + 파일 있음 + 같은 이름의 최신 버전과 내용(해시)이 같음
   */
  currentBasis: boolean;
}

/** CONFIRMED·SUBMITTED 는 K-20 이 추가한다 */
export type DeliverableStatus = "DRAFT" | "IN_REVIEW" | "CONFIRMED" | "SUBMITTED";

export interface ReviewSummary {
  deliverableStatus: DeliverableStatus;
  /** 제출물 전체의 OPEN + REFLECTION_PENDING 수. 0 일 때만 승인할 수 있다 */
  unresolvedCommentCount: number;
  /** roundNo 내림차순 (최신이 먼저) */
  rounds: ReviewRound[];
}

// ── K-11 피드백 ───────────────────────────────────────────────────────────

/** 서버가 조회 시 계산한다. 해결은 반영 판정이 아니다 (CONTRACTS 3장 6항) */
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
    /** 선택 입력. 비우면 null */
    reason: string | null;
  } | null;
}

// ── K-12 피드백 → 업무 ─────────────────────────────────────────────────────

export interface CommentTaskResult {
  commentId: string;
  taskId: string;
  /** false 면 이미 연결된 업무를 돌려준 것 (재시도) */
  created: boolean;
}
