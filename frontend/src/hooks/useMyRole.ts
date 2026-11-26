"use client";

import { useEffect, useState } from "react";
import api from "@/lib/api";
import { getMembers } from "@/lib/api/member";
import type { MemberRole } from "@/types/member";
import { onSessionReset } from "@/lib/sessionCache";

/**
 * 프로젝트별 내 역할 캐시. Sidebar처럼 페이지마다 다시 마운트되는 곳에서
 * 같은 요청을 반복하지 않게 진행 중인 요청을 함께 쓴다. 실패하면 캐시하지 않는다.
 */
const roleCache = new Map<string, Promise<MemberRole | null>>();
onSessionReset(() => roleCache.clear());

function loadRole(projectId: string): Promise<MemberRole | null> {
  const cached = roleCache.get(projectId);
  if (cached) return cached;
  const p = Promise.all([getMembers(projectId), api.get<{ id: string }>("/auth/profile")])
    .then(([members, profile]) => members.data.find((m) => m.userId === profile.data.id)?.role ?? null)
    .catch(() => {
      roleCache.delete(projectId);
      return null;
    });
  roleCache.set(projectId, p);
  return p;
}

/** 내 역할이 바뀐 뒤(예: 팀장이 스스로 역할을 바꿈) 다음 화면에서 다시 불러오게 한다 */
export function forgetMyRole(projectId: string) {
  roleCache.delete(projectId);
}

/**
 * 이 프로젝트에서 내 역할. 불러오는 중이거나 실패하면 null.
 * 프로젝트 단건 응답의 myRole(#70) 대신 멤버 목록으로 찾는다.
 * 화면 안내용일 뿐이며 권한은 서버가 다시 확인한다.
 */
export function useMyRole(projectId: string | null): MemberRole | null {
  // 역할은 어느 프로젝트의 것인지와 함께 둔다. 프로젝트가 바뀐 첫 그림부터 앞 프로젝트의 역할을 쓰지 않는다
  const [loaded, setLoaded] = useState<{ projectId: string; role: MemberRole | null } | null>(null);

  useEffect(() => {
    if (!projectId) return;
    let alive = true;
    loadRole(projectId).then((role) => { if (alive) setLoaded({ projectId, role }); });
    return () => { alive = false; };
  }, [projectId]);

  return projectId && loaded?.projectId === projectId ? loaded.role : null;
}
