"use client";

import { useState } from "react";
import { ChevronDown, ChevronUp, AlertTriangle, HelpCircle, ExternalLink, Sparkles } from "lucide-react";

export interface GuideStep {
  emoji: string;
  title: string;
  lines?: string[];
  /** 외부 사이트로 바로 이동하는 버튼 (새 탭으로 열림) */
  link?: { href: string; label: string };
  /** URL/코드 등 시각적 예시 — 강조 텍스트(highlightPart)는 다른 색으로 표시 */
  example?: { before: string; highlightPart: string; after?: string; caption?: string };
}

export interface GuideFaq {
  q: string;
  a: string;
}

export interface GuideWarning {
  title: string;
  lines: string[];
  tone?: "red" | "amber";
}

interface BeginnerGuideProps {
  toggleLabel: string;
  steps: GuideStep[];
  warning?: GuideWarning;
  faq?: GuideFaq[];
  defaultOpen?: boolean;
}

const WARNING_TONE = {
  red:   { box: "bg-red-500/10 border-red-500/30",     icon: "text-red-400",   title: "text-red-400",   text: "text-red-300/90"   },
  amber: { box: "bg-amber-500/10 border-amber-500/30", icon: "text-amber-400", title: "text-amber-400", text: "text-amber-300/90" },
} as const;

export default function BeginnerGuide({ toggleLabel, steps, warning, faq, defaultOpen = false }: BeginnerGuideProps) {
  const [open, setOpen] = useState(defaultOpen);
  const tone = WARNING_TONE[warning?.tone ?? "red"];

  return (
    <div className="mb-5">
      {/* 토글 버튼 */}
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between gap-3 px-4 py-3 rounded-xl
                   bg-bb-bg border border-bb-border hover:border-indigo-500/40
                   text-left transition-colors"
      >
        <span className="flex items-center gap-2 text-sm font-medium text-bb-text">
          <Sparkles size={15} className="text-indigo-400 shrink-0" />
          {toggleLabel}
        </span>
        {open
          ? <ChevronUp size={16} className="text-bb-text2 shrink-0" />
          : <ChevronDown size={16} className="text-bb-text2 shrink-0" />}
      </button>

      {open && (
        <div className="mt-3 bg-bb-surface border border-bb-border rounded-xl p-5 space-y-0">
          {/* 단계 목록 */}
          {steps.map((step, i) => (
            <div key={i} className="flex gap-3">
              {/* 번호 배지 + 연결선 */}
              <div className="flex flex-col items-center shrink-0">
                <span className="w-7 h-7 rounded-full bg-indigo-500/15 text-indigo-400
                                 text-xs font-bold flex items-center justify-center">
                  {i + 1}
                </span>
                {i < steps.length - 1 && <div className="w-px flex-1 bg-bb-border my-1.5 min-h-[16px]" />}
              </div>

              {/* 본문 */}
              <div className="flex-1 min-w-0 pb-5">
                <p className="text-sm font-semibold text-bb-text mb-1.5 break-keep">
                  <span className="mr-1.5">{step.emoji}</span>{step.title}
                </p>
                {step.lines && step.lines.length > 0 && (
                  <div className="space-y-1">
                    {step.lines.map((line, j) => (
                      <p key={j} className="text-xs text-bb-text2 leading-relaxed break-keep">{line}</p>
                    ))}
                  </div>
                )}
                {step.example && (
                  <div className="mt-2.5 bg-bb-bg border border-bb-border rounded-lg p-3 font-mono text-[11px] text-bb-text2 break-all">
                    {step.example.before}
                    <span className="text-indigo-400 font-bold">{step.example.highlightPart}</span>
                    {step.example.after}
                    {step.example.caption && (
                      <p className="mt-1.5 font-sans text-indigo-400 not-italic">↑ {step.example.caption}</p>
                    )}
                  </div>
                )}
                {step.link && (
                  <a
                    href={step.link.href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-2.5 inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg
                               bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium
                               transition-colors"
                  >
                    {step.link.label}
                    <ExternalLink size={12} />
                  </a>
                )}
              </div>
            </div>
          ))}

          {/* 강조 경고 박스 */}
          {warning && (
            <div className={`flex items-start gap-3 p-4 rounded-xl border mt-1 mb-4 ${tone.box}`}>
              <AlertTriangle size={16} className={`${tone.icon} shrink-0 mt-0.5`} />
              <div className="flex-1 min-w-0">
                <p className={`text-sm font-semibold mb-1.5 ${tone.title}`}>{warning.title}</p>
                <div className="space-y-1">
                  {warning.lines.map((line, j) => (
                    <p key={j} className={`text-xs leading-relaxed break-keep ${tone.text}`}>{line}</p>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* 자주 묻는 질문 */}
          {faq && faq.length > 0 && (
            <div className="pt-4 border-t border-bb-border/60">
              <p className="flex items-center gap-1.5 text-xs font-semibold text-bb-text2 mb-3">
                <HelpCircle size={13} />
                자주 묻는 질문
              </p>
              <div className="space-y-3">
                {faq.map((item, i) => (
                  <div key={i} className="text-xs">
                    <p className="text-bb-text font-medium break-keep">Q. {item.q}</p>
                    <p className="text-bb-text2 leading-relaxed mt-0.5 break-keep">→ {item.a}</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
