import api from "@/lib/api";
import type { AgentOrigins, AgentStatus, Proposal } from "@/types/agent";

// AI 도우미 API (K-30). 실행 스트림은 lib/agent/run.ts
const base = (projectId: string) => `/projects/${projectId}/agent`;

export const getAgentStatus = (projectId: string) => api.get<AgentStatus>(`${base(projectId)}/status`);
export const getProposals = (projectId: string, status?: Proposal["status"]) =>
  api.get<Proposal[]>(`${base(projectId)}/proposals`, { params: status ? { status } : undefined });
export const getProposal = (projectId: string, id: string) => api.get<Proposal>(`${base(projectId)}/proposals/${id}`);
/** 보낸 content가 통째로 대신한다. 고치지 않은 칸도 모두 보낸다 */
export const saveProposal = (projectId: string, id: string, content: Proposal["content"]) =>
  api.put<Proposal>(`${base(projectId)}/proposals/${id}`, { content });
export const acceptProposal = (projectId: string, id: string) => api.post<Proposal>(`${base(projectId)}/proposals/${id}/accept`);
export const rejectProposal = (projectId: string, id: string) => api.post<Proposal>(`${base(projectId)}/proposals/${id}/reject`);
export const getAgentOrigins = (projectId: string) => api.get<AgentOrigins>(`${base(projectId)}/origins`);
