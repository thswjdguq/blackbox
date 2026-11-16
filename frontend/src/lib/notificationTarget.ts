import { notificationLabels, type PersonalNotification } from "@/types/notification";

/**
 * 알림을 눌렀을 때 할 일(K-41 5장). 서버가 보낸 target·path를 따르고, 새 칸이 없는 옛 응답은 지금까지의 규칙을 쓴다.
 * - task: 이 자리에서 업무 창을 연다(messageId·fileId 위치로)
 * - path: 같은 프로젝트의 화면으로 이동한다
 */
export type NotificationAction =
  | { kind: "task"; taskId: string; messageId: string | null; fileId: string | null }
  | { kind: "path"; href: string }
  | { kind: "none" };

/** 서버가 보낸 path가 지금 프로젝트 안의 같은 사이트 경로인가. 아니면 이동하지 않는다 */
export function samePath(path: string | null | undefined, projectId: string): boolean {
  if (!path || path.length > 2000) return false;
  if (!path.startsWith(`/projects/${projectId}/`)) return false;
  if (path.includes("//") || path.includes("\\") || /[\u0000-\u001F\u007F]/.test(path)) return false;
  return true;
}

const withComment = (href: string, commentId: string | null | undefined) =>
  href.includes("#") || !commentId ? href : `${href}#comment-${commentId}`;

export function notificationAction(item: PersonalNotification, projectId: string): NotificationAction {
  if (!item.targetAvailable) return { kind: "none" };
  const t = item.target;
  if (t) {
    if (t.kind === "TASK" && t.taskId) return { kind: "task", taskId: t.taskId, messageId: t.messageId ?? item.messageId, fileId: t.fileId ?? item.fileId };
    // 피드백 위치는 path에 #comment-가 들어 있으면 그대로, 없으면 commentId로 붙인다(두 번 붙지 않게)
    if (samePath(item.path, projectId)) return { kind: "path", href: withComment(item.path!, t.commentId) };
    return { kind: "none" };
  }
  // 새 칸이 없는 옛 서버 응답
  if (item.deliverableId) return { kind: "path", href: withComment(`/projects/${projectId}/deliverables/${item.deliverableId}?tab=review`, item.commentId) };
  if (item.taskId) return { kind: "task", taskId: item.taskId, messageId: item.messageId, fileId: item.fileId };
  return { kind: "none" };
}

/** 보일 문장. 서버가 만든 message가 있으면 그대로, 옛 응답이면 지금까지의 문장 */
export function notificationText(item: PersonalNotification): string {
  return item.message ?? `${item.actorName}님 · ${notificationLabels[item.type] ?? "업무 소식"}`;
}

/** 버튼과 대상이 없을 때의 안내(화면 안내라 화면이 갖는다) */
export function notificationButton(item: PersonalNotification): { open: string; gone: string } {
  const kind = item.target?.kind ?? (item.deliverableId ? "DELIVERABLE_REVIEW" : item.taskId ? "TASK" : "UNKNOWN");
  if (kind === "TASK") return { open: "관련 업무 열기", gone: "삭제된 업무입니다." };
  if (kind === "DELIVERABLE_REVIEW") return { open: "검토 열기", gone: "삭제된 대상입니다." };
  return { open: "열기", gone: "삭제된 대상입니다." };
}
