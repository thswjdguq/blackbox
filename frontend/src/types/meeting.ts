// ── 회의록 관련 타입 (백엔드 DTO 1:1 매핑) ─────────────────────────────

/** 회의 응답 DTO */
export interface Meeting {
  id: string;
  projectId: string;
  title: string;
  meetingDate: string | null;   // OffsetDateTime → ISO string
  purpose: string | null;       // 회의 목적/안건
  notes: string | null;         // 회의록 내용
  decisions: string | null;     // 결정사항
  checkinCode: string;          // 6자리 체크인 코드
  aiSummary: string | null;     // AI 요약 (저장됨)
  notionPageId: string | null;  // Notion 동기화 URL
  notionSyncedAt: string | null; // 마지막 동기화 시각
  createdBy: string;
  createdAt: string;
}

/** 참석자 응답 DTO */
export interface Attendee {
  userId: string;
  name: string;
  email: string;
  checkedIn: boolean;
  checkedAt: string | null;     // 체크인 시각
}

/** 회의 생성 요청 */
export interface CreateMeetingPayload {
  title: string;
  meetingDate?: string;         // OffsetDateTime 형식: "2026-03-30T14:00:00+09:00"
  purpose?: string;
}

/** 회의 수정 요청 */
export interface UpdateMeetingPayload {
  title?: string;
  meetingDate?: string;
  purpose?: string;
  notes?: string;
  decisions?: string;
}
