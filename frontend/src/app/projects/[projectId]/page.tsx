"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import { Task } from "@/types/task";
import { Meeting } from "@/types/meeting";
import { FileRecord, ScoreEntry, Alert } from "@/types/vault";
import {
  Kanban, FileText, Files, BarChart2,
  Copy, Check, AlertTriangle, Users,
  Calendar, Clock, ChevronRight, Loader2,
  AlertCircle, TrendingUp, Hash,
} from "lucide-react";

// ── 타입 ─────────────────────────────────────────────────────────────────
interface ProjectDetail {
  id: string;
  name: string;
  description: string | null;
  courseName: string | null;
  semester: string | null;
  startDate: string | null;
  endDate: string | null;
  inviteCode: string;
  memberCount: number;
  myRole: string | null;
}

interface Member {
  userId: string;
  memberId: string;
  name: string;
  email: string;
  role: string;
}

// ── 날짜 포맷 ────────────────────────────────────────────────────────────
function fmtDate(iso: string | null, opts?: Intl.DateTimeFormatOptions) {
  if (!iso) return "-";
  return new Date(iso).toLocaleDateString("ko-KR", opts ?? { month: "short", day: "numeric" });
}
function fmtDateTime(iso: string | null) {
  if (!iso) return "날짜 미정";
  return new Date(iso).toLocaleDateString("ko-KR", {
    month: "short", day: "numeric", hour: "2-digit", minute: "2-digit",
  });
}

// ── 건강도 계산 ──────────────────────────────────────────────────────────
function getHealth(alerts: Alert[], tasks: Task[]) {
  const hasCritical = alerts.some((a) => a.alertType === "TAMPER" || a.alertType === "DROPOUT");
  const hasWarning  = alerts.some((a) => a.alertType === "FREE_RIDE" || a.alertType === "OVERLOAD");
  const done        = tasks.filter((t) => t.status === "DONE").length;
  const rate        = tasks.length > 0 ? done / tasks.length : 1;

  if (hasCritical)            return { dot: "🔴", label: "위험",  cls: "text-red-400 bg-red-500/10 border-red-500/25" };
  if (hasWarning || rate < 0.3) return { dot: "🟠", label: "주의",  cls: "text-orange-400 bg-orange-500/10 border-orange-500/25" };
  if (rate < 0.65)            return { dot: "🟡", label: "보통",  cls: "text-yellow-400 bg-yellow-500/10 border-yellow-500/25" };
  return                             { dot: "🟢", label: "정상",  cls: "text-green-400 bg-green-500/10 border-green-500/25" };
}

// ── 멤버 아바타 ──────────────────────────────────────────────────────────
function Avatar({ name, size = 8 }: { name: string; size?: number }) {
  const hue = name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360;
  const cls = `w-${size} h-${size} rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0 ring-2 ring-bb-bg`;
  return (
    <div className={cls} style={{ background: `hsl(${hue} 55% 42%)` }}>
      {name.slice(0, 2).toUpperCase()}
    </div>
  );
}

// ── KPI 카드 ─────────────────────────────────────────────────────────────
function KpiCard({
  icon: Icon, label, value, sub, color,
}: {
  icon: React.ElementType; label: string; value: string | number; sub?: string; color: string;
}) {
  return (
    <div className="bg-bb-surface border border-bb-border rounded-xl p-5">
      <div className="flex items-start justify-between mb-3">
        <span className="text-xs font-medium text-bb-text2">{label}</span>
        <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${color}`}>
          <Icon size={15} />
        </div>
      </div>
      <p className="text-2xl font-bold text-bb-text">{value}</p>
      {sub && <p className="text-xs text-bb-text2 mt-1">{sub}</p>}
    </div>
  );
}

// ── 태스크 상태 배지 ─────────────────────────────────────────────────────
function StatusBadge({ status }: { status: Task["status"] }) {
  const cfg = {
    TODO:        { label: "할 일",  cls: "bg-slate-700 text-slate-400" },
    IN_PROGRESS: { label: "진행 중", cls: "bg-indigo-500/20 text-indigo-400" },
    DONE:        { label: "완료",   cls: "bg-teal-500/15 text-teal-400" },
  }[status];
  return (
    <span className={`text-[10px] font-medium px-2 py-0.5 rounded-full ${cfg.cls}`}>
      {cfg.label}
    </span>
  );
}

// ── 섹션 헤더 ────────────────────────────────────────────────────────────
function SectionHeader({
  title, icon: Icon, href,
}: {
  title: string; icon: React.ElementType; href: string;
}) {
  return (
    <div className="flex items-center justify-between mb-4">
      <h2 className="text-base font-semibold text-bb-text flex items-center gap-2">
        <Icon size={16} className="text-bb-primary" />
        {title}
      </h2>
      <Link
        href={href}
        className="flex items-center gap-1 text-xs text-bb-text2 hover:text-bb-primary transition-colors"
      >
        전체 보기 <ChevronRight size={13} />
      </Link>
    </div>
  );
}

// ── 메인 ─────────────────────────────────────────────────────────────────
export default function ProjectHomePage() {
  const params    = useParams();
  const router    = useRouter();
  const projectId = params?.projectId as string;

  const [project,  setProject]  = useState<ProjectDetail | null>(null);
  const [tasks,    setTasks]    = useState<Task[]>([]);
  const [meetings, setMeetings] = useState<Meeting[]>([]);
  const [files,    setFiles]    = useState<FileRecord[]>([]);
  const [scores,   setScores]   = useState<ScoreEntry[]>([]);
  const [alerts,   setAlerts]   = useState<Alert[]>([]);
  const [members,  setMembers]  = useState<Member[]>([]);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState("");

  // 초대코드 복사 토스트
  const [copied, setCopied] = useState(false);

  const fetchAll = useCallback(async () => {
    if (!localStorage.getItem("accessToken")) { router.replace("/login"); return; }
    setLoading(true);
    setError("");
    try {
      const [projRes, taskRes, meetRes, fileRes, scoreRes, alertRes, memberRes] = await Promise.all([
        api.get<ProjectDetail>(`/projects/${projectId}`),
        api.get<Task[]>(`/projects/${projectId}/tasks`),
        api.get<Meeting[]>(`/projects/${projectId}/meetings`),
        api.get<FileRecord[]>(`/projects/${projectId}/files`).catch(() => ({ data: [] as FileRecord[] })),
        api.get<ScoreEntry[]>(`/projects/${projectId}/scores`).catch(() => ({ data: [] as ScoreEntry[] })),
        api.get<Alert[]>(`/projects/${projectId}/alerts`).catch(() => ({ data: [] as Alert[] })),
        api.get<Member[]>(`/projects/${projectId}/members`).catch(() => ({ data: [] as Member[] })),
      ]);
      setProject(projRes.data);
      setTasks(taskRes.data);
      setMeetings(meetRes.data);
      setFiles(fileRes.data);
      setScores(scoreRes.data);
      setAlerts(alertRes.data);
      setMembers(memberRes.data);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401) { router.replace("/login"); return; }
      if (status === 403 || status === 404) { router.replace("/dashboard"); return; }
      setError("프로젝트 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [projectId, router]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  const copyInviteCode = async () => {
    if (!project) return;
    try {
      await navigator.clipboard.writeText(project.inviteCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  };

  // ── 로딩 스켈레톤 ──────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="min-h-screen bg-bb-bg">
        <Sidebar />
        <main className="ml-64 min-h-screen p-8">
          <div className="animate-pulse space-y-4 max-w-5xl">
            <div className="h-8 bg-bb-surface rounded w-64 mb-2" />
            <div className="h-4 bg-bb-surface rounded w-40 mb-8" />
            <div className="grid grid-cols-4 gap-4">
              {[...Array(4)].map((_, i) => <div key={i} className="h-28 bg-bb-surface rounded-xl" />)}
            </div>
            <div className="grid grid-cols-2 gap-6 mt-6">
              <div className="h-64 bg-bb-surface rounded-xl" />
              <div className="h-64 bg-bb-surface rounded-xl" />
            </div>
          </div>
        </main>
      </div>
    );
  }

  if (error || !project) {
    return (
      <div className="min-h-screen bg-bb-bg">
        <Sidebar />
        <main className="ml-64 min-h-screen p-8 flex items-center justify-center">
          <div className="text-center">
            <AlertCircle size={32} className="text-red-400 mx-auto mb-3" />
            <p className="text-sm text-red-400">{error || "프로젝트를 찾을 수 없습니다"}</p>
            <button
              onClick={() => router.push("/dashboard")}
              className="mt-4 text-xs text-bb-text2 hover:text-bb-text underline"
            >
              대시보드로 돌아가기
            </button>
          </div>
        </main>
      </div>
    );
  }

  // ── 집계 ─────────────────────────────────────────────────────────────
  const todoCount  = tasks.filter((t) => t.status === "TODO").length;
  const progCount  = tasks.filter((t) => t.status === "IN_PROGRESS").length;
  const doneCount  = tasks.filter((t) => t.status === "DONE").length;
  const totalTasks = tasks.length;
  const doneRate   = totalTasks > 0 ? Math.round((doneCount / totalTasks) * 100) : 0;

  const avgScore   = scores.length > 0
    ? Math.round(scores.reduce((s, e) => s + e.totalScore, 0) / scores.length)
    : 0;
  const maxScore   = scores.length > 0 ? Math.max(...scores.map((s) => s.totalScore)) : 1;

  const health     = getHealth(alerts, tasks);
  const recentTasks    = [...tasks].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 3);
  const recentMeetings = meetings.slice(0, 3);
  const recentFiles    = files.slice(0, 3);
  const activeAlerts   = alerts.filter((a) => !a.isRead);

  const ALERT_LABEL: Record<string, string> = {
    FREE_RIDE: "무임승차 의심",
    OVERLOAD:  "과부하 위험",
    DROPOUT:   "이탈 감지",
    TAMPER:    "파일 변조",
  };

  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar />

      <main className="ml-64 min-h-screen p-8">
        <div className="max-w-5xl">

          {/* ── 프로젝트 헤더 ───────────────────────────────────────────── */}
          <div className="bg-bb-surface border border-bb-border rounded-2xl p-6 mb-6">
            <div className="flex items-start justify-between gap-4 flex-wrap">
              {/* 제목 + 메타 */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-3 flex-wrap mb-1">
                  <h1 className="text-2xl font-bold text-bb-text truncate">{project.name}</h1>
                  {/* 건강도 배지 */}
                  <span className={`flex items-center gap-1.5 text-xs font-semibold px-3 py-1 rounded-full border ${health.cls}`}>
                    {health.dot} {health.label}
                  </span>
                </div>
                <div className="flex flex-wrap items-center gap-3 text-xs text-bb-text2 mt-1">
                  {project.courseName && <span>{project.courseName}</span>}
                  {project.semester   && <span>· {project.semester}</span>}
                  {project.endDate    && (
                    <span className="flex items-center gap-1">
                      · <Calendar size={11} /> 마감 {fmtDate(project.endDate, { year: "numeric", month: "long", day: "numeric" })}
                    </span>
                  )}
                  {project.myRole && (
                    <span className="px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 font-medium">
                      {project.myRole}
                    </span>
                  )}
                </div>
                {project.description && (
                  <p className="text-sm text-bb-text2 mt-2 leading-relaxed">{project.description}</p>
                )}
              </div>

              {/* 우측: 초대코드 + 멤버 아바타 */}
              <div className="flex flex-col items-end gap-3 shrink-0">
                {/* 초대코드 복사 버튼 */}
                <button
                  onClick={copyInviteCode}
                  className="flex items-center gap-2 px-3 py-2 bg-bb-bg border border-bb-border
                             hover:border-indigo-500/40 hover:bg-indigo-500/5
                             rounded-xl text-xs text-bb-text2 hover:text-bb-text transition-all group"
                >
                  <Hash size={12} className="text-bb-text2 group-hover:text-indigo-400 transition-colors" />
                  <span className="font-mono font-semibold tracking-widest text-bb-text">
                    {project.inviteCode}
                  </span>
                  {copied
                    ? <Check size={13} className="text-green-400" />
                    : <Copy size={13} className="text-bb-text2 group-hover:text-indigo-400 transition-colors" />
                  }
                </button>

                {/* 멤버 아바타 스택 */}
                {members.length > 0 && (
                  <div className="flex items-center">
                    <div className="flex -space-x-2">
                      {members.slice(0, 5).map((m) => (
                        <Avatar key={m.userId} name={m.name} size={8} />
                      ))}
                    </div>
                    {members.length > 5 && (
                      <span className="ml-2 text-xs text-bb-text2">+{members.length - 5}</span>
                    )}
                    <span className="ml-2 text-xs text-bb-text2">{members.length}명</span>
                  </div>
                )}
              </div>
            </div>

            {/* 복사 성공 토스트 인라인 */}
            {copied && (
              <div className="mt-3 flex items-center gap-2 text-xs text-green-400">
                <Check size={12} />
                초대 코드가 클립보드에 복사됐습니다
              </div>
            )}
          </div>

          {/* ── KPI 4개 ──────────────────────────────────────────────────── */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
            <KpiCard
              icon={Kanban}
              label="태스크 완료율"
              value={`${doneRate}%`}
              sub={`${doneCount} / ${totalTasks}개 완료`}
              color="bg-indigo-500/10 text-indigo-400"
            />
            <KpiCard
              icon={FileText}
              label="전체 회의"
              value={meetings.length}
              sub={meetings.length > 0 ? `최근: ${fmtDate(meetings[0]?.meetingDate)}` : "아직 없음"}
              color="bg-teal-500/10 text-teal-400"
            />
            <KpiCard
              icon={Files}
              label="업로드 파일"
              value={files.length}
              sub={files.length > 0 ? `최근: ${files[0]?.fileName}` : "아직 없음"}
              color="bg-purple-500/10 text-purple-400"
            />
            <KpiCard
              icon={BarChart2}
              label="평균 기여도"
              value={scores.length > 0 ? avgScore : "-"}
              sub={scores.length > 0 ? `${scores.length}명 기준` : "점수 없음"}
              color="bg-amber-500/10 text-amber-400"
            />
          </div>

          {/* ── 두 열 그리드: 태스크 + 회의 ─────────────────────────────── */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">

            {/* 태스크 현황 */}
            <div className="bg-bb-surface border border-bb-border rounded-xl p-5">
              <SectionHeader
                title="태스크 현황"
                icon={Kanban}
                href={`/projects/${projectId}/board`}
              />

              {/* 상태별 카운트 */}
              <div className="grid grid-cols-3 gap-2 mb-5">
                {[
                  { label: "할 일",   count: todoCount,  dot: "bg-slate-500",  text: "text-slate-400" },
                  { label: "진행 중", count: progCount,  dot: "bg-indigo-500", text: "text-indigo-400" },
                  { label: "완료",   count: doneCount,  dot: "bg-teal-400",   text: "text-teal-400" },
                ].map(({ label, count, dot, text }) => (
                  <div key={label} className="bg-bb-bg rounded-xl p-3 text-center">
                    <div className="flex items-center justify-center gap-1.5 mb-1">
                      <div className={`w-2 h-2 rounded-full ${dot}`} />
                      <span className="text-[11px] text-bb-text2">{label}</span>
                    </div>
                    <p className={`text-xl font-bold ${text}`}>{count}</p>
                  </div>
                ))}
              </div>

              {/* 최근 태스크 */}
              {recentTasks.length === 0 ? (
                <p className="text-xs text-bb-text2 text-center py-4">등록된 태스크가 없습니다</p>
              ) : (
                <div className="space-y-2">
                  {recentTasks.map((t) => (
                    <div
                      key={t.id}
                      className="flex items-center gap-3 p-2.5 rounded-lg bg-bb-bg/50 hover:bg-bb-bg transition-colors"
                    >
                      <div className="flex-1 min-w-0">
                        <p className="text-sm text-bb-text truncate">{t.title}</p>
                        {t.assignees.length > 0 && (
                          <p className="text-[11px] text-bb-text2 mt-0.5 truncate">
                            👤 {t.assignees.map((a) => a.name).join(", ")}
                          </p>
                        )}
                      </div>
                      <StatusBadge status={t.status} />
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 최근 회의 */}
            <div className="bg-bb-surface border border-bb-border rounded-xl p-5">
              <SectionHeader
                title="최근 회의"
                icon={FileText}
                href={`/projects/${projectId}/meetings`}
              />

              {recentMeetings.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-8 text-center">
                  <FileText size={28} className="text-slate-600 mb-2" />
                  <p className="text-xs text-bb-text2">아직 회의가 없습니다</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {recentMeetings.map((m) => (
                    <Link
                      key={m.id}
                      href={`/projects/${projectId}/meetings/${m.id}`}
                      className="flex items-center gap-3 p-2.5 rounded-lg bg-bb-bg/50
                                 hover:bg-bb-bg transition-colors group"
                    >
                      <div className="w-8 h-8 rounded-lg bg-indigo-500/10 flex items-center justify-center shrink-0
                                      group-hover:bg-indigo-500/20 transition-colors">
                        <FileText size={14} className="text-indigo-400" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-bb-text truncate">{m.title}</p>
                        <p className="text-[11px] text-bb-text2 mt-0.5">
                          {fmtDateTime(m.meetingDate)}
                        </p>
                      </div>
                      {/* Notion 동기화 배지 */}
                      {m.notionPageId && (
                        <span className="flex items-center gap-1 text-[9px] font-medium px-1.5 py-0.5
                                         rounded-full bg-slate-800 border border-slate-600 text-slate-400 shrink-0">
                          <span className="font-bold bg-white text-[#191919] px-0.5 rounded text-[7px]">N</span>
                          동기화
                        </span>
                      )}
                    </Link>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* ── 기여도 현황 ──────────────────────────────────────────────── */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-5 mb-6">
            <SectionHeader
              title="기여도 현황"
              icon={BarChart2}
              href={`/projects/${projectId}/analytics`}
            />

            {scores.length === 0 ? (
              <p className="text-xs text-bb-text2 text-center py-4">
                아직 기여도 점수가 없습니다. 태스크를 완료하면 자동 계산됩니다.
              </p>
            ) : (
              <div className="space-y-3">
                {[...scores]
                  .sort((a, b) => b.totalScore - a.totalScore)
                  .map((s) => {
                    const pct = maxScore > 0 ? (s.totalScore / maxScore) * 100 : 0;
                    const memberAlert = activeAlerts.find((a) => a.userId === s.userId);
                    return (
                      <div key={s.userId} className="flex items-center gap-3">
                        <Avatar name={s.name} size={7} />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between mb-1">
                            <span className="text-xs font-medium text-bb-text truncate">{s.name}</span>
                            <div className="flex items-center gap-2 shrink-0 ml-2">
                              {memberAlert && (
                                <span className="flex items-center gap-1 text-[10px] text-amber-400 bg-amber-500/10
                                                 border border-amber-500/20 px-2 py-0.5 rounded-full">
                                  <AlertTriangle size={9} />
                                  {ALERT_LABEL[memberAlert.alertType] ?? memberAlert.alertType}
                                </span>
                              )}
                              <span className="text-xs font-bold text-bb-text">
                                {Math.round(s.totalScore)}점
                              </span>
                            </div>
                          </div>
                          <div className="h-1.5 bg-bb-bg rounded-full overflow-hidden">
                            <div
                              className="h-full rounded-full bg-gradient-to-r from-indigo-500 to-teal-400 transition-all duration-500"
                              style={{ width: `${pct}%` }}
                            />
                          </div>
                        </div>
                      </div>
                    );
                  })}
              </div>
            )}

            {/* 프로젝트 전체 경보 (userId=null) */}
            {activeAlerts.filter((a) => !a.userId).map((a) => (
              <div key={a.id} className="mt-3 flex items-center gap-2 p-3 bg-red-500/5 border border-red-500/20
                                         rounded-lg text-xs text-red-400">
                <AlertTriangle size={13} />
                {a.message}
              </div>
            ))}
          </div>

          {/* ── Hash Vault ───────────────────────────────────────────────── */}
          <div className="bg-bb-surface border border-bb-border rounded-xl p-5">
            <SectionHeader
              title="Hash Vault"
              icon={Files}
              href={`/projects/${projectId}/vault`}
            />

            {recentFiles.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-8 text-center">
                <Files size={28} className="text-slate-600 mb-2" />
                <p className="text-xs text-bb-text2">업로드된 파일이 없습니다</p>
              </div>
            ) : (
              <div className="space-y-2">
                {recentFiles.map((f) => (
                  <div
                    key={f.id}
                    className="flex items-center gap-3 p-2.5 rounded-lg bg-bb-bg/50"
                  >
                    <div className="w-8 h-8 rounded-lg bg-purple-500/10 flex items-center justify-center shrink-0">
                      <Files size={14} className="text-purple-400" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm text-bb-text truncate">{f.fileName}</p>
                      <p className="text-[11px] text-bb-text2 mt-0.5">
                        {f.uploaderName} · {fmtDateTime(f.uploadedAt)}
                      </p>
                    </div>
                    <span className="text-[10px] text-bb-text2 bg-bb-bg px-2 py-0.5 rounded-full shrink-0">
                      v{f.version}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>

        </div>
      </main>
    </div>
  );
}
