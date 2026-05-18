"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import { ScoreEntry, Alert, AlertType } from "@/types/vault";
import {
  RadialBarChart, RadialBar, ResponsiveContainer,
} from "recharts";
import {
  RefreshCw, AlertTriangle, Users, Loader2,
  AlertCircle, CheckSquare, Video, Files,
  Download, Zap, TrendingDown, TrendingUp,
  ListChecks,
} from "lucide-react";

// ── 타입 ──────────────────────────────────────────────────────────────────
interface TaskSummary {
  id: string;
  title: string;
  status: "TODO" | "IN_PROGRESS" | "DONE";
  dueDate: string | null;
  assignees: { userId: string; name: string }[];
}

interface RiskData {
  riskScore: number;
  level: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  totalTasks: number;
  doneTasks: number;
  overdueTasks: number;
  completionRate: number;
  daysRemaining: number | null;
  reasons: string[];
}

// ── 상수 ──────────────────────────────────────────────────────────────────
const LEVEL_CONFIG = {
  FULL:    { label: "전체 참여", cls: "bg-teal-500/15 text-teal-400 border-teal-500/30", dot: "bg-teal-400" },
  PARTIAL: { label: "부분 참여", cls: "bg-yellow-500/15 text-yellow-400 border-yellow-500/30", dot: "bg-yellow-400" },
  NONE:    { label: "미참여",    cls: "bg-red-500/15 text-red-400 border-red-500/30",    dot: "bg-red-400" },
} as const;

const ALERT_CONFIG: Record<AlertType, { label: string; color: string; bg: string }> = {
  FREE_RIDE: { label: "무임승차 의심",  color: "text-yellow-500", bg: "bg-yellow-50 dark:bg-yellow-400/10 border-yellow-200 dark:border-yellow-400/20" },
  OVERLOAD:  { label: "과부하 위험",    color: "text-orange-500", bg: "bg-orange-50 dark:bg-orange-400/10 border-orange-200 dark:border-orange-400/20" },
  DROPOUT:   { label: "이탈 감지",      color: "text-red-500",    bg: "bg-red-50 dark:bg-red-400/10 border-red-200 dark:border-red-400/20" },
  TAMPER:    { label: "파일 변조 의심", color: "text-red-500",    bg: "bg-red-50 dark:bg-red-500/10 border-red-200 dark:border-red-500/20" },
};

// ── 참여 뱃지 ─────────────────────────────────────────────────────────────
function LevelBadge({ level }: { level: ScoreEntry["participationLevel"] }) {
  const cfg = LEVEL_CONFIG[level];
  return (
    <span className={`inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full border ${cfg.cls}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${cfg.dot}`} />
      {cfg.label}
    </span>
  );
}

// ── 참여 셀 ──────────────────────────────────────────────────────────────
function ParticipationCell({ participated }: { participated: boolean }) {
  return participated ? (
    <span className="text-teal-400 font-bold text-base">✓</span>
  ) : (
    <span className="text-red-400/60 text-base">✗</span>
  );
}

// ── KPI 카드 ────────────────────────────────────────────────────────────
function KpiCard({
  label, value, sub, icon: Icon, accent,
}: {
  label: string; value: string | number; sub?: string;
  icon: React.ElementType; accent?: string;
}) {
  return (
    <div className="bg-white dark:bg-bb-surface border border-bb-border rounded-xl overflow-hidden shadow-sm">
      <div className="h-1 w-full" style={{ background: accent ?? "#6366F1" }} />
      <div className="p-5">
        <div className="flex items-center gap-2 mb-3">
          <Icon size={14} className="text-bb-text2" />
          <span className="text-xs text-bb-text2 font-medium">{label}</span>
        </div>
        <p className="text-3xl font-bold text-bb-text leading-none">{value}</p>
        {sub && <p className="text-xs text-bb-text2 mt-1.5">{sub}</p>}
      </div>
    </div>
  );
}

// ── 섹션 헤더 ──────────────────────────────────────────────────────────
function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <h2 className="text-sm font-semibold text-bb-text2 uppercase tracking-wider mb-4 flex items-center gap-2">
      {children}
    </h2>
  );
}

// ── 메인 페이지 ────────────────────────────────────────────────────────
export default function AnalyticsPage() {
  const params    = useParams();
  const router    = useRouter();
  const projectId = params?.projectId as string;

  const [scores,        setScores]        = useState<ScoreEntry[]>([]);
  const [alerts,        setAlerts]        = useState<Alert[]>([]);
  const [risk,          setRisk]          = useState<RiskData | null>(null);
  const [tasks,         setTasks]         = useState<TaskSummary[]>([]);
  const [loading,       setLoading]       = useState(true);
  const [recalculating, setRecalculating] = useState(false);
  const [error,         setError]         = useState("");
  const [downloading,   setDownloading]   = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [scoreRes, alertRes, riskRes, taskRes] = await Promise.all([
        api.get<ScoreEntry[]>(`/projects/${projectId}/scores`),
        api.get<Alert[]>(`/projects/${projectId}/alerts`),
        api.get<RiskData>(`/projects/${projectId}/risk`),
        api.get<TaskSummary[]>(`/projects/${projectId}/tasks`),
      ]);
      setScores(scoreRes.data);
      setAlerts(alertRes.data);
      setRisk(riskRes.data);
      setTasks(taskRes.data);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401) { router.replace("/login"); return; }
      setError("데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [projectId, router]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleDownloadReport = async () => {
    setDownloading(true);
    try {
      const res = await api.get(`/projects/${projectId}/report`, { responseType: "blob" });
      const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }));
      const a = document.createElement("a");
      a.href = url;
      a.download = `blackbox-report-${new Date().toISOString().slice(0, 10)}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError("리포트 생성에 실패했습니다.");
    } finally {
      setDownloading(false);
    }
  };

  const handleRecalculate = async () => {
    setRecalculating(true);
    try {
      const { data } = await api.post<ScoreEntry[]>(`/projects/${projectId}/scores/recalculate`);
      setScores(data);
    } catch {
      setError("재계산에 실패했습니다.");
    } finally {
      setRecalculating(false);
    }
  };

  // ── 파생 데이터 ──────────────────────────────────────────────────────
  const fullCount    = scores.filter((s) => s.participationLevel === "FULL").length;
  const partialCount = scores.filter((s) => s.participationLevel === "PARTIAL").length;
  const noneCount    = scores.filter((s) => s.participationLevel === "NONE").length;
  const unreadAlerts = alerts.filter((a) => !a.isResolved);

  // 위험도 게이지 데이터
  const riskGaugeData = risk ? [{ name: "위험도", value: risk.riskScore, fill: getRiskColor(risk.level) }] : [];

  // ── 로딩 스켈레톤 ─────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="min-h-screen bg-bb-bg">
        <Sidebar />
        <main className="ml-64 min-h-screen p-8">
          <div className="animate-pulse space-y-6">
            <div className="h-8 bg-bb-surface2 rounded w-48" />
            <div className="grid grid-cols-4 gap-4">
              {[...Array(4)].map((_, i) => <div key={i} className="h-28 bg-bb-surface rounded-xl" />)}
            </div>
            <div className="h-72 bg-bb-surface rounded-xl" />
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar />

      <main className="ml-64 min-h-screen p-8">
        {/* ── 헤더 ──────────────────────────────────────────────────── */}
        <div className="flex items-start justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold text-bb-text">기여도 분석</h1>
            <p className="text-sm text-bb-text2 mt-1">
              팀원별 역할 수행 여부 (참여 / 미참여) 기반 분석
            </p>
          </div>
          <div className="flex gap-2">
            <button
              onClick={handleDownloadReport}
              disabled={downloading}
              className="flex items-center gap-2 px-4 py-2 border border-bb-border bg-bb-surface
                         hover:bg-bb-bg text-sm rounded-lg transition-all disabled:opacity-50 text-bb-text2 font-medium"
            >
              {downloading ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />}
              PDF 리포트
            </button>
            <button
              onClick={handleRecalculate}
              disabled={recalculating}
              className="flex items-center gap-2 px-4 py-2 bg-bb-primary hover:bg-bb-primary-h text-white
                         text-sm rounded-lg transition-all disabled:opacity-50 font-medium"
            >
              {recalculating ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
              재계산
            </button>
          </div>
        </div>

        {/* ── 에러 ──────────────────────────────────────────────────── */}
        {error && (
          <div className="flex items-center gap-3 p-4 bg-red-50 dark:bg-red-500/10 border border-red-200 dark:border-red-500/20 rounded-xl mb-6">
            <AlertCircle size={16} className="text-red-500 shrink-0" />
            <p className="text-sm text-red-500">{error}</p>
            <button onClick={fetchData} className="ml-auto text-xs text-red-500 underline">다시 시도</button>
          </div>
        )}

        {/* ── 활성 경보 ─────────────────────────────────────────────── */}
        {unreadAlerts.length > 0 && (
          <div className="mb-8">
            <SectionTitle>
              <AlertTriangle size={14} className="text-red-500" />
              활성 경보 ({unreadAlerts.length})
            </SectionTitle>
            <div className="space-y-2">
              {unreadAlerts.map((alert) => {
                const cfg = ALERT_CONFIG[alert.alertType] ?? ALERT_CONFIG.FREE_RIDE;
                return (
                  <div key={alert.id} className={`flex items-start gap-3 p-4 border rounded-xl ${cfg.bg}`}>
                    <AlertTriangle size={15} className={`${cfg.color} shrink-0 mt-0.5`} />
                    <div>
                      <p className={`text-xs font-semibold ${cfg.color}`}>{cfg.label}</p>
                      <p className="text-xs text-bb-text2 mt-0.5">{alert.message}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* ── 데이터 없음 ───────────────────────────────────────────── */}
        {scores.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-32 text-center">
            <div className="w-16 h-16 rounded-2xl bg-bb-surface border border-bb-border flex items-center justify-center mb-4">
              <ListChecks size={28} className="text-bb-text2" />
            </div>
            <p className="text-sm font-semibold text-bb-text mb-1">아직 참여 데이터가 없습니다</p>
            <p className="text-xs text-bb-text2 mb-6">
              태스크를 완료하거나 회의에 체크인하면 자동으로 집계됩니다
            </p>
            <button
              onClick={handleRecalculate}
              disabled={recalculating}
              className="flex items-center gap-2 px-4 py-2 bg-bb-primary hover:bg-bb-primary-h
                         text-white text-sm rounded-lg transition-all disabled:opacity-50"
            >
              {recalculating ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
              지금 계산하기
            </button>
          </div>
        ) : (
          <>
            {/* ── KPI 카드 4개 ────────────────────────────────────── */}
            <div className="grid grid-cols-4 gap-4 mb-8">
              <KpiCard
                label="팀원 수"
                value={scores.length}
                sub="명"
                icon={Users}
                accent="#6366F1"
              />
              <KpiCard
                label="전체 참여"
                value={fullCount}
                sub={`${scores.length}명 중 ${fullCount}명`}
                icon={TrendingUp}
                accent="#2DD4BF"
              />
              <KpiCard
                label="부분 참여"
                value={partialCount}
                sub="2~3개 항목 참여"
                icon={Zap}
                accent="#F59E0B"
              />
              <KpiCard
                label="미참여"
                value={noneCount}
                sub="0~1개 항목 참여"
                icon={TrendingDown}
                accent="#EF4444"
              />
            </div>

            {/* ── 참여 여부 테이블 ─────────────────────────────────── */}
            <section className="mb-8">
              <SectionTitle>
                <ListChecks size={13} />
                팀원별 항목 참여 여부
              </SectionTitle>

              <div className="bg-bb-surface border border-bb-border rounded-xl overflow-hidden">
                {/* 테이블 헤더 */}
                <div className="grid grid-cols-[1fr_auto_auto_auto_auto_auto] gap-0 border-b border-bb-border bg-bb-bg/60">
                  {[
                    { label: "팀원", cls: "text-left pl-5" },
                    { label: "태스크 완료", cls: "text-center" },
                    { label: "회의 참석", cls: "text-center" },
                    { label: "파일 업로드", cls: "text-center" },
                    { label: "액션아이템", cls: "text-center" },
                    { label: "종합", cls: "text-center pr-5" },
                  ].map((col) => (
                    <div key={col.label} className={`py-3 px-4 text-[11px] font-semibold text-bb-text2 uppercase tracking-wide ${col.cls}`}>
                      {col.label}
                    </div>
                  ))}
                </div>

                {/* 행 */}
                {scores.map((s, idx) => {
                  const hue = s.name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360;
                  const isLast = idx === scores.length - 1;
                  return (
                    <div
                      key={s.userId}
                      className={`grid grid-cols-[1fr_auto_auto_auto_auto_auto] items-center gap-0 ${
                        !isLast ? "border-b border-bb-border" : ""
                      } hover:bg-bb-bg/30 transition-colors`}
                    >
                      {/* 팀원 */}
                      <div className="flex items-center gap-3 pl-5 py-4 pr-4">
                        <div
                          style={{ background: `hsl(${hue} 50% 45%)` }}
                          className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0"
                        >
                          {s.name.slice(0, 2).toUpperCase()}
                        </div>
                        <div>
                          <p className="text-sm font-semibold text-bb-text">{s.name}</p>
                          <p className="text-[11px] text-bb-text2">{s.email}</p>
                        </div>
                      </div>

                      {/* 태스크 완료 */}
                      <div className="py-4 px-6 text-center">
                        <div className="flex flex-col items-center gap-1">
                          <ParticipationCell participated={s.taskParticipated} />
                          <CheckSquare size={11} className="text-bb-text2" />
                        </div>
                      </div>

                      {/* 회의 참석 */}
                      <div className="py-4 px-6 text-center">
                        <div className="flex flex-col items-center gap-1">
                          <ParticipationCell participated={s.meetingParticipated} />
                          <Video size={11} className="text-bb-text2" />
                        </div>
                      </div>

                      {/* 파일 업로드 */}
                      <div className="py-4 px-6 text-center">
                        <div className="flex flex-col items-center gap-1">
                          <ParticipationCell participated={s.fileParticipated} />
                          <Files size={11} className="text-bb-text2" />
                        </div>
                      </div>

                      {/* 액션아이템 */}
                      <div className="py-4 px-6 text-center">
                        <div className="flex flex-col items-center gap-1">
                          <ParticipationCell participated={s.actionParticipated} />
                          <ListChecks size={11} className="text-bb-text2" />
                        </div>
                      </div>

                      {/* 종합 */}
                      <div className="py-4 px-5 text-center">
                        <LevelBadge level={s.participationLevel} />
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* 판정 기준 안내 */}
              <div className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-[11px] text-bb-text2">
                <span>태스크: 담당 태스크 1개↑ 완료</span>
                <span>회의: 전체 회의 50%↑ 체크인</span>
                <span>파일: 1개↑ 업로드</span>
                <span>액션아이템: 담당 1개↑ 완료</span>
              </div>
              <div className="mt-1 flex flex-wrap gap-x-6 gap-y-1 text-[11px] text-bb-text2">
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-teal-400 inline-block" />전체참여: 4개 모두
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-yellow-400 inline-block" />부분참여: 2~3개
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-red-400 inline-block" />미참여: 0~1개
                </span>
              </div>
            </section>

            {/* ── 마감 위험도 게이지 ───────────────────────────────── */}
            {risk && (
              <section className="mb-8">
                <SectionTitle>
                  <AlertTriangle size={13} />
                  마감 위험도
                </SectionTitle>
                <div className="grid grid-cols-2 gap-4">
                  {/* 게이지 */}
                  <div className="bg-bb-surface border border-bb-border rounded-xl p-6 flex items-center gap-6">
                    <div className="w-28 h-28 shrink-0">
                      <ResponsiveContainer width="100%" height="100%">
                        <RadialBarChart
                          cx="50%" cy="50%"
                          innerRadius="60%" outerRadius="90%"
                          startAngle={180} endAngle={0}
                          data={riskGaugeData}
                        >
                          <RadialBar dataKey="value" cornerRadius={4} background={{ fill: "#1E293B" }} />
                        </RadialBarChart>
                      </ResponsiveContainer>
                    </div>
                    <div>
                      <p className="text-4xl font-bold" style={{ color: getRiskColor(risk.level) }}>
                        {risk.riskScore}
                      </p>
                      <p className="text-xs text-bb-text2 mt-0.5">/ 100점</p>
                      <span className="mt-2 inline-block text-xs font-semibold px-2.5 py-1 rounded-full border"
                        style={{
                          color: getRiskColor(risk.level),
                          borderColor: getRiskColor(risk.level) + "40",
                          background: getRiskColor(risk.level) + "15",
                        }}>
                        {riskLevelKo(risk.level)}
                      </span>
                    </div>
                  </div>

                  {/* 통계 */}
                  <div className="bg-bb-surface border border-bb-border rounded-xl p-6 space-y-3">
                    <StatRow label="완료율" value={`${risk.completionRate}%`} />
                    <StatRow label="완료 태스크" value={`${risk.doneTasks} / ${risk.totalTasks}개`} />
                    <StatRow label="지연 태스크" value={`${risk.overdueTasks}개`} />
                    <StatRow label="남은 기간" value={risk.daysRemaining != null ? `${risk.daysRemaining}일` : "-"} />
                    {risk.reasons.length > 0 && (
                      <div className="pt-2 border-t border-bb-border">
                        {risk.reasons.map((r, i) => (
                          <p key={i} className="text-[11px] text-amber-400 mt-1">• {r}</p>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </section>
            )}

            {/* ── 팀원별 TDL 완료 현황 ───────────────────────────────── */}
            {tasks.length > 0 && scores.length > 0 && (
              <section className="mb-8">
                <SectionTitle>
                  <CheckSquare size={14} className="text-indigo-400" />
                  팀원별 TDL 완료 현황
                </SectionTitle>
                <div className="space-y-4">
                  {scores.map((member) => {
                    const myTasks    = tasks.filter((t) => t.assignees.some((a) => a.userId === member.userId));
                    const done       = myTasks.filter((t) => t.status === "DONE");
                    const inProgress = myTasks.filter((t) => t.status === "IN_PROGRESS");
                    const todo       = myTasks.filter((t) => t.status === "TODO");
                    const pct        = myTasks.length > 0 ? Math.round((done.length / myTasks.length) * 100) : 0;
                    const hue        = member.name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360;

                    return (
                      <div key={member.userId} className="bg-bb-surface border border-bb-border rounded-xl p-4">
                        <div className="flex items-center gap-3 mb-3">
                          <div
                            style={{ background: `hsl(${hue} 50% 45%)` }}
                            className="w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0"
                          >
                            {member.name.slice(0, 2).toUpperCase()}
                          </div>
                          <div className="flex-1">
                            <div className="flex items-center gap-2">
                              <p className="text-sm font-semibold text-bb-text">{member.name}</p>
                              <LevelBadge level={member.participationLevel} />
                            </div>
                            <div className="flex items-center gap-2 mt-0.5">
                              <span className="text-[10px] text-teal-400">✅ {done.length}완료</span>
                              <span className="text-[10px] text-indigo-400">🔄 {inProgress.length}진행</span>
                              <span className="text-[10px] text-bb-text2">⬜ {todo.length}예정</span>
                            </div>
                          </div>
                          <div className="text-right shrink-0">
                            <p className={`text-xl font-bold ${
                              pct === 100 ? "text-teal-400" : pct >= 50 ? "text-indigo-400" : "text-bb-text2"
                            }`}>{myTasks.length > 0 ? `${pct}%` : "-"}</p>
                            <p className="text-[10px] text-bb-text2">완료율</p>
                          </div>
                        </div>

                        {myTasks.length > 0 && (
                          <div className="h-1.5 bg-slate-800 rounded-full overflow-hidden mb-3">
                            <div
                              className="h-full bg-gradient-to-r from-indigo-500 to-teal-400 rounded-full transition-all duration-500"
                              style={{ width: `${pct}%` }}
                            />
                          </div>
                        )}

                        {myTasks.length === 0 ? (
                          <p className="text-xs text-bb-text2 italic">배정된 태스크가 없습니다</p>
                        ) : (
                          <div className="space-y-1.5">
                            {myTasks.map((task) => {
                              const overdue = task.dueDate && task.status !== "DONE"
                                && new Date(task.dueDate) < new Date();
                              return (
                                <div key={task.id} className="flex items-center gap-2.5">
                                  <span className="shrink-0 text-sm">
                                    {task.status === "DONE" ? "✅" : task.status === "IN_PROGRESS" ? "🔄" : "⬜"}
                                  </span>
                                  <span className={`text-xs flex-1 ${
                                    task.status === "DONE" ? "line-through text-bb-text2" : "text-bb-text"
                                  }`}>
                                    {task.title}
                                  </span>
                                  {task.dueDate && (
                                    <span className={`text-[10px] shrink-0 ${overdue ? "text-red-400 font-medium" : "text-bb-text2"}`}>
                                      {overdue ? "⚠️ " : ""}
                                      {new Date(task.dueDate).toLocaleDateString("ko-KR", { month: "short", day: "numeric" })}
                                    </span>
                                  )}
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </section>
            )}
          </>
        )}
      </main>
    </div>
  );
}

// ── 유틸 ─────────────────────────────────────────────────────────────────
function StatRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between text-xs">
      <span className="text-bb-text2">{label}</span>
      <span className="font-semibold text-bb-text">{value}</span>
    </div>
  );
}

function getRiskColor(level: RiskData["level"]) {
  return level === "CRITICAL" ? "#EF4444"
       : level === "HIGH"     ? "#F97316"
       : level === "MEDIUM"   ? "#F59E0B"
       :                        "#2DD4BF";
}

function riskLevelKo(level: RiskData["level"]) {
  return level === "CRITICAL" ? "위험"
       : level === "HIGH"     ? "높음"
       : level === "MEDIUM"   ? "보통"
       :                        "낮음";
}
