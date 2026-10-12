"use client";

import { useEffect } from "react";
import { Loader2, Sparkles } from "lucide-react";
import { useAgentStore } from "@/lib/store/agentStore";

/**
 * AI 도우미 여는 버튼 (화면 문서 3.1). 큰 화면은 사이드바, 작은 화면은 상단 바에 둔다.
 * AI가 켜져 있거나 대기 카드가 있을 때 보인다(4.1). 꺼져 있고 카드도 없거나 상태 조회가 실패하면 오류 없이 감춘다.
 * 사이드바는 화면마다 다시 그려지므로 버튼은 저장소의 값을 읽기만 한다.
 */
export default function AgentLauncher({ projectId, compact = false }: { projectId: string; compact?: boolean }) {
  const status = useAgentStore((s) => s.status[projectId]);
  const open = useAgentStore((s) => s.open);
  const setOpen = useAgentStore((s) => s.setOpen);
  const loadStatus = useAgentStore((s) => s.loadStatus);
  const running = useAgentStore((s) => (s.runs[projectId] ?? []).some((r) => r.state === "running"));

  // 프로젝트 화면에 들어올 때. 같은 프로젝트에서 30초 안에는 다시 부르지 않는다(저장소)
  useEffect(() => {
    loadStatus(projectId);
  }, [projectId, loadStatus]);

  if (!status || (!status.available && status.pendingProposalCount === 0)) return null;
  const count = status.pendingProposalCount;

  return (
    <button
      type="button"
      data-agent-launcher
      onClick={() => setOpen(!open)}
      aria-expanded={open}
      aria-label={compact ? `AI 도우미${running ? " (진행 중)" : ""}${count > 0 ? `, 대기 제안 ${count}개` : ""}` : undefined}
      className={
        compact
          ? "ml-auto rounded-lg p-1.5 text-bb-primary hover:bg-bb-surface2"
          : "mx-3 mt-3 flex items-center gap-2 rounded-lg border border-bb-primary/30 px-3 py-2 text-sm text-bb-primary hover:bg-bb-primary/10"
      }
    >
      {running ? <Loader2 size={16} className="animate-spin" aria-hidden="true" /> : <Sparkles size={16} aria-hidden="true" />}
      {!compact && (
        <>
          AI 도우미
          {running && <span className="ml-auto text-[11px] text-bb-text2">진행 중</span>}
          {!running && count > 0 && (
            <span className="ml-auto rounded-full bg-bb-primary px-1.5 text-[10px] text-white" aria-label={`대기 제안 ${count}개`}>{count}</span>
          )}
        </>
      )}
    </button>
  );
}
