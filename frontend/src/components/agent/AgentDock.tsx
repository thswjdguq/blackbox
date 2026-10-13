"use client";

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { Send, Sparkles, X } from "lucide-react";
import { useAgentStore } from "@/lib/store/agentStore";
import { BRIEF_MAX, buttonSkills, canAsk, QUESTION_MAX, SKILLS, skillLabel, suggestFor } from "@/lib/agent/skills";
import { shortDate } from "@/lib/due";
import RunEntryView from "./RunEntryView";
import ProposalList from "./ProposalList";

const EXAMPLES = ["늦은 업무가 있나요?", "이번 주에 끝내야 할 일은 무엇인가요?", "일이 몰린 사람이 있나요?"];

/** 768px 미만이면 전체 화면 대화 상자, 그 이상이면 옆 칸 (화면 문서 3.2) */
function useSmallScreen() {
  const [small, setSmall] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia("(max-width: 767px)");
    const apply = () => setSmall(mq.matches);
    apply();
    mq.addEventListener("change", apply);
    return () => mq.removeEventListener("change", apply);
  }, []);
  return small;
}

/** 보이는 여는 버튼(사이드바 또는 작은 화면 상단 바)으로 초점을 돌려준다 */
function focusLauncher() {
  const buttons = Array.from(document.querySelectorAll<HTMLElement>("[data-agent-launcher]"));
  buttons.find((b) => b.offsetParent !== null)?.focus();
}

/** 입력이 필요한 실행을 고른 뒤 창에 여는 입력 칸 */
type Pending = { skill: "PLAN_FROM_BRIEF" } | { skill: "BREAK_DOWN_TASKS" } | null;

/**
 * AI 도우미 창 (K-30, 화면 문서 3장). app/layout.tsx에 한 번만 둔다.
 * 화면을 옮겨도 닫히지 않고, 큰 화면에서는 본문이 창만큼 줄어든다(globals.css의 html[data-agent-open]).
 * 탭은 "도우미"(실행과 답)와 "제안"(카드 목록). AI가 꺼져 있어도 대기 카드가 있으면 "제안" 탭만 연다(4.1).
 */
export default function AgentDock() {
  const pathname = usePathname() ?? "";
  const projectId = pathname.match(/^\/projects\/([^/]+)/)?.[1] ?? null;
  const open = useAgentStore((s) => s.open);
  const setOpen = useAgentStore((s) => s.setOpen);
  const tab = useAgentStore((s) => s.tab);
  const setTab = useAgentStore((s) => s.setTab);
  const status = useAgentStore((s) => (projectId ? s.status[projectId] : undefined));
  const statusError = useAgentStore((s) => (projectId ? s.statusErrors[projectId] : undefined));
  const runs = useAgentStore((s) => (projectId ? s.runs[projectId] : undefined)) ?? [];
  const proposals = useAgentStore((s) => s.proposals);
  const run = useAgentStore((s) => s.run);
  const loadStatus = useAgentStore((s) => s.loadStatus);
  const pendingInput = useAgentStore((s) => s.pendingInput);
  const clearPendingInput = useAgentStore((s) => s.clearPendingInput);
  const small = useSmallScreen();
  const [question, setQuestion] = useState("");
  const [pending, setPending] = useState<Pending>(null);
  const [brief, setBrief] = useState("");
  const [pickedDeliverable, setPickedDeliverable] = useState("");
  const headingRef = useRef<HTMLHeadingElement>(null);
  const dockRef = useRef<HTMLElement>(null);

  const usable = !!status && (status.available || status.pendingProposalCount > 0);
  const visible = open && !!projectId;
  const proposalsOnly = !!status && !status.available;
  const activeTab = proposalsOnly ? "proposals" : tab;

  // 본문을 줄이는 것은 화면마다 고치지 않고 한 곳(globals.css)에서 한다
  useEffect(() => {
    document.documentElement.toggleAttribute("data-agent-open", visible);
    return () => document.documentElement.removeAttribute("data-agent-open");
  }, [visible]);

  // 열 때 상태를 다시 부른다(4.1)
  useEffect(() => {
    if (open && projectId) loadStatus(projectId, true);
  }, [open, projectId, loadStatus]);

  // 창이 보이게 되면 초점을 창 안으로 옮긴다(8장)
  useLayoutEffect(() => {
    if (visible) headingRef.current?.focus();
  }, [visible]);

  // 다른 프로젝트로 가면 고르던 입력은 버린다
  useEffect(() => {
    setPending(null);
    setQuestion(""); setBrief(""); setPickedDeliverable("");
  }, [projectId]);

  // 전체 화면 대화 상자에서는 키보드 초점이 뒤의 업무 화면으로 빠져나가지 않게 한다.
  useEffect(() => {
    if (!small || !visible) return;
    const trap = (e: KeyboardEvent) => {
      if (e.key !== "Tab" || !dockRef.current) return;
      const controls = Array.from(dockRef.current.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], textarea:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex="0"]')).filter(el => el.offsetParent !== null);
      const first = controls[0], last = controls.at(-1);
      if (!first || !last) return;
      if (e.shiftKey && (document.activeElement === first || document.activeElement === headingRef.current)) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    };
    const dock = dockRef.current;
    dock?.addEventListener("keydown",trap);
    return () => dock?.removeEventListener("keydown",trap);
  }, [small,visible,usable]);

  // 화면마다의 버튼(4.9)이 입력이 필요한 실행을 골랐으면 입력 칸을 연다
  useEffect(() => {
    if (!pendingInput || pendingInput.projectId !== projectId || !status) return;
    if (pendingInput.skill === "PLAN_FROM_BRIEF") setPending({ skill: "PLAN_FROM_BRIEF" });
    if (pendingInput.skill === "BREAK_DOWN_TASKS") {
      setPickedDeliverable(status.stages.find((s) => s.stage !== "NO_REQUIREMENT")?.deliverableId ?? "");
      setPending({ skill: "BREAK_DOWN_TASKS" });
    }
    clearPendingInput();
  }, [pendingInput, projectId, status, clearPendingInput]);

  if (!visible || !projectId) return null;

  const close = () => {
    setOpen(false);
    requestAnimationFrame(focusLauncher);
  };
  if (!usable || !status) return <aside ref={dockRef} aria-label="팀프로젝트 AI 도우미" {...(small ? {role:"dialog", "aria-modal":true} : {})} data-testid="agent-dock"
    onKeyDown={e => { if(e.key === "Escape") {e.stopPropagation();close();} }} className="fixed inset-0 z-40 flex flex-col bg-bb-surface md:inset-auto md:right-0 md:top-0 md:h-screen md:w-[420px] md:border-l md:border-bb-border md:shadow-xl">
    <header className="p-5 border-b border-bb-border flex justify-between items-center"><h2 ref={headingRef} tabIndex={-1} className="font-semibold flex gap-2 items-center outline-none"><Sparkles size={18} className="text-bb-primary"/>팀프로젝트 도우미</h2><button type="button" aria-label="AI 도우미 닫기" onClick={close} className="p-2 rounded-lg hover:bg-bb-bg"><X size={18}/></button></header>
    <div className="p-5 space-y-5 overflow-y-auto"><p className="text-sm text-bb-text2">과제 계획을 세우고, 업무를 나누고, 다음 할 일을 정리합니다.</p>
      <div className="space-y-3">{[["과제 안내문으로 계획 만들기","제출물·요구사항·업무 초안을 함께 정리"],["요구사항을 업무로 나누기","완료 기준이 있는 업무 후보 제안"],["지금 상태 살펴보기","진행 상황과 먼저 살펴볼 업무 요약"]].map(([title,description]) => <section key={title} className="rounded-xl border border-bb-border bg-bb-bg p-4"><h3 className="text-sm font-semibold">{title}</h3><p className="mt-1 text-xs text-bb-text2">{description}</p></section>)}</div>
      <div role="status" className="rounded-xl border border-bb-border p-4"><p className="text-sm">{statusError ?? status?.unavailableReason ?? "AI 도우미 연결 상태를 확인하고 있습니다."}</p><p className="mt-2 text-xs text-bb-text2">{status || statusError ? "지금은 AI 실행을 사용할 수 없습니다. 제출물과 업무는 직접 작성할 수 있습니다." : "연결 확인 후 사용 가능한 기능이 표시됩니다."}</p><button type="button" onClick={() => loadStatus(projectId,true)} className="mt-3 text-xs text-bb-primary underline">다시 확인</button></div>
      <p className="text-xs leading-relaxed text-bb-text2">AI의 제안은 팀원이 내용을 확인하고 채택할 때 프로젝트에 반영됩니다.</p>
    </div>
  </aside>;
  const onNavigate = () => {
    // 작은 화면에서는 링크를 누르면 창을 닫고 간다(5.3). 내용은 남는다
    if (small) setOpen(false);
  };
  const running = runs.some((r) => r.state === "running");
  const suggestion = suggestFor(status);
  const skills = buttonSkills(status);
  const askAllowed = canAsk(status);
  const trimmed = question.trim();

  const send = () => {
    if (!trimmed || running || trimmed.length > QUESTION_MAX) return;
    run(projectId, "ASK", { question: trimmed }, trimmed);
    setQuestion("");
  };

  /** 실행을 시작한다. 입력이 필요하고 아직 없으면 입력 칸을 연다(3.4의 표) */
  const start = (skill: string, input?: object, titleExtra?: string) => {
    const kind = SKILLS[skill]?.input;
    if (!kind) return;
    if (kind === "brief" && !input) {
      setPending({ skill: "PLAN_FROM_BRIEF" });
      return;
    }
    if (kind === "deliverable" && !input) {
      setPickedDeliverable(status.stages.find((s) => s.stage !== "NO_REQUIREMENT")?.deliverableId ?? "");
      setPending({ skill: "BREAK_DOWN_TASKS" });
      return;
    }
    setPending(null);
    run(projectId, skill, input ?? {}, titleExtra ? `${skillLabel(status, skill)} · ${titleExtra}` : skillLabel(status, skill));
  };

  const pendingForm = pending && (
    <section aria-labelledby="agent-input" className="rounded-lg border border-bb-primary/30 bg-bb-bg p-3">
      <h3 id="agent-input" className="text-xs font-semibold text-bb-text">{skillLabel(status, pending.skill)}</h3>
      {pending.skill === "PLAN_FROM_BRIEF" ? (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const text = brief.trim();
            if (!text || running) return;
            start("PLAN_FROM_BRIEF", { briefText: text });
            setBrief("");
          }}
          className="mt-2 space-y-2"
        >
          <label htmlFor="agent-brief" className="block text-xs text-bb-text2">과제 안내문을 그대로 붙여 넣으세요. 원문은 저장하지 않습니다.</label>
          <textarea
            id="agent-brief"
            value={brief}
            onChange={(e) => setBrief(e.target.value)}
            maxLength={BRIEF_MAX}
            rows={8}
            className="w-full resize-y rounded-lg border border-bb-border bg-bb-surface px-3 py-2 text-sm text-bb-text focus:border-bb-primary focus:outline-none"
          />
          <div className="flex items-center justify-between gap-2">
            <span className="text-[11px] text-bb-text2">{brief.length.toLocaleString()} / {BRIEF_MAX.toLocaleString()}자</span>
            <div className="flex gap-2">
              <button type="button" onClick={() => setPending(null)} className="px-2 py-1.5 text-xs text-bb-text2 hover:text-bb-text">취소</button>
              <button type="submit" disabled={!brief.trim() || running} className="rounded-lg bg-bb-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-bb-primary-h disabled:opacity-50">
                계획 만들기
              </button>
            </div>
          </div>
        </form>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const d = status.stages.find((s) => s.deliverableId === pickedDeliverable);
            if (!d || d.stage === "NO_REQUIREMENT" || running) return;
            start("BREAK_DOWN_TASKS", { deliverableId: d.deliverableId }, d.title);
          }}
          className="mt-2 space-y-2"
        >
          <label htmlFor="agent-deliverable" className="block text-xs text-bb-text2">업무로 나눌 제출물</label>
          <select
            id="agent-deliverable"
            value={pickedDeliverable}
            onChange={(e) => setPickedDeliverable(e.target.value)}
            className="w-full rounded-lg border border-bb-border bg-bb-surface px-3 py-2 text-sm text-bb-text focus:border-bb-primary focus:outline-none"
          >
            {status.stages.length === 0 && <option value="">제출물이 없습니다</option>}
            {status.stages.map((s) => (
              <option key={s.deliverableId} value={s.deliverableId} disabled={s.stage === "NO_REQUIREMENT"}>
                {s.title} · 기한 {shortDate(s.dueDate)}{s.stage === "NO_REQUIREMENT" ? " (요구사항이 없습니다)" : ""}
              </option>
            ))}
          </select>
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setPending(null)} className="px-2 py-1.5 text-xs text-bb-text2 hover:text-bb-text">취소</button>
            <button
              type="submit"
              disabled={running || !status.stages.some((s) => s.deliverableId === pickedDeliverable && s.stage !== "NO_REQUIREMENT")}
              className="rounded-lg bg-bb-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-bb-primary-h disabled:opacity-50"
            >
              업무로 나누기
            </button>
          </div>
        </form>
      )}
    </section>
  );

  const helpSections = (
    <div className="space-y-5">
      {suggestion && (
        <section aria-labelledby="agent-suggest">
          <h3 id="agent-suggest" className="text-xs font-semibold text-bb-text2">지금 단계에 맞는 도움</h3>
          <div className="mt-2 rounded-lg border border-bb-border bg-bb-bg p-3">
            {suggestion.deliverable && (
              <p className="text-sm text-bb-text">{suggestion.deliverable.title} · 기한 {shortDate(suggestion.deliverable.dueDate)}</p>
            )}
            <p className="mt-0.5 text-xs text-bb-text2">{suggestion.sentence}</p>
            <button
              type="button"
              disabled={running}
              onClick={() =>
                // 업무 나누기는 제출물이 정해져 있으므로 바로 실행한다(4.4)
                SKILLS[suggestion.skill]?.input === "deliverable"
                  ? start(suggestion.skill, suggestion.input, suggestion.deliverable?.title)
                  : start(suggestion.skill)
              }
              className="mt-2 rounded-lg border border-bb-primary/40 px-3 py-1.5 text-xs text-bb-primary hover:bg-bb-primary/10 disabled:opacity-50"
            >
              {skillLabel(status, suggestion.skill)}
            </button>
          </div>
        </section>
      )}
      {skills.length > 0 && (
        <section aria-labelledby="agent-skills">
          <h3 id="agent-skills" className="text-xs font-semibold text-bb-text2">할 수 있는 일</h3>
          <div className="mt-2 flex flex-wrap gap-2">
            {skills.map((s) => (
              <button
                key={s.skill}
                type="button"
                disabled={running}
                onClick={() => start(s.skill)}
                className="rounded-lg border border-bb-border px-3 py-1.5 text-xs text-bb-text hover:bg-bb-surface2 disabled:opacity-50"
              >
                {s.label}
              </button>
            ))}
          </div>
        </section>
      )}
      {runs.length === 0 && askAllowed && (
        <section aria-labelledby="agent-examples">
          <h3 id="agent-examples" className="text-xs font-semibold text-bb-text2">이렇게 물어보세요</h3>
          <div className="mt-2 flex flex-wrap gap-2">
            {EXAMPLES.map((q) => (
              // 누르면 입력 칸에 들어가고, 보내는 것은 사람이 한다(4.6)
              <button key={q} type="button" onClick={() => setQuestion(q)} className="rounded-full bg-bb-surface2 px-3 py-1 text-xs text-bb-text2 hover:text-bb-text">
                {q}
              </button>
            ))}
          </div>
        </section>
      )}
    </div>
  );

  const tabCls = (on: boolean) =>
    `rounded-md px-3 py-1 text-xs ${on ? "bg-bb-surface2 font-medium text-bb-text" : "text-bb-text2 hover:text-bb-text"}`;

  return (
    <aside
      ref={dockRef}
      aria-label="AI 도우미"
      {...(small ? { role: "dialog", "aria-modal": true } : {})}
      data-testid="agent-dock"
      onKeyDown={(e) => {
        if (e.key === "Escape") {
          e.stopPropagation();
          close();
        }
      }}
      // 업무 창(z-50)이 이 창 위에 열린다
      className="fixed inset-0 z-40 flex flex-col bg-bb-surface md:inset-auto md:right-0 md:top-0 md:h-screen md:w-[420px] md:border-l md:border-bb-border md:shadow-2xl md:shadow-black/30"
    >
      <header className="border-b border-bb-border px-5 pb-3 pt-4">
        <div className="flex items-center justify-between">
          <h2 ref={headingRef} tabIndex={-1} className="flex items-center gap-2 text-base font-semibold text-bb-text outline-none">
            <Sparkles size={16} className="text-bb-primary" aria-hidden="true" />
            팀프로젝트 도우미
          </h2>
          <button type="button" onClick={close} aria-label="AI 도우미 닫기" className="rounded-lg p-1.5 text-bb-text2 hover:bg-bb-surface2 hover:text-bb-text">
            <X size={16} />
          </button>
        </div>
        <div role="tablist" aria-label="AI 도우미 보기" className="mt-3 flex w-fit rounded-lg border border-bb-border bg-bb-bg p-0.5">
          <button type="button" role="tab" aria-selected={activeTab === "assistant"} disabled={proposalsOnly} onClick={() => setTab("assistant")} className={`${tabCls(activeTab === "assistant")} disabled:opacity-40`}>
            도우미
          </button>
          <button type="button" role="tab" aria-selected={activeTab === "proposals"} onClick={() => setTab("proposals")} className={tabCls(activeTab === "proposals")}>
            제안{status.pendingProposalCount > 0 && <span className="ml-1 rounded-full bg-bb-primary px-1.5 text-[10px] text-white">{status.pendingProposalCount}</span>}
          </button>
        </div>
      </header>

      <div className="flex-1 space-y-4 overflow-y-auto px-5 py-4" aria-live="polite">
        {activeTab === "proposals" ? (
          <>
            {proposalsOnly && (
              <p className="text-xs text-bb-text2">{status.unavailableReason ?? "AI 기능을 쓸 수 없습니다."} 이미 만들어진 제안은 제안 탭에서 볼 수 있습니다.</p>
            )}
            <ProposalList projectId={projectId} status={status} onNavigate={onNavigate} />
          </>
        ) : proposalsOnly ? null : (
          <>
            {pendingForm}
            {runs.length === 0 ? (
              helpSections
            ) : (
              <>
                <details className="text-xs text-bb-text2">
                  <summary className="cursor-pointer hover:text-bb-text">다른 도움 보기</summary>
                  <div className="mt-3">{helpSections}</div>
                </details>
                {runs.map((r) => (
                  <RunEntryView
                    key={r.localId}
                    entry={r}
                    projectId={projectId}
                    proposals={proposals}
                    status={status}
                    onRetry={() => run(projectId, r.skill, r.input, r.title)}
                    onNavigate={onNavigate}
                  />
                ))}
              </>
            )}
          </>
        )}
      </div>

      <footer className="border-t border-bb-border px-5 py-3">
        {activeTab === "assistant" && askAllowed && !proposalsOnly && (
          <form onSubmit={(e) => { e.preventDefault(); send(); }} className="flex items-end gap-2">
            <label htmlFor="agent-question" className="sr-only">프로젝트에 대해 물어보기</label>
            <textarea
              id="agent-question"
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={(e) => {
                // Enter로 보내고 Shift+Enter로 줄을 바꾼다. 한글 조합 중의 Enter는 보내지 않는다
                if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
                  e.preventDefault();
                  send();
                }
              }}
              rows={2}
              maxLength={QUESTION_MAX}
              disabled={running}
              placeholder="프로젝트에 대해 물어보세요. 질문마다 따로 답합니다"
              className="flex-1 resize-none rounded-lg border border-bb-border bg-bb-bg px-3 py-2 text-sm text-bb-text placeholder:text-bb-text2/70 focus:border-bb-primary focus:outline-none disabled:opacity-60"
            />
            <button type="submit" disabled={running || !trimmed} aria-label="보내기" className="rounded-lg bg-bb-primary p-2.5 text-white hover:bg-bb-primary-h disabled:opacity-50">
              <Send size={15} />
            </button>
          </form>
        )}
        <p className="mt-2 text-[11px] leading-relaxed text-bb-text2">
          프로젝트 기록(이름, 역할, 제출물·업무 내용)이 외부 AI 모델로 전송됩니다. 답은 새로 고치면 사라지고 제안은 남습니다.
        </p>
      </footer>
    </aside>
  );
}
