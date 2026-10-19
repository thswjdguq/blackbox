"use client";
import { useCallback, useEffect, useState } from "react";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import type { FeedbackInbox, FeedbackItem } from "@/types/taskMessage";

/**
 * 사이드바 배지용 '내가 지금 처리할 피드백' 수.
 * received에는 답변이 끝난 기록도 함께 오고(K-EXPO-06), 미해결 최종 제출물 피드백은 received와 revisions에 모두 들어 있으므로
 * 답할 의견 요청·확인할 완료 요청·고칠 내용만 세고 같은 항목은 한 번만 센다.
 */
export function countActionableFeedback(inbox: FeedbackInbox | null): number {
  if (!inbox) return 0;
  const key = (i: FeedbackItem) => `${i.kind}-${i.messageId ?? i.taskId}`;
  const toAnswer = inbox.received.filter(i => (i.kind === "OPINION" && i.status !== "ANSWERED") || i.kind === "COMPLETION");
  return new Set([...toAnswer, ...inbox.revisions].map(key)).size;
}

export function useFeedbackInbox(projectId: string | null) {
  const [data, setData] = useState<FeedbackInbox | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const refresh = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    try { const res = await api.get<FeedbackInbox>(`/projects/${projectId}/feedback`); setData(res.data); setError(""); }
    catch(e) { setError(apiError(e,"피드백 요청을 불러오지 못했습니다.")); }
    finally { setLoading(false); }
  }, [projectId]);
  useEffect(() => {
    setData(null); refresh();
    const update = () => { if (!document.hidden) refresh(); };
    const timer = window.setInterval(update,30000);
    window.addEventListener("focus", update); window.addEventListener("task-feedback-changed",update);
    return () => { window.clearInterval(timer); window.removeEventListener("focus",update); window.removeEventListener("task-feedback-changed",update); };
  }, [refresh]);
  return {data,error,loading,refresh};
}
