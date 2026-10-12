"use client";
import { useCallback, useEffect, useState } from "react";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import type { FeedbackInbox } from "@/types/taskMessage";

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
