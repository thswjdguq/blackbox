"use client";
import { useEffect } from "react";
import Link from "next/link";
import { Bell } from "lucide-react";
import { usePathname } from "next/navigation";
import { useNotificationStore } from "@/lib/store/notificationStore";

export default function NotificationLink({ projectId, onNavigate }: { projectId: string; onNavigate: () => void }) {
  const entry = useNotificationStore(s => s.projects[projectId]);
  const count = useNotificationStore(s => s.count);
  const active = usePathname() === `/projects/${projectId}/notifications`;
  useEffect(() => {
    const refresh = () => { if (!document.hidden) void count(projectId); };
    refresh(); const timer = window.setInterval(refresh, 30000);
    window.addEventListener("focus", refresh);
    return () => { window.clearInterval(timer); window.removeEventListener("focus", refresh); };
  }, [projectId, count]);
  return <Link href={`/projects/${projectId}/notifications`} onClick={onNavigate} aria-current={active ? "page" : undefined}
    className={`flex items-center gap-3 rounded-lg px-3 py-3 text-sm ${active ? "bg-bb-primary/10 text-bb-primary font-semibold" : "text-bb-text2 hover:bg-bb-surface2"}`}>
    <Bell size={17} /><span>알림</span>
    {entry?.countError ? <span aria-label="알림 조회 실패" className="ml-auto text-amber-600">!</span> : !!entry?.count && <span aria-label={`읽지 않은 알림 ${entry.count}건`} className="ml-auto rounded-full bg-bb-primary px-2 py-0.5 text-xs text-white">{entry.count > 99 ? "99+" : entry.count}</span>}
  </Link>;
}
