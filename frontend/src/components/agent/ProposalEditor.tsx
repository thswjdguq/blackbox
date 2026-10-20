"use client";

import { useEffect, useRef, useState } from "react";
import { Loader2, Plus, Trash2 } from "lucide-react";
import { saveProposal } from "@/lib/api/agent";
import { sessionGeneration } from "@/lib/sessionCache";
import { apiError } from "@/lib/apiError";
import { fieldName, fieldProblems, problemSummary } from "@/lib/agent/problems";
import { useAgentStore } from "@/lib/store/agentStore";
import type { PlanContent, Proposal, TasksContent } from "@/types/agent";
import OptionalDeadlineTime from "@/components/OptionalDeadlineTime";

const INPUT = "w-full rounded-md border border-bb-border bg-bb-surface px-2 py-1.5 text-xs text-bb-text focus:border-bb-primary focus:outline-none";
const ROW_BTN = "inline-flex min-h-[44px] items-center gap-1 rounded-md px-2 text-[11px] text-bb-text2 hover:bg-bb-surface2 hover:text-bb-text md:min-h-0 md:py-1";

type PlanTask = PlanContent["tasks"][number];
type TaskItem = TasksContent["tasks"][number];

/** 칸 아래 이유. name은 서버가 400에 적는 경로와 같다(6.5) */
function Problem({ name, problems }: { name: string; problems: Record<string, string> }) {
  return problems[name] ? <p className="mt-0.5 text-[11px] text-red-400">{problems[name]}</p> : null;
}

/** 요구사항 줄을 새로 더할 때 카드 안에서 겹치지 않는 key(4.7) */
function newKey(used: string[]) {
  let i = 1;
  while (used.includes(`n${i}`)) i++;
  return `n${i}`;
}

/**
 * 제안 카드의 고치는 모양 (화면 문서 4.7 "고치기"). 칸 이름·길이 제한은 최종 제출물 입력·업무 입력과 같다.
 * 저장은 PUT /proposals/{id}로 content를 통째로 보낸다(고치지 않은 칸도 모두). 응답의 카드로 바꾼다.
 * 채택 버튼은 여기에 없다. 저장한 뒤 펼친 모양에서 채택한다(보이는 것과 만들어지는 것이 같게).
 */
export default function ProposalEditor({
  proposal,
  projectId,
  initialProblems = {},
  focusDue = false,
  onDone,
}: {
  proposal: Proposal;
  projectId: string;
  /** 채택이 400이었으면 그 칸들을 처음부터 표시한다 */
  initialProblems?: Record<string, string>;
  /** "기한을 채워야 채택할 수 있습니다"에서 왔으면 기한 칸에 초점 */
  focusDue?: boolean;
  onDone: () => void;
}) {
  const putProposal = useAgentStore((s) => s.putProposal);
  const loadDeliverables = useAgentStore((s) => s.loadDeliverables);
  const deliverables = useAgentStore((s) => s.deliverables[projectId]);
  const status = useAgentStore((s) => s.status[projectId]);
  const initial = JSON.stringify(proposal.content);
  const [content, setContent] = useState(() => JSON.parse(initial) as Proposal["content"]);
  const [problems, setProblems] = useState<Record<string, string>>(initialProblems);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [confirmClose, setConfirmClose] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);
  const dirty = JSON.stringify(content) !== initial;

  useEffect(() => {
    if (proposal.kind === "TASKS") loadDeliverables(projectId);
  }, [proposal.kind, projectId, loadDeliverables]);

  useEffect(() => {
    const target = focusDue ? '[name="deliverable.dueDate"]' : Object.keys(initialProblems).length ? `[name="${Object.keys(initialProblems)[0]}"]` : "input, textarea";
    formRef.current?.querySelector<HTMLElement>(target)?.focus();
    // 열릴 때 한 번만
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const plan = proposal.kind === "DELIVERABLE_PLAN" ? (content as PlanContent) : null;
  const tasksContent = proposal.kind === "TASKS" ? (content as TasksContent) : null;
  const setPlan = (fn: (c: PlanContent) => PlanContent) => setContent((c) => fn(c as PlanContent));
  const setTasks = (fn: (c: TasksContent) => TasksContent) => setContent((c) => fn(c as TasksContent));
  const tasks: (PlanTask | TaskItem)[] = (plan ?? tasksContent)!.tasks;

  const updateTask = (i: number, patch: Partial<PlanTask & TaskItem>) => {
    if (plan) setPlan((c) => ({ ...c, tasks: c.tasks.map((t, j) => (j === i ? { ...t, ...patch } : t)) }));
    else setTasks((c) => ({ ...c, tasks: c.tasks.map((t, j) => (j === i ? { ...t, ...patch } : t)) }));
  };
  const addTask = () => {
    if (plan) setPlan((c) => ({ ...c, tasks: [...c.tasks, { title: "", completionCriteria: null, dueDate: null, requirementKey: null }] }));
    else setTasks((c) => ({ ...c, tasks: [...c.tasks, { title: "", completionCriteria: null, dueDate: null, requirementId: null }] }));
  };
  const removeTask = (i: number) => {
    if (plan) setPlan((c) => ({ ...c, tasks: c.tasks.filter((_, j) => j !== i) }));
    else setTasks((c) => ({ ...c, tasks: c.tasks.filter((_, j) => j !== i) }));
  };

  // 업무 카드의 연결할 요구사항은 그 최종 제출물의 요구사항에서 고른다
  const deliverableRequirements =
    tasksContent?.deliverableId ? deliverables?.find((d) => d.id === tasksContent.deliverableId)?.requirements ?? [] : [];
  const deliverableTitle = tasksContent
    ? tasksContent.deliverableId == null
      ? "최종 제출물 연결 없음"
      : status?.stages.find((s) => s.deliverableId === tasksContent.deliverableId)?.title ?? "삭제된 최종 제출물"
    : "";

  /** 서버에 보내기 전에 빈 필수 칸만 막는다. 나머지 규칙은 서버의 400을 칸에 표시한다 */
  const localProblems = (): Record<string, string> => {
    const out: Record<string, string> = {};
    if (plan?.deliverable.dueTime != null && !/^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/.test(plan.deliverable.dueTime)) out["deliverable.dueTime"] = "마감 시간을 입력하거나 시간 삭제를 눌러 주세요";
    if (plan && !plan.deliverable.title.trim()) out["deliverable.title"] = "최종 제출물 제목을 입력해주세요";
    plan?.requirements.forEach((r, i) => { if (!r.content.trim()) out[`requirements[${i}].content`] = "요구사항 내용을 입력해주세요"; });
    tasks.forEach((t, i) => { if (!t.title.trim()) out[`tasks[${i}].title`] = "업무 제목을 입력해주세요"; });
    if (tasks.length === 0) out.tasks = "업무는 한 줄 이상 있어야 합니다";
    return out;
  };

  const save = async (e: React.FormEvent) => {
    const generation = sessionGeneration();
    e.preventDefault();
    const local = localProblems();
    if (Object.keys(local).length) {
      setProblems(local);
      setError("");
      return;
    }
    setSaving(true);
    setError("");
    setProblems({});
    try {
      const { data } = await saveProposal(projectId, proposal.id, content);
      if (generation !== sessionGeneration()) return;
      putProposal(data);
      onDone();
    } catch (err) {
      if (generation !== sessionGeneration()) return;
      const detail = apiError(err, "저장하지 못했습니다.");
      const found = fieldProblems(detail);
      setProblems(found);
      // 칸을 찾았으면 이유는 칸 아래에 있으므로 위에는 요약만
      setError(Object.keys(found).length ? problemSummary(detail) : detail);
    } finally {
      setSaving(false);
    }
  };

  const cancel = () => (dirty ? setConfirmClose(true) : onDone());
  // 칸에 붙지 않은 이유는 폼 위에 모아 보인다(4.7)
  const shown = new Set<string>([
    ...(plan ? ["deliverable.title", "deliverable.dueDate", "deliverable.submissionMethod", "deliverable.description"] : []),
    ...(plan?.requirements.flatMap((_, i) => [`requirements[${i}].content`]) ?? []),
    ...tasks.flatMap((_, i) => [`tasks[${i}].title`, `tasks[${i}].completionCriteria`, `tasks[${i}].dueDate`, plan ? `tasks[${i}].requirementKey` : `tasks[${i}].requirementId`]),
  ]);
  const loose = Object.entries(problems).filter(([k]) => !shown.has(k) && k !== "tasks");

  return (
    <form ref={formRef} onSubmit={save} data-testid="proposal-editor" className="space-y-3 text-xs" noValidate>
      {(error || loose.length > 0 || problems.tasks) && (
        <div role="alert" className="rounded-md bg-red-500/10 p-2 text-red-400">
          {error && <p>{error}</p>}
          {problems.tasks && <p>{problems.tasks}</p>}
          {loose.length > 0 && (
            <ul className="mt-1 list-disc pl-4">{loose.map(([k, v]) => <li key={k}>{fieldName(k)}: {v}</li>)}</ul>
          )}
        </div>
      )}

      {plan && (
        <fieldset className="space-y-2">
          <legend className="font-medium text-bb-text2">최종 제출물</legend>
          <label className="block">
            <span className="text-bb-text2">최종 제출물 제목 <span className="text-red-400">*</span></span>
            <input name="deliverable.title" value={plan.deliverable.title} maxLength={255} className={INPUT}
              onChange={(e) => setPlan((c) => ({ ...c, deliverable: { ...c.deliverable, title: e.target.value } }))} />
            <Problem name="deliverable.title" problems={problems} />
          </label>
          <label className="block">
            <span className="text-bb-text2">기한 <span className="text-bb-text2/70">(채택하려면 필요)</span></span>
            <input type="date" name="deliverable.dueDate" value={plan.deliverable.dueDate ?? ""} className={INPUT}
              onChange={(e) => setPlan((c) => ({ ...c, deliverable: { ...c.deliverable, dueDate: e.target.value || null } }))} />
            <Problem name="deliverable.dueDate" problems={problems} />
          </label>
          <OptionalDeadlineTime name="deliverable.dueTime" value={plan.deliverable.dueTime}
            onChange={dueTime => setPlan(c => ({ ...c, deliverable: { ...c.deliverable, dueTime } }))} />
          <Problem name="deliverable.dueTime" problems={problems} />
          <label className="block">
            <span className="text-bb-text2">제출 방법</span>
            <input name="deliverable.submissionMethod" value={plan.deliverable.submissionMethod ?? ""} maxLength={500} className={INPUT}
              onChange={(e) => setPlan((c) => ({ ...c, deliverable: { ...c.deliverable, submissionMethod: e.target.value || null } }))} />
            <Problem name="deliverable.submissionMethod" problems={problems} />
          </label>
          <label className="block">
            <span className="text-bb-text2">설명</span>
            <textarea name="deliverable.description" value={plan.deliverable.description ?? ""} maxLength={5000} rows={2} className={INPUT}
              onChange={(e) => setPlan((c) => ({ ...c, deliverable: { ...c.deliverable, description: e.target.value || null } }))} />
            <Problem name="deliverable.description" problems={problems} />
          </label>
        </fieldset>
      )}

      {plan && (
        <fieldset className="space-y-2">
          <legend className="font-medium text-bb-text2">요구사항 {plan.requirements.length}</legend>
          {plan.requirements.map((r, i) => (
            <div key={r.key} className="rounded-md border border-bb-border p-2">
              <label className="block">
                <span className="sr-only">요구사항 {i + 1} 내용</span>
                <input name={`requirements[${i}].content`} value={r.content} maxLength={1000} placeholder="요구사항 내용" className={INPUT}
                  onChange={(e) => setPlan((c) => ({ ...c, requirements: c.requirements.map((x, j) => (j === i ? { ...x, content: e.target.value } : x)) }))} />
              </label>
              <Problem name={`requirements[${i}].content`} problems={problems} />
              <div className="mt-1 flex items-center justify-between">
                <label className="flex items-center gap-1.5 text-bb-text2">
                  <input type="checkbox" name={`requirements[${i}].required`} checked={r.required}
                    onChange={(e) => setPlan((c) => ({ ...c, requirements: c.requirements.map((x, j) => (j === i ? { ...x, required: e.target.checked } : x)) }))} />
                  필수
                </label>
                {/* 지우면 그 요구사항을 가리키던 업무의 연결을 비운다. 그대로 두면 서버가 400으로 거절한다 */}
                <button type="button" aria-label={`요구사항 ${i + 1} 지우기`} className={ROW_BTN}
                  onClick={() => setPlan((c) => ({
                    ...c,
                    requirements: c.requirements.filter((_, j) => j !== i),
                    tasks: c.tasks.map((t) => (t.requirementKey === r.key ? { ...t, requirementKey: null } : t)),
                  }))}>
                  <Trash2 size={12} /> 지우기
                </button>
              </div>
            </div>
          ))}
          <button type="button" className={ROW_BTN}
            onClick={() => setPlan((c) => ({ ...c, requirements: [...c.requirements, { key: newKey(c.requirements.map((x) => x.key)), content: "", required: true }] }))}>
            <Plus size={12} /> 요구사항 더하기
          </button>
        </fieldset>
      )}

      <fieldset className="space-y-2">
        <legend className="font-medium text-bb-text2">
          업무 {tasks.length}{tasksContent && <span className="font-normal"> · {deliverableTitle}</span>}
        </legend>
        {tasks.map((t, i) => (
          <div key={i} className="space-y-1 rounded-md border border-bb-border p-2">
            <label className="block">
              <span className="sr-only">업무 {i + 1} 제목</span>
              <input name={`tasks[${i}].title`} value={t.title} maxLength={255} placeholder="업무 제목" className={INPUT}
                onChange={(e) => updateTask(i, { title: e.target.value })} />
            </label>
            <Problem name={`tasks[${i}].title`} problems={problems} />
            <label className="block">
              <span className="sr-only">업무 {i + 1} 완료 기준</span>
              <textarea name={`tasks[${i}].completionCriteria`} value={t.completionCriteria ?? ""} maxLength={2000} rows={1} placeholder="완료 기준" className={INPUT}
                onChange={(e) => updateTask(i, { completionCriteria: e.target.value || null })} />
            </label>
            <Problem name={`tasks[${i}].completionCriteria`} problems={problems} />
            <div className="grid grid-cols-2 gap-2">
              <label className="block">
                <span className="text-[11px] text-bb-text2">기한(선택)</span>
                <input type="date" name={`tasks[${i}].dueDate`} value={t.dueDate ?? ""} className={INPUT}
                  onChange={(e) => updateTask(i, { dueDate: e.target.value || null })} />
              </label>
              <label className="block">
                <span className="text-[11px] text-bb-text2">연결할 요구사항</span>
                {plan ? (
                  <select name={`tasks[${i}].requirementKey`} value={(t as PlanTask).requirementKey ?? ""} className={INPUT}
                    onChange={(e) => updateTask(i, { requirementKey: e.target.value || null })}>
                    <option value="">없음</option>
                    {plan.requirements.map((r) => <option key={r.key} value={r.key}>{r.content || "(내용 없음)"}</option>)}
                  </select>
                ) : (
                  <select name={`tasks[${i}].requirementId`} value={(t as TaskItem).requirementId ?? ""} className={INPUT}
                    onChange={(e) => updateTask(i, { requirementId: e.target.value || null })}>
                    <option value="">없음</option>
                    {deliverableRequirements.map((r) => <option key={r.id} value={r.id}>{r.content}</option>)}
                  </select>
                )}
              </label>
            </div>
            <Problem name={`tasks[${i}].dueDate`} problems={problems} />
            <Problem name={plan ? `tasks[${i}].requirementKey` : `tasks[${i}].requirementId`} problems={problems} />
            {/* 업무 카드는 업무가 한 줄 이상 남아야 한다 */}
            {(plan || tasks.length > 1) && (
              <div className="flex justify-end">
                <button type="button" aria-label={`업무 ${i + 1} 지우기`} className={ROW_BTN} onClick={() => removeTask(i)}>
                  <Trash2 size={12} /> 지우기
                </button>
              </div>
            )}
          </div>
        ))}
        <button type="button" className={ROW_BTN} onClick={addTask}>
          <Plus size={12} /> 업무 더하기
        </button>
      </fieldset>

      {/* 두 줄에 다른 key를 준다. 같은 자리의 버튼 요소를 다시 쓰면 "계속 고치기"를 누르는 동안 그 요소가
          submit "저장"으로 바뀌어 폼이 제출됐다 */}
      {confirmClose ? (
        <div key="confirm-close" role="alert" className="flex flex-wrap items-center gap-2 rounded-md bg-amber-500/10 p-2 text-amber-400">
          고친 내용이 저장되지 않았습니다. 닫을까요?
          <button type="button" onClick={() => setConfirmClose(false)} className="ml-auto px-2 py-1 text-bb-text2 hover:text-bb-text">계속 고치기</button>
          <button type="button" onClick={onDone} className="rounded-md border border-amber-500/40 px-2 py-1 text-amber-300 hover:bg-amber-500/10">닫기</button>
        </div>
      ) : (
        <div key="actions" className="flex justify-end gap-2">
          <button type="button" onClick={cancel} disabled={saving} className="px-3 py-1.5 text-bb-text2 hover:text-bb-text">취소</button>
          <button type="submit" disabled={saving} className="inline-flex items-center gap-1 rounded-lg bg-bb-primary px-3 py-1.5 font-medium text-white hover:bg-bb-primary-h disabled:opacity-50">
            {saving && <Loader2 size={12} className="animate-spin" />}
            저장
          </button>
        </div>
      )}
    </form>
  );
}
