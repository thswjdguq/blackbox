// 프론트엔드 프로토타입용 인증 — 계정은 이 브라우저의 localStorage에만 저장됩니다.
export type Role = "STUDENT" | "PROFESSOR"
export interface Session { name: string; email: string; role: Role; school?: string }
interface Account extends Session { pwHash: string; createdAt: string }

const ACC_KEY = "blackbox_accounts"
const SES_KEY = "blackbox_session"
const EVT = "blackbox-auth"

async function hash(pw: string) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(pw))
  return [...new Uint8Array(buf)].map(b => b.toString(16).padStart(2, "0")).join("")
}
const accounts = (): Account[] => { try { return JSON.parse(localStorage.getItem(ACC_KEY) || "[]") } catch { return [] } }

export function getSession(): Session | null {
  if (typeof window === "undefined") return null
  try {
    const raw = localStorage.getItem(SES_KEY) ?? sessionStorage.getItem(SES_KEY)
    return raw ? JSON.parse(raw) : null
  } catch { return null }
}
function setSession(s: Session, remember: boolean) {
  (remember ? localStorage : sessionStorage).setItem(SES_KEY, JSON.stringify(s))
  window.dispatchEvent(new Event(EVT))
}
export function logout() {
  localStorage.removeItem(SES_KEY); sessionStorage.removeItem(SES_KEY)
  window.dispatchEvent(new Event(EVT))
}
export function onAuthChange(fn: () => void) {
  window.addEventListener(EVT, fn); return () => window.removeEventListener(EVT, fn)
}

export async function signUp(a: { name: string; email: string; password: string; role: Role; school: string }) {
  const email = a.email.trim().toLowerCase()
  if (accounts().some(x => x.email === email)) throw new Error("이미 가입된 이메일입니다. 로그인해 주세요.")
  const acc: Account = { name: a.name.trim(), email, role: a.role, school: a.school.trim(), pwHash: await hash(a.password), createdAt: new Date().toISOString() }
  localStorage.setItem(ACC_KEY, JSON.stringify([...accounts(), acc]))
  setSession({ name: acc.name, email, role: acc.role, school: acc.school }, true)
}

export async function signIn(email: string, password: string, remember: boolean) {
  const acc = accounts().find(x => x.email === email.trim().toLowerCase())
  if (!acc || acc.pwHash !== await hash(password)) throw new Error("이메일 또는 비밀번호가 올바르지 않습니다.")
  setSession({ name: acc.name, email: acc.email, role: acc.role, school: acc.school }, remember)
}

export function demoSignIn() {
  setSession({ name: "이지민", email: "demo@blackbox.team", role: "STUDENT", school: "한빛대학교" }, false)
}
