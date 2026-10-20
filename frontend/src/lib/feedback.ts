import type { FeedbackItem } from "@/types/taskMessage";

/** A reply alone does not confirm that requested changes have been reflected. */
export function isFeedbackFinished(item: FeedbackItem): boolean {
  return (item.kind === "OPINION" && item.status === "ANSWERED")
    || (item.kind === "REVIEW" && item.status === "RESOLVED");
}
