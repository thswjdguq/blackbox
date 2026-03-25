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
} from "lucide-react";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";

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
}

export default function DashboardPage() {
  const router = useRouter();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);

  // 프로젝트 생성 모달
  const [showCreate, setShowCreate] = useState(false);
  const [createForm, setCreateForm] = useState({
    name: "",
    description: "",
    courseName: "",
    semester: "",
    startDate: "",
    endDate: "",
  });
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");

  // 초대 코드 참여 모달
  const [showJoin, setShowJoin] = useState(false);
  const [inviteCode, setInviteCode] = useState("");
  const [joining, setJoining] = useState(false);
  const [joinError, setJoinError] = useState("");

  // 인증 확인 + 프로젝트 목록 조회
  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    if (!token) {
      router.replace("/login");
      return;
    }
    fetchProjects();
  }, []);

  const fetchProjects = async () => {
    try {
      const { data } = await api.get<Project[]>("/projects");
      setProjects(data);
    } catch {
      router.replace("/login");
    } finally {
      setLoading(false);
    }
  };

  // 프로젝트 생성
  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setCreateError("");
    try {
      const payload = {
        name: createForm.name,
        description: createForm.description || null,
        courseName: createForm.courseName || null,
        semester: createForm.semester || null,
        startDate: createForm.startDate || null,
        endDate: createForm.endDate || null,
      };
      const { data } = await api.post<Project>("/projects", payload);
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

  // 초대 코드 참여
  const handleJoin = async (e: React.FormEvent) => {
    e.preventDefault();
    setJoining(true);
    setJoinError("");
    try {
      await api.post("/projects/join", { inviteCode: inviteCode.trim().toUpperCase() });
      setShowJoin(false);
      setInviteCode("");
      fetchProjects();
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail;
      setJoinError(detail || "참여에 실패했습니다");
    } finally {
      setJoining(false);
    }
  };

  const closeCreate = () => {
    setShowCreate(false);
    setCreateError("");
    setCreateForm({ name: "", description: "", courseName: "", semester: "", startDate: "", endDate: "" });
  };

  const closeJoin = () => {
    setShowJoin(false);
    setJoinError("");
    setInviteCode("");
  };

  return (
    <div className="min-h-screen bg-slate-950">
      <Sidebar />

      <main className="ml-64 min-h-screen p-8">
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold text-slate-100">내 프로젝트</h1>
            <p className="text-sm text-slate-400 mt-1">
              참여 중인 프로젝트 {projects.length}개
            </p>
          </div>
          <div className="flex gap-3">
            <button
              onClick={() => setShowJoin(true)}
              className="bg-slate-700 hover:bg-slate-600 text-slate-200 px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
            >
              <Hash size={15} />
              초대 코드 참여
            </button>
            <button
              onClick={() => setShowCreate(true)}
              className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
            >
              <Plus size={15} />
              새 프로젝트
            </button>
          </div>
        </div>

        {/* Project Grid */}
        {loading ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {[...Array(3)].map((_, i) => (
              <div
                key={i}
                className="bg-slate-800 border border-slate-700 rounded-xl p-6 animate-pulse"
              >
                <div className="h-4 bg-slate-700 rounded w-2/3 mb-3" />
                <div className="h-3 bg-slate-700 rounded w-1/2 mb-6" />
                <div className="h-3 bg-slate-700 rounded w-full" />
              </div>
            ))}
          </div>
        ) : projects.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <div className="w-16 h-16 rounded-2xl bg-slate-800 border border-slate-700 flex items-center justify-center mb-4">
              <FolderKanban size={28} className="text-slate-500" />
            </div>
            <p className="text-sm font-medium text-slate-300 mb-1">프로젝트가 없습니다</p>
            <p className="text-xs text-slate-500 mb-6">
              새 프로젝트를 만들거나 초대 코드로 참여해 보세요
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => setShowJoin(true)}
                className="bg-slate-700 hover:bg-slate-600 text-slate-200 px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
              >
                <Hash size={15} />
                초대 코드 참여
              </button>
              <button
                onClick={() => setShowCreate(true)}
                className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
              >
                <Plus size={15} />
                새 프로젝트
              </button>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {projects.map((project) => (
              <ProjectCard key={project.id} project={project} />
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
                <input
                  type="date"
                  value={createForm.startDate}
                  onChange={(e) => setCreateForm({ ...createForm, startDate: e.target.value })}
                  className={INPUT_CLS}
                />
              </Field>
              <Field label="종료일">
                <input
                  type="date"
                  value={createForm.endDate}
                  onChange={(e) => setCreateForm({ ...createForm, endDate: e.target.value })}
                  className={INPUT_CLS}
                />
              </Field>
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <button type="button" onClick={closeCreate} className={BTN_SECONDARY}>
                취소
              </button>
              <button type="submit" disabled={creating} className={BTN_PRIMARY}>
                {creating ? <Spinner /> : "만들기"}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* 초대 코드 참여 모달 */}
      {showJoin && (
        <Modal title="초대 코드로 참여" onClose={closeJoin}>
          <form onSubmit={handleJoin} className="space-y-4">
            {joinError && (
              <div className="p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
                {joinError}
              </div>
            )}
            <Field label="초대 코드">
              <input
                value={inviteCode}
                onChange={(e) => setInviteCode(e.target.value.toUpperCase())}
                placeholder="예: ABCD1234"
                maxLength={8}
                required
                className={INPUT_CLS + " tracking-widest font-mono uppercase"}
              />
              <p className="text-xs text-slate-500 mt-1.5">
                팀장에게 받은 8자리 초대 코드를 입력하세요
              </p>
            </Field>
            <div className="flex justify-end gap-3 pt-2">
              <button type="button" onClick={closeJoin} className={BTN_SECONDARY}>
                취소
              </button>
              <button type="submit" disabled={joining} className={BTN_PRIMARY}>
                {joining ? <Spinner /> : <><LogIn size={15} /> 참여하기</>}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}

// ── 프로젝트 카드 ───────────────────────────────────────────────────────────

function ProjectCard({ project }: { project: Project }) {
  const router = useRouter();

  const formatDate = (d: string | null) =>
    d ? new Date(d).toLocaleDateString("ko-KR", { month: "short", day: "numeric" }) : null;

  const dateRange =
    project.startDate && project.endDate
      ? `${formatDate(project.startDate)} ~ ${formatDate(project.endDate)}`
      : null;

  return (
    <div
      onClick={() => router.push(`/projects/${project.id}`)}
      className="bg-slate-800 border border-slate-700 rounded-xl p-6 cursor-pointer hover:border-slate-500 hover:bg-slate-800/80 transition-all group"
    >
      {/* Top */}
      <div className="flex items-start justify-between mb-3">
        <div className="w-9 h-9 rounded-lg bg-blue-500/10 flex items-center justify-center group-hover:bg-blue-500/20 transition-colors">
          <FolderKanban size={18} className="text-blue-400" />
        </div>
        <span className="bg-green-500/20 text-green-400 px-2 py-0.5 rounded-full text-xs">
          진행 중
        </span>
      </div>

      {/* Title */}
      <h3 className="text-sm font-semibold text-slate-100 mb-1 line-clamp-1">
        {project.name}
      </h3>
      {project.description && (
        <p className="text-xs text-slate-400 mb-3 line-clamp-2">{project.description}</p>
      )}
      {!project.description && project.courseName && (
        <p className="text-xs text-slate-400 mb-3">{project.courseName}</p>
      )}

      {/* Meta */}
      <div className="flex items-center gap-4 mt-auto pt-3 border-t border-slate-700/60">
        <span className="flex items-center gap-1.5 text-xs text-slate-500">
          <Users size={12} />
          {project.memberCount}명
        </span>
        {project.semester && (
          <span className="flex items-center gap-1.5 text-xs text-slate-500">
            <Calendar size={12} />
            {project.semester}
          </span>
        )}
        {dateRange && !project.semester && (
          <span className="flex items-center gap-1.5 text-xs text-slate-500">
            <Calendar size={12} />
            {dateRange}
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
      <div className="relative w-full max-w-md bg-slate-800 border border-slate-700 rounded-xl p-6 shadow-2xl">
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-lg font-semibold text-slate-100">{title}</h2>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-200 transition-colors p-1 rounded-lg hover:bg-slate-700"
          >
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
      <label className="block text-xs font-medium text-slate-400 mb-1.5">{label}</label>
      {children}
    </div>
  );
}

function Spinner() {
  return <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin inline-block" />;
}

const INPUT_CLS =
  "w-full bg-slate-700 border border-slate-600 rounded-lg px-4 py-2 text-sm text-slate-200 placeholder-slate-400 focus:outline-none focus:border-blue-500 transition-colors";

const BTN_PRIMARY =
  "bg-blue-600 hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed text-white px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2";

const BTN_SECONDARY =
  "bg-slate-700 hover:bg-slate-600 text-slate-200 px-4 py-2 rounded-lg text-sm font-medium transition-colors";
