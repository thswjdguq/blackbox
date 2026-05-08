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
} from "lucide-react";
import { useState, useEffect } from "react";
import { useAuthStore } from "@/lib/store/authStore";

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

  const handleLogout = () => {
    clearTokens();
    localStorage.removeItem("projectCount");
    router.push("/login");
  };

  return (
    <>
      <aside className="w-64 bg-bb-sidebar border-r border-bb-border h-screen fixed left-0 top-0 flex flex-col z-30">
        {/* 로고 */}
        <div
          className="px-6 py-5 border-b border-bb-border cursor-pointer hover:bg-bb-surface2 transition-colors"
          onClick={() => router.push("/dashboard")}
        >
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-bb-primary/10 flex items-center justify-center">
              <Shield size={16} className="text-bb-primary" />
            </div>
            <span className="text-sm font-semibold text-bb-text">Team Blackbox</span>
          </div>
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
                prefetch={true}
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
              pathname.startsWith("/profile")
                ? "bg-bb-primary/10 text-bb-primary font-medium"
                : "text-bb-text2 hover:text-bb-text hover:bg-bb-surface2"
            }`}
          >
            <UserCircle size={16} className={pathname.startsWith("/profile") ? "text-bb-primary" : "text-bb-text2"} />
            프로필 설정
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
