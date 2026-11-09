export interface PersonalNotification {
  id: string; type: string; projectId: string; actorName: string;
  taskId: string | null; taskTitle: string; fileId: string | null; fileName: string | null;
  deliverableId?: string | null; deliverableTitle?: string | null; reviewId?: string | null; commentId?: string | null;
  fileVersion: number | null; messageId: string | null;
  createdAt: string; readAt: string | null; targetAvailable: boolean;
}
export interface NotificationPage { items: PersonalNotification[]; nextCursor: string | null; unreadCount: number }
export const notificationLabels: Record<string, string> = {
  TASK_COMMENT_POSTED: "업무 피드백", REVIEW_FEEDBACK_RECEIVED: "최종 제출물 피드백",
  REVIEW_FEEDBACK_RESOLVED: "피드백 반영 기록", REVIEW_FEEDBACK_REOPENED: "피드백 다시 확인 요청",
  REVIEW_VERSION_POSTED: "새 검토 버전 등록",
  TASK_ASSIGNED: "업무 배정",
  TASK_FILE_UPLOADED: "결과 파일 등록", TASK_COMPLETION_REQUESTED: "완료 확인 요청",
  TASK_REVISION_REQUESTED: "보완 요청", TASK_OPINION_REQUESTED: "의견 요청", TASK_OPINION_REPLIED: "의견 답변",
};
