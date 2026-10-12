"use client";

import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { Send, Sparkles, X } from "lucide-react";
import { useAgentStore } from "@/lib/store/agentStore";
import { buttonSkills, canAsk, QUESTION_MAX, skillLabel, suggestFor } from "@/lib/agent/skills";
import { shortDate } from "@/lib/due";
import RunEntryView from "./RunEntryView";

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

/**
 * AI 도우미 창 (K-30, 화면 문서 3장). app/layout.tsx에 한 번만 둔다.
 * 화면을 옮겨도 닫히지 않고, 큰 화면에서는 본문이 창만큼 줄어든다(globals.css의 html[data-agent-open]).
 * 1단계: 상태 조회, "지금 상태 살펴보기", "물어보기". 제안 카드의 채택·거절과 "제안" 탭은 2단계.
 */
export default function AgentDock() {
  const pathname = usePathname() ?? "";
  const projectId = pathname.match(/^\/projects\/([^/]+)/)?.[1] ?? null;
  const open = useAgentStore((s) => s.open);
  const setOpen = useAgentStore((s) => s.setOpen);
  const status = useAgentStore((s) => (projectId ? s.status[projectId] : undefined));
  const runs = useAgentStore((s) => (projectId ? s.runs[projectId] : undefined)) ?? [];
  const proposals = useAgentStore((s) => s.proposals);
  const run = useAgentStore((s) => s.run);
  const loadStatus = useAgentStore((s) => s.loadStatus);
  const small = useSmallScreen();
  const [question, setQuestion] = useState("");
  const headingRef = useRef<HTMLHeadingElement>(null);

  const visible = open && !!projectId && !!status?.available;

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

  if (!visible || !projectId || !status) return null;

  const close = () => {
    setOpen(false);
    requestAnimationFrame(focusLauncher);
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
  const start = (skill: string) => run(projectId, skill, {}, skillLabel(status, skill));

  const helpSections = (
    <div className="space-y-5">
      {suggestion && (
        <section aria-labelledby="agent-suggest">
          <h3 id="agent-suggest" className="text-xs font-semibold text-bb-text2">지금 단계에 맞는 도움</h3>
          <div className="mt-2 rounded-lg border border-bb-border bg-bb-bg p-3">
            <p className="text-sm text-bb-text">{suggestion.title} · 기한 {shortDate(suggestion.dueDate)}</p>
            <p className="mt-0.5 text-xs text-bb-text2">{suggestion.sentence}</p>
            <button
              type="button"
              disabled={running}
              onClick={() => start(suggestion.skill)}
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

  return (
    <aside
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
      <header className="flex items-center justify-between border-b border-bb-border px-5 py-4">
        <h2 ref={headingRef} tabIndex={-1} className="flex items-center gap-2 text-base font-semibold text-bb-text outline-none">
          <Sparkles size={16} className="text-bb-primary" aria-hidden="true" />
          AI 도우미
        </h2>
        <button type="button" onClick={close} aria-label="AI 도우미 닫기" className="rounded-lg p-1.5 text-bb-text2 hover:bg-bb-surface2 hover:text-bb-text">
          <X size={16} />
        </button>
      </header>

      <div className="flex-1 space-y-4 overflow-y-auto px-5 py-4" aria-live="polite">
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
                onRetry={() => run(projectId, r.skill, r.input, r.title)}
                // 작은 화면에서는 링크를 누르면 창을 닫고 간다(5.3). 내용은 남는다
                onNavigate={() => { if (small) setOpen(false); }}
              />
            ))}
          </>
        )}
      </div>

      <footer className="border-t border-bb-border px-5 py-3">
        {askAllowed && (
          <form
            onSubmit={(e) => { e.preventDefault(); send(); }}
            className="flex items-end gap-2"
          >
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
            <button
              type="submit"
              disabled={running || !trimmed}
              aria-label="보내기"
              className="rounded-lg bg-bb-primary p-2.5 text-white hover:bg-bb-primary-h disabled:opacity-50"
            >
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
