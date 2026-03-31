"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import api from "@/lib/api";

/** 첫 번째 프로젝트의 해당 경로로 리다이렉트하는 범용 shim */
interface Project { id: string; }

export default function MeetingsRedirectPage() {
  const router = useRouter();

  useEffect(() => {
    if (!localStorage.getItem("accessToken")) { router.replace("/login"); return; }

    api.get<Project[]>("/projects").then(({ data }) => {
      if (data.length > 0) {
        router.replace(`/projects/${data[0].id}/meetings`);
      } else {
        router.replace("/dashboard");
      }
    }).catch(() => router.replace("/dashboard"));
  }, []);

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center">
      <div className="flex flex-col items-center gap-3">
        <div className="w-8 h-8 border-2 border-indigo-500/30 border-t-blue-500 rounded-full animate-spin" />
        <p className="text-sm text-slate-500">이동 중...</p>
      </div>
    </div>
  );
}
