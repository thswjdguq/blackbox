"use client";
import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import ProjectNav from "@/components/project/ProjectNav";
import TaskModal from "@/components/kanban/TaskModal";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import { sessionGeneration } from "@/lib/sessionCache";
import { useNotificationStore } from "@/lib/store/notificationStore";
import { notificationLabels, type PersonalNotification } from "@/types/notification";
import type { Task } from "@/types/task";

type Member = { userId: string; name: string; email: string; role: string };
export default function NotificationsPage() {
  const projectId = useParams()?.projectId as string;
  return <div className="min-h-screen bg-bb-bg"><Sidebar /><main className="md:ml-64 mt-14 md:mt-0 px-4 md:px-8 py-6 max-w-[1440px]">
    <ProjectNav projectId={projectId} /><Inbox key={projectId} projectId={projectId} />
  </main></div>;
}
function Inbox({ projectId }: { projectId: string }) {
  const { projects, load, read } = useNotificationStore();
  const entry = projects[projectId];
  const [selected, setSelected] = useState<{ task: Task; item: PersonalNotification; members: Member[]; readOnly: boolean } | null>(null);
  const [opening, setOpening] = useState(false), [openError, setOpenError] = useState("");
  const active = useRef(true), openingLock = useRef(false);
  useEffect(() => { active.current = true; void load(projectId); return () => { active.current = false; }; }, [projectId, load]);
  const open = async (item: PersonalNotification) => {
    // 읽음 요청을 기다리지 않으므로 네트워크 오류가 업무 열기를 막지 않는다.
    if (!item.readAt) void read(projectId, item.id);
    if (!item.targetAvailable || openingLock.current) return;
    openingLock.current = true; setOpening(true); setOpenError("");
    const generation = sessionGeneration();
    try {
      const [task, members, profile] = await Promise.all([
        api.get<Task>(`/projects/${projectId}/tasks/${item.taskId}`),
        api.get<Member[]>(`/projects/${projectId}/members`), api.get<{ id: string }>("/auth/profile"),
      ]);
      if (!active.current || generation !== sessionGeneration()) return;
      const role = members.data.find(m => m.userId === profile.data.id)?.role;
      setSelected({ task: task.data, item, members: members.data.filter(m => m.role !== "OBSERVER"), readOnly: role !== "LEADER" && role !== "MEMBER" });
    } catch (e) { if (active.current && generation === sessionGeneration()) setOpenError(apiError(e, "업무를 열지 못했습니다. 다시 시도해 주세요.")); }
    finally { openingLock.current = false; if (active.current) setOpening(false); }
  };
  const settingsOnly = async () => { throw new Error("업무 화면에서 설정을 수정하세요."); };
  return <>
    <div className="mt-6 flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-bold">새 소식</h1>
      <p className="mt-2 text-sm text-bb-text2">팀원이 올린 결과와 나에게 온 요청을 확인하세요. 읽음 표시는 업무 완료와 별개입니다.</p></div>
      <button type="button" disabled={entry?.loading} onClick={() => load(projectId)} className="rounded-lg border border-bb-border px-3 py-2 text-sm disabled:opacity-50">{entry?.loading ? "불러오는 중…" : "새로고침"}</button></div>
    {(entry?.error || openError) && <p role="alert" className="mt-4 text-sm text-red-600">{entry?.error || openError}</p>}
    {entry?.readError && <div role="alert" className="mt-4 text-sm text-amber-700">{entry.readError} {entry.failedReadId && <button type="button" onClick={() => read(projectId, entry.failedReadId!)} className="underline">읽음 처리 다시 시도</button>}</div>}
    {!entry?.page && !entry?.error && <p role="status" className="mt-8 text-sm text-bb-text2">알림을 불러오는 중…</p>}
    {entry?.page && <div className="mt-5 space-y-3">
      {!entry.page.items.length && <p className="rounded-xl border border-bb-border p-8 text-center text-sm text-bb-text2">아직 받은 새 소식이 없습니다.</p>}
      {entry.page.items.map(item => <article key={item.id} className={`rounded-xl border p-4 ${item.readAt ? "border-bb-border bg-bb-surface" : "border-bb-primary/30 bg-bb-primary/5"}`}>
        <div className="flex flex-wrap justify-between gap-2 text-xs text-bb-text2"><span>{notificationLabels[item.type] ?? "업무 소식"} · {item.readAt ? "읽음" : "읽지 않음"}</span><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString("ko-KR")}</time></div>
        <h2 className="mt-2 break-words font-semibold text-sm">{item.taskTitle}</h2><p className="mt-1 text-sm text-bb-text2">{item.actorName}님 · {notificationLabels[item.type] ?? "업무 소식"}</p>
        {item.fileName && <p className="mt-2 break-all text-sm">{item.fileName} · 버전 {item.fileVersion}</p>}
        {item.targetAvailable ? <button type="button" disabled={opening} onClick={() => open(item)} className="mt-3 rounded-lg border border-bb-border px-3 py-2 text-sm text-bb-primary disabled:opacity-50">관련 업무 열기</button> : <div className="mt-3 text-sm text-bb-text2">삭제된 업무입니다. {!item.readAt && <button type="button" onClick={() => read(projectId, item.id)} className="underline">읽음으로 표시</button>}</div>}
      </article>)}
      {entry.page.nextCursor && <button type="button" disabled={entry.loading} onClick={() => load(projectId, true)} className="rounded-lg border border-bb-border px-4 py-2 text-sm disabled:opacity-50">이전 알림 더 보기</button>}
    </div>}
    {selected && <TaskModal key={`${selected.task.id}:${selected.item.id}`} mode="edit" task={selected.task} members={selected.members} readOnly={selected.readOnly} workOnly
      focusMessageId={selected.item.messageId} focusFileId={selected.item.fileId}
      onClose={() => setSelected(null)} onCreate={settingsOnly} onUpdate={settingsOnly} onDelete={settingsOnly}
      onWorkflowChanged={task => { setSelected(value => value ? { ...value, task } : value); window.dispatchEvent(new Event("task-feedback-changed")); }} />}
  </>;
}
