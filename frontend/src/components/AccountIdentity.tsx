"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useProfileStore } from "@/lib/store/profileStore";

export default function AccountIdentity({ compact = false, active = false, onNavigate }: {
  compact?: boolean; active?: boolean; onNavigate?: () => void;
}) {
  const { profile, loading, failed, load } = useProfileStore();
  useEffect(() => { void load(); }, [load]);
  const name = profile?.name.trim() || profile?.email || "";
  const initials = name ? Array.from(name).slice(0, 2).join("").toUpperCase() : failed ? "!" : "…";
  const label = profile ? `${name} (${profile.email}) · 프로필 설정` : failed ? "로그인 정보 확인 실패 · 프로필 설정" : "로그인 정보 확인 중";
  return <div className={compact ? "ml-auto shrink-0" : "min-w-0"}>
    <Link href="/profile/settings" onClick={onNavigate} aria-label={label} title={label}
      aria-current={active ? "page" : undefined}
      className={`${compact ? "inline-flex p-1" : "w-full flex gap-3 px-3 py-2"} items-center rounded-lg hover:bg-bb-surface2 ${active ? "bg-bb-primary/10" : ""}`}>
      <span aria-hidden="true" className="w-9 h-9 shrink-0 rounded-full flex items-center justify-center bg-blue-500/15 text-blue-700 dark:text-blue-300 text-sm font-semibold">{initials}</span>
      {!compact && <span className="min-w-0 text-left">
        <span className="block truncate text-sm font-medium text-bb-text">{name || (failed ? "계정 확인 실패" : "로그인 정보 확인 중")}</span>
        {profile && <span className="block truncate text-xs text-bb-text2">{profile.email}</span>}
        <span className="block text-xs text-bb-text2">프로필 설정</span>
      </span>}
    </Link>
    {failed && !compact && <button type="button" disabled={loading} onClick={() => void load()} className="ml-3 mt-1 text-xs text-bb-primary underline">계정 정보 다시 확인</button>}
  </div>;
}
