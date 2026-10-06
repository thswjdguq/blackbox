// 서버 MemberResponse / UpdateMemberRoleRequest 기준
export type MemberRole = "LEADER" | "MEMBER" | "OBSERVER";

export const ROLE_LABEL: Record<MemberRole, string> = {
  LEADER: "팀장",
  MEMBER: "팀원",
  OBSERVER: "관찰자",
};

export interface ProjectMember {
  memberId: string;
  userId: string;
  name: string;
  email: string;
  role: MemberRole;
  joinedAt: string;
}
