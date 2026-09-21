// ── 제출물·요구사항 타입 (CONTRACTS.md 1항 현재 약속 기준) ─────────────────

/**
 * 제출물에 딸린 요구사항 하나.
 * required=true 인 항목이 필수 요구사항이며, 진척 계산(K-13)의 분모가 된다.
 */
export interface Requirement {
  id: string;
  content: string;
  required: boolean; // true=필수, false=선택
}

/**
 * 제출물 전체 응답 (GET /api/projects/{projectId}/deliverables/{id}).
 * requirements 배열은 목록 API에서도 포함되어 반환된다고 가정한다.
 * 실제 응답에 포함되지 않는 경우 [] 로 처리한다.
 */
export interface Deliverable {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  dueDate: string;             // "YYYY-MM-DD" — due_at 으로 임의 변경 금지
  submissionMethod: string | null;
  ownerId: string | null;
  ownerName: string | null;
  requirements: Requirement[];
}

// ── 요청 페이로드 ─────────────────────────────────────────────────────────

/** POST /api/projects/{projectId}/deliverables */
export interface CreateDeliverablePayload {
  title: string;     // 필수
  dueDate: string;   // 필수, "YYYY-MM-DD"
  description?: string;
  submissionMethod?: string;
  ownerId?: string;
}

/** PUT /api/projects/{projectId}/deliverables/{id} */
export type UpdateDeliverablePayload = Partial<CreateDeliverablePayload>;

/** POST /api/projects/{projectId}/deliverables/{id}/requirements */
export interface CreateRequirementPayload {
  content: string;
  required: boolean;
}

/** PUT /api/projects/{projectId}/deliverables/{id}/requirements/{reqId} */
export type UpdateRequirementPayload = Partial<CreateRequirementPayload>;

// ── K-13 진척 응답 (DRAFT — 계약 AGREED 전까지 fixture 사용) ──────────────

/**
 * GET /api/projects/{projectId}/deliverables/{id}/progress
 *
 * 주의사항 (K-13 §2):
 *  - tasks.total=0 이면 "연결 업무 없음" 표시. 100% 로 표시 금지.
 *  - requiredRequirements.met=null 이면 "충족 확인 기능 준비 중" 표시.
 *  - API 호출 자체가 실패하면 가짜 0% 대신 오류 메시지 표시.
 */
export interface DeliverableProgress {
  deliverableId: string;
  tasks: {
    total: number;
    completed: number;
    percent: number; // 0~100, 소수 둘째 자리
  };
  requiredRequirements: {
    total: number;
    met: number | null;      // null = 충족 확인 기능 미지원
    percent: number | null;  // null = met 가 null 이면 함께 null
    assessmentAvailable: boolean;
  };
}
