"use client";

import { useState, useCallback, useRef, useEffect, useMemo } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import AgentButton from "@/components/agent/AgentButton";
import { useAiOrigins } from "@/components/agent/AiBadge";
import { useAgentStore } from "@/lib/store/agentStore";
import { Deliverable } from "@/types/deliverable";
import { apiError, PartialSaveError } from "@/lib/apiError";
import {
  Task,
  TaskStatus,
  TaskPriority,
  ScoreMap,
  KANBAN_COLUMNS,
  CreateTaskPayload,
} from "@/types/task";
import KanbanColumn from "./KanbanColumn";
import { defaultTaskView, restoreTaskView, inTaskScope, type TaskView, type TaskScope } from "@/lib/taskView";
import TaskModal from "./TaskModal";
import TaskListView from "./TaskListView";
import { Columns3, List } from "lucide-react";
import api from "@/lib/api";

export interface KanbanFilter {
  assigneeId: string | null;
  priority: TaskPriority | null;
  tag: string;
}

interface KanbanBoardProps {
  projectId: string;
  userId: string;
  role: string;
  onClearAssignee: () => void;
  onResetFilters: () => void;
  initialTasks: Task[];
  members: { userId: string; name: string; email: string }[];
  scoreMap: ScoreMap;
  filter?: KanbanFilter;
  onTasksChange?: (tasks: Task[]) => void;
  /** 관찰자: 서버가 업무 쓰기를 거절하므로 추가·상태 변경·수정을 숨긴다 */
  readOnly?: boolean;
}

export default function KanbanBoard({
  projectId,
  userId,
  role,
  onClearAssignee,
  onResetFilters,
  initialTasks,
  members,
  scoreMap,
  filter,
  onTasksChange,
  readOnly = false,
}: KanbanBoardProps) {
  const [tasks, setTasks] = useState<Task[]>(initialTasks);
  const searchParams = useSearchParams();
  const taskParam = searchParams.get("task");
  const deliverableParam = searchParams.get("deliverable");
  const aiOrigins = useAiOrigins(projectId);
  const recordsVersion = useAgentStore(s => s.recordsVersion);
  const mutationLock = useRef(false);
  const tasksRef = useRef(tasks);
  tasksRef.current = tasks;
  const [updating, setUpdating] = useState(false);

  // Modal state
  const [modalMode, setModalMode] = useState<"create" | "edit" | null>(null);
  const [editingTask, setEditingTask] = useState<Task | null>(null);
  const [createStatus, setCreateStatus] = useState<TaskStatus>("TODO");
  const [deliverables, setDeliverables] = useState<Deliverable[]>([]);
  const [deliveryFilter, setDeliveryFilter] = useState("");
  const [boardError, setBoardError] = useState("");
  const storageKey = `task-view-v2:${userId}:${projectId}:${role}`;
  const [preferences, setPreferences] = useState<TaskView>(() => defaultTaskView(role));
  const [ready, setReady] = useState(false);
  const [scopeOverride, setScopeOverride] = useState<TaskScope | null>(null);
  const [reviewIds, setReviewIds] = useState<Set<string>>(new Set());
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewError, setReviewError] = useState(false);
  const [reviewRetry, setReviewRetry] = useState(0);
  useEffect(() => {
    try { setPreferences(restoreTaskView(localStorage.getItem(storageKey), role)); }
    catch { setPreferences(defaultTaskView(role)); }
    setReady(true);
  }, [storageKey, role]);
  const updatePreferences = (patch: Partial<TaskView>) => {
    const next = { ...preferences, ...patch };
    setPreferences(next);
    try { localStorage.setItem(storageKey, JSON.stringify(next)); } catch { /* 이번 화면에서는 적용 */ }
  };
  const view = preferences.view;
  // 제출물에서 들어온 링크와 명시적인 담당자 필터를 개인 기본 보기보다 우선한다.
  const scope = filter?.assigneeId ? "all" : scopeOverride ?? (deliverableParam ? "all" : preferences.scope);
  const changeScope = (next: TaskScope) => { onClearAssignee(); setScopeOverride(next); updatePreferences({ scope: next }); };
  useEffect(() => { setScopeOverride(null); }, [deliverableParam]);
  useEffect(() => {
    if (readOnly) { setReviewIds(new Set()); return; }
    let active = true;
    const pending = tasks.filter(t => t.status !== "DONE" && t.completionRequestedAt);
    setReviewLoading(true); setReviewError(false);
    // 업무가 많아도 검토 권한 조회를 한꺼번에 발송하지 않는다.
    (async () => {
      const results: PromiseSettledResult<string | null>[] = [];
      for (let i = 0; i < pending.length && active; i += 4) {
        results.push(...await Promise.allSettled(pending.slice(i, i + 4).map(t => api.get<{ canReview: boolean }>(`/projects/${projectId}/tasks/${t.id}/completion`)
          .then(({data}) => data.canReview ? t.id : null))));
      }
      return results;
    })().then(results => {
      if (!active) return;
      setReviewIds(new Set(results.flatMap(r => r.status === "fulfilled" && r.value ? [r.value] : [])));
      setReviewError(results.some(r => r.status === "rejected")); setReviewLoading(false);
    });
    return () => { active = false; };
  }, [tasks, projectId, readOnly, reviewRetry]);
  const openedTask = useRef<string | null>(null);

  // 제출물 상세의 "보드에서 이 제출물 업무 보기"가 ?deliverable=<id> 로 연다
  useEffect(() => {
    setDeliveryFilter(deliverableParam ?? "");
  }, [deliverableParam]);

  const [deliverablesError, setDeliverablesError] = useState("");

  const loadDeliverables = useCallback(() => {
    setDeliverablesError("");
    api.get<Deliverable[]>(`/projects/${projectId}/deliverables`)
      .then(({ data }) => setDeliverables(data))
      .catch((err) => setDeliverablesError(apiError(err, "제출물 목록을 불러오지 못했습니다.")));
  }, [projectId]);

  useEffect(() => {
    loadDeliverables();
  }, [loadDeliverables, recordsVersion]);
  useEffect(() => {
    if (recordsVersion === 0) return;
    let active = true;
    api.get<Task[]>(`/projects/${projectId}/tasks`).then(({data}) => {
      if (!active) return;
      setTasks(data); onTasksChange?.(data);
    }).catch(e => { if(active) setBoardError(apiError(e,"채택한 업무 목록을 다시 읽지 못했습니다.")); });
    return () => {active=false;};
  }, [projectId,recordsVersion,onTasksChange]);

  useEffect(() => {
    setTasks(initialTasks);
  }, [initialTasks]);
  useEffect(() => {
    const id = taskParam;
    if (!id) {openedTask.current=null; return;}
    const task = initialTasks.find((item) => item.id === id);
    if (task && openedTask.current !== id) {
      openedTask.current = id;
      setEditingTask(task);
      setModalMode("edit");
    }
  }, [initialTasks,taskParam]);

  // ── Column grouping (filter 적용) ────────────────────────────────────

  // 보드와 목록이 같은 필터를 쓴다
  const visibleTasks = useMemo(() => {
    const q = filter?.tag?.toLowerCase();
    return tasks.filter((t) =>
      (preferences.showDone || t.status !== "DONE") &&
      inTaskScope(t, scope, userId, reviewIds) &&
      (!deliveryFilter || (deliveryFilter === "unlinked" ? !t.deliverableId : t.deliverableId === deliveryFilter)) &&
      (!filter?.assigneeId || t.assignees.some((a) => a.userId === filter.assigneeId)) &&
      (!filter?.priority || t.priority === filter.priority) &&
      (!q || !!t.tag?.toLowerCase().includes(q))
    );
  }, [tasks, filter, deliveryFilter, preferences.showDone, scope, userId, reviewIds]);

  const tasksByStatus = useCallback(
    (status: TaskStatus) => visibleTasks.filter((t) => t.status === status),
    [visibleTasks]
  );

  // ── Modal handlers ─────────────────────────────────────────────────────

  const openCreate = (status: TaskStatus = "TODO") => {
    setCreateStatus(status);
    setEditingTask(null);
    setModalMode("create");
  };

  const openEdit = (task: Task) => {
    setEditingTask(task);
    setModalMode("edit");
  };

  const closeModal = () => {
    setModalMode(null);
    setEditingTask(null);
  };

  const handleCreate = async (payload: CreateTaskPayload) => {
    const { data } = await api.post<Task>(`/projects/${projectId}/tasks`, payload);
    setTasks((prev) => [data, ...prev]);
    onTasksChange?.([data, ...tasks]);
    closeModal();
  };

  const handleUpdate = async (taskId: string, payload: Partial<CreateTaskPayload>) => {
    const original = tasks.find((t) => t.id === taskId);
    const reflectSavedTask = (saved: Task) => {
      setTasks(prev => prev.map(t => t.id === taskId ? saved : t));
      onTasksChange?.(tasks.map(t => t.id === taskId ? saved : t));
    };

    // 1) 기본 필드 업데이트 (title/description/priority/tag/dueDate)
    const { data } = await api.patch<Task>(`/projects/${projectId}/tasks/${taskId}`, payload);
    let finalTask = data;
    reflectSavedTask(data);

    // 2·3은 1이 이미 저장된 뒤라, 실패하면 어디까지 저장됐는지 알려준다
    // 2) 담당자 변경 — 별도 PUT 엔드포인트 사용 (UpdateTaskRequest에 assigneeIds 없음)
    if (payload.assigneeIds !== undefined) {
      try {
        const { data: assigneeData } = await api.put<Task>(
          `/projects/${projectId}/tasks/${taskId}/assignees`,
          { assigneeIds: payload.assigneeIds }
        );
        finalTask = assigneeData;
        reflectSavedTask(assigneeData);
      } catch (err) {
        throw new PartialSaveError(
          `업무 내용은 저장했지만 담당자 변경에 실패했습니다. ${apiError(err, "다시 시도해주세요.")}`
        );
      }
    }

    // 3) 상태 변경 — 별도 PATCH 엔드포인트 사용 (UpdateTaskRequest에 status 없음)
    if (payload.status && original?.status !== payload.status) {
      try {
        const { data: statusData } = await api.patch<Task>(
          `/projects/${projectId}/tasks/${taskId}/status`,
          { status: payload.status }
        );
        finalTask = statusData;
      } catch (err) {
        throw new PartialSaveError(
          `${payload.assigneeIds !== undefined ? "업무 내용과 담당자는" : "업무 내용은"} 저장했지만 상태 변경에 실패했습니다. ${apiError(err, "다시 시도해주세요.")}`
        );
      }
    }

    setTasks((prev) => prev.map((t) => (t.id === taskId ? finalTask : t)));
    onTasksChange?.(tasks.map((t) => t.id === taskId ? finalTask : t));
    closeModal();
  };

  const handleDelete = async (taskId: string) => {
    await api.delete(`/projects/${projectId}/tasks/${taskId}`);
    setTasks((prev) => prev.filter((t) => t.id !== taskId));
    onTasksChange?.(tasks.filter((t) => t.id !== taskId));
    closeModal();
  };

  const handleMoveTask = async (taskId: string, newStatus: TaskStatus) => {
    const original = tasksRef.current.find(t => t.id === taskId);
    if (readOnly || mutationLock.current || !original || original.status === "DONE" || original.completionRequestedAt || original.status === newStatus) return;
    if (newStatus === "DONE") { openEdit(original); return; }
    mutationLock.current = true; setUpdating(true); setBoardError("");
    try {
      const { data } = await api.patch<Task>(`/projects/${projectId}/tasks/${taskId}/status`, { status: newStatus });
      const next = tasksRef.current.map(t => t.id === taskId ? data : t);
      tasksRef.current = next; setTasks(next); onTasksChange?.(next);
    } catch (err) { setBoardError(apiError(err, "업무 상태를 저장하지 못했습니다. 다시 시도해주세요.")); }
    finally { mutationLock.current = false; setUpdating(false); }
  };

  // ── Render ─────────────────────────────────────────────────────────────

  if (!ready) return <p className="py-8 text-sm text-bb-text2">업무 보기를 불러오는 중입니다.</p>;

  return (
    <>
      <div className="flex flex-wrap items-center gap-3 mb-4">
        <select aria-label="제출물로 업무 필터" value={deliveryFilter} onChange={(e) => setDeliveryFilter(e.target.value)} className="max-w-full rounded-lg border border-bb-border bg-bb-surface px-3 py-2 text-sm">
          <option value="">전체 제출물</option>
          <option value="unlinked">제출물 미연결</option>
          {deliverables.map((item) => <option key={item.id} value={item.id}>{item.title}</option>)}
        </select>
        <Link href={`/projects/${projectId}/deliverables`} className="whitespace-nowrap text-sm text-indigo-500">제출물·요구사항 관리 →</Link>
        <AgentButton projectId={projectId} skill="STATUS_BRIEF" />
        <div role="group" aria-label="업무 보기 방식" className="ml-auto flex rounded-lg border border-bb-border bg-bb-surface p-0.5 text-sm">
          {([["list", "목록", List], ["board", "칸반", Columns3]] as const).map(([v, label, Icon]) => (
            <button
              key={v}
              onClick={() => updatePreferences({ view: v })}
              aria-pressed={view === v}
              className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 ${view === v ? "bg-bb-surface2 text-bb-text font-medium" : "text-bb-text2 hover:text-bb-text"}`}
            >
              <Icon size={14} />
              {label}
            </button>
          ))}
        </div>
      </div>
      <div className="mb-4 flex flex-wrap items-center gap-2" role="group" aria-label="업무 범위">
        {([...(readOnly ? [] : [["mine", "내 업무"], ["review", "내가 검토할 업무"]]), ["all", "전체 업무"], ["unassigned", "미배정"]] as [TaskScope, string][]).map(([key, label]) => {
          const count = tasks.filter(t => (preferences.showDone || t.status !== "DONE") && inTaskScope(t, key, userId, reviewIds)).length;
          return <button key={key} aria-pressed={scope === key} onClick={() => changeScope(key)} className={`rounded-lg border px-3 py-2 text-sm ${scope === key ? "border-bb-primary bg-bb-primary/10 text-bb-primary" : "border-bb-border text-bb-text2"}`}>{label} {key === "review" && reviewError ? "확인 실패" : key === "review" && reviewLoading ? "확인 중" : count}</button>;
        })}
        <label className="ml-auto flex items-center gap-2 text-sm text-bb-text2"><input type="checkbox" checked={preferences.showDone} onChange={e => updatePreferences({ showDone: e.target.checked })} />완료 업무 포함</label>
      </div>
      <p className="mb-3 text-xs text-bb-text2">프로젝트 {tasks.length}개 중 {visibleTasks.length}개 표시 · 범위 버튼의 건수는 프로젝트 전체 기준</p>
      {reviewError && <p role="alert" className="mb-3 text-sm text-red-500">검토 권한 일부를 확인하지 못했습니다. <button onClick={() => setReviewRetry(n => n + 1)} className="underline">다시 확인</button></p>}
      {scope === "review" && reviewLoading && <p role="status" className="mb-3 text-sm text-bb-text2">검토할 수 있는 업무를 확인하고 있습니다.</p>}
      {visibleTasks.length === 0 && tasks.length > 0 && !(scope === "review" && (reviewLoading || reviewError)) && <p className="mb-3 text-sm text-bb-text2">현재 조건에 맞는 업무가 없습니다. <button className="underline" onClick={() => { onResetFilters(); setScopeOverride("all"); setDeliveryFilter(""); updatePreferences({ scope: "all", showDone: true }); }}>모든 필터 해제·완료 업무 보기</button></p>}
      {deliverablesError && (
        <p role="alert" className="mb-4 text-sm text-red-500">
          {deliverablesError}{" "}
          <button onClick={loadDeliverables} className="underline hover:text-red-400">다시 시도</button>
        </p>
      )}
      {boardError && <p role="alert" className="mb-4 text-sm text-red-500">{boardError}</p>}
      {view === "list" && tasks.length > 0 ? (
        <>
          {!readOnly && (
            <div className="mb-3 flex justify-end">
              <button onClick={() => openCreate("TODO")} className="rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-500">
                + 업무 추가
              </button>
            </div>
          )}
          {!(scope === "review" && visibleTasks.length === 0 && (reviewLoading || reviewError)) && <TaskListView tasks={visibleTasks} onEdit={openEdit} onMove={handleMoveTask} busy={updating} readOnly={readOnly} aiIds={aiOrigins.task} />}
        </>
      ) : (
      <>
        {tasks.length === 0 ? (
          /* 빈 상태 — 태스크가 하나도 없을 때 */
          <div className="flex flex-col items-center justify-center text-center py-28">
            <span className="text-5xl mb-4">📋</span>
            <p className="text-base font-semibold text-bb-text mb-1.5">아직 태스크가 없어요</p>
            {readOnly ? (
              <p className="text-sm text-bb-text2">팀원이 업무를 만들면 여기서 확인할 수 있습니다</p>
            ) : (
              <>
                <p className="text-sm text-bb-text2 mb-6">첫 태스크를 만들어 팀원에게 배정해보세요</p>
                <button
                  onClick={() => openCreate("TODO")}
                  className="px-4 py-2.5 bg-indigo-600 hover:bg-indigo-500
                             text-white text-sm font-medium rounded-lg transition-all"
                >
                  + 태스크 추가
                </button>
              </>
            )}
          </div>
        ) : (
          <div className="grid grid-cols-[repeat(3,minmax(16rem,1fr))] md:grid-cols-3 gap-4 md:gap-6 h-full overflow-x-auto md:overflow-visible pb-2 md:pb-0">
            {KANBAN_COLUMNS.map((col) => (
              <KanbanColumn
                key={col.id}
                column={col.id === "DONE" && !preferences.showDone ? { ...col, label: "완료 · 숨김" } : col}
                tasks={tasksByStatus(col.id)}
                scoreMap={scoreMap}
                onAddTask={openCreate}
                onEditTask={openEdit}
                onMoveTask={handleMoveTask}
                readOnly={readOnly}
                busy={updating}
                aiIds={aiOrigins.task}
              />
            ))}
          </div>
        )}

      </>
      )}

      {/* Task create / edit modal */}
      {modalMode && (
        <TaskModal
          mode={modalMode}
          task={editingTask}
          members={members}
          deliverables={deliverables}
          defaultStatus={createStatus}
          onClose={closeModal}
          onCreate={handleCreate}
          onUpdate={handleUpdate}
          onDelete={handleDelete}
          readOnly={readOnly}
          availableTasks={tasks}
          onWorkflowChanged={data => { setEditingTask(data); setTasks(prev => prev.map(t => t.id === data.id ? data : t)); onTasksChange?.(tasks.map(t => t.id === data.id ? data : t)); }}
        />
      )}
    </>
  );
}
