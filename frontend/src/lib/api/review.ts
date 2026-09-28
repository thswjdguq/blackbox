/**
 * 검토 도메인 API 래퍼 (K-10 / K-11)
 *
 * K-10 계약 AGREED + A-10 서버 병합 전까지 fixture를 반환한다.
 * 전환 기준이 충족되면 USE_MOCK_REVIEW 를 false 로 바꾼다.
 */

import api from "@/lib/api";
import type {
  ReviewRound,
  DeliverableStatus,
  RequestReviewPayload,
  AddCommentPayload,
  ResolveCommentPayload,
  ReviewComment,
} from "@/types/review";

// K-10 계약 AGREED + A-10 서버 병합 완료 시 false 로 전환
const USE_MOCK_REVIEW = true;

export interface ReviewData {
  deliverableStatus: DeliverableStatus;
  rounds: ReviewRound[];
}

// ── 검토 조회 (K-10) ──────────────────────────────────────────────────────

export const getReviewData = async (
  projectId: string,
  deliverableId: string
): Promise<ReviewData> => {
  if (USE_MOCK_REVIEW) {
    console.warn("[MOCK] K-10 review — fixture 사용 중. 계약 AGREED 후 전환 필요.");
    const fixture = await import("@/__fixtures__/review/with-comments.json");
    return {
      ...fixture.default,
      rounds: fixture.default.rounds.map((r: ReviewRound) => ({
        ...r,
        deliverableId,
      })),
    } as ReviewData;
  }

  const res = await api.get<ReviewData>(
    `/projects/${projectId}/deliverables/${deliverableId}/reviews`
  );
  return res.data;
};

// ── 검토 요청 (K-10) ──────────────────────────────────────────────────────

export const requestReview = async (
  projectId: string,
  deliverableId: string,
  payload: RequestReviewPayload = {}
): Promise<ReviewRound> => {
  if (USE_MOCK_REVIEW) {
    console.warn("[MOCK] K-10 requestReview — 서버 호출 없이 mock 반환.");
    const now = new Date().toISOString();
    return {
      id: crypto.randomUUID(),
      deliverableId,
      roundNumber: 1,
      fileVersionId: payload.fileVersionId ?? null,
      fileVersionLabel: null,
      status: "OPEN",
      createdAt: now,
      comments: [],
    };
  }

  const res = await api.post<ReviewRound>(
    `/projects/${projectId}/deliverables/${deliverableId}/reviews`,
    payload
  );
  return res.data;
};

// ── 코멘트 CRUD (K-11) ────────────────────────────────────────────────────

export const addComment = async (
  projectId: string,
  deliverableId: string,
  reviewId: string,
  payload: AddCommentPayload
): Promise<ReviewComment> => {
  if (USE_MOCK_REVIEW) {
    console.warn("[MOCK] K-11 addComment — 서버 호출 없이 mock 반환.");
    return {
      id: crypto.randomUUID(),
      reviewId,
      authorId: "mock-user",
      authorName: "나",
      content: payload.content,
      resolved: false,
      linkedTaskId: null,
      createdAt: new Date().toISOString(),
    };
  }

  const res = await api.post<ReviewComment>(
    `/projects/${projectId}/deliverables/${deliverableId}/reviews/${reviewId}/comments`,
    payload
  );
  return res.data;
};

export const resolveComment = async (
  projectId: string,
  deliverableId: string,
  reviewId: string,
  commentId: string,
  payload: ResolveCommentPayload
): Promise<void> => {
  if (USE_MOCK_REVIEW) {
    console.warn("[MOCK] K-11 resolveComment — 서버 호출 없이 mock 반환.");
    return;
  }

  await api.patch(
    `/projects/${projectId}/deliverables/${deliverableId}/reviews/${reviewId}/comments/${commentId}/resolve`,
    payload
  );
};
