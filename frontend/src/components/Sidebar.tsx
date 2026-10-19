"use client";

import { usePathname, useRouter } from "next/navigation";
import { Menu, X, Shield, LogOut, Sun, Moon } from "lucide-react";
import { useState, useEffect, useRef, useCallback } from "react";
import { useAuthStore } from "@/lib/store/authStore";
import SidebarNavigation from "@/components/project/SidebarNavigation";
import AgentLauncher from "@/components/agent/AgentLauncher";
import ProjectSwitcher from "@/components/ProjectSwitcher";
import api from "@/lib/api";
import AccountIdentity from "@/components/AccountIdentity";

// 개인 알림은 사이드바 메뉴(알림)에서, 참여 기록·파일 변조 등 팀 알림은 팀 활동 페이지에서 확인한다.
// 헤더의 중복 벨과 빨간 경고 배지는 두지 않는다.

interface SidebarProps {
  hasProjects?: boolean;
}

export default function Sidebar({ hasProjects }: SidebarProps) {
  const pathname = usePathname();
  const router   = useRouter();
  const [isDark, setIsDark] = useState(false);
  // 768px 미만에서는 사이드바를 숨기고 상단 바의 메뉴 버튼으로 연다
  const [mobileOpen, setMobileOpen] = useState(false);
  const menuButtonRef  = useRef<HTMLButtonElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const clearTokens = useAuthStore((s) => s.clearTokens);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  // 닫기 버튼·배경·Escape로 닫으면 초점을 메뉴 버튼으로 돌려준다
  const closeMenu = useCallback(() => {
    setMobileOpen(false);
    menuButtonRef.current?.focus();
  }, []);

  useEffect(() => {
    if (!mobileOpen) return;
    closeButtonRef.current?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") closeMenu(); };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [mobileOpen, closeMenu]);

  // URL에서 현재 프로젝트 ID 추출 (/projects/[id]/xxx)
  const projectIdMatch = pathname.match(/\/projects\/([^/]+)/);
  const currentProjectId = projectIdMatch?.[1] ?? null;

  useEffect(() => {
    const theme = localStorage.getItem("teamplay-ui-theme") ?? "light";
    setIsDark(theme === "dark");
    document.documentElement.classList.toggle("dark", theme === "dark");
  }, []);

  const toggleTheme = () => {
    const next = !isDark;
    setIsDark(next);
    localStorage.setItem("teamplay-ui-theme", next ? "dark" : "light");
    document.documentElement.classList.toggle("dark", next);
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
      {/* 작은 화면 상단 바 */}
      <header className="md:hidden fixed top-0 inset-x-0 h-14 z-30 flex items-center gap-3 px-4 bg-bb-sidebar border-b border-bb-border">
        <button
          ref={menuButtonRef}
          onClick={() => setMobileOpen(true)}
          className="p-1.5 -ml-1.5 rounded-lg text-bb-text2 hover:text-bb-text hover:bg-bb-surface2"
          aria-label="메뉴 열기"
          aria-expanded={mobileOpen}
          aria-controls="app-sidebar"
        >
          <Menu size={20} />
        </button>
        <button onClick={() => router.push("/dashboard")} className="flex items-center gap-2">
          <Shield size={16} className="text-bb-primary" />
          <span className="text-sm font-semibold text-bb-text">Team Blackbox</span>
        </button>
        {currentProjectId && <AgentLauncher projectId={currentProjectId} compact />}
        <AccountIdentity compact active={pathname === "/profile/settings"} />
      </header>

      {mobileOpen && (
        <div className="md:hidden fixed inset-0 z-40 bg-black/50" onClick={closeMenu} aria-hidden="true" />
      )}

      {/* 닫혀 있을 때는 invisible로 숨겨 화면 밖 메뉴에 Tab 초점이 가지 않게 한다.
          열 때는 바로 보이게 해야 닫기 버튼에 초점을 줄 수 있고, 닫을 때만 밀려나는 동안 보이게 둔다 */}
      <aside
        id="app-sidebar"
        className={`w-64 bg-bb-sidebar border-r border-bb-border h-screen fixed left-0 top-0 flex flex-col z-50 md:z-30
                    duration-200 md:translate-x-0 md:visible
                    ${mobileOpen ? "translate-x-0 visible transition-transform" : "-translate-x-full invisible transition-[transform,visibility]"}`}
      >
        {/* 로고 */}
        <div className="px-5 py-6 border-b border-bb-border flex items-center justify-between">
          <div
            className="flex items-center gap-2 cursor-pointer hover:opacity-75 transition-opacity"
            onClick={() => router.push("/dashboard")}
          >
            <div className="w-8 h-8 rounded-lg bg-bb-primary/10 flex items-center justify-center">
              <Shield size={16} className="text-bb-primary" />
            </div>
            <span className="text-sm font-semibold text-bb-text">Team Blackbox</span>
          </div>
          <div className="flex items-center gap-1">
            <button
              ref={closeButtonRef}
              onClick={closeMenu}
              className="md:hidden p-1.5 rounded-lg text-bb-text2 hover:text-bb-text hover:bg-bb-surface2"
              aria-label="메뉴 닫기"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {currentProjectId && <ProjectSwitcher currentProjectId={currentProjectId} pathname={pathname} onNavigate={() => setMobileOpen(false)} />}
        {currentProjectId && <AgentLauncher projectId={currentProjectId} />}

        <SidebarNavigation projectId={currentProjectId} onNavigate={() => setMobileOpen(false)} />

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
          <AccountIdentity active={pathname === "/profile/settings"} onNavigate={() => setMobileOpen(false)} />
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

    </>
  );
}
