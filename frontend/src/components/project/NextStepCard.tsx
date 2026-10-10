"use client";

import Link from "next/link";
import { ArrowRight, ClipboardList } from "lucide-react";
import type { Deliverable } from "@/types/deliverable";
import type { Task } from "@/types/task";
import { daysLeft, dueLabel } from "@/lib/due";

interface Step {
  title: string;
  detail: string;
  href: string;
  action: string;
}

/**
 * 제출물 → 요구사항 → 업무 → 충족 확인 → 검토 순서에서 아직 안 끝난 첫 단계를 고른다.
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
  const view = "제출물 보기";

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
        canWrite ? "업무 보드에서 진행 상황을 바꾸세요." : "업무 보드에서 진행 상황을 볼 수 있습니다."
      }`,
      href: `/projects/${projectId}/board?deliverable=${d.id}`,
      action: "업무 보드 열기",
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
        detail: "업무와 필수 요구사항 확인이 끝났습니다. 초안을 파일 금고에 올리고 검토를 요청하세요.",
        href: base,
        action: "제출물 열기",
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
  if (deliverables.length === 0) {
    return (
      <Link
        href={`/projects/${projectId}/deliverables`}
        className="mb-6 block rounded-xl border border-teal-500/40 bg-teal-500/10 p-5 hover:bg-teal-500/15"
      >
        <p className="text-xs font-semibold text-teal-500">다음 할 일</p>
        <h2 className="mt-1 font-semibold text-bb-text">{canWrite ? "제출물부터 계획하기" : "아직 제출물이 없습니다"}</h2>
        <p className="mt-2 text-sm text-bb-text2">
          {canWrite
            ? "제출 기한과 요구사항을 정하고, 필요한 업무를 팀원에게 연결하세요."
            : "팀원이 제출물을 등록하면 여기에 다음 할 일이 표시됩니다."}
        </p>
        <span className="mt-3 inline-block text-sm font-medium text-teal-500">{canWrite ? "제출물 열기" : "제출물 목록 보기"} →</span>
      </Link>
    );
  }

  // 마감이 가장 가까운 제출물부터 본다. 기한이 지난 제출물도 포함한다
  const sorted = [...deliverables].sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  const target = sorted[0];
  const step = nextStepFor(projectId, target, tasks, canWrite);
  const overdue = daysLeft(target.dueDate) < 0;

  return (
    <section className="mb-6 rounded-xl border border-teal-500/40 bg-teal-500/10 p-5" aria-labelledby="next-step-title">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <span className="font-semibold text-teal-500">다음 할 일</span>
        <span className="flex items-center gap-1 text-bb-text2">
          <ClipboardList size={12} />
          {target.title}
        </span>
        <span className={overdue ? "text-red-400 font-medium" : "text-bb-text2"}>{dueLabel(target.dueDate)}</span>
      </div>
      <h2 id="next-step-title" className="mt-1.5 font-semibold text-bb-text">
        {step.title}
      </h2>
      <p className="mt-1 text-sm text-bb-text2">{step.detail}</p>
      <div className="mt-3 flex flex-wrap items-center gap-4">
        <Link href={step.href} className="inline-flex items-center gap-1 text-sm font-medium text-teal-500 hover:underline">
          {step.action}
          <ArrowRight size={14} />
        </Link>
        {sorted.length > 1 && (
          <Link href={`/projects/${projectId}/deliverables`} className="text-xs text-bb-text2 hover:text-bb-text">
            다른 제출물 {sorted.length - 1}개 보기
          </Link>
        )}
      </div>
    </section>
  );
}
