/**
 * AI 답의 글을 그릴 조각으로 나눈다 (K-30 4장, 화면 문서 6.6).
 * 쓰는 표기는 줄바꿈, 줄 맨 앞의 "- "(목록), 링크 [이름](경로) 세 가지뿐이다. 다른 표기(굵게, 제목)는 글자 그대로 둔다.
 * HTML로 넣지 않는다. 링크는 서버가 쓰는 세 가지 경로와 정확히 맞을 때만 만든다.
 */
export type AnswerPart = string | { label: string; href: string };
export interface AnswerLine { bullet: boolean; parts: AnswerPart[] }

const ID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

export function parseAnswer(text: string, projectId: string): AnswerLine[] {
  // /projects/{id}/로 시작하는지만 보면 "/projects/{id}/../../profile"도 통과한다(#102 리뷰)
  const inApp = new RegExp(`^/projects/${projectId}/(deliverables/${ID}|board(\\?task=${ID})?)$`, "i");
  return text.split("\n").map((raw) => {
    const bullet = raw.startsWith("- ");
    const line = bullet ? raw.slice(2) : raw;
    const parts: AnswerPart[] = [];
    let last = 0;
    for (const m of line.matchAll(/\[([^\]]+)\]\(([^)\s]+)\)/g)) {
      if (m.index! > last) parts.push(line.slice(last, m.index));
      parts.push(inApp.test(m[2]) ? { label: m[1], href: m[2] } : m[1]);
      last = m.index! + m[0].length;
    }
    if (last < line.length) parts.push(line.slice(last));
    return { bullet, parts };
  });
}
