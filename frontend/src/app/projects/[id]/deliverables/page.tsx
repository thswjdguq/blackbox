"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import Sidebar from "@/components/Sidebar";
import {
  getDeliverables,
  createDeliverable,
  deleteDeliverable,
} from "@/lib/api/deliverable";
import type { Deliverable, CreateDeliverablePayload } from "@/types/deliverable";
import {
  ClipboardList,
  Plus,
  Trash2,
  ChevronRight,
  AlertCircle,
  RefreshCw,
  CalendarDays,
  User,
  Loader2,
} from "lucide-react";

// ── 날짜 포맷 ────────────────────────────────────────────────────────────
function fmtDate(iso: string | null) {
  if (!iso) return "-";
  return new Date(iso).toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

// ── 스켈레톤 카드 (로딩 중 표시용) ───────────────────────────────────────
function SkeletonCard() {
  return (
    <div className="bg-bb-surface border border-bb-border rounded-xl p-5 animate-pulse">
      <div className="h-4 bg-bb-surface2 rounded w-2/3 mb-3" />
      <div className="h-3 bg-bb-surface2 rounded w-1/3" />
    </div>
  );
}

// ── 제출물 추가 폼 ────────────────────────────────────────────────────────
function AddDeliverableForm({
  onSave,
  onCancel,
}: {
  onSave: (payload: CreateDeliverablePayload) => Promise<void>;
  onCancel: () => void;
}) {
  const [title, setTitle]     = useState("");
  const [dueDate, setDueDate] = useState("");
  const [desc, setDesc]       = useState("");
  const [saving, setSaving]   = useState(false);
  const [error, setError]     = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !dueDate) return;
    setSaving(true);
    setError("");
    try {
      await onSave({ title: title.trim(), dueDate, description: desc || undefined });
    } catch (err) {
      // 서버 오류 메시지가 있으면 표시, 없으면 기본 안내
      const detail =
        (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setError(detail ?? "저장에 실패했습니다. 다시 시도해주세요.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="bg-bb-surface border border-bb-border rounded-xl p-5 space-y-3"
    >
      <p className="text-sm font-semibold text-bb-text">새 제출물 추가</p>

      {/* 오류 메시지 — 저장 실패 시 표시, 입력값은 보존 */}
      {error && (
        <div className="flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
          <AlertCircle size={14} className="shrink-0" />
          {error}
        </div>
      )}

      <input
        type="text"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="제출물 제목 (필수)"
        required
        className="w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm text-bb-text placeholder-slate-400 focus:outline-none focus:border-indigo-500"
      />
      <input
        type="date"
        value={dueDate}
        onChange={(e) => setDueDate(e.target.value)}
        required
        className="w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm text-bb-text focus:outline-none focus:border-indigo-500"
      />
      <textarea
        value={desc}
        onChange={(e) => setDesc(e.target.value)}
        placeholder="설명 (선택)"
        rows={2}
        className="w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm text-bb-text placeholder-slate-400 focus:outline-none focus:border-indigo-500 resize-none"
      />

      <div className="flex gap-2 justify-end">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-sm text-bb-text2 hover:text-bb-text transition-colors"
        >
          취소
        </button>
        <button
          type="submit"
          disabled={saving || !title.trim() || !dueDate}
          className="flex items-center gap-1.5 px-4 py-2 bg-bb-primary hover:bg-bb-primary-h disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm rounded-lg transition-colors"
        >
          {saving && <Loader2 size={14} className="animate-spin" />}
          저장
        </button>
      </div>
    </form>
  );
}

// ── 메인 ─────────────────────────────────────────────────────────────────
export default function DeliverablesPage() {
  const params    = useParams();
  const projectId = params?.id as string;

  const [deliverables, setDeliverables] = useState<Deliverable[]>([]);
  const [myRole,       setMyRole]       = useState<string | null>(null);
  const [loading,      setLoading]      = useState(true);
  const [error,        setError]        = useState("");
  const [showForm,     setShowForm]     = useState(false);
  const [deleteError,  setDeleteError]  = useState("");

  // 팀장·팀원만 쓰기 가능, 관찰자는 읽기 전용
  const canWrite = myRole === "LEADER" || myRole === "MEMBER";

  const fetchDeliverables = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      // 제출물 목록과 내 역할을 동시에 조회
      const [delRes, projRes] = await Promise.all([
        getDeliverables(projectId),
        import("@/lib/api").then(({ default: api }) =>
          api.get<{ myRole: string }>(`/projects/${projectId}`)
        ),
      ]);
      setDeliverables(delRes.data);
      setMyRole(projRes.data.myRole);
    } catch {
      setError("제출물 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    fetchDeliverables();
  }, [fetchDeliverables]);

  const handleCreate = async (payload: CreateDeliverablePayload) => {
    await createDeliverable(projectId, payload);
    setShowForm(false);
    // 저장 성공 후 서버 응답으로 목록 갱신 (낙관적 업데이트 대신 재조회)
    fetchDeliverables();
  };

  const handleDelete = async (deliverableId: string) => {
    setDeleteError("");
    try {
      await deleteDeliverable(projectId, deliverableId);
      setDeliverables((prev) => prev.filter((d) => d.id !== deliverableId));
    } catch (err) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        // 연결된 업무가 있을 때 서버에서 409 반환
        setDeleteError("연결된 업무를 먼저 해제한 뒤 삭제해주세요.");
      } else {
        setDeleteError("삭제에 실패했습니다. 다시 시도해주세요.");
      }
    }
  };

  return (
    <div className="flex h-screen bg-bb-bg">
      <Sidebar />

      <main className="flex-1 ml-64 overflow-y-auto p-8">
        {/* 헤더 */}
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-xl font-bold text-bb-text flex items-center gap-2">
            <ClipboardList size={20} className="text-bb-primary" />
            제출물
          </h1>
          {/* 팀장·팀원만 추가 버튼 표시 */}
          {canWrite && !showForm && (
            <button
              onClick={() => setShowForm(true)}
              className="flex items-center gap-1.5 px-4 py-2 bg-bb-primary hover:bg-bb-primary-h text-white text-sm rounded-lg transition-colors"
            >
              <Plus size={15} />
              제출물 추가
            </button>
          )}
        </div>

        {/* 삭제 오류 배너 */}
        {deleteError && (
          <div className="mb-4 flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
            <AlertCircle size={14} className="shrink-0" />
            {deleteError}
            <button
              onClick={() => setDeleteError("")}
              className="ml-auto text-red-400 hover:text-red-300"
            >
              ✕
            </button>
          </div>
        )}

        {/* 추가 폼 */}
        {showForm && (
          <div className="mb-4">
            <AddDeliverableForm
              onSave={handleCreate}
              onCancel={() => setShowForm(false)}
            />
          </div>
        )}

        {/* 로딩 상태 */}
        {loading && (
          <div className="space-y-3">
            <SkeletonCard />
            <SkeletonCard />
            <SkeletonCard />
          </div>
        )}

        {/* 네트워크 오류 */}
        {!loading && error && (
          <div className="flex flex-col items-center gap-3 py-20 text-center">
            <AlertCircle size={32} className="text-red-400" />
            <p className="text-sm text-bb-text2">{error}</p>
            <button
              onClick={fetchDeliverables}
              className="flex items-center gap-1.5 px-4 py-2 text-sm text-bb-text2 hover:text-bb-text border border-bb-border rounded-lg transition-colors"
            >
              <RefreshCw size={14} />
              다시 시도
            </button>
          </div>
        )}

        {/* 0건 빈 상태 */}
        {!loading && !error && deliverables.length === 0 && (
          <div className="flex flex-col items-center gap-3 py-20 text-center">
            <ClipboardList size={32} className="text-bb-text2 opacity-40" />
            <p className="text-sm text-bb-text2">등록된 제출물이 없습니다.</p>
            {canWrite && (
              <button
                onClick={() => setShowForm(true)}
                className="flex items-center gap-1.5 px-4 py-2 bg-bb-primary hover:bg-bb-primary-h text-white text-sm rounded-lg transition-colors"
              >
                <Plus size={14} />
                첫 제출물 추가
              </button>
            )}
          </div>
        )}

        {/* 제출물 카드 목록 */}
        {!loading && !error && deliverables.length > 0 && (
          <div className="space-y-3">
            {deliverables.map((d) => (
              <div
                key={d.id}
                className="group bg-bb-surface border border-bb-border rounded-xl p-5 hover:border-indigo-500/40 transition-colors"
              >
                <div className="flex items-start justify-between gap-4">
                  {/* 제출물 정보 */}
                  <Link
                    href={`/projects/${projectId}/deliverables/${d.id}`}
                    className="flex-1 min-w-0"
                  >
                    <p className="text-sm font-semibold text-bb-text group-hover:text-bb-primary transition-colors truncate">
                      {d.title}
                    </p>
                    <div className="flex items-center gap-4 mt-1.5 text-xs text-bb-text2">
                      {/* 기한 */}
                      <span className="flex items-center gap-1">
                        <CalendarDays size={12} />
                        {fmtDate(d.dueDate)}
                      </span>
                      {/* 담당자 */}
                      {d.ownerName && (
                        <span className="flex items-center gap-1">
                          <User size={12} />
                          {d.ownerName}
                        </span>
                      )}
                      {/* 요구사항 수 */}
                      <span>
                        요구사항 {d.requirements.length}개
                        {d.requirements.filter((r) => r.required).length > 0 &&
                          ` (필수 ${d.requirements.filter((r) => r.required).length}개)`}
                      </span>
                    </div>
                  </Link>

                  {/* 오른쪽 버튼 영역 */}
                  <div className="flex items-center gap-1 shrink-0">
                    {canWrite && (
                      <button
                        onClick={() => handleDelete(d.id)}
                        className="p-1.5 rounded-lg text-bb-text2 hover:text-red-400 hover:bg-red-500/10 opacity-0 group-hover:opacity-100 transition-all"
                        aria-label="제출물 삭제"
                      >
                        <Trash2 size={14} />
                      </button>
                    )}
                    <ChevronRight size={16} className="text-bb-text2" />
                  </div>
                </div>

                {/* 설명 (있을 때만) */}
                {d.description && (
                  <p className="mt-2 text-xs text-bb-text2 line-clamp-2">
                    {d.description}
                  </p>
                )}
              </div>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
