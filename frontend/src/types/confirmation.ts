import type { ReviewFile, UserRef } from "./review";

export interface ConfirmationView {
  status: string; canConfirm: boolean; soleContributor: boolean;
  checks: { code: string; passed: boolean; detail: string }[];
  candidate: { reviewId: string; roundNo: number; file: ReviewFile } | null;
  confirmation: { reviewId: string; roundNo: number; file: ReviewFile; confirmedBy: UserRef; confirmedAt: string; fileStillLatest: boolean } | null;
  submission: { channel: string | null; submittedAt: string; note: string | null; recordedBy: UserRef; recordedAt: string } | null;
  /** K-20 0.3. 취소한 확정, 오래된 것부터. 0.3 이전 서버는 보내지 않는다 */
  cancellations?: ConfirmationCancellation[];
}

export interface ConfirmationCancellation {
  reviewId: string; roundNo: number;
  confirmedBy: UserRef; confirmedAt: string;
  cancelledBy: UserRef; cancelledAt: string;
  reason: string;
}
