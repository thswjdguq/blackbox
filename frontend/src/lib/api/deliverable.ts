/**
 * 제출물 도메인 API 래퍼
 *
 * 진척 조회(getDeliverableProgress)는 K-13 계약 AGREED + B-10 서버 병합 후
 * USE_MOCK_PROGRESS 를 false 로 바꾼다.
 */

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

export const getDeliverables = (projectId: string) =>
  api.get<Deliverable[]>(`/projects/${projectId}/deliverables`);

export const getDeliverable = (projectId: string, deliverableId: string) =>
  api.get<Deliverable>(`/projects/${projectId}/deliverables/${deliverableId}`);

export const createDeliverable = (
  projectId: string,
  payload: CreateDeliverablePayload
) => api.post<Deliverable>(`/projects/${projectId}/deliverables`, payload);

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
 * 업무가 연결된 제출물 삭제 시 서버에서 409 반환 — 호출부에서 처리 필요.
 */
export const deleteDeliverable = (projectId: string, deliverableId: string) =>
  api.delete(`/projects/${projectId}/deliverables/${deliverableId}`);

// ── 요구사항 CRUD ─────────────────────────────────────────────────────────

export const createRequirement = (
  projectId: string,
  deliverableId: string,
  payload: CreateRequirementPayload
) =>
  api.post<DeliverableRequirement>(
    `/projects/${projectId}/deliverables/${deliverableId}/requirements`,
    payload
  );

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
 * USE_MOCK_PROGRESS=true 동안 with-tasks fixture 를 반환한다.
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
    return { ...fixture.default, deliverableId } as DeliverableProgress;
  }

  const res = await api.get<DeliverableProgress>(
    `/projects/${projectId}/deliverables/${deliverableId}/progress`
  );
  return res.data;
};
