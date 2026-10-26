"use client";

import { useState, useEffect, useLayoutEffect, useRef } from "react";
import { Task, TaskPriority, TaskStatus, CreateTaskPayload, KANBAN_COLUMNS } from "@/types/task";
import { X, Trash2, Save, Plus } from "lucide-react";
import SmartDateInput from "@/components/SmartDateInput";
import { Deliverable } from "@/types/deliverable";
import { apiError } from "@/lib/apiError";
import TaskResources from "./TaskResources";
import TaskCompletionPanel from "./TaskCompletionPanel";
import TaskSteps from "./TaskSteps";
import TaskDiscussion from "./TaskDiscussion";
import { DUE_BADGE, dueLabel, dueTone } from "@/lib/due";
import { useUnsavedGuard } from "@/hooks/useUnsavedGuard";

// ── 모달에서 사용할 멤버 타입 ─────────────────────────────────────────
interface Member {
  userId: string;
  name: string;
  email: string;
}

// ── Props 타입 ────────────────────────────────────────────────────────
interface TaskModalProps {
  mode: "create" | "edit";
  task: Task | null;
  members: Member[];
  defaultStatus?: TaskStatus;
  deliverables?: Deliverable[];
  defaultDeliverableId?: string;
  defaultRequirementId?: string;
  onClose: () => void;
  onCreate: (payload: CreateTaskPayload) => Promise<void>;
  onUpdate: (taskId: string, payload: Partial<CreateTaskPayload>) => Promise<void>;
  onDelete: (taskId: string) => Promise<void>;
  /** 관찰자: 내용만 보여주고 저장·삭제를 숨긴다 */
  readOnly?: boolean;
  availableTasks?: Task[];
  onWorkflowChanged?: (task: Task) => void;
  workOnly?: boolean;
  initialTab?: "info" | "resources";
  focusMessageId?: string | null;
  focusFileId?: string | null;
}

// ── 우선순위 선택지 ───────────────────────────────────────────────────
const PRIORITY_OPTIONS: { value: TaskPriority; label: string }[] = [
  { value: "LOW",    label: "낮음" },
  { value: "MEDIUM", label: "보통" },
  { value: "HIGH",   label: "높음" },
  { value: "URGENT", label: "긴급" },
];

// ── 공용 입력 스타일 ──────────────────────────────────────────────────
const INPUT_CLS =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2.5 text-sm " +
  "text-bb-text placeholder-slate-500 focus:outline-none focus:border-indigo-500 " +
  "focus:ring-1 focus:ring-indigo-500/30 transition-all";

// ── 태스크 생성/수정 모달 컴포넌트 ───────────────────────────────────
export default function TaskModal({
  mode,
  task,
  members,
  defaultStatus = "TODO",
  deliverables = [],
  defaultDeliverableId = "",
  defaultRequirementId = "",
  onClose,
  onCreate,
  onUpdate,
  onDelete,
  readOnly = false,
  availableTasks = [],
  onWorkflowChanged,
  workOnly = false,
  initialTab,
  focusMessageId,
  focusFileId,
}: TaskModalProps) {
  const [title, setTitle]       = useState(task?.title ?? "");
  const [deliverableId, setDeliverableId] = useState(task?.deliverableId ?? defaultDeliverableId);
  const [requirementId, setRequirementId] = useState(task?.requirementId ?? defaultRequirementId);
  const [completionCriteria, setCompletionCriteria] = useState(task?.completionCriteria ?? "");
  const [predecessorId, setPredecessorId] = useState(task?.predecessorId ?? "");
  const [attachmentRequired, setAttachmentRequired] = useState(task?.attachmentRequired ?? false);
  const locked = !!task && (task.status === "DONE" || !!task.completionRequestedAt);
  const selectedDeliverable = deliverables.find(d => d.id === deliverableId);
  const [description, setDescription] = useState(task?.description ?? "");
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? "MEDIUM");
  const [tag, setTag]           = useState(task?.tag ?? "");
  const [dueDate, setDueDate]   = useState(task?.dueDate ?? "");
  const [selectedAssignees, setSelectedAssignees] = useState<string[]>(
    task?.assignees.map((a) => a.userId) ?? []
  );
  const [status, setStatus]           = useState<TaskStatus>(task?.status ?? defaultStatus);
  const [submitting, setSubmitting]   = useState(false);
  const [error, setError]             = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [confirmClose, setConfirmClose] = useState(false);
  const [tab, setTab] = useState<"info" | "resources">(initialTab ?? (task ? "resources" : "info"));
  const [resourceBusy, setResourceBusy] = useState(false);
  const [filesRevision, setFilesRevision] = useState(0);
  const [discussionFiles, setDiscussionFiles] = useState<import("./TaskResources").TaskResource[]>([]);
  const [discussionDirty, setDiscussionDirty] = useState(false);
  const [completionDirty, setCompletionDirty] = useState(false);
  const resultsRef = useRef<HTMLDivElement>(null);
  const completionRef = useRef<HTMLDivElement>(null);
  const discussionRef = useRef<HTMLDivElement>(null);
  const [newStep, setNewStep] = useState("");

  // 배경을 잘못 눌러 작성 중인 내용을 잃지 않도록, 처음 값과 달라졌는지 비교한다
  const initialForm = JSON.stringify([
    task?.title ?? "", task?.deliverableId ?? defaultDeliverableId, task?.requirementId ?? defaultRequirementId,
    task?.completionCriteria ?? "", task?.description ?? "", task?.priority ?? "MEDIUM", task?.tag ?? "",
    task?.dueDate ?? "", task?.assignees.map((a) => a.userId) ?? [], task?.status ?? defaultStatus, task?.predecessorId ?? "", task?.attachmentRequired ?? false,
  ]);
  const currentForm = JSON.stringify([
    title, deliverableId, requirementId, completionCriteria, description, priority, tag, dueDate, selectedAssignees, status, predecessorId, attachmentRequired,
  ]);
  const dirty = !readOnly && (initialForm !== currentForm || !!newStep.trim());

  // 닫기는 한 곳으로: 배경·닫기(X)·취소·Escape 모두 작성 중이면 먼저 묻는다
  useUnsavedGuard(dirty || discussionDirty || completionDirty, "업무 창에 저장하지 않은 내용이 있습니다. 이 화면을 떠날까요?");
  const requestClose = () => { if (resourceBusy || submitting) return; dirty || discussionDirty || completionDirty ? setConfirmClose(true) : onClose(); };

  // 열리면 초점을 패널로 옮기고(그려지기 전에), 닫히면 열기 전 자리(업무 카드 등)로 돌려준다
  const panelRef = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const before = document.activeElement as HTMLElement | null;
    panelRef.current?.focus();
    return () => { before?.focus?.(); };
  }, []);
  // 초점이 패널 밖(누른 버튼이 사라진 뒤 등)이어도 Escape가 듣도록. 패널 안에서는 패널이 먼저 처리한다
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") requestClose(); };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  });

  // task prop 변경 시 폼 필드 동기화
  useEffect(() => {
    if (!task) return;
    setTitle(task.title);
    setDeliverableId(task.deliverableId ?? "");
    setRequirementId(task.requirementId ?? "");
    setCompletionCriteria(task.completionCriteria ?? "");
    setPredecessorId(task.predecessorId ?? ""); setAttachmentRequired(task.attachmentRequired ?? false);
    setDescription(task.description ?? "");
    setPriority(task.priority);
    setStatus(task.status);
    setTag(task.tag ?? "");
    setDueDate(task.dueDate ?? "");
    setSelectedAssignees(task.assignees.map((a) => a.userId));
  }, [task]);

  // 담당자 토글
  const toggleAssignee = (userId: string) => {
    setSelectedAssignees((prev) =>
      prev.includes(userId) ? prev.filter((id) => id !== userId) : [...prev, userId]
    );
  };

  // 저장 처리
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) { setError("제목을 입력해주세요"); return; }
    setSubmitting(true);
    setError("");
    try {
      const savedDescription = newStep.trim() ? `${description.trimEnd()}${description.trim() ? "\n" : ""}- [ ] ${newStep.trim()}` : description;
      const payload: CreateTaskPayload = {
        title:       title.trim(),
        description: savedDescription.trim(),
        priority,
        tag:         tag.trim() || undefined,
        dueDate:     dueDate || undefined,
        assigneeIds: selectedAssignees,
        status,
        deliverableId: deliverableId || undefined,
        requirementId: requirementId || undefined,
        clearDeliverable: mode === "edit" && !deliverableId,
        clearRequirement: mode === "edit" && !!deliverableId && !requirementId,
        completionCriteria: completionCriteria.trim(),
        predecessorId: predecessorId || undefined,
        clearPredecessor: mode === "edit" && !predecessorId,
        attachmentRequired,
      };
      if (mode === "create") {
        await onCreate(payload);
      } else if (task) {
        await onUpdate(task.id, payload);
      }
    } catch (err) {
      setError(apiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  // 삭제 처리
  const handleDelete = async () => {
    if (!task) return;
    setSubmitting(true);
    try {
      await onDelete(task.id);
    } catch (err) {
      setError(apiError(err, "삭제에 실패했습니다."));
      setSubmitting(false);
    }
  };

  return (
    // 넓은 화면에서는 오른쪽 패널로 열어 보드·최종 제출물이 옆에 보이게 하고(C-50 5번), 작은 화면에서는 전체 화면
    <div className="fixed inset-0 z-50 flex justify-end">
      {/* 배경 — 패널 옆 화면이 보이도록 옅게 */}
      <div
        className="absolute inset-0 bg-black/60 md:bg-black/30"
        onClick={requestClose}
      />

      {/* 패널 */}
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="task-panel-title"
        tabIndex={-1}
        onKeyDown={(e) => { if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); requestClose(); } }}
        className="relative flex h-full w-full flex-col bg-bb-surface shadow-2xl shadow-black/50 outline-none md:w-[640px] md:max-w-[90vw] md:border-l md:border-bb-border"
      >

        {confirmClose && (
          <div role="alert" className="flex items-center gap-2 px-6 py-3 bg-amber-500/10 border-b border-amber-500/30 text-xs text-amber-400">
            작성 중인 내용이 저장되지 않았습니다. 닫을까요?
            <button type="button" onClick={() => setConfirmClose(false)} className="ml-auto px-2 py-1 text-bb-text2 hover:text-bb-text">
              계속 작성
            </button>
            <button type="button" onClick={onClose} className="px-2 py-1 text-amber-300 border border-amber-500/40 rounded-md hover:bg-amber-500/10">
              닫기
            </button>
          </div>
        )}

        {/* 헤더 */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-bb-border">
          <h2 id="task-panel-title" className="text-base font-semibold text-bb-text">
            {mode === "create" ? "새 업무" : "업무 상세"}
          </h2>
          <button
            onClick={requestClose}
            className="text-bb-text2 hover:text-bb-text p-1.5 rounded-lg hover:bg-bb-surface2 transition-all"
            aria-label="닫기"
          >
            <X size={16} />
          </button>
        </div>

        {task && <div className="flex gap-2 px-6 py-3 border-b border-bb-border" role="tablist" aria-label="업무 상세">
          <button type="button" role="tab" aria-selected={tab === "resources"} disabled={submitting} onClick={() => setTab("resources")} className={`text-sm px-3 py-2 rounded ${tab === "resources" ? "bg-bb-primary text-white" : "text-bb-text2"}`}>수행 · 결과 확인</button>
          {!workOnly && <button type="button" role="tab" aria-selected={tab === "info"} disabled={resourceBusy} onClick={() => setTab("info")} className={`text-sm px-3 py-2 rounded ${tab === "info" ? "bg-bb-primary text-white" : "text-bb-text2"}`}>{readOnly ? "업무 정보" : "수정·삭제"}</button>}
        </div>}
        {task && tab === "resources" && <nav aria-label="업무 안에서 바로 이동" className="flex flex-wrap gap-2 border-b border-bb-border px-6 py-2">
          {[
            { label: "결과 파일", ref: resultsRef },
            { label: task.status === "DONE" ? "확인 기록" : task.completionRequestedAt ? "결과 확인하기" : "완료 확인 요청", ref: completionRef },
            { label: "대화·피드백", ref: discussionRef },
          ].map(({ label, ref }) => <button key={label} type="button" onClick={() => { ref.current?.scrollIntoView({ block: "start" }); ref.current?.focus({ preventScroll: true }); }} className="rounded-lg border border-bb-border px-3 py-2 text-xs text-bb-text hover:bg-bb-surface2">{label}</button>)}
        </nav>}
        {task && <div className={`${tab === "resources" ? "flex-1 overflow-y-auto" : "hidden"} px-6 py-5 space-y-6`}>
          <div><p className="text-xs text-bb-text2 mb-1">{task.deliverableTitle ?? "프로젝트 업무"}</p><h3 className="text-xl font-semibold break-words">{task.title}</h3><p className="mt-2 text-xs text-bb-text2">{task.assignees.map(a => a.name).join(", ") || "담당자 미지정"}{task.dueDate ? ` · ${task.dueDate}까지` : ""}</p>
            {task.dueDate && task.status !== "DONE" && <span className={`mt-2 inline-block rounded border px-2 py-0.5 text-xs font-semibold ${DUE_BADGE[dueTone(task.dueDate)]}`}>{dueLabel(task.dueDate)}</span>}
          </div>
          <ol aria-label="업무 진행 흐름" className="flex gap-2 text-xs">{["수행", "팀원 확인", "완료"].map((label, index) => <li key={label} className={`flex-1 rounded-lg px-2 py-2 text-center ${(task.status === "DONE" ? 2 : task.completionRequestedAt ? 1 : 0) === index ? "bg-bb-primary text-white" : "bg-bb-bg text-bb-text2"}`}>{index + 1}. {label}</li>)}</ol>
          {dirty && <p className="text-sm text-amber-700">수정한 업무 설정을 먼저 저장하세요. <button type="button" onClick={() => setTab("info")} className="underline">설정으로 이동</button></p>}
          <section className="rounded-xl border border-bb-border p-4"><h3 className="text-sm font-semibold mb-2">완료 기준 (선택)</h3><p className="text-sm whitespace-pre-wrap">{task.completionCriteria || "작성된 기준이 없습니다. 결과를 등록하고 팀원에게 확인을 요청할 수 있습니다."}</p></section>
          <TaskSteps task={task} disabled={readOnly || dirty || locked || resourceBusy} onChanged={updated => onWorkflowChanged?.(updated)} onBusy={setResourceBusy} />
          <div ref={resultsRef} tabIndex={-1} role="group" aria-label="결과 파일" className="scroll-mt-4">
          <TaskResources task={task} focusFileId={focusFileId} readOnly={readOnly || dirty || resourceBusy} uploadLocked={locked} onBusy={setResourceBusy} onFilesChanged={setDiscussionFiles} onUploaded={() => setFilesRevision(v => v + 1)} />
          </div>
          <div ref={completionRef} tabIndex={-1} role="group" aria-label="결과 확인" className="scroll-mt-4">
          <TaskCompletionPanel task={task} disabled={readOnly || dirty || submitting || resourceBusy} refreshKey={filesRevision}
            onSnapshot={updated => { if (!dirty && !resourceBusy && !submitting && JSON.stringify(updated) !== JSON.stringify(task)) onWorkflowChanged?.(updated); }}
            onChanged={updated => onWorkflowChanged?.(updated)} onBusy={setResourceBusy} onDraftChange={setCompletionDirty} />
          </div>
          <div ref={discussionRef} tabIndex={-1} role="group" aria-label="업무 대화와 피드백" className="scroll-mt-4">
          <TaskDiscussion task={task} members={members} files={discussionFiles} readOnly={readOnly} focusMessageId={focusMessageId} onDraftChange={setDiscussionDirty} onBusy={setResourceBusy} />
          </div>
        </div>}
        {/* 자료·검토 영역은 폼 밖에 둬 검토 입력 폼과 업무 저장 폼이 중첩되지 않게 한다. */}
        <form onSubmit={handleSubmit} className={`${tab === "resources" ? "hidden" : "flex-1 overflow-y-auto"} px-6 pt-5 space-y-4`}>

          {/* 에러 메시지 */}
          {error && (
            <div className="p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg text-sm text-rose-400">
              {error}
            </div>
          )}

          {!readOnly && locked && <p role="status" className="text-sm text-bb-text2">{task?.completionRequestedAt ? "완료 확인 요청 중입니다. 수행 · 결과 확인에서 요청을 취소한 뒤 수정·삭제할 수 있습니다." : "완료된 업무입니다. 수행 · 결과 확인에서 업무를 재개한 뒤 수정·삭제할 수 있습니다."}</p>}
          {!readOnly && !locked && mode === "edit" && <p className="text-xs text-bb-text2">제목·담당자·마감 등을 수정하고 저장하세요. 더 이상 진행하지 않을 업무는 아래에서 삭제할 수 있습니다. 삭제는 만든 사람과 팀장만 가능합니다.</p>}
          {readOnly && (
            <p className="text-xs text-bb-text2">관찰자는 업무를 볼 수만 있습니다.</p>
          )}

          <fieldset disabled={readOnly || locked || resourceBusy} className="space-y-4 min-w-0 border-0 p-0 m-0">
          {/* 제목 */}
          <div>
            <label className="block text-xs font-medium text-bb-text2 mb-1.5">
              제목 <span className="text-rose-400">*</span>
            </label>
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="예: 인터뷰 결과 5건을 비교표로 정리"
              className={INPUT_CLS}
              maxLength={255}
            />
          </div>

          {/* 담당자 선택 */}
          <div>
            <label className="block text-xs font-medium text-bb-text2 mb-2">담당자</label>
            <div className="flex flex-wrap gap-2">
              {members.map((m) => {
                const selected = selectedAssignees.includes(m.userId);
                // 이름 기반 결정적 색상 생성
                const hue = m.name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360;
                return (
                  <button
                    key={m.userId}
                    type="button"
                    onClick={() => toggleAssignee(m.userId)}
                    className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium transition-all border ${
                      selected
                        ? "border-indigo-500 bg-indigo-500/15 text-indigo-300"
                        : "border-bb-border bg-bb-bg text-bb-text2 hover:border-bb-text2/50"
                    }`}
                  >
                    <div
                      style={{ background: `hsl(${hue} 55% 45%)` }}
                      className="w-4 h-4 rounded-full flex items-center justify-center text-[8px] font-bold text-white"
                    >
                      {m.name.slice(0, 2).toUpperCase()}
                    </div>
                    {m.name}
                    {selected && (
                      <span className="w-3 h-3 rounded-full bg-indigo-500 flex items-center justify-center text-[8px] text-white">
                        ✓
                      </span>
                    )}
                  </button>
                );
              })}
              {members.length === 0 && (
                <span className="text-xs text-bb-text2/70">멤버가 없습니다</span>
              )}
            </div>
            {members.length > 1 && members.every(m => selectedAssignees.includes(m.userId)) && <p role="status" className="mt-2 text-xs text-amber-700">팀원 전원을 담당자로 지정하면 결과를 확인할 다른 팀원이 없습니다. 업무를 나누고 확인할 팀원을 남겨주세요.</p>}
          </div>

          {/* 마감일 */}
          <div>
            <label className="block text-xs font-medium text-bb-text2 mb-1.5">마감일</label>
            <SmartDateInput
              value={dueDate}
              onChange={setDueDate}
              className="w-full"
            />
          </div>

          <div>
            <label htmlFor="completion-criteria" className="block text-xs font-medium text-bb-text2 mb-1.5">어떤 결과면 끝인가요? <span className="font-normal">(선택)</span></label>
            <textarea id="completion-criteria" value={completionCriteria} onChange={e => setCompletionCriteria(e.target.value)}
              rows={2} maxLength={2000} className={`${INPUT_CLS} resize-none`} placeholder="예: 비교표에 공통 의견과 출처를 모두 포함" />
            <p className="mt-1 text-xs text-bb-text2">팀원이 결과를 확인할 때 참고할 메모입니다. 비워 두어도 확인 요청과 피드백을 주고받을 수 있습니다.</p>
          </div>
          <details className="rounded-xl border border-bb-border p-4">
            <summary className="text-sm font-medium text-bb-text">추가 설정 (선택)
              <span className="ml-2 text-xs font-normal text-bb-text2">{[selectedDeliverable?.title ?? (deliverableId ? task?.deliverableTitle : null), attachmentRequired ? "파일 첨부 필수" : null, predecessorId ? "선행 업무 있음" : null].filter(Boolean).join(" · ") || "설명 · 최종 제출물 연결 · 우선순위 등"}</span>
            </summary>
            <div className="mt-4 space-y-4">
          {/* 설명 */}
          <div>
            <label className="block text-xs font-medium text-bb-text2 mb-1.5">설명</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="작업 설명과 참고할 내용을 적어주세요"
              rows={3}
              className={`${INPUT_CLS} resize-none`}
            />
            <div className="flex gap-2 mt-2"><input aria-label="작은 수행 단계" value={newStep} onChange={e => setNewStep(e.target.value)} maxLength={200} className={INPUT_CLS} placeholder="작은 단계 추가 · 예: 자료 3개 수집" />
              <button type="button" disabled={!newStep.trim()} onClick={() => { setDescription(value => `${value.trimEnd()}${value.trim() ? "\n" : ""}- [ ] ${newStep.trim()}`); setNewStep(""); }} className="shrink-0 px-3 border border-bb-border rounded-lg text-sm disabled:opacity-40">추가</button></div>
            <p className="text-xs text-bb-text2 mt-1">작은 준비 사항은 체크리스트로 묶고, 결과 전체를 한 번에 확인받으세요.</p>
          </div>

          <div>
            <label htmlFor="task-deliverable" className="block text-xs font-medium text-bb-text2 mb-1.5">연결할 최종 제출물</label>
            <select id="task-deliverable" className={INPUT_CLS} value={deliverableId}
              onChange={e => { setDeliverableId(e.target.value); setRequirementId(""); }}>
              <option value="">최종 제출물 미연결</option>
              {deliverableId && !selectedDeliverable && <option value={deliverableId}>{task?.deliverableTitle ?? "현재 최종 제출물"}</option>}
              {deliverables.map(d => <option key={d.id} value={d.id}>{d.title}</option>)}
            </select>
            <p className="text-xs text-bb-text2 mt-1">이 업무가 어떤 최종 제출물을 완성하는지 연결하세요.</p>
          </div>
          {selectedDeliverable && <div>
            <label htmlFor="task-requirement" className="block text-xs font-medium text-bb-text2 mb-1.5">관련 요구사항 (선택)</label>
            <select id="task-requirement" className={INPUT_CLS} value={requirementId} onChange={e => setRequirementId(e.target.value)}>
              <option value="">최종 제출물 전체 작업</option>
              {selectedDeliverable.requirements.map(r => <option key={r.id} value={r.id}>{r.required ? "[필수] " : ""}{r.content}</option>)}
            </select>
          </div>}
          <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={attachmentRequired} onChange={e => setAttachmentRequired(e.target.checked)} />완료 요청 전에 이 업무에 파일을 첨부해야 합니다.</label>
          <div><label htmlFor="task-predecessor" className="block text-xs text-bb-text2 mb-1.5">먼저 승인 완료되어야 하는 업무</label>
            <select id="task-predecessor" value={predecessorId} onChange={e => setPredecessorId(e.target.value)} className={INPUT_CLS}>
              <option value="">선행 업무 없음 · 병렬 진행 가능</option>
              {availableTasks.filter(t => t.id !== task?.id).map(t => <option key={t.id} value={t.id}>{t.title}{t.completionApprovedAt ? " (승인 완료)" : ""}</option>)}
            </select><p className="text-xs text-bb-text2 mt-1">선행 업무의 결과 확인이 끝나야 이 업무를 시작할 수 있습니다.</p>
          </div>
          {/* 우선순위 + 태그 */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-bb-text2 mb-1.5">우선순위</label>
              <select
                value={priority}
                onChange={(e) => setPriority(e.target.value as TaskPriority)}
                className={INPUT_CLS}
              >
                {PRIORITY_OPTIONS.map((p) => (
                  <option key={p.value} value={p.value}>{p.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-bb-text2 mb-1.5">
                태그 <span className="text-bb-text2/70">(최대 30자)</span>
              </label>
              <input
                value={tag}
                onChange={(e) => setTag(e.target.value)}
                placeholder="예: FE, 백엔드"
                className={INPUT_CLS}
                maxLength={30}
              />
            </div>
          </div>

          {/* 상태 선택 (편집 모드) */}
          {mode === "edit" && (
            <div>
              <label className="block text-xs font-medium text-bb-text2 mb-1.5">상태</label>
              <div className="flex gap-2">
                {KANBAN_COLUMNS.map((col) => {
                  const isActive = status === col.id;
                  const colorMap: Record<string, string> = {
                    TODO:        isActive ? "border-slate-400 bg-bb-surface2 text-bb-text" : "border-bb-border text-bb-text2 hover:border-bb-text2/50 hover:text-bb-text",
                    IN_PROGRESS: isActive ? "border-indigo-500 bg-indigo-500/20 text-indigo-300" : "border-bb-border text-bb-text2 hover:border-indigo-500/40 hover:text-indigo-400",
                    DONE:        isActive ? "border-teal-500 bg-teal-500/20 text-teal-300" : "border-bb-border text-bb-text2 hover:border-teal-500/40 hover:text-teal-400",
                  };
                  return (
                    <button
                      key={col.id}
                      type="button"
                      disabled={col.id === "DONE"}
                      onClick={() => setStatus(col.id)}
                      className={`flex-1 py-2 px-3 rounded-lg text-xs font-medium border transition-all ${colorMap[col.id]}`}
                    >
                      {col.label}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

            </div>
          </details>

          </fieldset>
          {dirty && task && <p className="text-xs text-amber-700">업무 정보와 완료 조건을 먼저 저장한 뒤 완료를 요청하세요.</p>}

          {/* 하단 액션 버튼 */}
          {readOnly || locked ? (
            <div className="sticky bottom-0 -mx-6 flex justify-end border-t border-bb-border bg-bb-surface px-6 py-3">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 text-sm text-bb-text border border-bb-border rounded-lg hover:bg-bb-surface2 transition-colors"
              >
                닫기
              </button>
            </div>
          ) : (
          <div className="sticky bottom-0 -mx-6 flex items-center justify-between border-t border-bb-border bg-bb-surface px-6 py-3">
            {/* 삭제 버튼 (편집 모드만) */}
            {mode === "edit" ? (
              confirmDelete ? (
                <div className="flex items-center gap-2">
                  <span className="text-xs text-rose-400">정말 삭제할까요?</span>
                  <button
                    type="button"
                    onClick={handleDelete}
                    disabled={submitting}
                    className="text-xs px-3 py-1.5 bg-rose-500/20 text-rose-400 border border-rose-500/30 rounded-lg hover:bg-rose-500/30 transition-all"
                  >
                    삭제
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirmDelete(false)}
                    className="text-xs px-3 py-1.5 text-bb-text2 hover:text-bb-text transition-all"
                  >
                    취소
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setConfirmDelete(true)}
                  className="flex items-center gap-1.5 text-xs text-bb-text2 hover:text-rose-400 transition-colors"
                >
                  <Trash2 size={13} />
                  삭제
                </button>
              )
            ) : (
              <div />
            )}

            {/* 취소 + 저장 */}
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={requestClose}
                className="px-4 py-2 text-sm text-bb-text2 hover:text-bb-text transition-colors"
              >
                취소
              </button>
              <button
                type="submit"
                disabled={submitting || resourceBusy || locked}
                className="flex items-center gap-2 px-4 py-2.5 bg-indigo-600 hover:bg-indigo-500
                           disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm
                           font-medium rounded-lg transition-all"
              >
                {submitting ? (
                  <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                ) : mode === "create" ? (
                  <><Plus size={14} /> 추가</>
                ) : (
                  <><Save size={14} /> 저장</>
                )}
              </button>
            </div>
          </div>
          )}
        </form>
      </div>
    </div>
  );
}
