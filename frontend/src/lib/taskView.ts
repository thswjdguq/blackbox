import type { Task } from "@/types/task";

export type TaskScope = "mine" | "all" | "review" | "unassigned";
export interface TaskView { view: "list" | "board"; scope: TaskScope; showDone: boolean }

export function defaultTaskView(role: string): TaskView {
  return { view: "list", scope: role === "MEMBER" ? "mine" : "all", showDone: false };
}

export function restoreTaskView(raw: string | null, role: string): TaskView {
  const defaults = defaultTaskView(role);
  try {
    const value = JSON.parse(raw ?? "null");
    if (!value || !["list", "board"].includes(value.view) || !["mine", "all", "review", "unassigned"].includes(value.scope) || typeof value.showDone !== "boolean") return defaults;
    return { view: value.view, scope: role === "OBSERVER" && ["mine", "review"].includes(value.scope) ? "all" : value.scope, showDone: value.showDone };
  } catch { return defaults; }
}

export function inTaskScope(task: Task, scope: TaskScope, userId: string, reviewIds: Set<string>): boolean {
  if (scope === "mine") return task.assignees.some(a => a.userId === userId);
  if (scope === "review") return task.status !== "DONE" && !!task.completionRequestedAt && reviewIds.has(task.id);
  if (scope === "unassigned") return task.assignees.length === 0;
  return true;
}
