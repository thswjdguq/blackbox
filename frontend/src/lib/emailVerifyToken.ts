/**
 * 확인 링크 `/email/verify#token=…`의 토큰을 다루는 곳(K-41 0.5 9장).
 * 토큰은 fragment로만 받아 이 탭의 sessionStorage로 옮기고 주소에서 지운다.
 * 그래서 로그인으로 보낼 때의 next·쿼리·서버 접근 로그에 토큰이 실리지 않는다.
 */
export const VERIFY_PATH = "/email/verify";
const KEY = "bb-email-verify-token";

function hashParams(hash: string) {
  return new URLSearchParams(hash.startsWith("#") ? hash.slice(1) : hash);
}

/** fragment에서 토큰을 읽는다. 없거나 모양이 이상하면 null */
export function tokenFromHash(hash: string): string | null {
  const value = hashParams(hash).get("token");
  if (!value || value.length > 512 || /[\s\u0000-\u001F\u007F]/.test(value)) return null;
  return value;
}

type Storage = Pick<globalThis.Storage, "getItem" | "setItem" | "removeItem">;

/** 이 탭의 sessionStorage. 접근 자체가 막힌 브라우저(보안 설정 등)면 null */
export function tabStorage(): Storage | null {
  try { return window.sessionStorage; } catch { return null; }
}

/**
 * 쓸 토큰을 정한다.
 * - 주소에 token 칸이 있으면(새 링크) 유효 여부와 상관없이 주소에서 지운다. 유효하면 저장하고 쓰고,
 *   잘못됐으면 이전 탭 토큰도 지우고 null(다른 링크의 옛 토큰을 대신 보내지 않는다)
 * - 주소에 token 칸이 없을 때(로그인 뒤 돌아온 경우)만 탭에 저장해 둔 토큰을 쓴다
 */
export function stashToken(location: { hash: string }, storage: Storage | null, replace: (url: string) => void): string | null {
  // window.location은 replace 뒤 바뀌므로 지우기 전에 값을 받아 둔다
  const hash = location.hash;
  if (hashParams(hash).has("token")) {
    const fresh = tokenFromHash(hash);
    try { replace(VERIFY_PATH); } catch { /* 주소를 못 바꿔도 토큰은 받아 둔 값으로만 쓴다 */ }
    try {
      if (fresh) storage?.setItem(KEY, fresh);
      else storage?.removeItem(KEY);
    } catch { /* 저장소를 못 쓰면 이번 화면에서만 쓴다 */ }
    return fresh;
  }
  try { return storage?.getItem(KEY) ?? null; } catch { return null; }
}

export function clearToken(storage: Storage | null) {
  try { storage?.removeItem(KEY); } catch { /* 없음 */ }
}
