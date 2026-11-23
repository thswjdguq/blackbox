export interface PersonalNotification {
  id: string; type: string; projectId: string; actorName: string;
  taskId: string | null; taskTitle: string; fileId: string | null; fileName: string | null;
  deliverableId?: string | null; deliverableTitle?: string | null; reviewId?: string | null; commentId?: string | null;
  fileVersion: number | null; messageId: string | null;
  createdAt: string; readAt: string | null; targetAvailable: boolean;
  /** K-41 5장. 서버(B의 공통 생성기)가 만든 표시 문장·대상·같은 사이트 경로. 화면은 따로 만들지 않는다 */
  message: string;
  target: NotificationTarget;
  /** 유효한 대상 경로를 만들 수 없으면 null */
  path: string | null;
}
export interface NotificationTarget {
  kind: "TASK" | "DELIVERABLE_REVIEW" | "UNKNOWN" | string;
  taskId: string | null; deliverableId: string | null; reviewId: string | null;
  messageId: string | null; commentId: string | null; fileId: string | null;
}
export interface NotificationPage { items: PersonalNotification[]; nextCursor: string | null; unreadCount: number }
