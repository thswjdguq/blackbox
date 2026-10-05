"use client";

import { FormEvent, useState } from "react";
import { Loader2, X } from "lucide-react";
import { apiError } from "@/lib/apiError";
import type { Deliverable, SaveDeliverablePayload } from "@/types/deliverable";

export interface OwnerCandidate {
  userId: string;
  name: string;
}

const FIELD =
  "mt-1.5 w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm text-bb-text " +
  "placeholder-slate-400 focus:outline-none focus:border-indigo-500";

export default function DeliverableFormModal({
  initial,
  owners,
  onSave,
  onClose,
}: {
  /** null 이면 새 제출물 */
  initial: Deliverable | null;
  /** 제출 담당자로 지정할 수 있는 팀장·팀원 (관찰자 제외) */
  owners: OwnerCandidate[];
  onSave: (payload: SaveDeliverablePayload) => Promise<void>;
  onClose: () => void;
}) {
  const [title, setTitle] = useState(initial?.title ?? "");
  const [dueDate, setDueDate] = useState(initial?.dueDate ?? "");
  const [ownerId, setOwnerId] = useState(initial?.ownerId ?? "");
  const [submissionMethod, setSubmissionMethod] = useState(initial?.submissionMethod ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  // 기존 담당자가 관찰자로 바뀌었거나 나간 경우
  const ownerMissing = ownerId !== "" && !owners.some((o) => o.userId === ownerId);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !dueDate) return;
    setSaving(true);
    setError("");
    try {
      await onSave({
        title: title.trim(),
        dueDate,
        description,
        submissionMethod,
        ownerId: ownerId || null,
      });
    } catch (err) {
      setError(apiError(err));
      setSaving(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="deliverable-form-title"
    >
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-xl max-h-[90vh] overflow-auto bg-bb-surface border border-bb-border rounded-xl p-6 space-y-4"
      >
        <div className="flex items-center justify-between">
          <h2 id="deliverable-form-title" className="text-base font-semibold text-bb-text">
            {initial ? "제출물 수정" : "새 제출물"}
          </h2>
          <button type="button" onClick={onClose} disabled={saving} aria-label="닫기" className="text-bb-text2 hover:text-bb-text">
            <X size={18} />
          </button>
        </div>

        {error && (
          <p role="alert" className="p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
            {error}
          </p>
        )}

        <label className="block text-sm text-bb-text">
          제출물 이름 *
          <input
            autoFocus
            required
            maxLength={255}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="예: 중간발표 자료"
            className={FIELD}
          />
        </label>

        <label className="block text-sm text-bb-text">
          제출 기한 *
          <input type="date" required value={dueDate} onChange={(e) => setDueDate(e.target.value)} className={FIELD} />
        </label>

        <label className="block text-sm text-bb-text">
          제출 담당자
          <select value={ownerId} onChange={(e) => setOwnerId(e.target.value)} className={FIELD}>
            <option value="">나중에 지정</option>
            {ownerMissing && (
              <option value={ownerId}>현재 담당자는 참여 팀원이 아닙니다. 다시 지정하세요.</option>
            )}
            {owners.map((o) => (
              <option key={o.userId} value={o.userId}>
                {o.name}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm text-bb-text">
          제출 경로
          <input
            maxLength={500}
            value={submissionMethod}
            onChange={(e) => setSubmissionMethod(e.target.value)}
            placeholder="예: 학교 LMS 과제함"
            className={FIELD}
          />
        </label>

        <label className="block text-sm text-bb-text">
          설명
          <textarea
            rows={3}
            maxLength={5000}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className={`${FIELD} resize-none`}
          />
        </label>

        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={onClose} disabled={saving} className="px-4 py-2 text-sm text-bb-text2 hover:text-bb-text">
            취소
          </button>
          <button
            type="submit"
            disabled={saving || !title.trim() || !dueDate}
            className="flex items-center gap-1.5 px-4 py-2 bg-bb-primary hover:bg-bb-primary-h disabled:opacity-50 text-white text-sm rounded-lg"
          >
            {saving && <Loader2 size={14} className="animate-spin" />}
            저장
          </button>
        </div>
      </form>
    </div>
  );
}
