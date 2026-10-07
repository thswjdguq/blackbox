"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { daysLeft, dueLabel, parseLocalDate, shortDate, DUE_TEXT, dueTone } from "@/lib/due";
import type { Deliverable } from "@/types/deliverable";
import type { Task } from "@/types/task";

const DAY = 28;          // 하루 폭(px)
const LABEL_W = 200;     // 왼쪽 이름 칸 (작은 화면은 120)
const PAD_DAYS = 3;      // 처음·끝 여유

function addDays(d: Date, n: number): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
}
function toYmd(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

// #88(types/task.ts의 TASK_STATUS_TEXT)이 병합되면 그쪽을 쓴다
const TASK_STATUS_TEXT: Record<string, string> = { TODO: "할 일", IN_PROGRESS: "진행 중", DONE: "완료" };

const DOT: Record<string, string> = {
  TODO: "bg-slate-400",
  IN_PROGRESS: "bg-indigo-400",
  DONE: "bg-teal-400",
};

interface Row {
  key: string;
  title: string;
  href: string | null;
  due: string | null;
  tasks: Task[];
  undated: number;
}

/**
 * 제출물 일정 보기 (C-50 7번, Flow 간트차트·캘린더 참고). 읽기 전용.
 * 업무에는 시작일이 없어 막대 대신 마감일에 점을 찍는다. 제출물 기한은 마름모, 오늘은 세로선.
 * 점을 누르면 업무 보드에서 그 업무가, 제출물 이름을 누르면 제출물 상세가 열린다.
 */
export default function DeliverableTimeline({ projectId, deliverables, tasks }: {
  projectId: string;
  deliverables: Deliverable[];
  tasks: Task[];
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  // 작은 화면에서는 이름 칸을 줄여 날짜를 더 보여 준다
  const [labelW, setLabelW] = useState(LABEL_W);
  useEffect(() => {
    const mq = window.matchMedia("(max-width: 767px)");
    const update = () => setLabelW(mq.matches ? 120 : LABEL_W);
    update();
    mq.addEventListener("change", update);
    return () => mq.removeEventListener("change", update);
  }, []);

  const rows: Row[] = [...deliverables]
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
    .map((d) => {
      const linked = tasks.filter((t) => t.deliverableId === d.id);
      return {
        key: d.id, title: d.title, href: `/projects/${projectId}/deliverables/${d.id}`, due: d.dueDate,
        tasks: linked.filter((t) => t.dueDate), undated: linked.filter((t) => !t.dueDate).length,
      };
    });
  const unlinked = tasks.filter((t) => !t.deliverableId);
  if (unlinked.some((t) => t.dueDate)) {
    rows.push({ key: "unlinked", title: "제출물 미연결", href: null, due: null, tasks: unlinked.filter((t) => t.dueDate), undated: unlinked.filter((t) => !t.dueDate).length });
  }

  // 오늘과 모든 날짜를 포함하는 범위
  const today = parseLocalDate(toYmd(new Date()));
  const dates = rows.flatMap((r) => [r.due, ...r.tasks.map((t) => t.dueDate!)]).filter(Boolean) as string[];
  const times = [today.getTime(), ...dates.map((s) => parseLocalDate(s).getTime())];
  const start = addDays(new Date(Math.min(...times)), -PAD_DAYS);
  const end = addDays(new Date(Math.max(...times)), PAD_DAYS);
  const totalDays = Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1;
  const x = (ymd: string) => Math.round((parseLocalDate(ymd).getTime() - start.getTime()) / 86_400_000) * DAY + DAY / 2;
  const todayX = x(toYmd(today));
  const days = Array.from({ length: totalDays }, (_, i) => addDays(start, i));

  // 처음 열면 오늘이 보이도록
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollLeft = Math.max(0, todayX - (el.clientWidth - labelW) / 3);
  }, [todayX, labelW]);

  return (
    <div className="rounded-xl border border-bb-border bg-bb-surface">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-bb-border px-4 py-2 text-[11px] text-bb-text2">
        <span className="inline-flex items-center gap-1"><span className="h-2.5 w-2.5 rotate-45 bg-amber-400" /> 제출 기한</span>
        {(["TODO", "IN_PROGRESS", "DONE"] as const).map((s) => (
          <span key={s} className="inline-flex items-center gap-1"><span className={`h-2 w-2 rounded-full ${DOT[s]}`} /> 업무 {TASK_STATUS_TEXT[s]}</span>
        ))}
        <span className="inline-flex items-center gap-1"><span className="h-3 w-px bg-red-400" /> 오늘</span>
        <span className="ml-auto">업무에는 시작일이 없어 마감일만 표시합니다</span>
      </div>

      <div ref={scrollRef} className="overflow-x-auto" data-testid="timeline-scroll">
        {/* 기간이 짧아도 줄이 화면 끝까지 이어지게 */}
        <div className="relative" style={{ width: labelW + totalDays * DAY, minWidth: "100%" }}>
          {/* 날짜 머리줄 */}
          <div className="sticky top-0 z-10 flex border-b border-bb-border bg-bb-surface text-[10px] text-bb-text2">
            <div className="sticky left-0 z-20 flex shrink-0 items-end border-r border-bb-border bg-bb-surface px-3 py-1" style={{ width: labelW }}>제출물</div>
            {days.map((d) => {
              const first = d.getDate() === 1 || d.getTime() === start.getTime();
              const weekend = d.getDay() === 0 || d.getDay() === 6;
              return (
                <div key={d.getTime()} className={`shrink-0 py-1 text-center leading-tight ${weekend ? "text-bb-text2/50" : ""}`} style={{ width: DAY }}>
                  <span className="block h-3 whitespace-nowrap font-medium text-bb-text">{first ? `${d.getMonth() + 1}월` : ""}</span>
                  {d.getDate()}
                </div>
              );
            })}
          </div>

          {/* 오늘 세로선 */}
          <div className="pointer-events-none absolute bottom-0 top-0 w-px bg-red-400/70" style={{ left: labelW + todayX }} aria-hidden="true" />

          {rows.map((r) => (
            <div key={r.key} className="relative flex border-b border-bb-border/60 last:border-0" data-testid={`timeline-row-${r.key}`} style={{ height: 52 }}>
              <div className="sticky left-0 z-10 flex shrink-0 flex-col justify-center border-r border-bb-border bg-bb-surface px-3" style={{ width: labelW }}>
                {r.href ? (
                  <Link href={r.href} className="truncate text-sm font-medium text-bb-text hover:text-bb-primary">{r.title}</Link>
                ) : (
                  <span className="truncate text-sm text-bb-text2">{r.title}</span>
                )}
                <span className="truncate text-[11px] text-bb-text2">
                  {r.due && <span className={DUE_TEXT[dueTone(r.due)]}>{dueLabel(r.due)}</span>}
                  {r.due && " · "}업무 {r.tasks.length + r.undated}개{r.undated > 0 && ` (기한 없음 ${r.undated})`}
                </span>
              </div>
              <div className="relative flex-1">
                {r.due && (
                  <span
                    role="img"
                    aria-label={`${r.title} 제출 기한 ${shortDate(r.due)}`}
                    title={`제출 기한 ${shortDate(r.due)} (${dueLabel(r.due)})`}
                    className="absolute top-1/2 h-3 w-3 -translate-x-1/2 -translate-y-1/2 rotate-45 bg-amber-400"
                    style={{ left: x(r.due) }}
                  />
                )}
                {r.tasks.map((t, i) => {
                  const late = t.status !== "DONE" && daysLeft(t.dueDate!) < 0;
                  // 같은 날 업무가 겹치면 위아래로 조금씩 벌린다
                  const sameDay = r.tasks.slice(0, i).filter((o) => o.dueDate === t.dueDate).length;
                  return (
                    <Link
                      key={t.id}
                      href={`/projects/${projectId}/board?task=${t.id}`}
                      aria-label={`업무 ${t.title}, ${shortDate(t.dueDate!)} 마감, ${TASK_STATUS_TEXT[t.status]}${late ? ", 기한 지남" : ""}`}
                      title={`${t.title} · ${shortDate(t.dueDate!)} · ${TASK_STATUS_TEXT[t.status]}`}
                      className={`absolute h-2.5 w-2.5 -translate-x-1/2 rounded-full ring-2 ${late ? "ring-red-400" : "ring-bb-surface"} ${DOT[t.status]} hover:scale-150 focus-visible:scale-150 transition-transform`}
                      style={{ left: x(t.dueDate!), top: 14 + (sameDay % 3) * 9 }}
                    />
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
