"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import type { Task } from "@/types/task";
import { useMyRole } from "@/hooks/useMyRole";

interface Completion { task: Task; checks: { code: string; label: string; passed: boolean }[]; canRequest: boolean; canApprove: boolean; canReview: boolean }
export default function TaskCompletionPanel({ task: initialTask, disabled, onChanged, onSnapshot, onBusy, refreshKey = 0 }: {
  task: Task; disabled: boolean; onChanged: (task: Task) => void; onSnapshot?: (task: Task) => void; onBusy: (busy: boolean) => void; refreshKey?: number;
}) {
  const path = `/projects/${initialTask.projectId}/tasks/${initialTask.id}`;
  const role = useMyRole(initialTask.projectId);
  const [view, setView] = useState<Completion | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [returning, setReturning] = useState(false);
  const [reason, setReason] = useState("");
  const snapshot = useRef(onSnapshot); snapshot.current = onSnapshot;
  const sequence = useRef(0);
  const load = useCallback(async () => {
    const request = ++sequence.current;
    try { const { data } = await api.get<Completion>(`${path}/completion`);
      if (request !== sequence.current) return;
      setView(data); setError(""); snapshot.current?.(data.task);
    }
    catch(e) { if (request === sequence.current) setError(apiError(e, "결과 확인 조건을 불러오지 못했습니다.")); }
  }, [path]);
  useEffect(() => { load(); return () => { sequence.current++; }; }, [load, initialTask.updatedAt, initialTask.completionRequestedAt, initialTask.completionApprovedAt, refreshKey]);
  useEffect(() => {
    const refresh = () => { if (!document.hidden && !busy) load(); };
    window.addEventListener("focus", refresh);
    const timer = window.setInterval(refresh, 15000);
    return () => { window.removeEventListener("focus", refresh); window.clearInterval(timer); };
  }, [load, busy]);
  // 권한과 요청 상태는 같은 서버 응답으로 판정한다. 오래 열린 보드의 task prop으로 버튼을 숨기지 않는다.
  const task = view?.task.id === initialTask.id ? view.task : initialTask;
  const run = async (request: () => Promise<{ data: Task }>) => {
    setBusy(true); onBusy(true); setError("");
    try { const { data } = await request(); sequence.current++; setView(null); onChanged(data); setReason(""); setReturning(false); window.dispatchEvent(new Event("task-feedback-changed")); await load(); }
    catch(e) { setError(apiError(e, "요청을 처리하지 못했습니다.")); }
    finally { setBusy(false); onBusy(false); }
  };
  const pending = !!task.completionRequestedAt;
  const missing = view?.checks.filter(c => !c.passed) ?? [];
  return <section className="border border-bb-border rounded-xl bg-bb-bg p-4 space-y-3">
    <h3 className="font-semibold text-sm">{task.status === "DONE" ? "확인 완료" : pending ? "팀원 확인" : "결과 제출"}</h3>
    {error && <p role="alert" className="text-sm text-red-600">{error} <button type="button" onClick={load} className="underline">다시 확인</button></p>}
    {!view && !error && <p className="text-sm text-bb-text2">조건을 확인하는 중…</p>}
    {missing.length > 0 && <ul className="space-y-1 text-sm text-amber-700">{missing.map(c => <li key={c.code}>○ {c.label}</li>)}</ul>}
    {task.completionRejectionReason && <div className="rounded-lg border border-amber-500/30 p-3"><p className="text-xs font-semibold text-amber-700">보완할 내용</p><p className="text-sm whitespace-pre-wrap mt-1">{task.completionRejectionReason}</p></div>}
    {pending && <p role="status" className="text-sm text-bb-text2">{task.completionRequestedByName}님이 결과 확인을 요청했습니다. 담당자와 요청자를 제외한 팀원 한 명이 확인하면 완료됩니다.</p>}
    {pending && view && !view.canReview && <p className="text-xs text-bb-text2">{role === "OBSERVER" ? "관찰자는 결과를 확인하거나 보완을 요청할 수 없습니다." : "담당자와 요청자는 본인 결과를 완료 처리할 수 없습니다. 다른 팀원의 확인을 기다리고 있습니다."}</p>}
    {task.status === "DONE" && <p role="status" className="text-sm text-bb-primary">{task.completionApprovedAt ? `${task.completionApprovedByName}님이 결과를 확인했습니다.` : "이전 완료 기록 · 확인자 기록 없음"}</p>}
    {!pending && task.status === "IN_PROGRESS" && role !== "OBSERVER" && <>
      <p className="text-xs text-bb-text2">완료 기준에 맞게 작성했는지 팀원에게 확인받습니다.</p>
      <button type="button" disabled={disabled || busy || !view?.canRequest} onClick={() => run(() => api.post<Task>(`${path}/completion-request`))} className="w-full px-3 py-3 rounded-lg bg-bb-primary text-white text-sm font-medium disabled:opacity-40">결과 제출하고 확인 요청</button>
      {view && !view.canRequest && !missing.length && <p className="text-xs text-bb-text2">담당자 또는 팀장이 결과 확인을 요청할 수 있습니다.</p>}
    </>}
    {view?.canReview && pending && <>
      <p className="text-xs text-bb-text2">위의 완료 기준과 결과 내용을 확인한 뒤 처리하세요.</p>
      <div className="flex gap-2"><button type="button" disabled={disabled || busy || !view.canApprove} onClick={() => run(() => api.put<Task>(`${path}/completion-decision`, { approved: true, criteriaConfirmed: true }))} className="flex-1 px-3 py-3 rounded-lg bg-bb-primary text-white text-sm disabled:opacity-40">확인하고 완료</button>
      <button type="button" disabled={disabled || busy} onClick={() => setReturning(v => !v)} className="px-3 py-3 rounded-lg border border-bb-border text-sm">보완 요청</button></div>
      {returning && <div className="space-y-2"><textarea aria-label="보완 요청 사유" maxLength={2000} value={reason} disabled={disabled || busy} onChange={e => setReason(e.target.value)} placeholder="어떤 부분을 보완하면 되는지 적어주세요" className="w-full min-h-20 p-3 border border-bb-border rounded bg-bb-surface text-sm" />
      <button type="button" disabled={disabled || busy || !reason.trim()} onClick={() => run(() => api.put<Task>(`${path}/completion-decision`, { approved: false, reason }))} className="px-3 py-2 rounded-lg border border-bb-border text-sm disabled:opacity-40">보완 내용 전달</button></div>}
    </>}
    {!pending && task.status === "TODO" && role !== "OBSERVER" && <button type="button" disabled={disabled || busy} onClick={() => run(() => api.patch<Task>(`${path}/status`, { status: "IN_PROGRESS" }))} className="w-full px-3 py-3 rounded-lg bg-bb-primary text-white text-sm disabled:opacity-40">업무 시작</button>}
    {role === "LEADER" && task.status === "DONE" && <button type="button" disabled={disabled || busy} onClick={() => { const message = window.prompt("업무를 다시 진행해야 하는 사유를 적어주세요."); if (message?.trim()) run(() => api.post<Task>(`${path}/reopen`, { reason: message.trim() })); }} className="text-sm underline text-bb-text2">업무 다시 열기</button>}
  </section>;
}
