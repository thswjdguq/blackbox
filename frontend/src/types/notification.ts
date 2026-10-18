export interface PersonalNotification {
  id: string; type: string; projectId: string; actorName: string;
  taskId: string; taskTitle: string; fileId: string | null; fileName: string | null;
  fileVersion: number | null; messageId: string | null;
  createdAt: string; readAt: string | null; targetAvailable: boolean;
}
export interface NotificationPage { items: PersonalNotification[]; nextCursor: string | null; unreadCount: number }
export const notificationLabels: Record<string, string> = {
  TASK_ASSIGNED: "업무 배정",
  TASK_FILE_UPLOADED: "결과 파일 등록", TASK_COMPLETION_REQUESTED: "완료 확인 요청",
  TASK_REVISION_REQUESTED: "보완 요청", TASK_OPINION_REQUESTED: "의견 요청", TASK_OPINION_REPLIED: "의견 답변",
};
