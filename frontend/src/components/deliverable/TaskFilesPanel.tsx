"use client";
import { useCallback, useEffect, useState } from "react";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import FileDownloadLink from "@/components/FileDownloadLink";
import type { TaskResource } from "@/components/kanban/TaskResources";

export default function TaskFilesPanel({ projectId, deliverableId, onOpenTask }: { projectId: string; deliverableId: string; onOpenTask: (id: string) => void }) {
  const [items, setItems] = useState<TaskResource[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const load = useCallback(async () => { try { const { data } = await api.get<TaskResource[]>(`/projects/${projectId}/deliverables/${deliverableId}/task-files`); setItems(data); setError(""); } catch(e) { setError(apiError(e, "업무 자료를 불러오지 못했습니다.")); } finally { setLoading(false); } }, [projectId, deliverableId]);
  useEffect(() => { load(); }, [load]);
  return <section className="border border-bb-border bg-bb-surface rounded-xl p-5 mb-5"><div className="flex justify-between gap-2"><h2 className="font-semibold">업무에서 모인 자료</h2><button type="button" className="text-xs text-bb-primary" onClick={load}>새로고침</button></div><p className="text-xs text-bb-text2 mt-1">각 업무에 등록한 자료입니다. 최종 제출본은 검토·확정 절차에서 지정합니다.</p>
    {error && <p role="alert" className="text-sm text-red-600 mt-3">{error}</p>}
    {loading ? <p className="text-sm text-bb-text2 mt-3">자료를 불러오는 중…</p> : !error && !items.length ? <p className="text-sm text-bb-text2 mt-3">연결된 업무에 아직 자료가 없습니다.</p> : <ul className="mt-3 space-y-2">{items.map(item => <li key={`${item.taskId}-${item.file.id}`} className="flex flex-wrap gap-2 items-center border-b border-bb-border py-2"><FileDownloadLink fileId={item.file.id} fileName={item.file.fileName} className="flex-1 min-w-0 break-all text-sm text-bb-primary underline">{item.file.fileName} · v{item.file.version}</FileDownloadLink><button type="button" onClick={() => onOpenTask(item.taskId)} className="text-xs border border-bb-border rounded px-2 py-1">{item.taskTitle} 열기</button></li>)}</ul>}
  </section>;
}
