import { create } from "zustand";
import { getAgentStatus } from "@/lib/api/agent";
import { AgentRunError, runAgent } from "@/lib/agent/run";
import { onSessionReset, sessionGeneration } from "@/lib/sessionCache";
import type { AgentStatus, Proposal } from "@/types/agent";

/**
 * AI 도우미 창의 상태 (화면 문서 3.4, 6.4).
 * 화면을 옮기면 사이드바와 본문은 다시 그려지므로 창의 내용과 진행 중인 실행은 컴포넌트가 아니라 여기에 둔다.
 * 실행(run)도 여기서 돌아, 창을 닫거나 다른 화면으로 가도 이벤트를 계속 받는다.
 */
export interface RunEntry {
  localId: string;
  skill: string;
  /** 실행 기록의 제목. ASK는 질문, 나머지는 skills[].label */
  title: string;
  /** "다시 시도"가 같은 입력으로 다시 보낸다 */
  input: object;
  startedAt: number;
  steps: string[];
  text: string;
  proposalIds: string[];
  state: "running" | "done" | "failed";
  error: string | null;
}

interface AgentState {
  open: boolean;
  /** 프로젝트 id로 찾는다. 불러오지 못했으면 undefined */
  status: Record<string, AgentStatus | undefined>;
  runs: Record<string, RunEntry[]>;
  /** 카드 id로 찾는다 */
  proposals: Record<string, Proposal>;

  setOpen(open: boolean): void;
  loadStatus(projectId: string, force?: boolean): Promise<void>;
  run(projectId: string, skill: string, input: object, title: string): Promise<void>;
}

// 같은 프로젝트에서 30초 안에는 상태를 다시 부르지 않는다(4.1). 창을 열 때·실행이 끝났을 때는 force
const STATUS_TTL_MS = 30_000;
const loadedAt = new Map<string, number>();
let nextId = 0;

const empty = { open: false, status: {}, runs: {}, proposals: {} };

export const useAgentStore = create<AgentState>((set, get) => ({
  ...empty,

  setOpen: (open) => set({ open }),

  loadStatus: async (projectId, force = false) => {
    const at = loadedAt.get(projectId);
    if (!force && at && Date.now() - at < STATUS_TTL_MS) return;
    loadedAt.set(projectId, Date.now());
    const gen = sessionGeneration();
    try {
      const { data } = await getAgentStatus(projectId);
      if (gen !== sessionGeneration()) return;
      set((s) => ({ status: { ...s.status, [projectId]: data } }));
    } catch {
      // 실패해도 오류를 띄우지 않고 AI 버튼만 감춘다(2장 3번). 다음 화면에서 다시 부를 수 있게 시각을 지운다
      loadedAt.delete(projectId);
      if (gen !== sessionGeneration()) return;
      set((s) => ({ status: { ...s.status, [projectId]: undefined } }));
    }
  },

  run: async (projectId, skill, input, title) => {
    // 한 사람이 한 프로젝트에서 한 번에 하나만(서버도 409)
    if ((get().runs[projectId] ?? []).some((r) => r.state === "running")) return;
    const localId = `run-${++nextId}`;
    const entry: RunEntry = {
      localId, skill, title, input, startedAt: Date.now(),
      steps: [], text: "", proposalIds: [], state: "running", error: null,
    };
    set((s) => ({ runs: { ...s.runs, [projectId]: [...(s.runs[projectId] ?? []), entry] } }));

    // 로그아웃·계정 전환 뒤에 온 이벤트는 새 사람의 창에 쓰지 않는다(6.4)
    const gen = sessionGeneration();
    const update = (fn: (r: RunEntry) => RunEntry) => {
      if (gen !== sessionGeneration()) return;
      set((s) => ({
        runs: { ...s.runs, [projectId]: (s.runs[projectId] ?? []).map((r) => (r.localId === localId ? fn(r) : r)) },
      }));
    };

    try {
      await runAgent(projectId, skill, input, (e) => {
        switch (e.event) {
          case "step":
            update((r) => ({ ...r, steps: [...r.steps, e.data.label] }));
            break;
          case "text":
            update((r) => ({ ...r, text: r.text + e.data.text }));
            break;
          case "proposal":
            if (gen !== sessionGeneration()) return;
            set((s) => ({ proposals: { ...s.proposals, [e.data.id]: e.data } }));
            update((r) => ({ ...r, proposalIds: [...r.proposalIds, e.data.id] }));
            break;
          case "done":
            update((r) => ({ ...r, state: "done" }));
            break;
          case "error":
            update((r) => ({ ...r, state: "failed", error: e.data.detail }));
            break;
        }
      });
    } catch (err) {
      let message = "요청을 보내지 못했습니다. 연결을 확인해주세요";
      if (err instanceof AgentRunError) {
        message = err.message;
        if (err.status === 409) message += " 길어도 2분 안에 끝납니다.";
      }
      update((r) => (r.state === "running" ? { ...r, state: "failed", error: message } : r));
    } finally {
      // 끝나면(성공·실패 모두) 상태를 다시 부른다. AI가 꺼졌으면(503) 이걸로 버튼이 사라진다
      if (gen === sessionGeneration()) get().loadStatus(projectId, true);
    }
  },
}));

// 로그아웃하거나 계정이 바뀌면 전부 비운다(#82)
onSessionReset(() => {
  loadedAt.clear();
  useAgentStore.setState(empty);
});
