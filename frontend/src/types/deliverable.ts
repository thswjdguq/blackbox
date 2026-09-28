// ──────────────────────────────────────────────────────────────────────────
// 제출물 도메인 타입 (CONTRACTS.md §1 현재 약속 + K-13/K-14 확장)
//
// 변경 이력
//   C-10: DeliverableRequirement, Deliverable, 요청 페이로드, DeliverableProgress 추가
//   K-14: DeliverableRequirement 에 assessment 필드 추가 (A-04 병합 완료)
// ──────────────────────────────────────────────────────────────────────────

// ── 요구사항 충족 확인 (K-14) ─────────────────────────────────────────────

/**
 * 사람이 요구사항을 보고 "충족됐다"고 확인한 기록.
 * null = 아직 확인 전.
 * 요구사항 문구(content)가 바뀌면 서버가 자동으로 null 로 초기화한다(K-14 §2).
 */
export interface RequirementAssessment {
  /** 확인한 팀원 */
  assessedBy: { userId: string; name: string };
  /** 확인한 시각 — 시간대 포함 ISO-8601 */
  assessedAt: string;
}

// ── 요구사항 ──────────────────────────────────────────────────────────────

/**
 * 제출물에 딸린 요구사항 한 건.
 * required=true 인 항목이 필수 요구사항이며, K-13 진척 분모가 된다.
 * assessment=null 이면 아직 확인 전; 객체면 누가 언제 확인했는지 표시한다.
 */
export interface DeliverableRequirement {
  id: string;
  content: string;
  /** true=필수(K-13 분모에 포함), false=선택 */
  required: boolean;
  /** K-14 충족 확인 상태. null=미확인, 객체=확인 완료 */
  assessment: RequirementAssessment | null;
}

// ── 제출물 ────────────────────────────────────────────────────────────────

/**
 * 제출물 전체 응답 (GET /api/projects/{projectId}/deliverables/{id}).
 * requirements 배열은 목록 API 에서도 포함된다고 가정한다.
 */
export interface Deliverable {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  /** YYYY-MM-DD 형식. due_at 으로 임의 변경 금지(CONTRACTS §1) */
  dueDate: string;
  submissionMethod: string | null;
  ownerId: string | null;
  ownerName: string | null;
  requirements: DeliverableRequirement[];
}

// ── 요청 페이로드 ─────────────────────────────────────────────────────────

/** POST /api/projects/{projectId}/deliverables */
export interface CreateDeliverablePayload {
  /** 필수 */
  title: string;
  /** 필수, YYYY-MM-DD */
  dueDate: string;
  description?: string;
  submissionMethod?: string;
  ownerId?: string;
}

/** PUT /api/projects/{projectId}/deliverables/{id} — 변경할 필드만 전송 */
export type UpdateDeliverablePayload = Partial<CreateDeliverablePayload>;

/** POST .../requirements */
export interface CreateRequirementPayload {
  content: string;
  required: boolean;
}

/** PUT .../requirements/{reqId} — 변경할 필드만 전송 */
export type UpdateRequirementPayload = Partial<CreateRequirementPayload>;

// ── K-13 진척 응답 ────────────────────────────────────────────────────────

/**
 * GET /api/projects/{projectId}/deliverables/{id}/progress
 *
 * 주의사항(K-13 §2, CONTRACTS §3-2):
 *  - tasks.total=0 → "연결 업무 없음". 100% 표시 금지.
 *  - requiredRequirements.met=null → "충족 확인 기능 준비 중".
 *    null 을 0 으로 강제 변환하면 "미지원"이 "모두 미충족"으로 잘못 표시된다.
 *  - API 실패 → 가짜 0% 대신 오류 메시지 표시.
 *
 * 1차(현재): assessmentAvailable=false, met/percent=null.
 * 2차(A-04 이후): assessmentAvailable=true, met/percent 실제 값.
 */
export interface DeliverableProgress {
  deliverableId: string;
  tasks: {
    total: number;
    completed: number;
    /** 0~100, 소수 둘째 자리 HALF_UP */
    percent: number;
  };
  requiredRequirements: {
    total: number;
    /** null = 충족 확인 API 미지원(1차) */
    met: number | null;
    /** null = met 가 null 이면 함께 null */
    percent: number | null;
    /** false 동안 met/percent 를 UI 에 노출하지 않는다 */
    assessmentAvailable: boolean;
  };
}
