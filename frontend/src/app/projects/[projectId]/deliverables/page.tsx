"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import Sidebar from "@/components/Sidebar";
import DeliverableFormModal from "@/components/deliverable/DeliverableFormModal";
import ConfirmDeleteDialog from "@/components/deliverable/ConfirmDeleteDialog";
import api from "@/lib/api";
import { DUE_TEXT, dueLabel, dueTone, shortDate } from "@/lib/due";
import { apiError } from "@/lib/apiError";
import { getDeliverables, createDeliverable, deleteDeliverable } from "@/lib/api/deliverable";
import type { Deliverable, SaveDeliverablePayload } from "@/types/deliverable";
import type { Task } from "@/types/task";
import {
  ClipboardList,
  Plus,
  Trash2,
  ChevronRight,
  AlertCircle,
  RefreshCw,
  CalendarDays,
  User,
} from "lucide-react";

interface Member {
  userId: string;
  name: string;
  role: string;
}

function SkeletonCard() {
  return (
    <div className="bg-bb-surface border border-bb-border rounded-xl p-5 animate-pulse">
      <div className="h-4 bg-bb-surface2 rounded w-2/3 mb-3" />
      <div className="h-3 bg-bb-surface2 rounded w-1/3" />
    </div>
  );
}

export default function DeliverablesPage() {
  const params = useParams();
  const projectId = params?.projectId as string;

  const [deliverables, setDeliverables] = useState<Deliverable[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [projectName, setProjectName] = useState("");
  const [myUserId, setMyUserId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Deliverable | null>(null);

  // 팀장·팀원만 쓰기 가능. 관찰자(OBSERVER)는 읽기 전용.
  // 역할은 멤버 목록의 내 항목으로 판단한다. GET /projects/{id} 의 myRole 은 현재 항상 null 이다 (A 에 수정 요청)
  const myRole = members.find((m) => m.userId === myUserId)?.role ?? null;
  const canWrite = myRole === "LEADER" || myRole === "MEMBER";

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [delRes, taskRes, memRes, projRes, profileRes] = await Promise.all([
        getDeliverables(projectId),
        api.get<Task[]>(`/projects/${projectId}/tasks`),
        api.get<Member[]>(`/projects/${projectId}/members`),
        api.get<{ name: string }>(`/projects/${projectId}`),
        api.get<{ id: string }>("/auth/profile"),
      ]);
      setDeliverables(delRes.data);
      setTasks(taskRes.data);
      setMembers(memRes.data);
      setProjectName(projRes.data.name);
      setMyUserId(profileRes.data.id);
    } catch (err) {
      setError(apiError(err, "제출물 목록을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  const handleCreate = async (payload: SaveDeliverablePayload) => {
    await createDeliverable(projectId, payload);
    setShowForm(false);
    setNotice("제출물을 추가했습니다. 제출물을 열어 요구사항을 적으세요.");
    fetchAll();
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    await deleteDeliverable(projectId, deleteTarget.id);
    setDeliverables((prev) => prev.filter((d) => d.id !== deleteTarget.id));
    setDeleteTarget(null);
    setNotice("제출물을 삭제했습니다.");
  };

  const owners = members.filter((m) => m.role !== "OBSERVER");
  const unlinkedCount = tasks.filter((t) => !t.deliverableId).length;

  return (
    <div className="flex h-screen bg-bb-bg">
      <Sidebar />

      <main className="flex-1 ml-64 overflow-y-auto p-8">
        <Link href={`/projects/${projectId}`} className="text-xs text-bb-text2 hover:text-bb-text">
          {projectName || "프로젝트"} / 프로젝트 홈
        </Link>

        <div className="flex items-start justify-between gap-4 mt-3 mb-6">
          <div>
            <h1 className="text-xl font-bold text-bb-text flex items-center gap-2">
              <ClipboardList size={20} className="text-bb-primary" />
              제출물
            </h1>
            <p className="mt-1 text-sm text-bb-text2">무엇을 제출할지 정하고, 필요한 작업을 팀원에게 나누세요.</p>
          </div>
          {canWrite && (
            <button
              onClick={() => setShowForm(true)}
              className="flex items-center gap-1.5 px-4 py-2 bg-bb-primary hover:bg-bb-primary-h text-white text-sm rounded-lg transition-colors shrink-0"
            >
              <Plus size={15} />
              제출물 추가
            </button>
          )}
        </div>

        {notice && (
          <div role="status" className="mb-4 flex items-center gap-2 p-3 bg-teal-500/10 border border-teal-500/20 rounded-lg text-sm text-bb-text">
            {notice}
            <button onClick={() => setNotice("")} className="ml-auto text-bb-text2 hover:text-bb-text" aria-label="안내 닫기">
              ✕
            </button>
          </div>
        )}

        {loading && (
          <div className="space-y-3">
            <SkeletonCard />
            <SkeletonCard />
            <SkeletonCard />
          </div>
        )}

        {!loading && error && (
          <div className="flex flex-col items-center gap-3 py-20 text-center">
            <AlertCircle size={32} className="text-red-400" />
            <p className="text-sm text-bb-text2">{error}</p>
            <button
              onClick={fetchAll}
              className="flex items-center gap-1.5 px-4 py-2 text-sm text-bb-text2 hover:text-bb-text border border-bb-border rounded-lg transition-colors"
            >
              <RefreshCw size={14} />
              다시 시도
            </button>
          </div>
        )}

        {!loading && !error && deliverables.length === 0 && (
          <div className="flex flex-col items-center gap-3 py-20 text-center">
            <ClipboardList size={32} className="text-bb-text2 opacity-40" />
            <p className="text-sm font-medium text-bb-text">첫 제출물을 등록하세요</p>
            <p className="text-xs text-bb-text2">예: 중간발표 자료, 조사 보고서, 최종 작품 설명서</p>
            {canWrite ? (
              <button
                onClick={() => setShowForm(true)}
                className="flex items-center gap-1.5 px-4 py-2 bg-bb-primary hover:bg-bb-primary-h text-white text-sm rounded-lg transition-colors"
              >
                <Plus size={14} />
                제출물 등록하기
              </button>
            ) : (
              <p className="text-xs text-bb-text2">팀원이 제출물을 등록하면 여기서 확인할 수 있습니다.</p>
            )}
          </div>
        )}

        {!loading && !error && deliverables.length > 0 && (
          <div className="space-y-3">
            {deliverables.map((d) => {
              const requiredCount = d.requirements.filter((r) => r.required).length;
              const taskCount = tasks.filter((t) => t.deliverableId === d.id).length;
              return (
                <div
                  key={d.id}
                  className="group bg-bb-surface border border-bb-border rounded-xl p-5 hover:border-indigo-500/40 transition-colors"
                >
                  <div className="flex items-start justify-between gap-4">
                    <Link href={`/projects/${projectId}/deliverables/${d.id}`} className="flex-1 min-w-0">
                      <p className="text-sm font-semibold text-bb-text group-hover:text-bb-primary transition-colors truncate">
                        {d.title}
                      </p>
                      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 mt-1.5 text-xs text-bb-text2">
                        <span className="flex items-center gap-1">
                          <CalendarDays size={12} />
                          제출일 {shortDate(d.dueDate)}
                          <span className={`font-medium ${DUE_TEXT[dueTone(d.dueDate)]}`}>· {dueLabel(d.dueDate)}</span>
                        </span>
                        <span className="flex items-center gap-1">
                          <User size={12} />
                          {d.ownerName || "담당자 미지정"}
                        </span>
                        <span>
                          요구사항 {d.requirements.length}개{requiredCount > 0 && ` (필수 ${requiredCount})`} · 연결 업무 {taskCount}개
                        </span>
                      </div>
                      {d.description && (
                        <p className="mt-2 text-xs text-bb-text2 line-clamp-2">{d.description}</p>
                      )}
                    </Link>

                    <div className="flex items-center gap-1 shrink-0">
                      {canWrite && (
                        <button
                          onClick={() => setDeleteTarget(d)}
                          className="p-1.5 rounded-lg text-bb-text2 hover:text-red-400 hover:bg-red-500/10 opacity-0 group-hover:opacity-100 focus:opacity-100 transition-all"
                          aria-label={`${d.title} 삭제`}
                        >
                          <Trash2 size={14} />
                        </button>
                      )}
                      <ChevronRight size={16} className="text-bb-text2" />
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {!loading && !error && unlinkedCount > 0 && (
          <p className="mt-6 text-sm text-bb-text2">
            제출물에 연결되지 않은 업무가 {unlinkedCount}개 있습니다.{" "}
            <Link href={`/projects/${projectId}/board`} className="text-bb-primary underline">
              업무 보드에서 확인
            </Link>
          </p>
        )}
      </main>

      {showForm && (
        <DeliverableFormModal initial={null} owners={owners} onSave={handleCreate} onClose={() => setShowForm(false)} />
      )}
      {deleteTarget && (
        <ConfirmDeleteDialog
          title={`제출물 "${deleteTarget.title}"`}
          onConfirm={handleDelete}
          onClose={() => setDeleteTarget(null)}
        />
      )}
    </div>
  );
}
