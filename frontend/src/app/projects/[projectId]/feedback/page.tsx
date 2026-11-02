"use client";
import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowUpRight, RefreshCw, ChevronRight } from "lucide-react";
import Sidebar from "@/components/Sidebar";
import ProjectNav from "@/components/project/ProjectNav";
import TaskModal from "@/components/kanban/TaskModal";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import { useFeedbackInbox } from "@/hooks/useFeedbackInbox";
import type { Task } from "@/types/task";
import type { FeedbackItem } from "@/types/taskMessage";
import { isFeedbackFinished } from "@/lib/feedback";

type Member = {userId:string; name:string; email:string; role:string};
// 고칠 내용(revisions)은 받은 피드백의 일부라 따로 탭을 두지 않는다. 받은 피드백에 없는 보완 요청만 합쳐 보여 준다.
const tabs = [
  {key:"received",label:"받은 피드백",hint:"팀원이 나에게 남긴 의견·요청·보완 요청과 그 기록입니다. 진행 중인 항목부터 확인하고, 완료된 기록은 아래에서 펼쳐 볼 수 있습니다."},
  {key:"sent",label:"보낸 피드백",hint:"내가 남긴 피드백의 진행 상태를 확인하세요. 완료된 기록은 아래에 모았습니다."},
] as const;
const itemKey = (i:FeedbackItem) => `${i.kind}-${i.messageId??i.taskId}`;
const labels: Record<string,string> = {COMMENT:"업무 피드백",OPINION:"의견 요청",COMPLETION:"완료 확인 요청",REVISION:"보완 요청",REVIEW:"최종 제출물 피드백"};
const statuses: Record<string,string> = {OPEN:"확인·응답 대기",RESPONDED:"답글 있음",ANSWERED:"응답 완료",RESOLVED:"반영 기록 있음",REFLECTION_PENDING:"반영 확인 대기"};
export default function FeedbackPage() {
  const router=useRouter();
  const projectId = useParams()?.projectId as string;
  const {data,error,loading,refresh} = useFeedbackInbox(projectId);
  const [tab,setTab] = useState<typeof tabs[number]["key"]>("received");
  const lists = data && {
    received: [...data.received, ...data.revisions.filter(r => !data.received.some(i => itemKey(i)===itemKey(r)))]
      .sort((a,b) => (b.createdAt??"").localeCompare(a.createdAt??"")),
    sent: data.sent,
  };
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
    if (item.deliverableId) {
      router.push(`/projects/${projectId}/deliverables/${item.deliverableId}?tab=review#comment-${item.messageId}`);
      return;
    }
    setOpening(item.taskId); setOpenError("");
    try { const {data:task} = await api.get<Task>(`/projects/${projectId}/tasks/${item.taskId}`); setMessageId(item.messageId); setSelected(task); }
    catch(e) { setOpenError(apiError(e,"업무를 열지 못했습니다.")); }
    finally { setOpening(null); }
  };
  // 이 입구에서는 결과·대화만 다룬다. 업무 설정 편집은 보드의 기존 저장 흐름으로 이동한다.
  const settingsOnly = async () => { throw new Error("업무 화면에서 설정을 수정하세요."); };
  const activeItems = lists?.[tab].filter(item => !isFeedbackFinished(item)) ?? [];
  const finishedItems = lists?.[tab].filter(isFeedbackFinished) ?? [];
  const renderCard = (item: FeedbackItem) => (<button type="button" key={itemKey(item)} onClick={()=>open(item)} disabled={!ready||!!opening} className="w-full text-left border border-bb-border bg-bb-surface rounded-xl p-4 hover:border-bb-primary/50 transition-colors disabled:opacity-50">
          <div className="flex justify-between gap-3"><span className="text-xs font-medium rounded-md px-2 py-1 bg-bb-primary/10 text-bb-primary">{labels[item.kind]} · {statuses[item.status??"OPEN"]??item.status}</span><ArrowUpRight size={16} className="text-bb-text2"/></div>
          <h2 className="font-semibold mt-3 text-sm">{item.taskTitle}</h2><p className="text-sm text-bb-text2 mt-1 whitespace-pre-wrap line-clamp-2">{item.body}</p>
          {item.response && <p className="mt-3 border-l-2 border-bb-primary pl-3 text-sm whitespace-pre-wrap line-clamp-3">답변·반영 기록: {item.response}</p>}
          <p className="text-xs text-bb-text2 mt-3">{item.fromName ? `${item.fromName} · ` : ""}{new Date(item.createdAt).toLocaleString("ko-KR")} · {opening!==null && opening===item.taskId ? "여는 중…" : item.deliverableId ? "최종 제출물 열기" : isFeedbackFinished(item) ? "내용 보기" : item.kind==="OPINION" ? "업무에서 답변하기" : "업무 열기"}</p>
        </button>);
  return <div className="min-h-screen bg-bb-bg"><Sidebar />
    <main className="md:ml-64 mt-14 md:mt-0 px-4 md:px-8 py-6 max-w-[1440px]">
      <ProjectNav projectId={projectId} />
      <div className="flex justify-between items-center mt-6 gap-3"><div><h1 className="text-2xl font-bold">피드백</h1><p className="mt-2 text-sm text-bb-text2">업무와 최종 제출물의 피드백, 답변·반영 기록을 함께 확인하세요.</p></div>
        <button type="button" onClick={refresh} disabled={loading} aria-label="피드백 새로고침" className="p-2 rounded-lg border border-bb-border bg-bb-surface"><RefreshCw size={17} className={loading ? "animate-spin" : ""}/></button></div>
      <div role="tablist" aria-label="피드백 요청 분류" className="flex flex-wrap gap-2 mt-6">{tabs.map(t=><button key={t.key} role="tab" aria-selected={t.key===tab} onClick={()=>setTab(t.key)} className={`px-4 py-2.5 rounded-lg text-sm ${t.key===tab ? "bg-bb-primary text-white" : "bg-bb-surface border border-bb-border text-bb-text2"}`}>{t.label} {lists?.[t.key].length ?? ""}</button>)}</div>
      <p className="mt-3 text-xs text-bb-text2">{tabs.find(t=>t.key===tab)?.hint}</p>
      {(error||openError||memberError) && <p role="alert" className="mt-4 text-sm text-red-600">{error||openError||memberError}</p>}
      {selected && <Link href={`/projects/${projectId}/board?task=${selected.id}`} className="inline-flex mt-4 text-xs text-bb-primary underline">현재 업무의 설정 수정 →</Link>}
      {!lists && loading ? <p className="mt-8 text-sm text-bb-text2">요청을 불러오는 중…</p> : lists && <div role="tabpanel" className="mt-4 space-y-3">
        <section aria-labelledby="active-feedback-heading" className="space-y-3">
          <h2 id="active-feedback-heading" className="text-sm font-semibold text-bb-text">진행 중인 피드백 {activeItems.length}</h2>
          {activeItems.length ? activeItems.map(renderCard) : <p className="rounded-xl border border-bb-border bg-bb-surface px-4 py-8 text-center text-sm text-bb-text2">진행 중인 피드백이 없습니다.</p>}
        </section>
        {finishedItems.length > 0 && <details key={`${projectId}-${tab}`} className="group rounded-xl border border-bb-border bg-bb-surface">
          <summary className="flex cursor-pointer list-none items-center gap-2 px-4 py-4 text-sm font-medium text-bb-text2 [&::-webkit-details-marker]:hidden">
            <ChevronRight size={16} className="shrink-0 transition-transform group-open:rotate-90" />
            완료된 피드백 {finishedItems.length}
          </summary>
          <div className="space-y-3 border-t border-bb-border p-3">{finishedItems.map(renderCard)}</div>
        </details>}
      </div>}
      {selected && <TaskModal key={selected.id} mode="edit" task={selected} members={members.filter(m=>m.role!=="OBSERVER")} readOnly={readOnly} workOnly focusMessageId={messageId}
        onClose={()=>{setSelected(null);refresh();}} onCreate={settingsOnly} onUpdate={settingsOnly} onDelete={settingsOnly}
        onWorkflowChanged={task=>{setSelected(task);window.dispatchEvent(new Event("task-feedback-changed"));}} />}
    </main>
  </div>;
}
