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
  isResolved: boolean;
  createdAt: string;
  resolvedAt: string | null;
  resolveReason: string | null;
}

// ── 기여도 점수 관련 타입 ──────────────────────────────────────────────

/** 기여도 참여 여부 응답 DTO */
export interface ScoreEntry {
  userId: string;
  name: string;
  email: string;
  taskParticipated: boolean;
  meetingParticipated: boolean;
  fileParticipated: boolean;
  actionParticipated: boolean;
  /** FULL | PARTIAL | NONE */
  participationLevel: "FULL" | "PARTIAL" | "NONE";
  calculatedAt: string;
  /** K-40 2장. 같은 재계산에서 함께 저장된 항목. 새 기준으로 아직 계산하지 않은 행이나 이전 서버는 null·생략 */
  items?: ScoreItem[] | null;
  criteriaVersion?: string | null;
  period?: { from: string; to: string } | null;
}

/** 기여도 항목. code는 늘 수 있으므로 모르는 값도 label·count로 보인다 */
export interface ScoreItem {
  code: string;
  label: string;
  description: string;
  count: number;
  /** 비율로 판정하는 항목(회의 참석)에만 있다 */
  total?: number;
  passed: boolean;
  /** true면 판정(participationLevel)에 들어간다 */
  countsTowardLevel: boolean;
  details?: { code: string; label: string; count: number }[];
}
