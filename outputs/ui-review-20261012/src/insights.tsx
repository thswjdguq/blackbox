import { useEffect, useMemo, useState } from "react"
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from "recharts"
import { AlertTriangle, Check, ChevronRight, Copy, Info, Lock, ShieldCheck, X, ArrowUpRight, Clock } from "lucide-react"
import type { Project, Member } from "./App"

// ─── 기여도 계산 ───────────────────────────────────────────────
// 화면에 공식을 그대로 보여 주기 위해 가중치를 상수로 둡니다.
export const WEIGHTS = { done: 3, doing: 1, file: 2, review: 2, checkin: 1 } as const

export interface MemberStat {
  member: Member
  done: number; doing: number; files: number; reviews: number; checkins: number
  score: number; share: number; lastActive: string | null
}

const isOn = (t: { assigneeMemberId: string; assigneeMemberIds?: string[] }, id: string) =>
  t.assigneeMemberId === id || (t.assigneeMemberIds ?? []).includes(id)

export function computeStats(p: Project): MemberStat[] {
  const people = p.members.filter(m => m.role !== "OBSERVER")
  const rows = people.map(m => {
    const done = p.tasks.filter(t => t.status === "DONE" && isOn(t, m.id)).length
    const doing = p.tasks.filter(t => t.status === "IN_PROGRESS" && isOn(t, m.id)).length
    const files = p.files.reduce((n, f) => n + f.versions.filter(v => v.uploadedByMemberId === m.id).length, 0)
    const reviews = p.deliverables.reduce((n, d) => n + d.reviews.filter(r => r.reviewerMemberId === m.id && r.status !== "pending").length, 0)
    const checkins = p.meetings.filter(mt => mt.checkedInMemberIds.includes(m.id)).length
    const times = [m.lastActiveAt, ...p.activityLog.filter(a => a.memberId === m.id).map(a => a.createdAt)].filter(Boolean).sort()
    const score = done * WEIGHTS.done + doing * WEIGHTS.doing + files * WEIGHTS.file + reviews * WEIGHTS.review + checkins * WEIGHTS.checkin
    return { member: m, done, doing, files, reviews, checkins, score, share: 0, lastActive: times.at(-1) ?? null }
  })
  const total = rows.reduce((n, r) => n + r.score, 0)
  rows.forEach(r => { r.share = total ? r.score / total : 0 })
  return rows.sort((a, b) => b.score - a.score)
}

export function summarize(p: Project) {
  const stats = computeStats(p)
  const n = stats.length || 1
  const top = stats[0]
  const imbalance = top ? Math.max(0, top.share - 1 / n) : 0
  const idle = stats.filter(s => !s.lastActive || daysSince(s.lastActive) >= 7)
  const tasksDone = p.tasks.filter(t => t.status === "DONE").length
  const meetings = p.meetings.filter(m => m.attendeeMemberIds.length > 0)
  const expected = meetings.reduce((x, m) => x + m.attendeeMemberIds.length, 0)
  const came = meetings.reduce((x, m) => x + m.checkedInMemberIds.length, 0)
  return {
    stats, top, imbalance, idle, tasksDone, tasksTotal: p.tasks.length,
    attendance: expected ? came / expected : null,
    locked: p.deliverables.filter(d => d.finalLock).length, deliverables: p.deliverables.filter(d => !d.archived).length,
  }
}

function daysSince(iso: string) { return Math.floor((Date.now() - new Date(iso).getTime()) / 86400000) }
function rel(iso: string | null) {
  if (!iso) return "기록 없음"
  const d = daysSince(iso)
  if (d <= 0) { const h = Math.floor((Date.now() - new Date(iso).getTime()) / 3600000); return h <= 0 ? "방금" : `${h}시간 전` }
  return d === 1 ? "어제" : `${d}일 전`
}
const pct = (x: number) => `${Math.round(x * 100)}%`

// ─── 공통 조각 ────────────────────────────────────────────────
function Dot({ m, size = 20 }: { m: Member; size?: number }) {
  return (
    <span className="rounded-full text-white font-semibold flex items-center justify-center shrink-0"
      style={{ background: m.color, width: size, height: size, fontSize: size * 0.48 }}>{m.name.slice(0, 1)}</span>
  )
}

function Stat({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: "warn" | "ok" }) {
  return (
    <div className="px-4 py-3.5">
      <p className="text-xs text-slate-500">{label}</p>
      <p className={`mt-1 text-[22px] font-semibold tracking-[-0.02em] font-mono-data ${tone === "warn" ? "text-red-600" : tone === "ok" ? "text-emerald-600" : "text-slate-900"}`}>{value}</p>
      {sub && <p className="text-xs text-slate-500 mt-0.5">{sub}</p>}
    </div>
  )
}

function ShareBar({ stats }: { stats: MemberStat[] }) {
  return (
    <div className="flex h-2 rounded-full overflow-hidden bg-slate-100" aria-hidden>
      {stats.filter(s => s.share > 0).map(s => (
        <span key={s.member.id} style={{ width: `${s.share * 100}%`, background: s.member.color }} className="h-full border-r-2 border-white last:border-r-0" />
      ))}
    </div>
  )
}

// ─── 프로젝트 안: 기여도 화면 ──────────────────────────────────
export function ContributionView({ project }: { project: Project }) {
  const s = useMemo(() => summarize(project), [project])
  const n = s.stats.length
  const warn = n > 1 && s.imbalance >= 0.15

  const daily = useMemo(() => {
    const days: { key: string; label: string; [k: string]: number | string }[] = []
    for (let i = 13; i >= 0; i--) {
      const d = new Date(); d.setHours(0, 0, 0, 0); d.setDate(d.getDate() - i)
      const row: any = { key: d.toDateString(), label: `${d.getMonth() + 1}/${d.getDate()}` }
      s.stats.forEach(st => { row[st.member.id] = 0 })
      days.push(row)
    }
    project.activityLog.forEach(a => {
      const row: any = days.find(d => d.key === new Date(a.createdAt).toDateString())
      if (row && a.memberId in row) row[a.memberId]++
    })
    return days
  }, [project, s.stats])

  const feed = [...project.activityLog].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 12)

  return (
    <div className="h-full overflow-y-auto bg-white">
      <div className="max-w-[1040px] mx-auto px-8 pt-10 pb-16">
        <header className="mb-8">
          <h1 className="font-page font-bold text-[30px] text-slate-900 leading-tight">기여도</h1>
          <p className="text-sm text-slate-500 mt-2">업무·파일·검토·회의 기록에서 자동으로 계산합니다. 누가 했는지를 기억이 아닌 기록으로 봅니다.</p>
        </header>

        <section aria-label="요약" className="grid grid-cols-2 lg:grid-cols-4 border border-slate-200 rounded-lg divide-x divide-slate-200 [&>*:nth-child(3)]:max-lg:border-l-0 max-lg:[&>*:nth-child(n+3)]:border-t mb-8">
          <Stat label="완료한 업무" value={`${s.tasksDone}/${s.tasksTotal}`} sub={s.tasksTotal ? `진행률 ${pct(s.tasksDone / s.tasksTotal)}` : "업무 없음"} />
          <Stat label="회의 출석률" value={s.attendance == null ? "—" : pct(s.attendance)} sub="체크인 기준" />
          <Stat label="기여 편중도" value={n > 1 ? `+${pct(s.imbalance)}` : "—"} sub={s.top && n > 1 ? `${s.top.member.name} · 균등 몫 ${pct(1 / n)} 대비` : "팀원 2명 이상부터"} tone={warn ? "warn" : undefined} />
          <Stat label="7일 이상 활동 없음" value={`${s.idle.length}명`} sub={s.idle.map(x => x.member.name).join(", ") || "모두 활동 중"} tone={s.idle.length ? "warn" : "ok"} />
        </section>

        {(warn || s.idle.length > 0) && (
          <div role="status" className="mb-8 flex items-start gap-3 rounded-lg bg-red-50 border border-red-100 px-4 py-3">
            <AlertTriangle size={16} className="text-red-600 mt-0.5 shrink-0" />
            <div className="text-sm text-slate-700 leading-relaxed">
              {warn && <p><b className="font-semibold text-slate-900">{s.top!.member.name}</b>님에게 기여가 몰려 있어요 ({pct(s.top!.share)}). 남은 업무를 다시 나눠 보세요.</p>}
              {s.idle.length > 0 && <p>{s.idle.map(x => x.member.name).join(", ")}님은 7일 넘게 기록이 없어요.</p>}
            </div>
          </div>
        )}

        <section className="mb-10">
          <div className="flex items-end justify-between mb-3">
            <h2 className="text-sm font-semibold text-slate-900">팀원별 기여</h2>
            <p className="text-xs text-slate-500 flex items-center gap-1"><Info size={12} />점수 = 완료 업무×{WEIGHTS.done} + 진행 중×{WEIGHTS.doing} + 파일 버전×{WEIGHTS.file} + 검토×{WEIGHTS.review} + 체크인×{WEIGHTS.checkin}</p>
          </div>
          <ShareBar stats={s.stats} />
          <div role="table" className="mt-4 border-t border-slate-200 overflow-x-auto">
            <div role="row" className="grid grid-cols-[minmax(150px,1fr)_minmax(140px,1.4fr)_64px_64px_64px_64px_90px] h-8 items-center text-xs text-slate-500 border-b border-slate-200 min-w-[680px]">
              <span>팀원</span><span>비중</span><span className="text-right">완료</span><span className="text-right">파일</span><span className="text-right">검토</span><span className="text-right">출석</span><span className="text-right">최근 활동</span>
            </div>
            {s.stats.map(st => (
              <div role="row" key={st.member.id} className="grid grid-cols-[minmax(150px,1fr)_minmax(140px,1.4fr)_64px_64px_64px_64px_90px] h-11 items-center text-sm border-b border-slate-100 hover:bg-slate-50 min-w-[680px]">
                <span className="flex items-center gap-2 min-w-0"><Dot m={st.member} /><span className="truncate text-slate-900">{st.member.name}</span>{st.member.role === "LEADER" && <span className="text-[11px] text-slate-500">팀장</span>}</span>
                <span className="flex items-center gap-2 pr-6">
                  <span className="flex-1 h-1.5 rounded-full bg-slate-100 overflow-hidden"><span className="block h-full rounded-full" style={{ width: `${st.share * 100}%`, background: st.member.color }} /></span>
                  <span className="w-10 text-right font-mono-data text-xs text-slate-700">{pct(st.share)}</span>
                </span>
                <span className="text-right font-mono-data text-slate-700">{st.done}</span>
                <span className="text-right font-mono-data text-slate-700">{st.files}</span>
                <span className="text-right font-mono-data text-slate-700">{st.reviews}</span>
                <span className="text-right font-mono-data text-slate-700">{st.checkins}</span>
                <span className={`text-right text-xs ${st.lastActive && daysSince(st.lastActive) < 7 ? "text-slate-500" : "text-red-600 font-medium"}`}>{rel(st.lastActive)}</span>
              </div>
            ))}
          </div>
        </section>

        <div className="grid lg:grid-cols-[1.5fr_1fr] gap-10 mb-10">
          <section>
            <h2 className="text-sm font-semibold text-slate-900 mb-1">최근 14일 활동</h2>
            <p className="text-xs text-slate-500 mb-4">활동 기록 수 · 팀원별</p>
            <div className="h-52">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={daily} margin={{ top: 4, right: 0, left: -28, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="#E9E9E7" />
                  <XAxis dataKey="label" tick={{ fontSize: 11, fill: "#787774" }} tickLine={false} axisLine={{ stroke: "#E9E9E7" }} interval={1} />
                  <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: "#787774" }} tickLine={false} axisLine={false} />
                  <Tooltip cursor={{ fill: "rgba(55,53,47,0.04)" }} contentStyle={{ fontSize: 12, borderRadius: 6, border: "1px solid #E9E9E7", boxShadow: "0 4px 12px rgba(15,15,15,0.08)" }} />
                  {s.stats.map((st, i) => (
                    <Bar key={st.member.id} dataKey={st.member.id} name={st.member.name} stackId="a" fill={st.member.color}
                      radius={i === s.stats.length - 1 ? [2, 2, 0, 0] : 0} maxBarSize={18} />
                  ))}
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>
          <section>
            <h2 className="text-sm font-semibold text-slate-900 mb-4">타임라인</h2>
            {feed.length === 0 ? <p className="text-xs text-slate-500">아직 기록이 없어요.</p> : (
              <ol className="relative">
                <span className="absolute left-[9px] top-2 bottom-2 w-px bg-slate-200" aria-hidden />
                {feed.map(a => {
                  const m = project.members.find(x => x.id === a.memberId)
                  return (
                    <li key={a.id} className="relative flex gap-3 py-1.5">
                      {m ? <span className="relative z-10 ring-2 ring-white rounded-full"><Dot m={m} /></span> : <span className="relative z-10 w-5 h-5 rounded-full bg-slate-200 ring-2 ring-white" />}
                      <div className="min-w-0 pt-px">
                        <p className="text-sm text-slate-700 leading-snug">{a.description}</p>
                        <p className="text-xs text-slate-500 mt-0.5">{m?.name ?? "알 수 없음"} · {rel(a.createdAt)}</p>
                      </div>
                    </li>
                  )
                })}
              </ol>
            )}
          </section>
        </div>

        <HashVault project={project} />
      </div>
    </div>
  )
}

// ─── 제출본 지문 (Hash Vault) ──────────────────────────────────
async function sha256(text: string) {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text))
  return [...new Uint8Array(buf)].map(b => b.toString(16).padStart(2, "0")).join("")
}

export function HashVault({ project }: { project: Project }) {
  const locks = useMemo(() => project.deliverables.flatMap(d =>
    [...(d.finalLock ? [{ d, lock: d.finalLock, current: true }] : []), ...d.lockHistory.map(lock => ({ d, lock, current: false }))]
  ).sort((a, b) => b.lock.lockedAt.localeCompare(a.lock.lockedAt)), [project])
  const [hashes, setHashes] = useState<Record<string, string>>({})
  const [copied, setCopied] = useState("")

  useEffect(() => {
    let alive = true
    Promise.all(locks.map(async ({ d, lock }) => {
      const items = lock.items.map(it => {
        const g = project.files.find(f => f.id === it.fileGroupId)
        const v = g?.versions.find(x => x.version === it.version)
        return { file: g?.name ?? it.fileGroupId, version: it.version, uploadedAt: v?.uploadedAt ?? "", by: v?.uploadedByMemberId ?? "", size: v?.size ?? "" }
      })
      return [lock.id, await sha256(JSON.stringify({ deliverable: d.id, lockedAt: lock.lockedAt, by: lock.lockedByMemberId, items }))] as const
    })).then(r => alive && setHashes(Object.fromEntries(r)))
    return () => { alive = false }
  }, [locks, project.files])

  return (
    <section>
      <div className="flex items-end justify-between mb-1">
        <h2 className="text-sm font-semibold text-slate-900 flex items-center gap-1.5"><ShieldCheck size={15} className="text-slate-500" />제출본 지문</h2>
        <span className="text-xs text-slate-500">{locks.filter(l => l.current).length}개 확정 · 이력 {locks.length}건</span>
      </div>
      <p className="text-xs text-slate-500 mb-4">제출본을 확정하면 파일 이름·버전·업로드 시각으로 SHA-256 지문을 만들어요. 나중에 바뀌면 지문이 달라집니다.</p>
      {locks.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-200 px-4 py-6 text-center">
          <Lock size={20} strokeWidth={1.5} className="text-slate-300 mx-auto mb-2" />
          <p className="text-xs text-slate-500">아직 확정된 제출본이 없어요. 제출물 화면에서 최종본을 확정하면 여기에 기록돼요.</p>
        </div>
      ) : (
        <ul className="border-t border-slate-200">
          {locks.map(({ d, lock, current }) => {
            const h = hashes[lock.id]
            const who = project.members.find(m => m.id === lock.lockedByMemberId)
            return (
              <li key={lock.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-3 border-b border-slate-100">
                <span className={`text-[11px] font-medium px-1.5 py-0.5 rounded ${current ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-slate-500 line-through"}`}>{current ? "현재 확정" : "교체됨"}</span>
                <span className="text-sm text-slate-900 min-w-0 truncate">{d.title}</span>
                <span className="text-xs text-slate-500">파일 {lock.items.length}개 · {who?.name ?? "—"} · {new Date(lock.lockedAt).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" })}</span>
                <button onClick={() => { if (h) { navigator.clipboard?.writeText(h).catch(() => {}); setCopied(lock.id); setTimeout(() => setCopied(""), 1400) } }}
                  className="ml-auto flex items-center gap-1.5 font-mono-data text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded px-2 py-1 hover:bg-slate-100" title="지문 복사">
                  {h ? `${h.slice(0, 10)}…${h.slice(-6)}` : "계산 중…"}
                  {copied === lock.id ? <Check size={12} className="text-emerald-600" /> : <Copy size={12} className="text-slate-400" />}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

// ─── 교수·조교: 수업 대시보드 ──────────────────────────────────
export function ClassDashboard({ projects, onOpen }: { projects: Project[]; onOpen: (id: string) => void }) {
  const rows = useMemo(() => projects.map(p => ({ p, s: summarize(p) })), [projects])
  const [openId, setOpenId] = useState<string | null>(null)
  const sel = rows.find(r => r.p.id === openId)
  const risky = rows.filter(r => (r.s.stats.length > 1 && r.s.imbalance >= 0.15) || r.s.idle.length > 0).length

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpenId(null)
    window.addEventListener("keydown", onKey); return () => window.removeEventListener("keydown", onKey)
  }, [])

  if (rows.length === 0) return (
    <p className="text-sm text-slate-500 py-12 text-center">학생 팀에게 받은 초대 코드로 참여하면 이곳에 팀 현황이 모여요.</p>
  )

  return (
    <div>
      <div className="grid grid-cols-3 border border-slate-200 rounded-lg divide-x divide-slate-200 mb-6">
        <Stat label="담당 팀" value={`${rows.length}`} />
        <Stat label="확인이 필요한 팀" value={`${risky}`} tone={risky ? "warn" : "ok"} sub="편중 15%↑ 또는 7일 비활동" />
        <Stat label="제출본 확정" value={`${rows.reduce((n, r) => n + r.s.locked, 0)}/${rows.reduce((n, r) => n + r.s.deliverables, 0)}`} />
      </div>
      <div role="table" className="border-t border-slate-200 overflow-x-auto">
        <div role="row" className="grid grid-cols-[minmax(180px,1.4fr)_minmax(120px,1fr)_88px_88px_120px_24px] h-8 items-center text-xs text-slate-500 border-b border-slate-200 min-w-[640px]">
          <span>팀</span><span>기여 분포</span><span className="text-right">진행률</span><span className="text-right">편중도</span><span className="pl-4">상태</span><span />
        </div>
        {rows.map(({ p, s }) => {
          const warn = s.stats.length > 1 && s.imbalance >= 0.15
          return (
            <button role="row" key={p.id} onClick={() => setOpenId(p.id)}
              className={`w-full grid grid-cols-[minmax(180px,1.4fr)_minmax(120px,1fr)_88px_88px_120px_24px] h-12 items-center text-left text-sm border-b border-slate-100 min-w-[640px] transition-colors ${openId === p.id ? "bg-blue-50" : "hover:bg-slate-50"}`}>
              <span className="min-w-0 pr-3"><span className="block truncate text-slate-900">{p.name}</span><span className="block truncate text-xs text-slate-500">{p.subject || "과목 없음"} · {s.stats.length}명</span></span>
              <span className="pr-4"><ShareBar stats={s.stats} /></span>
              <span className="text-right font-mono-data text-slate-700">{s.tasksTotal ? pct(s.tasksDone / s.tasksTotal) : "—"}</span>
              <span className={`text-right font-mono-data ${warn ? "text-red-600 font-medium" : "text-slate-700"}`}>{s.stats.length > 1 ? `+${pct(s.imbalance)}` : "—"}</span>
              <span className="pl-4 text-xs">
                {warn || s.idle.length ? <span className="inline-flex items-center gap-1 text-red-600"><AlertTriangle size={12} />확인 필요</span>
                  : <span className="inline-flex items-center gap-1 text-emerald-600"><Check size={12} />양호</span>}
              </span>
              <ChevronRight size={14} className="text-slate-300" />
            </button>
          )
        })}
      </div>

      {sel && (
        <div className="fixed inset-0 z-50">
          <div className="absolute inset-0 bg-black/20" onClick={() => setOpenId(null)} />
          <aside role="dialog" aria-modal="true" aria-label={`${sel.p.name} 상세`}
            className="absolute right-0 top-0 h-full w-full max-w-[420px] bg-white border-l border-slate-200 shadow-[-12px_0_32px_-12px_rgba(15,15,15,0.18)] flex flex-col animate-[slide-in_.18s_ease-out]">
            <div className="h-12 px-4 flex items-center gap-2 border-b border-slate-100 shrink-0">
              <span className="text-xs text-slate-500 truncate flex-1">{sel.p.subject || "수업"}</span>
              <button onClick={() => onOpen(sel.p.id)} className="h-7 px-2 rounded-md text-xs text-slate-600 hover:bg-slate-100 flex items-center gap-1">프로젝트 열기<ArrowUpRight size={12} /></button>
              <button onClick={() => setOpenId(null)} aria-label="닫기" className="p-1.5 rounded-md hover:bg-slate-100"><X size={15} className="text-slate-500" /></button>
            </div>
            <div className="flex-1 overflow-y-auto px-6 py-6">
              <h2 className="font-page font-bold text-[22px] text-slate-900 leading-snug">{sel.p.name}</h2>
              <dl className="mt-4 grid grid-cols-[88px_1fr] gap-y-2 text-sm">
                <dt className="text-slate-500">진행률</dt><dd className="text-slate-900 font-mono-data">{sel.s.tasksDone}/{sel.s.tasksTotal} 업무</dd>
                <dt className="text-slate-500">출석률</dt><dd className="text-slate-900 font-mono-data">{sel.s.attendance == null ? "—" : pct(sel.s.attendance)}</dd>
                <dt className="text-slate-500">마감</dt><dd className="text-slate-900">{sel.p.deadline || "—"}</dd>
                <dt className="text-slate-500">제출본</dt><dd className="text-slate-900">{sel.s.locked}/{sel.s.deliverables} 확정</dd>
              </dl>
              <h3 className="text-sm font-semibold text-slate-900 mt-8 mb-3">팀원별 기여</h3>
              <ul className="space-y-3">
                {sel.s.stats.map(st => (
                  <li key={st.member.id}>
                    <div className="flex items-center gap-2 text-sm">
                      <Dot m={st.member} /><span className="text-slate-900">{st.member.name}</span>
                      <span className="ml-auto font-mono-data text-xs text-slate-700">{pct(st.share)}</span>
                    </div>
                    <div className="mt-1.5 ml-7 h-1.5 rounded-full bg-slate-100 overflow-hidden"><span className="block h-full rounded-full" style={{ width: `${st.share * 100}%`, background: st.member.color }} /></div>
                    <p className="ml-7 mt-1 text-xs text-slate-500 flex items-center gap-1"><Clock size={11} />{rel(st.lastActive)} · 완료 {st.done} · 파일 {st.files} · 출석 {st.checkins}</p>
                  </li>
                ))}
              </ul>
              {(sel.s.imbalance >= 0.15 && sel.s.stats.length > 1) || sel.s.idle.length ? (
                <div className="mt-6 rounded-lg bg-red-50 border border-red-100 px-3.5 py-3 text-sm text-slate-700 leading-relaxed">
                  {sel.s.imbalance >= 0.15 && sel.s.stats.length > 1 && <p>{sel.s.top!.member.name}님 비중이 균등 몫보다 {pct(sel.s.imbalance)} 높아요.</p>}
                  {sel.s.idle.length > 0 && <p>{sel.s.idle.map(x => x.member.name).join(", ")}님 7일 이상 기록 없음.</p>}
                </div>
              ) : null}
              <h3 className="text-sm font-semibold text-slate-900 mt-8 mb-3">최근 활동</h3>
              <ul className="space-y-2">
                {[...sel.p.activityLog].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 6).map(a => (
                  <li key={a.id} className="text-sm text-slate-700">{a.description}<span className="text-xs text-slate-500"> · {rel(a.createdAt)}</span></li>
                ))}
              </ul>
            </div>
          </aside>
        </div>
      )}
    </div>
  )
}
