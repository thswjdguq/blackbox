"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { CheckCircle2, Mail } from "lucide-react";
import { apiError } from "@/lib/apiError";
import { getEmailSettings, putEmailSettings, requestEmailVerification, type EmailSettings } from "@/lib/api/email";
import { sessionGeneration } from "@/lib/sessionCache";

/** 429의 Retry-After(초). 없거나 이상하면 60초(K-41 9장 재전송 최소 간격) */
function retryAfterSeconds(error: unknown): number {
  const raw = (error as { response?: { headers?: Record<string, string> } })?.response?.headers?.["retry-after"];
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? Math.ceil(n) : 60;
}

/**
 * 프로필 설정의 업무 이메일 알림(K-41 0.5 7~9장).
 * 켜면 PUT 응답의 상태를 바로 보이고, 주소 인증 전이면 그 자리에서 확인 메일 보내기를 보인다.
 * 서버가 보낸 문장(사용할 수 없는 이유, 오류 detail)은 그대로 보이고 화면이 따로 만들지 않는다.
 */
export default function EmailNotificationSettings({ email }: { email: string }) {
  const [settings, setSettings] = useState<EmailSettings | null>(null);
  const [loadError, setLoadError] = useState("");
  const [saving, setSaving] = useState(false);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const [waitUntil, setWaitUntil] = useState(0);
  const [now, setNow] = useState(() => Date.now());

  // 설정을 바꾸는 응답(GET·PUT)은 가장 최근에 보낸 요청의 것만 반영한다.
  // 늦게 끝난 옛 GET(false)이 방금 저장한 PUT(true)을 덮지 않게 순번·세션 세대·마운트를 함께 본다
  const seq = useRef(0);
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  const begin = () => ({ ticket: ++seq.current, gen: sessionGeneration() });
  const current = (t: { ticket: number; gen: number }) => alive.current && t.ticket === seq.current && t.gen === sessionGeneration();

  const load = useCallback(async () => {
    const t = { ticket: ++seq.current, gen: sessionGeneration() };
    const fresh = () => alive.current && t.ticket === seq.current && t.gen === sessionGeneration();
    try {
      const { data } = await getEmailSettings();
      if (fresh()) { setSettings(data); setLoadError(""); }
    } catch (e) {
      if (fresh()) setLoadError(apiError(e, "이메일 알림 설정을 불러오지 못했습니다."));
    }
  }, []);

  // 처음 한 번: 응답이 온 뒤에만 상태를 바꾼다(다시 시도·인증 뒤 새로고침은 load)
  useEffect(() => {
    const ticket = ++seq.current, gen = sessionGeneration();
    const fresh = () => alive.current && ticket === seq.current && gen === sessionGeneration();
    getEmailSettings()
      .then(({ data }) => { if (fresh()) { setSettings(data); setLoadError(""); } })
      .catch((e) => { if (fresh()) setLoadError(apiError(e, "이메일 알림 설정을 불러오지 못했습니다.")); });
  }, []);

  // 재요청 제한 남은 시간 표시
  useEffect(() => {
    if (waitUntil <= Date.now()) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [waitUntil]);
  const waitLeft = Math.max(0, Math.ceil((waitUntil - now) / 1000));

  const toggle = async (enabled: boolean) => {
    if (saving) return;
    setSaving(true); setMessage(null);
    const t = begin();
    try {
      const { data } = await putEmailSettings(enabled);
      if (current(t)) setSettings(data);
    } catch (e) {
      if (current(t)) setMessage({ ok: false, text: apiError(e, "이메일 알림 설정을 저장하지 못했습니다.") });
    } finally { if (alive.current) setSaving(false); }
  };

  const sendVerification = async () => {
    if (sending || waitLeft > 0) return;
    setSending(true); setMessage(null);
    const gen = sessionGeneration();
    const live = () => alive.current && gen === sessionGeneration();
    try {
      const res = await requestEmailVerification();
      if (!live()) return;
      if (res.status === 204) {
        // 이미 인증된 주소: 상태를 다시 읽는다
        await load();
        setMessage({ ok: true, text: "이미 확인된 주소입니다." });
      } else {
        // 202는 발송 요청 접수이지 받은편지함 도착이 아니다(K-41 9장). 문장은 서버의 message 그대로(A-72),
        // 화면은 받는 주소만 덧붙인다
        const serverMessage = (res.data as { message?: string } | undefined)?.message;
        setMessage({ ok: true, text: `${serverMessage ?? "확인 메일 발송을 요청했습니다."} 받는 주소: ${email}` });
        setWaitUntil(Date.now() + 60_000); setNow(Date.now());
      }
    } catch (e) {
      if (!live()) return;
      const status = (e as { response?: { status?: number } })?.response?.status;
      if (status === 429) { setWaitUntil(Date.now() + retryAfterSeconds(e) * 1000); setNow(Date.now()); }
      if (status === 503) await load();
      setMessage({ ok: false, text: apiError(e, "확인 메일을 요청하지 못했습니다.") });
    } finally { if (alive.current) setSending(false); }
  };

  if (loadError) {
    return (
      <div role="alert" className="text-sm text-red-500">
        {loadError} <button type="button" onClick={() => void load()} className="underline">다시 시도</button>
      </div>
    );
  }
  if (!settings) return <p className="text-sm text-bb-text2">이메일 알림 설정을 불러오는 중…</p>;

  return (
    <div className="space-y-4">
      <label className="flex items-start gap-3">
        <input type="checkbox" checked={settings.enabled} disabled={saving} onChange={(e) => void toggle(e.target.checked)}
          className="mt-0.5 h-4 w-4" aria-describedby="email-settings-help" />
        <span>
          <span className="block text-sm font-medium text-bb-text">[선택] 팀프로젝트 이메일 알림 받기</span>
          <span id="email-settings-help" className="block text-xs text-bb-text2">
            업무 결과 확인·최종 제출물 검토 요청을 계정 이메일({email})로 받습니다. 받으려면 이메일 주소 확인이 필요합니다.
          </span>
        </span>
      </label>

      {settings.emailVerified ? (
        <p className="flex items-center gap-1.5 text-sm text-teal-700 dark:text-teal-300">
          <CheckCircle2 size={15} aria-hidden="true" /> 확인된 주소입니다{settings.enabled ? ". 새 업무 알림부터 메일로 받습니다." : "."}
        </p>
      ) : settings.verificationRequired && (
        <div data-testid="email-verification" className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3">
          <p className="text-sm text-bb-text">메일을 받으려면 주소 인증이 필요합니다.</p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <button type="button" onClick={() => void sendVerification()}
              disabled={sending || waitLeft > 0 || !settings.verificationAvailable}
              className="inline-flex items-center gap-1.5 rounded-lg border border-bb-border bg-bb-surface px-3 py-1.5 text-sm text-bb-text hover:border-bb-primary/40 disabled:opacity-50">
              <Mail size={14} aria-hidden="true" />
              {sending ? "요청 중…" : waitLeft > 0 ? `확인 메일 보내기 (${waitLeft}초 뒤)` : "확인 메일 보내기"}
            </button>
            {!settings.verificationAvailable && settings.verificationUnavailableReason && (
              <span className="text-xs text-bb-text2">{settings.verificationUnavailableReason}</span>
            )}
          </div>
        </div>
      )}

      {message && <p role={message.ok ? "status" : "alert"} className={`text-sm ${message.ok ? "text-bb-text2" : "text-red-500"}`}>{message.text}</p>}
    </div>
  );
}
