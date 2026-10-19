"use client";

import { useEffect } from "react";
import { Sparkles } from "lucide-react";
import { useAgentStore } from "@/lib/store/agentStore";

/**
 * 화면마다의 AI 버튼 (화면 문서 4.9). 누르면 도우미 창을 열고 그 실행을 시작한다.
 * AI가 켜져 있고 그 실행이 이 사람에게 허용될 때만 그린다(관찰자에게 계획·나누기는 없음).
 * 기존 방법이 먼저이고 AI는 돕는 길이라 테두리만 있는 작은 버튼으로 둔다.
 */
export default function AgentButton({
  projectId,
  skill,
  input,
  titleExtra,
  className = "",
}: {
  projectId: string;
  skill: string;
  /** 없으면 창에 입력 칸을 연 채로 기다린다(계획 만들기의 안내문 등) */
  input?: object;
  /** 실행 기록 제목 뒤에 붙일 말(업무 나누기의 최종 제출물 이름) */
  titleExtra?: string;
  className?: string;
}) {
  const status = useAgentStore((s) => s.status[projectId]);
  const loadStatus = useAgentStore((s) => s.loadStatus);
  const openWith = useAgentStore((s) => s.openWith);
  const running = useAgentStore((s) => (s.runs[projectId] ?? []).some((r) => r.state === "running"));

  // 같은 프로젝트에서 30초 안에는 다시 부르지 않는다(저장소)
  useEffect(() => {
    loadStatus(projectId);
  }, [projectId, loadStatus]);

  const skillInfo = status?.available ? status.skills.find((s) => s.skill === skill && s.allowed) : undefined;
  if (!skillInfo) return null;

  return (
    <button
      type="button"
      data-agent-button={skill}
      disabled={running}
      onClick={() => openWith(projectId, skill, input, titleExtra)}
      title={running ? "AI 도우미가 다른 일을 하는 중입니다" : undefined}
      className={`inline-flex items-center gap-1 whitespace-nowrap rounded-lg border border-bb-primary/30 px-2.5 py-1 text-xs text-bb-primary hover:bg-bb-primary/10 disabled:opacity-50 ${className}`}
    >
      <Sparkles size={12} aria-hidden="true" />
      {skillInfo.label}
    </button>
  );
}
