"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Plus,
  Users,
  FolderKanban,
  Calendar,
  Hash,
  X,
  LogIn,
  Trash2,
  CheckSquare,
  Clock,
  ChevronRight,
  AlertCircle,
  RefreshCw,
} from "lucide-react";
import Link from "next/link";
import Sidebar from "@/components/Sidebar";
import MyTasks from "@/components/dashboard/MyTasks";
import DatePicker from "@/components/DatePicker";
import api from "@/lib/api";
import { roleLabel } from "@/lib/roleLabel";
import { apiError } from "@/lib/apiError";

interface Project {
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
  createdAt: string | null;
}

export default function DashboardPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  // 프로젝트 생성 모달
  const [showCreate, setShowCreate] = useState(false);
  const [createForm, setCreateForm] = useState({
    name: "", description: "", courseName: "", semester: "", startDate: "", endDate: "",
  });
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");

  // 코드 참여 모달
  const [showJoin, setShowJoin] = useState(false);
  const [code, setCode] = useState("");
  const [joining, setJoining] = useState(false);
  const [joinError, setJoinError] = useState("");
  const [joinSuccess, setJoinSuccess] = useState<{ type: "project"; label: string } | null>(null);

  // 프로젝트 삭제 모달
  const [showDeleteId, setShowDeleteId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");

  // 인증 확인 + 초기 데이터
  useEffect(() => {
    fetchProjects();

  }, []);

  const fetchProjects = async () => {
    setLoadError("");
    try {
      const { data } = await api.get<Project[]>("/projects");
      setProjects(data);
      localStorage.setItem("projectCount", data.length.toString());
    } catch (err) {
      // 인증 만료(401)는 api 인터셉터가 로그인으로 보낸다. 그 밖의 실패를 로그아웃처럼 보이게 하지 않는다
      setLoadError(apiError(err, "프로젝트 목록을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  };

  // 프로젝트 삭제
  const handleDelete = async () => {
    if (!showDeleteId) return;
    setDeleting(true);
    setDeleteError("");
    try {
      await api.delete(`/projects/${showDeleteId}`);
      const updated = projects.filter((p) => p.id !== showDeleteId);
      setProjects(updated);
      localStorage.setItem("projectCount", updated.length.toString());
      setShowDeleteId(null);
    } catch (err) {
      // 삭제할 수 없는 프로젝트는 서버가 409 와 사유(detail)를 준다 (A-03). 창을 닫지 않고 사유를 보여준다
      setDeleteError(apiError(err, "프로젝트를 삭제하지 못했습니다. 다시 시도해주세요."));
    } finally {
      setDeleting(false);
    }
  };

  // 프로젝트 생성
  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setCreateError("");
    try {
      const { data } = await api.post<Project>("/projects", {
        name: createForm.name,
        description: createForm.description || null,
        courseName: createForm.courseName || null,
        semester: createForm.semester || null,
        startDate: createForm.startDate || null,
        endDate: createForm.endDate || null,
      });
      setProjects((prev) => [data, ...prev]);
      setShowCreate(false);
      setCreateForm({ name: "", description: "", courseName: "", semester: "", startDate: "", endDate: "" });
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setCreateError(detail || "프로젝트 생성에 실패했습니다");
    } finally {
      setCreating(false);
    }
  };

  // 프로젝트 초대 코드로 참여하면 미마감 회의의 참석 대상에도 포함된다.
  const handleJoin = async (e: React.FormEvent) => {
    e.preventDefault(); setJoining(true); setJoinError(""); setJoinSuccess(null);
    try {
      await api.post("/projects/join", { inviteCode: code.trim().toUpperCase() });
      setJoinSuccess({ type: "project", label: "프로젝트에 참여했습니다!" }); setCode(""); fetchProjects();
      setTimeout(() => { setShowJoin(false); setJoinSuccess(null); }, 1500);
    } catch (e) { setJoinError(apiError(e, "프로젝트 초대 코드를 확인하세요.")); }
    finally { setJoining(false); }
  };

  const closeCreate = () => {
    setShowCreate(false);
    setCreateError("");
    setCreateForm({ name: "", description: "", courseName: "", semester: "", startDate: "", endDate: "" });
  };

  const closeJoin = () => {
    setShowJoin(false);
    setJoinError("");
    setJoinSuccess(null);
    setCode("");
  };

  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar hasProjects={projects.length > 0} />

      <main className="md:ml-64 mt-14 md:mt-0 min-h-screen p-4 md:p-8">
        {/* Header */}
        <div className="flex flex-wrap items-center justify-between gap-4 mb-8">
          <div>
            <p className="text-xs font-semibold tracking-widest text-bb-primary mb-2">TEAM WORKSPACE</p><h1 className="text-3xl font-bold text-bb-text">함께 만드는 결과물, 한곳에서.</h1>
            <p className="text-sm text-bb-text2 mt-1">참여 중인 프로젝트 {projects.length}개</p>
          </div>
          <div className="flex shrink-0 gap-3">
            <button
              onClick={() => setShowJoin(true)}
              className="bg-bb-surface2 hover:bg-bb-border text-bb-text px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2 whitespace-nowrap"
            >
              <Hash size={15} />
              코드 참여
            </button>
            <button
              onClick={() => setShowCreate(true)}
              className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2 whitespace-nowrap"
            >
              <Plus size={15} />
              새 프로젝트
            </button>
          </div>
        </div>

        {/* 모든 프로젝트에서 나에게 배정된 업무 (C-50) */}
        {!loading && <MyTasks projects={projects} />}

        {/* Project Grid */}
        <input aria-label="프로젝트 검색" value={query} onChange={e => setQuery(e.target.value)}
          placeholder="프로젝트 검색" className="mb-5 w-full max-w-md border border-bb-border bg-bb-surface rounded-lg p-3 text-sm" />
        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="bg-bb-surface border border-bb-border rounded-xl p-6 animate-pulse">
                <div className="h-4 bg-bb-surface2 rounded w-2/3 mb-3" />
                <div className="h-3 bg-bb-surface2 rounded w-1/2 mb-6" />
                <div className="h-3 bg-bb-surface2 rounded w-full" />
              </div>
            ))}
          </div>
        ) : loadError ? (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <AlertCircle size={28} className="text-red-400 mb-3" />
            <p className="text-sm text-bb-text2 mb-4">{loadError}</p>
            <button
              onClick={() => {
                setLoading(true);
                fetchProjects();
              }}
              className="flex items-center gap-1.5 px-4 py-2 text-sm text-bb-text2 hover:text-bb-text border border-bb-border rounded-lg transition-colors"
            >
              <RefreshCw size={14} />
              다시 시도
            </button>
          </div>
        ) : projects.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <div className="w-16 h-16 rounded-2xl bg-bb-surface border border-bb-border flex items-center justify-center mb-4">
              <FolderKanban size={28} className="text-bb-text2" />
            </div>
            <p className="text-sm font-medium text-bb-text2 mb-1">프로젝트가 없습니다</p>
            <p className="text-xs text-bb-text2 mb-6">새 프로젝트를 만들거나 코드로 참여해 보세요</p>
            <div className="flex gap-3">
              <button
                onClick={() => setShowJoin(true)}
                className="bg-bb-surface2 hover:bg-bb-border text-bb-text px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
              >
                <Hash size={15} />
                코드 참여
              </button>
              <button
                onClick={() => setShowCreate(true)}
                className="bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
              >
                <Plus size={15} />
                새 프로젝트
              </button>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {projects.filter(p => (p.name + " " + (p.courseName ?? "")).toLowerCase().includes(query.trim().toLowerCase())).map((project) => (
              <ProjectCard key={project.id} project={project} onDelete={() => setShowDeleteId(project.id)} />
            ))}
          </div>
        )}

      </main>

      {/* 프로젝트 생성 모달 */}
      {showCreate && (
        <Modal title="새 프로젝트 만들기" onClose={closeCreate}>
          <form onSubmit={handleCreate} className="space-y-4">
            {createError && (
              <div className="p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
                {createError}
              </div>
            )}
            <Field label="프로젝트 이름 *">
              <input
                value={createForm.name}
                onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                placeholder="예: 캡스톤 디자인 팀 프로젝트"
                required
                className={INPUT_CLS}
              />
            </Field>
            <Field label="설명">
              <textarea
                value={createForm.description}
                onChange={(e) => setCreateForm({ ...createForm, description: e.target.value })}
                placeholder="프로젝트에 대한 간단한 설명"
                rows={2}
                className={INPUT_CLS + " resize-none"}
              />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="과목명">
                <input
                  value={createForm.courseName}
                  onChange={(e) => setCreateForm({ ...createForm, courseName: e.target.value })}
                  placeholder="예: 소프트웨어공학"
                  className={INPUT_CLS}
                />
              </Field>
              <Field label="학기">
                <input
                  value={createForm.semester}
                  onChange={(e) => setCreateForm({ ...createForm, semester: e.target.value })}
                  placeholder="예: 2026-1"
                  className={INPUT_CLS}
                />
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="시작일">
                <DatePicker value={createForm.startDate} onChange={(v) => setCreateForm({ ...createForm, startDate: v })} placeholder="YYYY-MM-DD" />
              </Field>
              <Field label="종료일">
                <DatePicker value={createForm.endDate} onChange={(v) => setCreateForm({ ...createForm, endDate: v })} placeholder="YYYY-MM-DD" />
              </Field>
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <button type="button" onClick={closeCreate} className={BTN_SECONDARY}>취소</button>
              <button type="submit" disabled={creating} className={BTN_PRIMARY}>
                {creating ? <Spinner /> : "만들기"}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* 코드 참여 모달 */}
      {showJoin && (
        <Modal title="코드로 참여" onClose={closeJoin}>
          <form onSubmit={handleJoin} className="space-y-4">
            {joinError && (
              <div className="p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
                {joinError}
              </div>
            )}
            {joinSuccess && (
              <div className={`p-3 rounded-lg text-sm font-medium ${
                joinSuccess.type === "project"
                  ? "bg-indigo-500/10 border border-indigo-500/20 text-indigo-400"
                  : "bg-green-500/10 border border-green-500/20 text-green-400"
              }`}>

                {joinSuccess.label}
              </div>
            )}
            <Field label="초대 코드">
              <input
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                placeholder="예: ABCD1234"
                maxLength={8}
                required
                className={INPUT_CLS + " tracking-widest font-mono uppercase"}
              />
              <p className="text-xs text-slate-500 mt-1.5">
                프로젝트 초대 코드를 입력하세요. 예정된 회의에도 자동 등록됩니다
              </p>
            </Field>
            <div className="flex justify-end gap-3 pt-2">
              <button type="button" onClick={closeJoin} className={BTN_SECONDARY}>취소</button>
              <button type="submit" disabled={joining} className={BTN_PRIMARY}>
                {joining ? <Spinner /> : <><LogIn size={15} /> 참여</>}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* 프로젝트 삭제 확인 모달 */}
      {showDeleteId && (
        <Modal title="프로젝트 삭제" onClose={() => { setShowDeleteId(null); setDeleteError(""); }}>
          <div className="space-y-4">
            <p className="text-sm text-bb-text2">
              이 프로젝트를 삭제할까요? 삭제한 프로젝트는 복구할 수 없습니다. 활동 기록·파일·기여도 기록 등이 있는 프로젝트는 기록 보존을 위해 삭제가 제한됩니다.
            </p>
            {deleteError && (
              <p role="alert" className="p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400 whitespace-pre-wrap">
                {deleteError}
              </p>
            )}
            <div className="flex justify-end gap-3 pt-2">
              <button type="button" onClick={() => { setShowDeleteId(null); setDeleteError(""); }} disabled={deleting} className={BTN_SECONDARY}>
                취소
              </button>
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleting}
                className="bg-red-600 hover:bg-red-700 disabled:opacity-60 disabled:cursor-not-allowed text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
              >
                {deleting ? <Spinner /> : <><Trash2 size={15} /> 삭제</>}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

// ── 프로젝트 카드 ───────────────────────────────────────────────────────────

function ProjectCard({ project, onDelete }: { project: Project; onDelete: () => void }) {
  const router = useRouter();
  const formatDate = (d: string | null) =>
    d ? new Date(d).toLocaleDateString("ko-KR", { month: "short", day: "numeric" }) : null;
  const dateRange =
    project.startDate && project.endDate
      ? `${formatDate(project.startDate)} ~ ${formatDate(project.endDate)}`
      : null;
  // YYYY.MM.DD 포맷
  const createdAtLabel = project.createdAt
    ? (() => {
        const date = new Date(project.createdAt);
        const pad = (n: number) => String(n).padStart(2, "0");
        return `${date.getFullYear()}.${pad(date.getMonth() + 1)}.${pad(date.getDate())}`;
      })()
    : null;

  return (
    <div
      onClick={() => router.push(`/projects/${project.id}`)}
      className="bg-bb-surface border border-bb-border rounded-xl p-6 cursor-pointer hover:border-bb-primary/30 hover:shadow-sm transition-all group"
    >
      <div className="flex items-start justify-between mb-3">
        <div className="w-9 h-9 rounded-lg bg-indigo-500/10 flex items-center justify-center group-hover:bg-indigo-500/20 transition-colors">
          <FolderKanban size={18} className="text-indigo-500 dark:text-indigo-400" />
        </div>
        <div className="flex items-center gap-2">
          <span className="bg-green-500/20 text-green-600 dark:text-green-400 px-2 py-0.5 rounded-full text-xs">진행 중</span>
          {project.myRole === "LEADER" && (
            <button
              onClick={(e) => { e.stopPropagation(); onDelete(); }}
              className="p-1 rounded-lg text-slate-400 hover:text-red-500 hover:bg-red-500/10 transition-colors"
              title="프로젝트 삭제"
            >
              <Trash2 size={14} />
            </button>
          )}
        </div>
      </div>
      <h3 className="text-sm font-semibold text-bb-text mb-1 line-clamp-1">{project.name}</h3>
      {project.description && <p className="text-xs text-bb-text2 mb-3 line-clamp-2">{project.description}</p>}
      {!project.description && project.courseName && <p className="text-xs text-bb-text2 mb-3">{project.courseName}</p>}
      <div className="flex items-center gap-3 mt-auto pt-3 border-t border-bb-border flex-wrap">
        <span className="flex items-center gap-1.5 text-xs text-bb-text2"><Users size={12} />{project.memberCount}명</span>
        {/* 학기 + 생성일 (학기 옆에 표시) */}
        {project.semester ? (
          <span className="flex items-center gap-1.5 text-xs text-bb-text2">
            <Calendar size={12} />
            {project.semester}
            {createdAtLabel && (
              <span className="text-slate-600">· {createdAtLabel}</span>
            )}
          </span>
        ) : dateRange ? (
          <span className="flex items-center gap-1.5 text-xs text-bb-text2">
            <Calendar size={12} />{dateRange}
          </span>
        ) : createdAtLabel ? (
          <span className="flex items-center gap-1.5 text-xs text-bb-text2">
            <Calendar size={12} />{createdAtLabel}
          </span>
        ) : null}
        {project.myRole && (
          <span className="ml-auto text-xs font-medium text-indigo-500 dark:text-indigo-400">
            {roleLabel(project.myRole)}
          </span>
        )}
      </div>
    </div>
  );
}

// ── 공용 컴포넌트 ───────────────────────────────────────────────────────────

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60" onClick={onClose} />
      <div className="relative w-full max-w-md max-h-[calc(100dvh-2rem)] overflow-y-auto overscroll-contain bg-bb-surface border border-bb-border rounded-xl p-6 shadow-2xl">
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-lg font-semibold text-bb-text">{title}</h2>
          <button onClick={onClose} className="text-bb-text2 hover:text-bb-text transition-colors p-1 rounded-lg hover:bg-bb-surface2">
            <X size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="block text-xs font-medium text-bb-text2 mb-1.5">{label}</label>
      {children}
    </div>
  );
}

function Spinner() {
  return <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin inline-block" />;
}

const INPUT_CLS =
  "w-full bg-bb-surface border border-bb-border rounded-lg px-4 py-2 text-sm text-bb-text placeholder-bb-text2 focus:outline-none focus:border-bb-primary transition-colors";

const BTN_PRIMARY =
  "bg-bb-primary hover:bg-bb-primary-h disabled:opacity-60 disabled:cursor-not-allowed text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2";

const BTN_SECONDARY =
  "bg-bb-surface2 hover:bg-bb-border text-bb-text px-4 py-2 rounded-lg text-sm font-medium transition-colors";
