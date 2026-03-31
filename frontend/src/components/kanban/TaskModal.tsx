"use client";

import { useState, useEffect } from "react";
import { Task, TaskPriority, CreateTaskPayload } from "@/types/task";
import { X, Trash2, Save, Plus } from "lucide-react";

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
  onClose: () => void;
  onCreate: (payload: CreateTaskPayload) => Promise<void>;
  onUpdate: (taskId: string, payload: Partial<CreateTaskPayload>) => Promise<void>;
  onDelete: (taskId: string) => Promise<void>;
}

// ── 우선순위 선택지 ───────────────────────────────────────────────────
const PRIORITY_OPTIONS: { value: TaskPriority; label: string }[] = [
  { value: "LOW",    label: "낮음" },
  { value: "MEDIUM", label: "중간" },
  { value: "HIGH",   label: "높음" },
  { value: "URGENT", label: "긴급" },
];

// ── 공용 입력 스타일 ──────────────────────────────────────────────────
const INPUT_CLS =
  "w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2.5 text-sm " +
  "text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 " +
  "focus:ring-1 focus:ring-indigo-500/30 transition-all";

// ── 태스크 생성/수정 모달 컴포넌트 ───────────────────────────────────
export default function TaskModal({
  mode,
  task,
  members,
  onClose,
  onCreate,
  onUpdate,
  onDelete,
}: TaskModalProps) {
  const [title, setTitle]       = useState(task?.title ?? "");
  const [description, setDescription] = useState(task?.description ?? "");
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? "MEDIUM");
  const [tag, setTag]           = useState(task?.tag ?? "");
  const [dueDate, setDueDate]   = useState(task?.dueDate ?? "");
  const [selectedAssignees, setSelectedAssignees] = useState<string[]>(
    task?.assignees.map((a) => a.userId) ?? []
  );
  const [submitting, setSubmitting]   = useState(false);
  const [error, setError]             = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);

  // task prop 변경 시 폼 필드 동기화
  useEffect(() => {
    if (!task) return;
    setTitle(task.title);
    setDescription(task.description ?? "");
    setPriority(task.priority);
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
      };
      if (mode === "create") {
        await onCreate(payload);
      } else if (task) {
        await onUpdate(task.id, payload);
      }
    } catch {
      setError("저장에 실패했습니다. 다시 시도해주세요.");
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
    } catch {
      setError("삭제에 실패했습니다.");
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* 배경 딤 레이어 */}
      <div
        className="absolute inset-0 bg-black/70 backdrop-blur-sm"
        onClick={onClose}
      />

      {/* 모달 패널 */}
      <div className="relative w-full max-w-lg bg-slate-800 border border-slate-700 rounded-2xl shadow-2xl shadow-black/50 overflow-hidden">

        {/* 헤더 */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-700">
          <h2 className="text-base font-semibold text-slate-100">
            {mode === "create" ? "새 태스크" : "태스크 수정"}
          </h2>
          <button
            onClick={onClose}
            className="text-slate-500 hover:text-slate-200 p-1.5 rounded-lg hover:bg-slate-700 transition-all"
            aria-label="닫기"
          >
            <X size={16} />
          </button>
        </div>

        {/* 폼 */}
        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4 max-h-[80vh] overflow-y-auto">

          {/* 에러 메시지 */}
          {error && (
            <div className="p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg text-sm text-rose-400">
              {error}
            </div>
          )}

          {/* 제목 */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">
              제목 <span className="text-rose-400">*</span>
            </label>
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="예: 로그인 API 연동"
              className={INPUT_CLS}
              maxLength={255}
            />
          </div>

          {/* 설명 */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-1.5">설명</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="태스크에 대한 상세 설명"
              rows={3}
              className={`${INPUT_CLS} resize-none`}
            />
          </div>

          {/* 우선순위 + 태그 */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-400 mb-1.5">우선순위</label>
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
              <label className="block text-xs font-medium text-slate-400 mb-1.5">
                태그 <span className="text-slate-600">(최대 30자)</span>
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
            <label className="block text-xs font-medium text-slate-400 mb-1.5">마감일</label>
            <input
              type="date"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              className={INPUT_CLS}
            />
          </div>

          {/* 담당자 선택 */}
          <div>
            <label className="block text-xs font-medium text-slate-400 mb-2">담당자</label>
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
                        : "border-slate-700 bg-slate-900 text-slate-400 hover:border-slate-600"
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
                <span className="text-xs text-slate-600">멤버가 없습니다</span>
              )}
            </div>
          </div>

          {/* 하단 액션 버튼 */}
          <div className="flex items-center justify-between pt-2">
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
                    className="text-xs px-3 py-1.5 text-slate-400 hover:text-slate-200 transition-all"
                  >
                    취소
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setConfirmDelete(true)}
                  className="flex items-center gap-1.5 text-xs text-slate-500 hover:text-rose-400 transition-colors"
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
                onClick={onClose}
                className="px-4 py-2 text-sm text-slate-400 hover:text-slate-200 transition-colors"
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
        </form>
      </div>
    </div>
  );
}
