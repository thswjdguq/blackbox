"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import { ScoreEntry, Alert, AlertType } from "@/types/vault";
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, PieChart, Pie, Cell, LineChart,
  Line, RadialBarChart, RadialBar,
} from "recharts";
import {
  BarChart2, RefreshCw, AlertTriangle, Star, TrendingUp,
  Users, Loader2, AlertCircle, Zap, FileText, CheckSquare,
  Video, GitBranch, Download, ShieldCheck, Gauge, Clock,
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
const MEMBER_COLORS = ["#6366F1", "#2DD4BF", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899"];

const ALERT_CONFIG: Record<AlertType, { label: string; color: string; bg: string }> = {
  FREE_RIDE: { label: "무임승차 의심",  color: "text-yellow-500", bg: "bg-yellow-50 dark:bg-yellow-400/10 border-yellow-200 dark:border-yellow-400/20" },
  OVERLOAD:  { label: "과부하 위험",    color: "text-orange-500", bg: "bg-orange-50 dark:bg-orange-400/10 border-orange-200 dark:border-orange-400/20" },
  DROPOUT:   { label: "이탈 감지",      color: "text-red-500",    bg: "bg-red-50 dark:bg-red-400/10 border-red-200 dark:border-red-400/20" },
  TAMPER:    { label: "파일 변조 의심", color: "text-red-500",    bg: "bg-red-50 dark:bg-red-500/10 border-red-200 dark:border-red-500/20" },
};

// ── 차트 색상 — 라이트/다크 감지 ────────────────────────────────────────
function useChartTheme() {
  const [theme, setTheme] = useState({ grid: "#E2E8F0", axisText: "#64748B", tooltipBg: "#ffffff", tooltipBorder: "#E2E8F0" });
  useEffect(() => {
    const update = () => {
      const dark = document.documentElement.classList.contains("dark");
      setTheme(dark
        ? { grid: "#334155", axisText: "#94A3B8", tooltipBg: "#1E293B", tooltipBorder: "#334155" }
        : { grid: "#E2E8F0", axisText: "#64748B", tooltipBg: "#ffffff", tooltipBorder: "#E2E8F0" });
    };
    update();
    const obs = new MutationObserver(update);
    obs.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
    return () => obs.disconnect();
  }, []);
  return theme;
}

// ── 커스텀 툴팁 ────────────────────────────────────────────────────────
function ChartTooltip({ active, payload, label }: {
  active?: boolean; payload?: { value: number; name: string; color?: string }[]; label?: string;
}) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-bb-surface border border-bb-border rounded-xl px-4 py-3 shadow-xl text-xs">
      {label && <p className="font-semibold text-bb-text mb-2">{label}</p>}
      {payload.map((p, i) => (
        <div key={i} className="flex items-center justify-between gap-4">
          <span className="text-bb-text2">{p.name}</span>
          <span className="font-bold" style={{ color: p.color ?? "#6366F1" }}>
            {typeof p.value === "number" ? p.value.toFixed(1) : p.value}
          </span>
        </div>
      ))}
    </div>
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

// ── 인사이트 카드 ───────────────────────────────────────────────────────
function InsightCard({ label, name, icon: Icon }: {
  label: string; name: string | null; icon: React.ElementType;
}) {
  return (
    <div className="bg-indigo-50 dark:bg-bb-primary/10 border border-indigo-100 dark:border-bb-primary/20 rounded-xl p-4">
      <div className="flex items-center gap-2 mb-2">
        <Icon size={13} className="text-bb-primary" />
        <span className="text-[11px] font-medium text-bb-primary uppercase tracking-wide">{label}</span>
      </div>
      <p className="text-sm font-semibold text-bb-text">
        {name ?? <span className="text-bb-text2 font-normal text-xs">아직 데이터가 없습니다</span>}
      </p>
    </div>
  );
}

// ── 멤버 순위 카드 (개선) ───────────────────────────────────────────────
function MemberRankCard({ entry, rank }: { entry: ScoreEntry; rank: number }) {
  const total = Number(entry.totalScore);
  const hue = entry.name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360;
  const isFirst = rank === 1;

  return (
    <div className={`rounded-xl p-4 border transition-all ${
      isFirst
        ? "bg-indigo-50 dark:bg-bb-primary/5 border-l-4 border-l-bb-primary border-t-bb-border border-r-bb-border border-b-bb-border"
        : "bg-white dark:bg-bb-surface border-bb-border"
    }`}>
      <div className="flex items-center gap-3">
        {/* 순위 뱃지 */}
        <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold shrink-0 ${
          rank === 1 ? "bg-yellow-100 dark:bg-yellow-500/20 text-yellow-600 dark:text-yellow-400" :
          rank === 2 ? "bg-slate-100 dark:bg-slate-400/20 text-slate-500 dark:text-bb-text2" :
          rank === 3 ? "bg-orange-100 dark:bg-orange-600/20 text-orange-600 dark:text-orange-500" :
                       "bg-bb-surface2 text-bb-text2"
        }`}>
          {rank}
        </div>

        {/* 아바타 */}
        <div
          style={{ background: `hsl(${hue} 50% 50%)` }}
          className="w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0"
        >
          {entry.name.slice(0, 2).toUpperCase()}
        </div>

        {/* 이름 */}
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-bb-text truncate">{entry.name}</p>
          <p className="text-xs text-bb-text2 truncate">{entry.email}</p>
        </div>

        {/* 총점 */}
        <div className="text-right shrink-0">
          <p className="text-2xl font-bold text-bb-primary leading-none">{total.toFixed(1)}</p>
          <p className="text-[10px] text-bb-text2 mt-0.5">/ 150점</p>
        </div>
      </div>

      {/* 세부 지표 */}
      <div className="grid grid-cols-4 gap-2 mt-3 pt-3 border-t border-bb-border">
        {[
          { icon: CheckSquare, label: "태스크", value: entry.taskScore, color: "#6366F1" },
          { icon: Video,       label: "회의",   value: entry.meetingScore, color: "#2DD4BF" },
          { icon: FileText,    label: "파일",   value: entry.docScore, color: "#F59E0B" },
          { icon: GitBranch,   label: "Git",    value: entry.gitScore, color: "#EF4444" },
        ].map(({ icon: Icon, label, value, color }) => (
          <div key={label} className="text-center">
            <div className="flex items-center justify-center gap-1 mb-0.5">
              <Icon size={10} style={{ color }} />
              <span className="text-[10px] text-bb-text2">{label}</span>
            </div>
            <p className="text-xs font-semibold text-bb-text">{Number(value).toFixed(0)}</p>
          </div>
        ))}
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
  const chart     = useChartTheme();

  const [scores,        setScores]        = useState<ScoreEntry[]>([]);
  const [alerts,        setAlerts]        = useState<Alert[]>([]);
  const [risk,          setRisk]          = useState<RiskData | null>(null);
  const [tasks,         setTasks]         = useState<TaskSummary[]>([]);
  const [loading,       setLoading]       = useState(true);
  const [recalculating, setRecalculating] = useState(false);
  const [error,         setError]         = useState("");
  const [downloading,   setDownloading]   = useState(false);

  const normalise = (arr: ScoreEntry[]) =>
    arr.map((s) => ({
      ...s,
      gitScore:     Number(s.gitScore),
      docScore:     Number(s.docScore),
      meetingScore: Number(s.meetingScore),
      taskScore:    Number(s.taskScore),
      totalScore:   Number(s.totalScore),
    }));

  const fetchData = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const [scoreRes, alertRes, riskRes, taskRes] = await Promise.all([
        api.get<ScoreEntry[]>(`/projects/${projectId}/scores`),
        api.get<Alert[]>(`/projects/${projectId}/alerts`),
        api.get<RiskData>(`/projects/${projectId}/risk`),
        api.get<TaskSummary[]>(`/projects/${projectId}/tasks`),
      ]);
      setScores(normalise(scoreRes.data));
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
    if (!localStorage.getItem("accessToken")) { router.replace("/login"); return; }
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
      setScores(normalise(data));
    } catch {
      setError("재계산에 실패했습니다.");
    } finally {
      setRecalculating(false);
    }
  };

  // ── 파생 데이터 ──────────────────────────────────────────────────────
  const sorted        = [...scores].sort((a, b) => b.totalScore - a.totalScore);
  const avgScore      = scores.length ? scores.reduce((a, s) => a + s.totalScore, 0) / scores.length : 0;
  const maxScore      = scores.length ? Math.max(...scores.map((s) => s.totalScore)) : 0;
  const weeklyActivities = Math.round(
    scores.reduce((a, s) => a + s.taskScore / 10 + s.meetingScore / 15, 0),
  );

  const topActive  = sorted[0]   ?? null;
  const topMeeting = scores.length ? [...scores].sort((a, b) => b.meetingScore - a.meetingScore)[0] : null;
  const topTask    = scores.length ? [...scores].sort((a, b) => b.taskScore    - a.taskScore)[0]    : null;

  // 막대 차트 — 멤버별 총점
  const totalBarData = sorted.map((s, i) => ({
    name:  s.name.length > 5 ? s.name.slice(0, 4) + "…" : s.name,
    총점:  Number(s.totalScore.toFixed(1)),
    color: MEMBER_COLORS[i % MEMBER_COLORS.length],
  }));

  // 도넛 차트 — 항목별 비중
  const donutRaw = [
    { name: "태스크",    value: scores.reduce((a, s) => a + s.taskScore, 0),    color: "#6366F1" },
    { name: "회의 참여", value: scores.reduce((a, s) => a + s.meetingScore, 0), color: "#2DD4BF" },
    { name: "파일 업로드",value: scores.reduce((a, s) => a + s.docScore, 0),    color: "#F59E0B" },
  ];
  const donutData = donutRaw.filter((d) => d.value > 0);

  // 라인 차트 — 주차별 추이 (현재 주 = 팀 평균, 이전 3주 = 0)
  const weeklyTrend = [
    { week: "3주 전", 활동: 0 },
    { week: "2주 전", 활동: 0 },
    { week: "지난주",  활동: 0 },
    { week: "이번 주", 활동: Math.round(avgScore) },
  ];

  const unreadAlerts = alerts.filter((a) => !a.isRead);

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
            <div className="grid grid-cols-3 gap-4">
              {[...Array(3)].map((_, i) => <div key={i} className="h-16 bg-bb-surface rounded-xl" />)}
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
              팀 활동 로그를 기반으로 자동 계산된 기여도입니다
            </p>
          </div>
          <button
            onClick={handleRecalculate}
            disabled={recalculating}
            className="flex items-center gap-2 px-4 py-2 bg-bb-primary hover:bg-bb-primary-h text-white
                       text-sm rounded-lg transition-all disabled:opacity-50 font-medium"
          >
            {recalculating
              ? <Loader2 size={14} className="animate-spin" />
              : <RefreshCw size={14} />}
            점수 재계산
          </button>
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

        {/* ── 팀원별 TDL 완료 현황 ───────────────────────────────────── */}
        {tasks.length > 0 && scores.length > 0 && (
          <div className="mb-8">
            <SectionTitle>
              <CheckSquare size={14} className="text-indigo-400" />
              팀원별 TDL 완료 현황
            </SectionTitle>
            <div className="space-y-4">
              {scores.map((member) => {
                const myTasks = tasks.filter((t) =>
                  t.assignees.some((a) => a.userId === member.userId)
                );
                const done       = myTasks.filter((t) => t.status === "DONE");
                const inProgress = myTasks.filter((t) => t.status === "IN_PROGRESS");
                const todo       = myTasks.filter((t) => t.status === "TODO");
                const pct = myTasks.length > 0 ? Math.round((done.length / myTasks.length) * 100) : 0;
                const hue = member.name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360;

                return (
                  <div key={member.userId} className="bg-bb-surface border border-bb-border rounded-xl p-4">
                    {/* 멤버 헤더 */}
                    <div className="flex items-center gap-3 mb-3">
                      <div
                        style={{ background: `hsl(${hue} 50% 45%)` }}
                        className="w-9 h-9 rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0"
                      >
                        {member.name.slice(0, 2).toUpperCase()}
                      </div>
                      <div className="flex-1">
                        <p className="text-sm font-semibold text-bb-text">{member.name}</p>
                        <div className="flex items-center gap-2 mt-0.5">
                          <span className="text-[10px] text-teal-400">✅ {done.length}완료</span>
                          <span className="text-[10px] text-indigo-400">🔄 {inProgress.length}진행</span>
                          <span className="text-[10px] text-bb-text2">⬜ {todo.length}예정</span>
                        </div>
                      </div>
                      {/* 완료율 */}
                      <div className="text-right shrink-0">
                        <p className={`text-xl font-bold ${
                          pct === 100 ? "text-teal-400" : pct >= 50 ? "text-indigo-400" : "text-bb-text2"
                        }`}>{myTasks.length > 0 ? `${pct}%` : "-"}</p>
                        <p className="text-[10px] text-bb-text2">완료율</p>
                      </div>
                    </div>

                    {/* 진행 바 */}
                    {myTasks.length > 0 && (
                      <div className="h-1.5 bg-slate-800 rounded-full overflow-hidden mb-3">
                        <div
                          className="h-full bg-gradient-to-r from-indigo-500 to-teal-400 rounded-full transition-all duration-500"
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    )}

                    {/* 태스크 목록 */}
                    {myTasks.length === 0 ? (
                      <p className="text-xs text-bb-text2 italic">배정된 태스크가 없습니다</p>
                    ) : (
                      <div className="space-y-1.5">
                        {myTasks.map((task) => {
                          const overdue = task.dueDate && task.status !== "DONE"
                            && new Date(task.dueDate) < new Date();
                          return (
                            <div key={task.id} className="flex items-center gap-2.5">
                              {/* 상태 아이콘 */}
                              <span className="shrink-0 text-sm">
                                {task.status === "DONE" ? "✅"
                                  : task.status === "IN_PROGRESS" ? "🔄" : "⬜"}
                              </span>
                              <span className={`text-xs flex-1 ${
                                task.status === "DONE" ? "line-through text-bb-text2" : "text-bb-text"
                              }`}>
                                {task.title}
                              </span>
                              {task.dueDate && (
                                <span className={`text-[10px] shrink-0 ${
                                  overdue ? "text-red-400 font-medium" : "text-bb-text2"
                                }`}>
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
          </div>
        )}

        {/* ── 데이터 없음 ───────────────────────────────────────────── */}
        {scores.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-32 text-center">
            <div className="w-16 h-16 rounded-2xl bg-bb-surface border border-bb-border flex items-center justify-center mb-4">
              <BarChart2 size={28} className="text-bb-text2" />
            </div>
            <p className="text-sm font-semibold text-bb-text mb-1">아직 기여도 데이터가 없습니다</p>
            <p className="text-xs text-bb-text2 mb-6">
              태스크를 완료하거나 파일을 업로드하면 점수가 자동으로 산출됩니다
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
              <KpiCard label="팀원 수"        value={scores.length}           sub="명"               icon={Users}      accent="#6366F1" />
              <KpiCard label="팀 평균 점수"   value={avgScore.toFixed(1)}     sub="/ 150점"          icon={TrendingUp} accent="#2DD4BF" />
              <KpiCard label="최고 점수"      value={maxScore.toFixed(1)}     sub={topActive?.name ?? ""} icon={Star}  accent="#F59E0B" />
              <KpiCard label="이번 주 기록"   value={weeklyActivities}        sub="태스크·회의 합산" icon={Zap}        accent="#EF4444" />
            </div>

            {/* ── 인사이트 카드 3개 ────────────────────────────────── */}
            <div className="grid grid-cols-3 gap-4 mb-8">
              <InsightCard label="이번 주 가장 활발한 팀원"       name={topActive?.name  ?? null} icon={Zap} />
              <InsightCard label="회의 참여율이 가장 높은 팀원"   name={topMeeting?.name ?? null} icon={Video} />
              <InsightCard label="완료 태스크가 가장 많은 팀원"   name={topTask?.name    ?? null} icon={CheckSquare} />
            </div>

            {/* ── 멤버 순위 ────────────────────────────────────────── */}
            <section className="mb-8">
              <SectionTitle>
                <Users size={13} />
                멤버 순위
              </SectionTitle>
              <div className="space-y-2">
                {sorted.map((entry, i) => (
                  <MemberRankCard key={entry.userId} entry={entry} rank={i + 1} />
                ))}
              </div>
            </section>

            {/* ── 차트 1+2: 막대 + 도넛 ───────────────────────────── */}
            <div className="grid grid-cols-5 gap-4 mb-4">
              {/* 막대 차트 (3/5 너비) */}
              <section className="col-span-3">
                <SectionTitle><BarChart2 size={13} />멤버별 총 기여도</SectionTitle>
                <div className="bg-white dark:bg-bb-surface border border-bb-border rounded-xl p-6 shadow-sm">
                  <ResponsiveContainer width="100%" height={260}>
                    <BarChart data={totalBarData} margin={{ top: 5, right: 10, left: -10, bottom: 5 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} />
                      <XAxis dataKey="name" stroke={chart.axisText} tick={{ fontSize: 12 }} />
                      <YAxis domain={[0, 150]} stroke={chart.axisText} tick={{ fontSize: 11 }} />
                      <Tooltip content={<ChartTooltip />} />
                      <Bar dataKey="총점" radius={[6, 6, 0, 0]}>
                        {totalBarData.map((entry, i) => (
                          <Cell key={i} fill={entry.color} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                  {/* 범례 */}
                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-3 justify-center">
                    {sorted.map((s, i) => (
                      <div key={s.userId} className="flex items-center gap-1.5 text-xs text-bb-text2">
                        <div className="w-2.5 h-2.5 rounded-full" style={{ background: MEMBER_COLORS[i % MEMBER_COLORS.length] }} />
                        {s.name}
                      </div>
                    ))}
                  </div>
                </div>
              </section>

              {/* 도넛 차트 (2/5 너비) */}
              <section className="col-span-2">
                <SectionTitle>항목별 기여 비중</SectionTitle>
                <div className="bg-white dark:bg-bb-surface border border-bb-border rounded-xl p-6 shadow-sm h-[calc(100%-2rem)]">
                  {donutData.length > 0 ? (
                    <>
                      <ResponsiveContainer width="100%" height={200}>
                        <PieChart>
                          <Pie
                            data={donutData}
                            cx="50%"
                            cy="50%"
                            innerRadius={55}
                            outerRadius={85}
                            paddingAngle={3}
                            dataKey="value"
                          >
                            {donutData.map((entry, i) => (
                              <Cell key={i} fill={entry.color} />
                            ))}
                          </Pie>
                          <Tooltip content={<ChartTooltip />} />
                        </PieChart>
                      </ResponsiveContainer>
                      <div className="space-y-2 mt-2">
                        {donutData.map((d) => {
                          const total = donutData.reduce((a, x) => a + x.value, 0);
                          const pct = total > 0 ? Math.round(d.value / total * 100) : 0;
                          return (
                            <div key={d.name} className="flex items-center justify-between text-xs">
                              <div className="flex items-center gap-2">
                                <div className="w-2.5 h-2.5 rounded-full" style={{ background: d.color }} />
                                <span className="text-bb-text2">{d.name}</span>
                              </div>
                              <span className="font-semibold text-bb-text">{pct}%</span>
                            </div>
                          );
                        })}
                      </div>
                    </>
                  ) : (
                    <div className="h-full flex items-center justify-center text-xs text-bb-text2 text-center">
                      활동 데이터가 쌓이면<br />표시됩니다
                    </div>
                  )}
                </div>
              </section>
            </div>

            {/* ── 차트 3: 주차별 활동 추이 라인 차트 ──────────────── */}
            <section className="mb-8">
              <SectionTitle><TrendingUp size={13} />주차별 활동 추이</SectionTitle>
              <div className="bg-white dark:bg-bb-surface border border-bb-border rounded-xl p-6 shadow-sm">
                {avgScore > 0 ? (
                  <ResponsiveContainer width="100%" height={200}>
                    <LineChart data={weeklyTrend} margin={{ top: 5, right: 20, left: -10, bottom: 5 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke={chart.grid} />
                      <XAxis dataKey="week" stroke={chart.axisText} tick={{ fontSize: 12 }} />
                      <YAxis stroke={chart.axisText} tick={{ fontSize: 11 }} />
                      <Tooltip content={<ChartTooltip />} />
                      <Line
                        type="monotone"
                        dataKey="활동"
                        stroke="#6366F1"
                        strokeWidth={2.5}
                        dot={{ fill: "#6366F1", r: 5 }}
                        activeDot={{ r: 7 }}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                ) : (
                  <div className="h-48 flex items-center justify-center text-sm text-bb-text2">
                    활동 데이터가 쌓이면 추이 차트가 표시됩니다
                  </div>
                )}
              </div>
            </section>

            {/* ── 역할 분포 + 위험도 ───────────────────────────────────── */}
            <div className="grid grid-cols-5 gap-4 mb-8">

              {/* 역할별 기여 분포 (3/5) */}
              <section className="col-span-3">
                <SectionTitle><Users size={13} />역할별 기여 분포</SectionTitle>
                <div className="bg-white dark:bg-bb-surface border border-bb-border rounded-xl p-6 shadow-sm">
                  <div className="space-y-3">
                    {sorted.map((s, i) => {
                      const t = s.taskScore + s.meetingScore + s.docScore + s.gitScore || 1;
                      const bars = [
                        { label: "태스크", val: s.taskScore,    color: "#6366F1" },
                        { label: "회의",   val: s.meetingScore, color: "#2DD4BF" },
                        { label: "파일",   val: s.docScore,     color: "#F59E0B" },
                        { label: "Git",    val: s.gitScore,     color: "#EF4444" },
                      ];
                      return (
                        <div key={s.userId}>
                          <div className="flex items-center justify-between mb-1">
                            <span className="text-xs font-medium text-bb-text">{s.name}</span>
                            <span className="text-xs text-bb-text2">{s.totalScore.toFixed(0)}점</span>
                          </div>
                          <div className="flex h-5 rounded-full overflow-hidden gap-px">
                            {bars.map((b) => {
                              const pct = Math.round((b.val / t) * 100);
                              return pct > 0 ? (
                                <div
                                  key={b.label}
                                  title={`${b.label}: ${pct}%`}
                                  style={{ width: `${pct}%`, background: b.color }}
                                  className="transition-all"
                                />
                              ) : null;
                            })}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                  {/* 범례 */}
                  <div className="flex flex-wrap gap-x-4 gap-y-1 mt-4 pt-3 border-t border-bb-border">
                    {[
                      { label: "태스크", color: "#6366F1" },
                      { label: "회의",   color: "#2DD4BF" },
                      { label: "파일",   color: "#F59E0B" },
                      { label: "Git",    color: "#EF4444" },
                    ].map((l) => (
                      <div key={l.label} className="flex items-center gap-1.5 text-xs text-bb-text2">
                        <div className="w-2.5 h-2.5 rounded-sm" style={{ background: l.color }} />
                        {l.label}
                      </div>
                    ))}
                  </div>
                </div>
              </section>

              {/* 마감 위험도 (2/5) */}
              {risk && (
                <section className="col-span-2">
                  <SectionTitle><Gauge size={13} />마감 위험도</SectionTitle>
                  <div className="bg-white dark:bg-bb-surface border border-bb-border rounded-xl p-6 shadow-sm h-[calc(100%-2rem)] flex flex-col">
                    {/* 위험도 게이지 */}
                    <div className="flex items-center justify-center mb-4">
                      <div className="relative w-32 h-32">
                        <ResponsiveContainer width="100%" height="100%">
                          <RadialBarChart
                            cx="50%" cy="50%"
                            innerRadius="60%" outerRadius="85%"
                            startAngle={225} endAngle={-45}
                            data={[{ value: risk.riskScore, fill:
                              risk.level === "CRITICAL" ? "#EF4444" :
                              risk.level === "HIGH"     ? "#F59E0B" :
                              risk.level === "MEDIUM"   ? "#6366F1" : "#2DD4BF"
                            }]}
                          >
                            <RadialBar dataKey="value" cornerRadius={6} background={{ fill: "#f1f5f9" }} />
                          </RadialBarChart>
                        </ResponsiveContainer>
                        <div className="absolute inset-0 flex flex-col items-center justify-center">
                          <span className="text-2xl font-bold text-bb-text">{risk.riskScore}</span>
                          <span className="text-[10px] text-bb-text2">/ 100</span>
                        </div>
                      </div>
                    </div>

                    {/* 레벨 뱃지 */}
                    <div className="text-center mb-3">
                      <span className={`inline-block text-xs font-bold px-3 py-1 rounded-full ${
                        risk.level === "CRITICAL" ? "bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400" :
                        risk.level === "HIGH"     ? "bg-amber-100 dark:bg-amber-500/20 text-amber-600 dark:text-amber-400" :
                        risk.level === "MEDIUM"   ? "bg-indigo-100 dark:bg-bb-primary/20 text-bb-primary" :
                                                    "bg-teal-100 dark:bg-teal-500/20 text-teal-600 dark:text-teal-400"
                      }`}>
                        {risk.level === "CRITICAL" ? "심각" :
                         risk.level === "HIGH"     ? "위험" :
                         risk.level === "MEDIUM"   ? "주의" : "안전"}
                      </span>
                    </div>

                    {/* 통계 */}
                    <div className="grid grid-cols-2 gap-2 mb-3 text-center">
                      <div className="bg-bb-surface2/50 rounded-lg p-2">
                        <p className="text-xs text-bb-text2">완료율</p>
                        <p className="text-sm font-bold text-bb-text">{risk.completionRate}%</p>
                      </div>
                      <div className="bg-bb-surface2/50 rounded-lg p-2">
                        <p className="text-xs text-bb-text2">지연 태스크</p>
                        <p className={`text-sm font-bold ${risk.overdueTasks > 0 ? "text-red-500" : "text-bb-text"}`}>
                          {risk.overdueTasks}개
                        </p>
                      </div>
                    </div>
                    {risk.daysRemaining !== null && (
                      <div className="flex items-center gap-1.5 justify-center text-xs text-bb-text2 mb-3">
                        <Clock size={11} />
                        마감까지 {risk.daysRemaining >= 0 ? `${risk.daysRemaining}일` : `${Math.abs(risk.daysRemaining)}일 초과`}
                      </div>
                    )}

                    {/* 위험 이유 */}
                    <div className="space-y-1 mt-auto">
                      {risk.reasons.map((r, i) => (
                        <p key={i} className="text-[11px] text-bb-text2 flex items-start gap-1.5">
                          <span className="shrink-0 mt-0.5">•</span>{r}
                        </p>
                      ))}
                    </div>
                  </div>
                </section>
              )}
            </div>

            {/* ── 무결성 PDF 리포트 ─────────────────────────────────── */}
            <section>
              <SectionTitle>
                <ShieldCheck size={13} className="text-bb-primary" />
                무결성 PDF 리포트
              </SectionTitle>
              <div className="bg-white dark:bg-bb-surface border border-bb-border rounded-xl p-6 shadow-sm">
                <div className="flex items-start justify-between gap-6">
                  <div className="flex-1">
                    <h3 className="text-sm font-semibold text-bb-text mb-1">
                      SHA-256 해시 기반 무결성 보고서
                    </h3>
                    <p className="text-xs text-bb-text2 leading-relaxed">
                      기여도 점수, 칸반 태스크, Hash Vault 파일 목록을 포함한 PDF 리포트를 생성합니다.
                      각 데이터 행에 SHA-256 해시가 포함되어 위변조 여부를 검증할 수 있습니다.
                    </p>
                    <div className="flex flex-wrap gap-2 mt-3">
                      {["기여도 점수", "칸반 태스크", "Hash Vault 파일", "경보 현황", "리포트 해시"].map((tag) => (
                        <span key={tag} className="text-[10px] px-2 py-0.5 rounded-full bg-bb-primary/10 text-bb-primary font-medium">
                          {tag}
                        </span>
                      ))}
                    </div>
                  </div>
                  <button
                    onClick={handleDownloadReport}
                    disabled={downloading}
                    className="flex items-center gap-2 px-5 py-2.5 bg-bb-primary hover:bg-bb-primary-h
                               text-white text-sm font-medium rounded-lg transition-all
                               disabled:opacity-50 shrink-0"
                  >
                    {downloading
                      ? <Loader2 size={14} className="animate-spin" />
                      : <Download size={14} />}
                    {downloading ? "생성 중..." : "리포트 발급"}
                  </button>
                </div>
              </div>
            </section>
          </>
        )}
      </main>
    </div>
  );
}
