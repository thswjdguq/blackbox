import { useState } from "react"
import {
  Layers, ArrowRight, ClipboardList, MessageSquare, CalendarCheck, ShieldCheck, BarChart2, UserPlus,
  Users, Eye, Bell, BookOpen, Check,
} from "lucide-react"

type Go = (to: "login" | "signup") => void

const NAV = [["intro", "서비스 소개"], ["features", "주요 기능"], ["how", "이용 방법"], ["faq", "자주 묻는 질문"]] as const

const TABS = {
  student: {
    label: "팀원", note: "학생 · 조원",
    items: [
      { I: ClipboardList, t: "내 업무만 한눈에", d: "맡은 업무와 마감(D-day)을\n프로젝트별로 모아 보여 줘요." },
      { I: MessageSquare, t: "결과물에 바로 피드백", d: "의견·수정 요청을 업무에 남기고,\n반영하면 기록으로 닫혀요." },
      { I: CalendarCheck, t: "회의 출석 체크인", d: "회의 코드 6자리만 입력하면\n출석이 자동으로 기록돼요." },
    ],
  },
  leader: {
    label: "팀장", note: "조장 · 프로젝트 리더",
    items: [
      { I: UserPlus, t: "초대 코드로 팀 구성", d: "코드나 링크 하나만 공유하면\n팀원이 바로 참여해요." },
      { I: Users, t: "업무 분담 · 칸반", d: "담당자·기한·중요도를 정하고\n목록과 보드로 진행 상황을 봐요." },
      { I: ShieldCheck, t: "제출본 확정", d: "최종본을 고정하고 지문(해시)을 남겨\n나중에 바뀌었는지 확인할 수 있어요." },
    ],
  },
  professor: {
    label: "교수 · 조교", note: "열람 권한",
    items: [
      { I: Eye, t: "수업 대시보드", d: "담당 팀들의 진행률과 기여 분포를\n한 표에서 비교해요." },
      { I: BarChart2, t: "기여 편중 알림", d: "한 사람에게 기여가 몰리거나\n7일 넘게 활동이 없으면 표시돼요." },
      { I: Bell, t: "근거 있는 평가", d: "업무·파일·회의·피드백 기록을\n그대로 확인할 수 있어요." },
    ],
  },
} as const

const STEPS = [
  { t: "프로젝트 만들기", d: "과목, 과제 조건,\n마감을 적어요." },
  { t: "팀원 초대", d: "6자리 초대 코드를\n공유해요." },
  { t: "업무 분담", d: "누가 무엇을 언제까지\n맡을지 정해요." },
  { t: "피드백 · 수정", d: "결과물에 의견과\n수정 요청을 남겨요." },
  { t: "제출본 확정", d: "최종본을 고정하고\n제출 기록을 남겨요." },
]

const FAQ = [
  ["무료인가요?", "네. 지금은 프로토타입으로, 회원가입만 하면 모든 기능을 쓸 수 있어요."],
  ["팀원은 어떻게 들어오나요?", "팀장이 팀원 화면에서 초대 코드를 복사해 보내면, 팀원은 로그인 후 \"참여하기\"에 코드를 입력하면 돼요."],
  ["기여도는 어떻게 계산하나요?", "완료한 업무, 올린 파일 버전, 검토, 회의 체크인을 가중치로 합산해요. 계산 공식이 화면에 그대로 공개돼요."],
  ["교수님도 쓸 수 있나요?", "교수·조교로 가입하면 열람 권한으로 팀에 참여해 수업 대시보드에서 여러 팀을 비교할 수 있어요."],
] as const

function Dot() { return <span className="text-blue-500">.</span> }

export default function Landing({ go }: { go: Go }) {
  const [tab, setTab] = useState<keyof typeof TABS>("student")
  const [open, setOpen] = useState<number | null>(0)
  const scrollTo = (id: string) => document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" })

  return (
    <div className="min-h-screen bg-white text-slate-800 break-keep [word-break:keep-all] [-webkit-text-size-adjust:100%]">
      {/* ── 상단 내비게이션 ── */}
      <header className="sticky top-0 z-40 bg-white/95 backdrop-blur border-b border-slate-200">
        <div className="max-w-[1200px] mx-auto h-14 sm:h-16 px-4 sm:px-6 flex items-center gap-4 lg:gap-10">
          <button onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })} className="flex items-center gap-2 shrink-0" aria-label="Team Blackbox 처음으로">
            <span className="w-8 h-8 rounded-lg bg-slate-900 flex items-center justify-center"><Layers size={16} className="text-white" /></span>
            <span className="text-[17px] sm:text-[19px] font-bold tracking-[-0.015em] text-slate-900">Team Blackbox</span>
          </button>
          <nav aria-label="주요 메뉴" className="hidden lg:flex items-center gap-8">
            {NAV.map(([id, l]) => (
              <button key={id} onClick={() => scrollTo(id)} className="text-[16px] font-medium text-slate-700 hover:text-slate-900">{l}</button>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-1.5 sm:gap-2">
            <button onClick={() => go("signup")} className="h-9 sm:h-11 px-3 sm:px-5 rounded-lg border border-slate-200 text-[14px] sm:text-[15px] whitespace-nowrap font-medium text-slate-800 hover:bg-slate-50">회원가입</button>
            <button onClick={() => go("login")} className="h-9 sm:h-11 px-3 sm:px-5 rounded-lg border border-slate-200 text-[14px] sm:text-[15px] whitespace-nowrap font-medium text-slate-800 hover:bg-slate-50">로그인</button>
          </div>
        </div>
      </header>

      <main>
        {/* ── 히어로 배너 ── */}
        <section id="intro" className="max-w-[1200px] mx-auto px-4 sm:px-6 pt-4 sm:pt-6 scroll-mt-24">
          <div className="relative overflow-hidden rounded-[20px] bg-[#E6E6E3] md:min-h-[340px] flex flex-col md:flex-row md:items-center">
            <img src="https://images.unsplash.com/photo-1531545514256-b1400bc00f31?w=1400&h=900&fit=crop&auto=format"
              alt="맥북 앞에 모여 함께 과제를 보고 있는 팀원들"
              className="w-full h-[180px] sm:h-[240px] md:absolute md:right-0 md:top-0 md:h-full md:w-[68%] object-cover object-center bg-slate-300" />
            <div className="hidden md:block absolute inset-0 bg-gradient-to-r from-[#E6E6E3] via-[#E6E6E3]/90 via-45% to-transparent" aria-hidden />
            <div className="relative px-6 sm:px-10 lg:px-14 py-7 md:py-10 max-w-[720px]">
              <p className="inline-flex items-center gap-1.5 text-[13px] font-semibold text-slate-700 bg-white/70 rounded-full px-3 py-1 mb-5">
                <BookOpen size={13} />대학생 팀 프로젝트(팀플) 협업 툴
              </p>
              <h1 className="text-[23px] sm:text-[28px] lg:text-[34px] font-bold leading-[1.4] tracking-[-0.01em] text-slate-900">
                <span className="lg:whitespace-nowrap">팀플이 공정해지는 가장 간단한 방법!</span><br />
                <span className="lg:whitespace-nowrap">초대 코드만 있으면 바로 참여할 수 있어요<Dot /></span>
              </h1>
              <p className="text-[15px] sm:text-[17px] text-slate-700 mt-3 sm:mt-4 leading-[1.7]">
                업무 분담, 피드백, 회의 출석, 최종 제출본까지<br className="hidden md:block" /> 
                누가 무엇을 했는지 자동으로 기록돼요.
              </p>
              <div className="flex flex-wrap gap-2.5 mt-6">
                <button onClick={() => go("signup")} className="h-12 sm:h-[52px] px-6 sm:px-7 rounded-full bg-slate-900 text-white text-[16px] font-semibold hover:bg-slate-700 inline-flex items-center gap-2">
                  무료로 시작하기<ArrowRight size={16} />
                </button>
                <button onClick={() => go("login")} className="h-12 sm:h-[52px] px-5 sm:px-6 rounded-full bg-white/80 text-slate-900 text-[16px] font-semibold hover:bg-white inline-flex items-center gap-2">
                  <UserPlus size={16} />초대 코드로 참여
                </button>
              </div>
            </div>
          </div>
        </section>

        {/* ── 주요 기능 (탭) ── */}
        <section id="features" className="max-w-[1200px] mx-auto px-4 sm:px-6 pt-12 sm:pt-16 scroll-mt-20">
          <h2 className="text-[26px] sm:text-[34px] lg:text-[42px] font-bold leading-[1.3] tracking-[-0.015em] text-slate-900">
            누가 뭘 했는지,<br />기록이 대신 말해 줘요<Dot />
          </h2>
          <div className="mt-8 flex items-end border-b border-slate-200" role="tablist" aria-label="사용자별 기능">
            {(Object.keys(TABS) as (keyof typeof TABS)[]).map(k => (
              <button key={k} role="tab" aria-selected={tab === k} onClick={() => setTab(k)}
                className={`flex-1 sm:flex-none sm:w-[180px] lg:w-[220px] pb-3 sm:pb-4 text-[16px] sm:text-[20px] lg:text-[22px] whitespace-nowrap font-semibold border-b-[3px] -mb-px transition-colors ${tab === k ? "border-slate-900 text-slate-900" : "border-transparent text-slate-400 hover:text-slate-600"}`}>
                {TABS[k].label}
              </button>
            ))}
            <span className="ml-auto pb-4 text-[14px] text-slate-500 hidden sm:block">{TABS[tab].note}</span>
          </div>
          <div role="tabpanel" className="grid md:grid-cols-3 gap-5 mt-8" key={tab}>
            {TABS[tab].items.map(({ I, t, d }) => (
              <article key={t} className="rounded-2xl border border-slate-200 p-5 sm:p-7 hover:border-slate-300 hover:shadow-[0_8px_24px_-12px_rgba(15,15,15,0.15)] transition-all animate-fade-in">
                <span className="w-11 h-11 rounded-xl bg-slate-100 flex items-center justify-center text-slate-700"><I size={20} /></span>
                <h3 className="text-[18px] sm:text-[20px] font-bold tracking-[-0.01em] text-slate-900 mt-4 sm:mt-6">{t}</h3>
                <p className="text-[15px] text-slate-600 mt-2 leading-relaxed whitespace-pre-line break-keep">{d}</p>
              </article>
            ))}
          </div>
        </section>

        {/* ── 이용 방법 ── */}
        <section id="how" className="max-w-[1200px] mx-auto px-4 sm:px-6 pt-12 sm:pt-16 scroll-mt-20">
          <h2 className="text-[26px] sm:text-[34px] lg:text-[42px] font-bold leading-[1.3] tracking-[-0.015em] text-slate-900">
            5단계면 팀플 끝<Dot />
          </h2>
          <ol className="mt-8 grid sm:grid-cols-2 lg:grid-cols-5 gap-px bg-slate-200 rounded-2xl overflow-hidden border border-slate-200">
            {STEPS.map((s, i) => (
              <li key={s.t} className="bg-white p-6">
                <span className="font-mono-data text-[13px] font-semibold text-blue-500">STEP {i + 1}</span>
                <p className="text-[17px] sm:text-[18px] font-bold tracking-[-0.01em] text-slate-900 mt-2 sm:mt-3">{s.t}</p>
                <p className="text-[14px] text-slate-600 mt-1.5 leading-relaxed whitespace-pre-line break-keep">{s.d}</p>
              </li>
            ))}
          </ol>
        </section>

        {/* ── FAQ ── */}
        <section id="faq" className="max-w-[1200px] mx-auto px-4 sm:px-6 pt-12 sm:pt-16 scroll-mt-20">
          <h2 className="text-[26px] sm:text-[34px] lg:text-[40px] font-bold tracking-[-0.015em] text-slate-900">자주 묻는 질문<Dot /></h2>
          <ul className="mt-8 border-t border-slate-200">
            {FAQ.map(([q, a], i) => (
              <li key={q} className="border-b border-slate-200">
                <button onClick={() => setOpen(open === i ? null : i)} aria-expanded={open === i}
                  className="w-full flex items-center justify-between gap-4 py-5 sm:py-6 text-left text-[16px] sm:text-[18px] font-semibold text-slate-900">
                  {q}<span className={`text-slate-400 text-[24px] leading-none transition-transform ${open === i ? "rotate-45" : ""}`}>+</span>
                </button>
                {open === i && <p className="pb-5 sm:pb-6 -mt-2 text-[15px] sm:text-[16px] text-slate-600 leading-relaxed max-w-[760px]">{a}</p>}
              </li>
            ))}
          </ul>
        </section>

        {/* ── 마지막 CTA ── */}
        <section className="max-w-[1200px] mx-auto px-4 sm:px-6 py-14 sm:py-20">
          <div className="rounded-[20px] bg-slate-900 text-white px-6 sm:px-14 py-10 sm:py-14 flex flex-wrap items-center gap-8">
            <div className="flex-1 min-w-[260px]">
              <h2 className="text-[22px] sm:text-[28px] lg:text-[34px] font-bold tracking-[-0.01em] leading-snug">이번 학기 팀플, 기록으로 시작하세요.</h2>
              <ul className="mt-4 flex flex-wrap gap-x-5 gap-y-1 text-[15px] text-white/70">
                {["회원가입 1분", "초대 코드로 팀 구성", "교수님 열람 지원"].map(x => <li key={x} className="flex items-center gap-1.5"><Check size={14} />{x}</li>)}
              </ul>
            </div>
            <button onClick={() => go("signup")} className="h-[52px] px-7 rounded-full bg-white text-slate-900 text-[16px] font-semibold hover:bg-slate-100 inline-flex items-center gap-2">
              무료로 시작하기<ArrowRight size={16} />
            </button>
          </div>
        </section>
      </main>

      <footer className="border-t border-slate-200">
        <div className="max-w-[1200px] mx-auto px-4 sm:px-6 py-8 flex flex-wrap items-center gap-4 text-[13px] text-slate-500">
          <span className="font-semibold text-slate-700">Team Blackbox</span>
          <span>대학생 팀 프로젝트 협업 툴 · 프로토타입</span>
          <span className="sm:ml-auto">데이터는 이 브라우저에만 저장됩니다.</span>
        </div>
      </footer>
    </div>
  )
}
