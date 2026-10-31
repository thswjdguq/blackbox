/** 체크리스트는 기존 설명의 Markdown으로 저장한다. 별도 업무나 승인 요청을 만들지 않는다. */
export function checklistItems(description: string) {
  let fence: string | null = null;
  return description.split("\n").flatMap((line, index) => {
    const boundary = line.match(/^\s*(`{3,}|~{3,})/);
    if (boundary) { fence = fence === boundary[1][0] ? null : fence ?? boundary[1][0]; return []; }
    if (fence) return [];
    const match = line.match(/^- \[([ xX])\] (.+)\r?$/);
    return match ? [{ index, checked: match[1].toLowerCase() === "x", text: match[2].trimEnd() }] : [];
  });
}

export function toggleChecklistItem(description: string, index: number, checked: boolean) {
  if (!checklistItems(description).some(item => item.index === index)) return description;
  return description.split("\n").map((line, i) => i === index ? line.replace(/^- \[[ xX]\]/, checked ? "- [x]" : "- [ ]") : line).join("\n");
}
