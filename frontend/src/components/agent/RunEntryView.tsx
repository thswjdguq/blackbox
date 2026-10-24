"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Check, Loader2, RotateCcw, Sparkles } from "lucide-react";
import { parseAnswer } from "@/lib/agent/answer";
import type { RunEntry } from "@/lib/store/agentStore";
import type { Proposal } from "@/types/agent";

const KIND_LABEL: Record<string, string> = { DELIVERABLE_PLAN: "제출물 계획", TASKS: "업무" };

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
  onRetry,
  onNavigate,
}: {
  entry: RunEntry;
  projectId: string;
  proposals: Record<string, Proposal>;
  onRetry: () => void;
  onNavigate: () => void;
}) {
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
          <p className="mb-1 flex items-center gap-1 text-[11px] font-medium text-bb-primary">
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
          {!running && <p className="mt-2 text-[11px] text-bb-text2">기록과 다를 수 있습니다. 링크로 확인하세요.</p>}
        </div>
      )}

      {/* 제안 카드: 1단계에서는 접힌 모양으로 보이기만 한다. 채택·거절은 2단계 */}
      {entry.proposalIds.map((id) => {
        const p = proposals[id];
        if (!p) return null;
        const count = p.kind === "TASKS" ? `업무 ${p.content.tasks.length}개` : `요구사항 ${p.content.requirements.length} · 업무 ${p.content.tasks.length}`;
        return (
          <div key={id} data-testid="agent-proposal" className="mt-3 rounded-lg border border-bb-border bg-bb-bg p-3">
            <p className="flex flex-wrap items-center gap-1.5 text-xs">
              <span className="rounded bg-bb-surface2 px-1.5 py-0.5 text-bb-text2">{KIND_LABEL[p.kind] ?? "제안"}</span>
              <span className="rounded bg-bb-primary/10 px-1.5 py-0.5 text-bb-primary">AI 제안</span>
              <span className="text-bb-text2">{count}</span>
            </p>
            <p className="mt-1 text-sm font-medium text-bb-text">{p.title}</p>
            {p.rationale && <p className="mt-1 text-xs text-bb-text2">근거: {p.rationale}</p>}
          </div>
        );
      })}

      {entry.state === "failed" && entry.error && (
        <div className="mt-3">
          <p role="alert" className="text-sm text-red-400">{entry.error}</p>
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
