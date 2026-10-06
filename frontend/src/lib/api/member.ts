import api from "@/lib/api";
import type { MemberRole, ProjectMember } from "@/types/member";

export const getMembers = (projectId: string) =>
  api.get<ProjectMember[]>(`/projects/${projectId}/members`);

/** 팀장만 가능. 마지막 팀장을 다른 역할로 바꾸면 서버가 403 */
export const updateMemberRole = (projectId: string, memberId: string, role: MemberRole) =>
  api.patch<ProjectMember>(`/projects/${projectId}/members/${memberId}/role`, { role });

/** 팀장만 가능. 자기 자신은 내보낼 수 없다(403) */
export const removeMember = (projectId: string, memberId: string) =>
  api.delete(`/projects/${projectId}/members/${memberId}`);

/** 혼자 남은 팀장은 나갈 수 없다(403) */
export const leaveProject = (projectId: string) =>
  api.delete(`/projects/${projectId}/members/me`);
