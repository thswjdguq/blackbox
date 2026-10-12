"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import type { Task } from "@/types/task";

export interface TaskResource { taskId: string; taskTitle: string; file: {
  id: string; fileName: string; version: number; fileSize: number; uploaderName: string; uploadedAt: string;
} }

export default function TaskResources({ task, readOnly, uploadLocked = false, onBusy, onUploaded, onFilesChanged }: {
  task: Task; readOnly: boolean; uploadLocked?: boolean; onBusy: (busy: boolean) => void; onUploaded: () => void; onFilesChanged?: (files: TaskResource[]) => void;
}) {
  const path = `/projects/${task.projectId}/tasks/${task.id}`;
  const [files, setFiles] = useState<TaskResource[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const input = useRef<HTMLInputElement>(null);
  const load = useCallback(async () => {
    try { const { data } = await api.get<TaskResource[]>(`${path}/files`); setFiles(data); setError(""); }
    catch (e) { setError(apiError(e, "결과 파일을 불러오지 못했습니다.")); }
    finally { setLoading(false); }
  }, [path]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => { onFilesChanged?.(files); }, [files, onFilesChanged]);
  const upload = async (file: File) => {
    setBusy(true); onBusy(true); setError(""); setNotice("");
    try {
      const body = new FormData(); body.append("file", file);
      const { data } = await api.post<TaskResource>(`${path}/files`, body, { headers: { "Content-Type": "multipart/form-data" } });
      setFiles(prev => [data, ...prev]); onUploaded();
      setNotice("파일을 등록했습니다. 의견을 받거나 작업을 마친 뒤 완료를 요청하세요.");
    } catch (e) { setError(apiError(e, "파일을 등록하지 못했습니다.")); }
    finally { if (input.current) input.current.value = ""; setBusy(false); onBusy(false); }
  };
  return <section className="space-y-3">
    <div className="flex items-center justify-between"><h3 className="font-semibold text-sm">결과 파일</h3><span className="text-xs text-bb-text2">{task.attachmentRequired ? "첨부 필수" : "필요한 경우 첨부"}</span></div>
    {!readOnly && !uploadLocked && <label className="block rounded-xl border border-dashed border-bb-border bg-bb-bg p-4 text-sm">
      작업 결과 또는 수정본 추가
      <input ref={input} type="file" disabled={busy} className="block w-full mt-2 text-sm" onChange={e => { const file = e.target.files?.[0]; if (file) upload(file); }} />
    </label>}
    {error && <p role="alert" className="text-sm text-red-600">{error} <button type="button" disabled={busy} onClick={load} className="underline">다시 조회</button></p>}
    {notice && <p role="status" className="text-sm text-bb-primary">{notice}</p>}
    {loading ? <p className="text-sm text-bb-text2">파일을 불러오는 중…</p> : !files.length ? <p className="text-sm text-bb-text2">등록된 결과 파일이 없습니다.</p> :
      <ul className="space-y-2">{files.map(({ file }) => <li key={file.id} className="border border-bb-border rounded-lg p-3">
        <a href={`/api/files/${file.id}/download`} className="text-sm text-bb-primary break-all underline">{file.fileName}</a>
        <p className="text-xs text-bb-text2 mt-1">버전 {file.version} · {file.uploaderName} · {new Date(file.uploadedAt).toLocaleString("ko-KR")}</p>
      </li>)}</ul>}
    {task.deliverableId && <Link href={`/projects/${task.projectId}/deliverables/${task.deliverableId}`} className="inline-block text-xs text-bb-primary underline">
      {task.deliverableTitle ?? "연결된 제출물"}에서 최종 취합·검토 보기 →
    </Link>}
  </section>;
}
