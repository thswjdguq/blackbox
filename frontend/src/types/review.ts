// ── 제출물 상태 ───────────────────────────────────────────────────────────

export type DeliverableStatus = "DRAFT" | "IN_REVIEW" | "CONFIRMED" | "SUBMITTED";

// ── 검토 코멘트 (K-11) ────────────────────────────────────────────────────

export interface ReviewComment {
  id: string;
  reviewId: string;
  authorId: string;
  authorName: string;
  content: string;
  resolved: boolean;
  linkedTaskId: string | null;
  createdAt: string;
}

// ── 검토 회차 (K-10) ──────────────────────────────────────────────────────

export type ReviewRoundStatus = "OPEN" | "CLOSED";

export interface ReviewRound {
  id: string;
  deliverableId: string;
  roundNumber: number;
  fileVersionId: string | null;
  fileVersionLabel: string | null; // 짧은 해시 (표시용)
  status: ReviewRoundStatus;
  createdAt: string;
  comments: ReviewComment[];
}

// ── 요청 페이로드 ─────────────────────────────────────────────────────────

export interface RequestReviewPayload {
  fileVersionId?: string;
}

export interface AddCommentPayload {
  content: string;
}

export interface ResolveCommentPayload {
  resolved: boolean;
}
