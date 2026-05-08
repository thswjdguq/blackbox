"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import Sidebar from "@/components/Sidebar";
import api from "@/lib/api";
import {
  UserCircle,
  ArrowRight,
  MessageSquare,
  CheckCircle2,
  XCircle,
  Loader2,
  Send,
  Save,
} from "lucide-react";

const INPUT_CLS =
  "w-full bg-bb-bg border border-bb-border rounded-lg px-3 py-2.5 text-sm " +
  "text-bb-text placeholder-slate-500 focus:outline-none focus:border-indigo-500 " +
  "focus:ring-1 focus:ring-blue-500/30 transition-all";

interface DiscordSettings {
  webhookUrl:             string | null;
  notifyTaskAssign:       boolean;
  notifyTaskDone:         boolean;
  notifyMeetingCreate:    boolean;
  notifyMeetingReminder:  boolean;
  notifyAlert:            boolean;
}

const TOGGLES: { key: keyof Omit<DiscordSettings, "webhookUrl">; label: string; desc: string }[] = [
  { key: "notifyTaskAssign",       label: "태스크 담당자 배정",   desc: "담당자가 배정되면 알림" },
  { key: "notifyTaskDone",         label: "태스크 완료",          desc: "태스크가 완료되면 알림" },
  { key: "notifyMeetingCreate",    label: "회의 생성",            desc: "새 회의가 생성되면 알림" },
  { key: "notifyMeetingReminder",  label: "회의 10분 전",         desc: "회의 시작 10분 전 자동 알림" },
  { key: "notifyAlert",            label: "경보 발생",            desc: "FREE_RIDE / DROPOUT / OVERLOAD 경보 시 알림" },
];

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-bb-surface border border-bb-border rounded-xl p-6 mb-5">
      <h2 className="text-base font-semibold text-bb-text mb-5">{title}</h2>
      {children}
    </div>
  );
}

function Toggle({
  checked,
  onChange,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-5 w-9 shrink-0 rounded-full border-2 border-transparent
                  transition-colors focus:outline-none
                  ${checked ? "bg-indigo-600" : "bg-bb-surface2"}`}
    >
      <span
        className={`pointer-events-none inline-block h-4 w-4 rounded-full bg-white shadow
                    transform transition-transform
                    ${checked ? "translate-x-4" : "translate-x-0"}`}
      />
    </button>
  );
}

export default function ProjectSettingsPage() {
  const { projectId } = useParams<{ projectId: string }>();

  const [settings,  setSettings]  = useState<DiscordSettings | null>(null);
  const [webhookInput, setWebhookInput] = useState("");
  const [saving,    setSaving]    = useState(false);
  const [testing,   setTesting]   = useState(false);
  const [saveMsg,   setSaveMsg]   = useState<{ ok: boolean; text: string } | null>(null);
  const [testMsg,   setTestMsg]   = useState<{ ok: boolean; text: string } | null>(null);

  const fetch = useCallback(async () => {
    try {
      const { data } = await api.get<DiscordSettings>(`/projects/${projectId}/discord`);
      setSettings(data);
      setWebhookInput(data.webhookUrl ?? "");
    } catch { /* 미설정 시 기본값 */ }
  }, [projectId]);

  useEffect(() => { fetch(); }, [fetch]);

  const handleSave = async () => {
    if (!settings) return;
    setSaving(true);
    setSaveMsg(null);
    try {
      const { data } = await api.put<DiscordSettings>(`/projects/${projectId}/discord`, {
        ...settings,
        webhookUrl: webhookInput.trim() || null,
      });
      setSettings(data);
      setSaveMsg({ ok: true, text: "Discord 설정이 저장되었습니다" });
    } catch {
      setSaveMsg({ ok: false, text: "저장에 실패했습니다" });
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async () => {
    const url = webhookInput.trim();
    if (!url) { setTestMsg({ ok: false, text: "Webhook URL을 먼저 입력하세요" }); return; }
    setTesting(true);
    setTestMsg(null);
    try {
      const { data } = await api.post<{ success: boolean; message: string }>(
        `/projects/${projectId}/discord/test`,
        { webhookUrl: url },
      );
      setTestMsg({ ok: data.success, text: data.message });
    } catch {
      setTestMsg({ ok: false, text: "테스트 전송에 실패했습니다" });
    } finally {
      setTesting(false);
    }
  };

  const toggle = (key: keyof Omit<DiscordSettings, "webhookUrl">) => {
    if (!settings) return;
    setSettings({ ...settings, [key]: !settings[key] });
  };

  return (
    <div className="flex min-h-screen bg-bb-bg">
      <Sidebar />
      <main className="ml-64 flex-1 p-8">
        <div className="max-w-2xl">

          <h1 className="text-2xl font-bold text-bb-text mb-1">프로젝트 설정</h1>
          <p className="text-sm text-bb-text2 mb-8">프로젝트 관련 설정을 관리합니다</p>

          {/* Google 캘린더 안내 */}
          <Section title="Google 캘린더 연동">
            <div className="flex items-start gap-4">
              <div className="w-10 h-10 rounded-xl bg-indigo-500/10 flex items-center justify-center shrink-0">
                <UserCircle size={20} className="text-indigo-400" />
              </div>
              <div className="flex-1">
                <p className="text-xs text-bb-text2 leading-relaxed mb-4">
                  Google 캘린더 연동은 개인 프로필 설정에서 할 수 있습니다.
                  연동하면 AI 일정 조율 기능에서 내 캘린더가 자동 분석됩니다.
                </p>
                <Link
                  href="/profile/settings"
                  className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500
                             text-white text-sm font-medium rounded-lg transition-all"
                >
                  프로필 설정에서 연동하기
                  <ArrowRight size={14} />
                </Link>
              </div>
            </div>
          </Section>

          {/* Discord 알림 */}
          <Section title="Discord 알림">
            <div className="flex items-center gap-3 mb-5">
              <div className="w-10 h-10 rounded-xl bg-[#5865F2]/15 flex items-center justify-center shrink-0">
                <MessageSquare size={18} className="text-[#5865F2]" />
              </div>
              <div>
                <p className="text-sm font-medium text-bb-text">Discord Webhook 연동</p>
                <p className="text-xs text-bb-text2">팀 Discord 채널에 실시간 알림을 보냅니다</p>
              </div>
            </div>

            {/* Webhook URL */}
            <div className="mb-4">
              <label className="block text-xs font-medium text-bb-text2 mb-1.5">
                Webhook URL
              </label>
              <div className="flex gap-2">
                <input
                  value={webhookInput}
                  onChange={(e) => setWebhookInput(e.target.value)}
                  placeholder="https://discord.com/api/webhooks/..."
                  className={INPUT_CLS}
                />
                <button
                  onClick={handleTest}
                  disabled={testing}
                  className="flex items-center gap-1.5 px-3 py-2 text-sm border border-bb-border
                             text-bb-text2 hover:text-bb-text hover:border-bb-text2
                             rounded-lg transition-all disabled:opacity-50 shrink-0"
                >
                  {testing ? <Loader2 size={13} className="animate-spin" /> : <Send size={13} />}
                  테스트
                </button>
              </div>
              {testMsg && (
                <p className={`mt-1.5 flex items-center gap-1 text-xs
                               ${testMsg.ok ? "text-green-400" : "text-red-400"}`}>
                  {testMsg.ok
                    ? <CheckCircle2 size={12} />
                    : <XCircle size={12} />}
                  {testMsg.text}
                </p>
              )}
              <p className="mt-1 text-[11px] text-bb-text2">
                Discord 채널 설정 → 연동 → 웹후크 → 웹후크 URL 복사
              </p>
            </div>

            {/* 알림 토글 목록 */}
            <div className="space-y-3 mb-5">
              {TOGGLES.map(({ key, label, desc }) => (
                <div key={key} className="flex items-center justify-between py-2.5 border-b border-bb-border/50 last:border-0">
                  <div>
                    <p className="text-sm text-bb-text">{label}</p>
                    <p className="text-xs text-bb-text2">{desc}</p>
                  </div>
                  <Toggle
                    checked={settings?.[key] ?? true}
                    onChange={() => toggle(key)}
                  />
                </div>
              ))}
            </div>

            {saveMsg && (
              <p className={`mb-3 flex items-center gap-1 text-xs
                             ${saveMsg.ok ? "text-green-400" : "text-red-400"}`}>
                {saveMsg.ok ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                {saveMsg.text}
              </p>
            )}

            <button
              onClick={handleSave}
              disabled={saving}
              className="flex items-center gap-2 px-4 py-2.5 bg-indigo-600 hover:bg-indigo-500
                         text-white text-sm font-medium rounded-lg transition-all disabled:opacity-50"
            >
              {saving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
              {saving ? "저장 중..." : "저장"}
            </button>
          </Section>

        </div>
      </main>
    </div>
  );
}
