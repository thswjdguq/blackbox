import type { AgentStatus } from "@/types/agent";

/**
 * 실행 종류의 등록 (화면 문서 3.4). 종류마다 다른 것은 입력을 받는 방법뿐이다.
 * 창은 이 표만 보고 그리고, 서버가 화면이 모르는 종류를 주면 그리지 않는다.
 * - none: 누르면 바로 시작 / question: 창 아래 입력 칸 / brief: 긴 글 입력 칸 / deliverable: 제출물 고르기
 */
export type SkillInput = "none" | "question" | "brief" | "deliverable";

export const SKILLS: Record<string, { input: SkillInput }> = {
  PLAN_FROM_BRIEF: { input: "brief" },
  BREAK_DOWN_TASKS: { input: "deliverable" },
  STATUS_BRIEF: { input: "none" },
  ASK: { input: "question" },
};

export const QUESTION_MAX = 1000;
export const BRIEF_MAX = 20000;

/** "할 수 있는 일" 버튼으로 그릴 것. ASK는 버튼이 아니라 창 아래 입력 칸이다 */
export function buttonSkills(status: AgentStatus) {
  return status.skills.filter((s) => s.allowed && SKILLS[s.skill] && SKILLS[s.skill].input !== "question");
}

export function canAsk(status: AgentStatus) {
  return status.skills.some((s) => s.skill === "ASK" && s.allowed);
}

export function skillLabel(status: AgentStatus | undefined, skill: string) {
  return status?.skills.find((s) => s.skill === skill)?.label ?? skill;
}

function allowed(status: AgentStatus, skill: string) {
  return status.skills.some((s) => s.skill === skill && s.allowed);
}

/** 확정됐거나 제출을 기록한 제출물. 더 할 일이 없어 추천과 업무 나누기에서 뺀다(K-30 0.7) */
export function isClosedStage(stage: string) {
  return stage === "CONFIRMED" || stage === "SUBMITTED";
}

/** 업무 나누기에서 고를 수 없는 이유. 고를 수 있으면 null. 서버도 이 경우를 400으로 막는다 */
export function breakDownBlocked(stage: string): string | null {
  if (stage === "NO_REQUIREMENT") return "요구사항이 없습니다";
  if (stage === "CONFIRMED") return "최종본이 확정됐습니다";
  if (stage === "SUBMITTED") return "제출을 기록했습니다";
  return null;
}

export interface Suggestion {
  /** 제출물이 없으면 null */
  deliverable: { id: string; title: string; dueDate: string } | null;
  sentence: string;
  skill: string;
  input: object;
}

/**
 * "지금 단계에 맞는 도움" (화면 문서 4.9의 표). 확정·제출된 것을 건너뛰고 기한이 가장 빠른 제출물의 단계로 고른다.
 * 4단계의 화면마다의 버튼도 이 규칙을 같이 쓴다. 맞는 실행이 없거나 허용되지 않으면 null.
 */
export function suggestFor(status: AgentStatus): Suggestion | null {
  if (status.stages.length === 0) {
    return allowed(status, "PLAN_FROM_BRIEF")
      ? { deliverable: null, sentence: "아직 제출물이 없습니다", skill: "PLAN_FROM_BRIEF", input: {} }
      : null;
  }
  // 모두 확정·제출됐으면 추천하지 않는다(새 계획은 "할 수 있는 일"에서 고른다)
  const first = status.stages.find((s) => !isClosedStage(s.stage));
  if (!first) return null;
  const deliverable = { id: first.deliverableId, title: first.title, dueDate: first.dueDate };
  const pick = (skill: string, sentence: string, input: object): Suggestion | null =>
    allowed(status, skill) ? { deliverable, sentence, skill, input } : null;
  switch (first.stage) {
    case "NO_TASK":
      return pick("BREAK_DOWN_TASKS", "요구사항에 연결된 업무가 아직 없습니다", { deliverableId: first.deliverableId });
    case "UNCOVERED_REQUIREMENT":
      return pick("BREAK_DOWN_TASKS", "업무가 없는 필수 요구사항이 있습니다", { deliverableId: first.deliverableId });
    case "TASKS_IN_PROGRESS":
      return pick("STATUS_BRIEF", "업무가 진행 중입니다", {});
    default:
      // NO_REQUIREMENT, UNCHECKED_REQUIREMENT, READY_FOR_REVIEW, 검토가 시작된 뒤의 값, 모르는 값: 맞는 실행이 없다(11장)
      return null;
  }
}
