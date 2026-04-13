"use client";

import { useDroppable } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { Task, TaskStatus, KanbanColumn as ColumnMeta, ScoreMap } from "@/types/task";
import TaskCard from "./TaskCard";
import { Plus } from "lucide-react";

interface KanbanColumnProps {
  column: ColumnMeta;
  tasks: Task[];
  scoreMap: ScoreMap;
  onAddTask: (status: TaskStatus) => void;
  onEditTask: (task: Task) => void;
  onMoveTask: (taskId: string, newStatus: TaskStatus) => void;
}

export default function KanbanColumn({
  column,
  tasks,
  scoreMap,
  onAddTask,
  onEditTask,
  onMoveTask,
}: KanbanColumnProps) {
  const { setNodeRef, isOver } = useDroppable({ id: column.id });
  const isTodo = column.id === "TODO";

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

        {/* 추가 버튼 — To Do 컬럼에만 표시 */}
        {isTodo && (
          <button
            onClick={() => onAddTask(column.id)}
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
              onMove={onMoveTask}
            />
          ))}
        </SortableContext>

        {/* 빈 상태 */}
        {tasks.length === 0 && (
          isTodo ? (
            /* To Do만 클릭으로 추가 가능 */
            <div
              onClick={() => onAddTask(column.id)}
              className="flex-1 flex flex-col items-center justify-center py-8 text-xs text-slate-600
                         cursor-pointer hover:text-slate-400 hover:bg-slate-800/30 rounded-lg transition-all"
            >
              <div className="w-8 h-8 rounded-lg border border-dashed border-slate-700 flex items-center justify-center mb-2">
                <Plus size={14} className="text-slate-600" />
              </div>
              클릭하여 추가
            </div>
          ) : (
            /* In Progress / Done은 드롭 안내만 */
            <div className="flex-1 flex flex-col items-center justify-center py-8 text-xs text-slate-700 rounded-lg">
              <div className="w-8 h-8 rounded-lg border border-dashed border-slate-800 flex items-center justify-center mb-2">
                <Plus size={14} className="text-slate-700" />
              </div>
              드래그하여 이동
            </div>
          )
        )}
      </div>
    </div>
  );
}
