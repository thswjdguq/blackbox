import type { PersonalNotification } from "@/types/notification";

/**
 * 알림을 눌렀을 때 할 일(K-41 5장). 문장·대상·경로는 서버(B의 공통 생성기)가 정하고 화면은 따르기만 한다.
 * - task: 이 자리에서 업무 창을 연다(target의 messageId·fileId 위치로)
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

/** 필수 칸(message·target·path)이 빠지거나 모양이 틀린 응답. 옛 문구로 다시 만들지 않고 이동을 막는다(K-41 5장) */
export function notificationBroken(item: PersonalNotification): boolean {
  const t = item.target as unknown;
  return typeof item.message !== "string" || item.message.trim() === ""
    || typeof t !== "object" || t === null || typeof (t as { kind?: unknown }).kind !== "string"
    || (item.path !== null && typeof item.path !== "string");
}

/** 응답이 깨졌을 때 문장 자리에 보이는 화면 안내 */
export const BROKEN_NOTICE = "알림 내용을 불러오지 못했습니다. 새로고침해 주세요.";

export function notificationAction(item: PersonalNotification, projectId: string): NotificationAction {
  if (notificationBroken(item) || !item.targetAvailable) return { kind: "none" };
  const t = item.target;
  if (t.kind === "TASK" && t.taskId) return { kind: "task", taskId: t.taskId, messageId: t.messageId, fileId: t.fileId };
  // 피드백 위치는 path에 #comment-가 들어 있으면 그대로, 없으면 commentId로 붙인다(두 번 붙지 않게)
  if (samePath(item.path, projectId)) {
    const href = item.path!;
    return { kind: "path", href: href.includes("#") || !t.commentId ? href : `${href}#comment-${t.commentId}` };
  }
  return { kind: "none" };
}

/** 버튼과 대상이 없을 때의 안내(서버 문장이 아닌 화면 안내) */
export function notificationButton(item: PersonalNotification): { open: string; gone: string } {
  if (notificationBroken(item)) return { open: "열기", gone: "" };
  if (item.target.kind === "TASK") return { open: "관련 업무 열기", gone: "삭제된 업무입니다." };
  if (item.target.kind === "DELIVERABLE_REVIEW") return { open: "검토 열기", gone: "삭제된 대상입니다." };
  return { open: "열기", gone: "삭제된 대상입니다." };
}
