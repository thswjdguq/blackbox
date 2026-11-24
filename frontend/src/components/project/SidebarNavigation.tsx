"use client";
import Link from "next/link";
import { useEffect, useId, useState } from "react";
import { useLayoutEffect, useRef } from "react";
import { usePathname } from "next/navigation";
import { ArrowLeft, BookOpenText, ChevronDown, ClipboardList, FileText, Files, FolderKanban, Kanban, LayoutDashboard, Settings, BarChart2, MessageSquare } from "lucide-react";
import { countActionableFeedback, useFeedbackInbox } from "@/hooks/useFeedbackInbox";
import { projectNavigation, projectSection } from "@/lib/projectNavigation";
import NotificationLink from "./NotificationLink";

// 사이드바는 화면마다 새로 그려진다. 메뉴 칸의 스크롤 위치를 이 탭 안에서 기억했다가 다시 그릴 때 되돌린다
// (창이 낮을 때 아래쪽 보조 메뉴를 고르면 맨 위로 돌아가 고른 메뉴가 안 보이던 것)
let savedScrollTop = 0;

const icons = { home: LayoutDashboard, deliverables: ClipboardList, board: Kanban, feedback: MessageSquare, meetings: FileText, briefs: BookOpenText, vault: Files, analytics: BarChart2, settings: Settings };

export default function SidebarNavigation({ projectId, onNavigate }: { projectId: string | null; onNavigate: () => void }) {
  const pathname = usePathname();
  const { data: inbox, error: inboxError } = useFeedbackInbox(projectId);
  const waiting = countActionableFeedback(inbox);
  const selected = projectId ? projectSection(pathname, projectId) : undefined;
  const auxiliary = selected?.primary === false;
  const [more, setMore] = useState(auxiliary);
  const moreId = useId();
  const navRef = useRef<HTMLElement>(null);
  useLayoutEffect(() => {
    const nav = navRef.current;
    if (!nav) return;
    nav.scrollTop = savedScrollTop;
    // 그래도 고른 메뉴가 칸 밖이면 그만큼만 옮겨 보이게 한다
    const active = nav.querySelector<HTMLElement>('[aria-current="page"]');
    if (!active) return;
    const a = active.getBoundingClientRect(), n = nav.getBoundingClientRect();
    if (a.bottom > n.bottom) nav.scrollTop += a.bottom - n.bottom;
    else if (a.top < n.top) nav.scrollTop -= n.top - a.top;
    savedScrollTop = nav.scrollTop;
  }, []);
  useEffect(() => { setMore(auxiliary); }, [auxiliary, pathname]);
  const renderItem = (item: typeof projectNavigation[number]) => {
    const Icon = icons[item.key]; const active = selected?.key === item.key;
    return <Link key={item.key} href={`/projects/${projectId}${item.suffix}`} onClick={onNavigate}
      aria-current={active ? "page" : undefined}
      className={`flex items-center gap-3 px-3 py-3 rounded-lg text-sm transition-colors ${active ? "bg-bb-primary/10 text-bb-primary font-semibold" : "text-bb-text2 hover:text-bb-text hover:bg-bb-surface2"}`}>
      <Icon size={17} /><span>{item.label}</span>
      {item.key === "feedback" && !inboxError && waiting > 0 && <span aria-label={`처리할 요청 ${waiting}건`} className="ml-auto rounded-full bg-bb-primary text-white px-2 py-0.5 text-xs">{waiting}</span>}
    </Link>;
  };
  return <nav ref={navRef} onScroll={(e) => { savedScrollTop = e.currentTarget.scrollTop; }} aria-label="주 메뉴" className="flex-1 overflow-y-auto px-3 py-4">
    <Link href="/dashboard" onClick={onNavigate} aria-current={!projectId && pathname === "/dashboard" ? "page" : undefined}
      className={`flex items-center gap-3 px-3 py-3 rounded-lg text-sm ${projectId ? "text-bb-text2 hover:bg-bb-surface2" : "bg-bb-primary/10 text-bb-primary font-semibold"}`}>
      {projectId ? <ArrowLeft size={16} /> : <FolderKanban size={17} />}{projectId ? "전체 프로젝트" : "내 프로젝트"}
    </Link>
    {projectId ? <>
      <NotificationLink key={projectId} projectId={projectId} onNavigate={onNavigate} />
      <div className="mt-4 space-y-1">{projectNavigation.filter(item => item.primary).map(renderItem)}</div>
      <div className="border-t border-bb-border mt-5 pt-3">
        <button type="button" onClick={() => setMore(v => !v)} aria-expanded={more} aria-controls={moreId}
          className="flex items-center justify-between w-full px-3 py-2 text-xs text-bb-text2 rounded-lg hover:bg-bb-surface2">자료 · 활동 · 설정<ChevronDown size={14} className={more ? "rotate-180" : ""} /></button>
        {more && <div id={moreId} className="mt-1 space-y-1">{projectNavigation.filter(item => !item.primary).map(renderItem)}</div>}
      </div>
    </> : <p className="px-3 mt-4 text-xs leading-relaxed text-bb-text2">프로젝트를 만들거나 초대 코드로 참여해 시작하세요.</p>}
  </nav>;
}
