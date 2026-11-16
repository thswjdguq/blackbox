import type { ScoreEntry, ScoreItem } from "@/types/vault";

/** K-40 이전 응답(items 없음)의 네 항목. 판정 불리언만 있으므로 횟수는 없다 */
const LEGACY: { code: string; label: string; key: "taskParticipated" | "meetingParticipated" | "fileParticipated" | "actionParticipated"; description: string }[] = [
  { code: "TASK_DONE", label: "업무 완료", key: "taskParticipated", description: "담당 업무 1개 이상 완료" },
  { code: "MEETING_ATTENDANCE", label: "회의 참석", key: "meetingParticipated", description: "전체 회의의 절반 이상 체크인" },
  { code: "FILE_UPLOAD", label: "파일 업로드", key: "fileParticipated", description: "파일 1개 이상 업로드" },
  { code: "ACTION_ITEM", label: "회의 할 일", key: "actionParticipated", description: "회의에서 나온 할 일 1개 이상 완료" },
];

export const REVIEW_ACTIVITY = "REVIEW_ACTIVITY";

export type ScoreColumn = { code: string; label: string; description: string };

/** 표의 한 칸에 그릴 항목. items가 없던 응답은 count가 없다 */
export type LevelCell = ScoreColumn & { passed: boolean; count?: number; total?: number };

/** 판정에 들어가는 항목. items가 없으면 네 불리언으로 만든다(횟수 없음) */
export function levelItems(s: ScoreEntry): LevelCell[] {
  if (s.items) return s.items.filter((i) => i.countsTowardLevel);
  return LEGACY.map(({ code, label, description, key }) => ({ code, label, description, passed: s[key] }));
}

/** 판정에 넣지 않는 항목(검토 참여와 이후에 늘어날 것). items가 없으면 없다 */
export function extraItems(s: ScoreEntry): ScoreItem[] {
  return s.items ? s.items.filter((i) => !i.countsTowardLevel) : [];
}

export function reviewItem(s: ScoreEntry): ScoreItem | null {
  return s.items?.find((i) => i.code === REVIEW_ACTIVITY) ?? null;
}

/** 표의 칸. 모든 행의 항목을 처음 나온 순서대로 모아, 멤버마다 계산 시점이 달라도 칸이 빠지지 않게 한다 */
export function columns(scores: ScoreEntry[], pick: (s: ScoreEntry) => { code: string; label: string; description: string }[]): ScoreColumn[] {
  const seen = new Map<string, ScoreColumn>();
  for (const s of scores) for (const i of pick(s)) if (!seen.has(i.code)) seen.set(i.code, { code: i.code, label: i.label, description: i.description });
  return Array.from(seen.values());
}

/** 칸에 쓸 횟수 글. 회의처럼 total이 있으면 "1/4", 회의가 없으면 "회의 없음" */
export function countText(i: { count?: number; total?: number }): string | null {
  if (i.count == null) return null;
  if (i.total != null) return i.total === 0 ? "회의 없음" : `${i.count}/${i.total}`;
  return `${i.count}건`;
}

/** GET은 점수순, 재계산은 멤버순으로 와서 순서가 바뀌므로 화면은 이름순으로 고정한다 */
export function sortScores(scores: ScoreEntry[]): ScoreEntry[] {
  return [...scores].sort((a, b) => a.name.localeCompare(b.name, "ko") || a.userId.localeCompare(b.userId));
}
