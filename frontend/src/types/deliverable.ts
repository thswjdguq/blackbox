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
}

// ── 요청 페이로드 ─────────────────────────────────────────────────────────

export interface CreateDeliverablePayload {
  title: string;
  dueDate: string;
  description?: string;
  submissionMethod?: string;
  ownerId?: string;
}

export type UpdateDeliverablePayload = Partial<CreateDeliverablePayload>;

export interface CreateRequirementPayload {
  content: string;
  required: boolean;
}

export type UpdateRequirementPayload = Partial<CreateRequirementPayload>;

// ── K-13 진척 응답 (AGREED — B-10 병합 완료, USE_MOCK_PROGRESS=true 동안 fixture 사용) ──

/**
 * GET /api/projects/{projectId}/deliverables/{id}/progress
 *
 * - tasks.total=0 이면 "연결 업무 없음" 표시. 100% 로 표시 금지.
 * - requiredRequirements.met=null 이면 "충족 확인 기능 준비 중" 표시.
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
    met: number | null;
    percent: number | null;
    assessmentAvailable: boolean;
  };
}
