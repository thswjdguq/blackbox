export interface TaskMessage {
  id: string; authorId: string; authorName: string; body: string; kind: "COMMENT" | "REQUEST" | "REPLY";
  recipientId: string | null; recipientName: string | null; parentId: string | null;
  fileId: string | null; fileName: string | null; fileVersion: number | null;
  answeredAt: string | null; createdAt: string;
}
export interface FeedbackItem {
  kind: "OPINION" | "COMPLETION" | "REVISION" | "COMMENT" | "REVIEW"; taskId: string | null; taskTitle: string;
  messageId: string | null; body: string; fromName: string | null; createdAt: string;
  status?: string; response?: string | null; deliverableId?: string | null; reviewId?: string | null;
}
export interface FeedbackInbox { received: FeedbackItem[]; sent: FeedbackItem[]; revisions: FeedbackItem[] }
