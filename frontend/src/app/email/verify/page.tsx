"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { CheckCircle2, AlertCircle, Loader2 } from "lucide-react";
import { apiError } from "@/lib/apiError";
import { confirmEmailVerification } from "@/lib/api/email";
import { clearToken, stashToken, tabStorage } from "@/lib/emailVerifyToken";

const NO_TOKEN = Symbol("no-token");

type State = { kind: "checking" } | { kind: "done" } | { kind: "failed"; text: string } | { kind: "noToken" };

/**
 * 메일의 확인 링크 `/email/verify#token=…`(K-41 0.5 9장).
 * 1) 열리자마자 토큰을 이 탭의 sessionStorage로 옮기고 주소에서 지운다(로그인 이동·로그에 토큰이 실리지 않게)
 * 2) 로그인이 안 돼 있으면 AuthProvider가 /login?next=/email/verify로 보내고, 로그인 뒤 여기로 돌아온다
 * 3) 로그인된 채로 확인을 한 번만 보내고, 결과가 정해지면(204·400·410·409) 임시 토큰을 지운다
 * 링크를 여는 것만으로 상태를 바꾸지 않고, 확인은 로그인한 사용자의 POST로만 한다.
 */
export default function EmailVerifyPage() {
  const [state, setState] = useState<State>({ kind: "checking" });
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    // 저장소 접근 자체가 막힌 브라우저도 있다(tabStorage가 null). 그때는 이번 화면에서만 토큰을 쓰고,
    // 로그인으로 보내지면 토큰을 이어갈 수 없으므로 돌아온 화면은 "링크를 다시 열라"는 안내(noToken)가 된다
    const storage = tabStorage();
    const token = stashToken(window.location, storage, (url) => window.history.replaceState(null, "", url));
    (token ? confirmEmailVerification(token) : Promise.reject(NO_TOKEN))
      .then(() => { clearToken(storage); setState({ kind: "done" }); })
      .catch((e) => {
        if (e === NO_TOKEN) { setState({ kind: "noToken" }); return; }
        const status = (e as { response?: { status?: number } })?.response?.status;
        // 401은 로그인으로 보내진다. 토큰은 남겨 두어 로그인 뒤 다시 확인한다
        if (status === 401) return;
        if (status === 400 || status === 409 || status === 410) clearToken(storage);
        setState({ kind: "failed", text: apiError(e, "이메일 주소를 확인하지 못했습니다. 잠시 뒤 다시 시도해 주세요.") });
      });
  }, []);

  return (
    <main className="min-h-screen bg-bb-bg flex items-center justify-center p-4">
      <section aria-labelledby="verify-title" className="w-full max-w-md rounded-2xl border border-bb-border bg-bb-surface p-6 text-center">
        <h1 id="verify-title" className="text-lg font-semibold text-bb-text">이메일 주소 확인</h1>
        {state.kind === "checking" && (
          <p role="status" className="mt-4 flex items-center justify-center gap-2 text-sm text-bb-text2">
            <Loader2 size={16} className="animate-spin" aria-hidden="true" /> 주소를 확인하고 있습니다…
          </p>
        )}
        {state.kind === "done" && (
          <div role="status" className="mt-4 space-y-2">
            <CheckCircle2 size={32} className="mx-auto text-teal-600" aria-hidden="true" />
            <p className="text-sm text-bb-text">이메일 주소를 확인했습니다.</p>
            <p className="text-xs text-bb-text2">이메일 알림을 켜 두었다면 이제부터 생기는 업무 알림을 메일로 받습니다.</p>
          </div>
        )}
        {state.kind === "failed" && (
          <div role="alert" className="mt-4 space-y-2">
            <AlertCircle size={32} className="mx-auto text-red-500" aria-hidden="true" />
            <p className="text-sm text-bb-text">{state.text}</p>
          </div>
        )}
        {state.kind === "noToken" && (
          <p role="alert" className="mt-4 text-sm text-bb-text2">확인할 링크 정보가 없습니다. 메일의 링크를 다시 열거나, 프로필 설정에서 확인 메일을 다시 요청해 주세요.</p>
        )}
        {state.kind !== "checking" && (
          <Link href="/profile/settings" className="mt-6 inline-block rounded-lg bg-bb-primary px-4 py-2 text-sm font-medium text-white hover:bg-bb-primary-h">
            프로필 설정으로
          </Link>
        )}
      </section>
    </main>
  );
}
