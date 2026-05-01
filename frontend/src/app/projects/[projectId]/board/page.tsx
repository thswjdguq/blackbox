"use client";

import { useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import KanbanBoard from "@/components/kanban/KanbanBoard";
import { Task, ScoreMap } from "@/types/task";
import api from "@/lib/api";
import {
  RefreshCw,
  ChevronDown,
  FolderKanban,
  AlertCircle,
  ExternalLink,
  Loader2,
  SlidersHorizontal,
  X,
} from "lucide-react";
import { KanbanFilter } from "@/components/kanban/KanbanBoard";
import { TaskPriority } from "@/types/task";

interface Member {
  userId: string;
  memberId: string;
  name: string;
  email: string;
  role: string;
}

interface ProjectInfo {
  id: string;
  name: string;
  courseName: string | null;
  semester: string | null;
}

interface ScoreEntry {
  userId: string;
  totalScore: number;
}

// ── Small stats banner ──────────────────────────────────────────────────────
function StatPill({ label, value, accent }: { label: string; value: number; accent: string }) {
  return (
    <div className={`flex items-center gap-2 px-3 py-1.5 rounded-lg bg-bb-surface border border-bb-border`}>
      <div className={`w-2 h-2 rounded-full ${accent}`} />
      <span className="text-xs text-bb-text2">{label}</span>
      <span className="text-sm font-bold text-bb-text">{value}</span>
    </div>
  );
}

export default function BoardPage() {
  const params = useParams();
  const router = useRouter();
  const projectId = params?.projectId as string;

  const [project, setProject] = useState<ProjectInfo | null>(null);
  const [allProjects, setAllProjects] = useState<ProjectInfo[]>([]);
  const [showProjectDropdown, setShowProjectDropdown] = useState(false);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [scoreMap, setScoreMap] = useState<ScoreMap>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshing, setRefreshing] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [notionUrl, setNotionUrl] = useState<string | null>(null);

  // ── 필터 ──────────────────────────────────────────────────────────────────
  const [filter, setFilter] = useState<KanbanFilter>({ assigneeId: null, priority: null, tag: "" });
  const isFiltered = filter.assigneeId !== null || filter.priority !== null || filter.tag !== "";
  const clearFilter = () => setFilter({ assigneeId: null, priority: null, tag: "" });

  useEffect(() => {
    if (!projectId) {
      router.replace("/dashboard");
      return;
    }
    const token = localStorage.getItem("accessToken");
    if (!token) { router.replace("/login"); return; }
    bootstrap();
    // 참여 중인 프로젝트 목록 로드
    api.get<ProjectInfo[]>("/projects").then(({ data }) => setAllProjects(data)).catch(() => {});
  }, [projectId]);

  async function bootstrap() {
    setLoading(true);
    setError("");
    try {
      const [projRes, taskRes, memberRes] = await Promise.all([
        api.get<ProjectInfo>(`/projects/${projectId}`),
        api.get<Task[]>(`/projects/${projectId}/tasks`),
        api.get<Member[]>(`/projects/${projectId}/members`),
      ]);
      setProject(projRes.data);
      setTasks(taskRes.data);
      setMembers(memberRes.data);

      // Scores (may not exist yet — gracefully ignore 404)
      try {
        const scoreRes = await api.get<ScoreEntry[]>(`/projects/${projectId}/scores`);
        const map: ScoreMap = {};
        scoreRes.data.forEach((s) => { map[s.userId] = Number(s.totalScore); });
        setScoreMap(map);
      } catch {
        // No scores yet — leave scoreMap empty
      }
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401) { router.replace("/login"); return; }
      setError("프로젝트 데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleRefreshScores() {
    setRefreshing(true);
    try {
      const res = await api.post<ScoreEntry[]>(`/projects/${projectId}/scores/recalculate`);
      const map: ScoreMap = {};
      res.data.forEach((s) => { map[s.userId] = Number(s.totalScore); });
      setScoreMap(map);
    } catch { /* silent */ }
    finally { setRefreshing(false); }
  }

  async function handleNotionSync() {
    setSyncing(true);
    setNotionUrl(null);
    try {
      const res = await api.post<{ pageUrl: string; taskCount: number; message: string }>(
        `/projects/${projectId}/tasks/notion/sync`
      );
      setNotionUrl(res.data.pageUrl);
    } catch {
      setError("Notion 동기화 실패 — Notion 페이지에 Integration 연결이 되어있는지 확인해주세요 (페이지 → ... → Connections).");
    } finally {
      setSyncing(false);
    }
  }

  // Stats
  const todoCount = tasks.filter((t) => t.status === "TODO").length;
  const inProgressCount = tasks.filter((t) => t.status === "IN_PROGRESS").length;
  const doneCount = tasks.filter((t) => t.status === "DONE").length;
  const completionPct = tasks.length > 0
    ? Math.round((doneCount / tasks.length) * 100)
    : 0;

  // ── Loading skeleton ────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="min-h-screen bg-bb-bg">
        <Sidebar />
        <main className="ml-64 min-h-screen p-8">
          <div className="animate-pulse space-y-6">
            <div className="h-7 bg-bb-surface rounded-lg w-48" />
            <div className="grid grid-cols-3 gap-6">
              {[0, 1, 2].map((i) => (
                <div key={i} className="bg-bb-surface rounded-xl p-4 h-64" />
              ))}
            </div>
          </div>
        </main>
      </div>
    );
  }

  // ── Error state ─────────────────────────────────────────────────────────
  if (error) {
    return (
      <div className="min-h-screen bg-bb-bg">
        <Sidebar />
        <main className="ml-64 min-h-screen p-8 flex items-center justify-center">
          <div className="text-center">
            <AlertCircle size={40} className="text-rose-400 mx-auto mb-4" />
            <p className="text-bb-text text-sm mb-4">{error}</p>
            <button
              onClick={bootstrap}
              className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-500 transition-colors"
            >
              다시 시도
            </button>
          </div>
        </main>
      </div>
    );
  }

  // ── Main render ─────────────────────────────────────────────────────────
  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar />

      <main className="ml-64 min-h-screen flex flex-col">
        {/* Top bar with gradient accent */}
        <div className="relative px-8 pt-8 pb-6 border-b border-slate-800 overflow-hidden">
          {/* Decorative gradient */}
          <div className="absolute inset-0 bg-gradient-to-r from-indigo-600/5 via-transparent to-teal-400/5 pointer-events-none" />

          <div className="relative flex items-start justify-between">
            <div>
              {/* Breadcrumb */}
              <div className="flex items-center gap-2 text-xs text-bb-text2 mb-2">
                <FolderKanban size={12} />
                <span
                  className="hover:text-bb-text cursor-pointer transition-colors"
                  onClick={() => router.push("/dashboard")}
                >
                  대시보드
                </span>
                <ChevronDown size={10} className="-rotate-90" />
                <span className="text-bb-text2">칸반 보드</span>
              </div>

              {/* 프로젝트 선택 드롭다운 */}
              <div className="relative">
                <button
                  onClick={() => setShowProjectDropdown((v) => !v)}
                  className="flex items-center gap-2 group"
                >
                  <h1 className="text-xl font-bold text-bb-text group-hover:text-indigo-400 transition-colors">
                    {project?.name ?? "프로젝트"}
                  </h1>
                  <ChevronDown
                    size={16}
                    className={`text-bb-text2 transition-transform ${showProjectDropdown ? "rotate-180" : ""}`}
                  />
                </button>

                {showProjectDropdown && allProjects.length > 0 && (
                  <div className="absolute top-full left-0 mt-1 z-30 w-64 bg-bb-surface border border-bb-border rounded-xl shadow-xl overflow-hidden">
                    <div className="px-3 py-2 border-b border-bb-border">
                      <p className="text-[11px] text-bb-text2 font-medium">참여 중인 프로젝트</p>
                    </div>
                    <div className="max-h-60 overflow-y-auto">
                      {allProjects.map((p) => (
                        <button
                          key={p.id}
                          onClick={() => {
                            setShowProjectDropdown(false);
                            router.push(`/projects/${p.id}/board`);
                          }}
                          className={`w-full flex items-center gap-3 px-3 py-2.5 text-left hover:bg-bb-surface2 transition-colors ${
                            p.id === projectId ? "bg-indigo-500/10" : ""
                          }`}
                        >
                          <div className="w-6 h-6 rounded-lg bg-indigo-500/20 flex items-center justify-center shrink-0">
                            <FolderKanban size={12} className="text-indigo-400" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <p className={`text-sm truncate ${p.id === projectId ? "text-indigo-300 font-medium" : "text-bb-text"}`}>
                              {p.name}
                            </p>
                            {p.courseName && (
                              <p className="text-[11px] text-bb-text2 truncate">{p.courseName}</p>
                            )}
                          </div>
                          {p.id === projectId && (
                            <span className="text-[10px] text-indigo-400 shrink-0">현재</span>
                          )}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {project?.courseName && (
                <p className="text-xs text-bb-text2 mt-1">
                  {project.courseName}
                  {project.semester && ` · ${project.semester}`}
                </p>
              )}
            </div>

            {/* Stats + refresh */}
            <div className="flex items-center gap-3">
              <StatPill label="할 일" value={todoCount} accent="bg-slate-500" />
              <StatPill label="진행 중" value={inProgressCount} accent="bg-indigo-500" />
              <StatPill label="완료" value={doneCount} accent="bg-teal-400" />

              {/* Progress bar */}
              <div className="flex flex-col items-end gap-1">
                <span className="text-xs text-bb-text2">
                  완료율 <span className="text-teal-400 font-bold">{completionPct}%</span>
                </span>
                <div className="w-24 h-1.5 bg-slate-700 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-indigo-500 to-teal-400 rounded-full transition-all duration-500"
                    style={{ width: `${completionPct}%` }}
                  />
                </div>
              </div>

              {/* Notion sync */}
              <button
                onClick={handleNotionSync}
                disabled={syncing}
                title="칸반 보드를 Notion으로 내보내기"
                className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-bb-surface border border-bb-border
                           text-xs text-bb-text2 hover:text-white hover:bg-[#191919] hover:border-[#191919]
                           transition-all disabled:opacity-50 font-medium"
              >
                {syncing
                  ? <Loader2 size={13} className="animate-spin" />
                  : <span className="text-[11px]">N</span>}
                Notion
              </button>

              {/* Score refresh */}
              <button
                onClick={handleRefreshScores}
                disabled={refreshing}
                title="기여도 점수 재계산"
                className="p-2 rounded-lg bg-bb-surface border border-bb-border text-bb-text2
                           hover:text-teal-400 hover:border-teal-400/40 transition-all disabled:opacity-50"
              >
                <RefreshCw size={15} className={refreshing ? "animate-spin" : ""} />
              </button>
            </div>
          </div>
        </div>

        {/* Notion sync result banner */}
        {notionUrl && (
          <div className="mx-8 mt-4 flex items-center gap-3 px-4 py-3 bg-[#191919] border border-[#333] rounded-xl text-sm text-white">
            <span className="text-[11px] font-bold bg-white text-[#191919] px-1.5 py-0.5 rounded">N</span>
            <span className="text-xs text-gray-300">Notion 페이지가 생성됐습니다</span>
            <a
              href={notionUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="ml-auto flex items-center gap-1 text-xs text-teal-400 hover:underline"
            >
              열기 <ExternalLink size={11} />
            </a>
          </div>
        )}

        {/* ── 필터 바 ──────────────────────────────────────────────────────── */}
        <div className="px-8 py-3 border-b border-slate-800 flex items-center gap-3 flex-wrap">
          <div className="flex items-center gap-1.5 text-xs text-bb-text2 shrink-0">
            <SlidersHorizontal size={13} />
            <span>필터</span>
          </div>

          {/* 담당자 */}
          <select
            value={filter.assigneeId ?? ""}
            onChange={(e) => setFilter((f) => ({ ...f, assigneeId: e.target.value || null }))}
            className="text-xs bg-bb-surface border border-bb-border rounded-lg px-2.5 py-1.5
                       text-bb-text2 focus:outline-none focus:border-indigo-500 transition-all"
          >
            <option value="">담당자 전체</option>
            {members.map((m) => (
              <option key={m.userId} value={m.userId}>{m.name}</option>
            ))}
          </select>

          {/* 우선순위 */}
          {(["LOW", "MEDIUM", "HIGH", "URGENT"] as TaskPriority[]).map((p) => {
            const labels: Record<TaskPriority, string> = { LOW: "낮음", MEDIUM: "보통", HIGH: "높음", URGENT: "긴급" };
            const colors: Record<TaskPriority, string> = {
              LOW: "border-slate-600 text-slate-400 data-[active=true]:bg-slate-600/30 data-[active=true]:text-slate-200",
              MEDIUM: "border-blue-500/50 text-blue-400 data-[active=true]:bg-blue-500/20 data-[active=true]:text-blue-300",
              HIGH: "border-orange-500/50 text-orange-400 data-[active=true]:bg-orange-500/20 data-[active=true]:text-orange-300",
              URGENT: "border-red-500/50 text-red-400 data-[active=true]:bg-red-500/20 data-[active=true]:text-red-300",
            };
            return (
              <button
                key={p}
                data-active={filter.priority === p}
                onClick={() => setFilter((f) => ({ ...f, priority: f.priority === p ? null : p }))}
                className={`text-xs px-2.5 py-1 rounded-full border transition-all ${colors[p]}`}
              >
                {labels[p]}
              </button>
            );
          })}

          {/* 태그 */}
          <input
            type="text"
            value={filter.tag}
            onChange={(e) => setFilter((f) => ({ ...f, tag: e.target.value }))}
            placeholder="태그 검색"
            className="text-xs bg-bb-surface border border-bb-border rounded-lg px-2.5 py-1.5
                       text-bb-text placeholder-slate-500 focus:outline-none focus:border-indigo-500
                       transition-all w-28"
          />

          {/* 초기화 */}
          {isFiltered && (
            <button
              onClick={clearFilter}
              className="flex items-center gap-1 text-xs text-indigo-400 hover:text-indigo-300 transition-colors ml-auto"
            >
              <X size={12} />
              필터 초기화
            </button>
          )}
        </div>

        {/* Board area */}
        <div className="flex-1 p-8 overflow-x-auto">
          <div className="min-w-[900px]">
            <KanbanBoard
              projectId={projectId}
              initialTasks={tasks}
              members={members.map((m) => ({
                userId: m.userId,
                name: m.name,
                email: m.email,
              }))}
              scoreMap={scoreMap}
              filter={filter}
              onTasksChange={setTasks}
            />
          </div>

          {/* 팀원별 진행 현황 */}
          {members.length > 0 && (
            <div className="min-w-[900px] mt-8 bg-bb-surface border border-bb-border rounded-xl p-5">
              <h2 className="text-sm font-semibold text-bb-text mb-4 flex items-center gap-2">
                <FolderKanban size={14} className="text-indigo-400" />
                팀원별 태스크 현황
              </h2>
              <div className="grid gap-3">
                {members.map((m) => {
                  const myTasks = tasks.filter((t) =>
                    t.assignees.some((a) => a.userId === m.userId)
                  );
                  const myTodo = myTasks.filter((t) => t.status === "TODO").length;
                  const myInProgress = myTasks.filter((t) => t.status === "IN_PROGRESS").length;
                  const myDone = myTasks.filter((t) => t.status === "DONE").length;
                  const total = myTasks.length;
                  const pct = total > 0 ? Math.round((myDone / total) * 100) : 0;
                  const hue = m.name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360;

                  return (
                    <div key={m.userId} className="flex items-center gap-4">
                      {/* Avatar */}
                      <div
                        style={{ background: `hsl(${hue} 55% 45%)` }}
                        className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0"
                      >
                        {m.name.slice(0, 2).toUpperCase()}
                      </div>

                      {/* Name */}
                      <span className="text-sm text-bb-text w-20 shrink-0 truncate">{m.name}</span>

                      {/* Pill badges */}
                      <div className="flex items-center gap-2 shrink-0">
                        <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 border border-slate-700">
                          할 일 {myTodo}
                        </span>
                        <span className="text-[10px] px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                          진행 {myInProgress}
                        </span>
                        <span className="text-[10px] px-2 py-0.5 rounded-full bg-teal-400/10 text-teal-400 border border-teal-400/20">
                          완료 {myDone}
                        </span>
                      </div>

                      {/* Progress bar */}
                      <div className="flex-1 flex items-center gap-2">
                        <div className="flex-1 h-1.5 bg-slate-800 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-gradient-to-r from-indigo-500 to-teal-400 rounded-full transition-all duration-500"
                            style={{ width: `${pct}%` }}
                          />
                        </div>
                        <span className="text-xs text-bb-text2 w-8 text-right shrink-0">
                          {total > 0 ? `${pct}%` : "-"}
                        </span>
                      </div>

                      {/* Unassigned indicator */}
                      {total === 0 && (
                        <span className="text-[10px] text-bb-text2 shrink-0">미배정</span>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
