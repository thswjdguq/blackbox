"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import {
  ExternalLink, CheckCircle2, AlertCircle, Loader2,
  ChevronDown, ChevronUp, X, Unlink,
} from "lucide-react";

const INPUT_CLS =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2.5 text-sm " +
  "text-bb-text placeholder-slate-500 focus:outline-none focus:border-indigo-500 " +
  "focus:ring-1 focus:ring-blue-500/30 transition-all font-mono";

interface Step {
  num: number;
  title: string;
  content: React.ReactNode;
}

export default function NotionConnectPage() {
  const router = useRouter();

  const [connected,      setConnected]      = useState(false);
  const [workspaceName,  setWorkspaceName]  = useState<string | null>(null);
  const [loading,        setLoading]        = useState(true);
  const [saving,         setSaving]         = useState(false);
  const [disconnecting,  setDisconnecting]  = useState(false);
  const [error,          setError]          = useState("");
  const [apiKey,         setApiKey]         = useState("");
  const [pageId,         setPageId]         = useState("");
  const [openStep,       setOpenStep]       = useState<number | null>(1);

  useEffect(() => {
    api.get<{ connected: boolean; workspaceName: string | null }>("/notion/status")
      .then(res => {
        setConnected(res.data.connected);
        setWorkspaceName(res.data.workspaceName);
      })
      .catch(err => {
        if (err?.response?.status === 401) router.replace("/login");
      })
      .finally(() => setLoading(false));
  }, [router]);

  const handleConnect = async () => {
    setError("");
    if (!apiKey.trim() || !pageId.trim()) {
      setError("API Key와 Page ID를 모두 입력해주세요.");
      return;
    }
    setSaving(true);
    try {
      const res = await api.post<{ connected: boolean; workspaceName: string | null }>(
        "/notion/settings",
        { notionApiKey: apiKey.trim(), notionPageId: pageId.trim() },
      );
      if (res.data.connected) {
        setConnected(true);
        setWorkspaceName(res.data.workspaceName);
        setApiKey("");
        setPageId("");
      } else {
        setError("API Key가 유효하지 않습니다. 키를 다시 확인해주세요.");
      }
    } catch {
      setError("연결에 실패했습니다. API Key와 Page ID를 확인해주세요.");
    } finally {
      setSaving(false);
    }
  };

  const handleDisconnect = async () => {
    setDisconnecting(true);
    try {
      await api.delete("/notion/disconnect");
      setConnected(false);
      setWorkspaceName(null);
    } catch {
      setError("연결 해제에 실패했습니다.");
    } finally {
      setDisconnecting(false);
    }
  };

  const steps: Step[] = [
    {
      num: 1,
      title: "Notion 통합 페이지 접속",
      content: (
        <a
          href="https://www.notion.so/my-integrations"
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 px-4 py-2 bg-[#191919] hover:bg-[#2a2a2a]
                     text-white text-sm rounded-lg border border-[#333] transition-colors"
        >
          <span className="font-bold text-[11px] bg-white text-[#191919] px-1 rounded">N</span>
          notion.so/my-integrations 열기
          <ExternalLink size={12} />
        </a>
      ),
    },
    {
      num: 2,
      title: "새 통합(Integration) 생성",
      content: (
        <ol className="text-sm text-bb-text2 space-y-1.5 list-decimal list-inside">
          <li><strong className="text-bb-text">New integration</strong> 버튼 클릭</li>
          <li>Name 입력 (예: <code className="bg-bb-bg px-1.5 py-0.5 rounded text-xs">Team Blackbox</code>)</li>
          <li>Type은 <strong className="text-bb-text">Internal</strong> 선택</li>
          <li><strong className="text-bb-text">Save</strong> 클릭</li>
        </ol>
      ),
    },
    {
      num: 3,
      title: "Internal Integration Secret 복사",
      content: (
        <p className="text-sm text-bb-text2">
          생성 후 나타나는 <strong className="text-bb-text">Internal Integration Secret</strong> 옆
          복사 버튼을 클릭하세요.{" "}
          <code className="bg-bb-bg px-1.5 py-0.5 rounded text-xs">secret_...</code>으로 시작하는 값입니다.
          아래 API Key 필드에 붙여넣기 해주세요.
        </p>
      ),
    },
    {
      num: 4,
      title: "Notion 페이지에 Integration 연결",
      content: (
        <ol className="text-sm text-bb-text2 space-y-1.5 list-decimal list-inside">
          <li>회의록을 저장할 Notion 페이지로 이동</li>
          <li>오른쪽 상단 <strong className="text-bb-text">···</strong> 메뉴 클릭</li>
          <li><strong className="text-bb-text">Connect to</strong> 선택</li>
          <li>방금 만든 Integration 선택</li>
        </ol>
      ),
    },
    {
      num: 5,
      title: "Page ID 확인",
      content: (
        <div className="space-y-2 text-sm text-bb-text2">
          <p>해당 Notion 페이지 URL에서 마지막 <strong className="text-bb-text">32자리</strong>가 Page ID입니다.</p>
          <div className="bg-bb-bg rounded-lg p-3 font-mono text-xs break-all">
            notion.so/내-페이지-이름-
            <span className="text-indigo-400 font-bold">xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx</span>
          </div>
          <p>하이픈(-) 없이 32자리 영숫자만 아래 Page ID 필드에 입력하세요.</p>
        </div>
      ),
    },
  ];

  if (loading) {
    return (
      <div className="min-h-screen bg-bb-bg">
        <Sidebar />
        <main className="ml-64 min-h-screen p-8 flex items-center justify-center">
          <Loader2 size={24} className="animate-spin text-bb-text2" />
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar />
      <main className="ml-64 min-h-screen p-8">
        <div className="max-w-xl">
          <h1 className="text-2xl font-bold text-bb-text mb-1">Notion 연동 설정</h1>
          <p className="text-sm text-bb-text2 mb-8">
            개인 Notion 워크스페이스에 회의록을 자동으로 내보냅니다.
          </p>

          {/* 연결된 상태 */}
          {connected ? (
            <div className="bg-bb-surface border border-bb-border rounded-xl p-6">
              <div className="flex items-center gap-3 mb-4">
                <CheckCircle2 size={22} className="text-green-400 shrink-0" />
                <div>
                  <p className="text-sm font-semibold text-bb-text">Notion 연결됨</p>
                  {workspaceName && (
                    <p className="text-xs text-bb-text2 mt-0.5">워크스페이스: {workspaceName}</p>
                  )}
                </div>
              </div>
              <button
                onClick={handleDisconnect}
                disabled={disconnecting}
                className="flex items-center gap-2 px-4 py-2 text-sm text-red-400
                           border border-red-500/30 hover:bg-red-500/10 rounded-lg
                           transition-all disabled:opacity-50"
              >
                {disconnecting
                  ? <Loader2 size={14} className="animate-spin" />
                  : <Unlink size={14} />}
                연결 해제
              </button>
            </div>
          ) : (
            /* 연결 안 된 상태 */
            <div className="space-y-4">
              {/* 단계별 가이드 */}
              <div className="bg-bb-surface border border-bb-border rounded-xl overflow-hidden">
                <div className="px-5 py-4 border-b border-bb-border">
                  <p className="text-sm font-semibold text-bb-text">연동 방법</p>
                </div>
                {steps.map((step) => (
                  <div key={step.num} className="border-b border-bb-border/60 last:border-0">
                    <button
                      onClick={() => setOpenStep(openStep === step.num ? null : step.num)}
                      className="w-full flex items-center justify-between px-5 py-3.5
                                 hover:bg-bb-surface2/50 transition-colors text-left"
                    >
                      <div className="flex items-center gap-3">
                        <span className="w-6 h-6 rounded-full bg-bb-primary/20 text-bb-primary
                                          text-xs font-bold flex items-center justify-center shrink-0">
                          {step.num}
                        </span>
                        <span className="text-sm font-medium text-bb-text">{step.title}</span>
                      </div>
                      {openStep === step.num
                        ? <ChevronUp size={14} className="text-bb-text2 shrink-0" />
                        : <ChevronDown size={14} className="text-bb-text2 shrink-0" />}
                    </button>
                    {openStep === step.num && (
                      <div className="px-5 pb-4 pt-1">{step.content}</div>
                    )}
                  </div>
                ))}
              </div>

              {/* 입력 폼 */}
              <div className="bg-bb-surface border border-bb-border rounded-xl p-5 space-y-4">
                {error && (
                  <div className="flex items-center gap-2 p-3 bg-red-500/10 border border-red-500/20 rounded-lg">
                    <AlertCircle size={14} className="text-red-400 shrink-0" />
                    <p className="text-xs text-red-400 flex-1">{error}</p>
                    <button onClick={() => setError("")} className="text-red-400 hover:text-red-300 shrink-0">
                      <X size={12} />
                    </button>
                  </div>
                )}
                <div>
                  <label className="block text-xs font-medium text-bb-text2 mb-1.5">
                    Internal Integration Secret
                  </label>
                  <input
                    type="password"
                    value={apiKey}
                    onChange={(e) => setApiKey(e.target.value)}
                    placeholder="secret_..."
                    className={INPUT_CLS}
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-bb-text2 mb-1.5">
                    Page ID <span className="text-bb-text2 font-normal">(32자리 영숫자)</span>
                  </label>
                  <input
                    type="text"
                    value={pageId}
                    onChange={(e) => setPageId(e.target.value)}
                    placeholder="xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                    className={INPUT_CLS}
                  />
                </div>
                <button
                  onClick={handleConnect}
                  disabled={saving || !apiKey.trim() || !pageId.trim()}
                  className="w-full flex items-center justify-center gap-2 py-2.5
                             bg-bb-primary hover:bg-bb-primary-h disabled:opacity-50
                             text-white text-sm font-medium rounded-lg transition-all"
                >
                  {saving ? <Loader2 size={15} className="animate-spin" /> : null}
                  {saving ? "연결 확인 중..." : "연결하기"}
                </button>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
