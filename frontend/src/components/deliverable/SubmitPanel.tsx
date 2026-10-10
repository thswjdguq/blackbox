"use client";

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import Link from "next/link";
import { AlertTriangle, CheckCircle2, Clock, Download, Loader2, Lock, XCircle } from "lucide-react";
import { apiError } from "@/lib/apiError";
import { isReviewApiMissing } from "@/lib/api/review";
import { confirmDeliverable, getConfirmation, recordSubmission } from "@/lib/api/confirmation";
import type { ConfirmationCheck, ConfirmationInfo } from "@/types/confirmation";

const FIELD =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm text-bb-text " +
  "placeholder-slate-400 focus:outline-none focus:border-indigo-500";

const STATUS_TEXT: Record<ConfirmationInfo["status"], string> = {
  DRAFT: "초안",
  IN_REVIEW: "검토 중",
  CONFIRMED: "확정됨",
  SUBMITTED: "제출 기록됨",
};

/** 확정 뒤 막히는 것 (K-20 4장). 확정 창에서 그대로 보여 준다 */
const LOCKED_AFTER_CONFIRM = [
  "제출물 정보 수정·삭제",
  "요구사항 추가·수정·삭제와 충족 확인",
  "검토 회차 열기·결정, 피드백 작성·해결",
  "연결된 업무의 수정·상태 변경·담당자 변경·삭제·이동",
];

function fmt(iso: string): string {
  return new Date(iso).toLocaleString("ko-KR", { year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

/** datetime-local 입력값(사용자 시간대) */
function nowLocalInput(): string {
  const d = new Date();
  d.setSeconds(0, 0);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

/**
 * 최종 제출 탭 (C-30, K-20 v0.1).
 * 확정 가능 여부는 서버 응답(checks·canConfirm)만 따른다. 화면이 진척 수치로 따로 계산하지 않는다(CONTRACTS 3장 8항).
 * 제출 기록은 사람이 학교에 낸 뒤 남기는 기록이고, 앱이 대신 제출하지 않는다(10항).
 */
export default function SubmitPanel({
  projectId,
  deliverableId,
  myRole,
  requiredCount,
  submissionMethod,
  openTaskCount,
  onGoTab,
  onChanged,
}: {
  projectId: string;
  deliverableId: string;
  myRole: string | null;
  /** 필수 요구사항 수. 0이면 검사는 통과로 오지만 화면은 경고로 보여 준다 */
  requiredCount: number;
  submissionMethod: string | null;
  /** 끝나지 않은 연결 업무 수(K-13). 못 불러오면 null */
  openTaskCount: number | null;
  onGoTab: (tab: "overview" | "review") => void;
  /** 확정·기록 뒤 제출물 화면의 잠금을 다시 반영한다 */
  onChanged: () => void;
}) {
  const [info, setInfo] = useState<ConfirmationInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [notReady, setNotReady] = useState(false);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [busy, setBusy] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  // 제출 기록 입력
  const [channel, setChannel] = useState(submissionMethod ?? "");
  const [submittedAt, setSubmittedAt] = useState(nowLocalInput());
  const [note, setNote] = useState("");
  const [reviewing, setReviewing] = useState(false);
  const [formError, setFormError] = useState("");

  const isLeader = myRole === "LEADER";
  const canRecord = myRole === "LEADER" || myRole === "MEMBER";

  const load = useCallback(async () => {
    setError("");
    try {
      const { data } = await getConfirmation(projectId, deliverableId);
      setInfo(data);
      setNotReady(false);
    } catch (err) {
      if (isReviewApiMissing(err)) setNotReady(true);
      else setError(apiError(err, "확정 정보를 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [projectId, deliverableId]);

  useEffect(() => { load(); }, [load]);

  const handleConfirm = async () => {
    if (!info?.candidate) return;
    setBusy(true);
    setActionError("");
    try {
      const { data } = await confirmDeliverable(projectId, deliverableId, info.candidate.reviewId);
      setInfo(data);
      setShowConfirm(false);
      onChanged();
    } catch (err) {
      // 그 사이 대상이 바뀌었거나 조건이 깨졌다(409). 사유를 보여 주고 목록을 다시 불러온다
      setActionError(apiError(err, "확정하지 못했습니다."));
      setShowConfirm(false);
      load();
    } finally {
      setBusy(false);
    }
  };

  const checkForm = (): string => {
    if (!submittedAt) return "제출 시각을 입력해주세요.";
    if (new Date(submittedAt).getTime() > Date.now() + 5 * 60_000) return "제출 시각은 지금보다 뒤일 수 없습니다.";
    if (channel.length > 500) return "제출 채널은 500자까지 적을 수 있습니다.";
    if (note.length > 1000) return "메모는 1000자까지 적을 수 있습니다.";
    return "";
  };

  const handleRecord = async () => {
    setBusy(true);
    setActionError("");
    try {
      const { data } = await recordSubmission(projectId, deliverableId, {
        channel: channel.trim() || null,
        submittedAt: new Date(submittedAt).toISOString(),
        note: note.trim() || null,
      });
      setInfo(data);
      setReviewing(false);
      onChanged();
    } catch (err) {
      setActionError(apiError(err, "제출을 기록하지 못했습니다."));
      setReviewing(false);
      load();
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-32 gap-2 text-bb-text2">
        <Loader2 size={18} className="animate-spin" />
        <span className="text-sm">확정 정보를 불러오는 중...</span>
      </div>
    );
  }

  if (notReady) {
    return (
      <div className="flex flex-col items-center gap-2 py-16 text-center">
        <Clock size={28} className="text-bb-text2 opacity-50" />
        <p className="text-sm text-bb-text">최종 확정 기능은 서버 준비 중입니다</p>
        <p className="text-xs text-bb-text2">최종 확정·제출 기록 API(K-20)가 구현되면 이 탭에서 바로 사용할 수 있습니다.</p>
      </div>
    );
  }

  if (error || !info) {
    return (
      <div role="alert" className="flex flex-col items-center gap-3 py-12 text-center">
        <p className="text-sm text-red-400">{error || "확정 정보를 불러오지 못했습니다."}</p>
        <button onClick={load} className="text-xs text-bb-text2 underline hover:text-bb-text">다시 시도</button>
      </div>
    );
  }

  const firstFailed = info.checks.find((c) => !c.passed);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="rounded-full bg-bb-surface2 px-2.5 py-0.5 text-xs text-bb-text2" data-testid="confirm-status">
          {STATUS_TEXT[info.status]}
        </span>
        {info.confirmation && (
          <span className="inline-flex items-center gap-1 text-xs text-bb-text2">
            <Lock size={12} /> 확정된 제출물은 요구사항·업무·검토 기록을 바꿀 수 없습니다
          </span>
        )}
      </div>

      {actionError && <p role="alert" className="rounded-lg border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400">{actionError}</p>}

      {/* ── 확정 전 ── */}
      {!info.confirmation && (
        <section className="rounded-xl border border-bb-border bg-bb-surface p-5" aria-labelledby="confirm-ready-title">
          <h3 id="confirm-ready-title" className="text-sm font-semibold text-bb-text">최종본 확정 준비</h3>
          <p className="mt-1 text-xs text-bb-text2">서버가 확정할 때 아래 세 가지를 다시 확인합니다.</p>

          {info.soleContributor && (
            <div className="mt-3 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-400">
              파일을 올린 사람은 자기 파일을 승인할 수 없어, 팀장·팀원이 한 명뿐이면 확정할 수 없습니다. 파일을 올리지 않은 팀원의 승인이 필요합니다.{" "}
              <Link href={`/projects/${projectId}/settings`} className="underline">팀원 초대하기</Link>
            </div>
          )}

          <ul className="mt-3 space-y-2">
            {info.checks.map((c) => (
              <CheckRow key={c.code} check={c} requiredCount={requiredCount} onGoTab={onGoTab} />
            ))}
          </ul>

          {info.candidate && (
            <div className="mt-4 rounded-lg bg-bb-surface2/60 px-3 py-2 text-xs text-bb-text2" data-testid="confirm-candidate">
              확정할 최종본: <span className="font-medium text-bb-text">{info.candidate.file.fileName}</span> v{info.candidate.file.version} ·{" "}
              <span className="font-mono">{info.candidate.file.shortHash}</span> · {info.candidate.file.uploaderName} 올림 · {info.candidate.roundNo}회차 승인
            </div>
          )}

          <div className="mt-4">
            {isLeader ? (
              <>
                <button
                  onClick={() => setShowConfirm(true)}
                  disabled={!info.canConfirm || !info.candidate || busy}
                  className="rounded-lg bg-bb-primary px-4 py-2 text-sm font-medium text-white hover:bg-bb-primary-h disabled:cursor-not-allowed disabled:opacity-40"
                >
                  최종본 확정
                </button>
                {!info.canConfirm && firstFailed && <p className="mt-2 text-xs text-bb-text2">{firstFailed.detail}</p>}
              </>
            ) : canRecord ? (
              <p className="text-xs text-bb-text2">최종본 확정은 팀장이 합니다.</p>
            ) : null}
          </div>
        </section>
      )}

      {/* ── 확정 뒤 ── */}
      {info.confirmation && (
        <section className="rounded-xl border border-teal-500/30 bg-teal-500/5 p-5" aria-labelledby="confirmed-title">
          <h3 id="confirmed-title" className="flex items-center gap-1.5 text-sm font-semibold text-bb-text">
            <CheckCircle2 size={15} className="text-teal-400" /> 최종본이 확정됐습니다
          </h3>
          <dl className="mt-3 grid gap-2 text-xs sm:grid-cols-[6rem_1fr]">
            <dt className="text-bb-text2">확정본</dt>
            <dd className="text-bb-text">
              {info.confirmation.file.fileName} v{info.confirmation.file.version} · <span className="font-mono">{info.confirmation.file.shortHash}</span>{" "}
              <a href={`/api/files/${info.confirmation.file.fileId}/download`} className="ml-1 inline-flex items-center gap-0.5 text-bb-primary hover:underline">
                <Download size={12} /> 내려받기
              </a>
            </dd>
            <dt className="text-bb-text2">확정</dt>
            <dd className="text-bb-text">{info.confirmation.confirmedBy.name} · {fmt(info.confirmation.confirmedAt)} · {info.confirmation.roundNo}회차 승인본</dd>
          </dl>
          {!info.confirmation.fileStillLatest && (
            <p className="mt-3 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-400">
              확정 뒤에 같은 이름의 새 버전이 올라왔습니다. 확정본은 v{info.confirmation.file.version}입니다.
            </p>
          )}
        </section>
      )}

      {/* ── 제출 기록 ── */}
      {info.confirmation && !info.submission && (
        <section className="rounded-xl border border-bb-border bg-bb-surface p-5" aria-labelledby="submission-title">
          <h3 id="submission-title" className="text-sm font-semibold text-bb-text">제출 기록</h3>
          <p className="mt-1 text-xs text-bb-text2">학교 시스템 등에 낸 뒤 기록하세요. 이 기록은 팀이 남기는 것이며 실제 접수를 확인하지 않습니다.</p>
          {!canRecord ? (
            <p className="mt-3 text-xs text-bb-text2">팀장·팀원이 제출한 뒤 기록합니다.</p>
          ) : !reviewing ? (
            <form
              className="mt-3 space-y-3"
              onSubmit={(e) => {
                e.preventDefault();
                const msg = checkForm();
                setFormError(msg);
                if (!msg) setReviewing(true);
              }}
            >
              <label className="block text-xs text-bb-text2">
                제출한 곳
                <input value={channel} onChange={(e) => setChannel(e.target.value)} maxLength={500} placeholder="예: 학교 LMS 과제함" className={`${FIELD} mt-1`} />
              </label>
              <label className="block text-xs text-bb-text2">
                제출한 시각 <span className="text-rose-400">*</span>
                <input type="datetime-local" value={submittedAt} onChange={(e) => setSubmittedAt(e.target.value)} className={`${FIELD} mt-1`} />
              </label>
              <label className="block text-xs text-bb-text2">
                증빙 메모 (선택)
                <textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={1000} rows={2} placeholder="예: 접수 번호, 확인 메일 제목" className={`${FIELD} mt-1 resize-none`} />
                <span className="mt-1 block text-[11px]">증빙 파일은 파일 금고에 올리고 여기에 파일 이름을 적어 주세요.</span>
              </label>
              {formError && <p role="alert" className="text-xs text-red-400">{formError}</p>}
              <button className="rounded-lg bg-bb-primary px-4 py-2 text-sm font-medium text-white hover:bg-bb-primary-h">제출했다고 기록</button>
            </form>
          ) : (
            <div className="mt-3 space-y-2 rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-xs" role="group" aria-label="제출 기록 확인">
              <p className="font-medium text-amber-400">아래 내용으로 기록합니다. 기록 뒤에는 고칠 수 없습니다.</p>
              <p className="text-bb-text">제출한 곳: {channel.trim() || "(비움 — 제출물의 제출 경로)"}</p>
              <p className="text-bb-text">제출한 시각: {fmt(new Date(submittedAt).toISOString())}</p>
              <p className="text-bb-text">메모: {note.trim() || "없음"}</p>
              <div className="flex gap-2 pt-1">
                <button onClick={handleRecord} disabled={busy} className="rounded-lg bg-bb-primary px-3 py-1.5 text-white hover:bg-bb-primary-h disabled:opacity-40">
                  {busy ? <Loader2 size={13} className="animate-spin" /> : "확인하고 기록"}
                </button>
                <button onClick={() => setReviewing(false)} disabled={busy} className="px-3 py-1.5 text-bb-text2 hover:text-bb-text">고치기</button>
              </div>
            </div>
          )}
        </section>
      )}

      {info.submission && (
        <section className="rounded-xl border border-bb-border bg-bb-surface p-5" aria-labelledby="submitted-title">
          <h3 id="submitted-title" className="flex items-center gap-1.5 text-sm font-semibold text-bb-text">
            <CheckCircle2 size={15} className="text-teal-400" /> 제출이 기록됐습니다
          </h3>
          <dl className="mt-3 grid gap-2 text-xs sm:grid-cols-[6rem_1fr]">
            <dt className="text-bb-text2">제출한 곳</dt>
            <dd className="text-bb-text">{info.submission.channel ?? "-"}</dd>
            <dt className="text-bb-text2">제출한 시각</dt>
            <dd className="text-bb-text">{fmt(info.submission.submittedAt)}</dd>
            <dt className="text-bb-text2">메모</dt>
            <dd className="text-bb-text whitespace-pre-wrap break-words">{info.submission.note ?? "-"}</dd>
            <dt className="text-bb-text2">기록</dt>
            <dd className="text-bb-text">{info.submission.recordedBy.name} · {fmt(info.submission.recordedAt)}</dd>
          </dl>
          <p className="mt-3 text-[11px] text-bb-text2">이 기록은 팀이 직접 남긴 것으로, 학교 시스템의 접수 여부를 확인하지 않습니다.</p>
        </section>
      )}

      {showConfirm && info.candidate && (
        <ConfirmDialog
          info={info}
          openTaskCount={openTaskCount}
          busy={busy}
          onCancel={() => setShowConfirm(false)}
          onConfirm={handleConfirm}
        />
      )}
    </div>
  );
}

function CheckRow({ check, requiredCount, onGoTab }: {
  check: ConfirmationCheck;
  requiredCount: number;
  onGoTab: (tab: "overview" | "review") => void;
}) {
  // 필수 요구사항이 없으면 서버는 통과로 보내지만, 빈 목록을 다 확인한 것처럼 보이지 않게 경고로 그린다
  const warn = check.code === "REQUIRED_REQUIREMENTS" && check.passed && requiredCount === 0;
  const tab = check.code === "REQUIRED_REQUIREMENTS" ? "overview" : "review";
  return (
    <li className="flex items-start gap-2 text-sm" data-testid={`check-${check.code}`} data-state={warn ? "warn" : check.passed ? "pass" : "fail"}>
      {warn ? (
        <AlertTriangle size={15} className="mt-0.5 shrink-0 text-amber-400" aria-label="주의" />
      ) : check.passed ? (
        <CheckCircle2 size={15} className="mt-0.5 shrink-0 text-teal-400" aria-label="통과" />
      ) : (
        <XCircle size={15} className="mt-0.5 shrink-0 text-red-400" aria-label="통과하지 못함" />
      )}
      <span className="min-w-0 flex-1">
        <span className={check.passed ? "text-bb-text" : "text-bb-text"}>{check.detail}</span>
        {warn && <span className="block text-xs text-amber-400">과제 안내의 필수 내용을 요구사항으로 적었는지 확인하세요.</span>}
      </span>
      {(!check.passed || warn) && (
        <button onClick={() => onGoTab(tab)} className="shrink-0 whitespace-nowrap text-xs text-bb-primary hover:underline">
          {tab === "overview" ? "요구사항·업무로" : "검토로"}
        </button>
      )}
    </li>
  );
}

function ConfirmDialog({ info, openTaskCount, busy, onCancel, onConfirm }: {
  info: ConfirmationInfo;
  openTaskCount: number | null;
  busy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => {
    const before = document.activeElement as HTMLElement | null;
    ref.current?.focus();
    return () => { before?.focus?.(); };
  }, []);
  const c = info.candidate!;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60" onClick={() => !busy && onCancel()} />
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        tabIndex={-1}
        onKeyDown={(e) => { if (e.key === "Escape" && !busy) { e.stopPropagation(); onCancel(); } }}
        className="relative w-full max-w-md rounded-2xl border border-bb-border bg-bb-surface p-6 shadow-2xl outline-none max-h-[90vh] overflow-y-auto"
      >
        <h2 id="confirm-dialog-title" className="text-base font-semibold text-bb-text">최종본을 확정할까요?</h2>
        <p className="mt-3 rounded-lg bg-bb-surface2/60 px-3 py-2 text-xs text-bb-text">
          {c.file.fileName} v{c.file.version} · <span className="font-mono">{c.file.shortHash}</span> · {c.roundNo}회차 승인본
        </p>
        <p className="mt-4 text-xs font-medium text-bb-text">확정하면 바꿀 수 없는 것</p>
        <ul className="mt-1 list-disc space-y-0.5 pl-5 text-xs text-bb-text2">
          {LOCKED_AFTER_CONFIRM.map((t) => <li key={t}>{t}</li>)}
        </ul>
        <p className="mt-2 text-xs text-bb-text2">파일 금고에 새 버전은 계속 올릴 수 있지만 확정본은 바뀌지 않습니다.</p>
        {openTaskCount != null && openTaskCount > 0 && (
          <p className="mt-3 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs text-amber-400" data-testid="open-task-warning">
            끝나지 않은 연결 업무 {openTaskCount}건은 확정 뒤 바꿀 수 없습니다.
          </p>
        )}
        <p className="mt-3 text-xs text-red-400">확정은 되돌릴 수 없습니다. 고쳐야 하면 새 제출물로 다시 만들어야 합니다.</p>
        <div className="mt-5 flex justify-end gap-2">
          <button onClick={onCancel} disabled={busy} className="px-4 py-2 text-sm text-bb-text2 hover:text-bb-text">취소</button>
          <button onClick={onConfirm} disabled={busy} className="flex items-center gap-1.5 rounded-lg bg-bb-primary px-4 py-2 text-sm font-medium text-white hover:bg-bb-primary-h disabled:opacity-40">
            {busy && <Loader2 size={14} className="animate-spin" />} 확정하기
          </button>
        </div>
      </div>
    </div>
  );
}
