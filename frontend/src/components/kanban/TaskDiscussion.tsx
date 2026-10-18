"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import type { Task } from "@/types/task";
import type { TaskMessage } from "@/types/taskMessage";
import type { TaskResource } from "./TaskResources";

export default function TaskDiscussion({ task, members, files, readOnly, focusMessageId, onDraftChange, onBusy }: {
  task: Task; members: { userId: string; name: string }[]; files: TaskResource[]; readOnly: boolean; focusMessageId?: string | null; onDraftChange?: (dirty: boolean) => void; onBusy?: (busy: boolean) => void;
}) {
  const path = `/projects/${task.projectId}/tasks/${task.id}/messages`;
  const [messages, setMessages] = useState<TaskMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [body, setBody] = useState("");
  const [requesting, setRequesting] = useState(false);
  const [recipientId, setRecipientId] = useState("");
  const [fileId, setFileId] = useState("");
  const [replyTo, setReplyTo] = useState<TaskMessage | null>(null);
  const [busy, setBusy] = useState(false);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [myId, setMyId] = useState<string | null>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const sequence = useRef(0);
  const focused = useRef<string | null>(null);
  const focusedMessage = useRef<HTMLLIElement>(null);
  const scrolledMessage = useRef<string | null>(null);
  useEffect(() => {
    if (focusMessageId && focusedMessage.current && scrolledMessage.current !== focusMessageId) {
      scrolledMessage.current = focusMessageId;
      focusedMessage.current.scrollIntoView({ block: "center" });
    }
  }, [focusMessageId, messages]);
  useEffect(() => { onDraftChange?.(!!body.trim()); }, [body,onDraftChange]);
  const load = useCallback(async (olderPage = 0) => {
    const request = ++sequence.current;
    try {
      const { data } = await api.get<{ messages: TaskMessage[]; hasMore: boolean }>(`${path}?page=${olderPage}`);
      if (request !== sequence.current) return;
      // 최신 대화를 갱신해도 이미 펼친 과거 대화는 보존하고 ID로 중복을 제거한다.
      setMessages(previous => {
        const map = new Map(previous.map(m => [m.id, m]));
        data.messages.forEach(m => map.set(m.id, m));
        return Array.from(map.values()).sort((a,b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id));
      });
      if (olderPage > 0) { setPage(olderPage); setHasMore(data.hasMore); }
      else if (page === 0) setHasMore(data.hasMore);
      setError("");
    } catch(e) { if (request === sequence.current) setError(apiError(e,"대화를 불러오지 못했습니다.")); }
    finally { if (request === sequence.current) setLoading(false); }
  }, [path, page]);
  useEffect(() => { load(); return () => { sequence.current++; }; }, [load]);
  useEffect(() => { api.get<{ id: string }>("/auth/profile").then(({data}) => setMyId(data.id)).catch(() => {}); }, []);
  useEffect(() => {
    if (!focusMessageId || !myId || focused.current === focusMessageId) return;
    let active = true;
    // 오래된 미응답 요청이 최신 대화 페이지 밖에 있어도 요청 원문을 바로 연다.
    api.get<TaskMessage>(`${path}/${focusMessageId}`).then(({data}) => {
      if (!active) return;
      focused.current = data.id;
      setMessages(previous => previous.some(m=>m.id===data.id) ? previous : [data,...previous]);
      if (!readOnly && data.kind === "REQUEST" && data.recipientId === myId) { setReplyTo(data); input.current?.focus(); }
    }).catch(e => { if (active) setError(apiError(e,"요청 원문을 불러오지 못했습니다.")); });
    return () => {active=false;};
  }, [focusMessageId,myId,path,readOnly]);
  useEffect(() => {
    const refresh = () => { if (!document.hidden && !busy) load(); };
    const timer = window.setInterval(refresh,15000); window.addEventListener("focus",refresh);
    return () => { window.clearInterval(timer); window.removeEventListener("focus",refresh); };
  }, [load,busy]);
  const send = async (e: React.FormEvent) => {
    e.preventDefault(); if (!body.trim()) return;
    setBusy(true); onBusy?.(true); setError("");
    try {
      await api.post<TaskMessage>(path,{body:body.trim(),recipientId:requesting?recipientId:undefined,parentId:replyTo?.id,fileId:fileId||undefined});
      setBody(""); setRequesting(false); setRecipientId(""); setFileId(""); setReplyTo(null);
      await load(); window.dispatchEvent(new Event("task-feedback-changed"));
    } catch(e) { setError(apiError(e,"내용을 전송하지 못했습니다.")); }
    finally { setBusy(false); onBusy?.(false); }
  };
  const reply = (message: TaskMessage) => { setReplyTo(message); setRequesting(false); setRecipientId(""); input.current?.focus(); };
  return <section id="task-discussion" className="border-t border-bb-border pt-5 space-y-4">
    <div className="flex justify-between items-center"><h3 className="font-semibold text-sm">대화 · 피드백</h3>
      <Link href={`/projects/${task.projectId}/feedback`} className="text-xs text-bb-primary">피드백함 보기 →</Link></div>
    {loading ? <p className="text-sm text-bb-text2">대화를 불러오는 중…</p> : !messages.length && <p className="text-sm text-bb-text2">질문이나 의견을 여기에 남겨주세요.</p>}
    {hasMore && <button type="button" onClick={() => load(page+1)} disabled={busy} className="text-xs underline text-bb-text2">이전 대화 보기</button>}
    <ol className="space-y-3">{messages.map(m => <li id={`message-${m.id}`} key={m.id} ref={m.id === focusMessageId ? focusedMessage : undefined} className={`rounded-xl border p-3 ${m.id === focusMessageId ? "border-bb-primary bg-bb-primary/10" : m.kind === "REQUEST" ? "border-bb-primary/25 bg-bb-primary/5" : "border-bb-border bg-bb-surface"}`}>
      {m.id === focusMessageId && <p className="mb-2 text-xs text-bb-primary">알림·요청에 연결된 대화</p>}
      <div className="flex flex-wrap gap-2 items-center text-xs"><strong>{m.authorName}</strong><time className="text-bb-text2">{new Date(m.createdAt).toLocaleString("ko-KR")}</time>
        {m.kind === "REQUEST" && <span className="text-bb-primary">{m.recipientName}님에게 의견 요청 · {m.answeredAt ? "응답 완료" : "답변 대기"}</span>}
        {m.kind === "REPLY" && <span className="text-bb-text2">{messages.find(p => p.id === m.parentId)?.authorName ?? "의견 요청"}에 대한 답글</span>}</div>
      <p className="mt-2 whitespace-pre-wrap break-words text-sm">{m.body}</p>
      {m.fileId && <a href={`/api/files/${m.fileId}/download`} className="inline-block mt-2 text-xs text-bb-primary underline">{m.fileName} · 버전 {m.fileVersion}</a>}
      {!readOnly && m.kind === "REQUEST" && <button type="button" disabled={busy} onClick={() => reply(m)} className="block mt-2 text-xs font-medium text-bb-primary">답글 작성</button>}
    </li>)}</ol>
    {error && <p role="alert" className="text-sm text-red-600">{error} <button type="button" onClick={() => load()} className="underline">다시 조회</button></p>}
    {!readOnly && <form onSubmit={send} className="rounded-xl border border-bb-border p-3 space-y-3">
      {replyTo ? <div className="text-xs flex justify-between gap-2"><span>{replyTo.authorName}님의 요청에 답변: {replyTo.body.slice(0,60)}</span><button type="button" onClick={() => setReplyTo(null)}>취소</button></div> :
        <div className="flex gap-2"><button type="button" disabled={busy} onClick={() => setRequesting(false)} className={`px-3 py-1.5 text-xs rounded-lg ${!requesting ? "bg-bb-primary/10 text-bb-primary" : "text-bb-text2"}`}>댓글</button>
          {task.status !== "DONE" && <button type="button" disabled={busy} onClick={() => setRequesting(true)} className={`px-3 py-1.5 text-xs rounded-lg ${requesting ? "bg-bb-primary/10 text-bb-primary" : "text-bb-text2"}`}>팀원에게 의견 요청</button>}</div>}
      {requesting && <select aria-label="의견을 요청할 팀원" required value={recipientId} disabled={busy} onChange={e => setRecipientId(e.target.value)} className="w-full rounded-lg border border-bb-border bg-bb-bg p-2 text-sm">
        <option value="">의견을 요청할 팀원 선택</option>{members.filter(m => m.userId !== myId).map(m => <option key={m.userId} value={m.userId}>{m.name}</option>)}</select>}
      <textarea ref={input} aria-label="댓글 또는 의견 요청 내용" value={body} disabled={busy} maxLength={2000} onChange={e => setBody(e.target.value)} placeholder={replyTo ? "요청에 대한 의견을 적어주세요" : requesting ? "어떤 부분을 봐주면 좋을지 적어주세요" : "질문이나 의견을 남겨주세요"} className="w-full min-h-24 rounded-lg border border-bb-border bg-bb-bg p-3 text-sm" />
      <div className="flex flex-wrap items-center justify-between gap-2">
        {files.length > 0 ? <select aria-label="대화에 연결할 결과 파일" value={fileId} disabled={busy} onChange={e => setFileId(e.target.value)} className="max-w-full text-xs p-2 rounded-lg bg-bb-bg border border-bb-border"><option value="">파일 연결 (선택)</option>{files.map(({file}) => <option key={file.id} value={file.id}>{file.fileName} · v{file.version}</option>)}</select> : <span />}
        <button type="submit" disabled={busy || !body.trim() || (requesting && !recipientId)} className="rounded-lg bg-bb-primary text-white px-4 py-2 text-sm disabled:opacity-40">{busy ? "전송 중…" : replyTo ? "답변 보내기" : requesting ? "의견 요청 보내기" : "댓글 등록"}</button>
      </div>
      {requesting && <p className="text-xs text-bb-text2">의견을 기다리는 동안에도 업무를 계속 수정할 수 있습니다.</p>}
    </form>}
    {readOnly && <p className="text-xs text-bb-text2">관찰자는 대화를 볼 수만 있습니다.</p>}
  </section>;
}
