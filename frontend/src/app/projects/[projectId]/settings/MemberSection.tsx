"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import api from "@/lib/api";
import { getMembers, updateMemberRole, removeMember, leaveProject } from "@/lib/api/member";
import { ROLE_LABEL, type MemberRole, type ProjectMember } from "@/types/member";
import { AlertCircle, Loader2, LogOut, RefreshCw, UserMinus, Users } from "lucide-react";

const ROLES: MemberRole[] = ["LEADER", "MEMBER", "OBSERVER"];

const ROLE_BADGE: Record<MemberRole, string> = {
  LEADER: "bg-indigo-500/20 text-indigo-400",
  MEMBER: "bg-teal-500/15 text-teal-400",
  OBSERVER: "bg-slate-700 text-slate-300",
};

function errorDetail(err: unknown, fallback: string): string {
  return (
    (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail ?? fallback
  );
}

function fmtDate(iso: string) {
  return new Date(iso).toLocaleDateString("ko-KR", { year: "numeric", month: "short", day: "numeric" });
}

export default function MemberSection({ projectId }: { projectId: string }) {
  const router = useRouter();

  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [myUserId, setMyUserId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [actionError, setActionError] = useState("");
  // 역할 변경·내보내기 요청 중인 memberId
  const [busyId, setBusyId] = useState<string | null>(null);
  const [confirmRemoveId, setConfirmRemoveId] = useState<string | null>(null);
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [leaving, setLeaving] = useState(false);

  const fetchMembers = useCallback(async () => {
    setLoading(true);
    setLoadError("");
    try {
      const [memRes, profileRes] = await Promise.all([
        getMembers(projectId),
        api.get<{ id: string }>("/auth/profile"),
      ]);
      setMembers(memRes.data);
      setMyUserId(profileRes.data.id);
    } catch (err) {
      setLoadError(errorDetail(err, "멤버 목록을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    fetchMembers();
  }, [fetchMembers]);

  const me = members.find((m) => m.userId === myUserId);
  const isLeader = me?.role === "LEADER";
  const leaderCount = members.filter((m) => m.role === "LEADER").length;
  // 서버도 막지만, 막힐 동작은 미리 비활성화하고 이유를 보여준다
  const soleLeader = (m: ProjectMember) => m.role === "LEADER" && leaderCount <= 1;

  const handleRoleChange = async (member: ProjectMember, role: MemberRole) => {
    if (role === member.role) return;
    setActionError("");
    setBusyId(member.memberId);
    try {
      const { data } = await updateMemberRole(projectId, member.memberId, role);
      setMembers((prev) => prev.map((m) => (m.memberId === data.memberId ? data : m)));
    } catch (err) {
      setActionError(errorDetail(err, "역할 변경에 실패했습니다. 다시 시도해주세요."));
    } finally {
      setBusyId(null);
    }
  };

  const handleRemove = async (member: ProjectMember) => {
    setActionError("");
    setBusyId(member.memberId);
    try {
      await removeMember(projectId, member.memberId);
      setMembers((prev) => prev.filter((m) => m.memberId !== member.memberId));
      setConfirmRemoveId(null);
    } catch (err) {
      setActionError(errorDetail(err, "멤버를 내보내지 못했습니다. 다시 시도해주세요."));
    } finally {
      setBusyId(null);
    }
  };

  const handleLeave = async () => {
    setActionError("");
    setLeaving(true);
    try {
      await leaveProject(projectId);
      router.replace("/dashboard");
    } catch (err) {
      setActionError(errorDetail(err, "프로젝트에서 나가지 못했습니다. 다시 시도해주세요."));
      setLeaving(false);
      setConfirmLeave(false);
    }
  };

  return (
    <div className="bg-bb-surface border border-bb-border rounded-xl p-6 mb-5">
      <div className="flex items-center justify-between mb-5">
        <h2 className="text-base font-semibold text-bb-text flex items-center gap-2">
          <Users size={16} className="text-bb-primary" />
          멤버
          {!loading && !loadError && (
            <span className="text-xs font-normal text-bb-text2">{members.length}명</span>
          )}
        </h2>
      </div>

      {loading && (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-12 bg-bb-surface2 rounded-lg animate-pulse" />
          ))}
        </div>
      )}

      {!loading && loadError && (
        <div className="flex flex-col items-center gap-3 py-8 text-center">
          <AlertCircle size={24} className="text-red-400" />
          <p className="text-sm text-bb-text2">{loadError}</p>
          <button
            onClick={fetchMembers}
            className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-bb-text2 hover:text-bb-text border border-bb-border rounded-lg transition-colors"
          >
            <RefreshCw size={13} />
            다시 시도
          </button>
        </div>
      )}

      {!loading && !loadError && (
        <>
          {actionError && (
            <div className="mb-3 flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
              <AlertCircle size={14} className="shrink-0" />
              {actionError}
              <button
                onClick={() => setActionError("")}
                className="ml-auto text-red-400 hover:text-red-300"
                aria-label="안내 닫기"
              >
                ✕
              </button>
            </div>
          )}

          {!isLeader && (
            <p className="mb-3 text-xs text-bb-text2">역할 변경과 내보내기는 팀장만 할 수 있습니다.</p>
          )}

          <ul className="divide-y divide-bb-border/60">
            {members.map((m) => {
              const isMe = m.userId === myUserId;
              const busy = busyId === m.memberId;
              return (
                <li key={m.memberId} className="py-3">
                  <div className="flex items-center gap-3">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-bb-text truncate">{m.name}</span>
                        {isMe && (
                          <span className="text-[10px] px-1.5 py-0.5 rounded-full bg-bb-surface2 text-bb-text2">
                            나
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-bb-text2 truncate">
                        {m.email} · {fmtDate(m.joinedAt)} 참여
                      </p>
                    </div>

                    {isLeader ? (
                      <select
                        value={m.role}
                        disabled={busy}
                        onChange={(e) => handleRoleChange(m, e.target.value as MemberRole)}
                        aria-label={`${m.name} 역할`}
                        className="bg-bb-bg border border-bb-border rounded-lg px-2 py-1 text-xs text-bb-text focus:outline-none focus:border-indigo-500 disabled:opacity-50"
                      >
                        {ROLES.map((r) => (
                          <option key={r} value={r} disabled={soleLeader(m) && r !== "LEADER"}>
                            {ROLE_LABEL[r]}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <span className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${ROLE_BADGE[m.role]}`}>
                        {ROLE_LABEL[m.role]}
                      </span>
                    )}

                    {isLeader && !isMe && (
                      <button
                        onClick={() => setConfirmRemoveId(m.memberId)}
                        disabled={busy}
                        className="p-1.5 rounded-lg text-bb-text2 hover:text-red-400 hover:bg-red-500/10 transition-colors disabled:opacity-50"
                        aria-label={`${m.name} 내보내기`}
                      >
                        {busy ? <Loader2 size={14} className="animate-spin" /> : <UserMinus size={14} />}
                      </button>
                    )}
                  </div>

                  {confirmRemoveId === m.memberId && (
                    <div className="mt-2 flex items-center gap-2 p-2.5 bg-red-500/5 border border-red-500/20 rounded-lg text-xs">
                      <span className="text-bb-text">{m.name}님을 프로젝트에서 내보낼까요?</span>
                      <button
                        onClick={() => setConfirmRemoveId(null)}
                        className="ml-auto px-2.5 py-1 text-bb-text2 hover:text-bb-text"
                      >
                        취소
                      </button>
                      <button
                        onClick={() => handleRemove(m)}
                        disabled={busy}
                        className="px-2.5 py-1 bg-red-500/80 hover:bg-red-500 text-white rounded-md disabled:opacity-50"
                      >
                        내보내기
                      </button>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>

          {me && (
            <div className="mt-5 pt-4 border-t border-bb-border">
              {!confirmLeave ? (
                <div className="flex items-center justify-between gap-3">
                  <p className="text-xs text-bb-text2">
                    {soleLeader(me)
                      ? "혼자 남은 팀장은 나갈 수 없습니다. 다른 멤버를 팀장으로 지정한 뒤 나가세요."
                      : "프로젝트에서 나가면 다시 초대 코드로 참여해야 합니다."}
                  </p>
                  <button
                    onClick={() => setConfirmLeave(true)}
                    disabled={soleLeader(me)}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-red-400 border border-red-500/30 hover:bg-red-500/10 rounded-lg transition-colors disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
                  >
                    <LogOut size={13} />
                    프로젝트 나가기
                  </button>
                </div>
              ) : (
                <div className="flex items-center gap-2 p-2.5 bg-red-500/5 border border-red-500/20 rounded-lg text-xs">
                  <span className="text-bb-text">정말 이 프로젝트에서 나갈까요?</span>
                  <button
                    onClick={() => setConfirmLeave(false)}
                    className="ml-auto px-2.5 py-1 text-bb-text2 hover:text-bb-text"
                  >
                    취소
                  </button>
                  <button
                    onClick={handleLeave}
                    disabled={leaving}
                    className="flex items-center gap-1 px-2.5 py-1 bg-red-500/80 hover:bg-red-500 text-white rounded-md disabled:opacity-50"
                  >
                    {leaving && <Loader2 size={12} className="animate-spin" />}
                    나가기
                  </button>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
