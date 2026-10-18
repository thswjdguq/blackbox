"use client";

import TaskStatusControl from "./TaskStatusControl";
import { Task, TaskStatus, ScoreMap } from "@/types/task";
import AiBadge from "@/components/agent/AiBadge";
import { DUE_BADGE, DUE_BAR, DUE_TEXT, dueTone, dueLabel, shortDate } from "@/lib/due";
import {
  Calendar,
  Tag,
  ChevronUp,
  Minus,
  ChevronDown,
  ChevronsUp,
  Star,
  Check,
} from "lucide-react";

interface TaskCardProps {
  task: Task;
  scoreMap: ScoreMap;
  onEdit: (task: Task) => void;
  onMove: (taskId: string, newStatus: TaskStatus) => Promise<void>;
  busy?: boolean;
  readOnly?: boolean;
  isAi?: boolean;
}

const PRIORITY_CONFIG = {
  URGENT: { icon: ChevronsUp,  label: "긴급", cls: "text-rose-400 bg-rose-400/10" },
  HIGH:   { icon: ChevronUp,   label: "높음", cls: "text-orange-400 bg-orange-400/10" },
  MEDIUM: { icon: Minus,       label: "보통", cls: "text-indigo-400 bg-indigo-400/10" },
  LOW:    { icon: ChevronDown, label: "낮음", cls: "text-bb-text2 bg-bb-surface2" },
};

function formatDue(dueDate: string | null, isDone: boolean): { label: string; cls: string } | null {
  if (!dueDate) return null;
  return { label: shortDate(dueDate), cls: isDone ? "text-bb-text2" : DUE_TEXT[dueTone(dueDate)] };
}

function getDueWarning(dueDate: string | null, isDone: boolean): { label: string; badgeCls: string; barCls: string } | null {
  if (!dueDate || isDone) return null;
  const tone = dueTone(dueDate);
  return { label: dueLabel(dueDate), badgeCls: "border " + DUE_BADGE[tone], barCls: DUE_BAR[tone] };
}

function Avatar({ name }: { name: string }) {
  const initials = name.slice(0, 2).toUpperCase();
  const hue = name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360;
  return (
    <div
      title={name}
      style={{ background: `hsl(${hue} 55% 45%)` }}
      className="w-6 h-6 rounded-full flex items-center justify-center text-[9px] font-bold text-white ring-2 ring-bb-surface -ml-1 first:ml-0"
    >
      {initials}
    </div>
  );
}

function ScoreChip({ score }: { score: number }) {
  const color =
    score >= 120 ? "text-teal-300 bg-teal-400/10" :
    score >= 80  ? "text-indigo-300 bg-indigo-400/10" :
    score >= 50  ? "text-bb-text bg-bb-surface2" :
                   "text-rose-400 bg-rose-400/10";
  return (
    <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-semibold ${color}`}>
      <Star size={9} />
      {score.toFixed(0)}
    </span>
  );
}

export default function TaskCard({ task, scoreMap, onEdit, onMove, readOnly = false, isAi = false, busy }: TaskCardProps) {
  const priority = PRIORITY_CONFIG[task.priority] ?? PRIORITY_CONFIG.MEDIUM;
  const PriorityIcon = priority.icon;
  const dueInfo = formatDue(task.dueDate, task.status === "DONE");
  const dueWarning = getDueWarning(task.dueDate, task.status === "DONE");
  const representativeScore =
    task.assignees.length > 0 ? scoreMap[task.assignees[0].userId] : undefined;
  const isDone = task.status === "DONE";

  return (
    <div role="button" tabIndex={0} aria-label={`${task.title} 보기`}
      onKeyDown={e => { if (e.target === e.currentTarget && (e.key === "Enter" || e.key === " ")) { e.preventDefault(); onEdit(task); } }}
      onClick={() => onEdit(task)}
      className={`group relative cursor-pointer bg-bb-surface border rounded-xl p-4 overflow-hidden ${isDone ? "border-bb-border/40 opacity-60" : "border-bb-border/60 hover:border-indigo-500/50"}`}>
      {/* 마감일 경고 강조선 */}
      {dueWarning && (
        <div className={`absolute left-0 top-0 bottom-0 w-1 ${dueWarning.barCls}`} />
      )}

      {/* Top Row */}
      <div className="flex items-start gap-2 mb-3">

        {/* DONE 완료 표시 */}
        {task.status === "DONE" && (
          <div className="mt-0.5 shrink-0 w-5 h-5 rounded-full bg-teal-500/20 border-2 border-teal-500/60
                          flex items-center justify-center">
            <Check size={10} className="text-teal-400" />
          </div>
        )}

        {/* Title */}
        <p className={`flex-1 text-sm font-medium leading-snug line-clamp-2 transition-colors
                       ${isDone
                         ? "line-through text-bb-text2"
                         : "text-bb-text"}`}>
          {task.title} {isAi && <AiBadge />}
        </p>

        {/* Priority badge */}
        <span className={`shrink-0 flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] font-medium ${priority.cls}`}>
          <PriorityIcon size={10} />
          {priority.label}
        </span>

        {/* 마감일 경고 배지 */}
        {dueWarning && (
          <span className={`shrink-0 px-1.5 py-0.5 rounded text-[10px] font-semibold ${dueWarning.badgeCls}`}>
            {dueWarning.label}
          </span>
        )}
      </div>

      <p className="mb-2 text-xs text-teal-400 truncate" title={task.deliverableTitle ?? "제출물 미연결"}>
        {task.deliverableTitle ? `제출물: ${task.deliverableTitle}` : "제출물 미연결"}
      </p>
      {/* Description preview */}
      {task.description && (
        <p className="text-xs text-bb-text2 mb-3 line-clamp-2 leading-relaxed">
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

      <TaskStatusControl task={task} onEdit={onEdit} onMove={onMove} readOnly={readOnly} busy={busy} />
      {/* Bottom Row */}
      {task.completionRequestedAt && <p className="text-xs text-bb-primary mt-2">팀원 확인 대기</p>}
      {task.completionRejectionReason && <p className="text-xs text-amber-700 mt-2">보완 요청 · 업무를 열어 확인하세요.</p>}
      {task.predecessorTitle && <p className="text-xs text-bb-text2 mt-2">선행: {task.predecessorTitle}</p>}
      {task.status === "DONE" && !task.completionApprovedAt && <p className="text-xs text-bb-text2 mt-2">이전 완료 · 승인 기록 없음</p>}
      <div className="flex items-center justify-between mt-3 pt-3 border-t border-bb-border/60">
        {/* Assignee avatars */}
        <div className="flex items-center">
          {task.assignees.length === 0 ? (
            <span className="text-[10px] text-bb-text2/70">미배정</span>
          ) : (
            task.assignees.slice(0, 4).map((a) => (
              <Avatar key={a.userId} name={a.name} />
            ))
          )}
          {task.assignees.length > 4 && (
            <div className="w-6 h-6 rounded-full bg-bb-surface2 flex items-center justify-center text-[9px] text-bb-text2 -ml-1">
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


        </div>
      </div>
    </div>
  );
}
