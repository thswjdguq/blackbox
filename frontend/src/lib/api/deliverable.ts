/** 제출물 도메인 API 래퍼 */

import api from "@/lib/api";
import type {
  Deliverable,
  SaveDeliverablePayload,
  SaveRequirementPayload,
  DeliverableProgress,
  DeliverableRequirement,
} from "@/types/deliverable";

// ── 제출물 CRUD ───────────────────────────────────────────────────────────

export const getDeliverables = (projectId: string) =>
  api.get<Deliverable[]>(`/projects/${projectId}/deliverables`);

/** 다른 프로젝트의 제출물 id 도 404 로 응답한다 (존재 여부를 드러내지 않음) */
export const getDeliverable = (projectId: string, deliverableId: string) =>
  api.get<Deliverable>(`/projects/${projectId}/deliverables/${deliverableId}`);

export const createDeliverable = (
  projectId: string,
  payload: SaveDeliverablePayload
) => api.post<Deliverable>(`/projects/${projectId}/deliverables`, payload);

export const updateDeliverable = (
  projectId: string,
  deliverableId: string,
  payload: SaveDeliverablePayload
) =>
  api.put<Deliverable>(
    `/projects/${projectId}/deliverables/${deliverableId}`,
    payload
  );

/**
 * 업무가 연결된 제출물 삭제 시 서버에서 409 반환 — 호출부에서 처리 필요.
 */
export const deleteDeliverable = (projectId: string, deliverableId: string) =>
  api.delete(`/projects/${projectId}/deliverables/${deliverableId}`);

// ── 요구사항 CRUD ─────────────────────────────────────────────────────────

export const createRequirement = (
  projectId: string,
  deliverableId: string,
  payload: SaveRequirementPayload
) =>
  api.post<DeliverableRequirement>(
    `/projects/${projectId}/deliverables/${deliverableId}/requirements`,
    payload
  );

export const updateRequirement = (
  projectId: string,
  deliverableId: string,
  requirementId: string,
  payload: SaveRequirementPayload
) =>
  api.put<DeliverableRequirement>(
    `/projects/${projectId}/deliverables/${deliverableId}/requirements/${requirementId}`,
    payload
  );

/**
 * 업무가 연결된 요구사항 삭제 시 서버에서 409 반환 — 호출부에서 처리 필요.
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

export const assessRequirement = (
  projectId: string,
  deliverableId: string,
  requirementId: string
) =>
  api.put<DeliverableRequirement>(
    `/projects/${projectId}/deliverables/${deliverableId}/requirements/${requirementId}/assessment`
  );

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
 * 실패 시 가짜 0% 를 반환하지 않는다. 호출부에서 오류로 표시한다(K-13 §3).
 */
export const getDeliverableProgress = async (
  projectId: string,
  deliverableId: string
): Promise<DeliverableProgress> => {
  const res = await api.get<DeliverableProgress>(
    `/projects/${projectId}/deliverables/${deliverableId}/progress`
  );
  return res.data;
};
