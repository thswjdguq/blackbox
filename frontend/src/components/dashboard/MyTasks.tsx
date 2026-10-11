"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ChevronRight, ListTodo, RefreshCw } from "lucide-react";
import api from "@/lib/api";
import { DUE_TEXT, daysLeft, dueLabel, dueTone, shortDate } from "@/lib/due";
import type { Task } from "@/types/task";

interface ProjectRef {
  id: string;
  name: string;
}

type Item = Task & { projectName: string };

type GroupKey = "overdue" | "today" | "week" | "later" | "none";

const GROUPS: { key: GroupKey; label: string; collapsed: boolean }[] = [
  { key: "overdue", label: "기한 지남", collapsed: false },
  { key: "today",   label: "오늘",     collapsed: false },
  { key: "week",    label: "이번 주",  collapsed: false },
  { key: "later",   label: "나중",     collapsed: true },
  { key: "none",    label: "기한 없음", collapsed: true },
];

/** 접힌 묶음에서 처음 보여 줄 개수 */
const PREVIEW = 3;

function groupOf(t: Task): GroupKey {
  if (!t.dueDate) return "none";
  const d = daysLeft(t.dueDate);
  if (d < 0) return "overdue";
  if (d === 0) return "today";
  if (d <= 6) return "week";
  return "later";
}

const PRIORITY_ORDER = { URGENT: 0, HIGH: 1, MEDIUM: 2, LOW: 3 } as Record<string, number>;

/**
 * 모든 프로젝트에서 나에게 배정된 아직 안 끝난 업무를 마감별로 모은다 (C-50 3번, Flow 모아보기 참고).
 * 여러 프로젝트를 모아 주는 서버 API가 없어 프로젝트마다 업무 목록을 불러 합친다.
 * 일부 프로젝트를 못 불러오면 나머지는 보여 주고 어느 프로젝트가 빠졌는지 알린다.
 */
export default function MyTasks({ projects }: { projects: ProjectRef[] }) {
  const [items, setItems] = useState<Item[] | null>(null);
  const [failed, setFailed] = useState<string[]>([]);
  const [profileFailed, setProfileFailed] = useState(false);
  const [expanded, setExpanded] = useState<Set<GroupKey>>(new Set());

  const load = useCallback(async () => {
    setItems(null);
    setFailed([]);
    setProfileFailed(false);
    let myId: string;
    try {
      myId = (await api.get<{ id: string }>("/auth/profile")).data.id;
    } catch {
      setProfileFailed(true);
      setItems([]);
      return;
    }
    const results = await Promise.allSettled(
      projects.map((p) => api.get<Task[]>(`/projects/${p.id}/tasks`).then((r) => ({ p, tasks: r.data })))
    );
    const all: Item[] = [];
    const miss: string[] = [];
    results.forEach((r, i) => {
      if (r.status === "rejected") { miss.push(projects[i].name); return; }
      for (const t of r.value.tasks) {
        if (t.status !== "DONE" && t.assignees.some((a) => a.userId === myId)) all.push({ ...t, projectName: r.value.p.name });
      }
    });
    all.sort((a, b) =>
      (a.dueDate ?? "9999").localeCompare(b.dueDate ?? "9999") ||
      (PRIORITY_ORDER[a.priority] ?? 9) - (PRIORITY_ORDER[b.priority] ?? 9)
    );
    setItems(all);
    setFailed(miss);
  }, [projects]);

  useEffect(() => {
    if (projects.length > 0) load();
  }, [projects, load]);

  if (projects.length === 0) return null;

  const toggle = (k: GroupKey) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(k)) next.delete(k); else next.add(k);
      return next;
    });

  return (
    <section className="mb-10" aria-labelledby="my-tasks-title">
      <h2 id="my-tasks-title" className="text-sm font-semibold text-bb-text2 uppercase tracking-wider mb-3 flex items-center gap-2">
        <ListTodo size={14} />
        내 업무
        {items && items.length > 0 && <span className="normal-case text-xs font-normal">{items.length}개</span>}
      </h2>

      {items === null ? (
        <div className="h-24 rounded-xl bg-bb-surface border border-bb-border animate-pulse" />
      ) : profileFailed ? (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-red-500/20 bg-red-500/10 p-4 text-sm text-red-400">
          내 업무를 불러오지 못했습니다.
          <button onClick={load} className="inline-flex items-center gap-1 text-xs underline"><RefreshCw size={12} /> 다시 시도</button>
        </div>
      ) : (
        <div className="rounded-xl bg-bb-surface border border-bb-border divide-y divide-bb-border">
          {failed.length > 0 && (
            <div role="alert" className="flex flex-wrap items-center gap-3 px-4 py-3 text-xs text-amber-500">
              {failed.join(", ")} 프로젝트의 업무를 불러오지 못해 빠져 있습니다.
              <button onClick={load} className="inline-flex items-center gap-1 underline"><RefreshCw size={12} /> 다시 시도</button>
            </div>
          )}

          {items.length === 0 && failed.length === 0 && (
            <p className="px-4 py-6 text-center text-sm text-bb-text2">
              나에게 배정된 진행 중인 업무가 없습니다. 업무 보드에서 담당자로 지정되면 여기에 모입니다.
            </p>
          )}

          {GROUPS.map((g) => {
            const list = items.filter((t) => groupOf(t) === g.key);
            if (list.length === 0) return null;
            const showAll = !g.collapsed || expanded.has(g.key);
            const shown = showAll ? list : list.slice(0, PREVIEW);
            return (
              <div key={g.key} className="px-4 py-3" data-testid={`my-tasks-${g.key}`}>
                <p className={`mb-2 text-xs font-semibold ${g.key === "overdue" ? "text-red-400" : g.key === "today" ? "text-orange-400" : "text-bb-text2"}`}>
                  {g.label} <span className="font-normal">{list.length}</span>
                </p>
                <ul className="space-y-1">
                  {shown.map((t) => (
                    <li key={t.id}>
                      <Link
                        href={`/projects/${t.projectId}/board?task=${t.id}`}
                        className="group flex items-center gap-3 rounded-lg px-2 py-1.5 -mx-2 hover:bg-bb-surface2"
                      >
                        <span className={`shrink-0 text-[10px] px-1.5 py-0.5 rounded ${t.status === "IN_PROGRESS" ? "bg-indigo-500/15 text-indigo-400" : "bg-bb-surface2 text-bb-text2"}`}>
                          {t.status === "IN_PROGRESS" ? "진행 중" : "할 일"}
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm text-bb-text">{t.title}</span>
                          <span className="block truncate text-xs text-bb-text2">
                            {/* 작은 화면에서는 마감을 둘째 줄에 붙여 제목 폭을 넓힌다 */}
                            {t.dueDate && (
                              <span className={`sm:hidden ${DUE_TEXT[dueTone(t.dueDate)]}`}>{dueLabel(t.dueDate)} · </span>
                            )}
                            {t.projectName}{t.deliverableTitle ? ` · ${t.deliverableTitle}` : ""}
                          </span>
                        </span>
                        {t.dueDate && (
                          <span className={`hidden sm:inline shrink-0 whitespace-nowrap text-xs ${DUE_TEXT[dueTone(t.dueDate)]}`}>
                            {shortDate(t.dueDate)} · {dueLabel(t.dueDate)}
                          </span>
                        )}
                        <ChevronRight size={13} className="shrink-0 text-bb-text2 opacity-0 group-hover:opacity-100" />
                      </Link>
                    </li>
                  ))}
                </ul>
                {g.collapsed && list.length > PREVIEW && (
                  <button onClick={() => toggle(g.key)} className="mt-1 text-xs text-bb-text2 hover:text-bb-text">
                    {showAll ? "접기" : `${list.length - PREVIEW}개 더 보기`}
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
