"use client";

import { useState, useEffect, useLayoutEffect, useRef } from "react";
import { Task, TaskPriority, TaskStatus, CreateTaskPayload, KANBAN_COLUMNS } from "@/types/task";
import { X, Trash2, Save, Plus } from "lucide-react";
import SmartDateInput from "@/components/SmartDateInput";
import { Deliverable } from "@/types/deliverable";
import { apiError } from "@/lib/apiError";

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
}: TaskModalProps) {
  const [title, setTitle]       = useState(task?.title ?? "");
  const [deliverableId, setDeliverableId] = useState(task?.deliverableId ?? defaultDeliverableId);
  const [requirementId, setRequirementId] = useState(task?.requirementId ?? defaultRequirementId);
  const [completionCriteria, setCompletionCriteria] = useState(task?.completionCriteria ?? "");
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

  // 배경을 잘못 눌러 작성 중인 내용을 잃지 않도록, 처음 값과 달라졌는지 비교한다
  const initialForm = JSON.stringify([
    task?.title ?? "", task?.deliverableId ?? defaultDeliverableId, task?.requirementId ?? defaultRequirementId,
    task?.completionCriteria ?? "", task?.description ?? "", task?.priority ?? "MEDIUM", task?.tag ?? "",
    task?.dueDate ?? "", task?.assignees.map((a) => a.userId) ?? [], task?.status ?? defaultStatus,
  ]);
  const currentForm = JSON.stringify([
    title, deliverableId, requirementId, completionCriteria, description, priority, tag, dueDate, selectedAssignees, status,
  ]);
  const dirty = !readOnly && initialForm !== currentForm;

  // 닫기는 한 곳으로: 배경·닫기(X)·취소·Escape 모두 작성 중이면 먼저 묻는다
  const requestClose = () => (dirty ? setConfirmClose(true) : onClose());

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
      const payload: CreateTaskPayload = {
        title:       title.trim(),
        description: description.trim() || undefined,
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
    // 넓은 화면에서는 오른쪽 패널로 열어 보드·제출물이 옆에 보이게 하고(C-50 5번), 작은 화면에서는 전체 화면
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
        className="relative flex h-full w-full flex-col bg-bb-surface shadow-2xl shadow-black/50 outline-none md:w-[480px] md:max-w-[90vw] md:border-l md:border-bb-border"
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
            {readOnly ? "업무 보기" : mode === "create" ? "새 태스크" : "태스크 수정"}
          </h2>
          <button
            onClick={requestClose}
            className="text-bb-text2 hover:text-bb-text p-1.5 rounded-lg hover:bg-bb-surface2 transition-all"
            aria-label="닫기"
          >
            <X size={16} />
          </button>
        </div>

        {/* 폼 */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto px-6 pt-5 space-y-4">

          {/* 에러 메시지 */}
          {error && (
            <div className="p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg text-sm text-rose-400">
              {error}
            </div>
          )}

          {readOnly && (
            <p className="text-xs text-bb-text2">관찰자는 업무를 볼 수만 있습니다.</p>
          )}

          <fieldset disabled={readOnly} className="space-y-4 min-w-0 border-0 p-0 m-0">
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

          <div>
            <label htmlFor="task-deliverable" className="block text-xs font-medium text-bb-text2 mb-1.5">연결할 제출물</label>
            <select id="task-deliverable" className={INPUT_CLS} value={deliverableId}
              onChange={e => { setDeliverableId(e.target.value); setRequirementId(""); }}>
              <option value="">제출물 미연결</option>
              {deliverableId && !selectedDeliverable && <option value={deliverableId}>{task?.deliverableTitle ?? "현재 제출물"}</option>}
              {deliverables.map(d => <option key={d.id} value={d.id}>{d.title}</option>)}
            </select>
            <p className="text-xs text-bb-text2 mt-1">이 업무가 어떤 제출물을 완성하는지 연결하세요.</p>
          </div>
          {selectedDeliverable && <div>
            <label htmlFor="task-requirement" className="block text-xs font-medium text-bb-text2 mb-1.5">관련 요구사항 (선택)</label>
            <select id="task-requirement" className={INPUT_CLS} value={requirementId} onChange={e => setRequirementId(e.target.value)}>
              <option value="">제출물 전체 작업</option>
              {selectedDeliverable.requirements.map(r => <option key={r.id} value={r.id}>{r.required ? "[필수] " : ""}{r.content}</option>)}
            </select>
          </div>}
          <div>
            <label htmlFor="completion-criteria" className="block text-xs font-medium text-bb-text2 mb-1.5">완료 기준</label>
            <textarea id="completion-criteria" value={completionCriteria} onChange={e => setCompletionCriteria(e.target.value)}
              rows={2} maxLength={2000} className={`${INPUT_CLS} resize-none`} placeholder="예: 비교표에 공통 의견과 출처를 모두 포함" />
          </div>
          {/* 설명 */}
          <div>
            <label className="block text-xs font-medium text-bb-text2 mb-1.5">설명</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="태스크에 대한 상세 설명"
              rows={3}
              className={`${INPUT_CLS} resize-none`}
            />
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

          {/* 마감일 */}
          <div>
            <label className="block text-xs font-medium text-bb-text2 mb-1.5">마감일</label>
            <SmartDateInput
              value={dueDate}
              onChange={setDueDate}
              className="w-full"
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
          </div>

          </fieldset>

          {/* 하단 액션 버튼 */}
          {readOnly ? (
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
                disabled={submitting}
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
