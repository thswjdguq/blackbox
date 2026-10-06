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

/** "오늘 마감" / "D-3" / "2일 지남" */
export function dueLabel(ymd: string, now?: Date): string {
  const d = daysLeft(ymd, now);
  if (d < 0) return `${-d}일 지남`;
  if (d === 0) return "오늘 마감";
  return `D-${d}`;
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
  overdue: "text-red-400",
  today:   "text-orange-400",
  soon:    "text-yellow-400",
  later:   "text-bb-text2",
};

/** "10월 20일" */
export function shortDate(ymd: string): string {
  return parseLocalDate(ymd).toLocaleDateString("ko-KR", { month: "short", day: "numeric" });
}
