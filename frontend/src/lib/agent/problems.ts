/**
 * 카드의 수정·채택이 400이면 detail의 괄호 안에 "경로: 이유"가 쉼표로 이어져 온다(화면 문서 6.5).
 * 예: "제안의 내용을 확인해주세요 (deliverable.dueDate: must not be null, tasks[1].title: …)"
 * { "deliverable.dueDate": "must not be null", … }를 돌려준다. 괄호가 없으면 빈 객체.
 */
export function fieldProblems(detail: string): Record<string, string> {
  const inside = detail.match(/\((.*)\)\s*$/)?.[1];
  if (!inside) return {};
  const out: Record<string, string> = {};
  for (const part of inside.split(/,\s*(?=[A-Za-z][\w.\[\]]*:)/)) {
    const at = part.indexOf(":");
    if (at > 0) out[part.slice(0, at).trim()] = part.slice(at + 1).trim();
  }
  return out;
}

/** 칸 경로를 사람이 읽을 이름으로. 3단계의 고치는 모양이 생기기 전까지 카드 안에 목록으로 보인다 */
export function fieldName(path: string): string {
  const m = path.match(/^(requirements|tasks)\[(\d+)\]\.(\w+)$/);
  const names: Record<string, string> = {
    "deliverable.title": "최종 제출물 제목", "deliverable.dueDate": "기한", "deliverable.submissionMethod": "제출 방법",
    "deliverable.description": "설명", deliverableId: "최종 제출물", tasks: "업무", requirements: "요구사항",
    content: "내용", title: "제목", completionCriteria: "완료 기준", dueDate: "기한", key: "키",
    requirementKey: "연결할 요구사항", requirementId: "연결할 요구사항", required: "필수 여부",
  };
  if (m) return `${m[1] === "tasks" ? "업무" : "요구사항"} ${Number(m[2]) + 1}의 ${names[m[3]] ?? m[3]}`;
  return names[path] ?? path;
}

/** 칸별 이유를 따로 보일 때 폼 위에 남길 요약. 괄호 부분을 뺀다("제안의 내용을 확인해주세요") */
export function problemSummary(detail: string): string {
  return detail.replace(/\s*\(.*\)\s*$/, "").trim() || detail;
}
