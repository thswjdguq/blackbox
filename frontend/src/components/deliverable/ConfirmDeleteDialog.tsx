"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { apiError } from "@/lib/apiError";

export default function ConfirmDeleteDialog({
  title,
  onConfirm,
  onClose,
}: {
  title: string;
  onConfirm: () => Promise<void>;
  onClose: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const handleConfirm = async () => {
    setBusy(true);
    setError("");
    try {
      await onConfirm();
    } catch (err) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      setError(
        status === 409
          ? "연결된 업무가 있어 삭제할 수 없습니다. 업무 연결을 먼저 해제하세요."
          : apiError(err, "삭제에 실패했습니다. 다시 시도해주세요.")
      );
      setBusy(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-delete-title"
    >
      <section className="w-full max-w-md bg-bb-surface border border-bb-border rounded-xl p-6">
        <h2 id="confirm-delete-title" className="text-base font-semibold text-bb-text">
          {title} 삭제
        </h2>
        <p className="my-3 text-sm text-bb-text2">
          연결된 업무가 있으면 삭제할 수 없습니다. 삭제한 항목은 복구할 수 없습니다.
        </p>
        {error && (
          <p role="alert" className="mb-3 p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <button onClick={onClose} disabled={busy} className="px-4 py-2 text-sm text-bb-text2 hover:text-bb-text">
            취소
          </button>
          <button
            onClick={handleConfirm}
            disabled={busy}
            className="flex items-center gap-1.5 px-4 py-2 bg-red-500/80 hover:bg-red-500 disabled:opacity-50 text-white text-sm rounded-lg"
          >
            {busy && <Loader2 size={14} className="animate-spin" />}
            삭제
          </button>
        </div>
      </section>
    </div>
  );
}
