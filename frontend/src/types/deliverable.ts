import type { DeliverableStatus, ReviewDecisionResult } from "@/types/review";

// ── 요구사항 ──────────────────────────────────────────────────────────────

/**
 * 요구사항 충족 확인 정보 (K-14).
 * null 이면 아직 확인 전, 객체면 누가 언제 확인했는지 표시한다.
 */
export interface RequirementAssessment {
  assessedBy: { userId: string; name: string };
  assessedAt: string; // 시간대 포함 ISO-8601
}

export interface DeliverableRequirement {
  id: string;
  content: string;
  required: boolean;
  assessment: RequirementAssessment | null; // null = 확인 전 (K-14)
}

// ── 제출물 ────────────────────────────────────────────────────────────────

export interface Deliverable {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  dueDate: string;             // "YYYY-MM-DD" — due_at 으로 임의 변경 금지
  submissionMethod: string | null;
  ownerId: string | null;
  ownerName: string | null;
  requirements: DeliverableRequirement[];
  /** K-20 2장. A-20 이후 서버가 준다. 없으면 확정 전으로 본다 */
  status?: DeliverableStatus;
  /** 최신 회차의 결정. 회차가 없거나 결정 전이면 null */
  latestDecision?: ReviewDecisionResult | null;
}

// ── 요청 페이로드 ─────────────────────────────────────────────────────────

// 서버 PUT 은 전체 교체라 생성·수정 모두 모든 필드를 보낸다 (누락 시 null 로 저장됨)
export interface SaveDeliverablePayload {
  title: string;
  dueDate: string;
  description: string;
  submissionMethod: string;
  ownerId: string | null;
}

export interface SaveRequirementPayload {
  content: string;
  required: boolean;
}

// ── K-13 진척 응답 (B-10 서버, DeliverableProgressDtos) ──────────────────

/**
 * GET /api/projects/{projectId}/deliverables/{id}/progress
 *
 * - total=0 이면 percent 는 0 이지만 "없음"으로 표시한다. 0% 로 표시 금지.
 * - API 호출 자체가 실패하면 가짜 0% 대신 오류 메시지 표시.
 */
export interface DeliverableProgress {
  deliverableId: string;
  tasks: {
    total: number;
    completed: number;
    percent: number;
  };
  requiredRequirements: {
    total: number;
    met: number;
    percent: number;
    assessmentAvailable: boolean;
  };
}
