"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { ChevronRight, ClipboardCheck, RefreshCw } from "lucide-react";
import api from "@/lib/api";
import type { Task } from "@/types/task";

interface ProjectRef {
  id: string;
  name: string;
  myRole: string | null;
}

type Item = Task & { projectName: string };

function ago(iso: string, now = Date.now()): string {
  const min = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60000));
  if (min < 1) return "방금";
  if (min < 60) return `${min}분 전`;
  const h = Math.round(min / 60);
  if (h < 24) return `${h}시간 전`;
  return `${Math.round(h / 24)}일 전`;
}

/**
 * 모든 프로젝트에서 내가 확인해 줄 수 있는 결과 확인 요청을 모은다(업무 흐름 개선 3번).
 * 팀장·팀원인 프로젝트의 확인 요청 중 업무 가운데 내가 담당이 아닌 것만 추린 뒤,
 * 보드의 "내가 검토할 업무"와 같이 업무마다 서버의 canReview로 확정한다(한 번에 4개씩).
 * 요청이 없으면 아무것도 그리지 않는다. 일부를 못 불러오면 보이는 것은 두고 다시 시도를 둔다.
 */
export default function ReviewQueue({ projects }: { projects: ProjectRef[] }) {
  const [items, setItems] = useState<Item[] | null>(null);
  const [failed, setFailed] = useState(false);

  const load = useCallback(async () => {
    setFailed(false);
    const contributing = projects.filter((p) => p.myRole === "LEADER" || p.myRole === "MEMBER");
    if (contributing.length === 0) { setItems([]); return; }
    let myId: string;
    try {
      myId = (await api.get<{ id: string }>("/auth/profile")).data.id;
    } catch {
      setFailed(true); setItems([]); return;
    }
    const lists = await Promise.allSettled(
      contributing.map((p) => api.get<Task[]>(`/projects/${p.id}/tasks`).then((r) => r.data.map((t) => ({ ...t, projectName: p.name })))),
    );
    let miss = lists.some((r) => r.status === "rejected");
    const candidates = lists.flatMap((r) => (r.status === "fulfilled" ? r.value : []))
      .filter((t) => t.status !== "DONE" && t.completionRequestedAt && !t.assignees.some((a) => a.userId === myId));
    const found: Item[] = [];
    for (let i = 0; i < candidates.length; i += 4) {
      const checked = await Promise.allSettled(candidates.slice(i, i + 4).map((t) =>
        api.get<{ canReview: boolean }>(`/projects/${t.projectId}/tasks/${t.id}/completion`).then(({ data }) => (data.canReview ? t : null))));
      for (const c of checked) {
        if (c.status === "rejected") miss = true;
        else if (c.value) found.push(c.value);
      }
    }
    // 오래 기다린 요청부터
    found.sort((a, b) => (a.completionRequestedAt ?? "").localeCompare(b.completionRequestedAt ?? ""));
    setItems(found);
    setFailed(miss);
  }, [projects]);

  useEffect(() => {
    if (projects.length > 0) load();
  }, [projects, load]);

  if (!items || (items.length === 0 && !failed)) return null;

  return (
    <section className="mb-8" aria-labelledby="review-queue-title">
      <h2 id="review-queue-title" className="mb-3 flex items-center gap-2 text-sm font-semibold uppercase tracking-wider text-bb-text2">
        <ClipboardCheck size={14} aria-hidden="true" />
        확인할 결과
        {items.length > 0 && <span className="rounded-full bg-bb-primary px-2 py-0.5 text-xs font-bold normal-case tracking-normal text-white">{items.length}</span>}
      </h2>
      <div className="divide-y divide-bb-border rounded-xl border border-bb-border bg-bb-surface">
        {failed && (
          <div role="alert" className="flex flex-wrap items-center gap-3 px-4 py-3 text-xs text-amber-600">
            일부 프로젝트의 확인 요청을 불러오지 못했습니다.
            <button onClick={load} className="inline-flex items-center gap-1 underline"><RefreshCw size={12} aria-hidden="true" /> 다시 시도</button>
          </div>
        )}
        {items.length > 0 && (
          <ul className="px-2 py-2">
            {items.map((t) => (
              <li key={t.id}>
                <Link href={`/projects/${t.projectId}/board?task=${t.id}`} className="group flex min-h-[52px] items-center gap-3 rounded-lg px-2 py-2 hover:bg-bb-surface2">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-bb-text">{t.title}</span>
                    <span className="block truncate text-xs text-bb-text2">
                      {t.projectName} · {t.completionRequestedByName ?? "팀원"}님이 {t.completionRequestedAt ? ago(t.completionRequestedAt) : ""} 요청
                    </span>
                  </span>
                  <span className="shrink-0 text-xs font-semibold text-bb-primary">결과 확인</span>
                  <ChevronRight size={14} aria-hidden="true" className="shrink-0 text-bb-text2" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
