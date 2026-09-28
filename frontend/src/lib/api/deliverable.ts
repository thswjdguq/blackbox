// ──────────────────────────────────────────────────────────────────────────
// 제출물 도메인 API 래퍼 (C-10)
//
// 모든 호출은 lib/api.ts 의 axios 인스턴스를 경유한다 (/api 기본 경로 포함).
// 진척 조회(K-13)는 B-10 서버 병합 전까지 fixture 를 반환한다.
//
// Mock 전환 조건:
//   USE_MOCK_PROGRESS = false
//   → K-13 계약 AGREED + B-10 서버 브랜치 main 병합 완료
// ──────────────────────────────────────────────────────────────────────────

import api from "@/lib/api";
import type {
  Deliverable,
  CreateDeliverablePayload,
  UpdateDeliverablePayload,
  CreateRequirementPayload,
  UpdateRequirementPayload,
  DeliverableProgress,
  DeliverableRequirement,
} from "@/types/deliverable";

// K-13 계약 AGREED + B-10 병합 완료 시 false 로 전환
const USE_MOCK_PROGRESS = true;

// ── 제출물 CRUD ───────────────────────────────────────────────────────────

/** 프로젝트의 제출물 목록 조회 */
export const getDeliverables = (projectId: string) =>
  api.get<Deliverable[]>(`/projects/${projectId}/deliverables`);

/** 제출물 단건 조회 */
export const getDeliverable = (projectId: string, deliverableId: string) =>
  api.get<Deliverable>(`/projects/${projectId}/deliverables/${deliverableId}`);

/** 제출물 생성 — 팀장·팀원만 가능(서버에서 403 반환, 화면에서도 버튼 숨김) */
export const createDeliverable = (
  projectId: string,
  payload: CreateDeliverablePayload
) => api.post<Deliverable>(`/projects/${projectId}/deliverables`, payload);

/** 제출물 수정 — 팀장·팀원만 가능 */
export const updateDeliverable = (
  projectId: string,
  deliverableId: string,
  payload: UpdateDeliverablePayload
) =>
  api.put<Deliverable>(
    `/projects/${projectId}/deliverables/${deliverableId}`,
    payload
  );

/**
 * 제출물 삭제 — 팀장·팀원만 가능.
 * 업무가 연결된 제출물 삭제 시 서버 409 반환(CONTRACTS §1 삭제 충돌).
 * 호출부에서 status===409 를 잡아 "연결 업무 먼저 해제" 안내를 표시한다.
 */
export const deleteDeliverable = (projectId: string, deliverableId: string) =>
  api.delete(`/projects/${projectId}/deliverables/${deliverableId}`);

// ── 요구사항 CRUD ─────────────────────────────────────────────────────────

/** 요구사항 추가 */
export const createRequirement = (
  projectId: string,
  deliverableId: string,
  payload: CreateRequirementPayload
) =>
  api.post<DeliverableRequirement>(
    `/projects/${projectId}/deliverables/${deliverableId}/requirements`,
    payload
  );

/** 요구사항 수정 */
export const updateRequirement = (
  projectId: string,
  deliverableId: string,
  requirementId: string,
  payload: UpdateRequirementPayload
) =>
  api.put<DeliverableRequirement>(
    `/projects/${projectId}/deliverables/${deliverableId}/requirements/${requirementId}`,
    payload
  );

/**
 * 요구사항 삭제.
 * 업무가 연결된 요구사항 삭제 시 서버 409 반환(CONTRACTS §1 삭제 충돌).
 */
export const deleteRequirement = (
  projectId: string,
  deliverableId: string,
  requirementId: string
) =>
  api.delete(
    `/projects/${projectId}/deliverables/${deliverableId}/requirements/${requirementId}`
  );

// ── 요구사항 충족 확인 (K-14) ─────────────────────────────────────────────
// A-04 가 main 에 병합되어 이미 서버 구현 완료.
// PUT = 충족으로 체크, DELETE = 체크 해제.
// 두 요청 모두 갱신된 요구사항 객체를 반환하므로 화면은 응답으로 다시 그린다.

/**
 * 요구사항 충족 확인 (체크).
 * 이미 체크된 요구사항에 다시 PUT 하면 확인자·시각이 마지막 요청 기준으로 갱신된다.
 */
export const assessRequirement = (
  projectId: string,
  deliverableId: string,
  requirementId: string
) =>
  api.put<DeliverableRequirement>(
    `/projects/${projectId}/deliverables/${deliverableId}/requirements/${requirementId}/assessment`
  );

/**
 * 요구사항 충족 확인 해제.
 * 미확인 요구사항에 DELETE 를 보내도 200 이다(K-14 §2).
 */
export const unassessRequirement = (
  projectId: string,
  deliverableId: string,
  requirementId: string
) =>
  api.delete<DeliverableRequirement>(
    `/projects/${projectId}/deliverables/${deliverableId}/requirements/${requirementId}/assessment`
  );

// ── 진척 조회 (K-13) ──────────────────────────────────────────────────────

/**
 * 제출물 진척 조회.
 * USE_MOCK_PROGRESS=true 동안 with-tasks fixture 를 반환한다.
 * mock 인지 즉시 알 수 있도록 콘솔에 경고를 출력한다.
 *
 * 주의: API 실패 시 가짜 0% 를 반환하지 않는다. 호출부에서 progressError=true
 *       로 처리해 화면에 오류 메시지를 표시한다(K-13 §3 500 처리).
 */
export const getDeliverableProgress = async (
  projectId: string,
  deliverableId: string
): Promise<DeliverableProgress> => {
  if (USE_MOCK_PROGRESS) {
    console.warn("[MOCK] K-13 progress — fixture 사용 중. B-10 병합 후 전환 필요.");
    const fixture = await import(
      "@/__fixtures__/deliverable-progress/with-tasks.json"
    );
    // fixture 의 deliverableId 를 실제 ID 로 교체해 반환
    return { ...fixture.default, deliverableId } as DeliverableProgress;
  }

  const res = await api.get<DeliverableProgress>(
    `/projects/${projectId}/deliverables/${deliverableId}/progress`
  );
  return res.data;
};
