"use client";

import { useDroppable } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { Task, KanbanColumn as ColumnMeta, ScoreMap } from "@/types/task";
import TaskCard from "./TaskCard";
import { Plus } from "lucide-react";

interface KanbanColumnProps {
  column: ColumnMeta;
  tasks: Task[];
  scoreMap: ScoreMap;
  onAddTask: () => void;
  onEditTask: (task: Task) => void;
}

export default function KanbanColumn({
  column,
  tasks,
  scoreMap,
  onAddTask,
  onEditTask,
}: KanbanColumnProps) {
  const { setNodeRef, isOver } = useDroppable({ id: column.id });

  return (
    <div className="flex flex-col min-h-0">
      {/* Column Header */}
      <div className="flex items-center justify-between mb-4 px-1">
        <div className="flex items-center gap-2.5">
          <div className={`w-2 h-2 rounded-full ${column.accent}`} />
          <span className="text-sm font-semibold text-slate-200">{column.label}</span>
          <span
            className={`text-xs font-bold px-1.5 py-0.5 rounded-full bg-slate-800 border border-slate-700 ${column.countColor}`}
          >
            {tasks.length}
          </span>
        </div>

        {/* Add task — only on TODO */}
        {column.id === "TODO" && (
          <button
            onClick={onAddTask}
            className="text-slate-500 hover:text-indigo-400 hover:bg-indigo-500/10
                       p-1.5 rounded-lg transition-all duration-150"
            aria-label="태스크 추가"
          >
            <Plus size={15} />
          </button>
        )}
      </div>

      {/* Drop zone */}
      <div
        ref={setNodeRef}
        className={`flex-1 flex flex-col gap-3 rounded-xl p-3 min-h-[120px]
                    transition-colors duration-200
                    ${isOver
                      ? "bg-indigo-500/8 border-2 border-dashed border-indigo-500/50"
                      : "bg-slate-900/40 border-2 border-transparent"
                    }`}
      >
        <SortableContext items={tasks.map((t) => t.id)} strategy={verticalListSortingStrategy}>
          {tasks.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              scoreMap={scoreMap}
              onEdit={onEditTask}
            />
          ))}
        </SortableContext>

        {/* Empty state */}
        {tasks.length === 0 && (
          <div className="flex-1 flex flex-col items-center justify-center py-8 text-xs text-slate-600">
            <div className="w-8 h-8 rounded-lg border border-dashed border-slate-700 flex items-center justify-center mb-2">
              <Plus size={14} className="text-slate-600" />
            </div>
            여기로 드래그하세요
          </div>
        )}
      </div>
    </div>
  );
}
