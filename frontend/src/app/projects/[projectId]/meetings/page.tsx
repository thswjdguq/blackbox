"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import { Meeting, CreateMeetingPayload } from "@/types/meeting";
import {
  FileText,
  Plus,
  Calendar,
  Hash,
  ChevronRight,
  AlertCircle,
  X,
  Loader2,
} from "lucide-react";

// ── 날짜 포맷 헬퍼 ─────────────────────────────────────────────────────
function formatDate(iso: string | null): string {
  if (!iso) return "날짜 미정";
  return new Date(iso).toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** 회의를 월별로 그룹화 */
function groupByMonth(meetings: Meeting[]): Map<string, Meeting[]> {
  const map = new Map<string, Meeting[]>();
  for (const m of meetings) {
    const key = m.meetingDate
      ? new Date(m.meetingDate).toLocaleDateString("ko-KR", { year: "numeric", month: "long" })
      : "날짜 미정";
    const existing = map.get(key) ?? [];
    map.set(key, [...existing, m]);
  }
  return map;
}

// ── 공용 입력 스타일 ───────────────────────────────────────────────────
const INPUT_CLS =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2.5 text-sm " +
  "text-bb-text placeholder-slate-500 focus:outline-none focus:border-indigo-500 " +
  "focus:ring-1 focus:ring-blue-500/30 transition-all";

// ── 회의 생성 모달 ─────────────────────────────────────────────────────
interface CreateModalProps {
  onClose: () => void;
  onCreated: (m: Meeting) => void;
  projectId: string;
}

function CreateMeetingModal({ onClose, onCreated, projectId }: CreateModalProps) {
  const [title, setTitle] = useState("");
  const [meetingDate, setMeetingDate] = useState("");
  const [purpose, setPurpose] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) { setError("제목을 입력해주세요"); return; }
    setSubmitting(true);
    setError("");

    try {
      const payload: CreateMeetingPayload = {
        title: title.trim(),
        meetingDate: meetingDate ? new Date(meetingDate).toISOString() : undefined,
        purpose: purpose.trim() || undefined,
      };
      const { data } = await api.post<Meeting>(`/projects/${projectId}/meetings`, payload);
      onCreated(data);
    } catch {
      setError("회의 생성에 실패했습니다. 리더 권한이 필요합니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* 배경 딤 */}
      <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" onClick={onClose} />

      <div className="relative w-full max-w-md bg-bb-surface border border-bb-border rounded-2xl shadow-2xl">
        {/* 헤더 */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-bb-border">
          <h2 className="text-base font-semibold text-bb-text">새 회의 만들기</h2>
          <button
            onClick={onClose}
            className="text-bb-text2 hover:text-bb-text p-1.5 rounded-lg hover:bg-slate-700 transition-all"
          >
            <X size={16} />
          </button>
        </div>

        {/* 폼 */}
        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          {error && (
            <div className="p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
              {error}
            </div>
          )}

          {/* 제목 */}
          <div>
            <label className="block text-xs font-medium text-bb-text2 mb-1.5">
              회의 제목 <span className="text-red-400">*</span>
            </label>
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="예: 1차 스프린트 회의"
              className={INPUT_CLS}
              maxLength={255}
            />
          </div>

          {/* 일시 */}
          <div>
            <label className="block text-xs font-medium text-bb-text2 mb-1.5">회의 일시</label>
            <input
              type="datetime-local"
              value={meetingDate}
              onChange={(e) => setMeetingDate(e.target.value)}
              className={INPUT_CLS}
            />
          </div>

          {/* 목적/안건 */}
          <div>
            <label className="block text-xs font-medium text-bb-text2 mb-1.5">안건</label>
            <textarea
              value={purpose}
              onChange={(e) => setPurpose(e.target.value)}
              placeholder="회의 목적 또는 주요 안건"
              rows={3}
              className={`${INPUT_CLS} resize-none`}
            />
          </div>

          <div className="flex justify-end gap-2 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm text-bb-text2 hover:text-bb-text transition-colors"
            >
              취소
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex items-center gap-2 px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700
                         disabled:opacity-50 text-white text-sm font-medium rounded-lg transition-all"
            >
              {submitting ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
              만들기
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── 회의 카드 ──────────────────────────────────────────────────────────
function MeetingCard({ meeting, projectId }: { meeting: Meeting; projectId: string }) {
  const router = useRouter();

  // 회의록이 작성된 경우 초록, 미작성이면 회색
  const hasNotes = Boolean(meeting.notes?.trim());

  return (
    <div
      onClick={() => router.push(`/projects/${projectId}/meetings/${meeting.id}`)}
      className="group bg-bb-surface border border-bb-border rounded-xl p-5 cursor-pointer
                 hover:border-slate-500 hover:bg-slate-800/80 transition-all flex items-center gap-4"
    >
      {/* 아이콘 */}
      <div className="w-10 h-10 rounded-xl bg-indigo-500/10 flex items-center justify-center shrink-0
                      group-hover:bg-indigo-500/20 transition-colors">
        <FileText size={18} className="text-indigo-400" />
      </div>

      {/* 정보 */}
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-bb-text truncate group-hover:text-white transition-colors">
          {meeting.title}
        </p>
        <div className="flex items-center gap-3 mt-1">
          <span className="flex items-center gap-1 text-xs text-bb-text2">
            <Calendar size={11} />
            {formatDate(meeting.meetingDate)}
          </span>
          {/* 체크인 코드 미리보기 */}
          <span className="flex items-center gap-1 text-xs text-slate-600 font-mono">
            <Hash size={10} />
            {meeting.checkinCode}
          </span>
        </div>
        {meeting.purpose && (
          <p className="text-xs text-bb-text2 mt-1 truncate">{meeting.purpose}</p>
        )}
      </div>

      {/* 상태 + 화살표 */}
      <div className="flex items-center gap-2 shrink-0">
        <span
          className={`text-[10px] font-medium px-2 py-0.5 rounded-full ${
            hasNotes
              ? "bg-green-500/15 text-green-400"
              : "bg-slate-700 text-bb-text2"
          }`}
        >
          {hasNotes ? "작성 완료" : "미작성"}
        </span>
        <ChevronRight size={15} className="text-slate-600 group-hover:text-bb-text2 transition-colors" />
      </div>
    </div>
  );
}

// ── 메인 페이지 ────────────────────────────────────────────────────────
export default function MeetingsPage() {
  const params = useParams();
  const router = useRouter();
  const projectId = params?.projectId as string;

  const [meetings, setMeetings] = useState<Meeting[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showCreate, setShowCreate] = useState(false);

  const fetchMeetings = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const { data } = await api.get<Meeting[]>(`/projects/${projectId}/meetings`);
      setMeetings(data);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401) { router.replace("/login"); return; }
      setError("회의 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [projectId, router]);

  useEffect(() => {
    if (!localStorage.getItem("accessToken")) { router.replace("/login"); return; }
    fetchMeetings();
  }, [fetchMeetings]);

  // 회의 생성 성공 콜백
  const handleCreated = (m: Meeting) => {
    setMeetings((prev) => [m, ...prev]);
    setShowCreate(false);
  };

  const grouped = groupByMonth(meetings);

  // ── 로딩 스켈레톤 ────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="min-h-screen bg-bb-bg">
        <Sidebar />
        <main className="ml-64 min-h-screen p-8">
          <div className="animate-pulse space-y-3">
            <div className="h-6 bg-bb-surface rounded w-36 mb-6" />
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-20 bg-bb-surface rounded-xl" />
            ))}
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar />

      <main className="ml-64 min-h-screen p-8">
        {/* 헤더 */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold text-bb-text">회의록</h1>
            <p className="text-sm text-bb-text2 mt-1">총 {meetings.length}개의 회의</p>
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700
                       text-white text-sm font-medium rounded-lg transition-colors"
          >
            <Plus size={15} />
            새 회의
          </button>
        </div>

        {/* 에러 */}
        {error && (
          <div className="flex items-center gap-3 p-4 bg-red-500/10 border border-red-500/20 rounded-xl mb-6">
            <AlertCircle size={18} className="text-red-400 shrink-0" />
            <p className="text-sm text-red-400">{error}</p>
            <button
              onClick={fetchMeetings}
              className="ml-auto text-xs text-red-400 hover:text-red-300 underline"
            >
              다시 시도
            </button>
          </div>
        )}

        {/* 빈 상태 */}
        {!error && meetings.length === 0 && (
          <div className="flex flex-col items-center justify-center py-32 text-center">
            <div className="w-16 h-16 rounded-2xl bg-bb-surface border border-bb-border flex items-center justify-center mb-4">
              <FileText size={28} className="text-slate-600" />
            </div>
            <p className="text-sm font-medium text-bb-text mb-1">회의가 없습니다</p>
            <p className="text-xs text-bb-text2 mb-6">첫 번째 회의를 만들어보세요</p>
            <button
              onClick={() => setShowCreate(true)}
              className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700
                         text-white text-sm font-medium rounded-lg transition-colors"
            >
              <Plus size={14} />
              새 회의 만들기
            </button>
          </div>
        )}

        {/* 월별 그룹 타임라인 */}
        {[...grouped.entries()].map(([month, list]) => (
          <section key={month} className="mb-8">
            <h2 className="text-xs font-semibold text-bb-text2 uppercase tracking-wider mb-3 px-1">
              {month}
            </h2>
            <div className="space-y-2">
              {list.map((m) => (
                <MeetingCard key={m.id} meeting={m} projectId={projectId} />
              ))}
            </div>
          </section>
        ))}
      </main>

      {/* 회의 생성 모달 */}
      {showCreate && (
        <CreateMeetingModal
          projectId={projectId}
          onClose={() => setShowCreate(false)}
          onCreated={handleCreated}
        />
      )}
    </div>
  );
}
