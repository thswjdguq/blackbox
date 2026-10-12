import { useState, type FormEvent, type ReactNode } from "react"
import { Layers, Eye, EyeOff, Loader2, ArrowRight, Check, CheckSquare, Square, Hash, CalendarClock, GraduationCap, BookOpen, AlertCircle } from "lucide-react"
import { signIn, signUp, demoSignIn, type Role } from "./auth"

type Mode = "login" | "signup"

function Field({ label, id, hint, error, children }: { label: string; id: string; hint?: ReactNode; error?: string; children: ReactNode }) {
  return (
    <div>
      <div className="flex items-baseline justify-between mb-1.5">
        <label htmlFor={id} className="text-[13px] font-medium text-slate-700">{label}</label>
        {hint}
      </div>
      {children}
      {error && <p className="text-xs text-red-600 mt-1.5">{error}</p>}
    </div>
  )
}
const inputCls = (err?: boolean) =>
  `w-full h-10 px-3 rounded-md border bg-white text-[14px] text-slate-800 placeholder:text-slate-400 transition-shadow focus:outline-none focus:ring-[3px] ${err ? "border-red-300 focus:ring-red-100" : "border-slate-200 hover:border-slate-300 focus:border-blue-500 focus:ring-blue-100"}`

function strength(pw: string) {
  let s = 0
  if (pw.length >= 8) s++
  if (/[A-Za-z]/.test(pw) && /\d/.test(pw)) s++
  if (/[^A-Za-z0-9]/.test(pw) || pw.length >= 12) s++
  return s
}

/** 오른쪽 — 노션 페이지처럼 보이는 제품 미리보기 */
const FLOW = [
  { I: BookOpen, t: "과제 조건 정리", d: "평가 기준과 마감을 한곳에 적어 둡니다." },
  { I: CheckSquare, t: "업무 분담", d: "누가 무엇을 언제까지 맡았는지 남깁니다." },
  { I: CalendarClock, t: "회의 체크인", d: "출석 코드로 회의 참여를 기록합니다." },
  { I: GraduationCap, t: "검토 · 수정 요청", d: "피드백과 수정 이력이 결과물에 쌓입니다." },
  { I: Hash, t: "제출본 확정", d: "최종본을 해시로 고정해 변경을 막습니다." },
]

function ServiceIntro() {
  return (
    <ol className="relative w-full max-w-[420px]">
      <span className="absolute left-[19px] top-5 bottom-5 w-px bg-slate-200" aria-hidden />
      {FLOW.map(({ I, t, d }, i) => (
        <li key={t} className="relative flex gap-4 py-3">
          <span className="relative z-10 w-10 h-10 shrink-0 rounded-lg bg-white border border-slate-200 shadow-sm flex items-center justify-center text-slate-600"><I size={17} /></span>
          <div className="pt-0.5">
            <p className="text-[14px] font-semibold text-slate-800"><span className="font-mono-data text-[11px] text-slate-400 mr-2">0{i + 1}</span>{t}</p>
            <p className="text-[13px] text-slate-500 mt-0.5">{d}</p>
          </div>
        </li>
      ))}
    </ol>
  )
}

export default function AuthScreen() {
  const [mode, setMode] = useState<Mode>(() => (location.hash === "#signup" ? "signup" : "login"))
  const [f, setF] = useState({ name: "", email: "", password: "", confirm: "", school: "", role: "STUDENT" as Role })
  const [remember, setRemember] = useState(true)
  const [agree, setAgree] = useState(false)
  const [showPw, setShowPw] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof f, v: string) => { setF(x => ({ ...x, [k]: v })); setErrors(e => ({ ...e, [k]: "" })); setFormError("") }
  const switchMode = (m: Mode) => { setMode(m); setErrors({}); setFormError(""); history.replaceState(null, "", m === "signup" ? "#signup" : "#login") }

  const validate = () => {
    const e: Record<string, string> = {}
    if (!/^\S+@\S+\.\S+$/.test(f.email)) e.email = "올바른 이메일 형식이 아닙니다."
    if (mode === "login") { if (!f.password) e.password = "비밀번호를 입력해 주세요." }
    else {
      if (!f.name.trim()) e.name = "이름을 입력해 주세요."
      if (strength(f.password) < 2) e.password = "8자 이상, 영문과 숫자를 함께 써 주세요."
      if (f.confirm !== f.password) e.confirm = "비밀번호가 일치하지 않습니다."
      if (!agree) e.agree = "약관에 동의해야 가입할 수 있습니다."
    }
    setErrors(e); return Object.keys(e).length === 0
  }
  const submit = async (ev: FormEvent) => {
    ev.preventDefault(); if (!validate()) return
    setBusy(true)
    try {
      if (mode === "login") await signIn(f.email, f.password, remember)
      else await signUp({ name: f.name, email: f.email, password: f.password, role: f.role, school: f.school })
    } catch (err: any) { setFormError(err.message) } finally { setBusy(false) }
  }
  const st = strength(f.password)

  return (
    <div className="min-h-screen grid lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)] bg-white">
      <section className="flex flex-col px-6 sm:px-12 py-8">
        <header className="flex items-center gap-2 cursor-pointer w-fit" onClick={() => { location.hash = "" }} title="처음 화면으로">
          <span className="w-7 h-7 rounded-md bg-slate-900 flex items-center justify-center"><Layers size={14} className="text-white" /></span>
          <span className="font-semibold text-[15px] text-slate-900 tracking-tight">Team Blackbox</span>
        </header>

        <div className="flex-1 flex items-center">
          <div className="w-full max-w-[380px] mx-auto py-10 animate-fade-in" key={mode}>
            <h1 className="font-page font-bold text-[32px] leading-[1.15] text-slate-900">
              {mode === "login" ? "다시 오신 걸 환영해요" : "팀 프로젝트를\n기록으로 남기세요".split("\n").map((l, i) => <span key={i} className="block">{l}</span>)}
            </h1>
            <p className="text-[15px] text-slate-500 mt-3 leading-relaxed">
              {mode === "login" ? "업무, 회의, 제출본이 한 페이지에 모여 있습니다." : "누가 무엇을 했는지 자동으로 쌓이는 팀 워크스페이스."}
            </p>

            <button type="button" onClick={demoSignIn}
              className="mt-7 w-full h-10 rounded-md border border-slate-200 bg-white hover:bg-slate-50 text-[14px] font-medium text-slate-700 flex items-center justify-center gap-2 transition-colors">
              <BookOpen size={15} className="text-slate-500" />데모 계정으로 둘러보기
            </button>
            <div className="flex items-center gap-3 my-6 text-[12px] text-slate-400"><span className="flex-1 h-px bg-slate-200" />또는 이메일로<span className="flex-1 h-px bg-slate-200" /></div>

            <form onSubmit={submit} noValidate className="space-y-4">
              {mode === "signup" && (
                <>
                  <div role="radiogroup" aria-label="가입 유형" className="grid grid-cols-2 gap-2">
                    {([["STUDENT", "학생", "팀 프로젝트 참여"], ["PROFESSOR", "교수 · 조교", "팀 현황 열람"]] as const).map(([v, l, d]) => (
                      <button key={v} type="button" role="radio" aria-checked={f.role === v} onClick={() => set("role", v)}
                        className={`text-left rounded-md border px-3 py-2.5 transition-colors ${f.role === v ? "border-blue-500 bg-blue-50/60 ring-[3px] ring-blue-100" : "border-slate-200 hover:bg-slate-50"}`}>
                        <p className="text-[14px] font-semibold text-slate-800">{l}</p><p className="text-[12px] text-slate-500">{d}</p>
                      </button>
                    ))}
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <Field label="이름" id="au-name" error={errors.name}>
                      <input id="au-name" autoComplete="name" className={inputCls(!!errors.name)} value={f.name} onChange={e => set("name", e.target.value)} placeholder="이지민" />
                    </Field>
                    <Field label="학교" id="au-school">
                      <input id="au-school" className={inputCls()} value={f.school} onChange={e => set("school", e.target.value)} placeholder="선택 입력" />
                    </Field>
                  </div>
                </>
              )}
              <Field label={mode === "signup" && f.role === "STUDENT" ? "학교 이메일" : "이메일"} id="au-email" error={errors.email}>
                <input id="au-email" type="email" autoComplete="email" className={inputCls(!!errors.email)} value={f.email} onChange={e => set("email", e.target.value)} placeholder="name@university.ac.kr" />
              </Field>
              <Field label="비밀번호" id="au-pw" error={errors.password}
                hint={mode === "login" ? <button type="button" className="text-[12px] text-slate-500 hover:text-slate-800" onClick={() => setFormError("프로토타입에서는 비밀번호 재설정을 지원하지 않습니다. 새로 가입하거나 데모 계정을 이용해 주세요.")}>비밀번호를 잊으셨나요?</button> : undefined}>
                <div className="relative">
                  <input id="au-pw" type={showPw ? "text" : "password"} autoComplete={mode === "login" ? "current-password" : "new-password"}
                    className={`${inputCls(!!errors.password)} pr-10`} value={f.password} onChange={e => set("password", e.target.value)} placeholder={mode === "signup" ? "8자 이상, 영문+숫자" : "••••••••"} />
                  <button type="button" onClick={() => setShowPw(v => !v)} aria-label={showPw ? "비밀번호 숨기기" : "비밀번호 보기"}
                    className="absolute right-1 top-1 w-8 h-8 rounded flex items-center justify-center text-slate-400 hover:text-slate-700 hover:bg-slate-100">{showPw ? <EyeOff size={15} /> : <Eye size={15} />}</button>
                </div>
                {mode === "signup" && f.password && (
                  <div className="flex items-center gap-2 mt-2">
                    <div className="flex-1 grid grid-cols-3 gap-1">{[0, 1, 2].map(i => <span key={i} className={`h-1 rounded-full ${i < st ? ["bg-red-400", "bg-amber-400", "bg-emerald-500"][st - 1] : "bg-slate-200"}`} />)}</div>
                    <span className="text-[11px] text-slate-500 w-8">{["약함", "약함", "보통", "강함"][st]}</span>
                  </div>
                )}
              </Field>
              {mode === "signup" && (
                <Field label="비밀번호 확인" id="au-pw2" error={errors.confirm}>
                  <input id="au-pw2" type={showPw ? "text" : "password"} autoComplete="new-password" className={inputCls(!!errors.confirm)} value={f.confirm} onChange={e => set("confirm", e.target.value)} />
                </Field>
              )}

              {mode === "login" ? (
                <label className="flex items-center gap-2 text-[13px] text-slate-600 cursor-pointer select-none">
                  <input type="checkbox" checked={remember} onChange={e => setRemember(e.target.checked)} className="w-4 h-4 accent-blue-600" />로그인 상태 유지
                </label>
              ) : (
                <div>
                  <label className="flex items-start gap-2 text-[13px] text-slate-600 cursor-pointer select-none leading-relaxed">
                    <input type="checkbox" checked={agree} onChange={e => { setAgree(e.target.checked); setErrors(x => ({ ...x, agree: "" })) }} className="w-4 h-4 mt-0.5 accent-blue-600" />
                    <span><span className="underline underline-offset-2">이용약관</span>과 <span className="underline underline-offset-2">활동 기록 수집</span>에 동의합니다. 팀 활동은 기여도 계산에 사용됩니다.</span>
                  </label>
                  {errors.agree && <p className="text-xs text-red-600 mt-1.5 ml-6">{errors.agree}</p>}
                </div>
              )}

              {formError && (
                <p role="alert" className="flex items-start gap-2 text-[13px] text-red-700 bg-red-50 border border-red-100 rounded-md px-3 py-2.5"><AlertCircle size={15} className="shrink-0 mt-0.5" />{formError}</p>
              )}

              <button type="submit" disabled={busy}
                className="w-full h-10 rounded-md bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white text-[14px] font-semibold flex items-center justify-center gap-1.5 transition-colors disabled:opacity-60">
                {busy ? <Loader2 size={16} className="animate-spin" /> : <>{mode === "login" ? "로그인" : "가입하고 시작하기"}<ArrowRight size={15} /></>}
              </button>
            </form>

            <p className="text-[13px] text-slate-500 mt-6 text-center">
              {mode === "login" ? "아직 계정이 없나요? " : "이미 계정이 있나요? "}
              <button onClick={() => switchMode(mode === "login" ? "signup" : "login")} className="font-semibold text-slate-900 underline underline-offset-4 decoration-slate-300 hover:decoration-slate-900">
                {mode === "login" ? "회원가입" : "로그인"}
              </button>
            </p>
          </div>
        </div>
        <p className="text-[11px] text-slate-400">프로토타입 · 계정 정보는 이 브라우저에만 저장됩니다.</p>
      </section>

      <aside className="hidden lg:flex relative items-center justify-center bg-[#F7F6F3] border-l border-slate-200 px-12 overflow-hidden">
        <div className="absolute top-10 left-12 right-12">
          <p className="text-[12px] font-semibold uppercase tracking-[0.16em] text-slate-400">과제 조건부터 제출 기록까지</p>
          <p className="font-page text-[22px] text-slate-800 mt-2 leading-snug max-w-[380px]">"누가 했는지"를 기억에 맡기지 않는 팀플.</p>
        </div>
        <ServiceIntro />
      </aside>
    </div>
  )
}
