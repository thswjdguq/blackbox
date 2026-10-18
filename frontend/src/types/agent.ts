/**
 * AI 도우미 (K-30 v0.6, docs/team/contracts/K-30-agent.md).
 * 화면 흐름은 docs/team/handoffs/A-K30-screen-20261011.md 6.1을 그대로 옮겼다. 서로 다르면 K-30이 맞다.
 */
export interface UserRef { userId: string; name: string }

export type Stage =
  | "NO_REQUIREMENT" | "NO_TASK" | "UNCOVERED_REQUIREMENT"
  | "TASKS_IN_PROGRESS" | "UNCHECKED_REQUIREMENT" | "READY_FOR_REVIEW"
  | "IN_REVIEW" | "CHANGES_REQUESTED" | "APPROVED" | "CONFIRMED" | "SUBMITTED";

export interface AgentStatus {
  available: boolean;
  unavailableReason: string | null;
  skills: { skill: string; label: string; allowed: boolean }[];
  /** 이 사람이 제안 카드를 고치고 채택·거절할 수 있는가. AI가 꺼져 있어도 역할대로 온다 */
  canDecide: boolean;
  /** 기한이 빠른 순. 제출물이 없으면 빈 배열. AI가 꺼져 있어도 온다 */
  stages: { deliverableId: string; title: string; dueDate: string; stage: Stage | string }[];
  pendingProposalCount: number;
}

export interface PlanContent {
  deliverable: { title: string; description: string | null; dueDate: string | null; submissionMethod: string | null };
  requirements: { key: string; content: string; required: boolean }[];
  tasks: { title: string; completionCriteria: string | null; dueDate: string | null; requirementKey: string | null }[];
}

export interface TasksContent {
  deliverableId: string | null;
  tasks: { title: string; completionCriteria: string | null; dueDate: string | null; requirementId: string | null }[];
}

interface ProposalBase {
  id: string;
  runId: string;
  status: "PENDING" | "ACCEPTED" | "REJECTED";
  title: string;
  rationale: string;
  /** 사람이 모델의 원본을 고쳤는가 */
  edited: boolean;
  requestedBy: UserRef;
  createdAt: string;
  decision: { result: "ACCEPTED" | "REJECTED"; decidedBy: UserRef; decidedAt: string } | null;
  /** 채택 전이나 거절이면 null. TASKS 카드는 deliverableId가 null이고 requirementIds가 비어 있다 */
  results: { deliverableId: string | null; requirementIds: string[]; taskIds: string[] } | null;
}

export type Proposal =
  | (ProposalBase & { kind: "DELIVERABLE_PLAN"; content: PlanContent })
  | (ProposalBase & { kind: "TASKS"; content: TasksContent });

export interface AgentOrigins { deliverableIds: string[]; requirementIds: string[]; taskIds: string[] }

export type AgentEvent =
  | { event: "step"; data: { label: string; tool: string } }
  | { event: "text"; data: { text: string } }
  | { event: "proposal"; data: Proposal }
  | { event: "done"; data: { runId: string; proposalIds: string[] } }
  | { event: "error"; data: { detail: string } };
