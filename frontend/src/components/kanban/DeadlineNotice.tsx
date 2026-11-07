"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AlertTriangle, Clock } from "lucide-react";
import type { Task } from "@/types/task";
import { DUE_BADGE, daysLeft, dueLabel, dueTone } from "@/lib/due";

/** 조회한 업무에 기반한 화면 알림. 읽음/발송 기록을 가진 서버 경보와 구분한다. */
export default function DeadlineNotice({ tasks }: { tasks: Task[] }) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(timer);
  }, []);

  const pending = tasks.filter(t => t.status !== "DONE" && t.dueDate);
  const urgent = pending.filter(t => daysLeft(t.dueDate!, now) <= 3)
    .sort((a, b) => a.dueDate!.localeCompare(b.dueDate!));
  const overdue = urgent.filter(t => daysLeft(t.dueDate!, now) < 0).length;
  const today = urgent.filter(t => daysLeft(t.dueDate!, now) === 0).length;

  return <section aria-label="업무 마감 알림" className="rounded-xl border border-bb-border bg-bb-surface p-4">
    <div className="flex flex-wrap items-center gap-2 text-xs">
      <h2 className="mr-2 inline-flex items-center gap-1.5 font-semibold text-bb-text"><Clock size={14} />마감 알림</h2>
      <span className={`rounded border px-2 py-1 ${DUE_BADGE.overdue}`}>초과 {overdue} · 오늘 {today}</span>
      <span className={`rounded border px-2 py-1 ${DUE_BADGE.soon}`}>임박 · 1~3일 {urgent.length - overdue - today}</span>
      <span className={`rounded border px-2 py-1 ${DUE_BADGE.later}`}>여유 · 4일 이상 {pending.length - urgent.length}</span>
    </div>
    {urgent.length > 0 ? <details className="mt-3" open>
      <summary className="cursor-pointer text-sm font-medium text-bb-text">확인이 필요한 업무 {urgent.length}개</summary>
      <ul className="mt-2 max-h-60 overflow-y-auto space-y-1">
        {urgent.map(t => <li key={t.id}>
          <Link href={`/projects/${t.projectId}/board?task=${t.id}`} className="flex flex-wrap items-center gap-2 rounded-lg px-2 py-2 hover:bg-bb-surface2">
            <AlertTriangle size={14} aria-hidden="true" className="shrink-0 text-bb-text2" />
            <span className="min-w-0 flex-1 basis-32 break-words text-sm text-bb-text">{t.title}</span>
            <span className="text-xs text-bb-text2">{t.assignees.map(a => a.name).join(", ") || "담당자 미지정"}</span>
            <span className={`shrink-0 rounded border px-2 py-0.5 text-xs font-semibold ${DUE_BADGE[dueTone(t.dueDate!, 3, now)]}`}>{dueLabel(t.dueDate!, now)}</span>
          </Link>
        </li>)}
      </ul>
    </details> : <p className="mt-3 text-xs text-bb-text2">3일 이내 마감하거나 기한을 초과한 미완료 업무가 없습니다.</p>}
    <p className="mt-2 text-xs text-bb-text2">프로젝트 전체 미완료 업무 기준 · 마감일 당일까지 진행할 수 있습니다.</p>
  </section>;
}
