"use client";
import { useRef, useState } from "react";
import Link from "next/link";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import { sessionGeneration } from "@/lib/sessionCache";
import type { Brief, BriefSummary } from "@/types/brief";

/**
 * 제출물에 걸린 과제 안내문(K-31 3장). 요구사항 찾기는 걸린 안내문들을 읽는다.
 * 확정·제출된 제출물에는 걸거나 뗄 수 없다(서버 409와 같은 조건으로 버튼을 끈다)
 */
export default function DeliverableBriefs({ projectId, deliverableId, briefs, canWrite, locked, onChange }: {
  projectId: string; deliverableId: string; briefs: BriefSummary[]; canWrite: boolean; locked: boolean;
  onChange: (briefs: BriefSummary[]) => void;
}) {
  const [all, setAll] = useState<Brief[] | null>(null);
  const [picking, setPicking] = useState(false), [choice, setChoice] = useState("");
  const [busy, setBusy] = useState(false), [error, setError] = useState("");
  const pending = useRef(false);
  const base = `/projects/${projectId}`;

  const openPicker = async () => {
    setPicking(true); setError(""); setChoice("");
    try { setAll((await api.get<Brief[]>(`${base}/briefs`)).data); }
    catch (e) { setError(apiError(e, "안내문 목록을 불러오지 못했습니다.")); }
  };
  const change = async (method: "post" | "delete", briefId: string) => {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError("");
    const generation = sessionGeneration();
    try {
      const { data } = await api[method]<BriefSummary[]>(`${base}/deliverables/${deliverableId}/briefs/${briefId}`);
      if (generation !== sessionGeneration()) return;
      onChange(data); setPicking(false); setChoice("");
    } catch (e) { if (generation === sessionGeneration()) setError(apiError(e, method === "post" ? "안내문을 걸지 못했습니다." : "안내문을 떼지 못했습니다.")); }
    finally { pending.current = false; setBusy(false); }
  };
  const candidates = (all ?? []).filter((b) => !briefs.some((x) => x.id === b.id));
  const editable = canWrite && !locked;

  return (
    <section aria-label="과제 안내문" className="bg-bb-surface border border-bb-border rounded-xl p-5">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="text-sm font-semibold text-bb-text">과제 안내문 <span className="text-bb-text2">{briefs.length}</span></h2>
          <p className="mt-1 text-xs text-bb-text2">요구사항 찾기는 여기에 건 안내문을 읽고 빠졌거나 모호한 요구사항을 찾습니다.</p>
        </div>
        {editable && !picking && <button type="button" disabled={busy} onClick={() => void openPicker()} className="rounded-lg border border-bb-border px-3 py-1.5 text-xs disabled:opacity-40">안내문 걸기</button>}
      </div>
      {error && <p role="alert" className="mt-2 text-xs text-red-500">{error}</p>}
      {picking && (
        <div className="mt-3 flex flex-wrap items-center gap-2 rounded-lg bg-bb-bg p-3">
          {all === null ? <span className="text-xs text-bb-text2">불러오는 중…</span>
            : candidates.length === 0 ? <span className="text-xs text-bb-text2">걸 수 있는 안내문이 없습니다. <Link href={`${base}/briefs`} className="text-bb-primary underline">과제 안내문</Link>에서 먼저 등록하세요.</span>
            : <>
              <select aria-label="걸 안내문" value={choice} onChange={(e) => setChoice(e.target.value)} className="min-w-0 flex-1 rounded-lg border border-bb-border bg-bb-surface p-2 text-sm">
                <option value="">안내문 고르기</option>
                {candidates.map((b) => <option key={b.id} value={b.id}>{b.title}</option>)}
              </select>
              <button type="button" disabled={busy || !choice} onClick={() => void change("post", choice)} className="rounded-lg bg-bb-primary px-3 py-2 text-xs text-white disabled:opacity-40">{busy ? "거는 중…" : "걸기"}</button>
            </>}
          <button type="button" disabled={busy} onClick={() => { setPicking(false); setError(""); }} className="rounded-lg border border-bb-border px-3 py-2 text-xs">닫기</button>
        </div>
      )}
      {briefs.length === 0 ? (
        <p className="mt-3 text-xs text-bb-text2">{editable ? <>걸린 안내문이 없습니다. <Link href={`${base}/briefs`} className="text-bb-primary underline">과제 안내문</Link>에 등록한 뒤 거세요.</> : "걸린 안내문이 없습니다."}</p>
      ) : (
        <ul className="mt-3 space-y-1.5">
          {briefs.map((b) => (
            <li key={b.id} className="flex flex-wrap items-center gap-2 rounded-lg bg-bb-bg/60 px-3 py-2 text-sm">
              <Link href={`${base}/briefs?brief=${b.id}`} className="min-w-0 flex-1 break-all text-bb-primary underline">{b.title}</Link>
              <span className="text-xs text-bb-text2">{new Date(b.linkedAt).toLocaleDateString("ko-KR")} 걸음</span>
              {editable && <button type="button" disabled={busy} onClick={() => void change("delete", b.id)} aria-label={`${b.title} 떼기`} className="text-xs text-bb-text2 underline disabled:opacity-40">떼기</button>}
            </li>
          ))}
        </ul>
      )}
      {locked && briefs.length > 0 && <p className="mt-2 text-xs text-bb-text2">최종본이 확정되어 안내문을 걸거나 뗄 수 없습니다.</p>}
    </section>
  );
}
