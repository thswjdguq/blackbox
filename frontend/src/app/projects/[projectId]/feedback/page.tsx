"use client";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { MessageSquare, ArrowUpRight, RefreshCw } from "lucide-react";
import Sidebar from "@/components/Sidebar";
import ProjectNav from "@/components/project/ProjectNav";
import TaskModal from "@/components/kanban/TaskModal";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import { useFeedbackInbox } from "@/hooks/useFeedbackInbox";
import type { Task } from "@/types/task";
import type { FeedbackItem } from "@/types/taskMessage";

type Member = {userId:string; name:string; email:string; role:string};
const tabs = [{key:"received",label:"받은 요청"},{key:"sent",label:"보낸 요청"},{key:"revisions",label:"보완할 업무"}] as const;
export default function FeedbackPage() {
  const projectId = useParams()?.projectId as string;
  const {data,error,loading,refresh} = useFeedbackInbox(projectId);
  const [tab,setTab] = useState<typeof tabs[number]["key"]>("received");
  const [members,setMembers] = useState<Member[]>([]);
  const [readOnly,setReadOnly] = useState(true);
  const [ready,setReady] = useState(false);
  const [selected,setSelected] = useState<Task|null>(null);
  const [messageId,setMessageId] = useState<string|null>(null);
  const [opening,setOpening] = useState<string|null>(null);
  const [openError,setOpenError] = useState("");
  const [memberError,setMemberError] = useState("");
  useEffect(() => {
    let active = true;
    Promise.all([api.get<Member[]>(`/projects/${projectId}/members`),api.get<{id:string}>("/auth/profile")])
      .then(([people,profile]) => { if (!active) return; setMembers(people.data); setReadOnly(people.data.find(m=>m.userId===profile.data.id)?.role === "OBSERVER"); setReady(true); })
      .catch(e => { if(active) setMemberError(apiError(e,"팀원 정보를 불러오지 못했습니다.")); });
    return () => {active=false;};
  }, [projectId]);
  const open = async (item:FeedbackItem) => {
    setOpening(item.taskId); setOpenError("");
    try { const {data:task} = await api.get<Task>(`/projects/${projectId}/tasks/${item.taskId}`); setMessageId(item.messageId); setSelected(task); }
    catch(e) { setOpenError(apiError(e,"업무를 열지 못했습니다.")); }
    finally { setOpening(null); }
  };
  // 이 입구에서는 결과·대화만 다룬다. 업무 설정 편집은 보드의 기존 저장 흐름으로 이동한다.
  const settingsOnly = async () => { throw new Error("업무 화면에서 설정을 수정하세요."); };
  return <div className="min-h-screen bg-bb-bg"><Sidebar />
    <main className="md:ml-64 mt-14 md:mt-0 px-4 md:px-8 py-6 max-w-[1440px]">
      <ProjectNav projectId={projectId} />
      <div className="flex justify-between items-center mt-6 gap-3"><div><h1 className="text-2xl font-bold">피드백함</h1><p className="mt-2 text-sm text-bb-text2">답할 요청과 보완할 업무를 확인하세요.</p></div>
        <button type="button" onClick={refresh} disabled={loading} aria-label="피드백함 새로고침" className="p-2 rounded-lg border border-bb-border bg-bb-surface"><RefreshCw size={17} className={loading ? "animate-spin" : ""}/></button></div>
      <div role="tablist" aria-label="피드백 요청 분류" className="flex flex-wrap gap-2 mt-6">{tabs.map(t=><button key={t.key} role="tab" aria-selected={t.key===tab} onClick={()=>setTab(t.key)} className={`px-4 py-2.5 rounded-lg text-sm ${t.key===tab ? "bg-bb-primary text-white" : "bg-bb-surface border border-bb-border text-bb-text2"}`}>{t.label} {data?.[t.key].length ?? ""}</button>)}</div>
      {(error||openError||memberError) && <p role="alert" className="mt-4 text-sm text-red-600">{error||openError||memberError}</p>}
      {selected && <Link href={`/projects/${projectId}/board?task=${selected.id}`} className="inline-flex mt-4 text-xs text-bb-primary underline">현재 업무의 설정 수정 →</Link>}
      {!data && loading ? <p className="mt-8 text-sm text-bb-text2">요청을 불러오는 중…</p> : data && <div role="tabpanel" className="mt-4 space-y-3">
        {!data[tab].length && <div className="rounded-xl border border-bb-border bg-bb-surface py-14 text-center"><MessageSquare size={28} className="mx-auto text-bb-text2 mb-3"/><p className="text-sm">{tab==="received" ? "지금 답할 요청이 없습니다." : tab==="sent" ? "응답을 기다리는 요청이 없습니다." : "보완 요청을 받은 업무가 없습니다."}</p><Link href={`/projects/${projectId}/board`} className="inline-block mt-3 text-xs text-bb-primary">업무 보기 →</Link></div>}
        {data[tab].map(item=><button type="button" key={`${item.kind}-${item.messageId??item.taskId}`} onClick={()=>open(item)} disabled={!ready||!!opening} className="w-full text-left border border-bb-border bg-bb-surface rounded-xl p-4 hover:border-bb-primary/50 transition-colors disabled:opacity-50">
          <div className="flex justify-between gap-3"><span className={`text-xs font-medium rounded-md px-2 py-1 ${item.kind==="OPINION" ? "bg-indigo-50 text-indigo-700" : item.kind==="COMPLETION" ? "bg-teal-50 text-teal-700" : "bg-amber-50 text-amber-700"}`}>{item.kind==="OPINION" ? "의견 요청" : item.kind==="COMPLETION" ? "완료 확인 요청" : "보완 요청"}</span><ArrowUpRight size={16} className="text-bb-text2"/></div>
          <h2 className="font-semibold mt-3 text-sm">{item.taskTitle}</h2><p className="text-sm text-bb-text2 mt-1 whitespace-pre-wrap line-clamp-2">{item.body}</p>
          <p className="text-xs text-bb-text2 mt-3">{item.fromName ? `${item.fromName} · ` : ""}{new Date(item.createdAt).toLocaleString("ko-KR")} · {opening===item.taskId ? "여는 중…" : item.kind==="OPINION" ? "업무에서 답변하기" : "업무 열기"}</p>
        </button>)}
      </div>}
      {selected && <TaskModal key={selected.id} mode="edit" task={selected} members={members.filter(m=>m.role!=="OBSERVER")} readOnly={readOnly} workOnly focusMessageId={messageId}
        onClose={()=>{setSelected(null);refresh();}} onCreate={settingsOnly} onUpdate={settingsOnly} onDelete={settingsOnly}
        onWorkflowChanged={task=>{setSelected(task);window.dispatchEvent(new Event("task-feedback-changed"));}} />}
    </main>
  </div>;
}
