/**
 * 제출물 도메인 API 래퍼
 *
 * - 모든 호출은 lib/api.ts 의 axios 인스턴스를 경유한다 (/api 기본 경로 포함).
 * - 진척 조회(getDeliverableProgress)는 K-13 계약 AGREED + B-10 서버 병합 전까지
 *   fixture를 반환한다. 전환 기준이 충족되면 USE_MOCK_PROGRESS 를 false 로 바꾼다.
 */

import api from "@/lib/api";
import type {
  Deliverable,
  CreateDeliverablePayload,
  UpdateDeliverablePayload,
  CreateRequirementPayload,
  UpdateRequirementPayload,
  DeliverableProgress,
} from "@/types/deliverable";

// K-13 계약 AGREED + B-10 병합 완료 시 false 로 전환
const USE_MOCK_PROGRESS = true;

// ── 제출물 CRUD ──────────────────────────────────────────────────────────

/** 프로젝트의 제출물 목록 조회 */
export const getDeliverables = (projectId: string) =>
  api.get<Deliverable[]>(`/projects/${projectId}/deliverables`);

/** 제출물 단건 조회 */
export const getDeliverable = (projectId: string, deliverableId: string) =>
  api.get<Deliverable>(`/projects/${projectId}/deliverables/${deliverableId}`);

/** 제출물 생성 (팀장·팀원만 가능) */
export const createDeliverable = (
  projectId: string,
  payload: CreateDeliverablePayload
) => api.post<Deliverable>(`/projects/${projectId}/deliverables`, payload);

/** 제출물 수정 (팀장·팀원만 가능) */
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
 * 제출물 삭제 (팀장·팀원만 가능)
 * 업무가 연결된 제출물 삭제 시 서버에서 409 반환 — 호출부에서 처리 필요.
 */
export const deleteDeliverable = (
  projectId: string,
  deliverableId: string
) =>
  api.delete(`/projects/${projectId}/deliverables/${deliverableId}`);

// ── 요구사항 CRUD ────────────────────────────────────────────────────────

/** 요구사항 추가 */
export const createRequirement = (
  projectId: string,
  deliverableId: string,
  payload: CreateRequirementPayload
) =>
  api.post(
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
  api.put(
    `/projects/${projectId}/deliverables/${deliverableId}/requirements/${requirementId}`,
    payload
  );

/**
 * 요구사항 삭제
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

// ── 진척 조회 (K-13) ─────────────────────────────────────────────────────

/**
 * 제출물 진척 조회.
 * USE_MOCK_PROGRESS=true 인 동안은 with-tasks fixture 를 반환한다.
 * fixture 반환 시 콘솔에 경고를 출력해 운영 빌드에서 mock 인지 바로 알 수 있게 한다.
 */
export const getDeliverableProgress = async (
  projectId: string,
  deliverableId: string
): Promise<DeliverableProgress> => {
  if (USE_MOCK_PROGRESS) {
    console.warn("[MOCK] K-13 progress — fixture 사용 중. 계약 AGREED 후 전환 필요.");
    const fixture = await import(
      "@/__fixtures__/deliverable-progress/with-tasks.json"
    );
    // fixture의 deliverableId를 실제 ID로 교체해 반환
    return { ...fixture.default, deliverableId } as DeliverableProgress;
  }

  const res = await api.get<DeliverableProgress>(
    `/projects/${projectId}/deliverables/${deliverableId}/progress`
  );
  return res.data;
};
