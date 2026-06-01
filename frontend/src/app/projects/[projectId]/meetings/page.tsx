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
  Sparkles,
  CheckCircle2,
  Clock,
  Users,
  ChevronLeft,
  CalendarClock,
  TriangleAlert,
} from "lucide-react";
import { CalendarRecommendation, MemberCalendarStatus } from "@/types/calendar";
import SmartDateInput from "@/components/SmartDateInput";

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

// ── 상수 ──────────────────────────────────────────────────────────────
const DURATIONS = [
  { label: "30분", value: 30 },
  { label: "1시간", value: 60 },
  { label: "1.5시간", value: 90 },
  { label: "2시간", value: 120 },
  { label: "2시간 이상", value: -1 },
];

interface ProjectMember { userId: string; memberId: string; name: string; email: string; role: string; }

// ISO → datetime-local 변환
function toLocalInput(iso: string) {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

// ISO → 한국어 날짜 표시
function fmtRec(iso: string) {
  return new Date(iso).toLocaleDateString("ko-KR", {
    month: "short", day: "numeric", weekday: "short", hour: "2-digit", minute: "2-digit",
  });
}

// ── 단계 표시 바 ───────────────────────────────────────────────────────
function StepBar({ step }: { step: 1 | 2 | 3 }) {
  const steps = ["기본 정보", "일정 선택", "확인 및 생성"];
  return (
    <div className="flex items-center gap-0 px-6 py-3 border-b border-bb-border">
      {steps.map((label, i) => {
        const n = i + 1;
        const active  = n === step;
        const done    = n < step;
        return (
          <div key={n} className="flex items-center">
            <div className="flex items-center gap-1.5">
              <div className={`w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold transition-colors ${
                done    ? "bg-indigo-600 text-white" :
                active  ? "bg-indigo-500 text-white" :
                          "bg-slate-700 text-slate-500"
              }`}>
                {done ? <CheckCircle2 size={11} /> : n}
              </div>
              <span className={`text-[11px] font-medium ${active ? "text-slate-200" : "text-slate-500"}`}>
                {label}
              </span>
            </div>
            {i < 2 && <div className="w-8 h-px bg-slate-700 mx-2" />}
          </div>
        );
      })}
    </div>
  );
}

// ── 회의 생성 모달 (3단계) ─────────────────────────────────────────────
interface CreateModalProps {
  onClose: () => void;
  onCreated: (m: Meeting) => void;
  projectId: string;
}

function CreateMeetingModal({ onClose, onCreated, projectId }: CreateModalProps) {
  const [step, setStep]   = useState<1 | 2 | 3>(1);
  const [error, setError] = useState("");

  // ── 1단계 상태 ──────────────────────────────────────────────────────
  const [title, setTitle]                     = useState("");
  const [purpose, setPurpose]                 = useState("");
  const [members, setMembers]                 = useState<ProjectMember[]>([]);
  const [selectedIds, setSelectedIds]         = useState<Set<string>>(new Set());
  const [durationMin, setDurationMin]         = useState(60);
  const [loadingMembers, setLoadingMembers]   = useState(true);

  const [customDurationMin, setCustomDurationMin] = useState("150");

  // ── 2단계 상태 ──────────────────────────────────────────────────────
  const [dateMode, setDateMode]               = useState<"this_week" | "next_week" | "custom">("this_week");
  const [meetingDate, setMeetingDate]         = useState("");
  const [recommending, setRecommending]       = useState(false);
  const [recommendations, setRecommendations] = useState<CalendarRecommendation[]>([]);
  const [selectedRank, setSelectedRank]       = useState<number | null>(null);

  // ── 3단계 상태 ──────────────────────────────────────────────────────
  const [calStatus, setCalStatus]             = useState<MemberCalendarStatus[]>([]);
  const [submitting, setSubmitting]           = useState(false);

  // 멤버 목록 로드 (전원 미선택으로 시작)
  useEffect(() => {
    api.get<ProjectMember[]>(`/projects/${projectId}/members`)
      .then(({ data }) => {
        setMembers(data);
        setSelectedIds(new Set());
      })
      .catch(() => {})
      .finally(() => setLoadingMembers(false));
  }, [projectId]);

  // ── 1단계 → 2단계 ─────────────────────────────────────────────────
  const goStep2 = () => {
    if (!title.trim()) { setError("회의 제목을 입력해주세요"); return; }
    setError("");
    setStep(2);
  };

  // ── 2단계 AI 추천 ─────────────────────────────────────────────────
  const handleRecommend = async () => {
    setRecommending(true);
    setRecommendations([]);
    setSelectedRank(null);
    try {
      const { data } = await api.post<{ recommendations: CalendarRecommendation[] }>(
        `/calendar/recommend`,
        { projectId, targetDate: dateMode, attendeeIds: [...selectedIds] }
      );
      setRecommendations(data.recommendations ?? []);
    } catch {
      setError("AI 일정 추천을 가져오지 못했습니다.");
    } finally {
      setRecommending(false);
    }
  };

  const selectRec = (rec: CalendarRecommendation) => {
    setMeetingDate(toLocalInput(rec.time));
    setSelectedRank(rec.rank);
  };

  // ── 2단계 → 3단계 ─────────────────────────────────────────────────
  const goStep3 = async () => {
    if (!meetingDate) { setError("회의 날짜를 선택해주세요"); return; }
    setError("");
    // 캘린더 연동 상태 조회
    try {
      const { data } = await api.get<{ members: MemberCalendarStatus[] }>(
        `/projects/${projectId}/calendar/status`
      );
      setCalStatus(data.members ?? []);
    } catch {
      setCalStatus([]);
    }
    setStep(3);
  };

  // ── 최종 생성 ─────────────────────────────────────────────────────
  const handleSubmit = async () => {
    if (!meetingDate) { setError("회의 날짜를 선택해주세요"); return; }
    setSubmitting(true);
    setError("");
    try {
      // 1) 회의 생성
      const payload: CreateMeetingPayload = {
        title: title.trim(),
        meetingDate: new Date(meetingDate).toISOString(),
        purpose: purpose.trim() || undefined,
      };
      const { data: meeting } = await api.post<Meeting>(`/projects/${projectId}/meetings`, payload);

      // 2) 연동된 참석자 캘린더에 이벤트 등록
      const actualDurationMin = durationMin === -1 ? (parseInt(customDurationMin) || 150) : durationMin;
      const endIso = new Date(
        new Date(meetingDate).getTime() + actualDurationMin * 60000
      ).toISOString();
      await api.post(`/projects/${projectId}/calendar/events`, {
        title: title.trim(),
        startTime: new Date(meetingDate).toISOString(),
        endTime: endIso,
        attendeeIds: [...selectedIds],
        description: purpose.trim() || undefined,
      }).catch(() => {}); // 캘린더 등록 실패해도 회의는 생성됨

      onCreated(meeting);
    } catch (err: unknown) {
      const data = (err as { response?: { data?: { detail?: string; message?: string } } })
        ?.response?.data;
      setError(data?.detail ?? data?.message ?? "회의 생성에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const selectedMembers = members.filter((m) => selectedIds.has(m.userId));
  const unconnected     = selectedMembers.filter(
    (m) => calStatus.find((c) => c.userId === m.userId && !c.connected)
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" onClick={onClose} />

      <div className="relative w-full max-w-lg bg-bb-surface border border-bb-border rounded-2xl shadow-2xl flex flex-col max-h-[90vh]">

        {/* 헤더 */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-bb-border shrink-0">
          <h2 className="text-base font-semibold text-bb-text">새 회의 만들기</h2>
          <button onClick={onClose}
            className="text-bb-text2 hover:text-bb-text p-1.5 rounded-lg hover:bg-slate-700 transition-all">
            <X size={16} />
          </button>
        </div>

        {/* 단계 표시 */}
        <StepBar step={step} />

        {/* 에러 */}
        {error && (
          <div className="mx-6 mt-4 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400 shrink-0">
            {error}
          </div>
        )}

        {/* ── 1단계: 기본 정보 ─────────────────────────────────────── */}
        {step === 1 && (
          <div className="overflow-y-auto flex-1 px-6 py-5 space-y-5">
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
                autoFocus
              />
            </div>

            {/* 참석자 */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="text-xs font-medium text-bb-text2 flex items-center gap-1.5">
                  <Users size={12} /> 참석자
                </label>
                <span className="text-[11px] text-slate-500">{selectedIds.size}명 선택</span>
              </div>
              {loadingMembers ? (
                <div className="flex items-center gap-2 text-slate-500 text-xs py-3">
                  <Loader2 size={12} className="animate-spin" /> 멤버 불러오는 중...
                </div>
              ) : (
                <div className="space-y-1.5 max-h-44 overflow-y-auto pr-1">
                  {members.map((m) => {
                    const checked = selectedIds.has(m.userId);
                    return (
                      <button
                        key={m.userId}
                        type="button"
                        onClick={() => {
                          const next = new Set(selectedIds);
                          checked ? next.delete(m.userId) : next.add(m.userId);
                          setSelectedIds(next);
                        }}
                        className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg border text-left transition-all ${
                          checked
                            ? "border-indigo-500/50 bg-indigo-500/8"
                            : "border-slate-700 hover:border-slate-600 bg-slate-800/40"
                        }`}
                      >
                        <div className={`w-4 h-4 rounded border flex items-center justify-center shrink-0 transition-colors ${
                          checked ? "border-indigo-500 bg-indigo-500" : "border-slate-600"
                        }`}>
                          {checked && <CheckCircle2 size={10} className="text-white" />}
                        </div>
                        <div className="w-7 h-7 rounded-full bg-slate-700 flex items-center justify-center text-xs text-slate-200 font-medium shrink-0">
                          {m.name[0]}
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="text-sm text-slate-200">{m.name}</div>
                          <div className="text-[11px] text-slate-500 truncate">{m.email}</div>
                        </div>
                        <span className={`text-[10px] px-1.5 py-0.5 rounded-full shrink-0 ${
                          m.role === "LEADER"
                            ? "bg-indigo-500/15 text-indigo-400"
                            : "bg-slate-700 text-slate-500"
                        }`}>
                          {m.role === "LEADER" ? "팀장" : "팀원"}
                        </span>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            {/* 예상 소요 시간 */}
            <div>
              <label className="text-xs font-medium text-bb-text2 mb-2 flex items-center gap-1.5">
                <Clock size={12} /> 예상 소요 시간
              </label>
              <div className="grid grid-cols-5 gap-2">
                {DURATIONS.map((d) => (
                  <button
                    key={d.value}
                    type="button"
                    onClick={() => setDurationMin(d.value)}
                    className={`py-2 rounded-lg text-xs font-medium border transition-all ${
                      durationMin === d.value
                        ? "border-indigo-500 bg-indigo-500/15 text-indigo-300"
                        : "border-slate-700 bg-slate-800/50 text-slate-400 hover:border-slate-600"
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
                    value={customDurationMin}
                    onChange={(e) => setCustomDurationMin(e.target.value)}
                    placeholder="분 단위 입력"
                    className={`${INPUT_CLS} w-40`}
                  />
                  <span className="text-xs text-bb-text2">분</span>
                </div>
              )}
            </div>

            {/* 안건 (선택) */}
            <div>
              <label className="block text-xs font-medium text-bb-text2 mb-1.5">안건 (선택)</label>
              <textarea
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
                placeholder="회의 목적 또는 주요 안건"
                rows={2}
                className={`${INPUT_CLS} resize-none`}
              />
            </div>
          </div>
        )}

        {/* ── 2단계: 일정 선택 ─────────────────────────────────────── */}
        {step === 2 && (
          <div className="overflow-y-auto flex-1 px-6 py-5 space-y-5">
            {/* 참석자 아바타 */}
            <div>
              <p className="text-xs text-slate-500 mb-2">선택된 참석자</p>
              <div className="flex flex-wrap gap-2">
                {selectedMembers.map((m) => (
                  <div key={m.userId} className="flex items-center gap-1.5 bg-slate-800 border border-slate-700 rounded-full px-2.5 py-1">
                    <div className="w-5 h-5 rounded-full bg-indigo-500/30 flex items-center justify-center text-[10px] text-indigo-300 font-bold">
                      {m.name[0]}
                    </div>
                    <span className="text-xs text-slate-300">{m.name}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* 날짜 범위 */}
            <div>
              <label className="text-xs font-medium text-bb-text2 mb-2 flex items-center gap-1.5">
                <CalendarClock size={12} /> 날짜 범위
              </label>
              <div className="grid grid-cols-3 gap-2">
                {[
                  { label: "이번 주", value: "this_week" },
                  { label: "다음 주", value: "next_week" },
                  { label: "직접 선택", value: "custom" },
                ].map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => setDateMode(opt.value as typeof dateMode)}
                    className={`py-2 rounded-lg text-xs font-medium border transition-all ${
                      dateMode === opt.value
                        ? "border-indigo-500 bg-indigo-500/15 text-indigo-300"
                        : "border-slate-700 bg-slate-800/50 text-slate-400 hover:border-slate-600"
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            {/* AI 추천 버튼 (직접 선택 모드 아닐 때) */}
            {dateMode !== "custom" && (
              <div>
                <button
                  type="button"
                  onClick={handleRecommend}
                  disabled={recommending}
                  className="w-full flex items-center justify-center gap-2 py-2.5
                             bg-indigo-600/20 hover:bg-indigo-600/30 border border-indigo-500/40
                             text-indigo-300 text-sm font-medium rounded-lg transition-all disabled:opacity-50"
                >
                  {recommending ? <Loader2 size={14} className="animate-spin" /> : <Sparkles size={14} />}
                  {recommending ? "AI 분석 중..." : "AI 추천받기"}
                </button>

                {/* 추천 카드 */}
                {recommendations.length > 0 && (
                  <div className="mt-3 space-y-2">
                    <p className="text-[11px] text-slate-500 flex items-center gap-1">
                      <Sparkles size={10} className="text-indigo-400" />
                      카드를 클릭하면 날짜가 자동 입력됩니다
                    </p>
                    {recommendations.map((rec) => (
                      <button
                        key={rec.rank}
                        type="button"
                        onClick={() => selectRec(rec)}
                        className={`w-full text-left rounded-lg px-3 py-3 border transition-all ${
                          selectedRank === rec.rank
                            ? "border-indigo-500 bg-indigo-500/12"
                            : rec.rank === 1
                            ? "border-indigo-500/40 bg-indigo-500/6 hover:bg-indigo-500/12"
                            : "border-slate-700 bg-slate-800/40 hover:bg-slate-800/70"
                        }`}
                      >
                        {/* 헤더: 순위 + 시간 + 점수 배지 */}
                        <div className="flex items-start justify-between gap-2 mb-1.5">
                          <div className="flex items-center gap-2.5">
                            {selectedRank === rec.rank ? (
                              <CheckCircle2 size={15} className="text-indigo-400 shrink-0" />
                            ) : (
                              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded shrink-0 ${
                                rec.rank === 1 ? "bg-indigo-500/20 text-indigo-300" : "bg-slate-700 text-slate-300"
                              }`}>{rec.rank}위</span>
                            )}
                            <div>
                              <div className="text-xs font-semibold text-slate-200">{fmtRec(rec.time)}</div>
                              <div className="text-[11px] text-slate-300 mt-0.5 leading-relaxed">{rec.reason}</div>
                            </div>
                          </div>
                          <div className="flex items-center gap-1.5 shrink-0 mt-0.5">
                            <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full border ${
                              (rec.score ?? 100) >= 90
                                ? "bg-green-500/15 text-green-400 border-green-500/30"
                                : (rec.score ?? 100) >= 60
                                ? "bg-amber-500/15 text-amber-400 border-amber-500/30"
                                : "bg-red-500/15 text-red-400 border-red-500/30"
                            }`}>
                              추천도 {rec.score ?? 100}
                            </span>
                            <span className="text-[10px] text-slate-500">{rec.durationMinutes}분</span>
                          </div>
                        </div>
                        {/* needsConfirm 경고 / 전원 가능 */}
                        {rec.needsConfirm ? (
                          <div className="flex items-center gap-1.5 mt-1 bg-amber-500/10 border border-amber-500/20 rounded px-2 py-1">
                            <span className="text-[10px] text-amber-400">⚠️ {rec.softBlockMembers?.join(", ")}님 종일 일정 있음 — 직접 확인 필요</span>
                          </div>
                        ) : (
                          <div className="flex items-center gap-1 mt-1">
                            <CheckCircle2 size={10} className="text-green-400" />
                            <span className="text-[10px] text-green-400">전원 가능</span>
                          </div>
                        )}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 직접 날짜 입력 */}
            <div>
              <label className="block text-xs font-medium text-bb-text2 mb-1.5">
                {dateMode === "custom" ? "날짜 및 시간 선택" : "또는 직접 입력"}
              </label>
              <SmartDateInput
                withTime
                value={meetingDate}
                onChange={(v) => { setMeetingDate(v); setSelectedRank(null); }}
                className="w-full"
              />
            </div>
          </div>
        )}

        {/* ── 3단계: 확인 및 생성 ──────────────────────────────────── */}
        {step === 3 && (
          <div className="overflow-y-auto flex-1 px-6 py-5 space-y-4">
            {/* 회의 정보 요약 */}
            <div className="bg-slate-800/60 border border-slate-700 rounded-xl p-4 space-y-3">
              <SummaryRow icon={<FileText size={13} className="text-slate-400" />} label="제목" value={title} />
              <SummaryRow
                icon={<Calendar size={13} className="text-slate-400" />}
                label="일시"
                value={meetingDate ? new Date(meetingDate).toLocaleString("ko-KR", {
                  year: "numeric", month: "short", day: "numeric",
                  weekday: "short", hour: "2-digit", minute: "2-digit"
                }) : "미정"}
              />
              <SummaryRow
                icon={<Clock size={13} className="text-slate-400" />}
                label="소요 시간"
                value={durationMin === -1 ? `${customDurationMin}분` : (DURATIONS.find((d) => d.value === durationMin)?.label ?? `${durationMin}분`)}
              />
              <div className="flex items-start gap-2 pt-0.5">
                <Users size={13} className="text-slate-400 mt-0.5 shrink-0" />
                <span className="text-xs text-slate-500 w-16 shrink-0">참석자</span>
                <div className="flex flex-wrap gap-1.5">
                  {selectedMembers.map((m) => (
                    <span key={m.userId}
                      className="text-[11px] bg-slate-700 text-slate-300 px-2 py-0.5 rounded-full">
                      {m.name}
                    </span>
                  ))}
                </div>
              </div>
            </div>

            {/* 미연동 참석자 경고 */}
            {unconnected.length > 0 && (
              <div className="flex items-start gap-2.5 bg-yellow-500/8 border border-yellow-500/25 rounded-xl px-4 py-3">
                <TriangleAlert size={14} className="text-yellow-400 shrink-0 mt-0.5" />
                <div>
                  <p className="text-xs font-medium text-yellow-400 mb-1">캘린더 미연동 참석자</p>
                  <p className="text-[11px] text-yellow-400/70 leading-relaxed">
                    {unconnected.map((m) => m.name).join(", ")}님은 캘린더가 연동되어 있지 않아
                    Google Calendar 자동 등록이 되지 않습니다. 앱 내 알림만 전송됩니다.
                  </p>
                </div>
              </div>
            )}

            {/* 연동된 참석자 안내 */}
            {unconnected.length === 0 && calStatus.length > 0 && (
              <div className="flex items-center gap-2 bg-green-500/8 border border-green-500/25 rounded-xl px-4 py-3">
                <CheckCircle2 size={14} className="text-green-400 shrink-0" />
                <p className="text-[11px] text-green-400">
                  모든 참석자가 캘린더와 연동되어 있습니다. 회의 생성 시 자동 등록됩니다.
                </p>
              </div>
            )}
          </div>
        )}

        {/* ── 하단 버튼 ─────────────────────────────────────────────── */}
        <div className="px-6 py-4 border-t border-bb-border flex justify-between items-center shrink-0">
          <button
            type="button"
            onClick={step === 1 ? onClose : () => setStep((s) => (s - 1) as 1 | 2 | 3)}
            className="flex items-center gap-1.5 text-sm text-slate-400 hover:text-slate-200 transition-colors"
          >
            {step > 1 && <ChevronLeft size={15} />}
            {step === 1 ? "취소" : "이전"}
          </button>

          {step < 3 ? (
            <button
              type="button"
              onClick={step === 1 ? goStep2 : goStep3}
              className="flex items-center gap-2 px-5 py-2 bg-indigo-600 hover:bg-indigo-700
                         text-white text-sm font-medium rounded-lg transition-colors"
            >
              다음
              <ChevronRight size={14} />
            </button>
          ) : (
            <button
              type="button"
              onClick={handleSubmit}
              disabled={submitting}
              className="flex items-center gap-2 px-5 py-2 bg-indigo-600 hover:bg-indigo-700
                         disabled:opacity-50 text-white text-sm font-medium rounded-lg transition-colors"
            >
              {submitting ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />}
              {submitting ? "생성 중..." : "회의 만들기"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function SummaryRow({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center gap-2">
      <span className="shrink-0">{icon}</span>
      <span className="text-xs text-slate-500 w-16 shrink-0">{label}</span>
      <span className="text-xs text-slate-200 font-medium">{value}</span>
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

      {/* 상태 + Notion 배지 + 화살표 */}
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
        {/* Notion 동기화 배지 */}
        {meeting.notionPageId ? (
          <span className="flex items-center gap-1 text-[10px] font-medium px-2 py-0.5 rounded-full bg-slate-800 border border-slate-600 text-slate-400">
            <span className="text-[8px] font-bold bg-white text-[#191919] px-0.5 rounded leading-tight">N</span>
            동기화됨
          </span>
        ) : (
          <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-slate-800/50 text-slate-600 border border-slate-700/50">
            미동기화
          </span>
        )}
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
