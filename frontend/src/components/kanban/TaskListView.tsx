"use client";

import { useMemo, useState } from "react";
import { ArrowDown, ArrowUp } from "lucide-react";
import { DUE_TEXT, dueLabel, dueTone, shortDate } from "@/lib/due";
import { PRIORITY_TEXT, TASK_STATUS_TEXT, type Task, type TaskPriority, type TaskStatus } from "@/types/task";

type SortKey = "title" | "status" | "assignee" | "due" | "deliverable" | "priority";

const STATUS_ORDER: Record<TaskStatus, number> = { TODO: 0, IN_PROGRESS: 1, DONE: 2 };
const PRIORITY_ORDER: Record<TaskPriority, number> = { URGENT: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };

const STATUS_CLS: Record<TaskStatus, string> = {
  TODO: "bg-bb-surface2 text-bb-text2",
  IN_PROGRESS: "bg-indigo-500/15 text-indigo-400",
  DONE: "bg-teal-400/15 text-teal-400",
};

const COLUMNS: { key: SortKey; label: string; cls: string }[] = [
  { key: "title",       label: "업무",       cls: "w-[32%]" },
  { key: "status",      label: "상태",       cls: "w-[10%]" },
  { key: "assignee",    label: "담당자",     cls: "w-[14%]" },
  { key: "due",         label: "마감",       cls: "w-[16%]" },
  { key: "deliverable", label: "제출물·요구사항", cls: "w-[20%]" },
  { key: "priority",    label: "우선순위",   cls: "w-[8%]" },
];

function compare(a: Task, b: Task, key: SortKey): number {
  switch (key) {
    case "title":       return a.title.localeCompare(b.title, "ko");
    case "status":      return STATUS_ORDER[a.status] - STATUS_ORDER[b.status];
    case "assignee":    return (a.assignees[0]?.name ?? "￿").localeCompare(b.assignees[0]?.name ?? "￿", "ko");
    // 마감 없는 업무는 오름차순에서 맨 뒤
    case "due":         return (a.dueDate ?? "9999-12-31").localeCompare(b.dueDate ?? "9999-12-31");
    case "deliverable": return (a.deliverableTitle ?? "￿").localeCompare(b.deliverableTitle ?? "￿", "ko");
    case "priority":    return PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority];
  }
}

/**
 * 업무 목록 보기 (C-50 6번, Flow 업무 목록 참고). 보드와 같은 필터를 거친 업무를 표로 보여 준다.
 * 열 이름을 누르면 정렬하고, 행을 누르거나 Enter를 누르면 업무 창이 열린다.
 */
export default function TaskListView({ tasks, onEdit, readOnly }: {
  tasks: Task[];
  onEdit: (task: Task) => void;
  readOnly?: boolean;
}) {
  const [sortKey, setSortKey] = useState<SortKey>("due");
  const [asc, setAsc] = useState(true);

  const sorted = useMemo(() => {
    const list = [...tasks].sort((a, b) => compare(a, b, sortKey) || compare(a, b, "due") || compare(a, b, "title"));
    return asc ? list : list.reverse();
  }, [tasks, sortKey, asc]);

  const sortBy = (key: SortKey) => {
    if (key === sortKey) setAsc((v) => !v);
    else { setSortKey(key); setAsc(true); }
  };

  if (tasks.length === 0) {
    return <p className="py-16 text-center text-sm text-bb-text2">조건에 맞는 업무가 없습니다.</p>;
  }

  return (
    // 작은 화면에서는 표만 가로로 넘긴다
    <div className="overflow-x-auto rounded-xl border border-bb-border bg-bb-surface">
      <table className="w-full min-w-[720px] table-fixed text-sm">
        <caption className="sr-only">업무 목록. 열 이름을 눌러 정렬합니다.</caption>
        <thead>
          <tr className="border-b border-bb-border text-left text-xs text-bb-text2">
            {COLUMNS.map((c) => (
              <th key={c.key} scope="col" className={`px-3 py-2.5 font-medium ${c.cls}`}
                aria-sort={sortKey === c.key ? (asc ? "ascending" : "descending") : "none"}>
                <button onClick={() => sortBy(c.key)} className="inline-flex items-center gap-1 whitespace-nowrap hover:text-bb-text">
                  {c.label}
                  {sortKey === c.key && (asc ? <ArrowUp size={12} /> : <ArrowDown size={12} />)}
                </button>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sorted.map((t) => {
            const done = t.status === "DONE";
            return (
              <tr
                key={t.id}
                data-testid={`task-row-${t.id}`}
                tabIndex={0}
                onClick={() => onEdit(t)}
                onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onEdit(t); } }}
                aria-label={`${t.title} ${readOnly ? "보기" : "열기"}`}
                className={`cursor-pointer border-b border-bb-border/60 last:border-0 hover:bg-bb-surface2 focus:outline-none focus-visible:bg-bb-surface2 ${done ? "opacity-60" : ""}`}
              >
                <td className="px-3 py-2.5">
                  <span className={`block truncate text-bb-text ${done ? "line-through" : ""}`}>{t.title}</span>
                  {t.tag && <span className="block truncate text-[11px] text-bb-text2">#{t.tag}</span>}
                </td>
                <td className="px-3 py-2.5">
                  <span className={`whitespace-nowrap rounded px-1.5 py-0.5 text-[11px] ${STATUS_CLS[t.status]}`}>{TASK_STATUS_TEXT[t.status]}</span>
                </td>
                <td className="px-3 py-2.5 truncate text-bb-text2">
                  {t.assignees.length === 0 ? "미배정" : t.assignees.map((a) => a.name).join(", ")}
                </td>
                <td className="px-3 py-2.5 whitespace-nowrap">
                  {t.dueDate ? (
                    <>
                      <span className="text-bb-text2">{shortDate(t.dueDate)}</span>
                      {!done && <span className={`ml-1.5 text-xs ${DUE_TEXT[dueTone(t.dueDate)]}`}>{dueLabel(t.dueDate)}</span>}
                    </>
                  ) : (
                    <span className="text-bb-text2">-</span>
                  )}
                </td>
                <td className="px-3 py-2.5">
                  <span className="block truncate text-bb-text2">{t.deliverableTitle ?? "제출물 미연결"}</span>
                  {t.requirementContent && <span className="block truncate text-[11px] text-bb-text2/80">{t.requirementContent}</span>}
                </td>
                <td className="px-3 py-2.5 whitespace-nowrap text-bb-text2">{PRIORITY_TEXT[t.priority]}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
