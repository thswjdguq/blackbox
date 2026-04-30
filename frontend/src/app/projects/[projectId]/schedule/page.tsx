"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import { CalendarRecommendation, MemberCalendarStatus } from "@/types/calendar";
import { Meeting, CreateMeetingPayload } from "@/types/meeting";
import {
  CalendarClock,
  CheckCircle2,
  XCircle,
  Sparkles,
  Loader2,
  Clock,
  Users,
  ArrowRight,
  UserCircle,
  X,
} from "lucide-react";

const DURATIONS = [
  { label: "30분",    value: 30 },
  { label: "1시간",   value: 60 },
  { label: "1.5시간", value: 90 },
  { label: "2시간",   value: 120 },
  { label: "2시간 이상", value: -1 },
];

const GOOGLE_BLUE = "#4285F4";

function fmtRec(iso: string) {
  return new Date(iso).toLocaleDateString("ko-KR", {
    month: "short", day: "numeric", weekday: "short",
    hour: "2-digit", minute: "2-digit",
  });
}

// ── 회의 확정 모달 ──────────────────────────────────────────────────────
interface ConfirmModalProps {
  rec: CalendarRecommendation;
  projectId: string;
  members: MemberCalendarStatus[];
  durationMin: number;
  customDurationMin: string;
  onClose: () => void;
  onCreated: () => void;
}

function ConfirmMeetingModal({ rec, projectId, members, durationMin, customDurationMin, onClose, onCreated }: ConfirmModalProps) {
  const [title,      setTitle]      = useState(`팀 회의 — ${fmtRec(rec.time)}`);
  const [submitting, setSubmitting] = useState(false);
  const [error,      setError]      = useState("");
  const actualDuration = durationMin === -1 ? (parseInt(customDurationMin) || 150) : durationMin;

  const handleCreate = async () => {
    if (!title.trim()) { setError("회의 제목을 입력하세요"); return; }
    setSubmitting(true);
    setError("");
    try {
      const payload: CreateMeetingPayload = {
        title: title.trim(),
        meetingDate: rec.time,
      };
      await api.post<Meeting>(`/projects/${projectId}/meetings`, payload);

      // 연동된 팀원 캘린더에 이벤트 등록
      const connectedIds = members.filter((m) => m.connected).map((m) => m.userId);
      if (connectedIds.length > 0) {
        const endTime = new Date(new Date(rec.time).getTime() + actualDuration * 60000).toISOString();
        await api.post(`/projects/${projectId}/calendar/events`, {
          title: title.trim(),
          startTime: rec.time,
          endTime,
          attendeeIds: connectedIds,
        }).catch(() => {});
      }
      onCreated();
    } catch {
      setError("회의 생성에 실패했습니다. 리더 권한이 필요합니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-md bg-bb-surface border border-bb-border rounded-2xl shadow-2xl p-6">
        <button onClick={onClose} className="absolute top-4 right-4 text-bb-text2 hover:text-bb-text">
          <X size={16} />
        </button>

        <h2 className="text-base font-semibold text-bb-text mb-5">회의 확정</h2>

        {/* 선택된 시간 요약 */}
        <div className="bg-indigo-500/10 border border-indigo-500/20 rounded-xl px-4 py-3 mb-4">
          <p className="text-xs text-indigo-400 font-medium mb-0.5">선택된 일정</p>
          <p className="text-sm font-semibold text-bb-text">{fmtRec(rec.time)}</p>
          <p className="text-xs text-bb-text2 mt-0.5">소요 {actualDuration}분 · {rec.reason}</p>
        </div>

        {/* 회의 제목 */}
        <div className="mb-4">
          <label className="block text-xs font-medium text-bb-text2 mb-1.5">회의 제목</label>
          <input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2.5 text-sm
                       text-bb-text focus:outline-none focus:border-indigo-500 transition-all"
            autoFocus
          />
        </div>

        {/* 연동 팀원 캘린더 자동 등록 안내 */}
        {members.filter((m) => m.connected).length > 0 && (
          <div className="flex items-center gap-2 bg-green-500/10 border border-green-500/20 rounded-lg px-3 py-2.5 mb-4">
            <CheckCircle2 size={13} className="text-green-400 shrink-0" />
            <p className="text-xs text-green-400">
              연동된 {members.filter((m) => m.connected).length}명의 구글 캘린더에 자동 등록됩니다
            </p>
          </div>
        )}

        {error && <p className="text-xs text-red-400 mb-3">{error}</p>}

        <div className="flex gap-2">
          <button onClick={onClose} className="flex-1 py-2.5 border border-bb-border rounded-lg text-sm text-bb-text2 hover:bg-bb-surface2 transition-all">
            취소
          </button>
          <button
            onClick={handleCreate}
            disabled={submitting}
            className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-indigo-600 hover:bg-indigo-500
                       text-white text-sm font-medium rounded-lg transition-all disabled:opacity-50"
          >
            {submitting ? <Loader2 size={14} className="animate-spin" /> : <CheckCircle2 size={14} />}
            {submitting ? "생성 중..." : "회의 확정"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── 메인 페이지 ─────────────────────────────────────────────────────────
export default function SchedulePage() {
  const params    = useParams();
  const router    = useRouter();
  const projectId = params?.projectId as string;

  const [members,      setMembers]      = useState<MemberCalendarStatus[]>([]);
  const [loadingStatus,setLoadingStatus]= useState(true);

  const [dateMode,     setDateMode]     = useState<"this_week" | "next_week" | "custom">("this_week");
  const [durationMin,  setDurationMin]  = useState(60);
  const [customDur,    setCustomDur]    = useState("150");

  const [recommending, setRecommending] = useState(false);
  const [recs,         setRecs]         = useState<CalendarRecommendation[]>([]);
  const [recError,     setRecError]     = useState("");

  const [confirmRec,   setConfirmRec]   = useState<CalendarRecommendation | null>(null);
  const [successMsg,   setSuccessMsg]   = useState<string | null>(null);

  const fetchStatus = useCallback(async () => {
    setLoadingStatus(true);
    try {
      const { data } = await api.get<{ connected: boolean; members: MemberCalendarStatus[] }>(
        `/projects/${projectId}/schedule/team-status`
      );
      setMembers(data.members ?? []);
    } catch {
      setMembers([]);
    } finally {
      setLoadingStatus(false);
    }
  }, [projectId]);

  useEffect(() => {
    if (!localStorage.getItem("accessToken")) { router.replace("/login"); return; }
    fetchStatus();
  }, [fetchStatus, router]);

  const handleRecommend = async () => {
    setRecommending(true);
    setRecs([]);
    setRecError("");
    try {
      const connectedIds = members.filter((m) => m.connected).map((m) => m.userId);
      const { data } = await api.post<{ recommendations: CalendarRecommendation[] }>(
        `/calendar/recommend`,
        { projectId, targetDate: dateMode, attendeeIds: connectedIds }
      );
      setRecs(data.recommendations ?? []);
      if ((data.recommendations ?? []).length === 0) {
        setRecError("적합한 시간대를 찾지 못했습니다. 날짜 범위를 변경해 보세요.");
      }
    } catch {
      setRecError("AI 추천 중 오류가 발생했습니다.");
    } finally {
      setRecommending(false);
    }
  };

  const connectedCount = members.filter((m) => m.connected).length;
  const totalCount     = members.length;

  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar />

      {/* 회의 확정 모달 */}
      {confirmRec && (
        <ConfirmMeetingModal
          rec={confirmRec}
          projectId={projectId}
          members={members}
          durationMin={durationMin}
          customDurationMin={customDur}
          onClose={() => setConfirmRec(null)}
          onCreated={() => {
            setConfirmRec(null);
            setSuccessMsg("회의가 생성되었습니다! 회의록에서 확인하세요.");
            setTimeout(() => setSuccessMsg(null), 4000);
          }}
        />
      )}

      {/* 성공 토스트 */}
      {successMsg && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50
                        flex items-center gap-2 px-5 py-3 bg-bb-surface border border-bb-border
                        rounded-xl shadow-xl text-sm text-bb-text">
          <CheckCircle2 size={15} className="text-green-400 shrink-0" />
          {successMsg}
        </div>
      )}

      <main className="ml-64 min-h-screen p-8">
        <div className="max-w-2xl">

          {/* 헤더 */}
          <div className="mb-8">
            <h1 className="text-2xl font-bold text-bb-text flex items-center gap-2">
              <CalendarClock size={22} className="text-indigo-400" />
              회의 일정 조율
            </h1>
            <p className="text-sm text-bb-text2 mt-1">
              팀원 캘린더를 분석해 최적의 회의 시간을 추천합니다
            </p>
          </div>

          {/* 팀원 캘린더 연동 현황 */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-5 mb-5">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-sm font-semibold text-bb-text flex items-center gap-2">
                <Users size={14} className="text-bb-text2" />
                팀원 캘린더 연동 현황
              </h2>
              <span className="text-xs text-bb-text2">
                {loadingStatus ? "확인 중..." : `${connectedCount} / ${totalCount}명 연동`}
              </span>
            </div>

            {loadingStatus ? (
              <div className="flex items-center gap-2 text-bb-text2 text-xs py-3">
                <Loader2 size={12} className="animate-spin" /> 불러오는 중...
              </div>
            ) : (
              <>
                <div className="space-y-2">
                  {members.map((m) => (
                    <div key={m.userId} className="flex items-center justify-between bg-bb-surface2 rounded-lg px-3 py-2.5">
                      <div className="flex items-center gap-3">
                        <div className="w-7 h-7 rounded-full bg-bb-border flex items-center justify-center text-xs text-bb-text font-medium">
                          {m.name.slice(0, 1)}
                        </div>
                        <div>
                          <p className="text-sm text-bb-text">{m.name}</p>
                          <p className="text-xs text-bb-text2">{m.email}</p>
                        </div>
                      </div>
                      {m.connected ? (
                        <div className="flex items-center gap-1.5">
                          {/* 구글 아이콘 */}
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
                            <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                            <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                            <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z" fill="#FBBC05"/>
                            <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                          </svg>
                          <span className="text-xs text-green-400 font-medium">연동됨</span>
                        </div>
                      ) : (
                        <span className="text-xs text-bb-text2">미연동</span>
                      )}
                    </div>
                  ))}
                </div>

                {connectedCount < totalCount && (
                  <div className="mt-3 flex items-start gap-2 bg-amber-500/10 border border-amber-500/20 rounded-lg px-3 py-2.5">
                    <XCircle size={13} className="text-amber-400 shrink-0 mt-0.5" />
                    <div>
                      <p className="text-xs text-amber-400">미연동 팀원은 분석에서 제외됩니다</p>
                      <a
                        href="/profile/settings"
                        className="text-xs text-indigo-400 hover:underline flex items-center gap-1 mt-0.5"
                      >
                        <UserCircle size={11} />
                        프로필 설정에서 캘린더 연동하기
                      </a>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>

          {/* 일정 조율 섹션 */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-5 mb-5">
            <h2 className="text-sm font-semibold text-bb-text mb-4">일정 조율 설정</h2>

            {/* 날짜 범위 */}
            <div className="mb-4">
              <label className="text-xs font-medium text-bb-text2 mb-2 block">날짜 범위</label>
              <div className="grid grid-cols-3 gap-2">
                {[
                  { label: "이번 주", value: "this_week" as const },
                  { label: "다음 주", value: "next_week" as const },
                  { label: "직접 선택", value: "custom" as const },
                ].map((opt) => (
                  <button
                    key={opt.value}
                    onClick={() => setDateMode(opt.value)}
                    className={`py-2 rounded-lg text-xs font-medium border transition-all ${
                      dateMode === opt.value
                        ? "border-indigo-500 bg-indigo-500/15 text-indigo-300"
                        : "border-bb-border bg-bb-surface2/50 text-bb-text2 hover:border-bb-text2"
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            {/* 예상 소요 시간 */}
            <div className="mb-5">
              <label className="text-xs font-medium text-bb-text2 mb-2 flex items-center gap-1">
                <Clock size={11} /> 예상 소요 시간
              </label>
              <div className="grid grid-cols-5 gap-2">
                {DURATIONS.map((d) => (
                  <button
                    key={d.value}
                    onClick={() => setDurationMin(d.value)}
                    className={`py-2 rounded-lg text-xs font-medium border transition-all ${
                      durationMin === d.value
                        ? "border-indigo-500 bg-indigo-500/15 text-indigo-300"
                        : "border-bb-border bg-bb-surface2/50 text-bb-text2 hover:border-bb-text2"
                    }`}
                  >
                    {d.label}
                  </button>
                ))}
              </div>
              {durationMin === -1 && (
                <div className="mt-2 flex items-center gap-2">
                  <input
                    type="number"
                    min={121}
                    max={480}
                    value={customDur}
                    onChange={(e) => setCustomDur(e.target.value)}
                    placeholder="분 단위 입력"
                    className="w-40 bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm
                               text-bb-text focus:outline-none focus:border-indigo-500 transition-all"
                  />
                  <span className="text-xs text-bb-text2">분</span>
                </div>
              )}
            </div>

            {/* AI 추천 버튼 */}
            <button
              onClick={handleRecommend}
              disabled={recommending || connectedCount === 0}
              className="w-full flex items-center justify-center gap-2 py-3
                         bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50
                         text-white text-sm font-medium rounded-xl transition-all"
            >
              {recommending
                ? <><Loader2 size={15} className="animate-spin" /> AI 분석 중...</>
                : <><Sparkles size={15} /> AI 일정 추천받기</>}
            </button>

            {connectedCount === 0 && !loadingStatus && (
              <p className="text-xs text-bb-text2 text-center mt-2">
                캘린더를 연동한 팀원이 없으면 추천을 받을 수 없습니다
              </p>
            )}
          </div>

          {/* AI 추천 결과 */}
          {(recs.length > 0 || recError) && (
            <div className="space-y-3">
              {recError && (
                <p className="text-sm text-bb-text2 text-center py-4">{recError}</p>
              )}
              {recs.map((rec) => (
                <div
                  key={rec.rank}
                  className={`relative rounded-xl border p-5 transition-all ${
                    rec.rank === 1
                      ? "border-indigo-500/50 bg-indigo-500/8"
                      : "border-bb-border bg-bb-surface"
                  }`}
                >
                  {rec.rank === 1 && (
                    <span className="absolute top-4 right-4 text-[10px] font-bold px-2 py-0.5
                                     bg-indigo-500/20 text-indigo-300 rounded-full border border-indigo-500/30">
                      추천
                    </span>
                  )}

                  <div className="flex items-start gap-3 mb-3">
                    <span className={`text-[11px] font-bold px-2 py-0.5 rounded shrink-0 ${
                      rec.rank === 1 ? "bg-indigo-500/20 text-indigo-300" : "bg-bb-surface2 text-bb-text2"
                    }`}>
                      {rec.rank}위
                    </span>
                    <div>
                      <p className="text-sm font-semibold text-bb-text">{fmtRec(rec.time)}</p>
                      <p className="text-xs text-bb-text2 mt-0.5">{rec.durationMinutes}분 · {rec.reason}</p>
                    </div>
                  </div>

                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5 text-xs text-bb-text2">
                      <Users size={12} />
                      <span>연동 참여 {connectedCount}명 분석</span>
                    </div>
                    <button
                      onClick={() => setConfirmRec(rec)}
                      className="flex items-center gap-1.5 px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500
                                 text-white text-xs font-medium rounded-lg transition-all"
                    >
                      이 시간으로 회의 잡기
                      <ArrowRight size={12} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}

        </div>
      </main>
    </div>
  );
}
