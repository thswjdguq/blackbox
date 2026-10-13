import api from "@/lib/api";
import type { AgentEvent } from "@/types/agent";

/** 스트림이 시작되기 전의 실패(HTTP 상태와 detail), 또는 끝 이벤트 없이 끊긴 것(status 0) */
export class AgentRunError extends Error {
  constructor(public status: number, detail: string) {
    super(detail);
  }
}

// 서버의 실행 시간 상한(120초)보다 조금 길게 기다린다
const GIVE_UP_MS = 130_000;

/**
 * 받은 글 덩어리에서 이벤트를 하나씩 꺼낸다. 덩어리가 어디서 잘려 와도 된다.
 * 서버는 `event:step`처럼 콜론 뒤에 공백 없이 보내고 표준은 공백이 있어도 같으므로 둘 다 받는다.
 * 프록시가 줄 끝을 \r\n으로 바꿔도 받는다. 주석 줄(`:start`)과 읽지 못한 data는 건너뛴다.
 */
export function createEventParser(on: (e: AgentEvent) => void) {
  let buffer = "";
  return (chunk: string) => {
    // \r과 \n 사이에서 잘려도 다음 덩어리와 붙인 뒤에 바꾼다
    buffer = (buffer + chunk).replace(/\r\n/g, "\n");
    let end: number;
    while ((end = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, end);
      buffer = buffer.slice(end + 2);
      let event = "";
      let data = "";
      for (const line of block.split("\n")) {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:")) data += line.slice(5).trim();
      }
      if (!event) continue;
      try {
        on({ event, data: JSON.parse(data) } as AgentEvent);
      } catch {
        // 깨진 이벤트 하나 때문에 실행 전체를 버리지 않는다
      }
    }
  };
}

/**
 * POST /runs를 보내고 이벤트를 차례로 넘긴다. EventSource는 POST를 못 보내 fetch로 읽는다.
 * 던지는 것: AgentRunError(message를 그대로 보임), 그 밖의 예외(요청이 나가지 못함).
 */
export async function runAgent(projectId: string, skill: string, input: object, on: (e: AgentEvent) => void) {
  const giveUp = new AbortController();
  const timer = setTimeout(() => giveUp.abort(), GIVE_UP_MS);
  const send = () =>
    fetch(`/api/projects/${projectId}/agent/runs`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ skill, input }),
      signal: giveUp.signal,
    });
  try {
    let res = await send();
    if (res.status === 401) {
      // fetch는 공용 클라이언트의 자동 갱신을 타지 않는다
      await api.post("/auth/refresh");
      res = await send();
    }
    if (!res.ok) {
      const problem = await res.json().catch(() => ({}));
      throw new AgentRunError(res.status, problem.detail ?? "요청을 처리할 수 없습니다");
    }
    let ended = false;
    const feed = createEventParser((e) => {
      if (e.event === "done" || e.event === "error") ended = true;
      on(e);
    });
    const reader = res.body!.pipeThrough(new TextDecoderStream()).getReader();
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      feed(value);
    }
    if (!ended) throw new AgentRunError(0, "연결이 끊겼습니다. 만들어진 제안은 제안 탭에 있습니다");
  } catch (e) {
    if (giveUp.signal.aborted) throw new AgentRunError(0, "응답이 오지 않습니다. 잠시 뒤 다시 시도해주세요");
    throw e;
  } finally {
    clearTimeout(timer);
  }
}
