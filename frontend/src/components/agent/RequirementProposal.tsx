"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import api from "@/lib/api";
import { saveProposal } from "@/lib/api/agent";
import { apiError } from "@/lib/apiError";
import { onSessionReset, sessionGeneration } from "@/lib/sessionCache";
import { useAgentStore } from "@/lib/store/agentStore";
import type { Brief } from "@/types/brief";
import type { EvidenceQuote, Proposal, RequirementAddContent, RequirementReviseContent } from "@/types/agent";

type RequirementProposal = Extract<Proposal, { kind: "REQUIREMENT_ADD" | "REQUIREMENT_REVISE" }>;

export const isRequirementProposal = (p: Proposal): p is RequirementProposal => p.kind === "REQUIREMENT_ADD" || p.kind === "REQUIREMENT_REVISE";
/** 수정안 없이 확인 질문만 있는 카드(K-31 5.2). 채택할 수 없고 거절로 닫는다 */
export const isQuestionOnly = (p: Proposal) => p.kind === "REQUIREMENT_REVISE" && p.content.revision == null;

// 안내문의 지금 bodyHash. 카드 여러 장이 같은 목록을 쓰므로 프로젝트마다 잠깐 담아 둔다
const cache = new Map<string, { at: number; generation: number; briefs: Promise<Brief[]> }>();
onSessionReset(() => cache.clear());

/**
 * 프로젝트의 안내문 목록. 받은 목록을 프로젝트와 세션 세대로 묶어 두고, 지금 프로젝트·세대와 다르면 null을 돌려준다.
 * 화면이 오래 떠 있는 도우미 창처럼 프로젝트·계정이 바뀌어도 같은 컴포넌트가 남는 곳에서 이전 목록을 보이지 않게 한다
 */
export function useBriefs(projectId: string, enabled: boolean) {
  const generation = sessionGeneration();
  const key = `${projectId}:${generation}`;
  const [loaded, setLoaded] = useState<{ key: string; briefs: Brief[] } | null>(null);
  useEffect(() => {
    if (!enabled || !projectId) return;
    let hit = cache.get(projectId);
    if (!hit || hit.generation !== generation || Date.now() - hit.at > 30_000) {
      hit = { at: Date.now(), generation, briefs: api.get<Brief[]>(`/projects/${projectId}/briefs`).then((r) => r.data) };
      cache.set(projectId, hit);
    }
    let live = true;
    const settle = (briefs: Brief[]) => { if (live && generation === sessionGeneration()) setLoaded({ key: `${projectId}:${generation}`, briefs }); };
    hit.briefs.then(settle).catch(() => settle([]));
    return () => { live = false; };
  }, [projectId, enabled, generation]);
  return enabled && loaded?.key === key ? loaded.briefs : null;
}
export const forgetBriefs = (projectId: string) => cache.delete(projectId);

/** 근거 번호 하나. 누르면 원문과 안내문 바로가기를 펼친다 */
function EvidenceRef({ id, projectId, quote, requirement, briefs, currentRequirement }: {
  id: string; projectId: string; quote?: EvidenceQuote; requirement?: { requirementId: string; content: string };
  briefs: Brief[] | null; currentRequirement?: string | null;
}) {
  const [open, setOpen] = useState(false);
  const brief = quote ? briefs?.find((b) => b.id === quote.briefId) : undefined;
  // 안내문의 지금 해시가 카드에 저장된 것과 같을 때만 위치를 쓴다(6.4)
  const same = !!brief && brief.bodyHash === quote?.bodyHash;
  const changed = !!quote && briefs !== null && !same;
  return (
    <span className="inline">
      <button type="button" onClick={() => setOpen((v) => !v)} aria-expanded={open} data-evidence={id}
        className={`mx-0.5 rounded px-1 font-mono text-[10px] align-middle ${changed ? "bg-amber-500/15 text-amber-500" : "bg-bb-primary/10 text-bb-primary"}`}>{id}</button>
      {open && (
        <span className="mt-1 block rounded-lg border border-bb-border bg-bb-surface p-2 text-[11px] text-bb-text" data-testid={`evidence-${id}`}>
          {quote && (
            <>
              {changed && <span className="mb-1 block font-semibold text-amber-600 dark:text-amber-400">근거 안내문이 바뀜{brief ? "" : "(안내문을 찾을 수 없음)"} · 카드를 만들 때의 문장</span>}
              <span className="block whitespace-pre-wrap">“{quote.text}”</span>
              {brief && (
                <Link href={same ? `/projects/${projectId}/briefs?brief=${brief.id}&start=${quote.start}&end=${quote.end}&hash=${quote.bodyHash}` : `/projects/${projectId}/briefs?brief=${brief.id}`}
                  className="mt-1 inline-block text-bb-primary underline">{brief.title}에서 {same ? "이 문장 보기" : "지금 안내문 보기"}</Link>
              )}
            </>
          )}
          {requirement && (
            <>
              <span className="block">요구사항: “{requirement.content}”</span>
              {currentRequirement !== undefined && currentRequirement !== requirement.content && (
                <span className="mt-0.5 block text-amber-600 dark:text-amber-400">{currentRequirement == null ? "이 요구사항은 지워졌습니다. 위는 카드를 만들 때의 문구입니다" : `지금 문구: “${currentRequirement}” · 위는 카드를 만들 때의 문구입니다`}</span>
              )}
            </>
          )}
          {!quote && !requirement && <span className="text-bb-text2">근거 원문이 없습니다.</span>}
        </span>
      )}
    </span>
  );
}

function Evidenced({ text, evidence, ctx }: { text: string; evidence: string[]; ctx: EvidenceCtx }) {
  return (
    <span className="whitespace-pre-wrap">
      {text}
      {evidence.map((id) => <EvidenceRef key={id} id={id} projectId={ctx.projectId} quote={ctx.quotes[id]} requirement={ctx.requirements[id]}
        briefs={ctx.briefs} currentRequirement={ctx.requirements[id] ? ctx.currentOf(ctx.requirements[id].requirementId) : undefined} />)}
    </span>
  );
}

type EvidenceCtx = {
  projectId: string; briefs: Brief[] | null;
  quotes: Record<string, EvidenceQuote>;
  requirements: Record<string, { requirementId: string; content: string }>;
  currentOf: (requirementId: string) => string | null | undefined;
};

/** 빠진 요구사항·수정안·질문만 카드의 본문(K-31 11장) */
export function RequirementProposalBody({ proposal: p, projectId }: { proposal: RequirementProposal; projectId: string }) {
  const briefs = useBriefs(projectId, true);
  const loadDeliverables = useAgentStore((s) => s.loadDeliverables);
  const deliverables = useAgentStore((s) => s.deliverables[projectId]);
  useEffect(() => { loadDeliverables(projectId); }, [projectId, loadDeliverables]);
  const deliverable = deliverables?.find((d) => d.id === p.content.deliverableId);
  const currentOf = (requirementId: string) => (deliverables ? deliverable?.requirements.find((r) => r.id === requirementId)?.content ?? null : undefined);
  const ctx: EvidenceCtx = {
    projectId, briefs, quotes: p.content.evidenceText ?? {},
    requirements: p.kind === "REQUIREMENT_REVISE" ? p.content.evidenceRequirements ?? {} : {}, currentOf,
  };
  const row = "grid grid-cols-[4.5rem_1fr] gap-x-2 gap-y-1.5 text-xs";
  const target = deliverable ? <Link href={`/projects/${projectId}/deliverables/${deliverable.id}`} className="text-bb-primary underline">{deliverable.title}</Link> : <span className="text-bb-text2">최종 제출물</span>;

  if (p.kind === "REQUIREMENT_ADD") {
    const c: RequirementAddContent = p.content;
    return (
      <dl className={row}>
        <dt className="text-bb-text2">제출물</dt><dd>{target}</dd>
        <dt className="text-bb-text2">추가할 것</dt>
        <dd className="text-bb-text"><span className="text-bb-text2">[{c.requirement.required ? "필수" : "선택"}]</span> <Evidenced text={c.requirement.content} evidence={c.requirement.evidence} ctx={ctx} /></dd>
        <dt className="text-bb-text2">이유</dt><dd className="text-bb-text"><Evidenced text={c.reason.text} evidence={c.reason.evidence} ctx={ctx} /></dd>
      </dl>
    );
  }
  const c: RequirementReviseContent = p.content;
  const now = currentOf(c.requirementId);
  return (
    <dl className={row}>
      <dt className="text-bb-text2">제출물</dt><dd>{target}</dd>
      <dt className="text-bb-text2">지금 문구</dt>
      <dd className="text-bb-text">
        {c.currentContent}
        {now !== undefined && now !== c.currentContent && (
          <span className="mt-0.5 block text-amber-600 dark:text-amber-400">{now == null ? "카드를 만든 뒤 이 요구사항이 지워졌습니다." : `카드를 만든 뒤 문구가 바뀌었습니다: “${now}”`} 다시 찾기를 실행하세요.</span>
        )}
      </dd>
      <dt className="text-bb-text2">모호한 점</dt><dd className="text-bb-text"><Evidenced text={c.issue.text} evidence={c.issue.evidence} ctx={ctx} /></dd>
      <dt className="text-bb-text2">수정안</dt>
      <dd className="text-bb-text">{c.revision ? <Evidenced text={c.revision.content} evidence={c.revision.evidence} ctx={ctx} /> : <span className="text-bb-text2">안내문만으로 정할 수 없어 수정안이 없습니다.</span>}</dd>
      {c.question && <><dt className="text-bb-text2">확인 질문</dt><dd className="text-bb-text">{c.question}</dd></>}
    </dl>
  );
}

/** 카드 수정(K-30). 바꿀 수 있는 칸은 요구사항 문구·필수 여부, 수정안, 확인 질문뿐이다(K-31 7장) */
export function RequirementProposalEditor({ proposal: p, projectId, onDone }: { proposal: RequirementProposal; projectId: string; onDone: () => void }) {
  const putProposal = useAgentStore((s) => s.putProposal);
  const [content, setContent] = useState(p.content);
  const [busy, setBusy] = useState(false), [error, setError] = useState("");
  const save = async () => {
    setBusy(true); setError("");
    const generation = sessionGeneration();
    try {
      const { data } = await saveProposal(projectId, p.id, content as Proposal["content"]);
      if (generation !== sessionGeneration()) return;
      putProposal(data); onDone();
    } catch (e) { if (generation === sessionGeneration()) setError(apiError(e, "제안을 고치지 못했습니다.")); }
    finally { setBusy(false); }
  };
  const input = "mt-1 block w-full rounded-lg border border-bb-border bg-bb-surface p-2 text-xs";
  return (
    <form aria-label="제안 고치기" className="space-y-2" onSubmit={(e) => { e.preventDefault(); void save(); }}>
      {p.kind === "REQUIREMENT_ADD" && "requirement" in content && (
        <>
          <label className="block text-xs text-bb-text2">추가할 요구사항
            <textarea aria-label="추가할 요구사항" value={content.requirement.content} rows={2} maxLength={500} className={input}
              onChange={(e) => setContent({ ...content, requirement: { ...content.requirement, content: e.target.value } })} />
          </label>
          <label className="flex items-center gap-2 text-xs text-bb-text">
            <input type="checkbox" checked={content.requirement.required} onChange={(e) => setContent({ ...content, requirement: { ...content.requirement, required: e.target.checked } })} />필수 요구사항
          </label>
        </>
      )}
      {p.kind === "REQUIREMENT_REVISE" && "issue" in content && (
        <>
          {content.revision && (
            <label className="block text-xs text-bb-text2">수정안
              <textarea aria-label="수정안" value={content.revision.content} rows={2} maxLength={500} className={input}
                onChange={(e) => setContent({ ...content, revision: { ...content.revision!, content: e.target.value } })} />
            </label>
          )}
          <label className="block text-xs text-bb-text2">확인 질문
            <textarea aria-label="확인 질문" value={content.question ?? ""} rows={2} maxLength={500} className={input}
              onChange={(e) => setContent({ ...content, question: e.target.value })} />
          </label>
        </>
      )}
      <p className="text-[11px] text-bb-text2">근거와 원문은 고칠 수 없습니다.</p>
      {error && <p role="alert" className="text-xs text-red-400">{error}</p>}
      <div className="flex gap-2">
        <button type="submit" disabled={busy} className="rounded-lg bg-bb-primary px-3 py-1.5 text-xs text-white disabled:opacity-50">{busy ? "저장 중…" : "저장"}</button>
        <button type="button" disabled={busy} onClick={onDone} className="rounded-lg border border-bb-border px-3 py-1.5 text-xs">취소</button>
      </div>
    </form>
  );
}

/** 계획 카드의 근거 안내문이 카드 뒤에 바뀌었는가(K-31 4장) */
export function PlanBriefNote({ projectId, briefId, briefBodyHash }: { projectId: string; briefId?: string; briefBodyHash?: string }) {
  const briefs = useBriefs(projectId, !!briefId);
  if (!briefId || !briefs) return null;
  const brief = briefs.find((b) => b.id === briefId);
  if (!brief) return null;
  const changed = !!briefBodyHash && brief.bodyHash !== briefBodyHash;
  return (
    <p className={`text-xs ${changed ? "text-amber-600 dark:text-amber-400" : "text-bb-text2"}`} data-testid="plan-brief">
      {changed ? "근거 안내문이 바뀜 · 이 계획은 바뀌기 전의 안내문으로 만들었습니다: " : "근거 안내문: "}
      <Link href={`/projects/${projectId}/briefs?brief=${brief.id}`} className="text-bb-primary underline">{brief.title}</Link>
    </p>
  );
}
