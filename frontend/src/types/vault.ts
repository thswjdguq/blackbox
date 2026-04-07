// ── Hash Vault 관련 타입 ───────────────────────────────────────────────

/** 파일 이력 응답 DTO (목록 / 버전 이력 공통) */
export interface FileRecord {
  id: string;
  fileName: string;
  fileHash: string;             // SHA-256 hex string (64자)
  fileSize: number;             // bytes
  version: number;              // 버전 번호 (1부터 시작)
  uploaderId: string;
  uploaderName: string;
  uploadedAt: string;           // OffsetDateTime → ISO string
}

/** 파일 업로드 응답 DTO */
export interface FileUploadResult {
  id: string;
  fileName: string;
  fileHash: string;
  fileSize: number;
  version: number;
  tamperDetected: boolean;      // true 이면 이전 버전과 해시 불일치 → 경고 표시
  uploadedAt: string;
  notionPageUrl: string | null; // Notion 연동 시 자동 생성된 페이지 URL
}

// ── 경보 관련 타입 ─────────────────────────────────────────────────────

export type AlertType   = "FREE_RIDE" | "OVERLOAD" | "DROPOUT" | "TAMPER";
export type AlertSeverity = "LOW" | "MEDIUM" | "HIGH";

/** 경보 응답 DTO */
export interface Alert {
  id: string;
  userId: string | null;        // null 이면 프로젝트 전체 경보
  alertType: AlertType;
  severity: AlertSeverity;
  message: string;
  isRead: boolean;
  createdAt: string;
}

// ── 기여도 점수 관련 타입 ──────────────────────────────────────────────

/** 기여도 점수 응답 DTO */
export interface ScoreEntry {
  userId: string;
  name: string;
  email: string;
  gitScore: number;
  docScore: number;
  meetingScore: number;
  taskScore: number;
  totalScore: number;
  weightGit: number;
  weightDoc: number;
  weightMeeting: number;
  weightTask: number;
  calculatedAt: string;
}
