"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ClipboardList, Plus, ArrowRight, Pencil, Trash2, X } from "lucide-react";
import Sidebar from "@/components/Sidebar";
import TaskModal from "@/components/kanban/TaskModal";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import { Deliverable, DeliverableRequirement } from "@/types/deliverable";
import { CreateTaskPayload, Task } from "@/types/task";

interface Member { userId: string; name: string; email: string; role: string }
const field = "w-full rounded-lg border border-bb-border bg-bb-bg px-3 py-2 text-sm text-bb-text focus:outline-none focus:ring-2 focus:ring-teal-500";
const primary = "inline-flex items-center justify-center gap-2 rounded-lg bg-teal-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50";
const secondary = "inline-flex items-center gap-2 rounded-lg border border-bb-border px-3 py-2 text-sm text-bb-text hover:bg-bb-surface2 disabled:opacity-50";
const emptyForm = { title: "", description: "", dueDate: "", submissionMethod: "", ownerId: "" };

export default function DeliverablesPage() {
  const projectId = useParams<{ projectId: string }>().projectId;
  const [projectName, setProjectName] = useState("");
  const [deliverables, setDeliverables] = useState<Deliverable[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [canEdit, setCanEdit] = useState(false);
  const [selectedId, setSelectedId] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [requirementContent, setRequirementContent] = useState("");
  const [required, setRequired] = useState(true);
  const [editingRequirement, setEditingRequirement] = useState<string | null>(null);
  const [taskDraft, setTaskDraft] = useState<{ deliverableId: string; requirementId: string } | null>(null);
  const [linkTaskId, setLinkTaskId] = useState("");
  const [confirmDelete, setConfirmDelete] = useState<{ id: string; requirementId?: string } | null>(null);

  const load = useCallback(async () => {
    try {
      const [ds, ts, ms, profile, project] = await Promise.all([
        api.get<Deliverable[]>(`/projects/${projectId}/deliverables`),
        api.get<Task[]>(`/projects/${projectId}/tasks`),
        api.get<Member[]>(`/projects/${projectId}/members`),
        api.get<{ id: string }>("/auth/profile"),
        api.get<{ name: string }>(`/projects/${projectId}`),
      ]);
      setDeliverables(ds.data); setTasks(ts.data); setMembers(ms.data); setProjectName(project.data.name);
      const role = ms.data.find(m => m.userId === profile.data.id)?.role;
      setCanEdit(role === "LEADER" || role === "MEMBER");
      setSelectedId(previous => ds.data.some(d => d.id === previous) ? previous : ds.data[0]?.id ?? "");
      setLoadError("");
    } catch (e) {
      setLoadError(apiError(e, "제출물 정보를 불러오지 못했습니다. 다시 시도해주세요."));
      throw e;
    }
  }, [projectId]);

  useEffect(() => {
    setLoading(true);
    void load().catch(() => {}).finally(() => setLoading(false));
  }, [load]);
  useEffect(() => {
    setRequirementContent(""); setRequired(true); setEditingRequirement(null); setLinkTaskId(""); setConfirmDelete(null);
  }, [selectedId]);

  const selected = deliverables.find(d => d.id === selectedId);
  const linkedTasks = tasks.filter(t => t.deliverableId === selectedId);
  const unlinkedTasks = tasks.filter(t => !t.deliverableId);
  const beginEdit = (d?: Deliverable) => {
    setEditing(d?.id ?? null);
    setForm(d ? { title: d.title, description: d.description ?? "", dueDate: d.dueDate,
      submissionMethod: d.submissionMethod ?? "", ownerId: d.ownerId ?? "" } : emptyForm);
    setError(""); setShowForm(true);
  };
  const mutate = async (action: () => Promise<unknown>, success: () => void) => {
    setBusy(true); setError(""); setNotice("");
    try {
      await action();
      success();
      setNotice("저장했습니다.");
      await load().catch(() => setNotice("저장했지만 목록을 새로 불러오지 못했습니다. 다시 불러오기를 눌러주세요."));
    } catch (e) { setError(apiError(e)); }
    finally { setBusy(false); }
  };
  const saveDeliverable = (event: FormEvent) => {
    event.preventDefault();
    if (!form.title.trim()) { setError("제출물 이름을 입력해주세요."); return; }
    void mutate(async () => {
      const payload = { ...form, title: form.title.trim(), ownerId: form.ownerId || null };
      const response = editing
        ? await api.put<Deliverable>(`/projects/${projectId}/deliverables/${editing}`, payload)
        : await api.post<Deliverable>(`/projects/${projectId}/deliverables`, payload);
      setSelectedId(response.data.id);
    }, () => setShowForm(false));
  };
  const saveRequirement = (event: FormEvent) => {
    event.preventDefault();
    if (!selected || !requirementContent.trim()) return;
    const url = `/projects/${projectId}/deliverables/${selected.id}/requirements`;
    const payload = { content: requirementContent.trim(), required };
    void mutate(() => editingRequirement ? api.put(`${url}/${editingRequirement}`, payload) : api.post(url, payload),
      () => { setRequirementContent(""); setRequired(true); setEditingRequirement(null); });
  };
  const editRequirement = (r: DeliverableRequirement) => {
    setRequirementContent(r.content); setRequired(r.required); setEditingRequirement(r.id); setError("");
  };
  const createTask = async (payload: CreateTaskPayload) => {
    await api.post(`/projects/${projectId}/tasks`, payload);
    setTaskDraft(null);
    setNotice("업무를 추가했습니다. 담당자는 업무 보드에서 진행 상태를 변경할 수 있습니다.");
    await load().catch(() => {});
  };

  return <div className="min-h-screen bg-bb-bg">
    <Sidebar />
    <main className="ml-64 min-h-screen p-6 lg:p-10">
      <Link href={`/projects/${projectId}`} className="text-sm text-bb-text2 hover:underline">{projectName || "프로젝트"} / 프로젝트 홈</Link>
      <header className="my-6 flex flex-wrap items-start justify-between gap-4">
        <div><h1 className="text-2xl font-bold text-bb-text">제출물과 요구사항</h1>
          <p className="mt-2 text-sm text-bb-text2">무엇을 제출할지 정하고, 필요한 작업을 팀원에게 나누세요.</p></div>
        {canEdit && <button className={primary} onClick={() => beginEdit()}><Plus size={17} />제출물 추가</button>}
      </header>
      {loading ? <p role="status" className="py-12 text-bb-text2">제출물을 불러오는 중입니다…</p> : <>
        {loadError && <div role="alert" className="mb-5 rounded-lg border border-amber-500 p-4 text-bb-text">
          {loadError}<button className={`${secondary} ml-3`} onClick={() => { void load().catch(() => {}); }}>다시 불러오기</button>
        </div>}
        {error && <p role="alert" className="mb-4 rounded-lg bg-red-500/10 p-3 text-red-500">{error}</p>}
        {notice && <p role="status" className="mb-4 rounded-lg bg-teal-500/10 p-3 text-bb-text">{notice}</p>}
        {!loadError && deliverables.length === 0 ? <section className="rounded-xl border border-bb-border bg-bb-surface p-12 text-center">
          <ClipboardList size={40} className="mx-auto text-teal-500" /><h2 className="mt-5 text-lg font-semibold">첫 제출물을 등록하세요</h2>
          <p className="my-3 text-sm text-bb-text2">예: 중간발표 자료, 조사 보고서, 최종 작품 설명서</p>
          {canEdit && <button className={primary} onClick={() => beginEdit()}>제출물 등록하기</button>}
          {!canEdit && <p className="text-sm text-bb-text2">팀원이 제출물을 등록하면 여기서 확인할 수 있습니다.</p>}
        </section> : <div className="grid gap-6 xl:grid-cols-[300px_minmax(0,1fr)]">
          <aside className="space-y-3" aria-label="제출물 목록">
            {deliverables.map(d => {
              const count = tasks.filter(t => t.deliverableId === d.id).length;
              return <button key={d.id} disabled={busy} onClick={() => setSelectedId(d.id)} aria-pressed={selectedId === d.id}
                className={`w-full rounded-xl border p-4 text-left ${selectedId === d.id ? "border-teal-500 bg-teal-500/10" : "border-bb-border bg-bb-surface"}`}>
                <span className="block break-words font-semibold">{d.title}</span>
                <span className="mt-2 block text-sm text-bb-text2">제출일 {d.dueDate}</span>
                <span className="mt-2 block text-xs text-bb-text2">요구사항 {d.requirements.length}개 · 연결 업무 {count}개</span>
              </button>;
            })}
          </aside>
          {selected && <article className="min-w-0 space-y-6">
            <section className="rounded-xl border border-bb-border bg-bb-surface p-6">
              <div className="flex flex-wrap items-start justify-between gap-3"><h2 className="text-xl font-semibold break-words">{selected.title}</h2>
                {canEdit && <div className="flex gap-2"><button className={secondary} disabled={busy} onClick={() => beginEdit(selected)}><Pencil size={14} />수정</button>
                  <button className={secondary} disabled={busy} onClick={() => setConfirmDelete({ id: selected.id })}><Trash2 size={14} />삭제</button></div>}</div>
              <dl className="mt-5 grid gap-4 sm:grid-cols-3 text-sm">
                <div><dt className="text-bb-text2">제출 기한</dt><dd className="mt-1">{selected.dueDate}</dd></div>
                <div><dt className="text-bb-text2">제출 담당자</dt><dd className="mt-1">{selected.ownerName || "미지정"}</dd></div>
                <div><dt className="text-bb-text2">제출 경로</dt><dd className="mt-1 break-words">{selected.submissionMethod || "미설정"}</dd></div>
              </dl>
              {selected.description && <p className="mt-5 whitespace-pre-wrap break-words text-sm">{selected.description}</p>}
              <p className="mt-5 text-sm text-bb-text2">다음 단계: 요구사항을 적고 각 조건을 완성할 업무를 연결하세요.</p>
            </section>
            <section className="rounded-xl border border-bb-border bg-bb-surface p-6">
              <h3 className="font-semibold">요구사항 <span className="text-bb-text2">{selected.requirements.length}</span></h3>
              <p className="mt-1 text-xs text-bb-text2">과제 안내의 필수 내용과 형식을 적으세요. 업무 완료와 요구사항 검토는 구분합니다.</p>
              <ul className="my-4 divide-y divide-bb-border">
                {selected.requirements.map(r => <li key={r.id} className="py-4">
                  <div className="flex items-start gap-3"><span className={`shrink-0 text-xs ${r.required ? "text-teal-500" : "text-bb-text2"}`}>{r.required ? "필수" : "선택"}</span>
                    <p className="min-w-0 flex-1 whitespace-pre-wrap break-words text-sm">{r.content}</p></div>
                  <div className="mt-3 flex flex-wrap items-center gap-3 text-xs">
                    <span className="text-bb-text2">연결 업무 {linkedTasks.filter(t => t.requirementId === r.id).length}개</span>
                    {canEdit && <><button disabled={busy} className="text-teal-500 hover:underline" onClick={() => setTaskDraft({ deliverableId: selected.id, requirementId: r.id })}>이 요구사항의 업무 만들기</button>
                      <button disabled={busy} className="text-bb-text2 hover:underline" onClick={() => editRequirement(r)}>수정</button>
                      <button disabled={busy} className="text-bb-text2 hover:underline" onClick={() => setConfirmDelete({ id: selected.id, requirementId: r.id })}>삭제</button></>}
                  </div>
                </li>)}
              </ul>
              {selected.requirements.length === 0 && <p className="my-4 text-sm text-bb-text2">예: 조사 출처 포함, 발표자료 PDF 형식, 작품 설명서 첨부</p>}
              {canEdit && <form onSubmit={saveRequirement} className="space-y-3 border-t border-bb-border pt-4">
                <label htmlFor="requirement-content" className="block text-sm">{editingRequirement ? "요구사항 수정" : "요구사항 추가"}</label>
                <textarea id="requirement-content" className={field} rows={2} maxLength={1000} required value={requirementContent} onChange={e => setRequirementContent(e.target.value)} placeholder="예: 조사한 자료의 출처를 모두 표기" />
                <div className="flex flex-wrap items-center gap-3"><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={required} onChange={e => setRequired(e.target.checked)} />필수 조건</label>
                  <button className={primary} disabled={busy || !requirementContent.trim()}>{editingRequirement ? "수정 저장" : "요구사항 추가"}</button>
                  {editingRequirement && <button type="button" className={secondary} onClick={() => { setEditingRequirement(null); setRequirementContent(""); }}>취소</button>}
                </div>
              </form>}
            </section>
            <section className="rounded-xl border border-bb-border bg-bb-surface p-6">
              <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="font-semibold">연결 업무 {linkedTasks.length}개</h3>
                {canEdit && <button className={primary} onClick={() => setTaskDraft({ deliverableId: selected.id, requirementId: "" })}><Plus size={16} />업무 추가</button>}</div>
              <p className="mt-2 text-xs text-bb-text2">업무 완료 {linkedTasks.filter(t => t.status === "DONE").length}개 · 제출 준비 완료를 의미하지 않습니다.</p>
              <ul className="mt-4 divide-y divide-bb-border">{linkedTasks.map(t => <li key={t.id} className="py-4">
                <Link href={`/projects/${projectId}/board?task=${t.id}`} className="flex items-center gap-2 text-sm font-medium hover:text-teal-500">{t.title}<ArrowRight size={14} /></Link>
                <p className="mt-2 text-xs text-bb-text2">{{ TODO: "할 일", IN_PROGRESS: "진행 중", DONE: "업무 완료" }[t.status]} · {t.assignees.map(a => a.name).join(", ") || "담당자 미지정"} · {t.dueDate || "마감 미설정"}</p>
                <p className="mt-2 whitespace-pre-wrap break-words text-sm">완료 기준: {t.completionCriteria || "미설정 — 업무를 열어 작성하세요"}</p>
              </li>)}</ul>
              {linkedTasks.length === 0 && <p className="my-4 text-sm text-bb-text2">필요한 업무를 추가하거나 기존 업무를 연결하세요.</p>}
              {canEdit && unlinkedTasks.length > 0 && <form className="mt-4 flex flex-wrap gap-3" onSubmit={e => { e.preventDefault(); if (linkTaskId) void mutate(() => api.patch(`/projects/${projectId}/tasks/${linkTaskId}`, { deliverableId: selected.id }), () => setLinkTaskId("")); }}>
                <label className="min-w-0 flex-1 text-sm">기존 미연결 업무<select className={`${field} mt-2`} value={linkTaskId} onChange={e => setLinkTaskId(e.target.value)} required>
                  <option value="">연결할 업무 선택</option>{unlinkedTasks.map(t => <option key={t.id} value={t.id}>{t.title}</option>)}
                </select></label><button className={`${secondary} self-end`} disabled={busy || !linkTaskId}>이 제출물에 연결</button>
              </form>}
            </section>
          </article>}
        </div>}
        {unlinkedTasks.length > 0 && <p className="mt-6 text-sm text-bb-text2">제출물 미연결 업무 {unlinkedTasks.length}개가 있습니다. <Link className="text-teal-500 underline" href={`/projects/${projectId}/board`}>업무 보드에서 확인</Link></p>}
      </>}
    </main>
    {showForm && <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" role="dialog" aria-modal="true" aria-labelledby="deliverable-form-title">
      <form onSubmit={saveDeliverable} className="max-h-[90vh] w-full max-w-xl overflow-auto rounded-xl bg-bb-surface p-6 space-y-4">
        <div className="flex justify-between"><h2 id="deliverable-form-title" className="text-lg font-semibold">{editing ? "제출물 수정" : "제출물 등록"}</h2><button type="button" disabled={busy} aria-label="닫기" onClick={() => setShowForm(false)}><X size={20} /></button></div>
        {error && <p role="alert" className="text-sm text-red-500">{error}</p>}
        <label className="block text-sm">제출물 이름 *<input className={`${field} mt-2`} autoFocus required maxLength={255} value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} placeholder="예: 중간발표 자료" /></label>
        <label className="block text-sm">제출 기한 *<input type="date" className={`${field} mt-2`} required value={form.dueDate} onChange={e => setForm({ ...form, dueDate: e.target.value })} /></label>
        <label className="block text-sm">제출 담당자<select className={`${field} mt-2`} value={form.ownerId} onChange={e => setForm({ ...form, ownerId: e.target.value })}>
          <option value="">나중에 지정</option>
          {form.ownerId && !members.some(m => m.userId === form.ownerId && m.role !== "OBSERVER") && <option value={form.ownerId}>현재 담당자는 참여 팀원이 아닙니다. 다시 지정하세요.</option>}
          {members.filter(m => m.role !== "OBSERVER").map(m => <option key={m.userId} value={m.userId}>{m.name}</option>)}
        </select></label>
        <label className="block text-sm">제출 경로<input className={`${field} mt-2`} maxLength={500} value={form.submissionMethod} onChange={e => setForm({ ...form, submissionMethod: e.target.value })} placeholder="예: 학교 LMS 과제함" /></label>
        <label className="block text-sm">설명<textarea className={`${field} mt-2`} rows={3} maxLength={5000} value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></label>
        <button disabled={busy} className={`${primary} w-full`}>{busy ? "저장 중…" : "저장하고 요구사항 작성"}</button>
      </form>
    </div>}
    {confirmDelete && <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" role="dialog" aria-modal="true" aria-labelledby="delete-title">
      <section className="w-full max-w-md rounded-xl bg-bb-surface p-6"><h2 id="delete-title" className="text-lg font-semibold">{confirmDelete.requirementId ? "요구사항" : "제출물"} 삭제</h2>
        <p className="my-4 text-sm text-bb-text2">연결된 업무가 있으면 삭제할 수 없습니다. 삭제한 항목은 복구할 수 없습니다.</p>
        {error && <p role="alert" className="my-3 text-sm text-red-500">{error}</p>}
        <div className="flex gap-3"><button className={secondary} disabled={busy} onClick={() => setConfirmDelete(null)}>취소</button>
          <button className={primary} disabled={busy} onClick={() => { const target = confirmDelete; void mutate(() => api.delete(`/projects/${projectId}/deliverables/${target.id}${target.requirementId ? `/requirements/${target.requirementId}` : ""}`), () => setConfirmDelete(null)); }}>삭제</button></div>
      </section>
    </div>}
    {taskDraft && <TaskModal mode="create" task={null} members={members.filter(m => m.role !== "OBSERVER")} deliverables={deliverables}
      defaultDeliverableId={taskDraft.deliverableId} defaultRequirementId={taskDraft.requirementId}
      onClose={() => setTaskDraft(null)} onCreate={createTask} onUpdate={async () => {}} onDelete={async () => {}} />}
  </div>;
}
