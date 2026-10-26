"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import FileDownloadLink from "@/components/FileDownloadLink";
import { sessionGeneration } from "@/lib/sessionCache";
import type { ConfirmationView } from "@/types/confirmation";

function localNow() {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
export default function FinalSubmissionPanel({ projectId, deliverableId, isLeader, canWrite, submissionMethod, onChanged, onReview, onOverview, onDirty }: {
  projectId: string; deliverableId: string; isLeader: boolean; canWrite: boolean; submissionMethod: string | null;
  onChanged: () => void; onReview: () => void; onOverview: () => void; onDirty: (dirty: boolean) => void;
}) {
  const path = `/projects/${projectId}/deliverables/${deliverableId}`;
  const [view, setView] = useState<ConfirmationView | null>(null), [error, setError] = useState("");
  const [busy, setBusy] = useState(false), [loading, setLoading] = useState(false);
  const [confirmChecked, setConfirmChecked] = useState(false), [submittedChecked, setSubmittedChecked] = useState(false);
  const [channel, setChannel] = useState(submissionMethod ?? ""), [submittedAt, setSubmittedAt] = useState(localNow);
  const [note, setNote] = useState(""), [notice, setNotice] = useState("");
  const initialSubmittedAt = useRef(submittedAt);
  const pending = useRef(false), sequence = useRef(0);
  const mounted = useRef(true);
  useEffect(() => { setConfirmChecked(false); }, [view?.candidate?.reviewId, view?.candidate?.file.fileId]);
  const dirty = !!view?.confirmation && !view.submission && (!!note.trim() || channel !== (submissionMethod ?? "") || submittedAt !== initialSubmittedAt.current || submittedChecked);
  useEffect(() => { onDirty(dirty); return () => onDirty(false); }, [dirty, onDirty]);
  useEffect(() => {
    if (!dirty) return;
    const unload = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ""; };
    const navigate = (e: MouseEvent) => {
      const a = (e.target as HTMLElement).closest?.("a[href]");
      if (a?.hasAttribute("download")) return;
      if (a && !window.confirm("저장하지 않은 제출 기록이 있습니다. 이동할까요?")) { e.preventDefault(); e.stopPropagation(); }
    };
    window.addEventListener("beforeunload", unload); document.addEventListener("click", navigate, true);
    return () => { window.removeEventListener("beforeunload", unload); document.removeEventListener("click", navigate, true); };
  }, [dirty]);
  const load = useCallback(async () => {
    const ticket = ++sequence.current, generation = sessionGeneration(); setLoading(true);
    try {
      const { data } = await api.get<ConfirmationView>(`${path}/confirmation`);
      if (ticket === sequence.current && generation === sessionGeneration()) { setView(data); setError(""); }
    } catch (e) { if (ticket === sequence.current && generation === sessionGeneration()) setError(apiError(e, "최종본 정보를 불러오지 못했습니다.")); }
    finally { if (ticket === sequence.current) setLoading(false); }
  }, [path]);
  useEffect(() => { mounted.current = true; void load(); return () => { mounted.current = false; sequence.current++; }; }, [load]);
  const save = async (kind: "confirm" | "submit") => {
    if (!view || pending.current || loading) return;
    if (kind === "confirm" && (!isLeader || !view.canConfirm || !view.candidate || !confirmChecked)) return;
    if (kind === "submit" && (!canWrite || !view.confirmation || view.submission || !submittedChecked || !submittedAt)) return;
    const actualTime = new Date(submittedAt);
    if (kind === "submit" && (!Number.isFinite(actualTime.getTime()) || actualTime.getTime() > Date.now() + 300000)) { setError("실제로 제출한 시각을 확인해 주세요. 미래 시각으로 기록할 수 없습니다."); return; }
    pending.current = true; const ticket = ++sequence.current; setBusy(true); setError(""); setNotice("");
    const generation = sessionGeneration();
    try {
      const { data } = kind === "confirm"
        ? await api.put<ConfirmationView>(`${path}/confirmation`, { reviewId: view.candidate!.reviewId })
        : await api.put<ConfirmationView>(`${path}/submission`, { channel: channel.trim() || null, submittedAt: actualTime.toISOString(), note: note.trim() || null });
      if (generation !== sessionGeneration() || !mounted.current || ticket !== sequence.current) return;
      setView(data); setConfirmChecked(false); setSubmittedChecked(false); setNote(""); onDirty(false);
      setNotice(kind === "confirm" ? "최종본을 확정했습니다. 학교에 제출한 뒤 아래에 제출 기록을 남겨주세요." : "제출 기록을 저장했습니다."); onChanged();
    } catch (e) { if (generation === sessionGeneration() && mounted.current && ticket === sequence.current) setError(apiError(e, "저장하지 못했습니다. 입력 내용은 유지됩니다.")); }
    finally { pending.current = false; if (mounted.current && ticket === sequence.current) setBusy(false); }
  };
  const file = view?.confirmation?.file ?? view?.candidate?.file;
  return <section className="space-y-5" aria-label="최종본 확정과 제출 기록">
    <div><h2 className="text-lg font-semibold">최종본 확정·제출 기록</h2><p className="mt-2 text-sm text-bb-text2">팀이 확인한 파일 버전을 확정하고, 학교에 제출한 사실을 기록합니다.</p></div>
    {error && <div role="alert" className="text-sm text-red-600">{error} <button type="button" disabled={busy || loading} onClick={load} className="underline">정보 다시 확인</button></div>}
    {notice && <p role="status" className="text-sm text-bb-primary">{notice}</p>}
    {!view && !error && <p className="text-sm text-bb-text2">최종본 정보를 확인하는 중…</p>}
    {view && <>
      <ol className="flex flex-wrap gap-2 text-xs" aria-label="최종 제출 진행"><li>1. 최종 파일 검토</li><li>→ 2. 팀장 확정</li><li>→ 3. 학교 제출 후 기록</li></ol>
      {view.soleContributor && !view.confirmation && <p className="text-sm text-amber-700">파일을 올린 사람 외에 결과를 확인할 팀원이 필요합니다. 팀원을 초대한 뒤 검토를 진행하세요.</p>}
      {file && <div className="rounded-xl border border-bb-border bg-bb-surface p-4"><p className="text-xs text-bb-text2">{view.confirmation ? "확정된 파일" : "승인된 최종 후보"}</p><FileDownloadLink fileId={file.fileId} fileName={file.fileName} className="mt-2 inline-block break-all text-sm text-bb-primary underline">{file.fileName} · 버전 {file.version}</FileDownloadLink><p className="mt-1 text-xs text-bb-text2">등록 {file.uploaderName} · 파일 식별 {file.shortHash}</p></div>}
      {!view.confirmation ? <div className="space-y-3 rounded-xl border border-bb-border p-4">
        <h3 className="font-semibold text-sm">확정 전에 확인할 것</h3>
        <ul className="space-y-2 text-sm">{view.checks.map(check => <li key={check.code} className={check.passed ? "text-bb-text2" : "text-amber-700"}>{check.passed ? "✓" : "○"} {check.detail}</li>)}</ul>
        <div className="flex flex-wrap gap-3 text-sm"><button type="button" onClick={onOverview} className="text-bb-primary underline">요구사항 확인</button><button type="button" onClick={onReview} className="text-bb-primary underline">최종 파일 검토하기</button></div>
        {isLeader ? <><label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={confirmChecked} disabled={busy || !view.canConfirm} onChange={e => setConfirmChecked(e.target.checked)} />이 버전을 최종본으로 확정합니다. 확정 후 연결된 작업의 수정이 제한되며 되돌릴 수 없습니다.</label>
          <button type="button" disabled={busy || loading || !!error || !view.canConfirm || !confirmChecked} onClick={() => save("confirm")} className="rounded-lg bg-bb-primary px-4 py-2.5 text-sm text-white disabled:opacity-40">{busy ? "저장 중…" : "이 버전을 최종본으로 확정"}</button></> : <p className="text-sm text-bb-text2">검토가 끝나면 팀장이 최종본을 확정합니다.</p>}
      </div> : <p className="text-sm text-bb-text2">{view.confirmation.confirmedBy.name}님이 {new Date(view.confirmation.confirmedAt).toLocaleString("ko-KR")}에 확정했습니다.{!view.confirmation.fileStillLatest && " 이후 다른 내용의 파일이 등록됐지만 확정본은 위 버전으로 보존됩니다."}</p>}
      {view.submission ? <div role="status" className="rounded-xl border border-bb-border p-4 space-y-2"><h3 className="font-semibold text-sm">제출 기록 완료</h3><p className="text-sm">제출 경로: {view.submission.channel || "미기록"}</p><p className="text-sm">실제 제출 시각: {new Date(view.submission.submittedAt).toLocaleString("ko-KR")}</p><p className="text-sm">기록자: {view.submission.recordedBy.name}</p><p className="text-sm whitespace-pre-wrap">{view.submission.note}</p><p className="text-xs text-bb-text2">이 기록은 학교 시스템의 접수 확인을 대신하지 않습니다.</p></div> : view.confirmation && <form className="space-y-4 rounded-xl border border-bb-border p-4" onSubmit={e => { e.preventDefault(); void save("submit"); }}>
        <h3 className="font-semibold text-sm">학교에 제출한 뒤 기록하기</h3><p className="text-sm text-bb-text2">위 확정 파일을 내려받아 학교 시스템에 직접 제출하세요. 이 화면에서 학교로 자동 전송하지 않습니다. 기록은 한 번만 남길 수 있습니다.</p>
        <fieldset disabled={!canWrite || busy} className="space-y-3">
          <label className="block text-sm">제출 경로 (선택)<input aria-label="제출 경로" value={channel} maxLength={500} onChange={e => setChannel(e.target.value)} placeholder="예: 학교 LMS 과제 게시판" className="mt-1 block w-full rounded-lg border border-bb-border bg-bb-bg p-2.5" /></label>
          <label className="block text-sm">실제로 제출한 시각<input aria-label="실제 제출 시각" required type="datetime-local" value={submittedAt} onChange={e => setSubmittedAt(e.target.value)} className="mt-1 block w-full rounded-lg border border-bb-border bg-bb-bg p-2.5" /></label>
          <label className="block text-sm">접수 확인 내용 (선택)<textarea aria-label="제출 기록 메모" value={note} maxLength={1000} onChange={e => setNote(e.target.value)} placeholder="예: 접수번호 또는 학교 화면에서 확인한 내용" className="mt-1 block w-full rounded-lg border border-bb-border bg-bb-bg p-2.5" /></label>
          <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={submittedChecked} onChange={e => setSubmittedChecked(e.target.checked)} />학교 시스템에 직접 제출했으며 위 내용으로 제출 기록을 남깁니다.</label>
          <button type="submit" disabled={busy || loading || !!error || !submittedChecked} className="rounded-lg bg-bb-primary px-4 py-2.5 text-sm text-white disabled:opacity-40">{busy ? "저장 중…" : "제출 기록 저장"}</button>
        </fieldset>{!canWrite && <p className="text-sm text-bb-text2">관찰자는 제출 기록을 볼 수만 있습니다.</p>}
      </form>}
    </>}
  </section>;
}
