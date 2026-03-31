"use client";

import { useState, useCallback } from "react";
import {
  DndContext,
  DragEndEvent,
  DragOverEvent,
  DragOverlay,
  DragStartEvent,
  PointerSensor,
  useSensor,
  useSensors,
  closestCorners,
} from "@dnd-kit/core";
import { arrayMove } from "@dnd-kit/sortable";
import {
  Task,
  TaskStatus,
  ScoreMap,
  KANBAN_COLUMNS,
  CreateTaskPayload,
} from "@/types/task";
import KanbanColumn from "./KanbanColumn";
import TaskCard from "./TaskCard";
import TaskModal from "./TaskModal";
import api from "@/lib/api";

interface KanbanBoardProps {
  projectId: string;
  initialTasks: Task[];
  members: { userId: string; name: string; email: string }[];
  scoreMap: ScoreMap;
  onTasksChange?: (tasks: Task[]) => void;
}

export default function KanbanBoard({
  projectId,
  initialTasks,
  members,
  scoreMap,
  onTasksChange,
}: KanbanBoardProps) {
  const [tasks, setTasks] = useState<Task[]>(initialTasks);
  const [activeTask, setActiveTask] = useState<Task | null>(null);

  // Modal state
  const [modalMode, setModalMode] = useState<"create" | "edit" | null>(null);
  const [editingTask, setEditingTask] = useState<Task | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } })
  );

  // ── Column grouping ────────────────────────────────────────────────────

  const tasksByStatus = useCallback(
    (status: TaskStatus) => tasks.filter((t) => t.status === status),
    [tasks]
  );

  // ── Drag handlers ──────────────────────────────────────────────────────

  function handleDragStart({ active }: DragStartEvent) {
    const found = tasks.find((t) => t.id === active.id);
    if (found) setActiveTask(found);
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

      if (prev[activeIdx].status === newStatus) return prev;

      const updated = [...prev];
      updated[activeIdx] = { ...updated[activeIdx], status: newStatus };
      return updated;
    });
  }

  async function handleDragEnd({ active, over }: DragEndEvent) {
    setActiveTask(null);
    if (!over) return;

    const activeId = active.id as string;
    const overId = over.id as string;

    setTasks((prev) => {
      const activeIdx = prev.findIndex((t) => t.id === activeId);
      const overIdx = prev.findIndex((t) => t.id === overId);
      if (activeIdx === -1) return prev;

      // Reorder within same column
      if (overIdx !== -1 && activeIdx !== overIdx) {
        return arrayMove(prev, activeIdx, overIdx);
      }
      return prev;
    });

    // Persist status to backend
    const task = tasks.find((t) => t.id === activeId);
    if (!task) return;
    try {
      await api.patch(
        `/projects/${projectId}/tasks/${activeId}/status`,
        { status: task.status }
      );
      onTasksChange?.(tasks);
    } catch (err) {
      console.error("Status update failed:", err);
      // Revert optimistically updated state on error
      setTasks(initialTasks);
    }
  }

  // ── Modal handlers ─────────────────────────────────────────────────────

  const openCreate = () => {
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
    const { data } = await api.patch<Task>(`/projects/${projectId}/tasks/${taskId}`, payload);
    setTasks((prev) => prev.map((t) => (t.id === taskId ? data : t)));
    closeModal();
  };

  const handleDelete = async (taskId: string) => {
    await api.delete(`/projects/${projectId}/tasks/${taskId}`);
    setTasks((prev) => prev.filter((t) => t.id !== taskId));
    closeModal();
  };

  // ── Render ─────────────────────────────────────────────────────────────

  return (
    <>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
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
          onClose={closeModal}
          onCreate={handleCreate}
          onUpdate={handleUpdate}
          onDelete={handleDelete}
        />
      )}
    </>
  );
}
