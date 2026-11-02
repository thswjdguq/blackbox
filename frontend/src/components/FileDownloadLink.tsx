"use client";

import { useState, type ReactNode } from "react";
import { blobApiError } from "@/lib/apiError";
import { downloadFile } from "@/lib/download";

/**
 * 링크처럼 보이는 내려받기 버튼. 로그인 갱신을 거쳐 받는다(lib/download.ts).
 * 모양은 부르는 곳의 className을 그대로 쓰고, 실패하면 바로 아래에 서버의 사유를 보인다.
 */
export default function FileDownloadLink({ fileId, fileName, className = "", label, children }: {
  fileId: string;
  fileName: string;
  className?: string;
  /** 글자 없는 아이콘 버튼일 때의 이름(aria-label·title) */
  label?: string;
  children: ReactNode;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const download = async () => {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await downloadFile(fileId, fileName);
    } catch (err) {
      setError(await blobApiError(err, "내려받지 못했습니다. 다시 시도해주세요."));
    } finally {
      setBusy(false);
    }
  };
  return (
    <>
      <button type="button" onClick={download} disabled={busy} aria-busy={busy} aria-label={label} title={label} className={`text-left disabled:opacity-60 ${className}`}>
        {children}
      </button>
      {error && <span role="alert" className="block text-xs text-red-600 dark:text-red-400">{error}</span>}
    </>
  );
}
