"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import BeginnerGuide from "@/components/BeginnerGuide";
import api from "@/lib/api";
import {
  CheckCircle2, AlertCircle, Loader2,
  X, Unlink,
} from "lucide-react";

const INPUT_CLS =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2.5 text-sm " +
  "text-bb-text placeholder-slate-500 focus:outline-none focus:border-indigo-500 " +
  "focus:ring-1 focus:ring-blue-500/30 transition-all font-mono";

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
              {/* 초보자 가이드 */}
              <BeginnerGuide
                toggleLabel="Notion 연동이 처음이세요?"
                defaultOpen
                steps={[
                  {
                    emoji: "🔑",
                    title: "STEP 1 — Notion API 키 만들기",
                    lines: [
                      "1. notion.so/my-integrations 에 접속하세요",
                      "2. \"+ 새 API 통합\" 버튼을 클릭하세요",
                      "3. 이름을 입력하고 (예: 블랙박스) 저장을 누르세요",
                      "4. \"내부 통합 시크릿\" 옆 복사 버튼을 클릭하세요",
                      "5. 복사한 값을 아래 API 키 입력창에 붙여넣으세요",
                    ],
                    link: { href: "https://www.notion.so/my-integrations", label: "notion.so/my-integrations 바로가기" },
                  },
                  {
                    emoji: "📄",
                    title: "STEP 2 — 페이지 ID 찾기",
                    lines: [
                      "1. Notion에서 회의록을 저장할 페이지를 여세요 (없으면 새로 만드세요)",
                      "2. 주소창 URL의 맨 마지막 32자리를 복사하세요",
                    ],
                    example: {
                      before: "notion.so/내-페이지-",
                      highlightPart: "abc123def456abc123def456abc123de",
                      caption: "이 부분 32자리(영어+숫자)가 페이지 ID예요",
                    },
                  },
                ]}
                warning={{
                  title: "⚠️ STEP 3 — 반드시 해야 하는 단계: Notion 페이지에 통합 연결하기",
                  lines: [
                    "1. Notion에서 저장할 페이지를 여세요",
                    "2. 우측 상단 \"···\" 버튼을 클릭하세요",
                    "3. \"연결\" → 방금 만든 통합 이름을 선택하세요",
                    "4. 연결되었는지 확인하세요",
                    "이 단계를 빠뜨리면 오류가 납니다!",
                  ],
                  tone: "red",
                }}
                faq={[
                  { q: "403 오류가 나요", a: "STEP 3(통합 연결)을 했는지 다시 확인해주세요. 가장 흔한 원인이에요." },
                  { q: "페이지 ID가 뭔지 모르겠어요", a: "Notion 페이지 주소창 URL에서 맨 마지막에 있는 영어+숫자 32자리예요." },
                  { q: "API 키는 어디서 만들어요", a: "notion.so/my-integrations 에서 \"+ 새 API 통합\"으로 만들 수 있어요." },
                ]}
              />

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
