"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Check, Loader2, RotateCcw, Sparkles } from "lucide-react";
import { parseAnswer } from "@/lib/agent/answer";
import type { RunEntry } from "@/lib/store/agentStore";
import type { AgentStatus, Proposal } from "@/types/agent";
import ProposalCard from "./ProposalCard";

function useElapsed(startedAt: number, running: boolean) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!running) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [running]);
  return Math.max(0, Math.round((now - startedAt) / 1000));
}

/** 실행 기록 하나 (화면 문서 3.3, 4.2). 답의 글은 HTML로 넣지 않고 조각으로 그린다(6.6) */
export default function RunEntryView({
  entry,
  projectId,
  proposals,
  status,
  onRetry,
  onNavigate,
}: {
  entry: RunEntry;
  projectId: string;
  proposals: Record<string, Proposal>;
  status: AgentStatus;
  onRetry: () => void;
  onNavigate: () => void;
}) {
  // 실행 입력의 제출물(요구사항 찾기·업무 나누기). 막힌 이유를 고치러 갈 곳이다
  const deliverableId = (entry.input as { deliverableId?: string }).deliverableId;
  const running = entry.state === "running";
  const elapsed = useElapsed(entry.startedAt, running);
  const lines = entry.text ? parseAnswer(entry.text, projectId) : [];

  return (
    <article data-testid="agent-run" data-state={entry.state} className="border-b border-bb-border/60 pb-4">
      <h3 className="text-sm font-semibold text-bb-text break-words">{entry.title}</h3>

      {/* 단계: 진행 중에는 한 줄씩 보이고, 끝나면 접는다 */}
      {entry.steps.length > 0 &&
        (running ? (
          <ul className="mt-2 space-y-1 text-xs text-bb-text2">
            {entry.steps.map((s, i) => (
              <li key={i} className="flex items-center gap-1.5">
                <Check size={12} className="shrink-0 text-teal-500" aria-hidden="true" />
                {s}
              </li>
            ))}
          </ul>
        ) : (
          <details className="mt-2 text-xs text-bb-text2">
            <summary className="cursor-pointer hover:text-bb-text">기록 {entry.steps.length}가지를 살펴봤습니다</summary>
            <ul className="mt-1 space-y-1 pl-4">
              {entry.steps.map((s, i) => <li key={i}>{s}</li>)}
            </ul>
          </details>
        ))}

      {running && (
        <p className="mt-2 flex items-center gap-1.5 text-xs text-bb-text2">
          <Loader2 size={12} className="animate-spin" aria-hidden="true" />
          살펴보는 중입니다({elapsed}초). 창을 닫거나 다른 화면으로 가도 계속 진행됩니다.
        </p>
      )}

      {lines.length > 0 && (
        <div className="mt-3">
          <p className="mb-1 flex items-center gap-1 text-xs font-medium text-bb-primary">
            <Sparkles size={11} aria-hidden="true" />
            AI 답변
          </p>
          <div data-testid="agent-answer" className="space-y-1 text-sm leading-relaxed text-bb-text">
            {lines.map((line, i) => {
              const content = line.parts.map((p, j) =>
                typeof p === "string" ? (
                  <span key={j}>{p}</span>
                ) : (
                  <Link key={j} href={p.href} onClick={onNavigate} className="text-bb-primary underline hover:no-underline">
                    {p.label}
                  </Link>
                )
              );
              return line.bullet ? (
                <p key={i} className="flex gap-1.5 pl-1"><span aria-hidden="true">•</span><span>{content}</span></p>
              ) : (
                <p key={i} className="min-h-[1em] whitespace-pre-wrap break-words">{content}</p>
              );
            })}
          </div>
          {!running && <p className="mt-2 text-xs text-bb-text2">기록과 다를 수 있습니다. 링크로 확인하세요.</p>}
        </div>
      )}

      {/* 제안 카드: 실행 기록과 "제안" 탭이 같은 카드(저장소)를 본다. 한쪽에서 채택하면 다른 쪽도 바뀐다 */}
      {entry.proposalIds.map((id) =>
        proposals[id] ? (
          <ProposalCard key={id} proposal={proposals[id]} projectId={projectId} status={status} onNavigate={onNavigate} />
        ) : null
      )}

      {entry.state === "failed" && entry.error && (
        <div className="mt-3">
          <p role="alert" className="text-sm text-red-400">{entry.error}</p>
          {/* 문장은 서버 detail 그대로. 아래는 서버가 함께 보낸 칸으로 고칠 곳을 바로 열게 한다(K-31 8장) */}
          {entry.problem?.code === "BRIEFS_TOO_LONG" && entry.problem.briefs && (
            <div className="mt-2 rounded-lg border border-bb-border p-2.5 text-xs" data-testid="briefs-too-long">
              <p className="text-bb-text2">걸린 안내문 {entry.problem.total?.toLocaleString()} / {entry.problem.limit?.toLocaleString()}자</p>
              <ul className="mt-1 space-y-0.5">
                {entry.problem.briefs.map((b) => (
                  <li key={b.id} className="flex flex-wrap justify-between gap-2">
                    <Link href={`/projects/${projectId}/briefs?brief=${b.id}`} onClick={onNavigate} className="min-w-0 break-all text-bb-primary underline">{b.title}</Link>
                    <span className="shrink-0 text-bb-text2">{b.length.toLocaleString()}자</span>
                  </li>
                ))}
              </ul>
              {deliverableId && <Link href={`/projects/${projectId}/deliverables/${deliverableId}`} onClick={onNavigate} className="mt-1.5 inline-block text-bb-primary underline">최종 제출물에서 안내문 떼기</Link>}
            </div>
          )}
          {entry.problem?.code === "NO_BRIEF" && deliverableId && (
            <Link href={`/projects/${projectId}/deliverables/${deliverableId}`} onClick={onNavigate} className="mt-1 inline-block text-xs text-bb-primary underline">최종 제출물에서 안내문 걸기</Link>
          )}
          <button
            type="button"
            onClick={onRetry}
            className="mt-2 inline-flex items-center gap-1 rounded-lg border border-bb-border px-3 py-1.5 text-xs text-bb-text2 hover:bg-bb-surface2 hover:text-bb-text"
          >
            <RotateCcw size={12} aria-hidden="true" />
            다시 시도
          </button>
        </div>
      )}
    </article>
  );
}
