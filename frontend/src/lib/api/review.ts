// ──────────────────────────────────────────────────────────────────────────
// 검토 도메인 API 래퍼 (K-10 / K-11) (C-20)
//
// K-10: 검토 회차·파일 버전 조회/생성
// K-11: 피드백 코멘트 작성·해결 토글
//
// Mock 전환 조건:
//   USE_MOCK_REVIEW = false
//   → K-10 계약 AGREED + A-10 서버 브랜치 main 병합 완료
//
// mock 함수는 모두 console.warn 을 출력하므로 운영 빌드에서 즉시 식별 가능.
// ──────────────────────────────────────────────────────────────────────────

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

/** getReviewData 반환 형태 — 상태 + 회차 목록 */
export interface ReviewData {
  deliverableStatus: DeliverableStatus;
  rounds: ReviewRound[];
}

// ── 검토 조회 (K-10) ──────────────────────────────────────────────────────

/**
 * 제출물 검토 현황(상태 + 회차 목록) 조회.
 * mock 모드에서는 with-comments.json 을 반환하고 deliverableId 를 주입한다.
 *
 * 검토 탭 진입 시 한 번만 호출(lazy). 이후 상태는 낙관적 업데이트로 유지.
 */
export const getReviewData = async (
  projectId: string,
  deliverableId: string
): Promise<ReviewData> => {
  if (USE_MOCK_REVIEW) {
    console.warn("[MOCK] K-10 review — fixture 사용 중. 계약 AGREED 후 전환 필요.");
    const fixture = await import("@/__fixtures__/review/with-comments.json");
    // JSON import 는 status 를 string 으로 추론하므로 unknown 경유 캐스팅
    const data = fixture.default as unknown as ReviewData;
    return {
      ...data,
      // fixture 의 deliverableId 를 실제 값으로 덮어써서 URL 불일치 방지
      rounds: data.rounds.map((r) => ({ ...r, deliverableId })),
    };
  }

  const res = await api.get<ReviewData>(
    `/projects/${projectId}/deliverables/${deliverableId}/reviews`
  );
  return res.data;
};

// ── 검토 요청 (K-10) ──────────────────────────────────────────────────────

/**
 * 검토 요청 — DRAFT → IN_REVIEW 전환 트리거.
 * mock 모드에서는 crypto.randomUUID() 로 ID 를 생성해 새 회차를 반환한다.
 * 실제 API 연결 후에는 서버가 roundNumber 를 관리한다.
 */
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

// ── 코멘트 작성 (K-11) ────────────────────────────────────────────────────

/**
 * 피드백 코멘트 작성.
 * mock 모드에서는 authorName="나" 로 즉시 반환 — 낙관적 업데이트에 그대로 사용.
 * 실제 연결 후에는 서버에서 인증 토큰으로 authorId/Name 을 채운다.
 */
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

// ── 해결 토글 (K-11) ──────────────────────────────────────────────────────

/**
 * 피드백 해결/미해결 토글.
 * 화면에서는 낙관적으로 resolved 를 뒤집고, 서버 실패 시 원상 복구한다.
 * mock 모드에서는 아무것도 하지 않고 즉시 반환한다(낙관적 업데이트로 충분).
 */
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
