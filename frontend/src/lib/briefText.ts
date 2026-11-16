/** 과제 안내문의 상한(서버 app.agent.brief-max-length와 같다) */
export const BRIEF_MAX = 20000;
export const BRIEF_TITLE_MAX = 200;

/**
 * 서버가 저장 전에 맞추는 것과 같게 정리한다(K-31 2.5): NFC, 줄바꿈 \n, 앞뒤 공백·BOM 제거.
 * 그대로 세면 macOS에서 붙여 넣은 한글(풀어쓴 자모)을 서버보다 많게 세어 저장되는 글을 막을 수 있다
 */
export function normalizeBrief(text: string): string {
  return text.normalize("NFC").replace(/\r\n?/g, "\n").replace(/^[\s﻿]+|[\s﻿]+$/g, "");
}

/** 서버와 같은 기준(UTF-16 단위)의 글자 수 */
export function briefLength(text: string): number {
  return normalizeBrief(text).length;
}

/**
 * 제목을 비워 두면 본문의 첫 줄. 60자를 넘으면 앞 60자에 "…"(K-31 4장의 계획 만들기와 같은 규칙).
 * 서버(BriefService.firstLine)처럼 코드 포인트로 센다. UTF-16 단위로 자르면 이모지가 반쪽만 남는다
 */
export function defaultBriefTitle(body: string): string {
  const first = normalizeBrief(body).split("\n")[0].trim();
  const chars = Array.from(first);
  return chars.length > 60 ? `${chars.slice(0, 60).join("")}…` : first;
}

/** 오류 응답의 ProblemDetail에서 code와 확장 칸을 꺼낸다 */
export function problem<T = Record<string, unknown>>(error: unknown): ({ status?: number; code?: string; detail?: string } & Partial<T>) | null {
  const res = (error as { response?: { status?: number; data?: unknown } })?.response;
  if (!res) return null;
  const data = (res.data && typeof res.data === "object" ? res.data : {}) as Record<string, unknown>;
  return { ...(data as Partial<T>), status: res.status, code: typeof data.code === "string" ? data.code : undefined, detail: typeof data.detail === "string" ? data.detail : undefined };
}
