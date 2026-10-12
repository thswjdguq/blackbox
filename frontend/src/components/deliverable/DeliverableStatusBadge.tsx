import type { Deliverable } from "@/types/deliverable";

interface Badge {
  label: string;
  cls: string;
}

/**
 * 제출물이 어디까지 왔는지 (K-20 2장 status + 최신 회차의 결정 latestDecision).
 * 수정 요청을 받으면 status는 DRAFT(보완 중)이고, 승인되면 IN_REVIEW(확정 대기)다.
 * 서버가 status를 주지 않으면(A-20 전) null.
 */
export function deliverableBadge(d: Pick<Deliverable, "status" | "latestDecision">): Badge | null {
  switch (d.status) {
    case "SUBMITTED":
      return { label: "제출 기록됨", cls: "bg-teal-500/15 text-teal-400" };
    case "CONFIRMED":
      return { label: "확정됨 · 제출 기록 전", cls: "bg-indigo-500/15 text-indigo-400" };
    case "IN_REVIEW":
      if (d.latestDecision === "APPROVED") return { label: "승인됨 · 확정 전", cls: "bg-emerald-500/15 text-emerald-400" };
      return { label: "검토 중", cls: "bg-sky-500/15 text-sky-400" };
    case "DRAFT":
      if (d.latestDecision === "CHANGES_REQUESTED") return { label: "수정 요청 받음", cls: "bg-amber-500/15 text-amber-400" };
      return { label: "작성 중", cls: "bg-bb-surface2 text-bb-text2" };
    default:
      return null;
  }
}

export default function DeliverableStatusBadge({ deliverable }: { deliverable: Pick<Deliverable, "status" | "latestDecision"> }) {
  const badge = deliverableBadge(deliverable);
  if (!badge) return null;
  return (
    <span data-testid="deliverable-status" className={`shrink-0 whitespace-nowrap rounded px-1.5 py-0.5 text-[11px] font-medium ${badge.cls}`}>
      {badge.label}
    </span>
  );
}
