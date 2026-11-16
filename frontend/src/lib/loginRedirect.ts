/**
 * 로그인 뒤 원래 가려던 주소로 돌려보낸다(이메일 알림 링크 등, K-41).
 * 같은 사이트의 경로만 받는다. "//다른곳"·"/\\다른곳"·절대 주소는 다른 사이트로 보내는 데 쓰일 수 있어 버린다.
 */
const PUBLIC = ["/login", "/signup"];

export function safeNext(raw: string | null | undefined): string | null {
  if (!raw || raw.length > 2000) return null;
  if (!raw.startsWith("/") || raw.startsWith("//") || raw.startsWith("/\\")) return null;
  if (/[\u0000-\u001F\u007F]/.test(raw)) return null;
  const path = raw.split(/[?#]/)[0];
  if (path === "/" || PUBLIC.some((p) => path === p || path.startsWith(p + "/"))) return null;
  return raw;
}

/** 지금 주소를 next로 담은 로그인 주소. 원래 주소가 로그인·가입 화면이면 담지 않는다 */
export function loginHref(current?: string): string {
  const here = current ?? (typeof window === "undefined" ? "" : window.location.pathname + window.location.search + window.location.hash);
  const next = safeNext(here);
  return next ? `/login?next=${encodeURIComponent(next)}` : "/login";
}

/** 로그인 화면에서 돌아갈 곳. next가 없거나 안전하지 않으면 대시보드 */
export function afterLogin(search?: string): string {
  const query = search ?? (typeof window === "undefined" ? "" : window.location.search);
  return safeNext(new URLSearchParams(query).get("next")) ?? "/dashboard";
}
