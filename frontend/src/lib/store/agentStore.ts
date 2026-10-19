import { create } from "zustand";
import { getAgentStatus, getProposals } from "@/lib/api/agent";
import { getDeliverables } from "@/lib/api/deliverable";
import { AgentRunError, runAgent } from "@/lib/agent/run";
import { onSessionReset, sessionGeneration } from "@/lib/sessionCache";
import type { AgentStatus, Proposal } from "@/types/agent";
import type { Deliverable } from "@/types/deliverable";

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

export type ProposalFilter = Proposal["status"];

export interface ProposalList {
  filter: ProposalFilter;
  ids: string[];
  loading: boolean;
  error: string;
}

/** 서버가 한 번에 주는 카드 수의 상한(4.8) */
export const PROPOSAL_LIMIT = 50;

interface AgentState {
  open: boolean;
  tab: "assistant" | "proposals";
  /** 프로젝트 id로 찾는다. 불러오지 못했으면 undefined */
  status: Record<string, AgentStatus | undefined>;
  runs: Record<string, RunEntry[]>;
  /** 카드 id로 찾는다. 실행 기록과 제안 탭이 같은 것을 본다 */
  proposals: Record<string, Proposal>;
  /** 프로젝트별 "제안" 탭 목록 */
  proposalLists: Record<string, ProposalList | undefined>;
  /** 업무 카드의 요구사항 이름을 찾으려고 읽는 제출물(요구사항은 stages에 없다, 4.7) */
  deliverables: Record<string, Deliverable[] | undefined>;
  /** 채택할 때마다 1씩 는다. 뒤 화면이 이 값을 보고 다시 읽는다(4.11) */
  recordsVersion: number;
  /** 화면마다의 버튼이 연 입력 칸(입력이 필요한 실행). 창이 읽고 비운다 */
  pendingInput: { projectId: string; skill: string } | null;

  setOpen(open: boolean): void;
  setTab(tab: "assistant" | "proposals"): void;
  loadProposals(projectId: string, filter: ProposalFilter): Promise<void>;
  loadDeliverables(projectId: string): Promise<void>;
  /** 수정·채택·거절의 응답으로 받은 카드를 넣는다. 화면은 카드 내용을 따로 들고 있지 않는다(2장 9번) */
  putProposal(p: Proposal): void;
  bumpRecords(): void;
  /** 화면마다의 버튼이 부른다(4.9). 창을 열고, 입력이 다 있으면 실행까지 한다 */
  openWith(projectId: string, skill: string, input?: object, titleExtra?: string): void;
  clearPendingInput(): void;
  loadStatus(projectId: string, force?: boolean): Promise<void>;
  run(projectId: string, skill: string, input: object, title: string): Promise<void>;
}

// 같은 프로젝트에서 30초 안에는 상태를 다시 부르지 않는다(4.1). 창을 열 때·실행이 끝났을 때는 force
const STATUS_TTL_MS = 30_000;
const loadedAt = new Map<string, number>();
let nextId = 0;

const empty = {
  open: false,
  tab: "assistant" as const,
  status: {},
  runs: {},
  proposals: {},
  proposalLists: {},
  deliverables: {},
  recordsVersion: 0,
  pendingInput: null,
};

export const useAgentStore = create<AgentState>((set, get) => ({
  ...empty,

  setOpen: (open) => set({ open }),
  setTab: (tab) => set({ tab }),

  loadProposals: async (projectId, filter) => {
    const gen = sessionGeneration();
    set((s) => ({
      proposalLists: { ...s.proposalLists, [projectId]: { filter, ids: s.proposalLists[projectId]?.filter === filter ? s.proposalLists[projectId]!.ids : [], loading: true, error: "" } },
    }));
    try {
      const { data } = await getProposals(projectId, filter);
      if (gen !== sessionGeneration()) return;
      set((s) => {
        // 그 사이 다른 걸러 보기를 골랐으면 늦게 온 응답을 버린다
        if (s.proposalLists[projectId]?.filter !== filter) return {};
        const proposals = { ...s.proposals };
        for (const p of data) proposals[p.id] = p;
        return { proposals, proposalLists: { ...s.proposalLists, [projectId]: { filter, ids: data.map((p) => p.id), loading: false, error: "" } } };
      });
    } catch {
      if (gen !== sessionGeneration()) return;
      set((s) => ({
        proposalLists: { ...s.proposalLists, [projectId]: { filter, ids: [], loading: false, error: "제안 목록을 불러오지 못했습니다." } },
      }));
    }
  },

  loadDeliverables: async (projectId) => {
    if (get().deliverables[projectId]) return;
    const gen = sessionGeneration();
    try {
      const { data } = await getDeliverables(projectId);
      if (gen !== sessionGeneration()) return;
      set((s) => ({ deliverables: { ...s.deliverables, [projectId]: data } }));
    } catch {
      // 요구사항 이름만 못 보일 뿐이라 오류를 띄우지 않는다
    }
  },

  putProposal: (p) => set((s) => ({ proposals: { ...s.proposals, [p.id]: p } })),

  openWith: (projectId, skill, input, titleExtra) => {
    const status = get().status[projectId];
    const label = status?.skills.find((s) => s.skill === skill)?.label ?? skill;
    set({ open: true, tab: "assistant" });
    if (input) {
      get().run(projectId, skill, input, titleExtra ? `${label} · ${titleExtra}` : label);
    } else {
      set({ pendingInput: { projectId, skill } });
    }
  },
  clearPendingInput: () => set({ pendingInput: null }),

  // 채택으로 제출물·요구사항이 생기므로 요구사항 이름 캐시도 비운다
  bumpRecords: () => set((s) => ({ recordsVersion: s.recordsVersion + 1, deliverables: {} })),

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
