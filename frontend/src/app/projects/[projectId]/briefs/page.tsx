"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { BookOpenText, Lock, Plus } from "lucide-react";
import ProjectNav from "@/components/project/ProjectNav";
import Sidebar from "@/components/Sidebar";
import FileDownloadLink from "@/components/FileDownloadLink";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import { sessionGeneration } from "@/lib/sessionCache";
import { useMyRole } from "@/hooks/useMyRole";
import { useUnsavedGuard } from "@/hooks/useUnsavedGuard";
import { BRIEF_MAX, BRIEF_TITLE_MAX, briefLength, defaultBriefTitle, normalizeBrief, problem } from "@/lib/briefText";
import type { Brief } from "@/types/brief";
import type { FileRecord } from "@/types/vault";

type Draft = { mode: "new" | "edit"; title: string; body: string; sourceFileId: string; version: number | null; baseId: string | null };
const EMPTY: Draft = { mode: "new", title: "", body: "", sourceFileId: "", version: null, baseId: null };
const STATUS_LABEL: Record<string, string> = { DRAFT: "준비 중", IN_REVIEW: "검토 중", CONFIRMED: "확정", SUBMITTED: "제출 기록" };
const fmt = (iso: string) => new Date(iso).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });

/** K-31 2장: 프로젝트의 과제 안내문 공간. 계획 만들기와 요구사항 찾기가 이 글을 근거로 쓴다 */
export default function BriefsPage() {
  const projectId = useParams()?.projectId as string;
  const router = useRouter();
  const wanted = useSearchParams()?.get("brief") ?? null;
  const readOnly = useMyRole(projectId) === "OBSERVER";
  const [briefs, setBriefs] = useState<Brief[] | null>(null);
  const [files, setFiles] = useState<FileRecord[]>([]);
  // 고르기 전에는 주소의 ?brief=를 따른다(제출물·카드에서 바로 열 때)
  const [picked, setSelected] = useState<string | null | undefined>(undefined);
  const selected = picked === undefined ? wanted : picked;
  const [draft, setDraft] = useState<Draft | null>(null);
  const [conflict, setConflict] = useState<Brief | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(""), [notice, setNotice] = useState("");
  const pending = useRef(false);

  const base = draft?.baseId ? briefs?.find((b) => b.id === draft.baseId) : null;
  const dirty = !!draft && (draft.mode === "new"
    ? !!draft.title.trim() || !!draft.body.trim() || !!draft.sourceFileId
    : !!base && (draft.title !== base.title || draft.body !== base.body || draft.sourceFileId !== (base.sourceFile?.id ?? "")));
  useUnsavedGuard(dirty, "저장하지 않은 안내문이 있습니다. 이 화면을 떠날까요?");

  const load = useCallback(async () => {
    const generation = sessionGeneration();
    try {
      const [b, f] = await Promise.all([
        api.get<Brief[]>(`/projects/${projectId}/briefs`),
        api.get<FileRecord[]>(`/projects/${projectId}/files`).catch(() => ({ data: [] as FileRecord[] })),
      ]);
      if (generation !== sessionGeneration()) return;
      setBriefs(b.data); setFiles(f.data); setError("");
    } catch (e) {
      if ((e as { response?: { status?: number } })?.response?.status === 401) { router.replace("/login"); return; }
      if (generation === sessionGeneration()) setError(apiError(e, "안내문을 불러오지 못했습니다."));
    }
  }, [projectId, router]);
  useEffect(() => { void load(); }, [load]);

  const current = briefs?.find((b) => b.id === selected) ?? null;
  // 서버 목록과 같은 "최근 수정 먼저"를 지킨다. 응답의 안내문이 목록에 있으면 바꾸고, 없으면(내가 목록을 연 뒤
  // 팀원이 만든 같은 본문이 200으로 온 경우 등, #187 B 리뷰) 넣는다. 200은 수정 시각이 그대로라 자리가 바뀌지 않는다
  const replace = (b: Brief) => setBriefs((list) =>
    [...(list ?? []).filter((x) => x.id !== b.id), b].sort((x, y) => Date.parse(y.updatedAt) - Date.parse(x.updatedAt)));
  const open = (id: string | null) => {
    if (dirty && !window.confirm("저장하지 않은 안내문이 있습니다. 다른 안내문을 열까요?")) return;
    setDraft(null); setConflict(null); setSelected(id); setNotice(""); setError("");
  };

  const save = async () => {
    if (!draft || pending.current) return;
    const body = normalizeBrief(draft.body);
    const title = normalizeBrief(draft.title) || defaultBriefTitle(draft.body);
    if (!body) { setError("본문을 입력해 주세요."); return; }
    if (body.length > BRIEF_MAX) { setError(`본문은 최대 ${BRIEF_MAX}자입니다. 지금 ${body.length}자입니다.`); return; }
    pending.current = true; setBusy(true); setError(""); setNotice("");
    const generation = sessionGeneration();
    try {
      const payload = { title, body: draft.body, sourceFileId: draft.sourceFileId || null };
      if (draft.mode === "new") {
        const res = await api.post<Brief>(`/projects/${projectId}/briefs`, payload);
        if (generation !== sessionGeneration()) return;
        replace(res.data);
        setSelected(res.data.id); setDraft(null);
        // 200은 본문이 같은 안내문이 이미 있어 새로 만들지 않았다는 뜻이다(2.2). 입력한 제목·원본 파일은 쓰이지 않는다
        setNotice(res.status === 200
          ? `본문이 같은 안내문이 이미 있어 그것을 엽니다(입력한 제목과 원본 파일은 바꾸지 않았습니다): ${res.data.title}`
          : `안내문을 저장했습니다: ${res.data.title}`);
      } else {
        const res = await api.put<Brief>(`/projects/${projectId}/briefs/${draft.baseId}`, { ...payload, version: draft.version });
        if (generation !== sessionGeneration()) return;
        replace(res.data); setDraft(null); setConflict(null); setNotice(`안내문을 저장했습니다: ${res.data.title}`);
      }
    } catch (e) {
      if (generation !== sessionGeneration()) return;
      const p = problem<{ brief: Brief }>(e);
      if (p?.code === "BRIEF_CHANGED" && p.brief) {
        // 입력은 지우지 않고 최신 본문을 함께 보여 준다. 다시 저장하면 최신 판 위에 덮어쓴다
        replace(p.brief); setConflict(p.brief); setDraft((d) => d && { ...d, version: p.brief!.version });
        setError(p.detail ?? "다른 사람이 먼저 이 안내문을 고쳤습니다.");
      } else if (p?.code === "BRIEF_LOCKED") {
        setError(p.detail ?? "확정된 제출물이 쓰는 안내문은 고칠 수 없습니다."); void load();
      } else setError(apiError(e, "저장하지 못했습니다. 입력한 내용은 그대로 있습니다."));
    } finally { pending.current = false; setBusy(false); }
  };

  const remove = async (b: Brief) => {
    if (pending.current || !window.confirm(`안내문 "${b.title}"을(를) 지울까요?`)) return;
    pending.current = true; setBusy(true); setError(""); setNotice("");
    try {
      await api.delete(`/projects/${projectId}/briefs/${b.id}`);
      setBriefs((list) => (list ?? []).filter((x) => x.id !== b.id)); setSelected(null); setNotice(`안내문을 지웠습니다: ${b.title}`);
    } catch (e) {
      const p = problem(e);
      setError(p?.detail ?? apiError(e, "지우지 못했습니다."));
      if (p?.code === "BRIEF_IN_USE") void load();
    } finally { pending.current = false; setBusy(false); }
  };

  const length = draft ? briefLength(draft.body) : 0;
  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar />
      <main className="md:ml-64 mt-14 md:mt-0 min-h-screen p-4 md:p-8">
        <ProjectNav projectId={projectId} />
        <div className="flex flex-wrap items-start justify-between gap-3 mb-6">
          <div>
            <h1 className="text-2xl font-bold text-bb-text flex items-center gap-2"><BookOpenText size={22} className="text-indigo-400" />과제 안내문</h1>
            <p className="text-sm text-bb-text2 mt-1">교수님의 과제 안내와 공지를 붙여 두면 계획 만들기와 요구사항 찾기가 이 글을 근거로 씁니다. 개인정보는 적지 마세요.</p>
          </div>
          {!readOnly && <button type="button" disabled={busy} onClick={() => { if (dirty && !window.confirm("저장하지 않은 안내문이 있습니다. 새로 쓸까요?")) return; setSelected(null); setConflict(null); setNotice(""); setError(""); setDraft({ ...EMPTY }); }}
            className="flex items-center gap-1.5 rounded-lg bg-bb-primary px-3 py-2 text-sm text-white disabled:opacity-40"><Plus size={14} />새 안내문</button>}
        </div>

        {error && <p role="alert" className="mb-4 rounded-lg border border-red-500/20 bg-red-500/10 p-3 text-sm text-red-500">{error}</p>}
        {notice && <p role="status" className="mb-4 text-sm text-bb-primary">{notice}</p>}

        <div className="grid gap-6 lg:grid-cols-[minmax(16rem,22rem)_1fr]">
          {/* 목록: 최근 수정이 먼저 */}
          <section aria-label="안내문 목록" className="space-y-2">
            {briefs === null ? <p className="text-sm text-bb-text2">불러오는 중…</p>
              : briefs.length === 0 ? <p className="rounded-xl border border-dashed border-bb-border p-4 text-sm text-bb-text2">아직 안내문이 없습니다.{!readOnly && " 과제 안내를 붙여 넣어 시작하세요."}</p>
              : briefs.map((b) => (
                <button key={b.id} type="button" onClick={() => open(b.id)} aria-current={b.id === selected ? "true" : undefined}
                  className={`w-full rounded-xl border p-3 text-left ${b.id === selected ? "border-bb-primary bg-bb-primary/5" : "border-bb-border bg-bb-surface hover:bg-bb-surface2"}`}>
                  <span className="flex items-center gap-1.5 text-sm font-semibold text-bb-text break-all">{b.locked && <Lock size={12} aria-label="고칠 수 없음" />}{b.title}</span>
                  <span className="mt-1 block text-xs text-bb-text2">
                    {b.usage.deliverables.length > 0 ? `제출물 ${b.usage.deliverables.map((d) => d.title).join(", ")}` : "걸린 제출물 없음"}
                    {" · "}{b.updatedBy.name} {fmt(b.updatedAt)}
                  </span>
                </button>
              ))}
          </section>

          {/* 등록·수정 */}
          {draft ? (
            <form aria-label={draft.mode === "new" ? "새 안내문" : "안내문 고치기"} className="space-y-4 rounded-xl border border-bb-border bg-bb-surface p-5"
              onSubmit={(e) => { e.preventDefault(); void save(); }}>
              <h2 className="font-semibold text-bb-text">{draft.mode === "new" ? "새 안내문" : "안내문 고치기"}</h2>
              <label className="block text-sm">제목 {draft.mode === "new" && <span className="text-bb-text2">(비우면 본문의 첫 줄)</span>}
                <input aria-label="안내문 제목" value={draft.title} maxLength={BRIEF_TITLE_MAX} onChange={(e) => setDraft({ ...draft, title: e.target.value })}
                  placeholder={draft.body ? defaultBriefTitle(draft.body) : "예: [캡스톤디자인] 중간 보고서 제출 안내"} className="mt-1 block w-full rounded-lg border border-bb-border bg-bb-bg p-2.5" />
              </label>
              <label className="block text-sm">본문
                <textarea aria-label="안내문 본문" value={draft.body} rows={14} onChange={(e) => setDraft({ ...draft, body: e.target.value })}
                  placeholder="과제 안내·공지를 그대로 붙여 넣으세요" className="mt-1 block w-full rounded-lg border border-bb-border bg-bb-bg p-2.5 text-sm leading-relaxed" />
                <span className={`mt-1 block text-right text-xs ${length > BRIEF_MAX ? "text-red-500" : "text-bb-text2"}`}>{length.toLocaleString()} / {BRIEF_MAX.toLocaleString()}자</span>
              </label>
              <label className="block text-sm">원본 파일 (선택)
                <select aria-label="원본 파일" value={draft.sourceFileId} onChange={(e) => setDraft({ ...draft, sourceFileId: e.target.value })} className="mt-1 block w-full rounded-lg border border-bb-border bg-bb-bg p-2.5">
                  <option value="">없음</option>
                  {base?.sourceFile && !files.some((f) => f.id === base.sourceFile!.id) && <option value={base.sourceFile.id}>{base.sourceFile.fileName} · v{base.sourceFile.version}</option>}
                  {files.map((f) => <option key={f.id} value={f.id}>{f.fileName} · v{f.version}</option>)}
                </select>
              </label>
              {conflict && (
                <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm" data-testid="brief-conflict">
                  <p className="font-semibold text-amber-700 dark:text-amber-300">{conflict.updatedBy.name}님이 {fmt(conflict.updatedAt)}에 고친 최신 내용</p>
                  <p className="mt-1 text-xs text-bb-text2">아래 최신 내용을 확인하세요. 내 입력은 그대로 있으며, 다시 저장하면 최신 내용 위에 내 입력이 저장됩니다.</p>
                  <p className="mt-2 font-semibold break-all">{conflict.title}</p>
                  <pre className="mt-1 max-h-60 overflow-auto whitespace-pre-wrap break-words font-sans text-xs">{conflict.body}</pre>
                </div>
              )}
              <div className="flex flex-wrap gap-2">
                <button type="submit" disabled={busy || !draft.body.trim() || length > BRIEF_MAX} className="rounded-lg bg-bb-primary px-4 py-2.5 text-sm text-white disabled:opacity-40">{busy ? "저장 중…" : conflict ? "내 내용으로 다시 저장" : "저장"}</button>
                {/* 충돌 뒤 두 선택을 한 화면에: 내 입력을 지키거나, 팀원의 최신 내용에서 다시 시작하거나 */}
                {conflict && <button type="button" disabled={busy} onClick={() => {
                  if (!window.confirm("내 입력을 버리고 최신 내용에서 다시 고칠까요?")) return;
                  setDraft({ ...draft, title: conflict.title, body: conflict.body, sourceFileId: conflict.sourceFile?.id ?? "", version: conflict.version });
                  setConflict(null); setError("");
                }} className="rounded-lg border border-bb-border px-4 py-2.5 text-sm">최신 내용으로 다시 고치기</button>}
                <button type="button" disabled={busy} onClick={() => { if (dirty && !window.confirm("입력한 내용을 버릴까요?")) return; setDraft(null); setConflict(null); setError(""); }} className="rounded-lg border border-bb-border px-4 py-2.5 text-sm">취소</button>
              </div>
            </form>
          ) : current ? (
            <article aria-label="안내문" className="space-y-4 rounded-xl border border-bb-border bg-bb-surface p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <h2 className="font-semibold text-bb-text break-all">{current.title}</h2>
                  <p className="mt-1 text-xs text-bb-text2">{current.createdBy.name} 등록 · 마지막 수정 {current.updatedBy.name} {fmt(current.updatedAt)}</p>
                  {current.sourceFile && <FileDownloadLink fileId={current.sourceFile.id} fileName={current.sourceFile.fileName} className="mt-1 inline-block break-all text-xs text-bb-primary underline">원본 파일: {current.sourceFile.fileName} · v{current.sourceFile.version}</FileDownloadLink>}
                </div>
                {!readOnly && (
                  <div className="flex gap-2">
                    <button type="button" disabled={busy || current.locked} onClick={() => { setNotice(""); setError(""); setConflict(null); setDraft({ mode: "edit", title: current.title, body: current.body, sourceFileId: current.sourceFile?.id ?? "", version: current.version, baseId: current.id }); }}
                      className="rounded-lg border border-bb-border px-3 py-1.5 text-sm disabled:opacity-40">고치기</button>
                    <button type="button" disabled={busy || current.usage.deliverables.length > 0 || current.usage.pendingProposals > 0} onClick={() => void remove(current)}
                      className="rounded-lg border border-red-500/30 px-3 py-1.5 text-sm text-red-500 disabled:opacity-40">지우기</button>
                  </div>
                )}
              </div>
              {current.locked && <p className="text-xs text-amber-700 dark:text-amber-300">확정·제출된 제출물이 이 안내문을 써서 고칠 수 없습니다. 바뀐 내용은 추가 공지로 새로 등록해 아직 확정하지 않은 제출물에 거세요.</p>}
              {(current.usage.deliverables.length > 0 || current.usage.pendingProposals > 0) && (
                <div className="rounded-lg bg-bb-bg p-3 text-xs text-bb-text2" data-testid="brief-usage">
                  <p className="font-semibold text-bb-text">이 안내문을 쓰는 곳{!readOnly && " — 쓰는 곳이 있으면 지울 수 없습니다"}</p>
                  <ul className="mt-1 space-y-0.5">
                    {current.usage.deliverables.map((d) => <li key={d.id}><Link href={`/projects/${projectId}/deliverables/${d.id}`} className="text-bb-primary underline">{d.title}</Link> ({STATUS_LABEL[d.status] ?? d.status})</li>)}
                    {current.usage.pendingProposals > 0 && <li>AI 도우미의 검토 대기 제안 {current.usage.pendingProposals}개</li>}
                  </ul>
                </div>
              )}
              <pre data-testid="brief-body" className="max-h-[32rem] overflow-auto whitespace-pre-wrap break-words rounded-lg bg-bb-bg p-4 font-sans text-sm leading-relaxed text-bb-text">{current.body}</pre>
            </article>
          ) : briefs && briefs.length > 0 && (
            <p className="text-sm text-bb-text2">{selected ? "그 안내문을 찾을 수 없습니다. 지워졌을 수 있습니다." : "왼쪽에서 안내문을 고르세요."}</p>
          )}
        </div>
      </main>
    </div>
  );
}
