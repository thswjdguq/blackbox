"use client";

import type { Task, TaskStatus } from "@/types/task";
import { TASK_STATUS_TEXT } from "@/types/task";

export default function TaskStatusControl({ task, readOnly, busy, onMove, onEdit }: {
  task: Task; readOnly?: boolean; busy?: boolean;
  onMove: (id: string, status: TaskStatus) => Promise<void>;
  onEdit: (task: Task) => void;
}) {
  const locked = readOnly || task.status === "DONE" || !!task.completionRequestedAt;
  return <div className="flex flex-wrap items-center gap-1.5" onClick={e => e.stopPropagation()} onKeyDown={e => e.stopPropagation()}>
    {locked ? <span className="rounded bg-bb-surface2 px-2 py-1 text-xs text-bb-text">{task.completionRequestedAt && task.status !== "DONE" ? "검토 대기" : TASK_STATUS_TEXT[task.status]}</span> :
      <select aria-label={`${task.title} 상태`} value={task.status} disabled={busy}
        onChange={e => { void onMove(task.id, e.target.value as TaskStatus); }}
        className="max-w-full rounded border border-bb-border bg-bb-surface px-2 py-1 text-xs text-bb-text disabled:opacity-50">
        <option value="TODO">할 일</option><option value="IN_PROGRESS">진행 중</option>
      </select>}
    {!readOnly && task.status !== "DONE" && <button type="button" disabled={busy} onClick={() => onEdit(task)}
      className="rounded border border-bb-border px-2 py-1 text-xs text-bb-primary hover:bg-bb-surface2 disabled:opacity-50">
      {task.completionRequestedAt ? "요청 확인" : "완료 요청"}
    </button>}
  </div>;
}
