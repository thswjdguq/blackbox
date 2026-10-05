"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { CheckCircle2, FileText, Loader2, MessageSquare, RotateCcw, XCircle } from "lucide-react";
import { apiError } from "@/lib/apiError";
import {
  addComment,
  createTaskFromComment,
  decideReviewRound,
  reopenComment,
  resolveComment,
} from "@/lib/api/review";
import type { CommentStatus, ReviewComment, ReviewDecisionResult, ReviewRound } from "@/types/review";
import type { Task } from "@/types/task";

const COMMENT_STATUS: Record<CommentStatus, { label: string; cls: string }> = {
  OPEN: { label: "미해결", cls: "bg-amber-500/15 text-amber-400" },
  REFLECTION_PENDING: { label: "반영 확인 대기", cls: "bg-indigo-500/15 text-indigo-400" },
  RESOLVED: { label: "해결됨", cls: "bg-teal-500/15 text-teal-400" },
};

const TASK_STATUS_LABEL: Record<Task["status"], string> = { TODO: "할 일", IN_PROGRESS: "진행 중", DONE: "완료" };

const FIELD =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm text-bb-text placeholder-slate-400 focus:outline-none focus:border-indigo-500";

export function fmtRelative(iso: string): string {
  const mins = Math.floor((Date.now() - new Date(iso).getTime()) / 60_000);
  if (mins < 1) return "방금";
  if (mins < 60) return `${mins}분 전`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}시간 전`;
  return `${Math.floor(hrs / 24)}일 전`;
}

/** 확정 근거가 아닌 승인의 사유. 응답만으로 정해진다 (K-10 7장) */
function notBasisReason(round: ReviewRound): string {
  if (!round.latest) return "이후 새 회차가 열려";
  if (round.file == null) return "파일 없는 회차라";
  return "같은 이름의 최신 버전과 내용이 달라";
}

export default function ReviewRoundCard({
  projectId,
  deliverableId,
  round,
  comments,
  locked,
  unresolvedCount,
  canWrite,
  myUserId,
  tasks,
  onChanged,
}: {
  projectId: string;
  deliverableId: string;
  round: ReviewRound;
  comments: ReviewComment[];
  /** CONFIRMED·SUBMITTED 제출물은 검토 기록을 바꾸지 않는다 */
  locked: boolean;
  unresolvedCount: number;
  canWrite: boolean;
  myUserId: string | null;
  tasks: Task[];
  /** 서버 상태가 바뀌었으니 회차·코멘트·업무를 다시 불러온다 */
  onChanged: () => void;
}) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [newComment, setNewComment] = useState("");
  const [resolvingId, setResolvingId] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [taskFormId, setTaskFormId] = useState<string | null>(null);
  const [taskTitle, setTaskTitle] = useState("");
  const [taskCriteria, setTaskCriteria] = useState("");

  const result = round.decision?.result ?? null;
  const approved = result === "APPROVED";
  // 승인된 회차는 코멘트 작성·해결·다시 열기를 막는다. 승인 안 된 지난 회차는 해결·다시 열기만 된다 (K-11 1장)
  const writable = canWrite && !approved && !locked;
  // 파일을 올린 사람은 승인도 수정 요청도 할 수 없다 (K-10 2장, 403)
  const isUploader = round.file != null && round.file.uploaderId === myUserId;
  const canDecide = canWrite && round.latest && round.decision == null && !locked;

  const run = async (key: string, action: () => Promise<unknown>) => {
    setBusy(key);
    setError("");
    try {
      await action();
      onChanged();
      return true;
    } catch (err) {
      setError(apiError(err, "요청을 처리하지 못했습니다. 다시 시도해주세요."));
      return false;
    } finally {
      setBusy(null);
    }
  };

  const decide = (decision: ReviewDecisionResult) =>
    run(`decide-${decision}`, () => decideReviewRound(projectId, deliverableId, round.id, decision));

  const submitComment = async (e: FormEvent) => {
    e.preventDefault();
    if (!newComment.trim()) return;
    if (await run("comment", () => addComment(projectId, deliverableId, round.id, newComment.trim()))) setNewComment("");
  };

  const submitResolve = async (e: FormEvent, c: ReviewComment) => {
    e.preventDefault();
    if (await run(`resolve-${c.id}`, () => resolveComment(projectId, deliverableId, round.id, c.id, reason))) {
      setResolvingId(null);
    }
  };

  const startTaskForm = (c: ReviewComment) => {
    setTaskFormId(c.id);
    setTaskTitle(c.content.split("\n")[0].slice(0, 255));
    setTaskCriteria("");
  };

  const submitTask = async (e: FormEvent, c: ReviewComment) => {
    e.preventDefault();
    if (!taskTitle.trim()) return;
    const ok = await run(`task-${c.id}`, () =>
      createTaskFromComment(projectId, deliverableId, round.id, c.id, {
        title: taskTitle.trim(),
        completionCriteria: taskCriteria.trim() || undefined,
      })
    );
    if (ok) setTaskFormId(null);
  };

  return (
    <section
      data-testid={`round-${round.roundNo}`}
      className={`bg-bb-surface border rounded-xl p-5 ${round.latest ? "border-bb-border" : "border-bb-border/60"}`}
    >
      {/* 회차 머리 */}
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="text-sm font-semibold text-bb-text">{round.roundNo}회차</h3>
        {round.file ? (
          <span className="flex items-center gap-1 text-xs text-bb-text2">
            <FileText size={12} />
            {round.file.fileName} · v{round.file.version} · <span className="font-mono">{round.file.shortHash}</span> ·{" "}
            {round.file.uploaderName}
          </span>
        ) : (
          <span className="text-[11px] px-2 py-0.5 rounded-full bg-bb-surface2 text-bb-text2">파일 없음 · 중간 검토</span>
        )}
        {result === "APPROVED" && (
          <span
            className={`text-[11px] px-2 py-0.5 rounded-full ${
              round.currentBasis ? "bg-teal-500/15 text-teal-400" : "bg-bb-surface2 text-bb-text2 line-through"
            }`}
          >
            승인
          </span>
        )}
        {result === "CHANGES_REQUESTED" && (
          <span className="text-[11px] px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-400">수정 요청</span>
        )}
        {result == null && round.latest && (
          <span className="text-[11px] px-2 py-0.5 rounded-full bg-indigo-500/15 text-indigo-400">검토 중</span>
        )}
        {result == null && !round.latest && (
          <span className="text-[11px] px-2 py-0.5 rounded-full bg-bb-surface2 text-bb-text2">결정 없음</span>
        )}
      </div>
      <p className="mt-1 text-xs text-bb-text2">
        {round.openedBy.name}님이 {fmtRelative(round.openedAt)} 열었습니다
        {round.decision && ` · ${round.decision.decidedBy.name}님이 ${fmtRelative(round.decision.decidedAt)} 결정`}
      </p>
      {approved && !round.currentBasis && (
        <p className="mt-1 text-xs text-bb-text2">{notBasisReason(round)} 이 승인은 확정 근거가 아닙니다.</p>
      )}

      {error && (
        <p role="alert" className="mt-3 p-2.5 bg-red-500/10 border border-red-500/20 rounded-lg text-xs text-red-400">
          {error}
        </p>
      )}

      {/* 결정 */}
      {canDecide && (
        <div className="mt-4 p-3 bg-bb-surface2 rounded-lg space-y-2">
          {isUploader ? (
            <p className="text-xs text-bb-text2">직접 올린 파일은 다른 팀원이 승인하거나 수정을 요청합니다.</p>
          ) : (
            <>
              <div className="flex flex-wrap gap-2">
                <button
                  onClick={() => decide("APPROVED")}
                  disabled={busy != null || unresolvedCount > 0}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-white bg-teal-600 hover:bg-teal-500 disabled:opacity-40 disabled:cursor-not-allowed rounded-lg"
                >
                  {busy === "decide-APPROVED" ? <Loader2 size={13} className="animate-spin" /> : <CheckCircle2 size={13} />}
                  승인
                </button>
                <button
                  onClick={() => decide("CHANGES_REQUESTED")}
                  disabled={busy != null}
                  className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-amber-500 border border-amber-500/40 hover:bg-amber-500/10 disabled:opacity-40 rounded-lg"
                >
                  {busy === "decide-CHANGES_REQUESTED" ? <Loader2 size={13} className="animate-spin" /> : <XCircle size={13} />}
                  수정 요청
                </button>
              </div>
              {unresolvedCount > 0 && (
                <p className="text-xs text-bb-text2">미해결 피드백 {unresolvedCount}건을 먼저 해결해야 승인할 수 있습니다.</p>
              )}
              {round.file == null && (
                <p className="text-xs text-bb-text2">파일 없는 회차도 승인할 수 있지만 최종 확정 근거가 되지는 않습니다.</p>
              )}
              <p className="text-xs text-bb-text2">결정은 회차마다 한 번입니다. 바꾸려면 새 회차를 엽니다.</p>
            </>
          )}
        </div>
      )}

      {/* 피드백 */}
      <div className="mt-4">
        <p className="flex items-center gap-1.5 text-xs font-medium text-bb-text2">
          <MessageSquare size={12} />
          피드백 {comments.length}건
        </p>
        {comments.length === 0 && <p className="mt-2 text-xs text-bb-text2">아직 피드백이 없습니다.</p>}
        <ul className="mt-2 divide-y divide-bb-border">
          {comments.map((c) => {
            const cfg = COMMENT_STATUS[c.status];
            const linkedTask = c.linkedTaskId ? tasks.find((t) => t.id === c.linkedTaskId) : undefined;
            return (
              <li key={c.id} className="py-3">
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  <span className={`px-2 py-0.5 rounded-full ${cfg.cls}`}>{cfg.label}</span>
                  <span className="text-bb-text">{c.author.name}</span>
                  <span className="text-bb-text2">{fmtRelative(c.createdAt)}</span>
                </div>
                <p className="mt-1.5 text-sm text-bb-text whitespace-pre-wrap break-words">{c.content}</p>

                {c.linkedTaskId && (
                  <Link
                    href={`/projects/${projectId}/board?task=${c.linkedTaskId}`}
                    className="mt-1.5 inline-block text-xs text-bb-primary hover:underline"
                  >
                    업무: {linkedTask ? `${linkedTask.title} (${TASK_STATUS_LABEL[linkedTask.status]})` : "보드에서 보기"} →
                  </Link>
                )}

                {c.resolution && (
                  <p className="mt-1.5 text-xs text-teal-500">
                    해결: {c.resolution.resolvedBy.name} · {fmtRelative(c.resolution.resolvedAt)}
                    {c.resolution.reason && ` · ${c.resolution.reason}`}
                    <span className="block text-bb-text2">해결은 반영 판정이 아닙니다. 다음 회차 승인으로 판정합니다.</span>
                  </p>
                )}

                {writable && (
                  <div className="mt-2 flex flex-wrap items-center gap-3 text-xs">
                    {c.status !== "RESOLVED" && (
                      <button
                        onClick={() => {
                          setResolvingId(c.id);
                          setReason("");
                        }}
                        disabled={busy != null}
                        className="text-teal-500 hover:underline disabled:opacity-40 disabled:no-underline"
                      >
                        해결
                      </button>
                    )}
                    {c.status === "RESOLVED" && (
                      <button
                        onClick={() => run(`reopen-${c.id}`, () => reopenComment(projectId, deliverableId, round.id, c.id))}
                        disabled={busy != null}
                        className="flex items-center gap-1 text-bb-text2 hover:underline disabled:opacity-40"
                      >
                        <RotateCcw size={11} />
                        다시 열기
                      </button>
                    )}
                    {c.status !== "RESOLVED" && !c.linkedTaskId && (
                      <button onClick={() => startTaskForm(c)} disabled={busy != null} className="text-bb-primary hover:underline disabled:opacity-40">
                        업무로 만들기
                      </button>
                    )}
                  </div>
                )}

                {resolvingId === c.id && (
                  <form onSubmit={(e) => submitResolve(e, c)} className="mt-2 flex flex-wrap gap-2">
                    <input
                      autoFocus
                      value={reason}
                      onChange={(e) => setReason(e.target.value)}
                      maxLength={1000}
                      placeholder="어떻게 반영했는지 (선택)"
                      aria-label="해결 사유"
                      className={`${FIELD} flex-1 min-w-[200px]`}
                    />
                    <button type="button" onClick={() => setResolvingId(null)} className="px-2 text-xs text-bb-text2">
                      취소
                    </button>
                    <button
                      disabled={busy != null}
                      className="px-3 py-1.5 text-xs text-white bg-teal-600 hover:bg-teal-500 disabled:opacity-40 rounded-lg"
                    >
                      해결 저장
                    </button>
                  </form>
                )}

                {taskFormId === c.id && (
                  <form onSubmit={(e) => submitTask(e, c)} className="mt-2 space-y-2 p-3 bg-bb-surface2 rounded-lg">
                    <input
                      autoFocus
                      value={taskTitle}
                      onChange={(e) => setTaskTitle(e.target.value)}
                      maxLength={255}
                      placeholder="업무 제목"
                      aria-label="업무 제목"
                      className={FIELD}
                    />
                    <input
                      value={taskCriteria}
                      onChange={(e) => setTaskCriteria(e.target.value)}
                      maxLength={2000}
                      placeholder="완료 기준 (선택)"
                      className={FIELD}
                    />
                    <p className="text-[11px] text-bb-text2">담당자와 기한은 만든 뒤 업무 보드에서 정합니다.</p>
                    <div className="flex justify-end gap-2">
                      <button type="button" onClick={() => setTaskFormId(null)} className="px-2 text-xs text-bb-text2">
                        취소
                      </button>
                      <button
                        disabled={busy != null || !taskTitle.trim()}
                        className="px-3 py-1.5 text-xs text-white bg-bb-primary hover:bg-bb-primary-h disabled:opacity-40 rounded-lg"
                      >
                        업무 만들기
                      </button>
                    </div>
                  </form>
                )}
              </li>
            );
          })}
        </ul>

        {/* 새 피드백은 최신 회차에만 쓴다 (K-11 1장). 지난 회차는 해결·다시 열기만 */}
        {writable && round.latest && (
          <form onSubmit={submitComment} className="mt-3 flex gap-2">
            <textarea
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
              rows={2}
              maxLength={2000}
              placeholder="피드백을 남기세요"
              aria-label="새 피드백"
              className={`${FIELD} resize-none`}
            />
            <button
              disabled={busy != null || !newComment.trim()}
              className="self-end shrink-0 whitespace-nowrap px-3 py-2 text-xs text-white bg-bb-primary hover:bg-bb-primary-h disabled:opacity-40 rounded-lg"
            >
              {busy === "comment" ? <Loader2 size={13} className="animate-spin" /> : "남기기"}
            </button>
          </form>
        )}
      </div>
    </section>
  );
}
