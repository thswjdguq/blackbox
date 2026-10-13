"use client";

import { useEffect } from "react";
import { Loader2, Sparkles } from "lucide-react";
import { useAgentStore } from "@/lib/store/agentStore";

/**
 * AI 도우미 여는 버튼 (화면 문서 3.1). 큰 화면은 사이드바, 작은 화면은 상단 바에 둔다.
 * 별도 UI 환경에서는 연결 전에도 진입점을 유지하고 창 안에서 사용 가능 상태를 안내한다.
 * 실행 버튼의 노출/권한은 서버 상태에 따르며 준비 중인 기능을 실행 가능하게 표시하지 않는다.
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

  const count = status?.pendingProposalCount ?? 0;

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
          : "mx-3 mt-3 flex items-center gap-2 rounded-xl border border-bb-primary/20 bg-bb-primary/5 px-3 py-3 text-sm text-bb-primary hover:bg-bb-primary/10"
      }
    >
      {running ? <Loader2 size={16} className="animate-spin" aria-hidden="true" /> : <Sparkles size={16} aria-hidden="true" />}
      {!compact && (
        <>
          <span className="text-left"><span className="block font-semibold">AI 도우미</span><span className="block mt-0.5 text-[10px] text-bb-text2">계획 · 업무 · 진행 상황</span></span>
          {running && <span className="ml-auto text-[11px] text-bb-text2">진행 중</span>}
          {!running && count > 0 && (
            <span className="ml-auto rounded-full bg-bb-primary px-1.5 text-[10px] text-white" aria-label={`대기 제안 ${count}개`}>{count}</span>
          )}
        </>
      )}
    </button>
  );
}
