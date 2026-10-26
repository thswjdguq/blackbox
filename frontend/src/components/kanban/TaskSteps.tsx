"use client";
import { useState } from "react";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import { checklistItems, toggleChecklistItem } from "@/lib/taskChecklist";
import type { Task } from "@/types/task";

export default function TaskSteps({ task, disabled, onChanged, onBusy }: {
  task: Task; disabled: boolean; onChanged: (task: Task) => void; onBusy: (busy: boolean) => void;
}) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const description = task.description ?? "";
  const items = checklistItems(description);
  const stepLines = new Set(items.map(item => item.index));
  const prose = description.split("\n").filter((_, i) => !stepLines.has(i)).join("\n").trim();
  const toggle = async (index: number, checked: boolean) => {
    setBusy(true); onBusy(true); setError("");
    try {
      const { data } = await api.patch<Task>(`/projects/${task.projectId}/tasks/${task.id}`, { description: toggleChecklistItem(description, index, checked) });
      onChanged(data);
    } catch (e) { setError(apiError(e, "진행 단계를 저장하지 못했습니다.")); }
    finally { setBusy(false); onBusy(false); }
  };
  if (!description) return null;
  return <section className="space-y-3">
    {prose && <p className="text-sm text-bb-text2 whitespace-pre-wrap">{prose}</p>}
    {items.length > 0 && <><div className="flex justify-between text-sm"><h3 className="font-semibold">작은 수행 단계</h3><span className="text-bb-text2">{items.filter(i => i.checked).length}/{items.length}</span></div>
      <ul className="space-y-2">{items.map(item => <li key={item.index}><label className="flex gap-2 text-sm"><input type="checkbox" checked={item.checked} disabled={disabled || busy} onChange={e => toggle(item.index, e.target.checked)} /><span className={item.checked ? "text-bb-text2 line-through" : ""}>{item.text}</span></label></li>)}</ul>
      <p className="text-xs text-bb-text2">진행을 정리하는 체크리스트입니다. 각 단계마다 승인을 받지 않습니다.</p></>}
    {error && <p role="alert" className="text-sm text-red-600">{error}</p>}
  </section>;
}
