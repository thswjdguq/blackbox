"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Shield,
  FolderKanban,
  Kanban,
  FileText,
  Files,
  BarChart2,
  LogOut,
  Sun,
  Moon,
  LayoutDashboard,
  Settings,
  CalendarClock,
  UserCircle,
  Link2,
  Bell,
  AlertTriangle,
  Zap,
  TrendingDown,
  ShieldAlert,
} from "lucide-react";
import { useState, useEffect, useRef, useCallback } from "react";
import { useAuthStore } from "@/lib/store/authStore";
import api from "@/lib/api";
import { Alert } from "@/types/vault";

// ── 알림 유틸 ──────────────────────────────────────────────────────────────

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 1)  return "방금";
  if (mins < 60) return `${mins}분 전`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24)  return `${hrs}시간 전`;
  return `${Math.floor(hrs / 24)}일 전`;
}

const ALERT_CFG = {
  FREE_RIDE: { label: "무임승차",  color: "text-amber-400",  bg: "bg-amber-500/15",  Icon: TrendingDown  },
  OVERLOAD:  { label: "과부하",    color: "text-orange-400", bg: "bg-orange-500/15", Icon: Zap           },
  DROPOUT:   { label: "이탈 감지", color: "text-red-400",    bg: "bg-red-500/15",    Icon: AlertTriangle },
  TAMPER:    { label: "파일 변조", color: "text-purple-400", bg: "bg-purple-500/15", Icon: ShieldAlert   },
} as const;

// ── 알림 벨 컴포넌트 ────────────────────────────────────────────────────────

function NotificationBell({ projectId }: { projectId: string }) {
  const containerRef  = useRef<HTMLDivElement>(null);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [open,   setOpen]   = useState(false);

  const fetchAlerts = useCallback(async () => {
    try {
      const res = await api.get<Alert[]>(`/projects/${projectId}/alerts`);
      setAlerts(res.data);
    } catch { /* 벨은 비핵심 — 실패해도 무시 */ }
  }, [projectId]);

  useEffect(() => {
    fetchAlerts();
    const id = setInterval(fetchAlerts, 30_000);
    return () => clearInterval(id);
  }, [fetchAlerts]);

  // 드롭다운 외부 클릭 시 닫기
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node))
        setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  const handleMarkRead = (alert: Alert) => {
    if (!alert.isRead) {
      api.patch(`/projects/${projectId}/alerts/${alert.id}/read`)
        .then(() => setAlerts(prev => prev.map(a => a.id === alert.id ? { ...a, isRead: true } : a)))
        .catch(() => {});
    }
    setOpen(false);
  };

  const unread    = alerts.filter(a => !a.isRead).length;
  const displayed = alerts.slice(0, 10);

  return (
    <div className="relative" ref={containerRef}>
      {/* 벨 버튼 */}
      <button
        onClick={() => setOpen(v => !v)}
        className="relative p-1.5 rounded-lg text-bb-text2 hover:text-bb-text hover:bg-bb-surface2 transition-colors"
        aria-label="알림"
      >
        <Bell size={15} />
        {unread > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-0.5
                           bg-red-500 rounded-full text-[10px] font-bold text-white
                           flex items-center justify-center leading-none">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </button>

      {/* 드롭다운 */}
      {open && (
        <div className="absolute left-0 top-full mt-2 w-80
                        bg-bb-surface border border-bb-border rounded-xl
                        shadow-2xl z-50 overflow-hidden">
          {/* 헤더 */}
          <div className="px-4 py-3 border-b border-bb-border flex items-center justify-between">
            <span className="text-sm font-semibold text-bb-text">알림</span>
            {unread > 0 && (
              <span className="text-xs text-bb-text2">{unread}개 읽지 않음</span>
            )}
          </div>

          {/* 목록 */}
          <div className="max-h-80 overflow-y-auto">
            {displayed.length === 0 ? (
              <div className="py-10 text-center text-sm text-bb-text2">
                새로운 알림이 없습니다
              </div>
            ) : (
              displayed.map((alert) => {
                const cfg = ALERT_CFG[alert.alertType as keyof typeof ALERT_CFG] ?? ALERT_CFG.FREE_RIDE;
                return (
                  <button
                    key={alert.id}
                    onClick={() => handleMarkRead(alert)}
                    className={`w-full text-left px-4 py-3 transition-colors
                                border-b border-bb-border/40 last:border-0
                                hover:bg-bb-bg/60
                                ${!alert.isRead ? "bg-bb-bg/30" : ""}`}
                  >
                    <div className="flex items-start gap-2.5">
                      {/* 타입 배지 */}
                      <span className={`mt-0.5 shrink-0 text-[10px] font-semibold
                                        px-1.5 py-0.5 rounded
                                        ${cfg.color} ${cfg.bg}`}>
                        {cfg.label}
                      </span>
                      {/* 메시지 + 시간 */}
                      <div className="flex-1 min-w-0">
                        <p className="text-xs text-bb-text leading-relaxed">{alert.message}</p>
                        <p className="text-[10px] text-bb-text2 mt-1">{timeAgo(alert.createdAt)}</p>
                      </div>
                      {/* 읽지 않음 점 */}
                      {!alert.isRead && (
                        <div className="w-1.5 h-1.5 rounded-full bg-bb-primary shrink-0 mt-1.5" />
                      )}
                    </div>
                  </button>
                );
              })
            )}
          </div>

          {/* 전체 보기 */}
          <div className="px-4 py-2.5 border-t border-bb-border">
            <Link
              href={`/projects/${projectId}/analytics`}
              onClick={() => setOpen(false)}
              className="block w-full text-xs text-bb-text2 hover:text-bb-primary
                         transition-colors text-center py-1"
            >
              전체 보기 →
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

interface SidebarProps {
  hasProjects?: boolean;
}

export default function Sidebar({ hasProjects }: SidebarProps) {
  const pathname = usePathname();
  const router   = useRouter();
  const [isDark, setIsDark] = useState(true);
  const [toast,  setToast]  = useState(false);
  const clearTokens = useAuthStore((s) => s.clearTokens);

  // URL에서 현재 프로젝트 ID 추출 (/projects/[id]/xxx)
  const projectIdMatch = pathname.match(/\/projects\/([^/]+)/);
  const currentProjectId = projectIdMatch?.[1] ?? null;

  const NAV_ITEMS = [
    { href: "/dashboard",                                                                           icon: FolderKanban,    label: "내 프로젝트",   exactActive: false },
    { href: currentProjectId ? `/projects/${currentProjectId}`            : "/dashboard",           icon: LayoutDashboard, label: "프로젝트 홈",  needsProject: true, exactActive: true },
    { href: currentProjectId ? `/projects/${currentProjectId}/board`      : "/board",               icon: Kanban,          label: "칸반 보드",    needsProject: true, exactActive: false },
    { href: currentProjectId ? `/projects/${currentProjectId}/schedule`   : "/schedule",            icon: CalendarClock,   label: "일정 조율",    needsProject: true, exactActive: false },
    { href: currentProjectId ? `/projects/${currentProjectId}/meetings`   : "/meetings",            icon: FileText,        label: "회의록",       needsProject: true, exactActive: false },
    { href: currentProjectId ? `/projects/${currentProjectId}/vault`      : "/vault",               icon: Files,           label: "Hash Vault",   needsProject: true, exactActive: false },
    { href: currentProjectId ? `/projects/${currentProjectId}/analytics`  : "/analytics",           icon: BarChart2,       label: "기여도",       needsProject: true, exactActive: false },
    { href: currentProjectId ? `/projects/${currentProjectId}/settings`   : "/settings",            icon: Settings,        label: "프로젝트 설정", needsProject: true, exactActive: false },
  ];

  useEffect(() => {
    const theme = localStorage.getItem("theme") ?? "dark";
    setIsDark(theme === "dark");
    document.documentElement.classList.toggle("dark", theme === "dark");
  }, []);

  const toggleTheme = () => {
    const next = !isDark;
    setIsDark(next);
    localStorage.setItem("theme", next ? "dark" : "light");
    document.documentElement.classList.toggle("dark", next);
  };

  const showNoProjectToast = () => {
    setToast(true);
    setTimeout(() => setToast(false), 3000);
  };

  const handleNavClick = (e: React.MouseEvent, href: string, needsProject?: boolean) => {
    if (!needsProject) return;
    // 현재 URL에 projectId가 없으면 무조건 차단 (폴백 URL은 존재하지 않는 경로)
    if (!currentProjectId) {
      e.preventDefault();
      showNoProjectToast();
      return;
    }
    const stored = localStorage.getItem("projectCount");
    const count  = hasProjects !== undefined
      ? (hasProjects ? 1 : 0)
      : stored !== null ? Number(stored) : -1;
    if (count === 0) {
      e.preventDefault();
      showNoProjectToast();
    }
  };

  const handleLogout = async () => {
    try {
      await api.post("/auth/logout");
    } catch {
      // 서버 상태와 무관하게 클라이언트 auth 상태는 반드시 정리
    } finally {
      clearTokens();
      localStorage.removeItem("projectCount");
      router.replace("/login");
    }
  };

  return (
    <>
      <aside className="w-64 bg-bb-sidebar border-r border-bb-border h-screen fixed left-0 top-0 flex flex-col z-30">
        {/* 로고 + 알림 벨 */}
        <div className="px-6 py-5 border-b border-bb-border flex items-center justify-between">
          <div
            className="flex items-center gap-2 cursor-pointer hover:opacity-75 transition-opacity"
            onClick={() => router.push("/dashboard")}
          >
            <div className="w-8 h-8 rounded-lg bg-bb-primary/10 flex items-center justify-center">
              <Shield size={16} className="text-bb-primary" />
            </div>
            <span className="text-sm font-semibold text-bb-text">Team Blackbox</span>
          </div>
          {currentProjectId && <NotificationBell projectId={currentProjectId} />}
        </div>

        {/* 네비게이션 */}
        <nav className="flex-1 px-3 py-4 space-y-0.5">
          {NAV_ITEMS.map(({ href, icon: Icon, label, needsProject, exactActive }) => {
            // exactActive=true → exact pathname match only (프로젝트 홈 등)
            const segment = href.split("/").pop()!;
            const active = exactActive
              ? pathname === href
              : (pathname === href
                || pathname.endsWith(`/${segment}`)
                || pathname.includes(`/${segment}/`));
            return (
              <Link
                key={href}
                href={href}
                prefetch={!needsProject || !!currentProjectId}
                onClick={(e) => handleNavClick(e, href, needsProject)}
                className={`relative flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors ${
                  active
                    ? "bg-bb-primary/10 text-bb-primary font-medium before:absolute before:left-0 before:top-1 before:bottom-1 before:w-[3px] before:rounded-full before:bg-bb-primary"
                    : "text-bb-text2 hover:text-bb-text hover:bg-bb-surface2"
                }`}
              >
                <Icon size={16} className={active ? "text-bb-primary" : "text-bb-text2"} />
                {label}
              </Link>
            );
          })}
        </nav>

        {/* 하단 버튼 */}
        <div className="px-3 py-4 border-t border-bb-border space-y-0.5">
          <button
            onClick={toggleTheme}
            className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm
                       text-bb-text2 hover:text-bb-text hover:bg-bb-surface2 transition-colors"
          >
            {isDark ? <Sun size={16} /> : <Moon size={16} />}
            {isDark ? "라이트 모드" : "다크 모드"}
          </button>
          <Link
            href="/profile/settings"
            className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors ${
              pathname === "/profile/settings"
                ? "bg-bb-primary/10 text-bb-primary font-medium"
                : "text-bb-text2 hover:text-bb-text hover:bg-bb-surface2"
            }`}
          >
            <UserCircle size={16} className={pathname === "/profile/settings" ? "text-bb-primary" : "text-bb-text2"} />
            프로필 설정
          </Link>
          <Link
            href="/profile/notion"
            className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors ${
              pathname === "/profile/notion"
                ? "bg-bb-primary/10 text-bb-primary font-medium"
                : "text-bb-text2 hover:text-bb-text hover:bg-bb-surface2"
            }`}
          >
            <Link2 size={16} className={pathname === "/profile/notion" ? "text-bb-primary" : "text-bb-text2"} />
            Notion 연동
          </Link>
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm
                       text-bb-text2 hover:text-red-500 hover:bg-red-500/10 transition-colors"
          >
            <LogOut size={16} />
            로그아웃
          </button>
        </div>
      </aside>

      {/* 토스트 */}
      {toast && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50
                        flex items-center gap-2 px-5 py-3 rounded-xl shadow-lg
                        bg-bb-surface border border-bb-border
                        text-sm text-bb-text animate-fade-in">
          <FolderKanban size={15} className="text-bb-primary shrink-0" />
          먼저 프로젝트를 생성하거나 참여해주세요
        </div>
      )}
    </>
  );
}
