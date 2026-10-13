import type { AgentStatus } from "@/types/agent";

/**
 * 실행 종류의 등록 (화면 문서 3.4). 종류마다 다른 것은 입력을 받는 방법뿐이다.
 * 창은 이 표만 보고 그리고, 서버가 화면이 모르는 종류를 주면 그리지 않는다.
 * 1단계에는 "지금 상태 살펴보기"와 "물어보기"만 있다. 계획 만들기·업무 나누기는 제안 카드와 함께 2단계에서 더한다.
 */
export type SkillInput = "none" | "question";

export const SKILLS: Record<string, { input: SkillInput }> = {
  STATUS_BRIEF: { input: "none" },
  ASK: { input: "question" },
};

export const QUESTION_MAX = 1000;

/** "할 수 있는 일" 버튼으로 그릴 것. ASK는 버튼이 아니라 창 아래 입력 칸이다 */
export function buttonSkills(status: AgentStatus) {
  return status.skills.filter((s) => s.allowed && SKILLS[s.skill]?.input === "none");
}

export function canAsk(status: AgentStatus) {
  return status.skills.some((s) => s.skill === "ASK" && s.allowed);
}

export function skillLabel(status: AgentStatus | undefined, skill: string) {
  return status?.skills.find((s) => s.skill === skill)?.label ?? skill;
}

/**
 * "지금 단계에 맞는 도움" (화면 문서 4.9의 표). 기한이 가장 빠른 제출물의 단계로 고른다.
 * 1단계에서는 업무가 진행 중일 때의 "지금 상태 살펴보기"만 있다. 맞는 실행이 없으면 null.
 */
export function suggestFor(status: AgentStatus): { title: string; dueDate: string; sentence: string; skill: string } | null {
  const first = status.stages[0];
  if (!first) return null;
  if (first.stage === "TASKS_IN_PROGRESS") {
    const skill = "STATUS_BRIEF";
    if (!status.skills.some((s) => s.skill === skill && s.allowed)) return null;
    return { title: first.title, dueDate: first.dueDate, sentence: "업무가 진행 중입니다", skill };
  }
  return null;
}
