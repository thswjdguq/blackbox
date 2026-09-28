"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import {
  getDeliverable,
  getDeliverableProgress,
  createRequirement,
  deleteRequirement,
  assessRequirement,
  unassessRequirement,
} from "@/lib/api/deliverable";
import {
  getReviewData,
  requestReview,
  addComment,
  resolveComment,
} from "@/lib/api/review";
import type {
  Deliverable,
  DeliverableRequirement,
  DeliverableProgress,
} from "@/types/deliverable";
import type {
  DeliverableStatus,
  ReviewRound,
  ReviewComment,
} from "@/types/review";
import {
  ClipboardList,
  Plus,
  Trash2,
  AlertCircle,
  RefreshCw,
  CheckCircle2,
  Circle,
  Loader2,
  CheckSquare,
  ChevronDown,
  ChevronUp,
  Send,
  TriangleAlert,
} from "lucide-react";
import api from "@/lib/api";
import type { Task } from "@/types/task";

// ── 탭 정의 ──────────────────────────────────────────────────────────────
type Tab = "overview" | "review" | "submit";

const TABS: { id: Tab; label: string; implemented: boolean }[] = [
  { id: "overview", label: "요구사항·업무",  implemented: true  },
  { id: "review",   label: "검토",           implemented: true  },
  { id: "submit",   label: "최종 제출",       implemented: false },
];

// ── 제출물 상태 배지 설정 ──────────────────────────────────────────────────
const DEL_STATUS_CFG: Record<DeliverableStatus, { label: string; cls: string }> = {
  DRAFT:     { label: "초안",      cls: "bg-slate-700 text-slate-300" },
  IN_REVIEW: { label: "검토 중",   cls: "bg-indigo-500/20 text-indigo-400" },
  CONFIRMED: { label: "확정",      cls: "bg-teal-500/15 text-teal-400" },
  SUBMITTED: { label: "제출 완료", cls: "bg-green-500/15 text-green-400" },
};

// ── 수정본 안내 배너 ──────────────────────────────────────────────────────
function RevisionGuide({ unresolvedCount }: { unresolvedCount: number }) {
  if (unresolvedCount === 0) return null;
  return (
    <div className="flex items-start gap-3 p-4 bg-amber-500/10 border border-amber-500/30 rounded-xl mb-4">
      <TriangleAlert size={16} className="text-amber-400 mt-0.5 shrink-0" />
      <div className="text-sm">
        <p className="font-semibold text-amber-300">
          미해결 피드백 {unresolvedCount}건
        </p>
        <p className="text-amber-400/80 mt-0.5 text-xs">
          피드백을 모두 반영한 수정본을 Hash Vault에 업로드한 뒤 재검토를 요청하세요.
        </p>
      </div>
    </div>
  );
}

// ── 검토 회차 카드 ────────────────────────────────────────────────────────
function ReviewRoundCard({
  round,
  canWrite,
  projectId,
  deliverableId,
  onCommentAdded,
  onCommentResolved,
}: {
  round: ReviewRound;
  canWrite: boolean;
  projectId: string;
  deliverableId: string;
  onCommentAdded: (reviewId: string, comment: ReviewComment) => void;
  onCommentResolved: (reviewId: string, commentId: string, resolved: boolean) => void;
}) {
  const [expanded, setExpanded] = useState(true);
  const [commentText, setCommentText] = useState("");
  const [sending, setSending] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const unresolved = round.comments.filter((c) => !c.resolved).length;

  const handleSendComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentText.trim()) return;
    setSending(true);
    try {
      const newComment = await addComment(projectId, deliverableId, round.id, {
        content: commentText.trim(),
      });
      onCommentAdded(round.id, newComment);
      setCommentText("");
    } finally {
      setSending(false);
    }
  };

  const handleToggleResolve = async (comment: ReviewComment) => {
    const newResolved = !comment.resolved;
    onCommentResolved(round.id, comment.id, newResolved);
    try {
      await resolveComment(projectId, deliverableId, round.id, comment.id, {
        resolved: newResolved,
      });
    } catch {
      // 실패 시 원상 복구
      onCommentResolved(round.id, comment.id, comment.resolved);
    }
  };

  return (
    <div className="bg-bb-surface border border-bb-border rounded-xl overflow-hidden">
      {/* 회차 헤더 */}
      <button
        onClick={() => setExpanded((v) => !v)}
        className="w-full flex items-center justify-between px-4 py-3 hover:bg-bb-surface2 transition-colors"
      >
        <div className="flex items-center gap-3">
          <span className="text-sm font-semibold text-bb-text">
            {round.roundNumber}회차
          </span>
          {round.fileVersionLabel && (
            <span className="text-xs font-mono text-bb-text2 bg-bb-surface2 px-2 py-0.5 rounded">
              {round.fileVersionLabel}
            </span>
          )}
          <span
            className={`text-[10px] px-2 py-0.5 rounded-full font-medium ${
              round.status === "OPEN"
                ? "bg-indigo-500/20 text-indigo-400"
                : "bg-slate-700 text-slate-400"
            }`}
          >
            {round.status === "OPEN" ? "진행 중" : "종료"}
          </span>
          {unresolved > 0 && (
            <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-400">
              미해결 {unresolved}건
            </span>
          )}
        </div>
        {expanded ? (
          <ChevronUp size={14} className="text-bb-text2" />
        ) : (
          <ChevronDown size={14} className="text-bb-text2" />
        )}
      </button>

      {/* 코멘트 목록 */}
      {expanded && (
        <div className="border-t border-bb-border">
          {round.comments.length === 0 ? (
            <p className="text-xs text-bb-text2 text-center py-5">
              피드백이 없습니다.
            </p>
          ) : (
            <ul className="divide-y divide-bb-border">
              {round.comments.map((comment) => (
                <li key={comment.id} className="px-4 py-3 flex items-start gap-3">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-xs font-medium text-bb-text">
                        {comment.authorName}
                      </span>
                      <span className="text-[10px] text-bb-text2">
                        {fmtRelative(comment.createdAt)}
                      </span>
                      {comment.resolved && (
                        <span className="text-[10px] px-1.5 py-0.5 bg-teal-500/15 text-teal-400 rounded-full">
                          해결됨
                        </span>
                      )}
                    </div>
                    <p className={`text-sm ${comment.resolved ? "text-bb-text2 line-through" : "text-bb-text"}`}>
                      {comment.content}
                    </p>
                  </div>
                  {/* OPEN 회차 + 쓰기 권한만 토글 가능 */}
                  {round.status === "OPEN" && canWrite && (
                    <button
                      onClick={() => handleToggleResolve(comment)}
                      className={`shrink-0 p-1 rounded transition-colors ${
                        comment.resolved
                          ? "text-teal-400 hover:text-teal-300"
                          : "text-bb-text2 hover:text-teal-400"
                      }`}
                      title={comment.resolved ? "미해결로 변경" : "해결됨으로 표시"}
                    >
                      {comment.resolved
                        ? <CheckSquare size={14} />
                        : <Circle size={14} />
                      }
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}

          {/* 코멘트 작성 폼 — OPEN 회차 + 쓰기 권한만 */}
          {round.status === "OPEN" && canWrite && (
            <form
              onSubmit={handleSendComment}
              className="border-t border-bb-border px-4 py-3 flex gap-2"
            >
              <textarea
                ref={textareaRef}
                value={commentText}
                onChange={(e) => setCommentText(e.target.value)}
                placeholder="피드백 작성..."
                rows={1}
                className="flex-1 bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm text-bb-text placeholder-slate-400 focus:outline-none focus:border-indigo-500 resize-none"
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    handleSendComment(e as unknown as React.FormEvent);
                  }
                }}
              />
              <button
                type="submit"
                disabled={sending || !commentText.trim()}
                className="shrink-0 p-2 bg-bb-primary hover:bg-bb-primary-h disabled:opacity-40 rounded-lg transition-colors"
              >
                {sending
                  ? <Loader2 size={14} className="text-white animate-spin" />
                  : <Send size={14} className="text-white" />
                }
              </button>
            </form>
          )}
        </div>
      )}
    </div>
  );
}

// ── 날짜 포맷 ─────────────────────────────────────────────────────────────
function fmtRelative(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 1)  return "방금";
  if (mins < 60) return `${mins}분 전`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24)  return `${hrs}시간 전`;
  return `${Math.floor(hrs / 24)}일 전`;
}

// ── 진척 섹션 ─────────────────────────────────────────────────────────────
function ProgressSection({
  progress,
  progressError,
}: {
  progress: DeliverableProgress | null;
  progressError: boolean;
}) {
  if (progressError) {
    return (
      <div className="flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
        <AlertCircle size={14} className="shrink-0" />
        진척 정보를 불러올 수 없습니다.
      </div>
    );
  }
  if (!progress) return null;

  const { tasks, requiredRequirements: reqs } = progress;

  return (
    <div className="bg-bb-surface2 rounded-xl p-4 space-y-3">
      <p className="text-xs font-semibold text-bb-text2 uppercase tracking-wide">진척 현황</p>

      <div className="space-y-1.5">
        <div className="flex justify-between text-xs text-bb-text2">
          <span>업무 완료율</span>
          <span className="text-bb-text">
            {tasks.total === 0
              ? "연결 업무 없음"
              : `${tasks.completed}/${tasks.total} (${tasks.percent}%)`}
          </span>
        </div>
        {tasks.total > 0 && (
          <div className="h-1.5 bg-bb-border rounded-full overflow-hidden">
            <div
              className="h-full bg-indigo-500 rounded-full transition-all"
              style={{ width: `${tasks.percent}%` }}
            />
          </div>
        )}
      </div>

      <div className="flex justify-between text-xs text-bb-text2">
        <span>필수 요구사항 충족</span>
        <span className="text-bb-text">
          {reqs.total === 0
            ? "필수 요구사항 없음"
            : reqs.met === null
            ? "충족 확인 기능 준비 중"
            : `${reqs.met}/${reqs.total} (${reqs.percent}%)`}
        </span>
      </div>
    </div>
  );
}

// ── 메인 ─────────────────────────────────────────────────────────────────
export default function DeliverableDetailPage() {
  const params        = useParams();
  const projectId     = params?.projectId as string;
  const deliverableId = params?.deliverableId as string;

  const [deliverable,   setDeliverable]   = useState<Deliverable | null>(null);
  const [progress,      setProgress]      = useState<DeliverableProgress | null>(null);
  const [linkedTasks,   setLinkedTasks]   = useState<Task[]>([]);
  const [myRole,        setMyRole]        = useState<string | null>(null);
  const [activeTab,     setActiveTab]     = useState<Tab>("overview");
  const [loading,       setLoading]       = useState(true);
  const [error,         setError]         = useState("");
  const [progressError, setProgressError] = useState(false);

  const [showReqForm,  setShowReqForm]  = useState(false);
  const [reqContent,   setReqContent]   = useState("");
  const [reqRequired,  setReqRequired]  = useState(false);
  const [reqSaving,    setReqSaving]    = useState(false);
  const [reqError,     setReqError]     = useState("");

  // 검토 탭 상태
  const [delStatus,     setDelStatus]     = useState<DeliverableStatus>("DRAFT");
  const [rounds,        setRounds]        = useState<ReviewRound[]>([]);
  const [reviewLoading, setReviewLoading] = useState(false);
  const [reviewError,   setReviewError]   = useState("");
  const [requesting,    setRequesting]    = useState(false);

  const canWrite = myRole === "LEADER" || myRole === "MEMBER";

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError("");
    setProgressError(false);
    try {
      const [delRes, projRes, taskRes] = await Promise.all([
        getDeliverable(projectId, deliverableId),
        api.get<{ myRole: string }>(`/projects/${projectId}`),
        api.get<Task[]>(`/projects/${projectId}/tasks`),
      ]);
      setDeliverable(delRes.data);
      setMyRole(projRes.data.myRole);
      setLinkedTasks(
        taskRes.data.filter((t) => t.deliverableId === deliverableId)
      );
    } catch {
      setError("제출물 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [projectId, deliverableId]);

  const fetchProgress = useCallback(async () => {
    try {
      const data = await getDeliverableProgress(projectId, deliverableId);
      setProgress(data);
    } catch {
      setProgressError(true);
    }
  }, [projectId, deliverableId]);

  useEffect(() => {
    fetchAll();
    fetchProgress();
  }, [fetchAll, fetchProgress]);

  // 검토 탭 진입 시 lazy load (rounds가 없을 때만)
  const fetchReview = useCallback(async () => {
    setReviewLoading(true);
    setReviewError("");
    try {
      const data = await getReviewData(projectId, deliverableId);
      setDelStatus(data.deliverableStatus);
      setRounds(data.rounds);
    } catch {
      setReviewError("검토 정보를 불러오지 못했습니다.");
    } finally {
      setReviewLoading(false);
    }
  }, [projectId, deliverableId]);

  useEffect(() => {
    if (activeTab === "review" && rounds.length === 0 && !reviewLoading) {
      fetchReview();
    }
  }, [activeTab, rounds.length, reviewLoading, fetchReview]);

  const handleRequestReview = async () => {
    setRequesting(true);
    try {
      const newRound = await requestReview(projectId, deliverableId);
      setDelStatus("IN_REVIEW");
      setRounds((prev) => [...prev, newRound]);
    } finally {
      setRequesting(false);
    }
  };

  const handleCommentAdded = (reviewId: string, comment: ReviewComment) => {
    setRounds((prev) =>
      prev.map((r) =>
        r.id === reviewId ? { ...r, comments: [...r.comments, comment] } : r
      )
    );
  };

  const handleCommentResolved = (
    reviewId: string,
    commentId: string,
    resolved: boolean
  ) => {
    setRounds((prev) =>
      prev.map((r) =>
        r.id === reviewId
          ? {
              ...r,
              comments: r.comments.map((c) =>
                c.id === commentId ? { ...c, resolved } : c
              ),
            }
          : r
      )
    );
  };

  const handleAddRequirement = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reqContent.trim()) return;
    setReqSaving(true);
    setReqError("");
    try {
      await createRequirement(projectId, deliverableId, {
        content: reqContent.trim(),
        required: reqRequired,
      });
      setReqContent("");
      setReqRequired(false);
      setShowReqForm(false);
      fetchAll();
    } catch {
      setReqError("요구사항 저장에 실패했습니다.");
    } finally {
      setReqSaving(false);
    }
  };

  const handleDeleteRequirement = async (req: DeliverableRequirement) => {
    try {
      await deleteRequirement(projectId, deliverableId, req.id);
      setDeliverable((prev) =>
        prev
          ? { ...prev, requirements: prev.requirements.filter((r) => r.id !== req.id) }
          : prev
      );
    } catch (err) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        alert("이 요구사항에 연결된 업무를 먼저 해제한 뒤 삭제해주세요.");
      }
    }
  };

  // K-14: 충족 확인 토글 (낙관적 업데이트)
  const handleToggleAssessment = async (req: DeliverableRequirement) => {
    const isAssessed = req.assessment !== null;

    // 낙관적 업데이트
    setDeliverable((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        requirements: prev.requirements.map((r) =>
          r.id === req.id
            ? { ...r, assessment: isAssessed ? null : { assessedBy: { userId: "", name: "나" }, assessedAt: new Date().toISOString() } }
            : r
        ),
      };
    });

    try {
      const res = isAssessed
        ? await unassessRequirement(projectId, deliverableId, req.id)
        : await assessRequirement(projectId, deliverableId, req.id);
      // 서버 응답으로 정확한 값 반영
      setDeliverable((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          requirements: prev.requirements.map((r) =>
            r.id === req.id ? (res.data as DeliverableRequirement) : r
          ),
        };
      });
    } catch {
      // 실패 시 원상 복구
      fetchAll();
    }
  };

  const STATUS_CFG = {
    TODO:        { label: "할 일",   cls: "bg-slate-700 text-slate-400" },
    IN_PROGRESS: { label: "진행 중", cls: "bg-indigo-500/20 text-indigo-400" },
    DONE:        { label: "완료",    cls: "bg-teal-500/15 text-teal-400" },
  } as const;

  return (
    <div className="flex h-screen bg-bb-bg">
      <Sidebar />

      <main className="flex-1 ml-64 overflow-y-auto p-8">
        {loading && (
          <div className="flex items-center justify-center h-40 gap-2 text-bb-text2">
            <Loader2 size={20} className="animate-spin" />
            <span className="text-sm">불러오는 중...</span>
          </div>
        )}

        {!loading && error && (
          <div className="flex flex-col items-center gap-3 py-20 text-center">
            <AlertCircle size={32} className="text-red-400" />
            <p className="text-sm text-bb-text2">{error}</p>
            <button
              onClick={fetchAll}
              className="flex items-center gap-1.5 px-4 py-2 text-sm text-bb-text2 hover:text-bb-text border border-bb-border rounded-lg transition-colors"
            >
              <RefreshCw size={14} />
              다시 시도
            </button>
          </div>
        )}

        {!loading && !error && deliverable && (
          <>
            {/* 헤더 */}
            <div className="mb-6">
              <div className="flex items-center gap-3 flex-wrap">
                <h1 className="text-xl font-bold text-bb-text flex items-center gap-2">
                  <ClipboardList size={20} className="text-bb-primary" />
                  {deliverable.title}
                </h1>
                {/* 제출물 상태 배지 */}
                <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${DEL_STATUS_CFG[delStatus].cls}`}>
                  {DEL_STATUS_CFG[delStatus].label}
                </span>
              </div>
              {deliverable.description && (
                <p className="mt-1 text-sm text-bb-text2">{deliverable.description}</p>
              )}
              <p className="mt-1 text-xs text-bb-text2">
                기한: {deliverable.dueDate ?? "-"}
                {deliverable.ownerName && ` · 담당: ${deliverable.ownerName}`}
              </p>
            </div>

            {/* 탭 */}
            <div className="flex gap-1 mb-6 border-b border-bb-border">
              {TABS.map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => tab.implemented && setActiveTab(tab.id)}
                  className={`px-4 py-2.5 text-sm font-medium rounded-t-lg transition-colors
                    ${!tab.implemented
                      ? "opacity-40 cursor-not-allowed text-bb-text2"
                      : activeTab === tab.id
                      ? "text-bb-primary border-b-2 border-bb-primary"
                      : "text-bb-text2 hover:text-bb-text"
                    }`}
                  title={!tab.implemented ? `${tab.label} 기능 준비 중입니다` : undefined}
                >
                  {tab.label}
                  {!tab.implemented && (
                    <span className="ml-1.5 text-[10px] px-1.5 py-0.5 bg-bb-surface2 rounded-full">
                      준비 중
                    </span>
                  )}
                </button>
              ))}
            </div>

            {/* 요구사항·업무 탭 */}
            {activeTab === "overview" && (
              <div className="space-y-6">
                <ProgressSection
                  progress={progress}
                  progressError={progressError}
                />

                {/* 요구사항 */}
                <section>
                  <div className="flex items-center justify-between mb-3">
                    <h2 className="text-sm font-semibold text-bb-text">요구사항</h2>
                    {canWrite && (
                      <button
                        onClick={() => setShowReqForm((v) => !v)}
                        className="flex items-center gap-1 text-xs text-bb-text2 hover:text-bb-primary transition-colors"
                      >
                        <Plus size={13} />
                        추가
                      </button>
                    )}
                  </div>

                  {showReqForm && (
                    <form
                      onSubmit={handleAddRequirement}
                      className="mb-3 p-4 bg-bb-surface border border-bb-border rounded-lg space-y-2"
                    >
                      {reqError && (
                        <p className="text-xs text-red-400">{reqError}</p>
                      )}
                      <input
                        type="text"
                        value={reqContent}
                        onChange={(e) => setReqContent(e.target.value)}
                        placeholder="요구사항 내용"
                        required
                        className="w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm text-bb-text placeholder-slate-400 focus:outline-none focus:border-indigo-500"
                      />
                      <label className="flex items-center gap-2 text-xs text-bb-text2 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={reqRequired}
                          onChange={(e) => setReqRequired(e.target.checked)}
                          className="rounded"
                        />
                        필수 요구사항
                      </label>
                      <div className="flex gap-2 justify-end">
                        <button
                          type="button"
                          onClick={() => setShowReqForm(false)}
                          className="text-xs text-bb-text2 hover:text-bb-text px-3 py-1.5"
                        >
                          취소
                        </button>
                        <button
                          type="submit"
                          disabled={reqSaving || !reqContent.trim()}
                          className="flex items-center gap-1 px-3 py-1.5 bg-bb-primary hover:bg-bb-primary-h disabled:opacity-50 text-white text-xs rounded-lg"
                        >
                          {reqSaving && <Loader2 size={12} className="animate-spin" />}
                          저장
                        </button>
                      </div>
                    </form>
                  )}

                  {deliverable.requirements.length === 0 ? (
                    <p className="text-sm text-bb-text2 py-4 text-center">
                      등록된 요구사항이 없습니다.
                    </p>
                  ) : (
                    <ul className="space-y-2">
                      {deliverable.requirements.map((req) => {
                        const assessed = req.assessment !== null;
                        // 이 요구사항에 연결된 업무 수
                        const reqTasks = linkedTasks.filter(
                          (t) => t.requirementId === req.id
                        );
                        const reqDone = reqTasks.filter(
                          (t) => t.status === "DONE"
                        ).length;
                        const allDone =
                          reqTasks.length > 0 && reqDone === reqTasks.length;

                        return (
                          <li
                            key={req.id}
                            className="group flex items-start gap-2 p-3 bg-bb-surface border border-bb-border rounded-lg"
                          >
                            {/* 필수/선택 아이콘 */}
                            {req.required
                              ? <CheckCircle2 size={15} className="text-indigo-400 mt-0.5 shrink-0" />
                              : <Circle       size={15} className="text-bb-text2 mt-0.5 shrink-0" />
                            }

                            <div className="flex-1 min-w-0">
                              <p className="text-sm text-bb-text">{req.content}</p>
                              <div className="flex items-center gap-3 mt-1">
                                <p className="text-xs text-bb-text2">
                                  {req.required ? "필수" : "선택"}
                                </p>
                                {/* 연결 업무 진행 상황 (K-14 §6-3) */}
                                {reqTasks.length > 0 && (
                                  <p className="text-xs text-bb-text2">
                                    연결 업무 {reqDone}/{reqTasks.length} 완료
                                  </p>
                                )}
                                {/* 충족 확인 정보 (K-14 §6-2) */}
                                {assessed && req.assessment && (
                                  <p className="text-xs text-teal-400">
                                    {req.assessment.assessedBy.name} · {fmtRelative(req.assessment.assessedAt)}
                                  </p>
                                )}
                                {/* 업무 모두 완료인데 미확인 안내 (K-14 §6-4) */}
                                {allDone && !assessed && (
                                  <p className="text-xs text-amber-400">
                                    업무가 모두 끝났습니다. 충족 여부를 확인하세요.
                                  </p>
                                )}
                              </div>
                            </div>

                            {/* 충족 확인 체크박스 (K-14 §6-5: 관찰자는 비활성) */}
                            <button
                              onClick={() => canWrite && handleToggleAssessment(req)}
                              className={`shrink-0 p-1 rounded transition-colors ${
                                canWrite
                                  ? assessed
                                    ? "text-teal-400 hover:text-teal-300"
                                    : "text-bb-text2 hover:text-teal-400"
                                  : "text-bb-border cursor-not-allowed"
                              }`}
                              title={
                                !canWrite
                                  ? "관찰자는 확인할 수 없습니다."
                                  : assessed
                                  ? "확인 해제"
                                  : "충족 확인"
                              }
                              aria-label={assessed ? "충족 확인 해제" : "충족 확인"}
                            >
                              {assessed
                                ? <CheckSquare size={15} />
                                : <Circle size={15} />
                              }
                            </button>

                            {canWrite && (
                              <button
                                onClick={() => handleDeleteRequirement(req)}
                                className="p-1 rounded text-bb-text2 hover:text-red-400 opacity-0 group-hover:opacity-100 transition-all"
                                aria-label="요구사항 삭제"
                              >
                                <Trash2 size={13} />
                              </button>
                            )}
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </section>

                {/* 연결된 업무 */}
                <section>
                  <h2 className="text-sm font-semibold text-bb-text mb-3">연결된 업무</h2>
                  {linkedTasks.length === 0 ? (
                    <p className="text-sm text-bb-text2 py-4 text-center">
                      연결된 업무가 없습니다. 업무 보드에서 업무를 이 제출물에 연결해주세요.
                    </p>
                  ) : (
                    <ul className="space-y-2">
                      {linkedTasks.map((task) => {
                        const cfg = STATUS_CFG[task.status];
                        return (
                          <li
                            key={task.id}
                            className="flex items-center gap-3 p-3 bg-bb-surface border border-bb-border rounded-lg"
                          >
                            <span className={`text-[10px] font-medium px-2 py-0.5 rounded-full shrink-0 ${cfg.cls}`}>
                              {cfg.label}
                            </span>
                            <span className="text-sm text-bb-text truncate">{task.title}</span>
                            {task.completionCriteria && (
                              <span className="ml-auto text-xs text-bb-text2 shrink-0 truncate max-w-[160px]">
                                {task.completionCriteria}
                              </span>
                            )}
                          </li>
                        );
                      })}
                    </ul>
                  )}
                </section>
              </div>
            )}

            {/* 검토 탭 */}
            {activeTab === "review" && (
              <div>
                {/* 수정본 안내 배너 */}
                {(() => {
                  const unresolvedTotal = rounds
                    .filter((r) => r.status === "OPEN")
                    .reduce(
                      (acc, r) => acc + r.comments.filter((c) => !c.resolved).length,
                      0
                    );
                  return <RevisionGuide unresolvedCount={unresolvedTotal} />;
                })()}

                {/* 검토 요청 버튼 — DRAFT + 쓰기 권한 */}
                {delStatus === "DRAFT" && canWrite && (
                  <div className="mb-4">
                    <button
                      onClick={handleRequestReview}
                      disabled={requesting}
                      className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-sm rounded-lg transition-colors"
                    >
                      {requesting && <Loader2 size={14} className="animate-spin" />}
                      검토 요청
                    </button>
                  </div>
                )}

                {/* 로딩 */}
                {reviewLoading && (
                  <div className="flex items-center justify-center py-12 gap-2 text-bb-text2">
                    <Loader2 size={18} className="animate-spin" />
                    <span className="text-sm">검토 정보 불러오는 중...</span>
                  </div>
                )}

                {/* 오류 */}
                {!reviewLoading && reviewError && (
                  <div className="flex flex-col items-center gap-3 py-12 text-center">
                    <AlertCircle size={28} className="text-red-400" />
                    <p className="text-sm text-bb-text2">{reviewError}</p>
                    <button
                      onClick={fetchReview}
                      className="flex items-center gap-1.5 px-4 py-2 text-sm text-bb-text2 hover:text-bb-text border border-bb-border rounded-lg transition-colors"
                    >
                      <RefreshCw size={14} />
                      다시 시도
                    </button>
                  </div>
                )}

                {/* 빈 상태 */}
                {!reviewLoading && !reviewError && rounds.length === 0 && (
                  <div className="flex flex-col items-center gap-2 py-16 text-center">
                    <CheckCircle2 size={32} className="text-bb-text2 opacity-30" />
                    <p className="text-sm text-bb-text2">아직 검토 회차가 없습니다.</p>
                    {delStatus === "DRAFT" && canWrite && (
                      <p className="text-xs text-bb-text2">위의 "검토 요청" 버튼으로 첫 검토를 시작하세요.</p>
                    )}
                  </div>
                )}

                {/* 검토 회차 목록 (최신순) */}
                {!reviewLoading && !reviewError && rounds.length > 0 && (
                  <div className="space-y-3">
                    {[...rounds].reverse().map((round) => (
                      <ReviewRoundCard
                        key={round.id}
                        round={round}
                        canWrite={canWrite}
                        projectId={projectId}
                        deliverableId={deliverableId}
                        onCommentAdded={handleCommentAdded}
                        onCommentResolved={handleCommentResolved}
                      />
                    ))}
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}
