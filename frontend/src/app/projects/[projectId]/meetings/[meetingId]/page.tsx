"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import { Meeting, Attendee, UpdateMeetingPayload } from "@/types/meeting";
import { CreateTaskPayload } from "@/types/task";
import {
  ArrowLeft,
  Calendar,
  Hash,
  RefreshCw,
  Users,
  CheckCircle2,
  Circle,
  ClipboardList,
  Plus,
  Save,
  Loader2,
  AlertCircle,
  X,
  Sparkles,
  ChevronDown,
  ChevronUp,
  ExternalLink,
} from "lucide-react";

// ── 날짜 포맷 헬퍼 ─────────────────────────────────────────────────────
function formatDate(iso: string | null) {
  if (!iso) return "날짜 미정";
  return new Date(iso).toLocaleDateString("ko-KR", {
    year: "numeric", month: "long", day: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

// ── 공용 스타일 상수 ───────────────────────────────────────────────────
const TEXTAREA_CLS =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-4 py-3 text-sm " +
  "text-bb-text placeholder-slate-500 focus:outline-none focus:border-indigo-500 " +
  "focus:ring-1 focus:ring-blue-500/30 transition-all resize-none";

const INPUT_CLS =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2.5 text-sm " +
  "text-bb-text placeholder-slate-500 focus:outline-none focus:border-indigo-500 " +
  "focus:ring-1 focus:ring-blue-500/30 transition-all";

// ── 참석자 아바타 ──────────────────────────────────────────────────────
function AttendeeAvatar({ attendee }: { attendee: Attendee }) {
  const hue = attendee.name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360;
  return (
    <div className="flex items-center gap-3 p-3 bg-bb-surface2/50 rounded-xl border border-bb-border/50">
      <div
        style={{ background: `hsl(${hue} 55% 45%)` }}
        className="w-9 h-9 rounded-full flex items-center justify-center text-sm font-bold text-white shrink-0"
      >
        {attendee.name.slice(0, 2).toUpperCase()}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-medium text-bb-text truncate">{attendee.name}</p>
        <p className="text-xs text-bb-text2 truncate">{attendee.email}</p>
      </div>
      {attendee.checkedIn ? (
        <div className="flex items-center gap-1 text-xs text-green-500">
          <CheckCircle2 size={14} />
          <span>체크인</span>
        </div>
      ) : (
        <div className="flex items-center gap-1 text-xs text-bb-text2">
          <Circle size={14} />
          <span>미체크인</span>
        </div>
      )}
    </div>
  );
}

// ── AI 요약 패널 ───────────────────────────────────────────────────────
function AiSummaryPanel({ summary, onClose }: { summary: string; onClose: () => void }) {
  return (
    <div className="mt-4 p-4 bg-bb-primary/5 border border-bb-primary/20 rounded-xl relative">
      <button
        onClick={onClose}
        className="absolute top-3 right-3 text-bb-text2 hover:text-bb-text transition-colors"
      >
        <X size={14} />
      </button>
      <div className="flex items-center gap-2 mb-3">
        <Sparkles size={14} className="text-bb-primary" />
        <span className="text-xs font-semibold text-bb-primary">AI 요약</span>
      </div>
      <div className="text-sm text-bb-text leading-relaxed whitespace-pre-wrap">
        {summary}
      </div>
    </div>
  );
}

// ── 메인 페이지 ────────────────────────────────────────────────────────
export default function MeetingDetailPage() {
  const params    = useParams();
  const router    = useRouter();
  const projectId = params?.projectId as string;
  const meetingId = params?.meetingId as string;

  const [meeting,   setMeeting]   = useState<Meeting | null>(null);
  const [attendees, setAttendees] = useState<Attendee[]>([]);
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState("");

  // 회의록 편집
  const [editNotes,     setEditNotes]     = useState("");
  const [editDecisions, setEditDecisions] = useState("");
  const [saving,        setSaving]        = useState(false);
  const [saveSuccess,   setSaveSuccess]   = useState(false);

  // 코드 재생성
  const [regenerating, setRegenerating] = useState(false);

  // 액션 아이템 수동 생성
  const [showActionForm, setShowActionForm] = useState(false);
  const [actionTitle,    setActionTitle]    = useState("");
  const [actionAssignee, setActionAssignee] = useState("");
  const [addingAction,   setAddingAction]   = useState(false);

  // AI 요약
  const [summarizing,  setSummarizing]  = useState(false);
  const [aiSummary,    setAiSummary]    = useState<string | null>(null);

  // Notion 내보내기
  const [exporting,    setExporting]    = useState(false);
  const [notionUrl,    setNotionUrl]    = useState<string | null>(null);

  // AI 액션아이템 추출
  const [extracting,      setExtracting]      = useState(false);
  const [aiItems,         setAiItems]         = useState<string[] | null>(null);
  const [checkedAiItems,  setCheckedAiItems]  = useState<Set<number>>(new Set());
  const [addingAiItems,   setAddingAiItems]   = useState(false);
  const [aiItemsOpen,     setAiItemsOpen]     = useState(true);

  // ── 데이터 로드 ──────────────────────────────────────────────────────
  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [meetRes, attenRes] = await Promise.all([
        api.get<Meeting>(`/projects/${projectId}/meetings/${meetingId}`),
        api.get<Attendee[]>(`/projects/${projectId}/meetings/${meetingId}/attendees`),
      ]);
      setMeeting(meetRes.data);
      setAttendees(attenRes.data);
      setEditNotes(meetRes.data.notes ?? "");
      setEditDecisions(meetRes.data.decisions ?? "");
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401) { router.replace("/login"); return; }
      setError("회의 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [projectId, meetingId, router]);

  useEffect(() => {
    if (!localStorage.getItem("accessToken")) { router.replace("/login"); return; }
    fetchAll();
  }, [fetchAll]);

  // ── 저장 ─────────────────────────────────────────────────────────────
  const handleSave = async () => {
    setSaving(true);
    try {
      const payload: UpdateMeetingPayload = {
        notes: editNotes || undefined,
        decisions: editDecisions || undefined,
      };
      const { data } = await api.patch<Meeting>(
        `/projects/${projectId}/meetings/${meetingId}`, payload,
      );
      setMeeting(data);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2000);
    } catch {
      setError("저장에 실패했습니다. 리더 권한이 필요합니다.");
    } finally {
      setSaving(false);
    }
  };

  // ── 체크인 코드 재생성 ───────────────────────────────────────────────
  const handleRegenerateCode = async () => {
    setRegenerating(true);
    try {
      const { data } = await api.post<Meeting>(
        `/projects/${projectId}/meetings/${meetingId}/checkin-code/regenerate`,
      );
      setMeeting(data);
    } catch {
      setError("코드 재생성에 실패했습니다.");
    } finally {
      setRegenerating(false);
    }
  };

  // ── 수동 액션 아이템 ─────────────────────────────────────────────────
  const handleAddAction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!actionTitle.trim()) return;
    setAddingAction(true);
    try {
      const payload: CreateTaskPayload = {
        title: actionTitle.trim(),
        assigneeIds: actionAssignee ? [actionAssignee] : [],
      };
      await api.post(`/projects/${projectId}/meetings/${meetingId}/action-items`, payload);
      setActionTitle("");
      setActionAssignee("");
      setShowActionForm(false);
    } catch {
      setError("액션 아이템 생성에 실패했습니다.");
    } finally {
      setAddingAction(false);
    }
  };

  // ── AI 요약 ───────────────────────────────────────────────────────────
  const handleAiSummarize = async () => {
    if (!editNotes.trim() && !editDecisions.trim()) {
      setError("회의록 내용을 먼저 입력하세요.");
      return;
    }
    setSummarizing(true);
    try {
      const { data } = await api.post<{ summary: string }>(
        `/projects/${projectId}/meetings/${meetingId}/ai/summarize`,
      );
      setAiSummary(data.summary);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? "AI 요약에 실패했습니다. CLAUDE_API_KEY를 확인하세요.");
    } finally {
      setSummarizing(false);
    }
  };

  // ── Notion 내보내기 ──────────────────────────────────────────
  const handleNotionExport = async () => {
    if (!editNotes.trim() && !editDecisions.trim()) {
      setError("회의록 내용을 먼저 입력하고 저장하세요.");
      return;
    }
    setExporting(true);
    try {
      const { data } = await api.post<{ pageUrl: string }>(
        `/projects/${projectId}/meetings/${meetingId}/notion/export`,
      );
      setNotionUrl(data.pageUrl);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? "Notion 내보내기 실패. NOTION_API_KEY와 NOTION_PARENT_PAGE_ID를 확인하세요.");
    } finally {
      setExporting(false);
    }
  };

  // ── AI 액션아이템 추출 ────────────────────────────────────────────────
  const handleAiExtract = async () => {
    if (!editNotes.trim() && !editDecisions.trim()) {
      setError("회의록 내용을 먼저 입력하세요.");
      return;
    }
    setExtracting(true);
    setAiItems(null);
    try {
      const { data } = await api.post<{ items: string[] }>(
        `/projects/${projectId}/meetings/${meetingId}/ai/extract-actions`,
      );
      setAiItems(data.items);
      setCheckedAiItems(new Set(data.items.map((_, i) => i)));
      setAiItemsOpen(true);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? "AI 추출에 실패했습니다. CLAUDE_API_KEY를 확인하세요.");
    } finally {
      setExtracting(false);
    }
  };

  // ── AI 아이템 → 칸반 일괄 추가 ─────────────────────────────────────
  const handleAddAiItems = async () => {
    if (!aiItems || checkedAiItems.size === 0) return;
    setAddingAiItems(true);
    const selected = aiItems.filter((_, i) => checkedAiItems.has(i));
    try {
      await Promise.all(
        selected.map((title) =>
          api.post(`/projects/${projectId}/meetings/${meetingId}/action-items`, {
            title,
            assigneeIds: [],
          }),
        ),
      );
      setAiItems(null);
      setCheckedAiItems(new Set());
    } catch {
      setError("일부 액션아이템 추가에 실패했습니다.");
    } finally {
      setAddingAiItems(false);
    }
  };

  const toggleAiItem = (i: number) => {
    setCheckedAiItems((prev) => {
      const next = new Set(prev);
      next.has(i) ? next.delete(i) : next.add(i);
      return next;
    });
  };

  // ── 로딩 ─────────────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="min-h-screen bg-bb-bg">
        <Sidebar />
        <main className="ml-64 min-h-screen p-8">
          <div className="animate-pulse space-y-4 max-w-3xl">
            <div className="h-6 bg-bb-surface rounded w-24" />
            <div className="h-8 bg-bb-surface rounded w-64" />
            <div className="h-40 bg-bb-surface rounded-xl" />
            <div className="h-40 bg-bb-surface rounded-xl" />
          </div>
        </main>
      </div>
    );
  }

  if (!meeting) return null;

  const checkinCount = attendees.filter((a) => a.checkedIn).length;

  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar />

      <main className="ml-64 min-h-screen p-8">
        <div className="max-w-3xl">
          {/* 뒤로 가기 */}
          <button
            onClick={() => router.push(`/projects/${projectId}/meetings`)}
            className="flex items-center gap-2 text-sm text-bb-text2 hover:text-bb-text transition-colors mb-6"
          >
            <ArrowLeft size={15} />
            회의 목록으로
          </button>

          {/* 에러 배너 */}
          {error && (
            <div className="flex items-center gap-3 p-4 bg-red-500/10 border border-red-500/20 rounded-xl mb-6">
              <AlertCircle size={16} className="text-red-400 shrink-0" />
              <p className="text-sm text-red-400 flex-1">{error}</p>
              <button onClick={() => setError("")} className="text-red-400 hover:text-red-300">
                <X size={14} />
              </button>
            </div>
          )}

          {/* 회의 헤더 */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-6 mb-6">
            <h1 className="text-2xl font-bold text-bb-text mb-3">{meeting.title}</h1>
            <div className="flex flex-wrap items-center gap-4 text-sm text-bb-text2">
              <span className="flex items-center gap-1.5">
                <Calendar size={14} />
                {formatDate(meeting.meetingDate)}
              </span>
              <span className="flex items-center gap-1.5">
                <Users size={14} />
                참석 {checkinCount} / {attendees.length}명
              </span>
            </div>
            {meeting.purpose && (
              <p className="mt-3 text-sm text-bb-text2 leading-relaxed border-t border-bb-border/60 pt-3">
                {meeting.purpose}
              </p>
            )}
          </div>

          {/* 체크인 코드 */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-6 mb-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-bb-text flex items-center gap-2">
                <Hash size={16} className="text-bb-primary" />
                체크인 코드
              </h2>
              <button
                onClick={handleRegenerateCode}
                disabled={regenerating}
                className="flex items-center gap-1.5 text-xs text-bb-text2 hover:text-bb-text
                           px-3 py-1.5 rounded-lg border border-bb-border hover:border-bb-border
                           transition-all disabled:opacity-50"
              >
                <RefreshCw size={12} className={regenerating ? "animate-spin" : ""} />
                재생성
              </button>
            </div>
            <div className="bg-bb-bg rounded-xl p-6 text-center">
              <span className="font-mono text-4xl font-bold text-bb-primary tracking-[0.3em]">
                {meeting.checkinCode}
              </span>
              <p className="text-xs text-bb-text2 mt-2">팀원에게 이 코드를 공유해 출석을 확인하세요</p>
            </div>
          </div>

          {/* 참석자 */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-6 mb-6">
            <h2 className="text-lg font-semibold text-bb-text mb-4 flex items-center gap-2">
              <Users size={16} className="text-bb-primary" />
              참석자 ({attendees.length})
            </h2>
            {attendees.length === 0 ? (
              <p className="text-sm text-bb-text2">등록된 참석자가 없습니다.</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {attendees.map((a) => (
                  <AttendeeAvatar key={a.userId} attendee={a} />
                ))}
              </div>
            )}
          </div>

          {/* 회의록 편집 */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-6 mb-6">
            <h2 className="text-lg font-semibold text-bb-text mb-4">회의록</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-bb-text2 mb-1.5">내용</label>
                <textarea
                  value={editNotes}
                  onChange={(e) => setEditNotes(e.target.value)}
                  placeholder="회의 내용을 자유롭게 기록하세요..."
                  rows={8}
                  className={TEXTAREA_CLS}
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-bb-text2 mb-1.5">결정사항</label>
                <textarea
                  value={editDecisions}
                  onChange={(e) => setEditDecisions(e.target.value)}
                  placeholder="회의에서 결정된 사항을 기록하세요..."
                  rows={4}
                  className={TEXTAREA_CLS}
                />
              </div>

              {/* AI 요약 패널 */}
              {aiSummary && (
                <AiSummaryPanel summary={aiSummary} onClose={() => setAiSummary(null)} />
              )}

              {/* Notion 내보내기 성공 링크 */}
              {notionUrl && (
                <div className="flex items-center gap-2 p-3 bg-bb-primary/5 border border-bb-primary/20 rounded-lg">
                  <ExternalLink size={13} className="text-bb-primary shrink-0" />
                  <span className="text-xs text-bb-text2">Notion 페이지 생성됨:</span>
                  <a
                    href={notionUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-xs text-bb-primary hover:underline truncate"
                  >
                    {notionUrl}
                  </a>
                  <button onClick={() => setNotionUrl(null)} className="ml-auto text-bb-text2 hover:text-bb-text shrink-0">
                    <X size={13} />
                  </button>
                </div>
              )}

              <div className="flex items-center justify-between pt-1">
                {/* 왼쪽: AI 요약 + Notion 버튼 */}
                <div className="flex items-center gap-2">
                  <button
                    onClick={handleAiSummarize}
                    disabled={summarizing}
                    className="flex items-center gap-2 px-3 py-2 text-sm text-bb-primary
                               border border-bb-primary/30 hover:bg-bb-primary/5 rounded-lg
                               transition-all disabled:opacity-50"
                  >
                    {summarizing ? <Loader2 size={14} className="animate-spin" /> : <Sparkles size={14} />}
                    {summarizing ? "요약 중..." : "AI 요약"}
                  </button>

                  <button
                    onClick={handleNotionExport}
                    disabled={exporting}
                    className="flex items-center gap-2 px-3 py-2 text-sm text-slate-600 dark:text-bb-text2
                               border border-bb-border hover:bg-bb-surface2 rounded-lg
                               transition-all disabled:opacity-50"
                  >
                    {exporting ? <Loader2 size={14} className="animate-spin" /> : <ExternalLink size={14} />}
                    {exporting ? "내보내는 중..." : "Notion으로 내보내기"}
                  </button>
                </div>

                {/* 저장 버튼 */}
                <button
                  onClick={handleSave}
                  disabled={saving}
                  className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium rounded-lg transition-all ${
                    saveSuccess
                      ? "bg-green-600 text-white"
                      : "bg-bb-primary hover:bg-bb-primary-h text-white disabled:opacity-50"
                  }`}
                >
                  {saving ? (
                    <Loader2 size={14} className="animate-spin" />
                  ) : saveSuccess ? (
                    <CheckCircle2 size={14} />
                  ) : (
                    <Save size={14} />
                  )}
                  {saveSuccess ? "저장됨" : "저장"}
                </button>
              </div>
            </div>
          </div>

          {/* 액션 아이템 */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-bb-text flex items-center gap-2">
                <ClipboardList size={16} className="text-bb-accent" />
                액션 아이템
              </h2>
              <div className="flex items-center gap-2">
                {/* AI 자동 추출 버튼 */}
                <button
                  onClick={handleAiExtract}
                  disabled={extracting}
                  className="flex items-center gap-1.5 text-xs text-bb-primary hover:text-bb-primary-h
                             px-3 py-1.5 rounded-lg border border-bb-primary/30 hover:border-bb-primary/50
                             bg-bb-primary/5 hover:bg-bb-primary/10 transition-all disabled:opacity-50"
                >
                  {extracting
                    ? <Loader2 size={12} className="animate-spin" />
                    : <Sparkles size={12} />}
                  {extracting ? "추출 중..." : "AI 자동 추출"}
                </button>
                {/* 수동 추가 버튼 */}
                <button
                  onClick={() => setShowActionForm((v) => !v)}
                  className="flex items-center gap-1.5 text-xs text-bb-text2 hover:text-bb-text
                             px-3 py-1.5 rounded-lg border border-bb-border hover:border-bb-border
                             bg-bb-surface2/50 hover:bg-bb-surface2 transition-all"
                >
                  <Plus size={12} />
                  직접 추가
                </button>
              </div>
            </div>

            <p className="text-xs text-bb-text2 mb-4">
              액션 아이템은 칸반 보드의 태스크로 즉시 생성됩니다.
            </p>

            {/* AI 추출 결과 */}
            {aiItems !== null && (
              <div className="mb-4 bg-bb-bg border border-bb-border rounded-xl overflow-hidden">
                <button
                  onClick={() => setAiItemsOpen((v) => !v)}
                  className="w-full flex items-center justify-between px-4 py-3
                             text-sm font-medium text-bb-text hover:bg-bb-surface2/50 transition-colors"
                >
                  <span className="flex items-center gap-2">
                    <Sparkles size={13} className="text-bb-primary" />
                    AI가 추출한 액션아이템 ({aiItems.length}개)
                  </span>
                  {aiItemsOpen ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                </button>

                {aiItemsOpen && (
                  <div className="px-4 pb-4 space-y-2">
                    {aiItems.length === 0 ? (
                      <p className="text-xs text-bb-text2 py-2">추출된 액션아이템이 없습니다.</p>
                    ) : (
                      aiItems.map((item, i) => (
                        <label
                          key={i}
                          className="flex items-center gap-3 p-2.5 rounded-lg hover:bg-bb-surface2/50 cursor-pointer transition-colors"
                        >
                          <input
                            type="checkbox"
                            checked={checkedAiItems.has(i)}
                            onChange={() => toggleAiItem(i)}
                            className="w-4 h-4 rounded accent-indigo-500"
                          />
                          <span className="text-sm text-bb-text">{item}</span>
                        </label>
                      ))
                    )}
                    {aiItems.length > 0 && (
                      <div className="flex justify-end gap-2 pt-2 border-t border-bb-border/60">
                        <button
                          onClick={() => setAiItems(null)}
                          className="text-xs text-bb-text2 hover:text-bb-text px-3 py-1.5 transition-colors"
                        >
                          취소
                        </button>
                        <button
                          onClick={handleAddAiItems}
                          disabled={addingAiItems || checkedAiItems.size === 0}
                          className="flex items-center gap-1.5 text-xs px-4 py-1.5
                                     bg-bb-primary hover:bg-bb-primary-h text-white
                                     rounded-lg transition-all disabled:opacity-50"
                        >
                          {addingAiItems
                            ? <Loader2 size={12} className="animate-spin" />
                            : <Plus size={12} />}
                          {checkedAiItems.size}개 칸반에 추가
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}

            {/* 수동 추가 폼 */}
            {showActionForm && (
              <form onSubmit={handleAddAction} className="bg-bb-bg rounded-xl p-4 space-y-3">
                <div>
                  <label className="block text-xs font-medium text-bb-text2 mb-1.5">
                    태스크 제목 <span className="text-red-400">*</span>
                  </label>
                  <input
                    value={actionTitle}
                    onChange={(e) => setActionTitle(e.target.value)}
                    placeholder="예: API 명세서 작성"
                    className={INPUT_CLS}
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-bb-text2 mb-1.5">담당자 (선택)</label>
                  {attendees.length > 0 && (
                    <div className="flex flex-wrap gap-2">
                      {attendees.map((a) => (
                        <button
                          key={a.userId}
                          type="button"
                          onClick={() =>
                            setActionAssignee((prev) => (prev === a.userId ? "" : a.userId))
                          }
                          className={`text-xs px-2.5 py-1 rounded-full border transition-all ${
                            actionAssignee === a.userId
                              ? "border-bb-accent bg-bb-accent/15 text-bb-accent"
                              : "border-bb-border text-bb-text2 hover:border-bb-border"
                          }`}
                        >
                          {a.name}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                <div className="flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setShowActionForm(false)}
                    className="text-sm text-bb-text2 hover:text-bb-text px-3 py-1.5 transition-colors"
                  >
                    취소
                  </button>
                  <button
                    type="submit"
                    disabled={addingAction}
                    className="flex items-center gap-1.5 text-sm px-4 py-1.5
                               bg-bb-accent hover:bg-bb-accent/80 text-white
                               rounded-lg transition-all disabled:opacity-50"
                  >
                    {addingAction ? <Loader2 size={13} className="animate-spin" /> : <Plus size={13} />}
                    추가
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
