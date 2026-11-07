"use client";

import { useEffect, useMemo } from "react";
import { Sparkles } from "lucide-react";
import { useAgentStore } from "@/lib/store/agentStore";

/**
 * "AI 제안" 표시 (화면 문서 4.10). 뜻은 "AI가 제안했고 사람이 채택해 만들어졌다"이다.
 * 사람이 그 뒤에 고쳤어도 계속 붙는다. 글자로 보이고, 아이콘만 두지 않는다(8장).
 */
export default function AiBadge({ className = "" }: { className?: string }) {
  return (
    <span
      data-testid="ai-badge"
      title="AI가 제안하고 팀원이 채택해 만든 기록입니다"
      className={`inline-flex shrink-0 items-center gap-0.5 rounded bg-bb-primary/10 px-1.5 py-0.5 text-[10px] font-medium text-bb-primary ${className}`}
    >
      <Sparkles size={9} aria-hidden="true" />
      AI 제안
    </span>
  );
}

/**
 * 이 프로젝트에서 채택으로 만들어진 기록의 id. 화면이 열릴 때와 채택할 때마다(recordsVersion) 부른다.
 * 실패하면 빈 집합(표시 없음). AI가 꺼져 있어도 서버가 준다.
 */
export function useAiOrigins(projectId: string | null) {
  const origins = useAgentStore((s) => (projectId ? s.origins[projectId] : undefined));
  const loadOrigins = useAgentStore((s) => s.loadOrigins);
  const recordsVersion = useAgentStore((s) => s.recordsVersion);

  useEffect(() => {
    if (projectId) loadOrigins(projectId);
  }, [projectId, loadOrigins, recordsVersion]);

  return useMemo(
    () => ({
      deliverable: new Set(origins?.deliverableIds ?? []),
      requirement: new Set(origins?.requirementIds ?? []),
      task: new Set(origins?.taskIds ?? []),
    }),
    [origins]
  );
}
