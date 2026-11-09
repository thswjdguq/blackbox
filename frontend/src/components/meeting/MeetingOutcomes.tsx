"use client";
import { useState } from "react";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";

type Proposal = { text: string; evidence: string; selected?: boolean; done?: boolean; assignee?: string; due?: string };
interface Props {
  projectId: string; meetingId: string; notes: string; decisions: string; canWrite: boolean; busy: boolean;
  members: { userId: string; name: string }[];
  saveNotes: () => Promise<boolean>; saveDecisions: (text: string) => Promise<void>; onBusy: (busy: boolean) => void;
}
export default function MeetingOutcomes(p: Props) {
  const [result, setResult] = useState<{ decisions: Proposal[]; actions: Proposal[] } | null>(null);
  const [source, setSource] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [manual, setManual] = useState<string | null>(null);
  const stale = result !== null && source !== p.notes;
  const extract = async () => {
    setError(""); setNotice(""); p.onBusy(true);
    try {
      await p.saveNotes();
      const { data } = await api.post<{ decisions: Proposal[]; actions: Proposal[] }>(`/projects/${p.projectId}/meetings/${p.meetingId}/ai/extract-outcomes`);
      setResult(data); setSource(p.notes);
    } catch (e) {
      const status = (e as { response?: { status?: number } }).response?.status;
      setError(status === 502 || status === 503
        ? "AI 추출을 완료하지 못했습니다. 다시 시도하거나 아래에서 직접 정리해 주세요."
        : apiError(e, e instanceof Error ? e.message : "추출에 실패했습니다. 직접 정리할 수 있습니다."));
    }
    finally { p.onBusy(false); }
  };
  const update = (kind: "decisions" | "actions", index: number, patch: Partial<Proposal>) => setResult(r => r && ({ ...r, [kind]: r[kind].map((item, i) => i === index ? { ...item, ...patch } : item) }));
  const apply = async (kind: "decisions" | "actions") => {
    if (!result || stale || !p.canWrite || p.busy) return;
    const chosen = result[kind].map((item, i) => ({ item, i })).filter(({ item }) => item.selected && !item.done);
    setError(""); setNotice(""); p.onBusy(true);
    try {
      if (kind === "decisions") {
        await p.saveDecisions([p.decisions, ...chosen.map(({ item }) => `- ${item.text.trim()}`)].filter(Boolean).join("\n"));
        chosen.forEach(({ i }) => update(kind, i, { done: true, selected: false }));
      } else {
        // 성공한 항목을 즉시 표시하여 후속 항목 실패 후 재시도할 때 다시 보내지 않는다.
        for (const { item, i } of chosen) {
          await api.post(`/projects/${p.projectId}/meetings/${p.meetingId}/action-items`, { title: item.text.trim(), assigneeIds: item.assignee ? [item.assignee] : [], dueDate: item.due || undefined });
          update(kind, i, { done: true, selected: false });
        }
      }
      setNotice(kind === "decisions" ? "선택한 결정사항을 저장했습니다." : "선택한 할 일을 업무에 등록했습니다.");
    } catch (e) { setError(apiError(e, e instanceof Error ? e.message : "반영에 실패했습니다. 완료 표시가 없는 항목을 확인하세요.")); }
    finally { p.onBusy(false); }
  };
  return <section className="bg-bb-surface border border-bb-border rounded-xl p-6 mb-6">
    <div className="flex flex-wrap justify-between gap-3 mb-3"><div><h2 className="text-lg font-semibold">결정사항 · 할 일 정리</h2><p className="mt-1 text-xs text-bb-text2">회의록을 작성한 뒤 AI 제안을 확인하세요. 선택하기 전에는 저장되거나 업무로 등록되지 않습니다.</p></div>
      <button type="button" onClick={extract} disabled={!p.canWrite || p.busy || !p.notes.trim()} className="bg-bb-primary text-white rounded-lg px-4 py-2 text-sm disabled:opacity-50">{p.busy ? "처리 중…" : "결정사항·할 일 추출"}</button></div>
    <p className="text-xs text-bb-text2 mb-4">AI 추출 시 회의록 본문이 설정된 외부 AI 서비스로 전달됩니다. 담당자와 기한은 직접 지정하세요.</p>
    {error && <p role="alert" className="text-sm text-red-600 mb-3">{error}</p>}
    {notice && <p role="status" className="text-sm text-bb-primary mb-3">{notice}</p>}
    {stale && <p role="alert" className="text-sm text-amber-700 mb-3">회의록이 변경되었습니다. 최신 내용으로 다시 추출해 주세요.</p>}
    {result && (["decisions", "actions"] as const).map(kind => <div key={kind} className="mt-5">
      <h3 className="font-semibold text-sm mb-2">{kind === "decisions" ? "결정사항 제안" : "할 일 제안"}</h3>
      {!result[kind].length && <p className="text-sm text-bb-text2">명확한 항목을 찾지 못했습니다. 회의록을 보완하거나 직접 정리하세요.</p>}
      {result[kind].map((item, i) => <div key={i} className="p-3 mb-2 border border-bb-border rounded-lg">
        <div className="flex gap-2"><input aria-label={`${kind === "decisions" ? "결정사항" : "할 일"} ${i + 1} 선택`} type="checkbox" checked={!!item.selected} disabled={p.busy || item.done || stale || !p.canWrite} onChange={e => update(kind, i, { selected: e.target.checked })} />
          <input aria-label={`제안 ${i + 1} 내용`} maxLength={255} value={item.text} disabled={p.busy || item.done || stale || !p.canWrite} onChange={e => update(kind, i, { text: e.target.value })} className="flex-1 min-w-0 bg-bb-bg rounded px-2 py-1 text-sm" />{item.done && <span className="text-xs text-bb-primary">반영됨</span>}</div>
        <p className="text-xs text-bb-text2 mt-2 whitespace-pre-wrap">원문 근거: {item.evidence}</p>
        {kind === "actions" && <div className="flex flex-wrap gap-2 mt-2"><select aria-label={`할 일 ${i+1} 담당자`} disabled={p.busy || item.done || stale || !p.canWrite} value={item.assignee ?? ""} onChange={e => update(kind, i, { assignee: e.target.value })} className="text-sm bg-bb-bg border border-bb-border rounded p-1"><option value="">담당자 미지정</option>{p.members.map(m => <option key={m.userId} value={m.userId}>{m.name}</option>)}</select><input aria-label={`할 일 ${i+1} 기한`} type="date" value={item.due ?? ""} disabled={p.busy || item.done || stale || !p.canWrite} onChange={e => update(kind, i, { due: e.target.value })} className="text-sm bg-bb-bg border border-bb-border rounded p-1" /></div>}
      </div>)}
      {result[kind].length > 0 && <button type="button" onClick={() => apply(kind)} disabled={!p.canWrite || p.busy || stale || !result[kind].some(x => x.selected && !x.done) || result[kind].some(x => x.selected && !x.text.trim())} className="px-3 py-2 border border-bb-border rounded text-sm disabled:opacity-40">{kind === "decisions" ? "선택한 결정사항 저장" : "선택한 할 일을 업무로 등록"}</button>}
    </div>)}
    <details className="mt-5 border-t border-bb-border pt-4"><summary className="cursor-pointer text-sm font-medium">저장된 결정사항 {p.decisions.trim() ? "보기" : "없음"}</summary><p className="mt-3 text-sm whitespace-pre-wrap">{p.decisions || "아직 저장된 결정사항이 없습니다."}</p></details>
    {p.canWrite && <button type="button" disabled={p.busy} onClick={() => setManual(p.decisions)} className="mt-3 text-xs text-bb-primary">AI 없이 직접 정리 / 기존 결정사항 수정</button>}
    {manual !== null && <div className="mt-3"><textarea aria-label="결정사항 직접 정리" disabled={p.busy} value={manual} onChange={e => setManual(e.target.value)} className="w-full min-h-24 border border-bb-border rounded p-3 text-sm bg-bb-bg" /><button disabled={p.busy} onClick={async () => { p.onBusy(true); setError(""); try { await p.saveDecisions(manual); setManual(null); setNotice("결정사항을 저장했습니다."); } catch(e) { setError(apiError(e, e instanceof Error ? e.message : "저장 실패")); } finally { p.onBusy(false); } }} className="text-sm text-bb-primary mr-4">저장</button><button disabled={p.busy} onClick={() => setManual(null)} className="text-sm">취소</button></div>}
  </section>;
}
