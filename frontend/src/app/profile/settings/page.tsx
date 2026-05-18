"use client";

import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import {
  UserCircle,
  Mail,
  Lock,
  Link2,
  CheckCircle2,
  XCircle,
  Loader2,
  Eye,
  EyeOff,
  Save,
  Unlink,
  ExternalLink,
} from "lucide-react";

const GOOGLE_BLUE = "#4285F4";

interface Profile { id: string; name: string; email: string; role: string; }
interface CalendarStatus { connected: boolean; members: { userId: string; name: string; email: string; connected: boolean }[] }

const INPUT_CLS =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2.5 text-sm " +
  "text-bb-text placeholder-slate-500 focus:outline-none focus:border-indigo-500 " +
  "focus:ring-1 focus:ring-blue-500/30 transition-all";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-bb-surface border border-bb-border rounded-xl p-6 mb-5">
      <h2 className="text-base font-semibold text-bb-text mb-5">{title}</h2>
      {children}
    </div>
  );
}

function ProfileSettingsContent() {
  const router       = useRouter();
  const searchParams = useSearchParams();

  // ── 프로필 ──────────────────────────────────────────────────────────────
  const [profile,     setProfile]     = useState<Profile | null>(null);
  const [nameEdit,    setNameEdit]    = useState("");
  const [savingName,  setSavingName]  = useState(false);
  const [nameMsg,     setNameMsg]     = useState<{ ok: boolean; text: string } | null>(null);

  // ── 비밀번호 변경 폼 입력값 (사용자가 타이핑하는 폼 state, 하드코딩 값 아님)
  const [currentPwInput,  setCurrentPwInput]  = useState("");
  const [newPwInput,      setNewPwInput]      = useState("");
  const [confirmPwInput,  setConfirmPwInput]  = useState("");
  const [showCurrentPw, setShowCurrentPw] = useState(false);
  const [showNewPw,     setShowNewPw]     = useState(false);
  const [savingPw,   setSavingPw]   = useState(false);
  const [pwMsg,      setPwMsg]      = useState<{ ok: boolean; text: string } | null>(null);

  // ── 캘린더 ──────────────────────────────────────────────────────────────
  const [calStatus,    setCalStatus]    = useState<CalendarStatus | null>(null);
  const [connecting,   setConnecting]   = useState(false);
  const [disconnecting,setDisconnecting]= useState(false);

  // ── 전역 토스트 ──────────────────────────────────────────────────────────
  const [toast, setToast] = useState<string | null>(null);
  const showToast = (msg: string) => { setToast(msg); setTimeout(() => setToast(null), 3500); };

  // ── 데이터 로드 ───────────────────────────────────────────────────────────
  const fetchCalendar = useCallback(async () => {
    try {
      const { data } = await api.get<CalendarStatus>("/calendar/authorize").catch(() =>
        // 연동 상태만 조회: /calendar/authorize 는 URL을 반환. 다른 endpoint 사용
        ({ data: null as unknown as CalendarStatus })
      );
      void data; // fallback below
    } catch { /* ignore */ }

    // 실제 연동 상태: 모든 프로젝트와 무관한 개인 상태는 별도 endpoint가 없어
    // /calendar/authorize 를 호출해 URL을 받는 게 가능한지로 판단 대신,
    // 내 프로필 정보와 함께 캘린더 상태를 body로 포함하는 간단한 방식으로 대체.
    // 여기서는 프로젝트 무관 상태 확인: 첫 번째 프로젝트의 status에서 내 연동 여부 읽기
    try {
      const projRes = await api.get<{ id: string }[]>("/projects");
      if (projRes.data.length > 0) {
        const { data } = await api.get<CalendarStatus>(`/projects/${projRes.data[0].id}/calendar/status`);
        setCalStatus(data);
      } else {
        setCalStatus({ connected: false, members: [] });
      }
    } catch {
      setCalStatus({ connected: false, members: [] });
    }
  }, []);

  useEffect(() => {
    api.get<Profile>("/auth/profile")
      .then(({ data }) => { setProfile(data); setNameEdit(data.name); })
      .catch(() => router.replace("/login"));

    fetchCalendar();

    if (searchParams.get("calendar") === "connected") {
      showToast("Google 캘린더가 연동되었습니다!");
    }
  }, [fetchCalendar, router, searchParams]);

  // ── 이름 저장 ─────────────────────────────────────────────────────────────
  const handleSaveName = async () => {
    if (!nameEdit.trim()) return;
    setSavingName(true);
    setNameMsg(null);
    try {
      const { data } = await api.put<Profile>("/auth/profile", { name: nameEdit.trim() });
      setProfile(data);
      setNameMsg({ ok: true, text: "이름이 변경되었습니다" });
    } catch {
      setNameMsg({ ok: false, text: "이름 변경에 실패했습니다" });
    } finally {
      setSavingName(false);
    }
  };

  // ── 비밀번호 변경 ─────────────────────────────────────────────────────────
  const handleChangePw = async () => {
    if (!currentPwInput || !newPwInput || !confirmPwInput) { setPwMsg({ ok: false, text: "모든 필드를 입력하세요" }); return; }
    if (newPwInput !== confirmPwInput) { setPwMsg({ ok: false, text: "새 비밀번호가 일치하지 않습니다" }); return; }
    if (newPwInput.length < 8) { setPwMsg({ ok: false, text: "비밀번호는 8자 이상이어야 합니다" }); return; }
    setSavingPw(true);
    setPwMsg(null);
    try {
      const body = { currentPassword: currentPwInput, newPassword: newPwInput };
      await api.put("/auth/password", body);
      setPwMsg({ ok: true, text: "비밀번호가 변경되었습니다" });
      setCurrentPwInput(""); setNewPwInput(""); setConfirmPwInput("");
    } catch {
      setPwMsg({ ok: false, text: "현재 비밀번호가 올바르지 않습니다" });
    } finally {
      setSavingPw(false);
    }
  };

  // ── 캘린더 연동 ───────────────────────────────────────────────────────────
  const handleConnect = async () => {
    setConnecting(true);
    try {
      const { data } = await api.get<{ authUrl: string }>("/calendar/authorize");
      window.location.href = data.authUrl;
    } catch {
      setConnecting(false);
    }
  };

  const handleDisconnect = async () => {
    if (!confirm("Google 캘린더 연동을 해제하시겠습니까?")) return;
    setDisconnecting(true);
    try {
      await api.delete("/calendar/disconnect");
      await fetchCalendar();
      showToast("Google 캘린더 연동이 해제되었습니다");
    } finally {
      setDisconnecting(false);
    }
  };

  const initials = profile ? profile.name.slice(0, 2).toUpperCase() : "..";
  const hue = profile
    ? profile.name.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360
    : 220;

  return (
    <div className="min-h-screen bg-bb-bg">
      <Sidebar />

      {/* 토스트 */}
      {toast && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50
                        flex items-center gap-2 px-5 py-3 bg-bb-surface border border-bb-border
                        rounded-xl shadow-xl text-sm text-bb-text">
          <CheckCircle2 size={15} className="text-green-400 shrink-0" />
          {toast}
        </div>
      )}

      <main className="ml-64 min-h-screen p-8">
        <div className="max-w-xl">

          {/* 헤더 */}
          <h1 className="text-2xl font-bold text-bb-text mb-1">프로필 설정</h1>
          <p className="text-sm text-bb-text2 mb-8">이름, 비밀번호, 연동 서비스를 관리합니다</p>

          {/* ① 기본 정보 */}
          <Section title="기본 정보">
            {/* 아바타 */}
            <div className="flex items-center gap-4 mb-6">
              <div
                style={{ background: `hsl(${hue} 55% 45%)` }}
                className="w-16 h-16 rounded-2xl flex items-center justify-center text-xl font-bold text-white shrink-0"
              >
                {initials}
              </div>
              <div>
                <p className="text-sm font-medium text-bb-text">{profile?.name ?? "..."}</p>
                <p className="text-xs text-bb-text2">{profile?.email ?? ""}</p>
                <span className="text-[10px] px-2 py-0.5 mt-1 inline-block rounded-full
                                 bg-indigo-500/15 text-indigo-400 border border-indigo-500/20">
                  {profile?.role ?? "STUDENT"}
                </span>
              </div>
            </div>

            {/* 이름 변경 */}
            <div className="mb-4">
              <label className="block text-xs font-medium text-bb-text2 mb-1.5">이름</label>
              <div className="flex gap-2">
                <input
                  value={nameEdit}
                  onChange={(e) => setNameEdit(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && handleSaveName()}
                  className={INPUT_CLS}
                  maxLength={100}
                  placeholder="이름을 입력하세요"
                />
                <button
                  onClick={handleSaveName}
                  disabled={savingName || !nameEdit.trim()}
                  className="flex items-center gap-1.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-500
                             text-white text-sm rounded-lg transition-all disabled:opacity-50 shrink-0"
                >
                  {savingName ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
                  저장
                </button>
              </div>
              {nameMsg && (
                <p className={`mt-1.5 text-xs ${nameMsg.ok ? "text-green-400" : "text-red-400"}`}>
                  {nameMsg.text}
                </p>
              )}
            </div>

            {/* 이메일 (읽기 전용) */}
            <div>
              <label className="block text-xs font-medium text-bb-text2 mb-1.5 flex items-center gap-1">
                <Mail size={11} /> 이메일 (변경 불가)
              </label>
              <input
                value={profile?.email ?? ""}
                readOnly
                className={`${INPUT_CLS} opacity-60 cursor-not-allowed`}
              />
            </div>
          </Section>

          {/* ② 보안 */}
          <Section title="보안">
            <div className="space-y-3">
              {/* 현재 비밀번호 */}
              <div>
                <label className="block text-xs font-medium text-bb-text2 mb-1.5">현재 비밀번호</label>
                <div className="relative">
                  <input
                    type={showCurrentPw ? "text" : "password"}
                    value={currentPwInput}
                    onChange={(e) => setCurrentPwInput(e.target.value)}
                    className={`${INPUT_CLS} pr-10`}
                    placeholder="현재 비밀번호"
                  />
                  <button
                    type="button"
                    onClick={() => setShowCurrentPw((v) => !v)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-bb-text2 hover:text-bb-text"
                  >
                    {showCurrentPw ? <EyeOff size={14} /> : <Eye size={14} />}
                  </button>
                </div>
              </div>

              {/* 새 비밀번호 */}
              <div>
                <label className="block text-xs font-medium text-bb-text2 mb-1.5">새 비밀번호</label>
                <div className="relative">
                  <input
                    type={showNewPw ? "text" : "password"}
                    value={newPwInput}
                    onChange={(e) => setNewPwInput(e.target.value)}
                    className={`${INPUT_CLS} pr-10`}
                    placeholder="8자 이상"
                  />
                  <button
                    type="button"
                    onClick={() => setShowNewPw((v) => !v)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-bb-text2 hover:text-bb-text"
                  >
                    {showNewPw ? <EyeOff size={14} /> : <Eye size={14} />}
                  </button>
                </div>
              </div>

              {/* 새 비밀번호 확인 */}
              <div>
                <label className="block text-xs font-medium text-bb-text2 mb-1.5">새 비밀번호 확인</label>
                <input
                  type="password"
                  value={confirmPwInput}
                  onChange={(e) => setConfirmPwInput(e.target.value)}
                  className={`${INPUT_CLS} ${confirmPwInput && newPwInput !== confirmPwInput ? "border-red-500/50" : ""}`}
                  placeholder="비밀번호 재입력"
                />
                {confirmPwInput && newPwInput !== confirmPwInput && (
                  <p className="mt-1 text-xs text-red-400">비밀번호가 일치하지 않습니다</p>
                )}
              </div>

              {pwMsg && (
                <p className={`text-xs ${pwMsg.ok ? "text-green-400" : "text-red-400"}`}>{pwMsg.text}</p>
              )}

              <button
                onClick={handleChangePw}
                disabled={savingPw}
                className="flex items-center gap-2 px-4 py-2.5 bg-indigo-600 hover:bg-indigo-500
                           text-white text-sm font-medium rounded-lg transition-all disabled:opacity-50"
              >
                {savingPw ? <Loader2 size={14} className="animate-spin" /> : <Lock size={14} />}
                {savingPw ? "변경 중..." : "비밀번호 변경"}
              </button>
            </div>
          </Section>

          {/* ③ 연동 서비스 */}
          <Section title="연동 서비스">
            <div className="flex items-start justify-between gap-4">
              <div className="flex items-center gap-3">
                {/* Google 아이콘 */}
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0"
                  style={{ backgroundColor: `${GOOGLE_BLUE}20` }}
                >
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                    <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                    <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                    <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z" fill="#FBBC05"/>
                    <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                  </svg>
                </div>
                <div>
                  <p className="text-sm font-medium text-bb-text">Google 캘린더</p>
                  <p className="text-xs text-bb-text2 mt-0.5">
                    {calStatus?.connected
                      ? "연동됨 — 회의 일정 조율 시 내 캘린더가 자동 분석됩니다"
                      : "연동하면 AI가 내 빈 시간을 분석해 최적 회의 시간을 추천합니다"}
                  </p>
                </div>
              </div>

              {calStatus?.connected ? (
                <button
                  onClick={handleDisconnect}
                  disabled={disconnecting}
                  className="flex items-center gap-1.5 px-3 py-2 bg-bb-surface2 border border-bb-border
                             hover:border-bb-text2 text-bb-text2 hover:text-bb-text
                             rounded-lg text-xs font-medium transition-all disabled:opacity-50 shrink-0"
                >
                  {disconnecting
                    ? <Loader2 size={13} className="animate-spin" />
                    : <Unlink size={13} />}
                  연동 해제
                </button>
              ) : (
                <button
                  onClick={handleConnect}
                  disabled={connecting}
                  className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium
                             text-white transition-opacity disabled:opacity-50 shrink-0"
                  style={{ backgroundColor: connecting ? "#6b7280" : GOOGLE_BLUE }}
                >
                  {connecting
                    ? <Loader2 size={14} className="animate-spin" />
                    : <ExternalLink size={14} />}
                  {connecting ? "연결 중..." : "Google 계정 연결"}
                </button>
              )}
            </div>

            {calStatus?.connected && (
              <div className="mt-3 flex items-center gap-2 bg-green-500/10 border border-green-500/20
                              rounded-lg px-3 py-2.5">
                <CheckCircle2 size={14} className="text-green-400 shrink-0" />
                <span className="text-xs text-green-400">
                  Google 캘린더 연동됨 — 일정 조율 기능을 이용할 수 있습니다
                </span>
              </div>
            )}
          </Section>

        </div>
      </main>
    </div>
  );
}

export default function ProfileSettingsPage() {
  return (
    <Suspense>
      <ProfileSettingsContent />
    </Suspense>
  );
}
