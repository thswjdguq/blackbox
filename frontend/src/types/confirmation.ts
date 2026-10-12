// 최종 확정·제출 기록 타입 — K-20 v0.1(#93) 3장 응답 형식을 따른다.
// 계약이 바뀌면 이 파일과 lib/api/confirmation.ts 를 먼저 맞추고 화면이 따라간다.

import type { DeliverableStatus, ReviewFile, UserRef } from "@/types/review";

/** 확정 전 세 검사. 화면은 code 로 고칠 곳을 고르고 detail 을 그대로 보여 준다 */
export type ConfirmationCheckCode = "REQUIRED_REQUIREMENTS" | "APPROVED_FILE" | "NO_UNRESOLVED_COMMENTS";

export interface ConfirmationCheck {
  code: ConfirmationCheckCode;
  passed: boolean;
  detail: string;
}

/** 지금 확정하면 확정본이 될 회차와 파일. APPROVED_FILE 을 통과했을 때만 있다 */
export interface ConfirmationCandidate {
  reviewId: string;
  roundNo: number;
  file: ReviewFile;
}

export interface ConfirmationRecord extends ConfirmationCandidate {
  confirmedBy: UserRef;
  confirmedAt: string;
  /** 확정본이 지금도 그 파일 이름의 최신 내용인가. false 여도 확정본은 바뀌지 않는다 */
  fileStillLatest: boolean;
}

export interface SubmissionRecord {
  channel: string | null;
  submittedAt: string;
  note: string | null;
  recordedBy: UserRef;
  recordedAt: string;
}

/** GET·PUT /confirmation, PUT /submission 이 모두 돌려주는 객체 */
export interface ConfirmationInfo {
  status: DeliverableStatus;
  /** 세 검사를 모두 통과했고 아직 확정 전인가. 요청한 사람의 역할은 보지 않는다 */
  canConfirm: boolean;
  /** 확정 전에는 세 항목이 이 순서로, 확정 뒤에는 빈 배열 */
  checks: ConfirmationCheck[];
  candidate: ConfirmationCandidate | null;
  /** 팀장·팀원이 한 명뿐 — 혼자서는 확정 조건을 채울 수 없다 */
  soleContributor: boolean;
  confirmation: ConfirmationRecord | null;
  submission: SubmissionRecord | null;
}

export interface SubmissionPayload {
  /** null 이면 서버가 제출물의 제출 경로를 쓴다 */
  channel: string | null;
  /** 실제로 낸 시각(시간대 포함 ISO-8601). 서버 시각보다 5분 넘게 뒤면 400 */
  submittedAt: string;
  note: string | null;
}
