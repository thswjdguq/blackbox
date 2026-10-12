// 최종 확정·제출 기록 API — K-20 v0.1(#93) 2장 경로.
// 서버 구현(A-20) 전에는 404 가 오며, 화면은 이를 "서버 준비 중"으로 보여준다 (가짜 데이터는 쓰지 않는다).

import api from "@/lib/api";
import type { ConfirmationInfo, SubmissionPayload } from "@/types/confirmation";

const base = (projectId: string, deliverableId: string) =>
  `/projects/${projectId}/deliverables/${deliverableId}`;

/** 확정 준비와 확정 정보. 팀장·팀원·관찰자 */
export const getConfirmation = (projectId: string, deliverableId: string) =>
  api.get<ConfirmationInfo>(`${base(projectId, deliverableId)}/confirmation`);

/** 팀장만. 화면이 보여 준 candidate.reviewId 를 넣는다. 그 사이 대상이 바뀌면 409 */
export const confirmDeliverable = (projectId: string, deliverableId: string, reviewId: string) =>
  api.put<ConfirmationInfo>(`${base(projectId, deliverableId)}/confirmation`, { reviewId });

/** 팀장·팀원. CONFIRMED 에서 한 번. 같은 내용 재요청은 200, 다른 내용은 409 */
export const recordSubmission = (projectId: string, deliverableId: string, payload: SubmissionPayload) =>
  api.put<ConfirmationInfo>(`${base(projectId, deliverableId)}/submission`, payload);
