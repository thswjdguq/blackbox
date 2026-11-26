"use client";

import { useEffect, useState } from "react";
import api from "@/lib/api";
import { onSessionReset, sessionGeneration } from "@/lib/sessionCache";

export interface IntegrationStatus {
  discordConnected:  boolean | null;
  notionConnected:   boolean | null;
  calendarConnected: boolean | null;
  missingCount: number | null;
  loading: boolean;
}

interface ConnStatus {
  discordConnected:  boolean;
  notionConnected:   boolean;
  calendarConnected: boolean;
}

/**
 * 프로젝트별 마지막 조회 결과 캐시.
 * 페이지 이동마다 Sidebar/배너가 재마운트되며 상태가 false로 초기화되는 문제 때문에
 * 뱃지가 깜빡였음 — 캐시된 값을 즉시 보여주고 백그라운드에서 갱신한다 (stale-while-revalidate).
 */
const statusCache = new Map<string, ConnStatus>();
onSessionReset(() => statusCache.clear());

/** Discord / Notion / Google Calendar 연동 상태를 조합해 미연동 항목 개수를 계산 */
export function useIntegrationStatus(projectId: string | null): IntegrationStatus {
  // 결과는 어느 프로젝트의 것인지와 함께 둔다. 프로젝트가 바뀐 첫 그림부터 앞 프로젝트 값을 쓰지 않고
  // 그 프로젝트의 캐시(없으면 null)를 쓴다
  const [fetched, setFetched] = useState<{ projectId: string; status: ConnStatus } | null>(null);
  const [settledFor, setSettledFor] = useState<string | null>(null);
  const status = !projectId ? null
    : fetched?.projectId === projectId ? fetched.status : statusCache.get(projectId) ?? null;
  const loading = !!projectId && status === null && settledFor !== projectId;

  useEffect(() => {
    if (!projectId) return;
    let cancelled = false;
    const gen = sessionGeneration();
    (async () => {
      try {
        const [discordRes, notionRes, calendarRes] = await Promise.all([
          api.get<{ webhookUrl: string | null }>(`/projects/${projectId}/discord`)
            .catch(() => ({ data: { webhookUrl: null } })),
          api.get<{ connected: boolean }>(`/notion/status`)
            .catch(() => ({ data: { connected: false } })),
          api.get<{ connected: boolean }>(`/projects/${projectId}/calendar/status`)
            .catch(() => ({ data: { connected: false } })),
        ]);
        // 화면이 사라졌거나 그 사이 로그아웃했으면 넣지 않는다
        if (cancelled || gen !== sessionGeneration()) return;
        const next: ConnStatus = {
          discordConnected:  !!discordRes.data.webhookUrl,
          notionConnected:   notionRes.data.connected,
          calendarConnected: calendarRes.data.connected,
        };
        statusCache.set(projectId, next);
        setFetched({ projectId, status: next });
      } finally {
        if (!cancelled) setSettledFor(projectId);
      }
    })();

    return () => { cancelled = true; };
  }, [projectId]);

  // 데이터가 도착하기 전(null)에는 개수를 확정 짓지 않음 → 호출부에서 뱃지 렌더링을 보류
  const missingCount = status === null
    ? null
    : (status.discordConnected  ? 0 : 1)
    + (status.notionConnected   ? 0 : 1)
    + (status.calendarConnected ? 0 : 1);

  return {
    discordConnected:  status?.discordConnected  ?? null,
    notionConnected:   status?.notionConnected   ?? null,
    calendarConnected: status?.calendarConnected ?? null,
    missingCount,
    loading,
  };
}
