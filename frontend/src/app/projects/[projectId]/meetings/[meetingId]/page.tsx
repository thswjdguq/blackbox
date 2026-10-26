"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import AttendancePanel from "@/components/meeting/AttendancePanel";
import MeetingOutcomes from "@/components/meeting/MeetingOutcomes";
import MinutesEditor from "@/components/meeting/MinutesEditor";
import { useMyRole } from "@/hooks/useMyRole";
import Link from "next/link";
import ProjectNav from "@/components/project/ProjectNav";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import { Meeting, Attendee, UpdateMeetingPayload } from "@/types/meeting";
import { CreateTaskPayload } from "@/types/task";

import {
  ArrowLeft,
  Calendar,
  Users,
  CheckCircle2,
  ClipboardList,
  Plus,
  Save,
  Loader2,
  AlertCircle,
  X,
  Sparkles,
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
const INPUT_CLS =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2.5 text-sm " +
  "text-bb-text placeholder-slate-500 focus:outline-none focus:border-indigo-500 " +
  "focus:ring-1 focus:ring-blue-500/30 transition-all";

// ── 메인 페이지 ────────────────────────────────────────────────────────
export default function MeetingDetailPage() {
  const params    = useParams();
  const router    = useRouter();
  const projectId = params?.projectId as string;
  const meetingId = params?.meetingId as string;

  const [meeting,   setMeeting]   = useState<Meeting | null>(null);
  const [attendees, setAttendees] = useState<Attendee[]>([]);
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);
  const [loading,   setLoading]   = useState(true);
  const [error,     setError]     = useState("");

  const role = useMyRole(projectId);
  const canWrite = role === "LEADER" || role === "MEMBER";

  // 회의록 편집
  const [editNotes,     setEditNotes]     = useState("");
  const [editDecisions, setEditDecisions] = useState("");
  const [saving,        setSaving]        = useState(false);
  const [saveSuccess,   setSaveSuccess]   = useState(false);

  const dirty = meeting !== null && (editNotes !== (meeting.notes ?? "") || editDecisions !== (meeting.decisions ?? ""));
  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = ""; };
    const followLink = (e: MouseEvent) => {
      const target = e.target instanceof Element ? e.target.closest("a[href]") : null;
      if (target && !window.confirm("저장하지 않은 회의록이 있습니다. 이동할까요?")) { e.preventDefault(); e.stopPropagation(); }
    };
    window.addEventListener("beforeunload", warn);
    document.addEventListener("click", followLink, true);
    return () => { window.removeEventListener("beforeunload", warn); document.removeEventListener("click", followLink, true); };
  }, [dirty]);

  // 액션 아이템 수동 생성
  const [showActionForm, setShowActionForm] = useState(false);
  const [actionTitle,    setActionTitle]    = useState("");
  const [actionAssignee, setActionAssignee] = useState("");
  const [addingAction,   setAddingAction]   = useState(false);

  const [extracting, setExtracting] = useState(false);

  // 토스트
  const [toast, setToast] = useState<string | null>(null);
  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(null), 3000);
  };

  // 프로젝트 멤버 (역할 배정용)
  const [members, setMembers] = useState<{ userId: string; name: string; email: string; role: string }[]>([]);

  // ── 데이터 로드 ──────────────────────────────────────────────────────
  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [meetRes, attenRes, memberRes, profileRes] = await Promise.all([
        api.get<Meeting>(`/projects/${projectId}/meetings/${meetingId}`),
        api.get<Attendee[]>(`/projects/${projectId}/meetings/${meetingId}/attendees`),
        api.get<{ userId: string; name: string; email: string; role: string }[]>(`/projects/${projectId}/members`),
        api.get<{ id: string }>("/auth/profile"),
      ]);
      setMeeting(meetRes.data);
      setAttendees(attenRes.data);
      setMembers(memberRes.data);
      setCurrentUserId(profileRes.data.id);
      setEditNotes(meetRes.data.notes ?? "");
      setEditDecisions(meetRes.data.decisions ?? "");
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401) { router.replace("/login"); return; }
      setError("회의 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  // router는 Next.js App Router에서 안정적 참조 — 의존성 제외해 불필요한 리패치 방지
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId, meetingId]);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  // ── 저장 (내부 공유용) ───────────────────────────────────────────────
  const saveNow = async (decisionsOverride?: string): Promise<boolean> => {
    if (!canWrite) throw new Error("팀장·팀원만 회의록을 수정할 수 있습니다.");
    // Detect an already saved edit before overwriting it. This is not an atomic lock.
    const { data: latest } = await api.get<Meeting>(`/projects/${projectId}/meetings/${meetingId}`);
    if ((latest.notes ?? "") !== (meeting?.notes ?? "") || (latest.decisions ?? "") !== (meeting?.decisions ?? "")) {
      throw new Error("다른 팀원이 회의록을 수정했습니다. 작성 내용을 복사한 뒤 새로고침하여 변경 내용을 확인하세요.");
    }
    // Empty strings explicitly clear the server field; undefined would keep old content.
    const payload: UpdateMeetingPayload = { notes: editNotes, decisions: decisionsOverride ?? editDecisions };
    const { data } = await api.patch<Meeting>(
      `/projects/${projectId}/meetings/${meetingId}`, payload,
    );
    // 서버 응답으로 meeting 메타데이터만 동기화
    // editNotes / editDecisions 는 사용자 입력값을 유지 — 덮어쓰지 않음
    setMeeting(data);
    if (decisionsOverride !== undefined) setEditDecisions(data.decisions ?? "");
    return true;
  };

  const handleSave = async () => {
    setError("");
    setSaving(true);
    try {
      await saveNow();
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2000);
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })
        ?.response?.data;
      setError(data?.detail ?? data?.message ?? (err instanceof Error ? err.message : "저장에 실패했습니다."));
    } finally {
      setSaving(false);
    }
  };

  // ── 수동 액션 아이템 ─────────────────────────────────────────────────
  const handleAddAction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!actionTitle.trim() || !canWrite || extracting || saving) return;
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
      showToast("회의에서 정한 할 일을 업무에 등록했습니다.");
    } catch (err) {
      setError(apiError(err, "액션 아이템 생성에 실패했습니다."));
    } finally {
      setAddingAction(false);
    }
  };

  // ── 로딩 ─────────────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="min-h-screen bg-bb-bg">
        <Sidebar />
        <main className="md:ml-64 mt-14 md:mt-0 min-h-screen p-4 md:p-8">
        <ProjectNav projectId={projectId} />
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

  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar />

      {/* 토스트 알림 */}
      {toast && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50
                        flex items-center gap-2 px-4 py-2.5 bg-slate-800 border border-bb-border
                        rounded-xl shadow-xl text-sm text-bb-text animate-fade-in">
          <Sparkles size={14} className="text-indigo-400 shrink-0" />
          {toast}
        </div>
      )}

      <main className="md:ml-64 mt-14 md:mt-0 min-h-screen p-4 md:p-8">
        <ProjectNav projectId={projectId} />
        <div className="max-w-5xl mx-auto">
          {/* 뒤로 가기 */}
          <button
            onClick={() => { if (!dirty || window.confirm("저장하지 않은 회의록이 있습니다. 이동할까요?")) router.push(`/projects/${projectId}/meetings`); }}
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
            <div className="flex items-start justify-between gap-4">
              <h1 className="text-2xl font-bold text-bb-text mb-3">{meeting.title}</h1>

            </div>
            <div className="flex flex-wrap items-center gap-4 text-sm text-bb-text2">
              <span className="flex items-center gap-1.5">
                <Calendar size={14} />
                {formatDate(meeting.meetingDate)}
              </span>
              <span className="flex items-center gap-1.5">
                <Users size={14} />
                프로젝트 구성원 회의
              </span>
            </div>
            {meeting.purpose && (
              <p className="mt-3 text-sm text-bb-text2 leading-relaxed border-t border-bb-border/60 pt-3">
                {meeting.purpose}
              </p>
            )}

          </div>

          <AttendancePanel meeting={meeting} myUserId={currentUserId} role={role} onMeetingChanged={setMeeting} />

          <div className="mb-5 flex items-center justify-between gap-3 rounded-lg bg-bb-surface2 p-4 text-sm">
            <span>회의 결정사항을 실제 할 일로 연결하세요.</span>
            <Link href={`/projects/${projectId}/board`} className="text-bb-primary font-medium">업무 보러 가기 →</Link>
          </div>
          {/* 회의록 편집 */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-6 mb-6">
            <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
              <h2 className="text-lg font-semibold text-bb-text">회의록</h2>
              {canWrite && !editNotes.trim() && <button type="button" onClick={() => setEditNotes("## 1. 회의 개요\n\n- 일시 / 참석자:\n\n## 2. 논의 안건\n\n### 안건 A: 최종 제출물 일정 조율\n\n- 논의 내용:\n\n## 3. 결정사항 및 다음 할 일\n\n- [ ] ")} className="text-xs px-3 py-2 border border-bb-border rounded-lg">회의록 양식 넣기</button>}
            </div>
            <p role="status" className="mb-3 text-xs text-bb-text2">{!canWrite ? "읽기 전용 · 작성은 팀장과 팀원에게 허용됩니다." : saving ? "저장 중…" : dirty ? "저장하지 않은 변경사항이 있습니다." : "저장된 회의록입니다."}</p>
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-bb-text2 mb-1.5">내용</label>
                <MinutesEditor label="회의 내용" value={editNotes} onChange={setEditNotes} readOnly={!canWrite || saving || extracting} />
              </div>
              <div className="flex justify-end pt-1">
                {/* 저장 버튼 */}
                <button
                  onClick={handleSave}
                  disabled={saving || !canWrite || extracting}
                  className={`flex items-center gap-2 whitespace-nowrap px-4 py-2.5 text-sm font-medium rounded-lg transition-all ${
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

          <MeetingOutcomes projectId={projectId} meetingId={meetingId}
            notes={editNotes} decisions={editDecisions} canWrite={canWrite}
            busy={saving || extracting || addingAction} onBusy={setExtracting}
            members={members.filter(m => m.role === "LEADER" || m.role === "MEMBER")}
            saveNotes={() => saveNow()}
            saveDecisions={async (text) => { await saveNow(text); }} />

          {/* 액션 아이템 */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-6">
            <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
              <h2 className="text-lg font-semibold text-bb-text flex items-center gap-2">
                <ClipboardList size={16} className="text-bb-accent" />
                직접 업무 등록
              </h2>
              <div className="flex items-center gap-2">
                {/* 수동 추가 버튼 */}
                <button
                  onClick={() => setShowActionForm((v) => !v)}
                  disabled={!canWrite || extracting || saving || addingAction}
                  className="flex items-center gap-1.5 whitespace-nowrap text-xs text-bb-text2 hover:text-bb-text
                             px-3 py-1.5 rounded-lg border border-bb-border hover:border-bb-border
                             bg-bb-surface2/50 hover:bg-bb-surface2 transition-all"
                >
                  <Plus size={12} />
                  직접 추가
                </button>
              </div>
            </div>

            <p className="text-xs text-bb-text2 mb-4">
              AI 제안에 없는 할 일은 직접 등록할 수 있습니다.
            </p>

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
                      {members.filter(m => m.role === "LEADER" || m.role === "MEMBER").map((a) => (
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
                    disabled={addingAction || extracting || saving || !canWrite}
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
