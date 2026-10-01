"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { AlertCircle, Clock, Loader2, Plus, RefreshCw, TriangleAlert, X } from "lucide-react";
import api from "@/lib/api";
import { apiError } from "@/lib/apiError";
import { getComments, getReviewSummary, isReviewApiMissing, openReviewRound } from "@/lib/api/review";
import type { DeliverableStatus, ReviewComment, ReviewSummary } from "@/types/review";
import type { Task } from "@/types/task";
import ReviewRoundCard, { fmtRelative } from "./ReviewRoundCard";

/** GET /projects/{id}/files 응답 (FileHistoryResponse). 같은 이름의 모든 버전이 들어 있다 */
interface VaultFile {
  id: string;
  fileName: string;
  fileHash: string;
  version: number;
  uploaderId: string;
  uploaderName: string;
  uploadedAt: string;
}

const STATUS_BADGE: Record<DeliverableStatus, { label: string; cls: string }> = {
  DRAFT: { label: "초안", cls: "bg-bb-surface2 text-bb-text2" },
  IN_REVIEW: { label: "검토 중", cls: "bg-indigo-500/15 text-indigo-400" },
  CONFIRMED: { label: "확정", cls: "bg-teal-500/15 text-teal-400" },
  SUBMITTED: { label: "제출 완료", cls: "bg-green-500/15 text-green-400" },
};

export default function ReviewPanel({
  projectId,
  deliverableId,
  canWrite,
  myUserId,
  tasks,
  onTasksChanged,
}: {
  projectId: string;
  deliverableId: string;
  canWrite: boolean;
  myUserId: string | null;
  tasks: Task[];
  /** 피드백을 업무로 만들면 상세 화면의 업무·진척도 다시 불러와야 한다 */
  onTasksChanged: () => void;
}) {
  const [summary, setSummary] = useState<ReviewSummary | null>(null);
  const [comments, setComments] = useState<Record<string, ReviewComment[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notReady, setNotReady] = useState(false);
  const [showOpen, setShowOpen] = useState(false);

  const load = useCallback(async () => {
    setError("");
    try {
      const { data } = await getReviewSummary(projectId, deliverableId);
      // 코멘트 목록은 회차 단위 경로라 회차마다 요청한다 (K-11 1장)
      const lists = await Promise.all(
        data.rounds.map((r) => getComments(projectId, deliverableId, r.id).then((res) => [r.id, res.data] as const))
      );
      setSummary(data);
      setComments(Object.fromEntries(lists));
      setNotReady(false);
    } catch (err) {
      if (isReviewApiMissing(err)) setNotReady(true);
      else setError(apiError(err, "검토 정보를 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [projectId, deliverableId]);

  useEffect(() => {
    load();
  }, [load]);

  const handleChanged = () => {
    load();
    onTasksChanged();
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-32 gap-2 text-bb-text2">
        <Loader2 size={18} className="animate-spin" />
        <span className="text-sm">검토 정보를 불러오는 중...</span>
      </div>
    );
  }

  if (notReady) {
    return (
      <div className="flex flex-col items-center gap-2 py-16 text-center">
        <Clock size={28} className="text-bb-text2 opacity-50" />
        <p className="text-sm text-bb-text">검토 기능은 서버 준비 중입니다</p>
        <p className="text-xs text-bb-text2">검토 회차·피드백 API(K-10·K-11)가 구현되면 이 탭에서 바로 사용할 수 있습니다.</p>
      </div>
    );
  }

  if (error || !summary) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <AlertCircle size={28} className="text-red-400" />
        <p className="text-sm text-bb-text2">{error || "검토 정보를 불러오지 못했습니다."}</p>
        <button
          onClick={() => {
            setLoading(true);
            load();
          }}
          className="flex items-center gap-1.5 px-4 py-2 text-sm text-bb-text2 hover:text-bb-text border border-bb-border rounded-lg"
        >
          <RefreshCw size={14} />
          다시 시도
        </button>
      </div>
    );
  }

  const locked = summary.deliverableStatus === "CONFIRMED" || summary.deliverableStatus === "SUBMITTED";
  const latest = summary.rounds[summary.rounds.length - 1] ?? null;
  const badge = STATUS_BADGE[summary.deliverableStatus];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className={`text-xs px-2 py-0.5 rounded-full ${badge.cls}`}>{badge.label}</span>
          {summary.unresolvedCount > 0 && (
            <span className="text-xs px-2 py-0.5 rounded-full bg-amber-500/15 text-amber-400">
              미해결 피드백 {summary.unresolvedCount}건
            </span>
          )}
        </div>
        {canWrite && !locked && (
          <button
            onClick={() => setShowOpen(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs text-white bg-bb-primary hover:bg-bb-primary-h rounded-lg"
          >
            <Plus size={13} />
            {latest ? "새 검토 회차 열기" : "검토 요청"}
          </button>
        )}
      </div>

      {/* 수정본 안내: 수정 요청을 받은 뒤 다음에 할 일 */}
      {latest?.decision === "CHANGES_REQUESTED" && !locked && (
        <div className="flex items-start gap-3 p-4 bg-amber-500/10 border border-amber-500/30 rounded-xl">
          <TriangleAlert size={16} className="text-amber-400 mt-0.5 shrink-0" />
          <div className="text-sm">
            <p className="font-semibold text-amber-300">{latest.roundNo}회차에서 수정 요청을 받았습니다</p>
            <p className="mt-1 text-xs text-amber-400/90">
              {summary.unresolvedCount > 0 && `미해결 피드백 ${summary.unresolvedCount}건을 반영하고 해결 처리한 뒤, `}
              수정본을{latest.file ? ` 같은 이름(${latest.file.fileName})으로` : ""}{" "}
              <Link href={`/projects/${projectId}/vault`} className="underline">
                파일 금고
              </Link>
              에 올리고 새 검토 회차를 여세요.
            </p>
          </div>
        </div>
      )}

      {summary.rounds.length === 0 ? (
        <div className="py-12 text-center">
          <p className="text-sm text-bb-text">아직 검토 회차가 없습니다</p>
          <p className="mt-1 text-xs text-bb-text2">
            초안을{" "}
            <Link href={`/projects/${projectId}/vault`} className="underline">
              파일 금고
            </Link>
            에 올린 뒤 검토를 요청하세요. 파일 없이 중간 검토를 열 수도 있습니다.
          </p>
        </div>
      ) : (
        // 최신 회차를 위에 둔다
        [...summary.rounds].reverse().map((r) => (
          <ReviewRoundCard
            key={r.id}
            projectId={projectId}
            deliverableId={deliverableId}
            round={r}
            comments={comments[r.id] ?? []}
            isLatest={r.id === latest?.id}
            locked={locked}
            unresolvedCount={summary.unresolvedCount}
            canWrite={canWrite}
            myUserId={myUserId}
            tasks={tasks}
            onChanged={handleChanged}
          />
        ))
      )}

      {showOpen && (
        <OpenRoundDialog
          projectId={projectId}
          deliverableId={deliverableId}
          previousFileName={latest?.file?.fileName ?? null}
          hasCurrentApproval={summary.rounds.some((r) => r.decision === "APPROVED" && r.isCurrentBasis)}
          onClose={() => setShowOpen(false)}
          onOpened={() => {
            setShowOpen(false);
            load();
          }}
        />
      )}
    </div>
  );
}

function OpenRoundDialog({
  projectId,
  deliverableId,
  previousFileName,
  hasCurrentApproval,
  onClose,
  onOpened,
}: {
  projectId: string;
  deliverableId: string;
  previousFileName: string | null;
  hasCurrentApproval: boolean;
  onClose: () => void;
  onOpened: () => void;
}) {
  const [files, setFiles] = useState<VaultFile[] | null>(null);
  const [loadError, setLoadError] = useState("");
  const [fileName, setFileName] = useState("");
  const [fileId, setFileId] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    api
      .get<VaultFile[]>(`/projects/${projectId}/files`)
      .then(({ data }) => setFiles(data))
      .catch((err) => setLoadError(apiError(err, "파일 목록을 불러오지 못했습니다.")));
  }, [projectId]);

  // 파일 금고의 버전 사슬은 같은 이름 기준이다 (A-10 결정 (다))
  const byName = useMemo(() => {
    const map = new Map<string, VaultFile[]>();
    for (const f of files ?? []) map.set(f.fileName, [...(map.get(f.fileName) ?? []), f]);
    for (const list of map.values()) list.sort((a, b) => b.version - a.version);
    return map;
  }, [files]);

  useEffect(() => {
    if (!files || fileName) return;
    const initial = previousFileName && byName.has(previousFileName) ? previousFileName : [...byName.keys()][0];
    if (initial) {
      setFileName(initial);
      setFileId(byName.get(initial)![0].id);
    }
  }, [files, byName, fileName, previousFileName]);

  const versions = fileName ? byName.get(fileName) ?? [] : [];
  const differentName = previousFileName != null && fileName !== "" && fileName !== previousFileName;

  const submit = async () => {
    setSaving(true);
    setError("");
    try {
      await openReviewRound(projectId, deliverableId, fileId ? { fileId } : {});
      onOpened();
    } catch (err) {
      setError(apiError(err, "검토 회차를 열지 못했습니다. 다시 시도해주세요."));
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" role="dialog" aria-modal="true" aria-labelledby="open-round-title">
      <section className="w-full max-w-lg bg-bb-surface border border-bb-border rounded-xl p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h2 id="open-round-title" className="text-base font-semibold text-bb-text">
            검토 회차 열기
          </h2>
          <button onClick={onClose} disabled={saving} aria-label="닫기" className="text-bb-text2 hover:text-bb-text">
            <X size={18} />
          </button>
        </div>

        {error && <p role="alert" className="p-3 bg-red-500/10 border border-red-500/20 rounded-lg text-sm text-red-400">{error}</p>}
        {loadError && <p role="alert" className="text-sm text-red-400">{loadError}</p>}
        {!files && !loadError && <p className="text-sm text-bb-text2">파일 목록을 불러오는 중...</p>}

        {files && (
          <>
            <label className="block text-sm text-bb-text">
              검토할 파일
              <select
                value={fileName}
                onChange={(e) => {
                  setFileName(e.target.value);
                  setFileId(e.target.value ? byName.get(e.target.value)![0].id : "");
                }}
                className="mt-1.5 w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm text-bb-text"
              >
                <option value="">파일 없이 중간 검토</option>
                {[...byName.keys()].map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            </label>

            {versions.length > 0 && (
              <label className="block text-sm text-bb-text">
                버전
                <select
                  value={fileId}
                  onChange={(e) => setFileId(e.target.value)}
                  className="mt-1.5 w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2 text-sm text-bb-text"
                >
                  {versions.map((v, i) => (
                    <option key={v.id} value={v.id}>
                      v{v.version} · {v.fileHash.slice(0, 7)} · {v.uploaderName} · {fmtRelative(v.uploadedAt)}
                      {i === 0 ? " (최신)" : ""}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {files.length === 0 && (
              <p className="text-xs text-bb-text2">
                파일 금고에 올린 파일이 없습니다. 파일 없이 중간 검토를 열거나{" "}
                <Link href={`/projects/${projectId}/vault`} className="underline">
                  파일 금고
                </Link>
                에 먼저 올리세요.
              </p>
            )}
            {differentName && (
              <p className="text-xs text-amber-400">
                이전 회차({previousFileName})와 다른 파일입니다. 같은 파일의 수정본이라면 같은 이름으로 올려야 새 버전으로 이어집니다.
              </p>
            )}
            {!fileId && <p className="text-xs text-bb-text2">파일 없는 회차의 승인은 최종 확정 근거가 되지 않습니다.</p>}
            {hasCurrentApproval && (
              <p className="text-xs text-amber-400">새 회차를 열면 지금 승인은 확정 근거에서 빠집니다.</p>
            )}
          </>
        )}

        <div className="flex justify-end gap-2">
          <button onClick={onClose} disabled={saving} className="px-4 py-2 text-sm text-bb-text2 hover:text-bb-text">
            취소
          </button>
          <button
            onClick={submit}
            disabled={saving || !files}
            className="flex items-center gap-1.5 px-4 py-2 text-sm text-white bg-bb-primary hover:bg-bb-primary-h disabled:opacity-50 rounded-lg"
          >
            {saving && <Loader2 size={14} className="animate-spin" />}
            회차 열기
          </button>
        </div>
      </section>
    </div>
  );
}
