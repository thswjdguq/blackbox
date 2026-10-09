"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import Sidebar from "@/components/Sidebar";
import TaskModal from "@/components/kanban/TaskModal";
import DeliverableFormModal from "@/components/deliverable/DeliverableFormModal";
import ConfirmDeleteDialog from "@/components/deliverable/ConfirmDeleteDialog";
import ReviewPanel from "@/components/deliverable/ReviewPanel";
import SubmitPanel from "@/components/deliverable/SubmitPanel";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import {
  getDeliverable,
  getDeliverableProgress,
  updateDeliverable,
  deleteDeliverable,
  createRequirement,
  updateRequirement,
  deleteRequirement,
  assessRequirement,
  unassessRequirement,
} from "@/lib/api/deliverable";
import type {
  Deliverable,
  DeliverableRequirement,
  DeliverableProgress,
  SaveDeliverablePayload,
} from "@/types/deliverable";
import type { CreateTaskPayload, Task } from "@/types/task";
import {
  AlertCircle,
  ArrowRight,
  CheckSquare,
  ChevronLeft,
  Circle,
  ClipboardList,
  Loader2,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
} from "lucide-react";

interface Member {
  userId: string;
  name: string;
  email: string;
  role: string;
}

type Tab = "overview" | "review" | "submit";

const TABS: { id: Tab; label: string; implemented: boolean }[] = [
  { id: "overview", label: "요구사항·업무", implemented: true },
  { id: "review", label: "검토", implemented: true },
  { id: "submit", label: "최종 제출", implemented: true },
];

const TASK_STATUS_LABEL: Record<Task["status"], string> = {
  TODO: "할 일",
  IN_PROGRESS: "진행 중",
  DONE: "업무 완료",
};

const FIELD =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm text-bb-text " +
  "placeholder-slate-400 focus:outline-none focus:border-indigo-500";

function fmtRelative(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 1) return "방금";
  if (mins < 60) return `${mins}분 전`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}시간 전`;
  return `${Math.floor(hrs / 24)}일 전`;
}

function ProgressSection({
  progress,
  progressError,
  onRetry,
}: {
  progress: DeliverableProgress | null;
  progressError: boolean;
  onRetry: () => void;
}) {
  if (progressError) {
    return (
      <div className="flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
        <AlertCircle size={14} className="shrink-0" />
        진척 정보를 불러올 수 없습니다.
        <button onClick={onRetry} className="ml-auto flex items-center gap-1 text-xs text-red-300 hover:text-red-200">
          <RefreshCw size={12} />
          다시 시도
        </button>
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
            {tasks.total === 0 ? "연결 업무 없음" : `${tasks.completed}/${tasks.total} (${tasks.percent}%)`}
          </span>
        </div>
        {tasks.total > 0 && (
          <div className="h-1.5 bg-bb-border rounded-full overflow-hidden">
            <div className="h-full bg-indigo-500 rounded-full transition-all" style={{ width: `${tasks.percent}%` }} />
          </div>
        )}
      </div>
      <div className="flex justify-between text-xs text-bb-text2">
        <span>필수 요구사항 충족</span>
        <span className="text-bb-text">
          {reqs.total === 0 ? "필수 요구사항 없음" : `${reqs.met}/${reqs.total} (${reqs.percent}%)`}
        </span>
      </div>
      <p className="text-[11px] text-bb-text2">업무 완료와 요구사항 충족은 제출 준비 완료를 뜻하지 않습니다.</p>
    </div>
  );
}

export default function DeliverableDetailPage() {
  const params = useParams();
  const router = useRouter();
  const projectId = params?.projectId as string;
  const deliverableId = params?.deliverableId as string;

  const [deliverable, setDeliverable] = useState<Deliverable | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [myUserId, setMyUserId] = useState<string | null>(null);
  const [progress, setProgress] = useState<DeliverableProgress | null>(null);
  const [activeTab, setActiveTab] = useState<Tab>("overview");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [progressError, setProgressError] = useState(false);
  const [notice, setNotice] = useState("");
  const [actionError, setActionError] = useState("");

  // 요구사항 추가·수정 폼 (editingReqId 가 있으면 수정)
  const [reqContent, setReqContent] = useState("");
  const [reqRequired, setReqRequired] = useState(true);
  const [editingReqId, setEditingReqId] = useState<string | null>(null);
  const [reqSaving, setReqSaving] = useState(false);
  const [reqError, setReqError] = useState("");

  const [showEdit, setShowEdit] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<{ kind: "deliverable" } | { kind: "requirement"; req: DeliverableRequirement } | null>(null);
  const [taskDraft, setTaskDraft] = useState<{ requirementId: string } | null>(null);
  const [linkTaskId, setLinkTaskId] = useState("");
  const [linking, setLinking] = useState(false);
  // 충족 확인 요청 중인 요구사항 — 빠른 연타로 PUT·DELETE 가 엇갈려 도착하지 않게 한다
  const [assessingIds, setAssessingIds] = useState<Set<string>>(new Set());

  // 역할은 멤버 목록에서 내 항목으로 판단한다. GET /projects/{id} 응답의 myRole 은 현재 항상 null 이다 (A 에 수정 요청)
  const myRole = members.find((m) => m.userId === myUserId)?.role ?? null;
  const canWrite = myRole === "LEADER" || myRole === "MEMBER";
  // K-14 §2: 문구(content)가 바뀌면 서버가 충족 확인을 푼다. 필수 여부만 바꾸면 유지된다
  const editingReq = deliverable?.requirements.find((r) => r.id === editingReqId) ?? null;
  const assessmentWillReset =
    editingReq?.assessment != null && reqContent.trim() !== editingReq.content.trim();
  const linkedTasks = tasks.filter((t) => t.deliverableId === deliverableId);
  const unlinkedTasks = tasks.filter((t) => !t.deliverableId);
  const contributors = members.filter((m) => m.role !== "OBSERVER");

  const fetchAll = useCallback(async () => {
    setError("");
    try {
      const [delRes, taskRes, memRes, profileRes] = await Promise.all([
        getDeliverable(projectId, deliverableId),
        api.get<Task[]>(`/projects/${projectId}/tasks`),
        api.get<Member[]>(`/projects/${projectId}/members`),
        api.get<{ id: string }>("/auth/profile"),
      ]);
      setDeliverable(delRes.data);
      setTasks(taskRes.data);
      setMembers(memRes.data);
      setMyUserId(profileRes.data.id);
    } catch (err) {
      setError(apiError(err, "제출물 정보를 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [projectId, deliverableId]);

  const fetchProgress = useCallback(async () => {
    try {
      const data = await getDeliverableProgress(projectId, deliverableId);
      setProgress(data);
      setProgressError(false);
    } catch {
      setProgressError(true);
    }
  }, [projectId, deliverableId]);

  const refresh = useCallback(() => {
    fetchAll();
    fetchProgress();
  }, [fetchAll, fetchProgress]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const replaceRequirement = (updated: DeliverableRequirement) =>
    setDeliverable((prev) =>
      prev ? { ...prev, requirements: prev.requirements.map((r) => (r.id === updated.id ? updated : r)) } : prev
    );

  // ── 제출물 수정·삭제 ──────────────────────────────────────────────────
  const handleSaveDeliverable = async (payload: SaveDeliverablePayload) => {
    await updateDeliverable(projectId, deliverableId, payload);
    setShowEdit(false);
    setNotice("제출물을 수정했습니다.");
    fetchAll();
  };

  const handleDeleteDeliverable = async () => {
    await deleteDeliverable(projectId, deliverableId);
    router.replace(`/projects/${projectId}/deliverables`);
  };

  // ── 요구사항 ──────────────────────────────────────────────────────────
  const resetReqForm = () => {
    setReqContent("");
    setReqRequired(true);
    setEditingReqId(null);
    setReqError("");
  };

  const startEditRequirement = (req: DeliverableRequirement) => {
    setEditingReqId(req.id);
    setReqContent(req.content);
    setReqRequired(req.required);
    setReqError("");
  };

  const handleSaveRequirement = async (e: FormEvent) => {
    e.preventDefault();
    if (!reqContent.trim()) return;
    setReqSaving(true);
    setReqError("");
    try {
      const payload = { content: reqContent.trim(), required: reqRequired };
      if (editingReqId) {
        await updateRequirement(projectId, deliverableId, editingReqId, payload);
        setNotice("요구사항을 수정했습니다.");
      } else {
        await createRequirement(projectId, deliverableId, payload);
        setNotice("요구사항을 추가했습니다.");
      }
      resetReqForm();
      refresh();
    } catch (err) {
      setReqError(apiError(err, "요구사항 저장에 실패했습니다. 다시 시도해주세요."));
    } finally {
      setReqSaving(false);
    }
  };

  const handleDeleteRequirement = async (req: DeliverableRequirement) => {
    await deleteRequirement(projectId, deliverableId, req.id);
    setDeleteTarget(null);
    if (editingReqId === req.id) resetReqForm();
    setNotice("요구사항을 삭제했습니다.");
    refresh();
  };

  // K-14: 충족 확인 토글 (낙관적 업데이트, 실패 시 해당 항목만 원복)
  const handleToggleAssessment = async (req: DeliverableRequirement) => {
    if (assessingIds.has(req.id)) return;
    const isAssessed = req.assessment !== null;
    setActionError("");
    setAssessingIds((prev) => new Set(prev).add(req.id));
    replaceRequirement({
      ...req,
      assessment: isAssessed ? null : { assessedBy: { userId: "", name: "나" }, assessedAt: new Date().toISOString() },
    });
    try {
      const res = isAssessed
        ? await unassessRequirement(projectId, deliverableId, req.id)
        : await assessRequirement(projectId, deliverableId, req.id);
      replaceRequirement(res.data);
      fetchProgress();
    } catch (err) {
      replaceRequirement(req);
      setActionError(
        apiError(err, isAssessed ? "충족 확인 해제에 실패했습니다. 다시 시도해주세요." : "충족 확인에 실패했습니다. 다시 시도해주세요.")
      );
    } finally {
      setAssessingIds((prev) => {
        const next = new Set(prev);
        next.delete(req.id);
        return next;
      });
    }
  };

  // ── 업무 ─────────────────────────────────────────────────────────────
  const handleCreateTask = async (payload: CreateTaskPayload) => {
    await api.post(`/projects/${projectId}/tasks`, payload);
    setTaskDraft(null);
    setNotice("업무를 추가했습니다. 담당자는 업무 보드에서 진행 상태를 바꿀 수 있습니다.");
    refresh();
  };

  const handleLinkTask = async (e: FormEvent) => {
    e.preventDefault();
    if (!linkTaskId) return;
    setLinking(true);
    setActionError("");
    try {
      await api.patch(`/projects/${projectId}/tasks/${linkTaskId}`, { deliverableId });
      setLinkTaskId("");
      setNotice("업무를 이 제출물에 연결했습니다.");
      refresh();
    } catch (err) {
      setActionError(apiError(err, "업무를 연결하지 못했습니다. 다시 시도해주세요."));
    } finally {
      setLinking(false);
    }
  };

  return (
    <div className="flex h-screen bg-bb-bg">
      <Sidebar />

      <main className="flex-1 md:ml-64 mt-14 md:mt-0 overflow-y-auto p-4 md:p-8">
        <Link
          href={`/projects/${projectId}/deliverables`}
          className="inline-flex items-center gap-1 mb-3 text-xs text-bb-text2 hover:text-bb-text transition-colors"
        >
          <ChevronLeft size={14} />
          제출물 목록
        </Link>

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
              onClick={() => {
                setLoading(true);
                refresh();
              }}
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
            <section className="mb-6">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <h1 className="text-xl font-bold text-bb-text flex items-center gap-2 min-w-0 break-words">
                  <ClipboardList size={20} className="text-bb-primary shrink-0" />
                  {deliverable.title}
                </h1>
                {canWrite && (
                  <div className="flex gap-2 shrink-0">
                    <button
                      onClick={() => setShowEdit(true)}
                      className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-bb-text border border-bb-border hover:bg-bb-surface2 rounded-lg"
                    >
                      <Pencil size={13} />
                      수정
                    </button>
                    <button
                      onClick={() => setDeleteTarget({ kind: "deliverable" })}
                      className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-red-400 border border-red-500/30 hover:bg-red-500/10 rounded-lg"
                    >
                      <Trash2 size={13} />
                      삭제
                    </button>
                  </div>
                )}
              </div>
              <dl className="mt-4 grid gap-3 sm:grid-cols-3 text-sm">
                <div>
                  <dt className="text-xs text-bb-text2">제출 기한</dt>
                  <dd className="mt-0.5 text-bb-text">{deliverable.dueDate}</dd>
                </div>
                <div>
                  <dt className="text-xs text-bb-text2">제출 담당자</dt>
                  <dd className="mt-0.5 text-bb-text">{deliverable.ownerName || "미지정"}</dd>
                </div>
                <div>
                  <dt className="text-xs text-bb-text2">제출 경로</dt>
                  <dd className="mt-0.5 text-bb-text break-words">{deliverable.submissionMethod || "미설정"}</dd>
                </div>
              </dl>
              {deliverable.description && (
                <p className="mt-4 text-sm text-bb-text2 whitespace-pre-wrap break-words">{deliverable.description}</p>
              )}
            </section>

            {/* 탭 */}
            <div className="flex gap-1 mb-6 border-b border-bb-border overflow-x-auto">
              {TABS.map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => tab.implemented && setActiveTab(tab.id)}
                  className={`shrink-0 whitespace-nowrap px-4 py-2.5 text-sm font-medium rounded-t-lg transition-colors ${
                    !tab.implemented
                      ? "opacity-40 cursor-not-allowed text-bb-text2"
                      : activeTab === tab.id
                      ? "text-bb-primary border-b-2 border-bb-primary"
                      : "text-bb-text2 hover:text-bb-text"
                  }`}
                  title={!tab.implemented ? `${tab.label} 기능 준비 중입니다` : undefined}
                >
                  {tab.label}
                  {!tab.implemented && (
                    <span className="ml-1.5 text-[10px] px-1.5 py-0.5 bg-bb-surface2 rounded-full">준비 중</span>
                  )}
                </button>
              ))}
            </div>

            {activeTab === "overview" && (
              <div className="space-y-6">
                {notice && (
                  <div role="status" className="flex items-center gap-2 p-3 bg-teal-500/10 border border-teal-500/20 rounded-lg text-sm text-bb-text">
                    {notice}
                    <button onClick={() => setNotice("")} className="ml-auto text-bb-text2 hover:text-bb-text" aria-label="안내 닫기">
                      ✕
                    </button>
                  </div>
                )}
                {actionError && (
                  <div role="alert" className="flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
                    <AlertCircle size={14} className="shrink-0" />
                    {actionError}
                    <button onClick={() => setActionError("")} className="ml-auto text-red-400 hover:text-red-300" aria-label="안내 닫기">
                      ✕
                    </button>
                  </div>
                )}

                <ProgressSection progress={progress} progressError={progressError} onRetry={fetchProgress} />

                {/* 요구사항 */}
                <section className="bg-bb-surface border border-bb-border rounded-xl p-5">
                  <h2 className="text-sm font-semibold text-bb-text">
                    요구사항 <span className="text-bb-text2">{deliverable.requirements.length}</span>
                  </h2>
                  <p className="mt-1 text-xs text-bb-text2">
                    과제 안내의 필수 내용과 형식을 적으세요. 업무 완료와 요구사항 충족은 따로 확인합니다.
                  </p>
                  {!canWrite && (
                    <p className="mt-1 text-xs text-bb-text2">관찰자는 충족 여부를 확인할 수 없습니다.</p>
                  )}

                  {deliverable.requirements.length === 0 ? (
                    <p className="my-4 text-sm text-bb-text2">예: 조사 출처 포함, 발표자료 PDF 형식, 작품 설명서 첨부</p>
                  ) : (
                    <ul className="my-4 divide-y divide-bb-border">
                      {deliverable.requirements.map((req) => {
                        const assessed = req.assessment !== null;
                        const reqTasks = linkedTasks.filter((t) => t.requirementId === req.id);
                        const reqDone = reqTasks.filter((t) => t.status === "DONE").length;
                        const allDone = reqTasks.length > 0 && reqDone === reqTasks.length;
                        const openTasks = reqTasks.filter((t) => t.status !== "DONE");

                        return (
                          <li key={req.id} className="py-3">
                            <div className="flex items-start gap-3">
                              <button
                                onClick={() => canWrite && handleToggleAssessment(req)}
                                disabled={!canWrite || assessingIds.has(req.id)}
                                className={`shrink-0 mt-0.5 p-0.5 rounded transition-colors ${
                                  canWrite
                                    ? assessed
                                      ? "text-teal-400 hover:text-teal-300"
                                      : "text-bb-text2 hover:text-teal-400"
                                    : "text-bb-border cursor-not-allowed"
                                }`}
                                title={!canWrite ? "관찰자는 확인할 수 없습니다." : assessed ? "충족 확인 해제" : "충족 확인"}
                                aria-label={assessed ? "충족 확인 해제" : "충족 확인"}
                              >
                                {assessed ? <CheckSquare size={16} /> : <Circle size={16} />}
                              </button>
                              <span className={`shrink-0 mt-0.5 text-xs ${req.required ? "text-teal-400" : "text-bb-text2"}`}>
                                {req.required ? "필수" : "선택"}
                              </span>
                              <p className="flex-1 min-w-0 text-sm text-bb-text whitespace-pre-wrap break-words">{req.content}</p>
                            </div>

                            <div className="mt-2 ml-[52px] flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                              <span className="text-bb-text2">
                                연결 업무 {reqTasks.length}개{reqTasks.length > 0 && ` · ${reqDone}개 완료`}
                                {/* K-14 §6-3: 확인 전이면 아직 안 끝난 업무를 보여 "안 봤다"와 "부족하다"를 구분한다 */}
                                {!assessed && openTasks.length > 0 &&
                                  ` · ${openTasks
                                    .slice(0, 2)
                                    .map((t) => `${t.title}(${TASK_STATUS_LABEL[t.status]})`)
                                    .join(", ")}${openTasks.length > 2 ? ` 외 ${openTasks.length - 2}개` : ""}`}
                              </span>
                              {assessed && req.assessment && (
                                <span className="text-teal-400">
                                  충족 확인: {req.assessment.assessedBy.name} · {fmtRelative(req.assessment.assessedAt)}
                                </span>
                              )}
                              {allDone && !assessed && (
                                <span className="text-amber-400">업무가 모두 끝났습니다. 충족 여부를 확인하세요.</span>
                              )}
                              {canWrite && (
                                <>
                                  <button onClick={() => setTaskDraft({ requirementId: req.id })} className="text-bb-primary hover:underline">
                                    이 요구사항의 업무 만들기
                                  </button>
                                  <button onClick={() => startEditRequirement(req)} className="text-bb-text2 hover:underline">
                                    수정
                                  </button>
                                  <button onClick={() => setDeleteTarget({ kind: "requirement", req })} className="text-bb-text2 hover:underline">
                                    삭제
                                  </button>
                                </>
                              )}
                            </div>
                          </li>
                        );
                      })}
                    </ul>
                  )}

                  {canWrite && (
                    <form onSubmit={handleSaveRequirement} className="space-y-2 border-t border-bb-border pt-4">
                      <label htmlFor="requirement-content" className="block text-sm text-bb-text">
                        {editingReqId ? "요구사항 수정" : "요구사항 추가"}
                      </label>
                      {assessmentWillReset && editingReq?.assessment && (
                        <p role="alert" className="text-xs text-amber-400">
                          저장하면 {editingReq.assessment.assessedBy.name}님의 충족 확인이 풀립니다.
                        </p>
                      )}
                      {reqError && <p role="alert" className="text-xs text-red-400">{reqError}</p>}
                      <textarea
                        id="requirement-content"
                        rows={2}
                        maxLength={1000}
                        required
                        value={reqContent}
                        onChange={(e) => setReqContent(e.target.value)}
                        placeholder="예: 조사한 자료의 출처를 모두 표기"
                        className={`${FIELD} resize-none`}
                      />
                      <div className="flex flex-wrap items-center gap-3">
                        <label className="flex items-center gap-2 text-xs text-bb-text2 cursor-pointer">
                          <input type="checkbox" checked={reqRequired} onChange={(e) => setReqRequired(e.target.checked)} />
                          필수 조건
                        </label>
                        <div className="ml-auto flex gap-2">
                          {editingReqId && (
                            <button type="button" onClick={resetReqForm} className="px-3 py-1.5 text-xs text-bb-text2 hover:text-bb-text">
                              취소
                            </button>
                          )}
                          <button
                            type="submit"
                            disabled={reqSaving || !reqContent.trim()}
                            className="flex items-center gap-1 px-3 py-1.5 bg-bb-primary hover:bg-bb-primary-h disabled:opacity-50 text-white text-xs rounded-lg"
                          >
                            {reqSaving && <Loader2 size={12} className="animate-spin" />}
                            {editingReqId ? "수정 저장" : "요구사항 추가"}
                          </button>
                        </div>
                      </div>
                    </form>
                  )}
                </section>

                {/* 연결 업무 */}
                <section className="bg-bb-surface border border-bb-border rounded-xl p-5">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <h2 className="text-sm font-semibold text-bb-text">연결 업무 {linkedTasks.length}개</h2>
                    <div className="flex items-center gap-3">
                      <Link
                        href={`/projects/${projectId}/board?deliverable=${deliverableId}`}
                        className="text-xs text-bb-text2 hover:text-bb-primary"
                      >
                        보드에서 이 제출물 업무 보기
                      </Link>
                      {canWrite && (
                        <button
                          onClick={() => setTaskDraft({ requirementId: "" })}
                          className="flex items-center gap-1 px-3 py-1.5 bg-bb-primary hover:bg-bb-primary-h text-white text-xs rounded-lg"
                        >
                          <Plus size={13} />
                          업무 추가
                        </button>
                      )}
                    </div>
                  </div>
                  <p className="mt-1 text-xs text-bb-text2">
                    업무 완료 {linkedTasks.filter((t) => t.status === "DONE").length}개 · 제출 준비 완료를 뜻하지 않습니다.
                  </p>

                  {linkedTasks.length === 0 ? (
                    <p className="my-4 text-sm text-bb-text2">필요한 업무를 추가하거나 기존 업무를 연결하세요.</p>
                  ) : (
                    <ul className="mt-3 divide-y divide-bb-border">
                      {linkedTasks.map((t) => (
                        <li key={t.id} className="py-3">
                          <Link
                            href={`/projects/${projectId}/board?task=${t.id}`}
                            className="flex items-center gap-2 text-sm font-medium text-bb-text hover:text-bb-primary"
                          >
                            {t.title}
                            <ArrowRight size={14} />
                          </Link>
                          <p className="mt-1 text-xs text-bb-text2">
                            {TASK_STATUS_LABEL[t.status]} · {t.assignees.map((a) => a.name).join(", ") || "담당자 미지정"} ·{" "}
                            {t.dueDate || "마감 미설정"}
                            {t.requirementContent && ` · 요구사항: ${t.requirementContent}`}
                          </p>
                          <p className="mt-1 text-xs text-bb-text2 whitespace-pre-wrap break-words">
                            완료 기준: {t.completionCriteria || "미설정 — 업무를 열어 작성하세요"}
                          </p>
                        </li>
                      ))}
                    </ul>
                  )}

                  {canWrite && unlinkedTasks.length > 0 && (
                    <form onSubmit={handleLinkTask} className="mt-4 flex flex-wrap items-end gap-3 border-t border-bb-border pt-4">
                      <label className="flex-1 min-w-[200px] text-xs text-bb-text2">
                        기존 미연결 업무
                        <select
                          value={linkTaskId}
                          onChange={(e) => setLinkTaskId(e.target.value)}
                          required
                          className={`${FIELD} mt-1.5`}
                        >
                          <option value="">연결할 업무 선택</option>
                          {unlinkedTasks.map((t) => (
                            <option key={t.id} value={t.id}>
                              {t.title}
                            </option>
                          ))}
                        </select>
                      </label>
                      <button
                        disabled={linking || !linkTaskId}
                        className="flex items-center gap-1 px-3 py-2 text-xs text-bb-text border border-bb-border hover:bg-bb-surface2 disabled:opacity-50 rounded-lg"
                      >
                        {linking && <Loader2 size={12} className="animate-spin" />}
                        이 제출물에 연결
                      </button>
                    </form>
                  )}
                </section>
              </div>
            )}

            {activeTab === "review" && (
              <ReviewPanel
                projectId={projectId}
                deliverableId={deliverableId}
                canWrite={canWrite}
            {activeTab === "submit" && deliverable && (
              <SubmitPanel
                projectId={projectId}
                deliverableId={deliverableId}
                myRole={myRole}
                requiredCount={deliverable.requirements.filter((r) => r.required).length}
                submissionMethod={deliverable.submissionMethod}
                openTaskCount={progress ? progress.tasks.total - progress.tasks.completed : null}
                onGoTab={setActiveTab}
                onChanged={refresh}
              />
            )}

                myUserId={myUserId}
                tasks={tasks}
                onTasksChanged={refresh}
              />
            )}
          </>
        )}
      </main>

      {showEdit && deliverable && (
        <DeliverableFormModal
          initial={deliverable}
          owners={contributors}
          onSave={handleSaveDeliverable}
          onClose={() => setShowEdit(false)}
        />
      )}
      {deleteTarget && deliverable && (
        <ConfirmDeleteDialog
          title={deleteTarget.kind === "deliverable" ? `제출물 "${deliverable.title}"` : "요구사항"}
          onConfirm={() =>
            deleteTarget.kind === "deliverable" ? handleDeleteDeliverable() : handleDeleteRequirement(deleteTarget.req)
          }
          onClose={() => setDeleteTarget(null)}
        />
      )}
      {taskDraft && (
        <TaskModal
          mode="create"
          task={null}
          members={contributors}
          deliverables={deliverable ? [deliverable] : []}
          defaultDeliverableId={deliverableId}
          defaultRequirementId={taskDraft.requirementId}
          onClose={() => setTaskDraft(null)}
          onCreate={handleCreateTask}
          onUpdate={async () => {}}
          onDelete={async () => {}}
        />
      )}
    </div>
  );
}
