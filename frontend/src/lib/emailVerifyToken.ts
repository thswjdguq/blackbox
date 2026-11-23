/**
 * 확인 링크 `/email/verify#token=…`의 토큰을 다루는 곳(K-41 0.5 9장).
 * 토큰은 fragment로만 받아 이 탭의 sessionStorage로 옮기고 주소에서 지운다.
 * 그래서 로그인으로 보낼 때의 next·쿼리·서버 접근 로그에 토큰이 실리지 않는다.
 */
export const VERIFY_PATH = "/email/verify";
const KEY = "bb-email-verify-token";

/** fragment에서 토큰을 읽는다. 없거나 모양이 이상하면 null */
export function tokenFromHash(hash: string): string | null {
  const raw = hash.startsWith("#") ? hash.slice(1) : hash;
  const value = new URLSearchParams(raw).get("token");
  if (!value || value.length > 512 || /[\s\u0000-\u001F\u007F]/.test(value)) return null;
  return value;
}

type Storage = Pick<globalThis.Storage, "getItem" | "setItem" | "removeItem">;

/** 주소의 토큰을 탭 저장소로 옮기고, 주소에서 fragment를 지운다. 옮긴 토큰(또는 이미 있던 토큰)을 돌려준다 */
export function stashToken(location: { hash: string }, storage: Storage, replace: (url: string) => void): string | null {
  const fromHash = tokenFromHash(location.hash);
  if (fromHash) {
    try { storage.setItem(KEY, fromHash); } catch { /* 저장소를 못 쓰면 이번 화면에서만 쓴다 */ }
    replace(VERIFY_PATH);
    return fromHash;
  }
  try { return storage.getItem(KEY); } catch { return null; }
}

export function clearToken(storage: Storage) {
  try { storage.removeItem(KEY); } catch { /* 없음 */ }
}
