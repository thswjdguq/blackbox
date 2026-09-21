"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import {
  getDeliverable,
  getDeliverableProgress,
  createRequirement,
  deleteRequirement,
} from "@/lib/api/deliverable";
import type { Deliverable, Requirement, DeliverableProgress } from "@/types/deliverable";
import {
  ClipboardList,
  Plus,
  Trash2,
  AlertCircle,
  RefreshCw,
  CheckCircle2,
  Circle,
  Loader2,
} from "lucide-react";
import api from "@/lib/api";
import type { Task } from "@/types/task";

// ── 탭 정의 ──────────────────────────────────────────────────────────────
type Tab = "overview" | "review" | "submit";

const TABS: { id: Tab; label: string; implemented: boolean }[] = [
  { id: "overview", label: "요구사항·업무",  implemented: true  },
  { id: "review",   label: "검토",           implemented: false },
  { id: "submit",   label: "최종 제출",       implemented: false },
];

// ── 진척 섹션 ─────────────────────────────────────────────────────────────
function ProgressSection({
  progress,
  progressError,
}: {
  progress: DeliverableProgress | null;
  progressError: boolean;
}) {
  // API 호출 실패 — 가짜 0% 표시 금지, 오류 메시지만 표시
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

      {/* 업무 완료율 */}
      <div className="space-y-1.5">
        <div className="flex justify-between text-xs text-bb-text2">
          <span>업무 완료율</span>
          {/* total=0 이면 "연결 업무 없음" — 100% 표시 금지 */}
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

      {/* 필수 요구사항 충족률 */}
      <div className="space-y-1.5">
        <div className="flex justify-between text-xs text-bb-text2">
          <span>필수 요구사항 충족</span>
          <span className="text-bb-text">
            {reqs.total === 0
              ? "필수 요구사항 없음"             // total=0: 100% 표시 금지
              : reqs.met === null
              ? "충족 확인 기능 준비 중"         // met=null: 기능 미지원
              : `${reqs.met}/${reqs.total} (${reqs.percent}%)`}
          </span>
        </div>
      </div>
    </div>
  );
}

// ── 메인 ─────────────────────────────────────────────────────────────────
export default function DeliverableDetailPage() {
  const params         = useParams();
  const projectId      = params?.id as string;
  const deliverableId  = params?.deliverableId as string;

  const [deliverable,    setDeliverable]    = useState<Deliverable | null>(null);
  const [progress,       setProgress]       = useState<DeliverableProgress | null>(null);
  const [linkedTasks,    setLinkedTasks]    = useState<Task[]>([]);
  const [myRole,         setMyRole]         = useState<string | null>(null);
  const [activeTab,      setActiveTab]      = useState<Tab>("overview");
  const [loading,        setLoading]        = useState(true);
  const [error,          setError]          = useState("");
  const [progressError,  setProgressError]  = useState(false);

  // 요구사항 추가 폼 상태
  const [showReqForm,    setShowReqForm]    = useState(false);
  const [reqContent,     setReqContent]    = useState("");
  const [reqRequired,    setReqRequired]   = useState(false);
  const [reqSaving,      setReqSaving]     = useState(false);
  const [reqError,       setReqError]      = useState("");

  const canWrite = myRole === "LEADER" || myRole === "MEMBER";

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError("");
    setProgressError(false);
    try {
      const [delRes, projRes, taskRes] = await Promise.all([
        getDeliverable(projectId, deliverableId),
        api.get<{ myRole: string }>(`/projects/${projectId}`),
        // 이 제출물에 연결된 업무만 필터링
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

  // 진척은 별도로 조회 — 실패해도 나머지 화면은 정상 표시
  const fetchProgress = useCallback(async () => {
    try {
      const data = await getDeliverableProgress(projectId, deliverableId);
      setProgress(data);
    } catch {
      // 진척 API 실패 시 가짜 0% 대신 오류 표시
      setProgressError(true);
    }
  }, [projectId, deliverableId]);

  useEffect(() => {
    fetchAll();
    fetchProgress();
  }, [fetchAll, fetchProgress]);

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
      fetchAll(); // 저장 후 서버 응답으로 갱신
    } catch {
      setReqError("요구사항 저장에 실패했습니다.");
    } finally {
      setReqSaving(false);
    }
  };

  const handleDeleteRequirement = async (req: Requirement) => {
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

  // ── 업무 상태 배지 ──────────────────────────────────────────────────────
  const STATUS_CFG = {
    TODO:        { label: "할 일",   cls: "bg-slate-700 text-slate-400" },
    IN_PROGRESS: { label: "진행 중", cls: "bg-indigo-500/20 text-indigo-400" },
    DONE:        { label: "완료",    cls: "bg-teal-500/15 text-teal-400" },
  } as const;

  return (
    <div className="flex h-screen bg-bb-bg">
      <Sidebar />

      <main className="flex-1 ml-64 overflow-y-auto p-8">
        {/* 로딩 */}
        {loading && (
          <div className="flex items-center justify-center h-40 gap-2 text-bb-text2">
            <Loader2 size={20} className="animate-spin" />
            <span className="text-sm">불러오는 중...</span>
          </div>
        )}

        {/* 전체 오류 */}
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
              <h1 className="text-xl font-bold text-bb-text flex items-center gap-2">
                <ClipboardList size={20} className="text-bb-primary" />
                {deliverable.title}
              </h1>
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
                  // 미구현 탭은 비활성 스타일 + 클릭 불가
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
                  {/* 미구현 탭에 "준비 중" 뱃지 */}
                  {!tab.implemented && (
                    <span className="ml-1.5 text-[10px] px-1.5 py-0.5 bg-bb-surface2 rounded-full">
                      준비 중
                    </span>
                  )}
                </button>
              ))}
            </div>

            {/* 탭 콘텐츠 — 요구사항·업무 */}
            {activeTab === "overview" && (
              <div className="space-y-6">
                {/* 진척 현황 */}
                <ProgressSection
                  progress={progress}
                  progressError={progressError}
                />

                {/* 요구사항 목록 */}
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

                  {/* 요구사항 추가 폼 */}
                  {showReqForm && (
                    <form onSubmit={handleAddRequirement} className="mb-3 p-4 bg-bb-surface border border-bb-border rounded-lg space-y-2">
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
                        <button type="button" onClick={() => setShowReqForm(false)} className="text-xs text-bb-text2 hover:text-bb-text px-3 py-1.5">취소</button>
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

                  {/* 요구사항 0건 */}
                  {deliverable.requirements.length === 0 ? (
                    <p className="text-sm text-bb-text2 py-4 text-center">
                      등록된 요구사항이 없습니다.
                    </p>
                  ) : (
                    <ul className="space-y-2">
                      {deliverable.requirements.map((req) => (
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
                            <p className="text-xs text-bb-text2 mt-0.5">
                              {req.required ? "필수" : "선택"}
                            </p>
                          </div>
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
                      ))}
                    </ul>
                  )}
                </section>

                {/* 연결된 업무 목록 */}
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
          </>
        )}
      </main>
    </div>
  );
}
