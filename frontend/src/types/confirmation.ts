import type { ReviewFile, UserRef } from "./review";

export interface ConfirmationView {
  status: string; canConfirm: boolean; soleContributor: boolean;
  checks: { code: string; passed: boolean; detail: string }[];
  candidate: { reviewId: string; roundNo: number; file: ReviewFile } | null;
  confirmation: { reviewId: string; roundNo: number; file: ReviewFile; confirmedBy: UserRef; confirmedAt: string; fileStillLatest: boolean } | null;
  submission: { channel: string | null; submittedAt: string; note: string | null; recordedBy: UserRef; recordedAt: string } | null;
}
