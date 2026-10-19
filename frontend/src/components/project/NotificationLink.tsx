"use client";
import { useEffect } from "react";
import Link from "next/link";
import { Bell } from "lucide-react";
import { usePathname } from "next/navigation";
import { useNotificationStore } from "@/lib/store/notificationStore";

export default function NotificationLink({ projectId, onNavigate, compact=false }: { projectId: string; onNavigate: () => void; compact?: boolean }) {
  const entry = useNotificationStore(s => s.projects[projectId]);
  const count = useNotificationStore(s => s.count);
  const active = usePathname() === `/projects/${projectId}/notifications`;
  useEffect(() => {
    const refresh = () => { if (!document.hidden) void count(projectId); };
    refresh(); const timer = window.setInterval(refresh, 10000);
    window.addEventListener("focus", refresh);
    window.addEventListener("task-feedback-changed", refresh);
    return () => { window.clearInterval(timer); window.removeEventListener("focus", refresh); window.removeEventListener("task-feedback-changed", refresh); };
  }, [projectId, count]);
  return <Link href={`/projects/${projectId}/notifications`} onClick={onNavigate} aria-current={active ? "page" : undefined}
    aria-label="개인 알림" title="피드백·업무 개인 알림"
    className={`flex items-center ${compact?"gap-1 p-1.5":"gap-3 px-3 py-3"} rounded-lg text-sm ${active ? "bg-bb-primary/10 text-bb-primary font-semibold" : "text-bb-text2 hover:bg-bb-surface2"}`}>
    <Bell size={17} /><span className={compact?"sr-only":""}>알림</span>
    {entry?.countError ? <span aria-label="알림 조회 실패" className="ml-auto text-amber-600">!</span> : !!entry?.count && <span aria-label={`읽지 않은 알림 ${entry.count}건`} className="ml-auto rounded-full bg-bb-primary px-2 py-0.5 text-xs text-white">{entry.count > 99 ? "99+" : entry.count}</span>}
  </Link>;
}
