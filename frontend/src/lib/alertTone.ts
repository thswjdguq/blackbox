import type { AlertSeverity } from "@/types/vault";

/** 유형은 원인, severity는 대응의 급함을 나타낸다. */
export const ALERT_TONE: Record<AlertSeverity, { label: string; color: string; bg: string }> = {
  HIGH: { label: "긴급", color: "text-red-700 dark:text-red-300", bg: "bg-red-500/10 border-red-500/30" },
  MEDIUM: { label: "주의", color: "text-yellow-800 dark:text-yellow-300", bg: "bg-yellow-500/10 border-yellow-500/30" },
  LOW: { label: "안내", color: "text-blue-700 dark:text-blue-300", bg: "bg-blue-500/10 border-blue-500/30" },
};
