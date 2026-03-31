// ── API Response Types (백엔드 DTO 1:1 매핑) ─────────────────────────────

export interface AssigneeSummary {
  userId: string;
  name: string;
  email: string;
}

export type TaskStatus = "TODO" | "IN_PROGRESS" | "DONE";
export type TaskPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export interface Task {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  tag: string | null;
  dueDate: string | null;       // LocalDate → "YYYY-MM-DD"
  completedAt: string | null;   // OffsetDateTime → ISO string
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  assignees: AssigneeSummary[];
}

export interface ScoreMap {
  [userId: string]: number; // totalScore (BigDecimal → number)
}

// ── Request Types ─────────────────────────────────────────────────────────

export interface CreateTaskPayload {
  title: string;
  description?: string;
  priority?: TaskPriority;
  tag?: string;
  dueDate?: string; // "YYYY-MM-DD"
  assigneeIds?: string[];
}

export interface UpdateTaskStatusPayload {
  status: TaskStatus;
}

// ── Column meta ──────────────────────────────────────────────────────────

export interface KanbanColumn {
  id: TaskStatus;
  label: string;
  accent: string;      // Tailwind bg color class for header dot
  countColor: string;  // Tailwind text color for count badge
}

export const KANBAN_COLUMNS: KanbanColumn[] = [
  { id: "TODO",        label: "To Do",       accent: "bg-slate-500",  countColor: "text-slate-400" },
  { id: "IN_PROGRESS", label: "In Progress", accent: "bg-indigo-500", countColor: "text-indigo-400" },
  { id: "DONE",        label: "Done",        accent: "bg-teal-400",   countColor: "text-teal-400" },
];
