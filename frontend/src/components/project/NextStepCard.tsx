"use client";

import Link from "next/link";
import { useEffect } from "react";
import AgentButton from "@/components/agent/AgentButton";
import { useAgentStore } from "@/lib/store/agentStore";
import { suggestFor } from "@/lib/agent/skills";
import { ArrowRight, ClipboardList } from "lucide-react";
import type { Deliverable } from "@/types/deliverable";
import type { Task } from "@/types/task";
import { DUE_BADGE, dueTone, dueLabel } from "@/lib/due";

interface Step {
  title: string;
  detail: string;
  href: string;
  action: string;
}

/**
 * 최종 제출물 → 요구사항 → 업무 → 충족 확인 → 검토 순서에서 아직 안 끝난 첫 단계를 고른다.
 * 화면이 판단하는 안내일 뿐이며, 충족·승인은 사람이 확인한다 (CONTRACTS 3장 1항).
 * 요구사항–업무 연결은 선택이므로, 충족 확인이 끝난 요구사항에는 업무를 요구하지 않는다.
 * canWrite=false(관찰자)에게는 할 일을 시키지 않고 현재 상태만 알린다.
 */
export function nextStepFor(projectId: string, d: Deliverable, tasks: Task[], canWrite: boolean): Step {
  const base = `/projects/${projectId}/deliverables/${d.id}`;
  const linked = tasks.filter((t) => t.deliverableId === d.id);
  const open = linked.filter((t) => t.status !== "DONE");
  const required = d.requirements.filter((r) => r.required);
  const unchecked = required.filter((r) => r.assessment == null);
  const uncovered = unchecked.filter((r) => !linked.some((t) => t.requirementId === r.id));
  const view = "최종 제출물 보기";

  if (d.status === "SUBMITTED") return { title: "제출 기록이 완료됐습니다", detail: "확정된 파일과 제출 경로·시각을 확인할 수 있습니다.", href: `${base}?tab=submit`, action: "제출 기록 보기" };
  if (d.status === "CONFIRMED") return { title: "학교에 제출한 뒤 기록을 남기세요", detail: "최종본이 확정됐습니다. 학교 시스템에 직접 제출한 뒤 제출 기록을 남기세요.", href: `${base}?tab=submit`, action: canWrite ? "확정본·제출 기록" : "확정본 보기" };
  if (d.status === "IN_REVIEW") {
    if (d.latestDecision === "APPROVED") return { title: "검토한 최종본을 확정할 차례입니다", detail: "팀장이 필수 요구사항과 승인된 파일을 확인한 뒤 최종본을 확정합니다.", href: `${base}?tab=submit`, action: "최종본 확인" };
    if (d.latestDecision === "CHANGES_REQUESTED") return { title: "검토 의견을 반영해 다시 요청하세요", detail: "미해결 피드백을 보완하고 수정한 파일 버전으로 검토를 요청하세요.", href: `${base}?tab=review`, action: "보완할 내용 보기" };
    return { title: "최종 파일 검토를 기다리고 있습니다", detail: "파일을 올린 사람 외의 팀원이 결과를 확인해 주세요.", href: `${base}?tab=review`, action: "검토 이어가기" };
  }

  if (d.requirements.length === 0) {
    return canWrite
      ? {
          title: "요구사항을 적으세요",
          detail: "과제 안내의 필수 내용과 형식을 요구사항으로 정리하면 업무를 나눌 수 있습니다.",
          href: base,
          action: "요구사항 적기",
        }
      : {
          title: "아직 요구사항이 없습니다",
          detail: "팀원이 과제 안내의 필수 내용과 형식을 요구사항으로 정리하면 여기에 진행 단계가 표시됩니다.",
          href: base,
          action: view,
        };
  }
  // 필수 요구사항이 모두 충족 확인됐다면 업무 없이도 다음 단계로 간다
  if (linked.length === 0 && (required.length === 0 || unchecked.length > 0)) {
    return canWrite
      ? {
          title: "업무를 만들어 팀원에게 나누세요",
          detail: `요구사항 ${d.requirements.length}개에 연결된 업무가 아직 없습니다.`,
          href: base,
          action: "업무 만들기",
        }
      : {
          title: "아직 업무가 나뉘지 않았습니다",
          detail: `요구사항 ${d.requirements.length}개에 연결된 업무가 아직 없습니다.`,
          href: base,
          action: view,
        };
  }
  if (uncovered.length > 0) {
    return {
      title: "업무가 없는 필수 요구사항이 있습니다",
      detail: `아직 충족 확인 전인 "${uncovered[0].content}"${uncovered.length > 1 ? ` 외 ${uncovered.length - 1}개` : ""}에 연결된 업무가 없습니다.`,
      href: base,
      action: canWrite ? "업무 연결하기" : view,
    };
  }
  if (open.length > 0) {
    return {
      title: `진행 중인 업무 ${open.length}개`,
      detail: `연결 업무 ${linked.length}개 중 ${linked.length - open.length}개 완료. ${
        canWrite ? "업무에서 진행 상태를 바꾸고 자료를 등록하세요." : "업무에서 진행 상황을 확인할 수 있습니다."
      }`,
      href: `/projects/${projectId}/board?deliverable=${d.id}`,
      action: "업무 이어하기",
    };
  }
  if (unchecked.length > 0) {
    const done = linked.length > 0 ? "업무는 모두 끝났습니다. " : "";
    return canWrite
      ? {
          title: "필수 요구사항 충족을 확인하세요",
          detail: `${done}필수 요구사항 ${required.length}개 중 ${required.length - unchecked.length}개를 확인했습니다.`,
          href: base,
          action: "충족 확인하기",
        }
      : {
          title: "필수 요구사항 충족 확인을 기다리고 있습니다",
          detail: `${done}필수 요구사항 ${required.length}개 중 ${required.length - unchecked.length}개가 확인됐습니다.`,
          href: base,
          action: view,
        };
  }
  return canWrite
    ? {
        title: "검토를 요청할 준비가 됐습니다",
        detail: "업무와 필수 요구사항 확인이 끝났습니다. 업무에서 자료를 등록하고, 최종 제출물에서 검토를 요청하세요.",
        href: base,
        action: "최종 제출물 열기",
      }
    : {
        title: "검토 요청 준비가 끝났습니다",
        detail: "업무와 필수 요구사항 확인이 끝났습니다. 팀원이 초안을 올리고 검토를 요청할 차례입니다.",
        href: base,
        action: view,
      };
}

export default function NextStepCard({
  projectId,
  deliverables,
  tasks,
  canWrite,
}: {
  projectId: string;
  deliverables: Deliverable[];
  tasks: Task[];
  canWrite: boolean;
}) {
  const agentStatus = useAgentStore(s => s.status[projectId]);
  const loadAgentStatus = useAgentStore(s => s.loadStatus);
  useEffect(() => { loadAgentStatus(projectId); }, [projectId,loadAgentStatus]);
  const help = agentStatus?.available ? suggestFor(agentStatus) : null;
  if (deliverables.length === 0) {
    return (
      <section
        className="mb-6 block rounded-xl border border-blue-500/30 bg-blue-500/10 p-5 hover:bg-blue-500/15"
      >
        <p className="text-xs font-semibold text-blue-700 dark:text-blue-300">다음 할 일</p>
        <h2 className="mt-1 font-semibold text-bb-text">{canWrite ? "최종 제출물부터 계획하기" : "아직 최종 제출물이 없습니다"}</h2>
        <p className="mt-2 text-sm text-bb-text2">
          {canWrite
            ? "제출 기한과 요구사항을 정하고, 필요한 업무를 팀원에게 연결하세요."
            : "팀원이 최종 제출물을 등록하면 여기에 다음 할 일이 표시됩니다."}
        </p>
        <div className="mt-3 flex flex-wrap gap-3"><Link href={`/projects/${projectId}/deliverables`} className="text-sm font-medium text-blue-700 dark:text-blue-300">{canWrite ? "최종 제출물 열기" : "최종 제출물 목록 보기"} →</Link><AgentButton projectId={projectId} skill="PLAN_FROM_BRIEF" /></div>
      </section>
    );
  }

  // 마감이 가장 가까운 최종 제출물부터 본다. 기한이 지난 최종 제출물도 포함한다
  const sorted = [...deliverables].sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  const target = sorted.find(d => d.status !== "SUBMITTED") ?? sorted[0];
  const step = nextStepFor(projectId, target, tasks, canWrite);

  return (
    <section className="mb-6 rounded-xl border border-blue-500/30 bg-blue-500/10 p-5" aria-labelledby="next-step-title">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <span className="font-semibold text-blue-700 dark:text-blue-300">다음 할 일</span>
        <span className="flex items-center gap-1 text-bb-text2">
          <ClipboardList size={12} />
          {target.title}
        </span>
        <span className={`rounded border px-2 py-0.5 font-medium ${DUE_BADGE[dueTone(target.dueDate)]}`}>{dueLabel(target.dueDate)}</span>
      </div>
      <h2 id="next-step-title" className="mt-1.5 font-semibold text-bb-text">
        {step.title}
      </h2>
      <p className="mt-1 text-sm text-bb-text2">{step.detail}</p>
      <div className="mt-3 flex flex-wrap items-center gap-4">
        <Link href={step.href} className="inline-flex items-center gap-1 text-sm font-medium text-blue-700 dark:text-blue-300 hover:underline">
          {step.action}
          <ArrowRight size={14} />
        </Link>
        {sorted.length > 1 && (
          <Link href={`/projects/${projectId}/deliverables`} className="text-xs text-bb-text2 hover:text-bb-text">
            다른 최종 제출물 {sorted.length - 1}개 보기
          </Link>
        )}
        {help && <AgentButton projectId={projectId} skill={help.skill} input={help.skill === "BREAK_DOWN_TASKS" ? help.input : undefined} titleExtra={help.deliverable?.title} />}
      </div>
    </section>
  );
}
