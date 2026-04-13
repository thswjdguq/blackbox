"use client";

import { useState } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Task, TaskStatus, ScoreMap } from "@/types/task";
import {
  GripVertical,
  Calendar,
  Tag,
  ChevronUp,
  Minus,
  ChevronDown,
  ChevronsUp,
  Star,
  Check,
  ArrowRight,
} from "lucide-react";

interface TaskCardProps {
  task: Task;
  scoreMap: ScoreMap;
  onEdit: (task: Task) => void;
  onMove?: (taskId: string, newStatus: TaskStatus) => void;
}

const PRIORITY_CONFIG = {
  URGENT: { icon: ChevronsUp,  label: "긴급", cls: "text-rose-400 bg-rose-400/10" },
  HIGH:   { icon: ChevronUp,   label: "높음", cls: "text-orange-400 bg-orange-400/10" },
  MEDIUM: { icon: Minus,       label: "중간", cls: "text-indigo-400 bg-indigo-400/10" },
  LOW:    { icon: ChevronDown, label: "낮음", cls: "text-slate-400 bg-slate-700" },
};

function formatDue(dueDate: string | null): { label: string; cls: string } | null {
  if (!dueDate) return null;
  const due = new Date(dueDate);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diff = Math.ceil((due.getTime() - today.getTime()) / 86400000);
  const label = due.toLocaleDateString("ko-KR", { month: "short", day: "numeric" });
  if (diff < 0)  return { label, cls: "text-rose-400" };
  if (diff <= 2) return { label, cls: "text-orange-400" };
  return { label, cls: "text-slate-400" };
}

function Avatar({ name }: { name: string }) {
  const initials = name.slice(0, 2).toUpperCase();
  const hue = name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360;
  return (
    <div
      title={name}
      style={{ background: `hsl(${hue} 55% 45%)` }}
      className="w-6 h-6 rounded-full flex items-center justify-center text-[9px] font-bold text-white ring-2 ring-slate-800 -ml-1 first:ml-0"
    >
      {initials}
    </div>
  );
}

function ScoreChip({ score }: { score: number }) {
  const color =
    score >= 120 ? "text-teal-300 bg-teal-400/10" :
    score >= 80  ? "text-indigo-300 bg-indigo-400/10" :
    score >= 50  ? "text-slate-300 bg-slate-700" :
                   "text-rose-400 bg-rose-400/10";
  return (
    <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-semibold ${color}`}>
      <Star size={9} />
      {score.toFixed(0)}
    </span>
  );
}

export default function TaskCard({ task, scoreMap, onEdit, onMove }: TaskCardProps) {
  const [isMoving, setIsMoving] = useState(false);

  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: task.id });

  // 카드 전체에 drag listeners 적용 — 어디서든 드래그 가능
  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.4 : isMoving ? 0 : 1,
  };

  const priority = PRIORITY_CONFIG[task.priority] ?? PRIORITY_CONFIG.MEDIUM;
  const PriorityIcon = priority.icon;
  const dueInfo = formatDue(task.dueDate);
  const representativeScore =
    task.assignees.length > 0 ? scoreMap[task.assignees[0].userId] : undefined;
  const isDone = task.status === "DONE";

  // 버튼 클릭 → 이동 트리거 (drag와 분리)
  const triggerMove = (e: React.MouseEvent, newStatus: TaskStatus) => {
    e.stopPropagation();       // 카드 onClick(모달) 방지
    if (isMoving || !onMove) return;
    setIsMoving(true);
    // 160ms 페이드아웃 후 실제 이동
    setTimeout(() => onMove(task.id, newStatus), 160);
  };

  // 버튼에서 포인터 다운 시 drag 시작 방지
  const stopPointer = (e: React.PointerEvent) => e.stopPropagation();

  return (
    <div
      ref={setNodeRef}
      style={style}
      // 카드 전체에 drag listeners 부착 (grip 아이콘 외에도 드래그 가능)
      {...attributes}
      {...listeners}
      onClick={() => !isMoving && onEdit(task)}
      className={`group bg-slate-800 border rounded-xl p-4 cursor-grab active:cursor-grabbing
                  transition-colors duration-200 select-none
                  ${isDone
                    ? "border-slate-700/40 opacity-60"
                    : "border-slate-700/60 hover:border-indigo-500/50 hover:shadow-lg hover:shadow-indigo-500/5"
                  }`}
    >
      {/* Top Row */}
      <div className="flex items-start gap-2 mb-3">

        {/* TODO 체크박스 — 클릭 시 In Progress 이동 */}
        {task.status === "TODO" && (
          <button
            onPointerDown={stopPointer}   // drag 시작 방지
            onClick={(e) => triggerMove(e, "IN_PROGRESS")}
            className="mt-0.5 shrink-0 w-5 h-5 rounded-full border-2 border-indigo-500/40
                       hover:border-indigo-500 hover:bg-indigo-500/20
                       flex items-center justify-center transition-all duration-150 group/cb"
            aria-label="진행 중으로 이동"
          >
            <Check
              size={10}
              className="text-indigo-500 opacity-0 group-hover/cb:opacity-100 transition-opacity duration-100"
            />
          </button>
        )}

        {/* DONE 완료 표시 */}
        {task.status === "DONE" && (
          <div className="mt-0.5 shrink-0 w-5 h-5 rounded-full bg-teal-500/20 border-2 border-teal-500/60
                          flex items-center justify-center">
            <Check size={10} className="text-teal-400" />
          </div>
        )}

        {/* 드래그 핸들 (시각 힌트) */}
        <GripVertical
          size={14}
          className="mt-0.5 text-slate-600 group-hover:text-slate-400 transition-colors shrink-0"
        />

        {/* Title */}
        <p className={`flex-1 text-sm font-medium leading-snug line-clamp-2 transition-colors
                       ${isDone
                         ? "line-through text-slate-500"
                         : "text-slate-200 group-hover:text-white"}`}>
          {task.title}
        </p>

        {/* Priority badge */}
        <span className={`shrink-0 flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] font-medium ${priority.cls}`}>
          <PriorityIcon size={10} />
          {priority.label}
        </span>
      </div>

      {/* Description preview */}
      {task.description && (
        <p className="text-xs text-slate-500 mb-3 line-clamp-2 leading-relaxed">
          {task.description}
        </p>
      )}

      {/* Tag */}
      {task.tag && (
        <div className="flex items-center gap-1 mb-3">
          <Tag size={10} className="text-teal-400 shrink-0" />
          <span className="text-[10px] text-teal-400 bg-teal-400/10 px-1.5 py-0.5 rounded-full font-medium">
            {task.tag}
          </span>
        </div>
      )}

      {/* Bottom Row */}
      <div className="flex items-center justify-between mt-3 pt-3 border-t border-slate-700/60">
        {/* Assignee avatars */}
        <div className="flex items-center">
          {task.assignees.length === 0 ? (
            <span className="text-[10px] text-slate-600">미배정</span>
          ) : (
            task.assignees.slice(0, 4).map((a) => (
              <Avatar key={a.userId} name={a.name} />
            ))
          )}
          {task.assignees.length > 4 && (
            <div className="w-6 h-6 rounded-full bg-slate-700 flex items-center justify-center text-[9px] text-slate-400 -ml-1">
              +{task.assignees.length - 4}
            </div>
          )}
        </div>

        {/* Score + Due + IN_PROGRESS → Done 버튼 */}
        <div className="flex items-center gap-2">
          {representativeScore !== undefined && (
            <ScoreChip score={representativeScore} />
          )}
          {dueInfo && (
            <span className={`flex items-center gap-1 text-[10px] font-medium ${dueInfo.cls}`}>
              <Calendar size={9} />
              {dueInfo.label}
            </span>
          )}

          {task.status === "IN_PROGRESS" && (
            <button
              onPointerDown={stopPointer}   // drag 시작 방지
              onClick={(e) => triggerMove(e, "DONE")}
              className="flex items-center gap-1 px-2 py-1 rounded-lg text-[10px] font-semibold
                         text-teal-400 bg-teal-400/10 border border-teal-400/20
                         hover:bg-teal-400/20 hover:border-teal-400/50
                         transition-all duration-150"
              aria-label="완료로 이동"
            >
              <ArrowRight size={9} />
              완료
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
