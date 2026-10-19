"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ChevronDown, ChevronUp, Loader2 } from "lucide-react";
import { acceptProposal, getProposal, rejectProposal } from "@/lib/api/agent";
import { apiError } from "@/lib/apiError";
import { fieldName, fieldProblems, problemSummary } from "@/lib/agent/problems";
import { shortDate } from "@/lib/due";
import { useAgentStore } from "@/lib/store/agentStore";
import type { AgentStatus, Proposal } from "@/types/agent";
import ProposalEditor from "./ProposalEditor";
import { sessionGeneration } from "@/lib/sessionCache";

const KIND_LABEL: Record<string, string> = { DELIVERABLE_PLAN: "최종 제출물 계획", TASKS: "업무" };
const STATUS_LABEL: Record<string, string> = { PENDING: "대기", ACCEPTED: "채택됨", REJECTED: "거절됨" };
const STATUS_CLS: Record<string, string> = {
  PENDING: "bg-amber-500/15 text-amber-400",
  ACCEPTED: "bg-teal-500/15 text-teal-400",
  REJECTED: "bg-bb-surface2 text-bb-text2",
};
const NEEDS = "[작성 필요";

function when(iso: string) {
  return new Date(iso).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

/** 값에 "[작성 필요"가 든 칸의 수 (4.7 채워야 할 칸) */
function needsCount(p: Proposal): number {
  const values: (string | null)[] =
    p.kind === "DELIVERABLE_PLAN"
      ? [
          p.content.deliverable.title, p.content.deliverable.description, p.content.deliverable.submissionMethod,
          ...p.content.requirements.map((r) => r.content),
          ...p.content.tasks.flatMap((t) => [t.title, t.completionCriteria]),
        ]
      : p.kind === "TASKS"
        ? p.content.tasks.flatMap((t) => [t.title, t.completionCriteria])
        : [];
  return values.filter((v) => v?.includes(NEEDS)).length;
}

function Value({ text }: { text: string | null | undefined }) {
  if (!text) return <span className="text-bb-text2">-</span>;
  return text.includes(NEEDS) ? <mark className="rounded bg-amber-500/20 px-1 text-amber-300">{text}</mark> : <>{text}</>;
}

/**
 * 제안 카드 (화면 문서 4.7). 접힌 모양 ↔ 펼친 모양. 채택·거절은 서버의 카드로 화면을 바꾼다.
 * 고치는 모양은 ProposalEditor. 채택이 400이면 고치는 모양을 열어 막힌 칸을 표시한다(4.7 채택 4번).
 */
export default function ProposalCard({
  proposal: p,
  projectId,
  status,
  defaultOpen = false,
  onNavigate,
  onGone,
}: {
  proposal: Proposal;
  projectId: string;
  status: AgentStatus;
  defaultOpen?: boolean;
  onNavigate: () => void;
  /** 404: 카드가 없어졌다. 목록을 다시 부른다 */
  onGone?: () => void;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const [busy, setBusy] = useState<"" | "accept" | "reject">("");
  const [confirmReject, setConfirmReject] = useState(false);
  const [error, setError] = useState("");
  const [problems, setProblems] = useState<Record<string, string>>({});
  const [gone, setGone] = useState(false);
  const [editing, setEditing] = useState<null | { problems: Record<string, string>; focusDue: boolean }>(null);
  const resultRef = useRef<HTMLParagraphElement>(null);
  const putProposal = useAgentStore((s) => s.putProposal);
  const bumpRecords = useAgentStore((s) => s.bumpRecords);
  const loadStatus = useAgentStore((s) => s.loadStatus);
  const loadDeliverables = useAgentStore((s) => s.loadDeliverables);
  const deliverables = useAgentStore((s) => s.deliverables[projectId]);

  const known = p.kind === "DELIVERABLE_PLAN" || p.kind === "TASKS";
  const needsRequirementNames = open && p.kind === "TASKS" && p.content.tasks.some((t) => t.requirementId);
  useEffect(() => {
    if (needsRequirementNames) loadDeliverables(projectId);
  }, [needsRequirementNames, projectId, loadDeliverables]);

  const stageTitle = (id: string | null) =>
    id == null ? "최종 제출물 연결 없음" : status.stages.find((s) => s.deliverableId === id)?.title ?? "삭제된 최종 제출물";
  const requirementName = (deliverableId: string | null, requirementId: string | null) => {
    if (!requirementId) return null;
    const d = deliverables?.find((x) => x.id === deliverableId);
    return d?.requirements.find((r) => r.id === requirementId)?.content ?? "요구사항";
  };

  const counts =
    p.kind === "DELIVERABLE_PLAN"
      ? `요구사항 ${p.content.requirements.length} · 업무 ${p.content.tasks.length} · ${p.content.deliverable.dueDate ? `기한 ${shortDate(p.content.deliverable.dueDate)}` : "기한 없음"}`
      : p.kind === "TASKS"
        ? `업무 ${p.content.tasks.length} · ${stageTitle(p.content.deliverableId)}`
        : "";
  const needs = known ? needsCount(p) : 0;
  const noDue = p.kind === "DELIVERABLE_PLAN" && !p.content.deliverable.dueDate;
  const duplicate = p.kind === "DELIVERABLE_PLAN" && status.stages.some((s) => s.title === p.content.deliverable.title);
  const acceptLabel =
    p.kind === "DELIVERABLE_PLAN"
      ? `채택 — 최종 제출물 1 · 요구사항 ${p.content.requirements.length} · 업무 ${p.content.tasks.length} 만들기`
      : p.kind === "TASKS"
        ? `채택 — 업무 ${p.content.tasks.length}개 만들기`
        : "";

  const afterDecision = (next: Proposal) => {
    putProposal(next);
    loadStatus(projectId, true);
    requestAnimationFrame(() => resultRef.current?.focus());
  };

  // 409: 누가 먼저 결정했거나 기존 기능이 거절했다. 카드를 다시 받아 지금 상태로(5.2)
  const refetch = async () => {
    const generation = sessionGeneration();
    try {
      const { data } = await getProposal(projectId, p.id);
      if (generation !== sessionGeneration()) return;
      putProposal(data);
    } catch {
      /* 문장만 남긴다 */
    }
  };

  const handle = async (kind: "accept" | "reject") => {
    const generation = sessionGeneration();
    setBusy(kind);
    setError("");
    setProblems({});
    try {
      const { data } = kind === "accept" ? await acceptProposal(projectId, p.id) : await rejectProposal(projectId, p.id);
      if (generation !== sessionGeneration()) return;
      if (kind === "accept" && data.status === "ACCEPTED") bumpRecords();
      setConfirmReject(false);
      afterDecision(data);
    } catch (err) {
      if (generation !== sessionGeneration()) return;
      const code = (err as { response?: { status?: number } })?.response?.status;
      const detail = apiError(err, kind === "accept" ? "채택하지 못했습니다." : "거절하지 못했습니다.");
      if (code === 404) {
        setGone(true);
        onGone?.();
        return;
      }
      setError(detail);
      // 400: 아무것도 만들어지지 않았다. 고치는 모양으로 열어 칸을 표시한다
      if (code === 400) {
        const found = fieldProblems(detail);
        setEditing({ problems: found, focusDue: false });
        if (Object.keys(found).length) setError(problemSummary(detail));
      }
      if (code === 409) refetch();
    } finally {
      setBusy("");
    }
  };

  if (gone) {
    return <p className="mt-3 rounded-lg border border-bb-border p-3 text-xs text-bb-text2">이 제안을 찾을 수 없습니다.</p>;
  }

  const decided = p.decision && (
    <p ref={resultRef} tabIndex={-1} data-testid="proposal-result" className="text-xs text-bb-text2 outline-none">
      {p.decision.result === "ACCEPTED" ? "채택됨" : "거절됨"} · {p.decision.decidedBy.name} · {when(p.decision.decidedAt)}
    </p>
  );

  return (
    <div data-testid="agent-proposal" data-status={p.status} className="mt-3 rounded-lg border border-bb-border bg-bb-bg">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex w-full items-start gap-2 p-3 text-left hover:bg-bb-surface2/50"
      >
        <div className="min-w-0 flex-1">
          <p className="flex flex-wrap items-center gap-1.5 text-xs">
            <span className="rounded bg-bb-surface2 px-1.5 py-0.5 text-bb-text2">{KIND_LABEL[p.kind] ?? "제안"}</span>
            <span className={`rounded px-1.5 py-0.5 ${STATUS_CLS[p.status] ?? ""}`}>{STATUS_LABEL[p.status] ?? p.status}</span>
          </p>
          <p className="mt-1 text-sm font-medium text-bb-text break-words">{p.title}</p>
          <p className="mt-0.5 text-[11px] text-bb-text2">
            {counts}{counts && " · "}{p.requestedBy.name} · {when(p.createdAt)}
          </p>
        </div>
        {open ? <ChevronUp size={14} className="mt-1 shrink-0 text-bb-text2" /> : <ChevronDown size={14} className="mt-1 shrink-0 text-bb-text2" />}
      </button>

      {open && editing && (
        <div className="border-t border-bb-border px-3 pb-3 pt-2">
          {error && <p role="alert" className="mb-2 text-xs text-red-400">{error}</p>}
          <ProposalEditor
            proposal={p}
            projectId={projectId}
            initialProblems={editing.problems}
            focusDue={editing.focusDue}
            onDone={() => { setEditing(null); setError(""); }}
          />
        </div>
      )}

      {open && !editing && (
        <div className="space-y-3 border-t border-bb-border px-3 pb-3 pt-2 text-sm">
          <p className="flex flex-wrap gap-1.5 text-[11px]">
            <span className="rounded bg-bb-primary/10 px-1.5 py-0.5 text-bb-primary">AI 제안</span>
            {p.edited && <span className="rounded bg-bb-surface2 px-1.5 py-0.5 text-bb-text2">사람이 고침</span>}
          </p>
          {p.rationale && <p className="text-xs text-bb-text2"><span className="font-medium">근거</span> {p.rationale}</p>}

          {!known && <p className="text-xs text-bb-text2">이 화면에서는 아직 열 수 없는 제안입니다.</p>}

          {p.kind === "DELIVERABLE_PLAN" && (
            <>
              <dl className="grid grid-cols-[4rem_1fr] gap-x-2 gap-y-1 text-xs">
                <dt className="text-bb-text2">최종 제출물</dt><dd className="text-bb-text"><Value text={p.content.deliverable.title} /></dd>
                <dt className="text-bb-text2">기한</dt><dd className="text-bb-text">{p.content.deliverable.dueDate ?? <span className="text-amber-400">없음</span>}</dd>
                <dt className="text-bb-text2">제출</dt><dd className="text-bb-text"><Value text={p.content.deliverable.submissionMethod} /></dd>
                <dt className="text-bb-text2">설명</dt><dd className="text-bb-text whitespace-pre-wrap"><Value text={p.content.deliverable.description} /></dd>
              </dl>
              <div>
                <p className="text-xs font-medium text-bb-text2">요구사항 {p.content.requirements.length}</p>
                <ul className="mt-1 space-y-1 text-xs text-bb-text">
                  {p.content.requirements.map((r) => (
                    <li key={r.key}><span className="text-bb-text2">[{r.required ? "필수" : "선택"}]</span> <Value text={r.content} /></li>
                  ))}
                </ul>
              </div>
            </>
          )}

          {known && (
            <div>
              <p className="text-xs font-medium text-bb-text2">
                업무 {p.content.tasks.length}
                {p.kind === "TASKS" && <span className="font-normal"> · {stageTitle(p.content.deliverableId)}</span>}
              </p>
              <ul className="mt-1 space-y-2 text-xs">
                {p.content.tasks.map((t, i) => {
                  const req =
                    p.kind === "DELIVERABLE_PLAN"
                      ? p.content.requirements.find((r) => r.key === (t as { requirementKey: string | null }).requirementKey)?.content ?? null
                      : requirementName(p.content.deliverableId, (t as { requirementId: string | null }).requirementId);
                  return (
                    <li key={i} className="text-bb-text">
                      <Value text={t.title} />{t.dueDate && <span className="text-bb-text2"> · {shortDate(t.dueDate)}</span>}
                      {t.completionCriteria && <p className="pl-3 text-bb-text2">완료 기준: <Value text={t.completionCriteria} /></p>}
                      {req && <p className="pl-3 text-bb-text2">요구사항: {req}</p>}
                    </li>
                  );
                })}
              </ul>
            </div>
          )}

          {p.status === "PENDING" && (
            <>
              {duplicate && <p className="text-xs text-amber-400">같은 제목의 최종 제출물이 이미 있습니다.</p>}
              {needs > 0 && <p className="text-xs text-amber-400">채워야 할 칸이 {needs}개 남았습니다.</p>}
              {noDue && <p className="text-xs text-amber-400">기한을 채워야 채택할 수 있습니다.</p>}
            </>
          )}

          {error && (
            <div role="alert" className="text-xs text-red-400">
              <p>{error}</p>
              {Object.keys(problems).length > 0 && (
                <ul className="mt-1 list-disc pl-4">
                  {Object.entries(problems).map(([k, v]) => <li key={k}>{fieldName(k)}: {v}</li>)}
                </ul>
              )}
            </div>
          )}

          {p.status === "PENDING" && status.canDecide && (
            confirmReject ? (
              <div className="flex flex-wrap items-center gap-2 text-xs">
                <span className="text-bb-text">이 제안을 거절할까요? 되돌릴 수 없습니다.</span>
                <button type="button" disabled={!!busy} onClick={() => handle("reject")} className="rounded-lg border border-red-500/40 px-3 py-1.5 text-red-400 hover:bg-red-500/10 disabled:opacity-50">
                  {busy === "reject" ? <Loader2 size={12} className="animate-spin" /> : "거절"}
                </button>
                <button type="button" disabled={!!busy} onClick={() => setConfirmReject(false)} className="px-2 py-1.5 text-bb-text2 hover:text-bb-text">취소</button>
              </div>
            ) : (
              <div className="space-y-1.5">
                <div className="flex flex-wrap gap-2">
                  {known && (
                    <button
                      type="button"
                      disabled={!!busy || noDue}
                      onClick={() => handle("accept")}
                      className="inline-flex items-center gap-1 rounded-lg bg-bb-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-bb-primary-h disabled:opacity-50"
                    >
                      {busy === "accept" && <Loader2 size={12} className="animate-spin" />}
                      {acceptLabel}
                    </button>
                  )}
                  {known && (
                    <button
                      type="button"
                      disabled={!!busy}
                      onClick={() => { setError(""); setProblems({}); setEditing({ problems: {}, focusDue: noDue }); }}
                      className="rounded-lg border border-bb-border px-3 py-1.5 text-xs text-bb-text hover:bg-bb-surface2 disabled:opacity-50"
                    >
                      고치기
                    </button>
                  )}
                  <button type="button" disabled={!!busy} onClick={() => setConfirmReject(true)} className="rounded-lg border border-bb-border px-3 py-1.5 text-xs text-bb-text2 hover:bg-bb-surface2 hover:text-bb-text disabled:opacity-50">
                    거절
                  </button>
                </div>
                {known && <p className="text-[11px] text-bb-text2">담당자는 만든 뒤 업무 보드에서 정합니다.</p>}
              </div>
            )
          )}

          {p.status === "ACCEPTED" && (
            <div className="space-y-1.5">
              {decided}
              <div className="flex flex-wrap gap-3 text-xs">
                {acceptedLinks(p, projectId).map((l) => (
                  <Link key={l.href} href={l.href} onClick={onNavigate} className="text-bb-primary underline hover:no-underline">{l.label}</Link>
                ))}
              </div>
              <p className="text-[11px] text-bb-text2">담당자는 업무 보드에서 정하세요.</p>
            </div>
          )}
          {p.status === "REJECTED" && decided}
        </div>
      )}
    </div>
  );
}

/** 채택 뒤 만들어진 것으로 가는 링크 (4.7 표) */
function acceptedLinks(p: Proposal, projectId: string): { label: string; href: string }[] {
  const base = `/projects/${projectId}`;
  const r = p.results;
  if (!r) return [];
  if (p.kind === "DELIVERABLE_PLAN" && r.deliverableId) {
    return [
      { label: "최종 제출물 열기", href: `${base}/deliverables/${r.deliverableId}` },
      { label: `업무 ${r.taskIds.length}개 보기`, href: `${base}/board?deliverable=${r.deliverableId}` },
    ];
  }
  if (p.kind === "TASKS") {
    if (r.taskIds.length === 1) return [{ label: "업무 열기", href: `${base}/board?task=${r.taskIds[0]}` }];
    if (p.content.deliverableId) return [{ label: `업무 ${r.taskIds.length}개 보기`, href: `${base}/board?deliverable=${p.content.deliverableId}` }];
    return [{ label: "업무 보드 열기", href: `${base}/board` }];
  }
  return [];
}
