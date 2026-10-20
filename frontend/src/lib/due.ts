/**
 * 마감일("YYYY-MM-DD") 표시 공통 함수.
 *
 * new Date("YYYY-MM-DD")는 UTC 자정으로 읽혀 한국에서는 그날 오전 9시가 된다.
 * 그래서 오늘 마감이 "D-1", 어제 마감이 "오늘 마감"으로 보였다. 여기서는 날짜를
 * 사용자 시간대의 자정으로 읽고, 화면은 모두 이 함수로 마감을 표시한다.
 */

/** "YYYY-MM-DD"(뒤에 시각이 붙어도 날짜만)를 사용자 시간대의 그날 자정으로 */
export function parseLocalDate(ymd: string): Date {
  const [y, m, d] = ymd.slice(0, 10).split("-").map(Number);
  return new Date(y, m - 1, d);
}

/** 오늘부터 마감까지 남은 날. 오늘 0, 지났으면 음수 */
export function daysLeft(ymd: string, now: Date = new Date()): number {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  // 일광 절약 시간이 있는 시간대에서도 하루가 23·25시간일 수 있어 반올림한다
  return Math.round((parseLocalDate(ymd).getTime() - today.getTime()) / 86_400_000);
}

/** 날짜 단위 계약이므로 초과 시간을 추정하지 않는다. */
export function dueLabel(ymd: string, now?: Date): string {
  const d = daysLeft(ymd, now);
  if (d < 0) return `${-d}일 초과`;
  if (d === 0) return "오늘 마감";
  return `${d <= 3 ? "임박" : "여유"} · D-${d}`;
}

export type DueTone = "overdue" | "today" | "soon" | "later";

/** 색을 고르는 기준. soon은 1~soonDays일 남음 */
export function dueTone(ymd: string, soonDays = 3, now?: Date): DueTone {
  const d = daysLeft(ymd, now);
  if (d < 0) return "overdue";
  if (d === 0) return "today";
  if (d <= soonDays) return "soon";
  return "later";
}

/** 글자 색 (Tailwind) */
export const DUE_TEXT: Record<DueTone, string> = {
  overdue: "text-red-700 dark:text-red-300",
  today:   "text-red-700 dark:text-red-300",
  soon:    "text-yellow-800 dark:text-yellow-300",
  later:   "text-blue-700 dark:text-blue-300",
};

/** 최종 제출물의 기한 표시. 확정·제출을 기록했으면 기한이 지나도 "초과"가 아니라 그 상태를 보인다 */
export function deliverableDue(d: { dueDate: string; dueTime?: string | null; status?: string | null }, now: Date = new Date()): { label: string; cls: string } {
  if (d.status === "SUBMITTED") return { label: "제출 기록됨", cls: "text-bb-text2" };
  if (d.status === "CONFIRMED") return { label: "확정됨", cls: "text-bb-text2" };
  if (d.dueTime) {
    const deadline = new Date(`${d.dueDate}T${d.dueTime}:00+09:00`);
    if (now.getTime() >= deadline.getTime()) return { label: "기한 지남", cls: DUE_TEXT.overdue };
    const koreanDate = new Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
    const koreanNow = parseLocalDate(koreanDate);
    return { label: dueLabel(d.dueDate, koreanNow), cls: DUE_TEXT[dueTone(d.dueDate, 3, koreanNow)] };
  }
  return { label: dueLabel(d.dueDate, now), cls: DUE_TEXT[dueTone(d.dueDate, 3, now)] };
}

export function deliverableDeadline(d: { dueDate: string; dueTime?: string | null }): string {
  return `${shortDate(d.dueDate)} · ${d.dueTime ? `${d.dueTime} (한국 시간)` : "시간 미지정"}`;
}

export const DUE_BADGE: Record<DueTone, string> = {
  overdue: `${DUE_TEXT.overdue} bg-red-500/10 border-red-500/30`,
  today: `${DUE_TEXT.today} bg-red-500/10 border-red-500/30`,
  soon: `${DUE_TEXT.soon} bg-yellow-500/10 border-yellow-500/30`,
  later: `${DUE_TEXT.later} bg-blue-500/10 border-blue-500/30`,
};

export const DUE_BAR: Record<DueTone, string> = {
  overdue: "bg-red-500", today: "bg-red-500", soon: "bg-yellow-500", later: "bg-blue-500",
};

/** "10월 20일" */
export function shortDate(ymd: string): string {
  return parseLocalDate(ymd).toLocaleDateString("ko-KR", { month: "short", day: "numeric" });
}
