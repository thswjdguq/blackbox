"use client";

import { useCallback, useEffect, useState } from "react";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import type { Meeting } from "@/types/meeting";

interface Attendance { userId: string; name: string; email: string; status: string; recordedAt: string | null }
interface Summary { serverTime: string; opensAt: string; lateAfter: string; closesAt: string; attendees: Attendance[] }
const labels: Record<string, string> = { PENDING: "확인 전", PRESENT: "참석", LATE: "지각", ABSENT: "불참", LEGACY: "이전 출석 기록" };
const time = (value: string) => new Date(value).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });

export default function AttendancePanel({ meeting, myUserId, role, onMeetingChanged }: {
  meeting: Meeting; myUserId: string | null; role: string | null; onMeetingChanged: (m: Meeting) => void;
}) {
  const path = `/projects/${meeting.projectId}/meetings/${meeting.id}`;
  const [summary, setSummary] = useState<Summary | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [grace, setGrace] = useState(meeting.attendanceGraceMinutes ?? 10);
  const [windowMinutes, setWindow] = useState(meeting.attendanceWindowMinutes ?? 60);
  const load = useCallback(async () => { try { const { data } = await api.get<Summary>(`${path}/attendance`); setSummary(data); setError(""); } catch (e) { setError(apiError(e, "참석 현황을 불러오지 못했습니다.")); } }, [path]);
  useEffect(() => { load(); const timer = setInterval(load, 30000); return () => clearInterval(timer); }, [load]);
  const act = async (request: () => Promise<unknown>) => {
    setBusy(true); setError(""); try { await request(); await load(); } catch (e) { setError(apiError(e, "참석 기록을 저장하지 못했습니다.")); } finally { setBusy(false); }
  };
  const me = summary?.attendees.find(a => a.userId === myUserId);
  const serverTime = summary ? new Date(summary.serverTime).getTime() : 0;
  const closed = !!summary && serverTime >= new Date(summary.closesAt).getTime();
  const open = !!summary && serverTime >= new Date(summary.opensAt).getTime();
  const writable = role === "LEADER" || role === "MEMBER";
  const recorded = !!me && me.status !== "PENDING" && me.status !== "LEGACY";
  return <section className="bg-bb-surface border border-bb-border rounded-xl p-6 mb-6">
    <h2 className="text-lg font-semibold">회의 참석 현황</h2>
    <p className="text-xs text-bb-text2 mt-1">프로젝트 팀장·팀원이 참석 대상에 자동으로 포함됩니다. 참석 확인은 회의 시작 10분 전부터 가능합니다.</p>
    {error && <p role="alert" className="text-sm text-red-600 mt-3">{error} <button type="button" onClick={load} className="underline">다시 조회</button></p>}
    {!summary && !error && <p className="mt-4 text-sm text-bb-text2">참석 현황을 불러오는 중…</p>}
    {summary && <>
      <p className="text-sm text-bb-text2 my-4">참석 기준: {time(summary.lateAfter)}까지 · 확인 마감: {time(summary.closesAt)}<br />기준 이후 확인은 지각, 마감까지 미확인은 불참으로 표시됩니다.</p>
      {writable && me && <div className="flex flex-wrap items-center gap-3 mb-4">
        <span className="text-sm">내 상태: <strong>{labels[me.status]}</strong></span>
        <button type="button" disabled={busy || !open || closed || recorded} onClick={() => act(() => api.put(`${path}/attendance`, { attending: true }))} className="text-sm bg-bb-primary text-white rounded-lg px-3 py-2 disabled:opacity-40">참석했습니다</button>
        <button type="button" disabled={busy || closed || recorded} onClick={() => act(() => api.put(`${path}/attendance`, { attending: false }))} className="text-sm border border-bb-border rounded-lg px-3 py-2 disabled:opacity-40">불참 예정</button>
        {closed && <span className="text-xs text-bb-text2">확인 마감 · 정정은 팀장에게 요청하세요.</span>}
      </div>}
      <div className="grid sm:grid-cols-2 gap-2">{summary.attendees.map(a => <div key={a.userId} className="flex flex-wrap items-center gap-2 rounded-lg bg-bb-bg p-3">
        <span className="flex-1 text-sm font-medium">{a.name}</span><span className={`text-xs ${a.status === "ABSENT" ? "text-red-600" : a.status === "LATE" ? "text-amber-700" : "text-bb-text2"}`}>{labels[a.status]}</span>
        {role === "LEADER" && serverTime >= new Date(meeting.meetingDate!).getTime() && <select aria-label={`${a.name} 참석 상태 정정`} disabled={busy} value="" onChange={e => { const status = e.target.value; if (status && window.confirm(`${a.name}님의 상태를 ${labels[status]}으로 정정할까요?`)) act(() => api.put(`${path}/attendance/${a.userId}`, { status })); }} className="text-xs bg-bb-surface border border-bb-border rounded p-1"><option value="">정정</option><option value="PRESENT">참석</option><option value="LATE">지각</option><option value="ABSENT">불참</option></select>}
        {a.recordedAt && <span className="w-full text-xs text-bb-text2">기록: {time(a.recordedAt)}</span>}
      </div>)}</div>
    </>}
    {role === "LEADER" && <details className="mt-4 border-t border-bb-border pt-3"><summary className="text-sm cursor-pointer">출석 시간 기준 설정</summary>
      <div className="flex flex-wrap items-end gap-3 mt-3"><label className="text-xs">지각 기준 (시작 후 분)<input type="number" min={0} max={120} value={grace} onChange={e => setGrace(Number(e.target.value))} className="block w-28 border border-bb-border rounded p-2 bg-bb-bg" /></label><label className="text-xs">확인 마감 (시작 후 분)<input type="number" min={grace+1} max={480} value={windowMinutes} onChange={e => setWindow(Number(e.target.value))} className="block w-28 border border-bb-border rounded p-2 bg-bb-bg" /></label>
        <button type="button" disabled={busy || grace < 0 || grace > 120 || windowMinutes <= grace || windowMinutes > 480} onClick={() => act(async () => { const { data } = await api.patch<Meeting>(path, { attendanceGraceMinutes: grace, attendanceWindowMinutes: windowMinutes }); onMeetingChanged(data); })} className="text-sm bg-bb-primary text-white rounded px-3 py-2 disabled:opacity-40">기준 저장</button></div>
      <p className="text-xs text-bb-text2 mt-2">참석 기록이 생기면 일시와 기준을 변경할 수 없습니다. 개별 기록은 정정할 수 있습니다.</p>
    </details>}
  </section>;
}
