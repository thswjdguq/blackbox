"use client";

import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Task, ScoreMap } from "@/types/task";
import {
  GripVertical,
  Calendar,
  Tag,
  ChevronUp,
  Minus,
  ChevronDown,
  ChevronsUp,
  Star,
} from "lucide-react";

interface TaskCardProps {
  task: Task;
  scoreMap: ScoreMap;
  onEdit: (task: Task) => void;
}

// ── Priority config ────────────────────────────────────────────────────────
const PRIORITY_CONFIG = {
  URGENT: { icon: ChevronsUp,  label: "긴급", cls: "text-rose-400 bg-rose-400/10" },
  HIGH:   { icon: ChevronUp,   label: "높음", cls: "text-orange-400 bg-orange-400/10" },
  MEDIUM: { icon: Minus,       label: "중간", cls: "text-indigo-400 bg-indigo-400/10" },
  LOW:    { icon: ChevronDown, label: "낮음", cls: "text-slate-400 bg-slate-700" },
};

// ── Due-date helpers ───────────────────────────────────────────────────────
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

// ── Avatar ─────────────────────────────────────────────────────────────────
function Avatar({ name }: { name: string }) {
  const initials = name.slice(0, 2).toUpperCase();
  // deterministic hue from name
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

// ── Score chip ─────────────────────────────────────────────────────────────
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

// ── Main Component ─────────────────────────────────────────────────────────
export default function TaskCard({ task, scoreMap, onEdit }: TaskCardProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: task.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.4 : 1,
  };

  const priority = PRIORITY_CONFIG[task.priority] ?? PRIORITY_CONFIG.MEDIUM;
  const PriorityIcon = priority.icon;
  const dueInfo = formatDue(task.dueDate);

  // Representative score = first assignee's score (or undefined)
  const representativeScore = task.assignees.length > 0
    ? scoreMap[task.assignees[0].userId]
    : undefined;

  return (
    <div
      ref={setNodeRef}
      style={style}
      onClick={() => onEdit(task)}
      className="group bg-slate-800 border border-slate-700/60 rounded-xl p-4 cursor-pointer
                 hover:border-indigo-500/50 hover:shadow-lg hover:shadow-indigo-500/5
                 transition-all duration-200 select-none"
    >
      {/* Card Top Row */}
      <div className="flex items-start justify-between gap-2 mb-3">
        {/* Drag handle */}
        <button
          {...attributes}
          {...listeners}
          onClick={(e) => e.stopPropagation()}
          className="mt-0.5 text-slate-600 hover:text-slate-400 transition-colors cursor-grab active:cursor-grabbing shrink-0"
          aria-label="드래그 핸들"
        >
          <GripVertical size={14} />
        </button>

        {/* Title */}
        <p className="flex-1 text-sm font-medium text-slate-200 leading-snug group-hover:text-white transition-colors line-clamp-2">
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

      {/* Bottom Row: assignees + score + due */}
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

        {/* Score + Due */}
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
        </div>
      </div>
    </div>
  );
}
