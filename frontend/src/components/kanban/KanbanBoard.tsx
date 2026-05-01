"use client";

import { useState, useCallback, useRef } from "react";
import {
  DndContext,
  DragEndEvent,
  DragOverEvent,
  DragOverlay,
  DragStartEvent,
  PointerSensor,
  useSensor,
  useSensors,
  closestCenter,
  pointerWithin,
  rectIntersection,
  MeasuringStrategy,
} from "@dnd-kit/core";
import { arrayMove } from "@dnd-kit/sortable";
import {
  Task,
  TaskStatus,
  TaskPriority,
  ScoreMap,
  KANBAN_COLUMNS,
  CreateTaskPayload,
} from "@/types/task";
import KanbanColumn from "./KanbanColumn";
import TaskCard from "./TaskCard";
import TaskModal from "./TaskModal";
import api from "@/lib/api";

export interface KanbanFilter {
  assigneeId: string | null;
  priority: TaskPriority | null;
  tag: string;
}

interface KanbanBoardProps {
  projectId: string;
  initialTasks: Task[];
  members: { userId: string; name: string; email: string }[];
  scoreMap: ScoreMap;
  filter?: KanbanFilter;
  onTasksChange?: (tasks: Task[]) => void;
}

export default function KanbanBoard({
  projectId,
  initialTasks,
  members,
  scoreMap,
  filter,
  onTasksChange,
}: KanbanBoardProps) {
  const [tasks, setTasks] = useState<Task[]>(initialTasks);
  const [activeTask, setActiveTask] = useState<Task | null>(null);

  // Track new status during drag — avoids stale-closure bug in handleDragEnd
  const dragStatusRef = useRef<TaskStatus | null>(null);

  // Modal state
  const [modalMode, setModalMode] = useState<"create" | "edit" | null>(null);
  const [editingTask, setEditingTask] = useState<Task | null>(null);
  const [createStatus, setCreateStatus] = useState<TaskStatus>("TODO");

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } })
  );

  // Custom collision detection: pointer-within first, then closest center.
  // This ensures empty columns are properly detected as drop targets.
  const collisionDetection = useCallback(
    (args: Parameters<typeof closestCenter>[0]) => {
      const pointerCollisions = pointerWithin(args);
      if (pointerCollisions.length > 0) return pointerCollisions;
      return rectIntersection(args);
    },
    []
  );

  // ── Column grouping (filter 적용) ────────────────────────────────────

  const tasksByStatus = useCallback(
    (status: TaskStatus) => {
      let filtered = tasks.filter((t) => t.status === status);
      if (filter?.assigneeId) {
        filtered = filtered.filter((t) =>
          t.assignees.some((a) => a.userId === filter.assigneeId)
        );
      }
      if (filter?.priority) {
        filtered = filtered.filter((t) => t.priority === filter.priority);
      }
      if (filter?.tag) {
        const q = filter.tag.toLowerCase();
        filtered = filtered.filter((t) => t.tag?.toLowerCase().includes(q));
      }
      return filtered;
    },
    [tasks, filter]
  );

  // ── Drag handlers ──────────────────────────────────────────────────────

  function handleDragStart({ active }: DragStartEvent) {
    const found = tasks.find((t) => t.id === active.id);
    if (found) {
      setActiveTask(found);
      dragStatusRef.current = found.status; // initial status
    }
  }

  function handleDragOver({ active, over }: DragOverEvent) {
    if (!over) return;
    const activeId = active.id as string;
    const overId = over.id as string;

    const isOverColumn = KANBAN_COLUMNS.some((c) => c.id === overId);

    setTasks((prev) => {
      const activeIdx = prev.findIndex((t) => t.id === activeId);
      if (activeIdx === -1) return prev;

      const newStatus: TaskStatus = isOverColumn
        ? (overId as TaskStatus)
        : (prev.find((t) => t.id === overId)?.status ?? prev[activeIdx].status);

      // Always update ref so handleDragEnd has the latest status
      dragStatusRef.current = newStatus;

      if (prev[activeIdx].status === newStatus) return prev;

      const updated = [...prev];
      updated[activeIdx] = { ...updated[activeIdx], status: newStatus };
      return updated;
    });
  }

  async function handleDragEnd({ active, over }: DragEndEvent) {
    setActiveTask(null);

    const newStatus = dragStatusRef.current;
    dragStatusRef.current = null;

    if (!over || newStatus === null) return;

    const activeId = active.id as string;
    const overId = over.id as string;

    // Reorder within same column if dropped on another task
    setTasks((prev) => {
      const activeIdx = prev.findIndex((t) => t.id === activeId);
      const overIdx = prev.findIndex((t) => t.id === overId);
      if (activeIdx === -1) return prev;
      if (overIdx !== -1 && activeIdx !== overIdx) {
        return arrayMove(prev, activeIdx, overIdx);
      }
      return prev;
    });

    // Persist status to backend using ref value (not stale closure)
    try {
      await api.patch(
        `/projects/${projectId}/tasks/${activeId}/status`,
        { status: newStatus }
      );
      onTasksChange?.(tasks);
    } catch (err) {
      console.error("Status update failed:", err);
      setTasks(initialTasks);
    }
  }

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

    // 1) 기본 필드 업데이트 (title/description/priority/tag/dueDate)
    const { data } = await api.patch<Task>(`/projects/${projectId}/tasks/${taskId}`, payload);
    let finalTask = data;

    // 2) 담당자 변경 — 별도 PUT 엔드포인트 사용 (UpdateTaskRequest에 assigneeIds 없음)
    if (payload.assigneeIds !== undefined) {
      const { data: assigneeData } = await api.put<Task>(
        `/projects/${projectId}/tasks/${taskId}/assignees`,
        { assigneeIds: payload.assigneeIds }
      );
      finalTask = assigneeData;
    }

    // 3) 상태 변경 — 별도 PATCH 엔드포인트 사용 (UpdateTaskRequest에 status 없음)
    if (payload.status && original?.status !== payload.status) {
      const { data: statusData } = await api.patch<Task>(
        `/projects/${projectId}/tasks/${taskId}/status`,
        { status: payload.status }
      );
      finalTask = statusData;
    }

    setTasks((prev) => prev.map((t) => (t.id === taskId ? finalTask : t)));
    closeModal();
  };

  const handleDelete = async (taskId: string) => {
    await api.delete(`/projects/${projectId}/tasks/${taskId}`);
    setTasks((prev) => prev.filter((t) => t.id !== taskId));
    closeModal();
  };

  // ── 버튼 클릭으로 상태 이동 (To Do→In Progress, In Progress→Done) ─────
  const handleMoveTask = async (taskId: string, newStatus: TaskStatus) => {
    const original = tasks.find((t) => t.id === taskId);
    if (!original || original.status === newStatus) return;

    // 낙관적 업데이트
    setTasks((prev) =>
      prev.map((t) => (t.id === taskId ? { ...t, status: newStatus } : t))
    );

    try {
      await api.patch(`/projects/${projectId}/tasks/${taskId}/status`, { status: newStatus });
    } catch (err) {
      console.error("Task move failed:", err);
      // 실패 시 원상복구
      setTasks((prev) =>
        prev.map((t) => (t.id === taskId ? original : t))
      );
    }
  };

  // ── Render ─────────────────────────────────────────────────────────────

  return (
    <>
      <DndContext
        sensors={sensors}
        collisionDetection={collisionDetection}
        measuring={{ droppable: { strategy: MeasuringStrategy.Always } }}
        onDragStart={handleDragStart}
        onDragOver={handleDragOver}
        onDragEnd={handleDragEnd}
      >
        <div className="grid grid-cols-3 gap-6 h-full">
          {KANBAN_COLUMNS.map((col) => (
            <KanbanColumn
              key={col.id}
              column={col}
              tasks={tasksByStatus(col.id)}
              scoreMap={scoreMap}
              onAddTask={openCreate}
              onEditTask={openEdit}
              onMoveTask={handleMoveTask}
            />
          ))}
        </div>

        {/* Drag overlay — ghost card while dragging */}
        <DragOverlay>
          {activeTask && (
            <div className="rotate-2 scale-105 opacity-90 shadow-2xl shadow-indigo-500/20">
              <TaskCard task={activeTask} scoreMap={scoreMap} onEdit={() => { }} />
            </div>
          )}
        </DragOverlay>
      </DndContext>

      {/* Task create / edit modal */}
      {modalMode && (
        <TaskModal
          mode={modalMode}
          task={editingTask}
          members={members}
          defaultStatus={createStatus}
          onClose={closeModal}
          onCreate={handleCreate}
          onUpdate={handleUpdate}
          onDelete={handleDelete}
        />
      )}
    </>
  );
}
