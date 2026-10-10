// 검토 API 래퍼 — K-10 v0.2(#69), K-11 v0.3(#59), K-12 v0.1(#60) DRAFT 경로.
// 서버 구현(A-11·B-20·B-21) 전에는 404 가 오며, 화면은 이를 "서버 준비 중"으로 보여준다 (가짜 데이터는 쓰지 않는다).

import api from "@/lib/api";
import type {
  CommentTaskResult,
  ReviewComment,
  ReviewDecisionResult,
  ReviewRound,
  ReviewSummary,
} from "@/types/review";

const reviews = (projectId: string, deliverableId: string) =>
  `/projects/${projectId}/deliverables/${deliverableId}/reviews`;

// ── K-10 검토 회차 ────────────────────────────────────────────────────────

export const getReviewSummary = (projectId: string, deliverableId: string) =>
  api.get<ReviewSummary>(reviews(projectId, deliverableId));

/** fileId 가 null 이면 파일 없는 중간 검토. 새 회차가 열리면 이전 승인은 확정 근거에서 빠진다 */
export const openReviewRound = (projectId: string, deliverableId: string, fileId: string | null) =>
  api.post<ReviewRound>(reviews(projectId, deliverableId), { fileId });

/** 최신 회차에만, 회차마다 한 번. 파일 올린 사람(403), 미해결 피드백이 남은 승인(409)은 서버가 거절한다 */
export const decideReviewRound = (projectId: string, deliverableId: string, reviewId: string, decision: ReviewDecisionResult) =>
  api.put<ReviewRound>(`${reviews(projectId, deliverableId)}/${reviewId}/decision`, { decision });

// ── K-11 피드백 ───────────────────────────────────────────────────────────

const comments = (projectId: string, deliverableId: string, reviewId: string) =>
  `${reviews(projectId, deliverableId)}/${reviewId}/comments`;

export const getComments = (projectId: string, deliverableId: string, reviewId: string) =>
  api.get<ReviewComment[]>(comments(projectId, deliverableId, reviewId));

export const addComment = (projectId: string, deliverableId: string, reviewId: string, content: string) =>
  api.post<ReviewComment>(comments(projectId, deliverableId, reviewId), { content });

/**
 * 사유는 선택이다. 본문은 JSON 객체여야 하므로 사유가 없으면 {} 를 보낸다 (K-11 2장).
 * 연결 업무가 끝나지 않았어도 해결할 수 있다. 이미 해결된 항목에 다시 보내면 현재 상태를 돌려준다 (멱등)
 */
export const resolveComment = (projectId: string, deliverableId: string, reviewId: string, commentId: string, reason: string) =>
  api.put<ReviewComment>(
    `${comments(projectId, deliverableId, reviewId)}/${commentId}/resolution`,
    reason.trim() ? { reason: reason.trim() } : {}
  );

export const reopenComment = (projectId: string, deliverableId: string, reviewId: string, commentId: string) =>
  api.delete<ReviewComment>(`${comments(projectId, deliverableId, reviewId)}/${commentId}/resolution`);

// ── K-12 피드백 → 업무 ─────────────────────────────────────────────────────

/** 같은 피드백으로 다시 보내면 기존 업무를 돌려준다 (201 → 200, created=false) */
export const createTaskFromComment = (
  projectId: string,
  deliverableId: string,
  reviewId: string,
  commentId: string,
  payload: { title: string; completionCriteria?: string }
) => api.post<CommentTaskResult>(`${comments(projectId, deliverableId, reviewId)}/${commentId}/task`, payload);

/** 서버에 검토 API 가 아직 없을 때 (라우트 없음 404 / 405) */
export function isReviewApiMissing(err: unknown): boolean {
  const res = (err as { response?: { status?: number; data?: { detail?: string } } })?.response;
  if (!res) return false;
  if (res.status === 405) return true;
  // 컨트롤러가 없으면 Spring 이 "No static resource ..." 404 를 준다. 도메인 404(제출물 없음 등)와 구분한다
  return res.status === 404 && (res.data?.detail ?? "").startsWith("No static resource");
}
