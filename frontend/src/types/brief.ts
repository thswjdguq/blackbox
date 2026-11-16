// ── K-31 과제 안내문 ────────────────────────────────────────────────────

export interface BriefPerson { id: string; name: string }

/** 이 안내문을 쓰는 곳. 비어 있지 않으면 지울 수 없다(2.4) */
export interface BriefUsage {
  deliverables: { id: string; title: string; status: string }[];
  pendingProposals: number;
}

export interface Brief {
  id: string;
  title: string;
  /** 서버가 정규화한 본문(NFC, 줄바꿈 \n, 앞뒤 공백 제거) */
  body: string;
  sourceFile: { id: string; fileName: string; version: number } | null;
  createdBy: BriefPerson;
  createdAt: string;
  updatedBy: BriefPerson;
  updatedAt: string;
  bodyHash: string;
  /** 수정 요청에 화면이 읽은 값을 담는다(2.3) */
  version: number;
  usage: BriefUsage;
  /** 걸린 제출물 중 확정·제출된 것이 있으면 true. 고칠 수 없다 */
  locked: boolean;
}

/** 제출물에 걸린 안내문 요약(3장). 건 순서(오래된 것부터) */
export interface BriefSummary {
  id: string;
  title: string;
  bodyHash: string;
  linkedAt: string;
}

/** ProblemDetail의 code(8장). 화면은 code로 안내를 나눈다 */
export type BriefProblemCode = "BRIEF_IN_USE" | "BRIEF_LOCKED" | "BRIEF_CHANGED" | "BRIEFS_TOO_LONG" | "NO_BRIEF";
