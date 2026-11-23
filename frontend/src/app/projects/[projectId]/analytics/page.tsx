"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import ProjectNav from "@/components/project/ProjectNav";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import { DUE_TEXT, dueLabel, dueTone, shortDate } from "@/lib/due";
import { ALERT_TONE } from "@/lib/alertTone";
import { apiError, blobApiError } from "@/lib/apiError";
import { useMyRole } from "@/hooks/useMyRole";
import { ScoreEntry, Alert, AlertType } from "@/types/vault";
import { columns, countText, extraItems, levelItems, noTarget, reviewItem, sortScores } from "@/lib/scoreItems";
import {
  RadialBarChart, RadialBar, ResponsiveContainer,
} from "recharts";
import {
  RefreshCw, AlertTriangle, Users, Loader2,
  AlertCircle, CheckSquare, Video, Files,
  Download, Zap, TrendingDown, TrendingUp,
  ListChecks, MessageSquareText,
} from "lucide-react";
import { loginHref } from "@/lib/loginRedirect";

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
  NONE:    { label: "집계 기록 적음",    cls: "bg-bb-surface2 text-bb-text2 border-bb-border",    dot: "bg-slate-400" },
} as const;

const ALERT_CONFIG: Record<AlertType, { label: string; color: string; bg: string }> = {
  FREE_RIDE: { label: "참여 기록 확인",  color: "text-yellow-500", bg: "bg-yellow-50 dark:bg-yellow-400/10 border-yellow-200 dark:border-yellow-400/20" },
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
    <span className="text-teal-400 font-bold text-base" aria-label="충족">✓</span>
  ) : (
    <span className="text-bb-text2 text-base" aria-label="미충족">–</span>
  );
}

// 아는 항목의 아이콘. 모르는 code는 아이콘 없이 label만 보인다(K-40 2장)
const ITEM_ICON: Record<string, React.ElementType> = {
  TASK_DONE: CheckSquare, MEETING_ATTENDANCE: Video, FILE_UPLOAD: Files, ACTION_ITEM: ListChecks, REVIEW_ACTIVITY: MessageSquareText,
};

// ── KPI 카드 ────────────────────────────────────────────────────────────
function KpiCard({
  label, value, sub, icon: Icon,
}: {
  label: string; value: string | number; sub?: string;
  icon: React.ElementType;
}) {
  return (
    <div className="bg-white dark:bg-bb-surface border border-bb-border rounded-xl overflow-hidden shadow-sm">
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
  // 수동 재계산은 팀장·팀원만(관찰자 403). 역할을 아직 모르면 버튼을 두고 서버 판단을 따른다
  const role      = useMyRole(projectId);
  const canRecalc = role !== "OBSERVER";

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
      if (status === 401) { router.replace(loginHref()); return; }
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
    } catch (err) {
      setError(await blobApiError(err, "리포트 생성에 실패했습니다."));
    } finally {
      setDownloading(false);
    }
  };

  const handleRecalculate = async () => {
    setRecalculating(true);
    try {
      const { data } = await api.post<ScoreEntry[]>(`/projects/${projectId}/scores/recalculate`);
      setScores(data);
    } catch (err) {
      setError(apiError(err, "재계산에 실패했습니다."));
    } finally {
      setRecalculating(false);
    }
  };

  // ── 파생 데이터 ──────────────────────────────────────────────────────
  const sorted       = sortScores(scores);
  const levelCols    = columns(sorted, levelItems);
  const extraCols    = columns(sorted, extraItems);
  const reviewRows   = sorted.filter((s) => reviewItem(s));
  const gridCols     = `minmax(13rem, 1fr) repeat(${levelCols.length + extraCols.length}, minmax(5.5rem, auto)) minmax(7rem, auto)`;
  const criteria     = sorted.find((s) => s.criteriaVersion)?.criteriaVersion ?? null;
  const lastCalc     = sorted.reduce<string | null>((a, s) => (!a || s.calculatedAt > a ? s.calculatedAt : a), null);
  const fullCount    = scores.filter((s) => s.participationLevel === "FULL").length;
  const partialCount = scores.filter((s) => s.participationLevel === "PARTIAL").length;
  const noneCount    = scores.filter((s) => s.participationLevel === "NONE").length;
  const unreadAlerts = alerts.filter((a) => !a.isResolved);
  const participationNotes = unreadAlerts.filter((a) => a.alertType === "FREE_RIDE");
  const otherAlerts = unreadAlerts.filter((a) => a.alertType !== "FREE_RIDE");

  // 위험도 게이지 데이터
  const riskGaugeData = risk ? [{ name: "위험도", value: risk.riskScore, fill: getRiskColor(risk.level) }] : [];

  // ── 로딩 스켈레톤 ─────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="min-h-screen bg-bb-bg">
        <Sidebar />
        <main className="md:ml-64 mt-14 md:mt-0 min-h-screen px-4 py-5 md:px-12 md:py-6">
        <ProjectNav projectId={projectId} />
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

      <main className="md:ml-64 mt-14 md:mt-0 min-h-screen px-4 py-5 md:px-12 md:py-6">
        <ProjectNav projectId={projectId} />
        {/* ── 헤더 ──────────────────────────────────────────────────── */}
        <div className="flex flex-wrap items-start justify-between gap-4 mb-6">
          <div>
            <h1 className="text-2xl font-bold text-bb-text">활동·기여 기록</h1>
            <p className="text-sm text-bb-text2 mt-1">
              자동 집계된 활동 기록입니다. 모든 기여를 포함하지는 않으며 평가·감점 기준이 아닙니다.
            </p>
          </div>
          <div className="flex shrink-0 gap-2">
            <button
              onClick={handleDownloadReport}
              disabled={downloading}
              className="flex items-center gap-2 whitespace-nowrap px-4 py-2 border border-bb-border bg-bb-surface
                         hover:bg-bb-bg text-sm rounded-lg transition-all disabled:opacity-50 text-bb-text2 font-medium"
            >
              {downloading ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />}
              기여 활동 보고서 PDF
            </button>
            {canRecalc && (
              <button
                onClick={handleRecalculate}
                disabled={recalculating}
                className="flex items-center gap-2 whitespace-nowrap px-4 py-2 bg-bb-primary hover:bg-bb-primary-h text-white
                           text-sm rounded-lg transition-all disabled:opacity-50 font-medium"
              >
                {recalculating ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
                재계산
              </button>
            )}
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
          <div className="mb-6">
            <SectionTitle>
              <AlertTriangle size={14} className="text-bb-text2" />
              함께 확인할 기록
            </SectionTitle>
            <div className="space-y-2">
              {/* 참여 기록 확인은 같은 안내가 사람 수만큼 반복되므로 한 줄로 묶고 중립 색으로 보여 준다(평가·감점 아님). */}
              {participationNotes.length > 0 && (
                <div className="flex items-start gap-3 p-4 border border-bb-border bg-bb-surface rounded-xl">
                  <AlertTriangle size={15} className="text-bb-text2 shrink-0 mt-0.5" />
                  <div>
                    <p className="text-xs font-semibold text-bb-text">참여 기록이 적은 팀원 {participationNotes.length}명 — 함께 확인해 주세요</p>
                    {/* 안내 문장은 서버가 경보에 담아 보낸 것(PARTICIPATION_NOTICE)을 그대로 쓴다. 화면이 같은 뜻의 문장을 따로 만들지 않는다 */}
                    <p className="text-xs text-bb-text2 mt-0.5">{participationNotes[0].message} 평가·감점 기준이 아닙니다.</p>
                  </div>
                </div>
              )}
              {otherAlerts.map((alert) => {
                const cfg = ALERT_CONFIG[alert.alertType] ?? ALERT_CONFIG.FREE_RIDE;
                const tone = ALERT_TONE[alert.severity] ?? ALERT_TONE.LOW;
                return (
                  <div key={alert.id} className={`flex items-start gap-3 p-4 border rounded-xl ${tone.bg}`}>
                    <AlertTriangle size={15} className={`${tone.color} shrink-0 mt-0.5`} />
                    <div>
                      <p className={`text-xs font-semibold ${tone.color}`}>{tone.label} · {cfg.label}</p>
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
            <span className="text-5xl mb-4">📊</span>
            <p className="text-sm font-semibold text-bb-text mb-1.5">아직 분석할 데이터가 없어요</p>
            <p className="text-xs text-bb-text2 mb-6">
              업무 완료, 회의 체크인, 파일 업로드 후 분석이 시작됩니다
            </p>
            {canRecalc ? (
              <button
                onClick={handleRecalculate}
                disabled={recalculating}
                className="flex items-center gap-2 px-4 py-2 bg-bb-primary hover:bg-bb-primary-h
                           text-white text-sm rounded-lg transition-all disabled:opacity-50"
              >
                {recalculating ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />}
                지금 계산하기
              </button>
            ) : (
              <p className="text-xs text-bb-text2">팀장·팀원이 계산하면 여기에 표시됩니다.</p>
            )}
          </div>
        ) : (
          <>
            {/* ── KPI 카드 4개 ────────────────────────────────────── */}
            <div className="grid grid-cols-4 gap-4 mb-6">
              <KpiCard
                label="팀원 수"
                value={scores.length}
                sub="명"
                icon={Users}
              />
              <KpiCard
                label="전체 참여"
                value={fullCount}
                sub={`${scores.length}명 중 ${fullCount}명`}
                icon={TrendingUp}
              />
              <KpiCard
                label="부분 참여"
                value={partialCount}
                sub="2~3개 항목 참여"
                icon={Zap}
              />
              <KpiCard
                label="집계 기록 적음"
                value={noneCount}
                sub="0~1개 항목 참여"
                icon={TrendingDown}
              />
            </div>

            {/* ── 참여 여부 테이블 ─────────────────────────────────── */}
            <section className="mb-6">
              <SectionTitle>
                <ListChecks size={13} />
                팀원별 항목 참여 여부
              </SectionTitle>

              <p className="mb-3 text-xs text-bb-text2">항목마다 했는지만 봅니다. 양과 질은 재지 않습니다.</p>
              <div className="bg-bb-surface border border-bb-border rounded-xl overflow-x-auto">
                <div role="table" aria-label="팀원별 항목 참여 여부" className="min-w-max">
                {/* 테이블 헤더 */}
                <div role="row" className="grid gap-0 border-b border-bb-border bg-bb-bg/60" style={{ gridTemplateColumns: gridCols }}>
                  <div role="columnheader" className="py-3 px-4 pl-5 text-left text-xs font-semibold text-bb-text2 uppercase tracking-wide">팀원</div>
                  {levelCols.map((col) => (
                    <div key={col.code} role="columnheader" title={col.description} className="py-3 px-4 text-center text-xs font-semibold text-bb-text2 uppercase tracking-wide">
                      {col.label}
                    </div>
                  ))}
                  {extraCols.map((col) => (
                    <div key={col.code} role="columnheader" title={col.description} className="py-3 px-4 text-center text-xs font-semibold text-bb-text2 tracking-wide">
                      {col.label}<span className="block font-normal">판정 제외</span>
                    </div>
                  ))}
                  <div role="columnheader" className="py-3 px-4 pr-5 text-center text-xs font-semibold text-bb-text2 uppercase tracking-wide">종합</div>
                </div>

                {/* 행 */}
                {sorted.map((s, idx) => {
                  const hue = s.name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360;
                  const isLast = idx === sorted.length - 1;
                  const level = levelItems(s), extra = extraItems(s);
                  return (
                    <div
                      key={s.userId}
                      role="row"
                      data-testid={`score-row-${s.userId}`}
                      style={{ gridTemplateColumns: gridCols }}
                      className={`grid items-center gap-0 ${
                        !isLast ? "border-b border-bb-border" : ""
                      } hover:bg-bb-bg/30 transition-colors`}
                    >
                      {/* 팀원 */}
                      <div role="cell" className="flex items-center gap-3 pl-5 py-4 pr-4">
                        <div
                          style={{ background: `hsl(${hue} 50% 45%)` }}
                          className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0"
                        >
                          {s.name.slice(0, 2).toUpperCase()}
                        </div>
                        <div>
                          <p className="text-sm font-semibold text-bb-text">{s.name}</p>
                          <p className="text-xs text-bb-text2">{s.email}</p>
                        </div>
                      </div>

                      {/* 판정 항목: 충족 여부와 횟수 */}
                      {levelCols.map((col) => {
                        const item = level.find((i) => i.code === col.code);
                        const Icon = ITEM_ICON[col.code];
                        const text = item ? countText(item) : null;
                        return (
                          <div key={col.code} role="cell" title={col.description} className="py-4 px-4 text-center">
                            {item && noTarget(item) ? (
                              // 확인할 대상이 없으면 미충족(✗)으로 보이지 않는다(PDF와 같음)
                              <span className="text-xs text-bb-text2">{text}</span>
                            ) : item ? (
                              <div className="flex flex-col items-center gap-1">
                                <ParticipationCell participated={item.passed} />
                                {text ? <span className="text-xs text-bb-text2">{text}</span> : Icon && <Icon size={11} className="text-bb-text2" />}
                              </div>
                            ) : <span className="text-xs text-bb-text2">–</span>}
                          </div>
                        );
                      })}

                      {/* 판정에 넣지 않는 항목: 횟수만 */}
                      {extraCols.map((col) => {
                        const item = extra.find((i) => i.code === col.code);
                        return (
                          <div key={col.code} role="cell" title={col.description} className="py-4 px-4 text-center text-sm font-semibold text-bb-text">
                            {item ? countText(item) : <span className="text-xs font-normal text-bb-text2">–</span>}
                          </div>
                        );
                      })}

                      {/* 종합 */}
                      <div role="cell" className="py-4 px-5 text-center">
                        <LevelBadge level={s.participationLevel} />
                      </div>
                    </div>
                  );
                })}
                </div>
              </div>

              {/* 판정 기준 안내: 응답의 설명을 그대로 쓴다 */}
              <ul className="mt-3 flex flex-wrap gap-x-6 gap-y-1 text-xs text-bb-text2" aria-label="항목 설명">
                {[...levelCols, ...extraCols].map((col) => (
                  <li key={col.code}>{col.label}: {col.description}</li>
                ))}
              </ul>
              <div className="mt-1 flex flex-wrap gap-x-6 gap-y-1 text-xs text-bb-text2">
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-teal-400 inline-block" />전체참여: 판정 항목 4개 모두
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-yellow-400 inline-block" />부분참여: 2~3개
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-slate-400 inline-block" />집계 기록 적음: 0~1개
                </span>
              </div>
              {(criteria || lastCalc) && (
                <p className="mt-1 text-xs text-bb-text2">
                  {criteria && `계산 기준 ${criteria}`}{criteria && lastCalc && " · "}{lastCalc && `마지막 계산 ${new Date(lastCalc).toLocaleString("ko-KR")}`}
                </p>
              )}
            </section>

            {/* ── 검토·피드백 참여 (K-40: 판정에 넣지 않고 횟수와 세부만). 제목·설명은 서버 항목의 label·description ── */}
            {reviewRows.length > 0 && (
              <section className="mb-6" aria-label={reviewItem(reviewRows[0])?.label}>
                <SectionTitle>
                  <MessageSquareText size={13} />
                  {reviewItem(reviewRows[0])?.label}
                </SectionTitle>
                <p className="mb-3 text-xs text-bb-text2">{reviewItem(reviewRows[0])?.description}</p>
                <div className="grid gap-3 sm:grid-cols-2">
                  {reviewRows.map((s) => {
                    const r = reviewItem(s)!;
                    return (
                      <div key={s.userId} data-testid={`review-${s.userId}`} className="bg-bb-surface border border-bb-border rounded-xl p-4">
                        <div className="flex items-baseline justify-between gap-2">
                          <p className="text-sm font-semibold text-bb-text">{s.name}</p>
                          <p className="text-sm font-semibold text-bb-text">{r.count}건</p>
                        </div>
                        {r.details && r.details.length > 0 && (
                          <ul className="mt-2 flex flex-wrap gap-1.5">
                            {r.details.map((d) => (
                              <li key={d.code} className={`text-xs px-2 py-0.5 rounded-full border border-bb-border ${d.count > 0 ? "text-bb-text" : "text-bb-text2 opacity-60"}`}>
                                {d.label} {d.count}
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    );
                  })}
                </div>
              </section>
            )}

            {/* ── 마감 위험도 게이지 ───────────────────────────────── */}
            {risk && (
              <section className="mb-6">
                <SectionTitle>
                  <AlertTriangle size={13} />
                  마감 위험도
                </SectionTitle>
                <div className="grid grid-cols-2 gap-4">
                  {/* 게이지 */}
                  <div className="bg-bb-surface border border-bb-border rounded-xl p-5 flex items-center gap-6">
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
                  <div className="bg-bb-surface border border-bb-border rounded-xl p-5 space-y-3">
                    <StatRow label="완료율" value={`${risk.completionRate}%`} />
                    <StatRow label="완료 업무" value={`${risk.doneTasks} / ${risk.totalTasks}개`} />
                    <StatRow label="지연 업무" value={`${risk.overdueTasks}개`} />
                    <StatRow label="남은 기간" value={risk.daysRemaining != null ? `${risk.daysRemaining}일` : "-"} />
                    {risk.reasons.length > 0 && (
                      <div className="pt-2 border-t border-bb-border">
                        {risk.reasons.map((r, i) => (
                          <p key={i} className="text-xs text-amber-400 mt-1">• {r}</p>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </section>
            )}

            {/* ── 팀원별 업무 완료 현황 ───────────────────────────────── */}
            {tasks.length > 0 && scores.length > 0 && (
              <section className="mb-6">
                <SectionTitle>
                  <CheckSquare size={14} className="text-indigo-400" />
                  팀원별 업무 완료 현황
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
                              <span className="text-xs text-teal-400">✅ {done.length}완료</span>
                              <span className="text-xs text-indigo-400">🔄 {inProgress.length}진행</span>
                              <span className="text-xs text-bb-text2">⬜ {todo.length}예정</span>
                            </div>
                          </div>
                          <div className="text-right shrink-0">
                            <p className={`text-xl font-bold ${
                              pct === 100 ? "text-teal-400" : pct >= 50 ? "text-indigo-400" : "text-bb-text2"
                            }`}>{myTasks.length > 0 ? `${pct}%` : "-"}</p>
                            <p className="text-xs text-bb-text2">완료율</p>
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
                          <p className="text-xs text-bb-text2 italic">배정된 업무가 없습니다</p>
                        ) : (
                          <div className="space-y-1.5">
                            {myTasks.map((task) => {
                              // 마감일이 지난 뒤부터 지연. 마감 당일은 지연이 아니다
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
                                    <span className={`text-xs shrink-0 ${task.status !== "DONE" ? DUE_TEXT[dueTone(task.dueDate)] : "text-bb-text2"}`}>
                                      {shortDate(task.dueDate)}{task.status !== "DONE" && ` · ${dueLabel(task.dueDate)}`}
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
  return level === "CRITICAL" ? "#DC2626"
       : level === "HIGH"     ? "#EF4444"
       : level === "MEDIUM"   ? "#CA8A04"
       :                        "#3B82F6";
}

function riskLevelKo(level: RiskData["level"]) {
  return level === "CRITICAL" ? "위험 · 즉시 확인"
       : level === "HIGH"     ? "높음 · 긴급"
       : level === "MEDIUM"   ? "보통 · 주의"
       :                        "낮음 · 안정";
}
