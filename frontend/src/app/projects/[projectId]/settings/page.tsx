"use client";

import { useParams } from "next/navigation";
import Link from "next/link";
import Sidebar from "@/components/Sidebar";
import { UserCircle, ArrowRight } from "lucide-react";

export default function ProjectSettingsPage() {
  const { projectId } = useParams<{ projectId: string }>();
  void projectId;

  return (
    <div className="flex min-h-screen bg-bb-bg">
      <Sidebar />
      <main className="ml-64 flex-1 p-8">
        <div className="max-w-2xl">

          <h1 className="text-2xl font-bold text-bb-text mb-1">프로젝트 설정</h1>
          <p className="text-sm text-bb-text2 mb-8">프로젝트 관련 설정을 관리합니다</p>

          {/* 캘린더 연동 안내 카드 */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-6">
            <div className="flex items-start gap-4">
              <div className="w-10 h-10 rounded-xl bg-indigo-500/10 flex items-center justify-center shrink-0">
                <UserCircle size={20} className="text-indigo-400" />
              </div>
              <div className="flex-1">
                <h2 className="text-sm font-semibold text-bb-text mb-1">Google 캘린더 연동</h2>
                <p className="text-xs text-bb-text2 leading-relaxed mb-4">
                  Google 캘린더 연동은 개인 프로필 설정에서 할 수 있습니다.
                  연동하면 AI 일정 조율 기능에서 내 캘린더가 자동 분석됩니다.
                </p>
                <Link
                  href="/profile/settings"
                  className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500
                             text-white text-sm font-medium rounded-lg transition-all"
                >
                  프로필 설정에서 연동하기
                  <ArrowRight size={14} />
                </Link>
              </div>
            </div>
          </div>

        </div>
      </main>
    </div>
  );
}
