"use client";

import { useEffect } from "react";
import { Loader2 } from "lucide-react";
import { PROPOSAL_LIMIT, useAgentStore, type ProposalFilter } from "@/lib/store/agentStore";
import type { AgentStatus } from "@/types/agent";
import ProposalCard from "./ProposalCard";

const FILTERS: { value: ProposalFilter; label: string; empty: string }[] = [
  { value: "PENDING", label: "대기", empty: "기다리는 제안이 없습니다. 도우미에게 계획이나 다음 할 일을 물어보세요." },
  { value: "ACCEPTED", label: "채택됨", empty: "채택된 제안이 없습니다." },
  { value: "REJECTED", label: "거절됨", empty: "거절된 제안이 없습니다." },
];

/**
 * "제안" 탭 (화면 문서 4.8). 프로젝트 전체의 카드이고 최신이 먼저, 50건까지.
 * 탭을 열 때마다 다시 부르고 따로 주기적으로 부르지 않는다.
 */
export default function ProposalList({
  projectId,
  status,
  onNavigate,
}: {
  projectId: string;
  status: AgentStatus;
  onNavigate: () => void;
}) {
  const list = useAgentStore((s) => s.proposalLists[projectId]);
  const proposals = useAgentStore((s) => s.proposals);
  const loadProposals = useAgentStore((s) => s.loadProposals);
  const filter = list?.filter ?? "PENDING";

  useEffect(() => {
    loadProposals(projectId, filter);
    // 탭을 열 때(이 컴포넌트가 그려질 때)만 부른다. 걸러 보기를 바꾸면 아래 버튼이 부른다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId, loadProposals]);

  const current = FILTERS.find((f) => f.value === filter)!;

  return (
    <div>
      <div role="group" aria-label="제안 걸러 보기" className="flex w-fit rounded-lg border border-bb-border bg-bb-bg p-0.5 text-xs">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            type="button"
            aria-pressed={filter === f.value}
            onClick={() => loadProposals(projectId, f.value)}
            className={`rounded-md px-3 py-1 ${filter === f.value ? "bg-bb-surface2 font-medium text-bb-text" : "text-bb-text2 hover:text-bb-text"}`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {list?.loading && (
        <p className="mt-4 flex items-center gap-1.5 text-xs text-bb-text2">
          <Loader2 size={12} className="animate-spin" aria-hidden="true" /> 불러오는 중
        </p>
      )}
      {list?.error && (
        <p role="alert" className="mt-4 text-xs text-red-400">
          {list.error}{" "}
          <button type="button" onClick={() => loadProposals(projectId, filter)} className="underline">다시 시도</button>
        </p>
      )}
      {list && !list.loading && !list.error && list.ids.length === 0 && (
        <p className="mt-4 text-sm text-bb-text2">{current.empty}</p>
      )}
      {list?.ids.map((id) =>
        proposals[id] ? (
          <ProposalCard
            key={id}
            proposal={proposals[id]}
            projectId={projectId}
            status={status}
            onNavigate={onNavigate}
            onGone={() => loadProposals(projectId, filter)}
          />
        ) : null
      )}
      {list && list.ids.length >= PROPOSAL_LIMIT && <p className="mt-3 text-[11px] text-bb-text2">최근 {PROPOSAL_LIMIT}건만 보입니다.</p>}
    </div>
  );
}
