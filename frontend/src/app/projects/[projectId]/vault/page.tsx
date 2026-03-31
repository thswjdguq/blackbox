"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import { FileRecord, FileUploadResult } from "@/types/vault";
import {
  Files,
  Upload,
  Download,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Hash,
  ChevronDown,
  ChevronRight,
  AlertCircle,
  Loader2,
  FileIcon,
} from "lucide-react";

// ── 바이트 → 사람이 읽기 좋은 크기 ────────────────────────────────────
function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
}

// ── 날짜 포맷 헬퍼 ─────────────────────────────────────────────────────
function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("ko-KR", {
    month: "short", day: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

// ── 해시 단축 표시 (앞 8자 + ... + 뒤 8자) ─────────────────────────────
function shortHash(hash: string): string {
  return `${hash.slice(0, 8)}…${hash.slice(-8)}`;
}

// ── 파일 확장자로 아이콘 색상 ──────────────────────────────────────────
function fileColor(name: string): string {
  const ext = name.split(".").pop()?.toLowerCase() ?? "";
  if (["pdf"].includes(ext)) return "text-red-400";
  if (["png", "jpg", "jpeg", "gif", "svg"].includes(ext)) return "text-purple-400";
  if (["ts", "tsx", "js", "jsx", "py", "java"].includes(ext)) return "text-yellow-400";
  if (["md", "txt"].includes(ext)) return "text-bb-text";
  return "text-indigo-400";
}

// ── 파일 이력 아이템 ───────────────────────────────────────────────────
function HistoryItem({
  record,
  projectId,
  isTampered,
}: {
  record: FileRecord;
  projectId: string;
  isTampered: boolean;
}) {
  const handleDownload = () => {
    window.open(`/api/files/${record.id}/download`, "_blank");
  };

  return (
    <div
      className={`flex items-center gap-4 p-4 rounded-xl border transition-all ${
        isTampered
          ? "bg-red-500/5 border-red-500/30"
          : "bg-bb-surface/50 border-bb-border/50"
      }`}
    >
      {/* 타임라인 점 */}
      <div className="flex flex-col items-center self-stretch shrink-0">
        <div
          className={`w-3 h-3 rounded-full mt-1 ${
            isTampered ? "bg-red-500" : "bg-indigo-500"
          }`}
        />
      </div>

      {/* 버전 뱃지 */}
      <span className="text-xs font-mono font-bold text-bb-text2 shrink-0 w-8">
        v{record.version}
      </span>

      {/* 해시 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          {isTampered && (
            <AlertTriangle size={12} className="text-red-400 shrink-0" />
          )}
          <span className="font-mono text-xs text-bb-text2 truncate">
            {shortHash(record.fileHash)}
          </span>
        </div>
        <div className="flex items-center gap-3 mt-1 text-xs text-bb-text2">
          <span className="flex items-center gap-1">
            <Clock size={10} />
            {formatDate(record.uploadedAt)}
          </span>
          <span>{formatSize(record.fileSize)}</span>
          <span>{record.uploaderName}</span>
        </div>
      </div>

      {/* 다운로드 버튼 */}
      <button
        onClick={handleDownload}
        title="다운로드"
        className="p-2 text-bb-text2 hover:text-indigo-400 hover:bg-indigo-500/10 rounded-lg transition-all"
      >
        <Download size={14} />
      </button>
    </div>
  );
}

// ── 파일 그룹 (이름별 아코디언) ───────────────────────────────────────
function FileGroup({
  fileName,
  records,
  projectId,
}: {
  fileName: string;
  records: FileRecord[];
  projectId: string;
}) {
  // 기본으로 첫 번째 그룹만 펼치기
  const [open, setOpen] = useState(false);

  // 버전 1부터 N까지 정렬 (오름차순)
  const sorted = [...records].sort((a, b) => a.version - b.version);

  // 연속 버전 간 해시 변경 = 변조 의심
  const tamperSet = new Set<number>();
  for (let i = 1; i < sorted.length; i++) {
    if (sorted[i].fileHash !== sorted[i - 1].fileHash) {
      tamperSet.add(sorted[i].version);
    }
  }

  const latestVersion = sorted.at(-1)!;
  const hasTamper = tamperSet.size > 0;

  return (
    <div className="bg-bb-surface border border-bb-border rounded-xl overflow-hidden">
      {/* 파일 헤더 */}
      <button
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center gap-4 p-5 hover:bg-bb-surface2/50 transition-colors text-left"
      >
        {/* 파일 아이콘 */}
        <FileIcon size={20} className={fileColor(fileName)} />

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold text-bb-text truncate">{fileName}</span>
            {hasTamper && (
              <span className="shrink-0 flex items-center gap-1 text-[10px] font-medium
                               px-2 py-0.5 rounded-full bg-red-500/15 text-red-400 border border-red-500/30">
                <AlertTriangle size={9} />
                변조 감지
              </span>
            )}
          </div>
          <div className="flex items-center gap-3 mt-1 text-xs text-bb-text2">
            <span>버전 {records.length}개</span>
            <span className="flex items-center gap-1">
              <Hash size={9} />
              <span className="font-mono">{shortHash(latestVersion.fileHash)}</span>
            </span>
            <span>{formatSize(latestVersion.fileSize)}</span>
          </div>
        </div>

        {/* 최신 버전 상태 */}
        <div className="flex items-center gap-2 shrink-0">
          {!hasTamper && (
            <span className="flex items-center gap-1 text-xs text-green-400">
              <CheckCircle2 size={12} />
              무결성 확인
            </span>
          )}
          {open ? (
            <ChevronDown size={16} className="text-bb-text2" />
          ) : (
            <ChevronRight size={16} className="text-bb-text2" />
          )}
        </div>
      </button>

      {/* 버전 이력 타임라인 */}
      {open && (
        <div className="px-5 pb-5 space-y-2 border-t border-bb-border/60">
          <p className="text-xs text-bb-text2 pt-4 pb-1">버전 이력 (오래된 순)</p>
          {sorted.map((r) => (
            <HistoryItem
              key={r.id}
              record={r}
              projectId={projectId}
              isTampered={tamperSet.has(r.version)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ── 업로드 드롭존 ──────────────────────────────────────────────────────
interface UploadZoneProps {
  projectId: string;
  onUploaded: (result: FileUploadResult) => void;
}

function UploadZone({ projectId, onUploaded }: UploadZoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState("");

  const uploadFile = async (file: File) => {
    setUploading(true);
    setUploadError("");
    try {
      const formData = new FormData();
      formData.append("file", file);
      const { data } = await api.post<FileUploadResult>(
        `/projects/${projectId}/files`,
        formData,
        // Content-Type은 Axios가 boundary 포함해 자동 설정 — 수동 지정 시 boundary 누락으로 업로드 실패
      );
      onUploaded(data);
    } catch {
      setUploadError("업로드에 실패했습니다. 파일 크기나 권한을 확인해주세요.");
    } finally {
      setUploading(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) uploadFile(file);
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) uploadFile(file);
  };

  return (
    <div className="mb-8">
      {/* 드롭존 */}
      <div
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        onClick={() => !uploading && inputRef.current?.click()}
        className={`border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-all ${
          dragging
            ? "border-indigo-500 bg-indigo-500/10"
            : "border-bb-border hover:border-indigo-500/50 hover:bg-bb-surface2/50"
        }`}
      >
        {uploading ? (
          <div className="flex flex-col items-center gap-3">
            <Loader2 size={32} className="text-indigo-400 animate-spin" />
            <p className="text-sm text-bb-text2">업로드 중...</p>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-3">
            <Upload size={32} className={dragging ? "text-indigo-400" : "text-bb-text2"} />
            <div>
              <p className="text-sm font-medium text-bb-text">
                파일을 드래그하거나 클릭하여 업로드
              </p>
              <p className="text-xs text-bb-text2 mt-1">
                SHA-256 해시가 자동으로 생성되어 변조 여부를 추적합니다
              </p>
            </div>
          </div>
        )}
      </div>

      <input
        ref={inputRef}
        type="file"
        onChange={handleChange}
        className="hidden"
      />

      {/* 업로드 에러 */}
      {uploadError && (
        <div className="flex items-center gap-2 mt-3 p-3 bg-red-500/10 border border-red-500/20 rounded-lg">
          <AlertCircle size={14} className="text-red-400 shrink-0" />
          <p className="text-xs text-red-400">{uploadError}</p>
        </div>
      )}
    </div>
  );
}

// ── 메인 페이지 ────────────────────────────────────────────────────────
export default function VaultPage() {
  const params = useParams();
  const router = useRouter();
  const projectId = params?.projectId as string;

  const [files, setFiles] = useState<FileRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  // 업로드 직후 변조 감지 알림
  const [tamperAlert, setTamperAlert] = useState<string | null>(null);

  const fetchFiles = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const { data } = await api.get<FileRecord[]>(`/projects/${projectId}/files`);
      setFiles(data);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401) { router.replace("/login"); return; }
      setError("파일 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [projectId, router]);

  useEffect(() => {
    if (!localStorage.getItem("accessToken")) { router.replace("/login"); return; }
    fetchFiles();
  }, [fetchFiles]);

  // 업로드 완료 콜백
  const handleUploaded = (result: FileUploadResult) => {
    if (result.tamperDetected) {
      setTamperAlert(
        `⚠️ "${result.fileName}" 파일의 해시가 이전 버전과 다릅니다. 변조 의심 경보가 생성되었습니다.`,
      );
    }
    // 파일 목록 새로고침
    fetchFiles();
  };

  // 파일을 이름별로 그룹화
  const grouped = files.reduce<Record<string, FileRecord[]>>((acc, f) => {
    (acc[f.fileName] ??= []).push(f);
    return acc;
  }, {});

  // 통계
  const uniqueFiles = Object.keys(grouped).length;
  const totalVersions = files.length;
  const tamperFiles = Object.values(grouped).filter((recs) => {
    const sorted = [...recs].sort((a, b) => a.version - b.version);
    return sorted.some((r, i) => i > 0 && r.fileHash !== sorted[i - 1].fileHash);
  }).length;

  if (loading) {
    return (
      <div className="min-h-screen bg-bb-bg">
        <Sidebar />
        <main className="ml-64 min-h-screen p-8">
          <div className="animate-pulse space-y-3">
            <div className="h-6 bg-bb-surface rounded w-32 mb-6" />
            <div className="h-32 bg-bb-surface rounded-xl" />
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-20 bg-bb-surface rounded-xl" />
            ))}
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar />

      <main className="ml-64 min-h-screen p-8">
        {/* 헤더 */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-bb-text flex items-center gap-2">
              <Files size={22} className="text-indigo-400" />
              Hash Vault
            </h1>
            <p className="text-sm text-bb-text2 mt-1">
              파일 무결성을 SHA-256 해시로 추적합니다
            </p>
          </div>
        </div>

        {/* 변조 감지 알림 */}
        {tamperAlert && (
          <div className="flex items-start gap-3 p-4 bg-red-500/10 border border-red-500/30
                          rounded-xl mb-6 animate-pulse-once">
            <AlertTriangle size={18} className="text-red-400 shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="text-sm font-medium text-red-400">변조 감지 경보</p>
              <p className="text-xs text-red-400/80 mt-0.5">{tamperAlert}</p>
            </div>
            <button
              onClick={() => setTamperAlert(null)}
              className="text-red-400/60 hover:text-red-400 shrink-0"
            >
              ✕
            </button>
          </div>
        )}

        {/* 에러 */}
        {error && (
          <div className="flex items-center gap-3 p-4 bg-red-500/10 border border-red-500/20 rounded-xl mb-6">
            <AlertCircle size={16} className="text-red-400 shrink-0" />
            <p className="text-sm text-red-400">{error}</p>
            <button onClick={fetchFiles} className="ml-auto text-xs text-red-400 underline">
              다시 시도
            </button>
          </div>
        )}

        {/* 통계 카드 */}
        <div className="grid grid-cols-3 gap-4 mb-8">
          {[
            { label: "등록 파일", value: uniqueFiles, color: "text-indigo-400", icon: Files },
            { label: "전체 버전", value: totalVersions, color: "text-bb-text", icon: Hash },
            { label: "변조 감지", value: tamperFiles, color: tamperFiles > 0 ? "text-red-400" : "text-green-400", icon: AlertTriangle },
          ].map(({ label, value, color, icon: Icon }) => (
            <div key={label} className="bg-bb-surface border border-bb-border rounded-xl p-4">
              <div className="flex items-center gap-2 mb-2">
                <Icon size={14} className={color} />
                <span className="text-xs text-bb-text2">{label}</span>
              </div>
              <p className={`text-2xl font-bold ${color}`}>{value}</p>
            </div>
          ))}
        </div>

        {/* 업로드 영역 */}
        <UploadZone projectId={projectId} onUploaded={handleUploaded} />

        {/* 파일 목록 */}
        {uniqueFiles === 0 ? (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <div className="w-16 h-16 rounded-2xl bg-bb-surface border border-bb-border flex items-center justify-center mb-4">
              <Files size={28} className="text-slate-600" />
            </div>
            <p className="text-sm font-medium text-bb-text">아직 파일이 없습니다</p>
            <p className="text-xs text-bb-text2 mt-1">파일을 업로드하면 해시가 자동으로 기록됩니다</p>
          </div>
        ) : (
          <div className="space-y-3">
            {Object.entries(grouped)
              .sort(([, a], [, b]) => {
                // 최신 업로드 시각 기준 정렬
                const latestA = Math.max(...a.map((r) => new Date(r.uploadedAt).getTime()));
                const latestB = Math.max(...b.map((r) => new Date(r.uploadedAt).getTime()));
                return latestB - latestA;
              })
              .map(([fileName, records]) => (
                <FileGroup
                  key={fileName}
                  fileName={fileName}
                  records={records}
                  projectId={projectId}
                />
              ))}
          </div>
        )}
      </main>
    </div>
  );
}
