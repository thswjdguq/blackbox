import { useState, useCallback, useEffect, useRef, useMemo } from "react"
import { ContributionView, ClassDashboard } from "./insights"
import AuthScreen from "./AuthScreen"
import Landing from "./Landing"
import { getSession, logout, onAuthChange } from "./auth"
import {
  Layers, LayoutDashboard, ClipboardList, CalendarClock, FolderOpen,
  Users, Plus, Search, X, ChevronDown, ChevronRight, ChevronLeft,
  ChevronUp, Check, CheckCircle, AlertTriangle, AlertCircle, Bell,
  Clock, Calendar, Hash, Download, Upload, Sparkles, Pencil, Trash2,
  Copy, Info, Loader2, Link2, UserCheck, Circle, ArrowLeft,
  List, Columns, Home, MapPin, ExternalLink, FileText, CalendarDays,
  FileClock, MessageSquare, ShieldCheck, Settings, UserPlus, BarChart2, LogOut, ChevronsUpDown, Menu as MenuIcon, Send, CornerDownLeft, Flag,
} from "lucide-react"

// ═══════════════════════════════════════════════════════════════
// TYPES
// ═══════════════════════════════════════════════════════════════
type ProjectView = "overview"|"tasks"|"meetings"|"files"|"deliverables"|"team"|"settings"|"analytics"
const PROJECT_VIEWS: ProjectView[] = ["overview","tasks","meetings","files","deliverables","team","settings","analytics"]
type TaskStatus = "TODO"|"IN_PROGRESS"|"DONE"
type MemberRole = "LEADER"|"MEMBER"|"OBSERVER"

export interface Member { id:string; name:string; role:MemberRole; color:string; email:string; lastActiveAt:string }
type ReqCheck = "unchecked"|"met"|"needs_work"
interface Requirement {
  id:string; content:string; required:boolean
  checkStatus?:ReqCheck; checkedByMemberId?:string; checkedAt?:string; evidence?:string
}
interface FileRef { fileGroupId:string; version:number }
interface DeliverableFileLink { fileGroupId:string; role:"result"|"reference"; linkedAt:string; linkedByMemberId:string }
export interface FinalLock {
  id:string; items:FileRef[]; lockedByMemberId:string; lockedAt:string; note:string
  openIssues:string[]; replacedAt?:string; replacedByMemberId?:string; replaceReason?:string
}
interface FileReview {
  id:string; fileGroupId:string; fileVersion:number
  requestedByMemberId:string; reviewerMemberId:string
  status:"pending"|"approved"|"change_requested"
  comment:string; createdAt:string; reviewedAt:string
  requestNote?:string; responseNote?:string; previousReviewId?:string; revisionTaskId?:string
  reviewerLog?:{ fromMemberId:string; toMemberId:string; byMemberId:string; at:string }[]
}
interface SubmissionRecord {
  id:string; submittedByMemberId:string; submittedAt:string
  fileGroupId:string; fileVersion:number
  submissionPath:string; note:string
  kind?:"file"|"link"|"presentation"|"physical"|"other"; items?:FileRef[]
  target?:string; confirmationNo?:string; confirmationUrl?:string; receiptNote?:string; description?:string
  recordedByMemberId?:string; recordedAt?:string
  supersedesId?:string; changeType?:"first"|"correction"|"resubmission"; changeReason?:string
  complete?:boolean; warnings?:string[]; warningReason?:string
}
export interface Deliverable {
  id:string; title:string; dueDate:string; ownerMemberId:string
  submissionMethod:string; description:string; requirements:Requirement[]
  status:"PREPARING"|"SUBMITTED"
  reviews:FileReview[]; submissionRecords:SubmissionRecord[]
  fileLinks:DeliverableFileLink[]; finalLock:FinalLock|null; lockHistory:FinalLock[]; archived:boolean
}
type TaskPriority = "HIGH"|"MEDIUM"|"LOW"|""
export interface Task {
  id:string; title:string; status:TaskStatus; assigneeMemberId:string
  assigneeMemberIds:string[]; priority:TaskPriority; description:string
  dueDate:string; completionCriteria:string; deliverableId:string
  requirementId:string; meetingId:string; meetingItemId:string
  updatedAt:string; createdAt:string
  linkedFileIds:string[]; blockedReason:string; dependsOnTaskId:string
  sourceReviewId:string; targetFileGroupId:string
  comments?:TaskComment[]
}
type CommentKind = "comment"|"request"|"approve"
export interface TaskComment {
  id:string; memberId:string; kind:CommentKind; body:string; createdAt:string
  resolvedAt?:string; resolvedByMemberId?:string
}
interface MeetingActionItem { id:string; assigneeMemberId:string; task:string; dueDate:string; taskId:string }
export interface Meeting {
  id:string; title:string; date:string; time:string; location:string
  attendeeMemberIds:string[]; checkinCode:string; checkedInMemberIds:string[]
  hasMinutes:boolean; minutesContent:string
  aiSummary:{ keyDecisions:string[]; actionItems:MeetingActionItem[]; nextSteps:string[] }|null
  manualActionItems:MeetingActionItem[]
  createdAt:string
}
interface FileVersion {
  version:number; uploadedByMemberId:string; uploadedAt:string
  size:string; changeNote:string; changeType:"new"|"modified"; fileDataKey?:string; url?:string
}
export interface FileGroup { id:string; name:string; category:string; versions:FileVersion[]; kind?:"file"|"link" }
export interface ActivityEntry { id:string; description:string; memberId:string; createdAt:string }

export interface Project {
  id:string; name:string; subject:string; semester:string; description:string; deadline:string
  isDemo:boolean; createdAt:string
  members:Member[]; tasks:Task[]; deliverables:Deliverable[]
  meetings:Meeting[]; files:FileGroup[]
  readAlertIds:string[]
  activityLog:ActivityEntry[]
  status:"active"|"archived"; archivedAt:string; assignmentBrief:string; assignmentUrl:string
  retrospective:string; resolvedAlertIds:string[]
  inviteCode:string
}

// ═══════════════════════════════════════════════════════════════
// CONSTANTS
// ═══════════════════════════════════════════════════════════════
function getMyUserId(): string {
  if (typeof window === "undefined") return ""
  const k = "blackbox_user_id"
  let s = localStorage.getItem(k)
  if (!s) { s = Math.random().toString(36).slice(2, 10); localStorage.setItem(k, s) }
  // 로그인한 계정마다 고유 ID. 처음 로그인한 계정이 기존(로그인 이전) 데이터를 이어받습니다.
  const email = getSession()?.email
  if (!email) return s
  let map: Record<string, string> = {}
  try { map = JSON.parse(localStorage.getItem("blackbox_user_map") || "{}") } catch {}
  if (!map[email]) {
    map[email] = Object.values(map).includes(s) ? Math.random().toString(36).slice(2, 10) : s
    localStorage.setItem("blackbox_user_map", JSON.stringify(map))
  }
  return map[email]
}
let MY_USER_ID = getMyUserId()
/** SSR·테스트 환경에서도 안전하게 해시를 읽습니다 */
function readHash(): string { return typeof window === "undefined" ? "" : window.location.hash.slice(1) }
function refreshMyUserId() { MY_USER_ID = getMyUserId() }
const INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
/** 프로젝트 ID에서 항상 같은 6자리 초대 코드를 만듭니다 */
function makeInviteCode(seed: string): string {
  let h = 2166136261
  for (const ch of seed) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619) >>> 0 }
  let out = ""
  for (let i = 0; i < 6; i++) { out += INVITE_ALPHABET[h % INVITE_ALPHABET.length]; h = Math.floor(h / INVITE_ALPHABET.length) ^ Math.imul(h, 31 + i) >>> 0 }
  return out
}
const uid = () => Math.random().toString(36).slice(2, 10)
const MEMBER_COLORS = ["#2563eb","#0891b2","#be185d","#059669","#d97706","#7c3aed","#dc2626"]
const STORAGE_KEY = "blackbox_v3_projects"
const FILE_DATA_KEY = "blackbox_v3_file_data"

// ═══════════════════════════════════════════════════════════════
// DEMO DATA
// ═══════════════════════════════════════════════════════════════
function createDemoProject(): Project { return migrateProject(createDemoRaw()) }
function createDemoRaw(): any {
  const m1id = MY_USER_ID, m2id = "demo-m2", m3id = "demo-m3"
  return {
    id:"demo-project", name:"캠퍼스 생활 개선 서비스",
    subject:"소프트웨어공학", semester:"2026-1",
    description:"대학생 캠퍼스 생활 불편 해소를 위한 모바일 서비스 기획 및 초기 프로토타입 개발",
    deadline:"2026-11-29", isDemo:true, createdAt:"2026-09-01T09:00:00",
    readAlertIds:[], activityLog:[], status:"active", archivedAt:"", assignmentBrief:"", assignmentUrl:"", retrospective:"", resolvedAlertIds:[],
    members:[
      { id:m1id, name:"이지민", role:"LEADER",  color:"#2563eb", email:"jimin@univ.ac.kr",  lastActiveAt:"2026-09-29T10:00:00" },
      { id:m2id, name:"박서준", role:"MEMBER",  color:"#0891b2", email:"seojun@univ.ac.kr", lastActiveAt:"2026-09-29T09:30:00" },
      { id:m3id, name:"최유나", role:"MEMBER",  color:"#be185d", email:"yuna@univ.ac.kr",   lastActiveAt:"2026-09-14T14:00:00" },
    ],
    deliverables:[
      { id:"d1", title:"중간발표 자료", dueDate:"2026-10-20", ownerMemberId:m1id, submissionMethod:"학교 LMS 과제함",
        description:"사용자 조사 결과와 초기 UI 설계 발표", status:"PREPARING" as const,
        reviews:[], submissionRecords:[], fileLinks:[
          { fileGroupId:"f1", role:"result", linkedAt:"2026-09-27T16:40:00", linkedByMemberId:m1id },
          { fileGroupId:"f2", role:"reference", linkedAt:"2026-09-27T16:40:00", linkedByMemberId:m1id },
        ], finalLock:null, lockHistory:[], archived:false,
        requirements:[
          { id:"r1", content:"사용자 인터뷰 3건 이상 포함", required:true },
          { id:"r2", content:"시장 분석 및 경쟁 서비스 비교 포함", required:true },
          { id:"r3", content:"UI 프로토타입 화면 3개 이상", required:false },
        ]},
      { id:"d2", title:"최종 보고서", dueDate:"2026-11-28", ownerMemberId:"", submissionMethod:"",
        description:"서비스 개선 결과물과 사용자 테스트 결과", status:"PREPARING" as const,
        reviews:[], submissionRecords:[], fileLinks:[], finalLock:null, lockHistory:[], archived:false,
        requirements:[
          { id:"r4", content:"개선 전후 화면 비교 포함", required:true },
          { id:"r5", content:"사용자 테스트 결과 (5명 이상)", required:true },
        ]},
    ],
    tasks:[
      { id:"t1", title:"사용자 인터뷰 정리", status:"DONE" as TaskStatus,        assigneeMemberId:m2id, assigneeMemberIds:[m2id], priority:"" as TaskPriority, description:"", dueDate:"2026-10-05", completionCriteria:"인터뷰 3건 공통 문제 표로 정리, 출처 첨부",   deliverableId:"d1", requirementId:"r1", meetingId:"mt1", meetingItemId:"ai1", updatedAt:"2026-09-28T15:00:00", createdAt:"2026-09-10T10:00:00" },
      { id:"t2", title:"UI 개선안 작성",     status:"IN_PROGRESS" as TaskStatus, assigneeMemberId:m3id, assigneeMemberIds:[m3id], priority:"HIGH" as TaskPriority, description:"", dueDate:"2026-10-12", completionCriteria:"개선 전후 화면 3개 이상 Figma로 작성",        deliverableId:"d1", requirementId:"r3", meetingId:"mt1", meetingItemId:"ai2", updatedAt:"2026-09-27T11:00:00", createdAt:"2026-09-10T10:00:00" },
      { id:"t3", title:"발표자료 통합",      status:"TODO" as TaskStatus,        assigneeMemberId:m1id, assigneeMemberIds:[m1id], priority:"MEDIUM" as TaskPriority, description:"", dueDate:"2026-10-17", completionCriteria:"파트별 자료 합쳐 흐름 확인, 발표 연습 1회",   deliverableId:"d1", requirementId:"",  meetingId:"",    meetingItemId:"",    updatedAt:"2026-09-20T10:00:00", createdAt:"2026-09-15T09:00:00" },
      { id:"t4", title:"사용자 테스트 진행", status:"TODO" as TaskStatus,        assigneeMemberId:"",   assigneeMemberIds:[], priority:"" as TaskPriority, description:"", dueDate:"2026-11-10", completionCriteria:"참여자 5명 이상, 결과 스프레드시트 정리",       deliverableId:"d2", requirementId:"r5", meetingId:"",    meetingItemId:"",    updatedAt:"2026-09-25T10:00:00", createdAt:"2026-09-15T09:00:00" },
      { id:"t5", title:"경쟁 서비스 조사",   status:"TODO" as TaskStatus,        assigneeMemberId:m2id, assigneeMemberIds:[m2id], priority:"LOW" as TaskPriority, description:"", dueDate:"",           completionCriteria:"경쟁 사례 3개 출처와 비교표로 정리",            deliverableId:"",   requirementId:"",  meetingId:"",    meetingItemId:"",    updatedAt:"2026-09-22T10:00:00", createdAt:"2026-09-15T09:00:00" },
    ],
    meetings:[
      { id:"mt1", title:"중간 점검 회의", date:"2026-09-15", time:"14:00", location:"도서관 스터디룸 3",
        attendeeMemberIds:[m1id,m2id,m3id], checkinCode:"MT2600", checkedInMemberIds:[m1id,m2id,m3id],
        hasMinutes:true,
        minutesContent:"## 안건 1: 인터뷰 진행 현황\n\n박서준: 현재 2건 완료. 나머지 1건은 이번 주 진행 예정.\n\n## 안건 2: UI 방향성 논의\n\n최유나가 초기 와이어프레임 3개 제시. 팀원 피드백 반영하여 수정 예정.\n\n## 결정 사항\n\n- 인터뷰 3건을 9월 19일까지 완료\n- 최유나 UI 프로토타입 9월 28일 전 1차 완성",
        manualActionItems:[],
        aiSummary:{
          keyDecisions:["인터뷰 3건 9월 19일까지 완료","UI 프로토타입 1차 완성: 9월 28일","다음 회의: 9월 28일 15:00"],
          actionItems:[
            {id:"ai1",assigneeMemberId:m2id,task:"사용자 인터뷰 3번째 건 진행 및 정리",dueDate:"2026-09-19",taskId:"t1"},
            {id:"ai2",assigneeMemberId:m3id,task:"UI 프로토타입 1차 완성 (Figma)",dueDate:"2026-09-27",taskId:"t2"},
            {id:"ai3",assigneeMemberId:m1id,task:"발표자료 구조 초안 작성",dueDate:"2026-09-25",taskId:""},
          ],
          nextSteps:["인터뷰 결과를 공유 드라이브에 업로드","피그마 링크를 단톡에 공유"],
        },
        createdAt:"2026-09-10T09:00:00" },
      { id:"mt2", title:"UI 리뷰 회의", date:"2026-09-28", time:"15:00", location:"도서관 스터디룸 3",
        attendeeMemberIds:[m1id,m2id,m3id], checkinCode:"MT2601", checkedInMemberIds:[m1id,m2id],
        hasMinutes:false, minutesContent:"", aiSummary:null, manualActionItems:[],
        createdAt:"2026-09-15T09:00:00" },
    ],
    files:[
      { id:"f1", name:"발표자료_중간.pptx", category:"발표자료", versions:[
        { version:2, uploadedByMemberId:m1id, uploadedAt:"2026-09-27T16:30:00", size:"4.2 MB", changeNote:"디자인 템플릿 변경, 인터뷰 결과 슬라이드 추가", changeType:"modified" },
        { version:1, uploadedByMemberId:m1id, uploadedAt:"2026-09-22T11:00:00", size:"3.8 MB", changeNote:"초안 업로드", changeType:"new" },
      ]},
      { id:"f2", name:"사용자인터뷰_결과.xlsx", category:"조사자료", versions:[
        { version:1, uploadedByMemberId:m2id, uploadedAt:"2026-09-26T14:00:00", size:"0.8 MB", changeNote:"인터뷰 3건 정리 완료", changeType:"new" },
      ]},
    ],
  }
}

// ═══════════════════════════════════════════════════════════════
// STORAGE
// ═══════════════════════════════════════════════════════════════
function migrateProject(raw: any): Project {
  return {
    id: raw.id ?? uid(),
    name: raw.name ?? "프로젝트",
    subject: raw.subject ?? "",
    semester: raw.semester ?? "",
    description: raw.description ?? "",
    deadline: raw.deadline ?? "",
    isDemo: raw.isDemo ?? false,
    createdAt: raw.createdAt ?? new Date().toISOString(),
    inviteCode: raw.inviteCode || makeInviteCode(String(raw.id ?? "")),
    members: (raw.members ?? []).map((m: any) => ({
      email: "", lastActiveAt: new Date().toISOString(), ...m,
    })),
    tasks: (raw.tasks ?? []).map((t: any) => {
      const ids: string[] = (t.assigneeMemberIds && t.assigneeMemberIds.length) ? t.assigneeMemberIds : (t.assigneeMemberId ? [t.assigneeMemberId] : [])
      return {
        meetingItemId: "", description: "", priority: "" as TaskPriority,
        linkedFileIds: [], blockedReason: "", dependsOnTaskId: "", sourceReviewId: "", targetFileGroupId: "",
        ...t, comments: t.comments ?? [],
        requirementId: t.requirementId === "_new" ? "" : (t.requirementId ?? ""),
        assigneeMemberIds: ids, assigneeMemberId: ids[0] ?? "",
      }
    }),
    deliverables: (raw.deliverables ?? []).map((d: any) => ({
      submissionMethod: "", description: "", status: "PREPARING" as const,
      finalLock: null, lockHistory: [], archived: false, ...d,
      requirements: (d.requirements ?? []).map((r: any) => ({ checkStatus: "unchecked", checkedByMemberId: "", checkedAt: "", evidence: "", ...r })),
      reviews: (d.reviews ?? []).map((r: any) => ({
        requestNote: r.status === "pending" ? (r.comment ?? "") : "",
        responseNote: r.status !== "pending" ? (r.comment ?? "") : "",
        previousReviewId: "", revisionTaskId: "", reviewerLog: [], ...r,
      })),
      submissionRecords: (d.submissionRecords ?? []).map((r: any) => ({
        kind: r.fileGroupId ? "file" : "other",
        items: r.fileGroupId ? [{ fileGroupId: r.fileGroupId, version: r.fileVersion }] : [],
        target: "", confirmationNo: "", confirmationUrl: "", receiptNote: "", description: "",
        recordedByMemberId: r.submittedByMemberId ?? "", recordedAt: r.submittedAt ?? "",
        supersedesId: "", changeType: "first", changeReason: "",
        complete: !!(r.submittedByMemberId && r.submittedAt && r.submissionPath),
        warnings: [], warningReason: "", ...r,
      })),
      // 기존 데이터: 제출물에 연결 정보가 없으면 빈 목록으로 시작 (전체 자료를 자동 연결하지 않음)
      fileLinks: d.fileLinks ?? [],
    })),
    meetings: (raw.meetings ?? []).map((m: any) => ({
      ...m,
      manualActionItems: m.manualActionItems ?? [],
      aiSummary: m.aiSummary ? {
        ...m.aiSummary,
        actionItems: (m.aiSummary.actionItems ?? []).map((a: any) => ({
          id: a.id ?? uid(), taskId: a.taskId ?? "", ...a,
        })),
      } : null,
    })),
    files: (raw.files ?? []).map((f: any) => ({ kind: "file", ...f })),
    readAlertIds: raw.readAlertIds ?? [],
    activityLog: raw.activityLog ?? [],
    status: raw.status ?? "active", archivedAt: raw.archivedAt ?? "",
    assignmentBrief: raw.assignmentBrief ?? "", assignmentUrl: raw.assignmentUrl ?? "",
    retrospective: raw.retrospective ?? "", resolvedAlertIds: raw.resolvedAlertIds ?? [],
  }
}

function loadProjects(): Project[] {
  try {
    // Try v3 first
    const raw3 = localStorage.getItem(STORAGE_KEY)
    if (raw3) {
      const parsed = JSON.parse(raw3) as any[]
      if (Array.isArray(parsed) && parsed.length > 0) {
        const migrated = parsed.map(migrateProject)
        if (!migrated.some(p => p.id === "demo-project")) {
          return [createDemoProject(), ...migrated]
        }
        return migrated
      }
    }
    // Try v2 migration
    const raw2 = localStorage.getItem("blackbox_v2_projects")
    if (raw2) {
      const parsed = JSON.parse(raw2) as any[]
      if (Array.isArray(parsed) && parsed.length > 0) {
        const migrated = parsed.map(migrateProject)
        if (!migrated.some(p => p.id === "demo-project")) {
          return [createDemoProject(), ...migrated]
        }
        return migrated
      }
    }
    return [createDemoProject()]
  } catch { return [createDemoProject()] }
}

function saveProjects(projects: Project[]): boolean {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(projects))
    return true
  } catch { return false }
}

function storeFileData(key: string, dataUrl: string): boolean {
  try {
    const stored = JSON.parse(localStorage.getItem(FILE_DATA_KEY) ?? "{}")
    stored[key] = dataUrl
    localStorage.setItem(FILE_DATA_KEY, JSON.stringify(stored))
    return true
  } catch { return false }
}
function getFileData(key: string): string | null {
  try {
    const s = localStorage.getItem(FILE_DATA_KEY)
    if (!s) return null
    return JSON.parse(s)[key] ?? null
  } catch { return null }
}

// ═══════════════════════════════════════════════════════════════
// UTILITIES
// ═══════════════════════════════════════════════════════════════
function getMyMember(project: Project): Member | undefined {
  return project.members.find(m => m.id === MY_USER_ID)
}
function getMember(project: Project, id: string): Member | undefined {
  return project.members.find(m => m.id === id)
}
function getMemberName(project: Project, id: string): string {
  return getMember(project, id)?.name ?? "알 수 없음"
}
function fmtDate(iso: string) {
  if (!iso) return "미설정"
  try { return new Date(iso).toLocaleDateString("ko-KR", { month: "short", day: "numeric" }) } catch { return iso }
}
function fmtDateTime(iso: string) {
  if (!iso) return ""
  try { return new Date(iso).toLocaleDateString("ko-KR", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" }) } catch { return iso }
}
function localDateStr(dt: Date = new Date()): string {
  const y = dt.getFullYear(), m = String(dt.getMonth() + 1).padStart(2, "0"), d = String(dt.getDate()).padStart(2, "0")
  return `${y}-${m}-${d}`
}
function parseLocalDate(s: string): Date {
  const [y, m, d] = s.slice(0, 10).split("-").map(Number)
  return new Date(y, (m || 1) - 1, d || 1)
}
function daysUntil(d: string): number | null {
  if (!d) return null
  const today = parseLocalDate(localDateStr())
  return Math.round((parseLocalDate(d).getTime() - today.getTime()) / 86400000)
}
function taskAssignees(t: Task): string[] {
  return t.assigneeMemberIds?.length ? t.assigneeMemberIds : (t.assigneeMemberId ? [t.assigneeMemberId] : [])
}
function dueBadge(dateStr: string) {
  const d = daysUntil(dateStr)
  if (d === null) return null
  if (d < 0)  return { label:`마감 ${Math.abs(d)}일 초과`, cls:"bg-red-50 text-red-700 border border-red-200" }
  if (d === 0) return { label:"오늘 마감", cls:"bg-red-50 text-red-700 border border-red-200" }
  if (d <= 3) return { label:`D-${d}`, cls:"bg-orange-50 text-orange-700 border border-orange-200" }
  if (d <= 7) return { label:`D-${d}`, cls:"bg-amber-50 text-amber-700 border border-amber-200" }
  return { label:`D-${d}`, cls:"bg-slate-100 text-slate-600 border border-slate-200" }
}
function statusCfg(s: TaskStatus) {
  if (s === "TODO")        return { label:"할 일",    dot:"bg-slate-400",   text:"text-slate-600",   bg:"bg-slate-50",   border:"border-slate-200" }
  if (s === "IN_PROGRESS") return { label:"진행 중",  dot:"bg-slate-800",    text:"text-slate-900",    bg:"bg-slate-100",    border:"border-slate-300" }
  return                          { label:"업무 완료", dot:"bg-emerald-500", text:"text-emerald-700", bg:"bg-emerald-50", border:"border-emerald-200" }
}
function renderMarkdown(text: string): React.ReactNode[] {
  return text.split("\n").map((line, i) => {
    if (line.startsWith("## ")) return <h3 key={i} className="font-semibold text-slate-900 text-sm mt-4 mb-1 first:mt-0">{line.slice(3)}</h3>
    if (line.startsWith("# "))  return <h2 key={i} className="font-bold text-slate-900 text-base mt-4 mb-2 first:mt-0">{line.slice(2)}</h2>
    if (line.startsWith("- "))  return <li key={i} className="text-sm text-slate-700 ml-4 list-disc leading-relaxed">{line.slice(2)}</li>
    if (!line.trim())           return <br key={i} />
    return <p key={i} className="text-sm text-slate-700 leading-relaxed">{line}</p>
  })
}
function addActivity(project: Project, description: string): Project {
  const entry: ActivityEntry = { id: uid(), description, memberId: MY_USER_ID, createdAt: new Date().toISOString() }
  return { ...project, activityLog: [entry, ...project.activityLog].slice(0, 100) }
}

// ═══════════════════════════════════════════════════════════════
// DYNAMIC ALERTS
// ═══════════════════════════════════════════════════════════════
type AlertType = "inactive"|"unassigned"|"unlinked"|"overdue"
interface ComputedAlert {
  id:string; type:AlertType; severity:"warning"|"info"
  title:string; detail:string; relatedTaskId:string; relatedMemberId:string; isRead:boolean
}

function computeAlerts(project: Project): ComputedAlert[] {
  const alerts: ComputedAlert[] = []
  project.tasks.filter(t => t.status !== "DONE" && t.dueDate && (daysUntil(t.dueDate) ?? 0) < 0).forEach(t => {
    const id = `overdue-${t.id}`
    alerts.push({ id, type:"overdue", severity:"warning", title:"마감 초과 업무",
      detail:`"${t.title}" 업무가 ${Math.abs(daysUntil(t.dueDate)!)}일 초과됐습니다.`,
      relatedTaskId:t.id, relatedMemberId:"", isRead:project.readAlertIds.includes(id) })
  })
  project.tasks.filter(t => taskAssignees(t).length === 0 && t.status !== "DONE").forEach(t => {
    const id = `unassigned-${t.id}`
    alerts.push({ id, type:"unassigned", severity:"warning", title:"담당자 미지정 업무",
      detail:`"${t.title}" 업무에 담당자가 없습니다.`,
      relatedTaskId:t.id, relatedMemberId:"", isRead:project.readAlertIds.includes(id) })
  })
  project.tasks.filter(t => !t.deliverableId && t.status !== "DONE").forEach(t => {
    const id = `unlinked-${t.id}`
    alerts.push({ id, type:"unlinked", severity:"info", title:"제출물 미연결 업무",
      detail:`"${t.title}" 업무가 제출물에 연결되지 않았습니다.`,
      relatedTaskId:t.id, relatedMemberId:"", isRead:project.readAlertIds.includes(id) })
  })
  project.members.forEach(m => {
    const daysSince = Math.floor((Date.now() - new Date(m.lastActiveAt).getTime()) / 86400000)
    if (daysSince >= 14) {
      const id = `inactive-${m.id}`
      alerts.push({ id, type:"inactive", severity:"warning", title:"최근 2주 활동 없음",
        detail:`${m.name}님의 마지막 활동이 ${daysSince}일 전입니다.`,
        relatedTaskId:"", relatedMemberId:m.id, isRead:project.readAlertIds.includes(id) })
    }
  })
  return alerts
}

// ═══════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════
function Btn({ children, onClick, variant = "primary", size = "md", disabled, type = "button", className = "" }: {
  children: React.ReactNode; onClick?: () => void
  variant?: "primary"|"secondary"|"ghost"|"danger"; size?: "sm"|"md"
  disabled?: boolean; type?: "button"|"submit"; className?: string
}) {
  const base = "inline-flex items-center gap-1.5 font-medium rounded-md transition-colors focus-visible:outline-2 focus-visible:outline-slate-400 focus-visible:outline-offset-2 disabled:opacity-50 disabled:cursor-not-allowed shrink-0"
  const sz = size === "sm" ? "h-7 px-2.5 text-xs" : "h-8 px-3 text-[13px]"
  const v = {
    primary:   "bg-slate-900 text-white hover:bg-slate-700 active:bg-slate-950",
    secondary: "bg-white border border-slate-200 text-slate-700 shadow-[0_1px_1px_rgba(15,15,15,0.04)] hover:bg-slate-50 active:bg-slate-100",
    ghost:     "text-slate-600 hover:bg-black/[0.04] active:bg-black/[0.07]",
    danger:    "bg-red-50 border border-red-200 text-red-700 hover:bg-red-100 active:bg-red-200",
  }[variant]
  return (
    <button type={type} onClick={onClick} disabled={disabled} className={`${base} ${sz} ${v} ${className}`}>
      {children}
    </button>
  )
}

function Avatar({ member, size = 7 }: { member?: Member; size?: number }) {
  const sz = `w-${size} h-${size}`
  const bg = member?.color ?? "#64748b"
  return (
    <div className={`${sz} rounded-full flex items-center justify-center text-xs font-bold text-white shrink-0`}
      style={{ background: bg }}>
      {(member?.name ?? "?").slice(0, 1)}
    </div>
  )
}

function StatusBadge({ status }: { status: TaskStatus }) {
  const c = statusCfg(status)
  return (
    <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium border ${c.bg} ${c.text} ${c.border}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${c.dot}`} />{c.label}
    </span>
  )
}

function EmptyState({ icon: Icon, title, desc, action }: {
  icon: any; title: string; desc: string; action?: React.ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center px-4">
      <Icon size={32} strokeWidth={1.5} className="text-slate-300 mb-4" />
      <p className="text-sm font-semibold text-slate-900 mb-1.5">{title}</p>
      <p className="text-xs text-slate-500 mb-5 max-w-xs leading-relaxed">{desc}</p>
      {action}
    </div>
  )
}

function Toast({ message, type = "success", onDismiss }: {
  message: string; type?: "success"|"error"; onDismiss: () => void
}) {
  useEffect(() => {
    const t = setTimeout(onDismiss, type === "error" ? 7000 : 3000)
    return () => clearTimeout(t)
  }, [onDismiss, type])
  return (
    <div className={`fixed bottom-6 left-1/2 -translate-x-1/2 z-[60] text-white text-sm px-5 py-2.5 rounded-xl shadow-xl flex items-center gap-2 animate-fade-in max-w-sm text-center ${type === "error" ? "bg-red-700" : "bg-slate-900"}`}>
      {type === "error"
        ? <AlertCircle size={14} className="text-red-300 shrink-0" />
        : <CheckCircle size={14} className="text-emerald-400 shrink-0" />}
      <span>{message}</span>
    </div>
  )
}

function UnsavedChangesDialog({ onSave, onDiscard, onCancel, saving }: {
  onSave: () => void; onDiscard: () => void; onCancel: () => void; saving?: boolean
}) {
  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
      <div className="relative bg-white rounded-xl shadow-2xl p-6 max-w-sm w-full animate-fade-in">
        <h3 className="text-base font-bold text-slate-900 font-display mb-1">저장하지 않은 변경이 있습니다</h3>
        <p className="text-sm text-slate-500 mb-5 leading-relaxed">이동하면 작성 중인 내용이 사라집니다.</p>
        <div className="flex gap-2 justify-end flex-wrap">
          <Btn variant="ghost" onClick={onCancel}>계속 작성</Btn>
          <Btn variant="secondary" onClick={onDiscard}>변경 버리기</Btn>
          <Btn onClick={onSave} disabled={saving}>
            {saving ? <><Loader2 size={12} className="animate-spin" />저장 중…</> : "저장하고 이동"}
          </Btn>
        </div>
      </div>
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// PROJECT LIST
// ═══════════════════════════════════════════════════════════════
function ProjectList({ projects: allProjects, onOpen, onCreateClick, onJoinClick }: {
  projects: Project[]; onOpen: (id: string) => void; onCreateClick: () => void; onJoinClick: () => void
}) {
  // 내가 팀원으로 속한 프로젝트(와 예시 프로젝트)만 보여 줍니다
  const projects = allProjects.filter(p => p.isDemo || getMyMember(p))
  const myMemberName = (() => {
    const p = projects.find(p => getMyMember(p))
    return p ? (getMyMember(p)?.name ?? "나") : "나"
  })()
  const session = getSession()
  const isProf = session?.role === "PROFESSOR"
  const [tab, setTab] = useState<"projects"|"mytasks"|"class">(isProf ? "class" : "projects")

  // Cross-project my tasks
  const myAllTasks = useMemo(() => {
    const result: { task: Task; project: Project }[] = []
    projects.filter(p => !p.isDemo).forEach(p => {
      p.tasks.filter(t => t.assigneeMemberId === MY_USER_ID && t.status !== "DONE").forEach(t => {
        result.push({ task: t, project: p })
      })
    })
    return result.sort((a, b) => {
      const da = daysUntil(a.task.dueDate) ?? 999
      const db = daysUntil(b.task.dueDate) ?? 999
      return da - db
    })
  }, [projects])

  return (
    <div className="min-h-screen bg-white flex flex-col">
      <header className="h-12 px-5 flex items-center gap-2.5 border-b border-slate-100">
        <span className="w-6 h-6 rounded-[5px] bg-slate-900 flex items-center justify-center shrink-0">
          <Layers size={13} className="text-white" />
        </span>
        <span className="text-[14px] font-semibold text-slate-900">Team Blackbox</span>
        <div className="ml-auto flex items-center gap-1">
          <span className="text-[13px] text-slate-500 mr-1">{session?.name ?? myMemberName}</span>
          <div className="w-6 h-6 rounded-full bg-slate-900 flex items-center justify-center text-[11px] font-bold text-white">
            {(session?.name ?? myMemberName).slice(0, 1)}
          </div>
          <button onClick={logout} className="ml-1 h-7 px-2 rounded-md text-[12px] text-slate-500 hover:bg-black/[0.04] hover:text-slate-800 flex items-center gap-1"><LogOut size={13} />로그아웃</button>
        </div>
      </header>
      <main className="flex-1 px-6 pt-14 pb-10 max-w-[880px] mx-auto w-full">
        <div className="mb-8">
          <p className="text-[13px] text-slate-500 mb-1">{new Date().toLocaleDateString("ko-KR", { month: "long", day: "numeric", weekday: "long" })}</p>
          <h1 className="font-page font-bold text-[34px] leading-tight text-slate-900">{session?.name ?? myMemberName}님, 안녕하세요</h1>
          <p className="text-[15px] text-slate-500 mt-2">진행 중인 프로젝트 {projects.length}개 · 남은 내 업무 {myAllTasks.length}개</p>
        </div>
        <div className="flex items-center justify-between mb-5">
          <div className="flex gap-0 border-b border-slate-200">
            {[...(isProf ? [["class","수업 대시보드"]] : []), ["projects","내 프로젝트"], ...(isProf ? [] : [["mytasks","내 업무"]])].map(([key, label]) => (
              <button key={key} onClick={() => setTab(key as any)}
                className={`px-1 mr-5 py-2 text-[14px] font-medium border-b-2 transition-colors -mb-px ${tab === key ? "border-slate-900 text-slate-900" : "border-transparent text-slate-500 hover:text-slate-800"}`}>
                {label}
                {key === "mytasks" && myAllTasks.length > 0 && (
                  <span className="ml-1.5 text-xs bg-slate-200/70 text-slate-900 px-1.5 py-0.5 rounded-full">{myAllTasks.length}</span>
                )}
              </button>
            ))}
          </div>
          {tab === "projects" && (
            <div className="flex gap-1.5">
              <Btn variant="secondary" onClick={onJoinClick}><UserPlus size={14} />참여하기</Btn>
              <Btn onClick={onCreateClick}><Plus size={15} />새 프로젝트</Btn>
            </div>
          )}
        </div>

        {tab === "class" && <ClassDashboard projects={projects} onOpen={onOpen} />}
        {tab === "projects" && (
          projects.length === 0 ? (
            <EmptyState icon={FolderOpen} title="아직 참여한 프로젝트가 없습니다"
              desc="팀장에게 받은 초대 코드로 참여하거나, 새 프로젝트를 만들어 팀원을 초대하세요."
              action={<div className="flex gap-2"><Btn variant="secondary" onClick={onJoinClick}><UserPlus size={14} />초대 코드로 참여</Btn><Btn onClick={onCreateClick}><Plus size={14} />새 프로젝트 만들기</Btn></div>} />
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {projects.map(p => {
                const me = getMyMember(p)
                const openTasks = p.tasks.filter(t => t.status !== "DONE").length
                const upcomingDels = [...p.deliverables]
                  .filter(d => d.dueDate && d.status !== "SUBMITTED")
                  .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
                const nextDel = upcomingDels[0]
                const due = nextDel ? dueBadge(nextDel.dueDate) : null
                const unreadAlerts = p.isDemo ? 0 : computeAlerts(p).filter(a => !a.isRead).length
                return (
                  <button key={p.id} onClick={() => onOpen(p.id)}
                    className="text-left bg-white rounded-lg border border-slate-200 p-5 hover:bg-slate-50/60 hover:shadow-[0_4px_16px_-6px_rgba(15,15,15,0.12)] transition-all focus-visible:outline-2 focus-visible:outline-slate-400 group">
                    <div className="flex items-start justify-between gap-2 mb-2">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-0.5 flex-wrap">
                          <p className="text-base font-bold text-slate-900 font-display truncate">{p.name}</p>
                          {p.isDemo && (
                            <span className="text-xs bg-violet-100 text-violet-700 px-1.5 py-0.5 rounded font-medium shrink-0">예시</span>
                          )}
                        </div>
                        {(p.subject || p.semester) && (
                          <p className="text-xs text-slate-500">{p.subject}{p.semester ? ` · ${p.semester}` : ""}</p>
                        )}
                      </div>
                      <FolderOpen size={16} className="shrink-0 mt-0.5 text-slate-300 group-hover:text-slate-500 transition-colors" />
                    </div>
                    {p.description && (
                      <p className="text-sm text-slate-500 mb-3 line-clamp-2 leading-relaxed">{p.description}</p>
                    )}
                    <div className="flex items-center gap-3 flex-wrap text-xs text-slate-400">
                      <span className="flex items-center gap-1"><Users size={11} />{p.members.length}명</span>
                      <span className="flex items-center gap-1"><ClipboardList size={11} />진행 중 {openTasks}개</span>
                      {p.deadline && (
                        <span className="flex items-center gap-1"><Calendar size={11} />{p.deadline.slice(0, 7)}</span>
                      )}
                      {me && (
                        <span className="ml-auto font-medium text-slate-800">
                          {me.role === "LEADER" ? "팀장" : me.role === "OBSERVER" ? "열람" : "팀원"}
                        </span>
                      )}
                    </div>
                    {(due || unreadAlerts > 0) && (
                      <div className="mt-3 pt-3 border-t border-slate-100 flex items-center gap-2 flex-wrap">
                        {due && (
                          <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${due.cls}`}>
                            {nextDel?.title} {due.label}
                          </span>
                        )}
                        {unreadAlerts > 0 && (
                          <span className="text-xs text-amber-700 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-full flex items-center gap-1">
                            <AlertTriangle size={10} />확인 필요 {unreadAlerts}건
                          </span>
                        )}
                      </div>
                    )}
                  </button>
                )
              })}
            </div>
          )
        )}

        {tab === "mytasks" && (
          <div>
            {myAllTasks.length === 0 ? (
              <EmptyState icon={ClipboardList} title="담당 업무가 없습니다"
                desc="참여 중인 프로젝트에서 업무를 생성하거나 담당자를 지정받으면 여기에 표시됩니다." />
            ) : (
              <div className="bg-white rounded-xl border border-slate-200 divide-y divide-slate-100">
                {myAllTasks.map(({ task: t, project: p }) => {
                  const due = dueBadge(t.dueDate)
                  const del = p.deliverables.find(d => d.id === t.deliverableId)
                  return (
                    <button key={`${p.id}-${t.id}`} onClick={() => onOpen(p.id)}
                      className="w-full text-left px-4 py-3.5 flex items-center gap-3 hover:bg-slate-50 transition-colors">
                      <StatusBadge status={t.status} />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-slate-900 truncate">{t.title}</p>
                        <div className="flex items-center gap-1.5 mt-0.5 flex-wrap">
                          <span className="text-xs text-slate-800 font-medium">{p.name}</span>
                          {del && <><span className="text-xs text-slate-300">·</span><span className="text-xs text-slate-500 truncate">{del.title}</span></>}
                        </div>
                      </div>
                      {due && <span className={`text-xs px-1.5 py-0.5 rounded font-medium shrink-0 ${due.cls}`}>{due.label}</span>}
                      <ChevronRight size={13} className="text-slate-300 shrink-0" />
                    </button>
                  )
                })}
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// CREATE PROJECT MODAL
// ═══════════════════════════════════════════════════════════════
function JoinProjectModal({ projects, initialCode, onClose, onJoin }: {
  projects: Project[]; initialCode: string; onClose: () => void; onJoin: (projectId: string) => void
}) {
  const [code, setCode] = useState(initialCode)
  const [tried, setTried] = useState(!!initialCode)
  const clean = code.replace(/[^A-Za-z0-9]/g, "").toUpperCase()
  const found = clean.length === 6 ? projects.find(p => !p.isDemo && p.inviteCode === clean) : undefined
  const already = found ? !!getMyMember(found) : false
  const leader = found?.members.find(m => m.role === "LEADER")
  const isProf = getSession()?.role === "PROFESSOR"

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div role="dialog" aria-modal="true" aria-labelledby="join-title" className="relative bg-white rounded-xl shadow-2xl w-full max-w-[420px] animate-fade-in">
        <div className="px-6 pt-6">
          <button onClick={onClose} aria-label="닫기" className="absolute top-3 right-3 p-1.5 rounded-md hover:bg-black/[0.04]"><X size={16} className="text-slate-400" /></button>
          <h2 id="join-title" className="font-page font-bold text-[22px] text-slate-900">프로젝트 참여하기</h2>
          <p className="text-[14px] text-slate-500 mt-1.5 leading-relaxed">팀장에게 받은 6자리 초대 코드를 입력하세요. 팀장은 <b className="font-medium text-slate-700">팀원 → 팀원 초대</b>에서 코드를 확인할 수 있습니다.</p>
        </div>
        <form className="px-6 pt-5 pb-6" onSubmit={e => { e.preventDefault(); setTried(true); if (found) onJoin(found.id) }}>
          <label htmlFor="join-code" className="sr-only">초대 코드</label>
          <input id="join-code" autoFocus value={code} maxLength={7} autoComplete="off" spellCheck={false}
            onChange={e => { setCode(e.target.value.toUpperCase()); setTried(false) }} placeholder="예: K7M2QX"
            className="w-full h-14 rounded-lg border border-slate-200 text-center font-mono-data text-[26px] font-bold tracking-[0.35em] uppercase text-slate-900 placeholder:text-slate-300 placeholder:tracking-[0.2em] placeholder:text-[20px] placeholder:font-medium focus:outline-none focus:border-slate-400 focus:ring-[3px] focus:ring-slate-100" />

          {found ? (
            <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-4 animate-fade-in">
              <div className="flex items-start gap-3">
                <span className="w-9 h-9 rounded-md bg-slate-800 text-white font-semibold flex items-center justify-center shrink-0">{found.name.slice(0, 1)}</span>
                <div className="min-w-0 flex-1">
                  <p className="text-[15px] font-semibold text-slate-900 truncate">{found.name}</p>
                  <p className="text-[12px] text-slate-500 mt-0.5">{[found.subject, found.semester].filter(Boolean).join(" · ") || "과목 정보 없음"}</p>
                </div>
              </div>
              <div className="flex items-center gap-2 mt-3">
                <div className="flex -space-x-1.5">{found.members.slice(0, 5).map(m => <span key={m.id} className="ring-2 ring-slate-50 rounded-full"><Avatar member={m} size={6} /></span>)}</div>
                <span className="text-[12px] text-slate-500">팀원 {found.members.length}명{leader ? ` · 팀장 ${leader.name}` : ""}</span>
              </div>
              {isProf && !already && <p className="text-[12px] text-slate-500 mt-3">교수·조교 계정은 <b className="font-medium">열람 권한</b>으로 참여합니다.</p>}
            </div>
          ) : tried && clean.length > 0 ? (
            <p role="alert" className="mt-3 flex items-start gap-2 text-[13px] text-red-700 bg-red-50 border border-red-100 rounded-md px-3 py-2.5">
              <AlertCircle size={15} className="shrink-0 mt-0.5" />
              {clean.length < 6 ? "초대 코드는 6자리입니다." : "이 코드에 해당하는 프로젝트를 찾지 못했습니다. 코드를 다시 확인해 주세요."}
            </p>
          ) : null}

          <div className="flex gap-2 justify-end mt-5">
            <Btn variant="ghost" onClick={onClose}>취소</Btn>
            {already
              ? <Btn onClick={() => onJoin(found!.id)}>이미 참여 중 · 열기</Btn>
              : <Btn type="submit" disabled={clean.length !== 6}><UserPlus size={14} />참여하기</Btn>}
          </div>
          <p className="text-[11px] text-slate-400 mt-4 leading-relaxed">프로토타입: 같은 브라우저에 저장된 프로젝트에서만 코드를 찾습니다. 다른 기기에서 참여하려면 서버 연동이 필요합니다.</p>
        </form>
      </div>
    </div>
  )
}

function CreateProjectModal({ onClose, onCreate }: {
  onClose: () => void; onCreate: (p: Project) => void
}) {
  const [form, setForm] = useState({ name:"", subject:"", semester:"", description:"", deadline:"" })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")
  const nameRef = useRef<HTMLInputElement>(null)
  useEffect(() => { nameRef.current?.focus() }, [])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.name.trim()) { setError("프로젝트 이름을 입력해 주세요."); return }
    setSaving(true)
    await new Promise(r => setTimeout(r, 300))
    const id = uid()
    const project: Project = migrateProject({
      id, inviteCode: makeInviteCode(id), name: form.name.trim(), subject: form.subject,
      semester: form.semester, description: form.description, deadline: form.deadline,
      isDemo: false, createdAt: new Date().toISOString(),
      members: [{ id: MY_USER_ID, name: getSession()?.name ?? "나", role: "LEADER", color: "#2563eb", email: getSession()?.email ?? "", lastActiveAt: new Date().toISOString() }],
      tasks: [], deliverables: [], meetings: [], files: [], readAlertIds: [], activityLog: [],
    })
    onCreate(project)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30" onClick={onClose} />
      <form onSubmit={handleSubmit} className="relative w-full max-w-md bg-white rounded-xl shadow-xl p-6 space-y-4 animate-fade-in">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold text-slate-900 font-display">새 프로젝트 만들기</h2>
          <button type="button" onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-100" aria-label="닫기">
            <X size={16} className="text-slate-500" />
          </button>
        </div>
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-3 py-2 rounded-lg flex items-center gap-2">
            <AlertCircle size={13} className="shrink-0" />{error}
          </div>
        )}
        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1.5">프로젝트 이름 *</label>
          <input ref={nameRef} value={form.name}
            onChange={e => { setForm(f => ({ ...f, name: e.target.value })); setError("") }}
            placeholder="예: 캡스톤 디자인, 졸업 프로젝트"
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">과목명</label>
            <input value={form.subject} onChange={e => setForm(f => ({ ...f, subject: e.target.value }))}
              placeholder="예: 소프트웨어공학"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">학기</label>
            <input value={form.semester} onChange={e => setForm(f => ({ ...f, semester: e.target.value }))}
              placeholder="예: 2026-1"
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
          </div>
        </div>
        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1.5">설명 <span className="text-slate-400 font-normal">(선택)</span></label>
          <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={2}
            placeholder="어떤 결과물을 만드는 프로젝트인지 적어 주세요."
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-slate-300" />
        </div>
        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1.5">프로젝트 기한 <span className="text-slate-400 font-normal">(선택)</span></label>
          <input type="date" value={form.deadline} onChange={e => setForm(f => ({ ...f, deadline: e.target.value }))}
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
        </div>
        <p className="text-xs text-slate-400">팀원 초대와 제출물 등록은 프로젝트 생성 후 할 수 있습니다.</p>
        <div className="flex justify-end gap-2">
          <Btn variant="secondary" type="button" onClick={onClose}>취소</Btn>
          <Btn type="submit" disabled={saving}>
            {saving ? <><Loader2 size={13} className="animate-spin" />만드는 중…</> : "프로젝트 만들기"}
          </Btn>
        </div>
      </form>
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// SIDEBAR
// ═══════════════════════════════════════════════════════════════
const NAV: { view: ProjectView; icon: any; label: string }[] = [
  { view:"overview",     icon:LayoutDashboard, label:"프로젝트 홈" },
  { view:"deliverables", icon:FileText,        label:"제출물" },
  { view:"tasks",        icon:ClipboardList,   label:"업무" },
  { view:"meetings",     icon:CalendarClock,   label:"일정·회의" },
  { view:"analytics",    icon:BarChart2,       label:"기여도" },
  { view:"files",        icon:FolderOpen,      label:"자료" },
  { view:"team",         icon:Users,           label:"팀원" },
]

function ProjectSidebar({ project, view, setView, alertCount, onBack, onSwitch, projects, mobileOpen = false, onCloseMobile, onCreate }: {
  project: Project; view: ProjectView; setView: (v: ProjectView) => void
  alertCount: number; onBack: () => void; onSwitch: (id: string) => void; projects: Project[]
  mobileOpen?: boolean; onCloseMobile?: () => void; onCreate?: (v: ProjectView) => void
}) {
  const [showNew, setShowNew] = useState(false)
  const [showSwitch, setShowSwitch] = useState(false)
  const session = getSession()
  const item = (active: boolean) =>
    `w-full flex items-center gap-2 h-10 md:h-[30px] px-2 rounded-md text-[15px] md:text-[14px] text-left transition-colors focus-visible:outline-2 focus-visible:outline-slate-400 ${active ? "bg-slate-200 text-slate-900 font-medium" : "text-slate-600 hover:bg-slate-100"}`
  return (
    <>
    {mobileOpen && <div className="md:hidden fixed inset-0 z-40 bg-black/30" onClick={onCloseMobile} aria-hidden />}
    <aside className={`w-[272px] md:w-60 bg-[#F7F7F5] border-r border-slate-200/70 h-[100dvh] fixed left-0 top-0 flex-col z-50 md:z-30 md:flex ${mobileOpen ? "flex animate-slide-in-left" : "hidden"}`}>
      <div className="px-2 pt-2 relative">
        <button onClick={() => setShowSwitch(v => !v)} aria-expanded={showSwitch}
          className="w-full flex items-center gap-2 h-11 px-2 rounded-md hover:bg-black/[0.04] transition-colors text-left">
          <span className="w-6 h-6 rounded-[5px] bg-slate-800 text-white text-[12px] font-semibold flex items-center justify-center shrink-0">{project.name.slice(0, 1)}</span>
          <div className="flex-1 min-w-0">
            <p className="text-[14px] font-semibold text-slate-800 truncate leading-tight">{project.name}</p>
            <p className="text-[11px] text-slate-500 truncate">{project.subject || "Team Blackbox"}</p>
          </div>
          <ChevronsUpDown size={14} className="text-slate-400 shrink-0" />
        </button>
        {showSwitch && (
          <div className="absolute left-2 right-2 top-[52px] z-40 bg-white border border-slate-200 rounded-lg shadow-[0_8px_24px_-6px_rgba(15,15,15,0.18)] p-1 animate-fade-in">
            <p className="px-2 pt-1.5 pb-1 text-[11px] font-medium text-slate-400">{session?.email}</p>
            {projects.map(p => (
              <button key={p.id} onClick={() => { if (p.id !== project.id) onSwitch(p.id); setShowSwitch(false) }}
                className="w-full text-left h-8 px-2 rounded-md text-[13px] text-slate-700 hover:bg-black/[0.04] flex items-center gap-2">
                <span className="w-5 h-5 rounded bg-slate-200 text-slate-600 text-[11px] font-semibold flex items-center justify-center shrink-0">{p.name.slice(0, 1)}</span>
                <span className="truncate flex-1">{p.name}</span>
                {p.id === project.id && <Check size={13} className="text-slate-500" />}
              </button>
            ))}
            <div className="h-px bg-slate-100 my-1" />
            <button onClick={() => { onBack(); setShowSwitch(false) }}
              className="w-full text-left h-8 px-2 rounded-md text-[13px] text-slate-600 hover:bg-black/[0.04] flex items-center gap-2">
              <Home size={13} />모든 프로젝트
            </button>
          </div>
        )}
      </div>
      <div className="px-2 pt-2 relative">
        <button onClick={() => setShowNew(v => !v)} aria-expanded={showNew}
          className="w-full h-8 px-2.5 rounded-md bg-slate-900 text-white text-[13px] font-medium flex items-center gap-1.5 hover:bg-slate-700">
          <Plus size={14} />새로 만들기
        </button>
        {showNew && (
          <div className="absolute left-2 right-2 top-full mt-1 z-50 bg-white rounded-lg border border-slate-200 shadow-lg p-1 animate-fade-in">
            {([["tasks", ClipboardList, "업무"], ["meetings", CalendarClock, "회의"], ["deliverables", FileText, "제출물"], ["files", FolderOpen, "자료 올리기"]] as const).map(([v, I, l]) => (
              <button key={v} onClick={() => { setShowNew(false); onCreate?.(v); onCloseMobile?.() }}
                className="w-full h-8 px-2 rounded-md text-[13px] text-slate-700 hover:bg-black/[0.04] flex items-center gap-2">
                <I size={14} className="text-slate-500" />{l}
              </button>
            ))}
          </div>
        )}
      </div>
      <nav className="flex-1 px-2 pt-3 overflow-y-auto">
        <p className="px-2 pb-1 text-[11px] font-medium text-slate-400">워크스페이스</p>
        <div className="space-y-px">
          {NAV.map(({ view: v, icon: Icon, label }) => {
            const active = view === v
            return (
              <button key={v} onClick={() => { setView(v); onCloseMobile?.() }} aria-current={active ? "page" : undefined} className={item(active)}>
                <Icon size={14} strokeWidth={1.9} className="text-slate-500" />
                {label}
                {v === "team" && alertCount > 0 && (
                  <span className="ml-auto min-w-[18px] h-[18px] px-1 bg-[#DC2626] text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                    {alertCount > 9 ? "9+" : alertCount}
                  </span>
                )}
              </button>
            )
          })}
        </div>
        <div className="mt-3 space-y-px">
          <button onClick={() => { setView("settings"); onCloseMobile?.() }} className={item(view === "settings")}>
            <Settings size={14} strokeWidth={1.9} className="text-slate-500" />설정
          </button>
          <button onClick={onBack} className={item(false)}>
            <Home size={14} strokeWidth={1.9} className="text-slate-500" />모든 프로젝트
          </button>
        </div>
      </nav>
      <div className="p-2 border-t border-slate-200/70">
        <div className="flex items-center gap-2 h-10 px-2 rounded-md">
          <span className="w-6 h-6 rounded-full bg-slate-900 text-white text-[11px] font-bold flex items-center justify-center shrink-0">{(session?.name ?? "나").slice(0, 1)}</span>
          <div className="flex-1 min-w-0 leading-tight">
            <p className="text-[13px] font-medium text-slate-800 truncate">{session?.name ?? "게스트"}</p>
            <p className="text-[11px] text-slate-500">{session?.role === "PROFESSOR" ? "교수 · 조교" : "학생"}</p>
          </div>
          <button onClick={logout} title="로그아웃" aria-label="로그아웃" className="p-1.5 rounded-md text-slate-400 hover:text-slate-700 hover:bg-black/[0.05]"><LogOut size={14} /></button>
        </div>
      </div>
    </aside>
    </>
  )
}

// ═══════════════════════════════════════════════════════════════
// TOP BAR
// ═══════════════════════════════════════════════════════════════
function TopBar({ title, alertCount, onBell, myName, onMenu }: {
  title: string; alertCount: number; onBell: () => void; myName: string; onMenu?: () => void
}) {
  const crumbs = title.split(" · ")
  return (
    <header className="h-12 bg-white/90 backdrop-blur flex items-center px-2 md:px-4 gap-1 md:gap-2 sticky top-0 z-20 border-b border-slate-100">
      <button onClick={onMenu} aria-label="메뉴 열기" className="md:hidden p-2 rounded-md text-slate-600 hover:bg-black/[0.04]"><MenuIcon size={18} /></button>
      <nav aria-label="현재 위치" className="flex-1 min-w-0 flex items-center gap-1 text-[14px]">
        {crumbs.map((c, i) => (
          <span key={i} className={`items-center gap-1 min-w-0 ${i < crumbs.length - 1 ? "hidden md:flex" : "flex"}`}>
            {i > 0 && <span className="text-slate-300 hidden md:inline">/</span>}
            <span className={`truncate px-1.5 py-0.5 rounded ${i === crumbs.length - 1 ? "text-slate-800 font-medium" : "text-slate-500"}`}>{c}</span>
          </span>
        ))}
      </nav>
      <button onClick={onBell}
        className="relative p-1.5 rounded-md hover:bg-black/[0.04] focus-visible:outline-2 focus-visible:outline-slate-400"
        aria-label={`알림${alertCount > 0 ? ` (${alertCount}개 읽지 않음)` : ""}`}>
        <Bell size={17} strokeWidth={1.8} className="text-slate-500" />
        {alertCount > 0 && (
          <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-[#DC2626] ring-2 ring-white" />
        )}
      </button>
      <div className="w-6 h-6 rounded-full bg-slate-900 flex items-center justify-center text-[11px] font-bold text-white" title={myName}>
        {myName.slice(0, 1)}
      </div>
    </header>
  )
}

// ═══════════════════════════════════════════════════════════════
// NOTIFICATION PANEL
// ═══════════════════════════════════════════════════════════════
function NotificationPanel({ project, onClose, onMarkRead, onNavigate }: {
  project: Project; onClose: () => void
  onMarkRead: (id: string) => void; onNavigate: (taskId: string) => void
}) {
  const alerts = computeAlerts(project)
  const unread = alerts.filter(a => !a.isRead).length
  const meta: Record<string, { icon: any; cls: string }> = {
    inactive:   { icon: Clock,         cls: "text-orange-600 bg-orange-50 border-orange-200" },
    unassigned: { icon: UserCheck,     cls: "text-amber-600 bg-amber-50 border-amber-200" },
    unlinked:   { icon: Link2,         cls: "text-slate-800 bg-slate-50 border-slate-200" },
    overdue:    { icon: AlertTriangle, cls: "text-red-600 bg-red-50 border-red-200" },
  }
  const actionLabel: Record<string, string> = {
    inactive:"확인하기", unassigned:"담당자 지정", unlinked:"업무 연결", overdue:"업무 보기",
  }
  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-black/20" onClick={onClose} />
      <div className="relative w-96 bg-white shadow-xl h-full flex flex-col animate-slide-in-right">
        <div className="px-5 py-4 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h2 className="text-sm font-bold text-slate-900 font-display">알림</h2>
            {unread > 0
              ? <p className="text-xs text-slate-500">{unread}개 읽지 않음</p>
              : <p className="text-xs text-slate-400">모두 읽었습니다</p>}
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-100" aria-label="닫기">
            <X size={16} className="text-slate-500" />
          </button>
        </div>
        <div className="px-4 py-2.5 border-b border-slate-100 bg-slate-50">
          <p className="text-xs text-slate-500">알림은 현재 프로젝트 상태에서 자동으로 계산됩니다. 문제를 해결하면 사라집니다.</p>
        </div>
        <div className="flex-1 overflow-y-auto">
          {alerts.length === 0 ? (
            <div className="py-12 text-center text-sm text-slate-400">확인이 필요한 알림이 없습니다</div>
          ) : alerts.map(a => {
            const m = meta[a.type]
            const Icon = m.icon
            const [bgCls, textCls, borderCls] = m.cls.split(" ")
            return (
              <div key={a.id} className={`px-5 py-4 border-b border-slate-100 ${!a.isRead ? "bg-slate-50/60" : ""}`}>
                <div className="flex gap-3">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 border ${bgCls} ${borderCls}`}>
                    <Icon size={14} className={textCls} />
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-0.5">
                      <p className="text-sm font-semibold text-slate-800">{a.title}</p>
                      {!a.isRead && <span className="w-2 h-2 rounded-full bg-slate-800 shrink-0" />}
                    </div>
                    <p className="text-xs text-slate-500 leading-relaxed mb-2">{a.detail}</p>
                    <div className="flex items-center gap-3">
                      {a.relatedTaskId && (
                        <button onClick={() => { onMarkRead(a.id); onNavigate(a.relatedTaskId); onClose() }}
                          className="text-xs font-medium text-slate-800 hover:text-slate-950">
                          {actionLabel[a.type]} →
                        </button>
                      )}
                      {!a.isRead && (
                        <button onClick={() => onMarkRead(a.id)} className="text-xs text-slate-400 hover:text-slate-600">
                          읽음
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// OVERVIEW
// ═══════════════════════════════════════════════════════════════
function timeAgo(iso: string) {
  const m = Math.floor((Date.now() - new Date(iso).getTime()) / 60000)
  if (m < 1) return "방금"
  if (m < 60) return `${m}분 전`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}시간 전`
  const d = Math.floor(h / 24)
  return d < 7 ? `${d}일 전` : fmtDate(iso)
}

function HomeSection({ title, count, action, children }: { title: string; count?: number; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section>
      <div className="flex items-center gap-2 h-8 mb-1 border-b border-slate-200">
        <h3 className="text-[13px] font-semibold text-slate-500">{title}</h3>
        {count !== undefined && <span className="text-[12px] text-slate-400 tabular-nums">{count}</span>}
        <div className="ml-auto">{action}</div>
      </div>
      {children}
    </section>
  )
}

function OverviewView({ project, setView, openTask, openDeliverable, openMeeting, openAlerts, updateProject }: {
  project: Project; setView: (v: ProjectView) => void
  openTask: (id: string) => void; openDeliverable: (id: string) => void; openMeeting: (id: string) => void
  openAlerts: () => void
  updateProject: (upd: (p: Project) => Project) => boolean
}) {
  const me = getMyMember(project)
  const today = localDateStr()
  const [copied, setCopied] = useState(false)
  const [justDone, setJustDone] = useState<string[]>([])

  // ── 내 할 일: 내 업무 + 나에게 온 수정 요청 + 오늘 회의 체크인
  const mine = project.tasks.filter(t => me && taskAssignees(t).includes(me.id) && (t.status !== "DONE" || justDone.includes(t.id)))
    .sort((a, b) => (daysUntil(a.dueDate) ?? 999) - (daysUntil(b.dueDate) ?? 999))
  const requestsToMe = project.tasks.flatMap(t => me && taskAssignees(t).includes(me.id)
    ? (t.comments ?? []).filter(c => c.kind === "request" && !c.resolvedAt).map(c => ({ t, c })) : [])
  const checkins = project.meetings.filter(m => m.date === today && me && !m.checkedInMemberIds.includes(me.id))
  const todoCount = mine.filter(t => t.status !== "DONE").length + requestsToMe.length + checkins.length

  const toggleDone = (t: Task) => {
    const done = t.status !== "DONE"
    setJustDone(ids => done ? [...ids, t.id] : ids.filter(x => x !== t.id))
    updateProject(p => addActivity({ ...p, tasks: p.tasks.map(x => x.id === t.id ? { ...x, status: done ? "DONE" : "IN_PROGRESS", updatedAt: new Date().toISOString() } : x) },
      done ? `업무 "${t.title}"을(를) 완료했어요` : `업무 "${t.title}" 완료 취소`))
  }

  // ── 팀 현황
  const doing = project.tasks.filter(t => t.status !== "DONE")
  const soon = doing.filter(t => { const d = daysUntil(t.dueDate); return d !== null && d <= 3 })
  const openFb = project.tasks.reduce((n, t) => n + openRequests(t), 0)
  const nextMeeting = [...project.meetings].filter(m => m.date >= today).sort((a, b) => (a.date + a.time).localeCompare(b.date + b.time))[0]
  const upcoming = [
    ...project.meetings.filter(m => m.date >= today).map(m => ({ k: "m" as const, id: m.id, title: m.title, date: m.date, sub: m.time })),
    ...project.deliverables.filter(d => d.dueDate >= today && d.status !== "SUBMITTED").map(d => ({ k: "d" as const, id: d.id, title: d.title, date: d.dueDate, sub: "제출 마감" })),
  ].sort((a, b) => a.date.localeCompare(b.date)).slice(0, 4)
  const alerts = computeAlerts(project).filter(a => !a.isRead)

  const total = project.tasks.length
  const doneN = project.tasks.filter(t => t.status === "DONE").length
  const pct = total ? Math.round(doneN / total * 100) : 0
  const dl = project.deadline ? daysUntil(project.deadline) : null
  const finalDel = [...project.deliverables].sort((a, b) => b.dueDate.localeCompare(a.dueDate))[0]
  const isNew = project.tasks.length === 0 && project.deliverables.length === 0

  const dLabel = (d: number | null) => d === null ? "" : d < 0 ? `${-d}일 지남` : d === 0 ? "오늘" : d === 1 ? "내일" : `D-${d}`
  const dCls = (d: number | null) => d === null ? "text-slate-400" : d < 0 ? "text-red-600" : d <= 1 ? "text-amber-700" : "text-slate-500"

  const copyCode = () => {
    navigator.clipboard?.writeText(project.inviteCode).catch(() => {})
    setCopied(true); setTimeout(() => setCopied(false), 1500)
  }

  const stats = [
    { label: "진행 중 업무", n: doing.length, go: () => setView("tasks") },
    { label: "마감 임박 (3일)", n: soon.length, go: () => setView("tasks"), warn: soon.length > 0 },
    { label: "열린 피드백", n: openFb, go: () => setView("tasks"), warn: openFb > 0 },
    { label: "다음 회의", n: nextMeeting ? dLabel(daysUntil(nextMeeting.date)) : "—", go: () => nextMeeting ? openMeeting(nextMeeting.id) : setView("meetings") },
  ]

  return (
    <div className="max-w-[1080px] mx-auto px-5 sm:px-10 pt-10 pb-16 break-keep">
      {/* ── 머리말 ── */}
      <header>
        <div className="w-12 h-12 rounded-[14px] bg-gradient-to-br from-slate-700 to-slate-950 shadow-[inset_0_1px_0_rgba(255,255,255,0.15),0_4px_12px_-4px_rgba(15,15,15,0.35)] flex items-center justify-center" aria-hidden>
          <Layers size={22} strokeWidth={1.75} className="text-white" />
        </div>
        <h1 className="mt-4 text-[28px] sm:text-[32px] font-bold tracking-[-0.015em] text-slate-900 leading-tight">{project.name}</h1>
        <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-[13px] text-slate-500">
          {project.subject && <span>{project.subject}{project.semester ? ` · ${project.semester}` : ""}</span>}
          {project.deadline && <span>최종 마감 <b className={`font-semibold ${dCls(dl)}`}>{dLabel(dl)}</b> <span className="text-slate-400">({project.deadline})</span></span>}
          <button onClick={() => setView("team")} className="flex items-center -space-x-1.5 hover:opacity-80" aria-label="팀원 보기">
            {project.members.slice(0, 5).map(m => <span key={m.id} className="ring-2 ring-white rounded-full"><Avatar member={m} size={6} /></span>)}
            {project.members.length > 5 && <span className="pl-3 text-slate-400">+{project.members.length - 5}</span>}
          </button>
        </div>
        {total > 0 && (
          <div className="mt-5 flex items-center gap-3 max-w-[480px]">
            <div className="flex-1 h-1.5 rounded-full bg-slate-100 overflow-hidden"><div className="h-full bg-slate-800 rounded-full transition-all" style={{ width: `${pct}%` }} /></div>
            <span className="text-[12px] text-slate-500 tabular-nums">{doneN}/{total} 완료 · {pct}%</span>
          </div>
        )}
        {project.description && <p className="mt-4 text-[14px] text-slate-600 leading-relaxed max-w-[640px]">{project.description}</p>}
      </header>

      {isNew && (
        <div className="mt-8 rounded-xl bg-slate-50 border border-slate-200 p-5">
          <p className="text-[15px] font-semibold text-slate-900">팀플을 시작해 보세요</p>
          <p className="text-[14px] text-slate-600 mt-1">제출물을 등록하고, 팀원을 초대한 다음 업무를 나눠 보세요.</p>
          <div className="flex gap-2 flex-wrap mt-4">
            <Btn onClick={() => setView("deliverables")}><FileText size={14} />제출물 등록</Btn>
            <Btn variant="secondary" onClick={() => setView("team")}><UserPlus size={14} />팀원 초대</Btn>
          </div>
        </div>
      )}

      <div className="mt-10 grid lg:grid-cols-[minmax(0,1fr)_280px] gap-x-10 gap-y-10">
        <div className="space-y-10 min-w-0">
          {/* ── 내 할 일 ── */}
          <HomeSection title="내 할 일" count={todoCount}
            action={<button onClick={() => setView("tasks")} className="text-[12px] text-slate-500 hover:text-slate-900">모든 업무 →</button>}>
            {!me ? (
              <p className="py-6 text-[14px] text-slate-400">이 프로젝트의 팀원이 아니라 열람만 할 수 있어요.</p>
            ) : todoCount === 0 && mine.length === 0 ? (
              <div className="py-8 text-center">
                <span className="mx-auto mb-3 w-10 h-10 rounded-full bg-emerald-50 flex items-center justify-center"><CheckCircle size={20} strokeWidth={1.75} className="text-emerald-600" /></span>
                <p className="text-[15px] font-medium text-slate-700">오늘 할 일 끝!</p>
                <p className="text-[13px] text-slate-400 mt-1">맡은 업무가 생기면 여기에 모여요.</p>
              </div>
            ) : (
              <ul>
                {checkins.map(m => (
                  <li key={m.id}>
                    <button onClick={() => openMeeting(m.id)} className="w-full flex items-center gap-3 h-11 px-2 -mx-2 rounded-md hover:bg-slate-50 text-left">
                      <CalendarCheckIcon />
                      <span className="flex-1 truncate text-[14px] text-slate-800"><b className="font-semibold">회의 체크인</b> · {m.title}</span>
                      <span className="text-[12px] text-slate-500 shrink-0">{m.time || "오늘"}</span>
                    </button>
                  </li>
                ))}
                {requestsToMe.map(({ t, c }) => (
                  <li key={c.id}>
                    <button onClick={() => openTask(t.id)} className="w-full flex items-center gap-3 h-11 px-2 -mx-2 rounded-md hover:bg-slate-50 text-left">
                      <span className="w-6 h-6 rounded-md bg-amber-50 flex items-center justify-center shrink-0"><MessageSquare size={13} strokeWidth={2} className="text-amber-600" /></span>
                      <span className="flex-1 min-w-0 truncate text-[14px] text-slate-800">
                        <b className="font-semibold text-amber-800">수정 요청</b> · {t.title}
                        <span className="text-slate-400"> — {getMemberName(project, c.memberId)}: {c.body}</span>
                      </span>
                      <span className="text-[12px] text-slate-400 shrink-0">{timeAgo(c.createdAt)}</span>
                    </button>
                  </li>
                ))}
                {mine.slice(0, 8).map(t => {
                  const d = daysUntil(t.dueDate); const done = t.status === "DONE"
                  return (
                    <li key={t.id} className="group flex items-center gap-3 h-11 px-2 -mx-2 rounded-md hover:bg-slate-50">
                      <button onClick={() => toggleDone(t)} aria-label={done ? "완료 취소" : "완료로 표시"}
                        className={`w-6 h-6 flex items-center justify-center shrink-0`}><span className={`w-[18px] h-[18px] rounded-[5px] border-[1.5px] flex items-center justify-center transition-colors ${done ? "bg-slate-800 border-slate-800" : "border-slate-300 hover:border-slate-500"}`}>
                        {done && <Check size={12} className="text-white" strokeWidth={3} />}</span>
                      </button>
                      <button onClick={() => openTask(t.id)} className={`flex-1 min-w-0 truncate text-left text-[14px] ${done ? "text-slate-400 line-through" : "text-slate-800"}`}>{t.title}</button>
                      {openRequests(t) > 0 && <span className="text-[11px] font-medium text-amber-800 bg-amber-50 px-1.5 py-0.5 rounded shrink-0">수정 {openRequests(t)}</span>}
                      {t.dueDate && !done && <span className={`text-[12px] font-medium shrink-0 tabular-nums ${dCls(d)}`}>{dLabel(d)}</span>}
                    </li>
                  )
                })}
              </ul>
            )}
          </HomeSection>

          {/* ── 팀 현황 ── */}
          <HomeSection title="팀 현황">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-px bg-slate-200 border border-slate-200 rounded-lg overflow-hidden mt-3">
              {stats.map(s => (
                <button key={s.label} onClick={s.go} className="bg-white hover:bg-slate-50 px-4 py-3.5 text-left">
                  <p className="text-[12px] text-slate-500">{s.label}</p>
                  <p className={`mt-1 text-[22px] font-bold tabular-nums ${s.warn ? "text-amber-700" : "text-slate-900"}`}>{s.n}</p>
                </button>
              ))}
            </div>
            {alerts.length > 0 && (
              <button onClick={openAlerts} className="mt-3 w-full flex items-center gap-2 text-left text-[13px] text-slate-600 hover:text-slate-900">
                <AlertCircle size={14} strokeWidth={2} className="text-amber-500 shrink-0" />
                <span className="truncate">{alerts[0].title}{alerts.length > 1 ? ` 외 ${alerts.length - 1}건` : ""}</span>
                <ChevronRight size={13} className="text-slate-400 shrink-0" />
              </button>
            )}
          </HomeSection>

          {/* ── 최근 활동 ── */}
          <HomeSection title="최근 활동" action={<button onClick={() => setView("analytics")} className="text-[12px] text-slate-500 hover:text-slate-900">기여도 →</button>}>
            {project.activityLog.length === 0 ? (
              <p className="py-6 text-[14px] text-slate-400">아직 기록이 없어요. 업무를 완료하거나 파일을 올리면 여기에 쌓여요.</p>
            ) : (
              <ol className="mt-2">
                {project.activityLog.slice(0, 8).map(a => {
                  const m = getMember(project, a.memberId)
                  return (
                    <li key={a.id} className="flex items-start gap-3 py-2">
                      <Avatar member={m} size={6} />
                      <p className="flex-1 min-w-0 text-[14px] text-slate-700 leading-6">
                        <b className="font-semibold text-slate-900">{m?.name ?? "알 수 없음"}</b>님이 {a.description}
                      </p>
                      <span className="text-[12px] text-slate-400 shrink-0 leading-6">{timeAgo(a.createdAt)}</span>
                    </li>
                  )
                })}
              </ol>
            )}
          </HomeSection>
        </div>

        {/* ── 오른쪽 칸 ── */}
        <aside className="space-y-8">
          <HomeSection title="다가오는 일정" action={<button onClick={() => setView("meetings")} className="text-[12px] text-slate-500 hover:text-slate-900">전체 →</button>}>
            {upcoming.length === 0 ? <p className="py-4 text-[13px] text-slate-400">예정된 일정이 없어요.</p> : (
              <ul className="mt-1">
                {upcoming.map(u => {
                  const dt = parseLocalDate(u.date)
                  return (
                    <li key={u.k + u.id}>
                      <button onClick={() => u.k === "m" ? openMeeting(u.id) : openDeliverable(u.id)} className="w-full flex items-center gap-3 py-2 px-2 -mx-2 rounded-md hover:bg-slate-50 text-left">
                        <span className="w-10 text-center shrink-0">
                          <span className="block text-[10px] text-slate-400">{dt.getMonth() + 1}월</span>
                          <span className="block text-[17px] font-bold text-slate-900 leading-none tabular-nums">{dt.getDate()}</span>
                        </span>
                        <span className="flex-1 min-w-0">
                          <span className="block text-[13px] font-medium text-slate-800 truncate">{u.title}</span>
                          <span className={`block text-[12px] ${u.k === "d" ? "text-red-600" : "text-slate-400"}`}>{u.sub || "회의"}</span>
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </HomeSection>

          {finalDel && (
            <HomeSection title="최종 제출물">
              <button onClick={() => openDeliverable(finalDel.id)} className="mt-2 w-full text-left rounded-lg border border-slate-200 p-3.5 hover:bg-slate-50">
                <p className="text-[14px] font-semibold text-slate-900 truncate">{finalDel.title}</p>
                <p className="text-[12px] mt-1.5 flex items-center gap-1.5">
                  {finalDel.status === "SUBMITTED"
                    ? <><CheckCircle size={13} strokeWidth={2} className="text-emerald-600" /><span className="text-emerald-700 font-medium">제출 완료</span></>
                    : finalDel.finalLock
                      ? <><ShieldCheck size={13} strokeWidth={2} className="text-slate-700" /><span className="text-slate-700 font-medium">최종본 확정됨</span></>
                      : <><Clock size={13} strokeWidth={2} className="text-slate-400" /><span className="text-slate-500">준비 중 · {dLabel(daysUntil(finalDel.dueDate))}</span></>}
                </p>
              </button>
            </HomeSection>
          )}

          <HomeSection title="팀원 초대">
            <div className="mt-2 inline-flex items-center gap-1.5">
              <code className="h-8 px-2.5 rounded-md bg-slate-100 inline-flex items-center font-mono-data text-[14px] font-semibold tracking-[0.08em] text-slate-900">{project.inviteCode}</code>
              <button onClick={copyCode} className="h-8 px-2.5 rounded-md text-[12px] text-slate-600 hover:bg-slate-100 inline-flex items-center gap-1 whitespace-nowrap">
                {copied ? <><Check size={13} />복사됨</> : <><Copy size={13} />복사</>}
              </button>
            </div>
          </HomeSection>
        </aside>
      </div>
    </div>
  )
}
function CalendarCheckIcon() { return <span className="w-6 h-6 rounded-md bg-slate-100 flex items-center justify-center shrink-0"><UserCheck size={13} strokeWidth={2} className="text-slate-800" /></span> }

// ═══════════════════════════════════════════════════════════════
// TASK DETAIL PANEL
// ═══════════════════════════════════════════════════════════════
const COMMENT_KIND: Record<CommentKind, { label: string; cls: string; dot: string }> = {
  comment: { label: "의견", cls: "text-slate-600 bg-slate-100", dot: "bg-slate-400" },
  request: { label: "수정 요청", cls: "text-amber-800 bg-amber-50", dot: "bg-amber-500" },
  approve: { label: "확인했어요", cls: "text-emerald-700 bg-emerald-50", dot: "bg-emerald-600" },
}
function openRequests(t: Task) { return (t.comments ?? []).filter(c => c.kind === "request" && !c.resolvedAt).length }

function TaskFeedback({ task, project, onPost, onResolve }: {
  task: Task; project: Project
  onPost: (kind: CommentKind, body: string) => void
  onResolve: (commentId: string) => void
}) {
  const [kind, setKind] = useState<CommentKind>("comment")
  const [body, setBody] = useState("")
  const me = getMyMember(project)
  const list = [...(task.comments ?? [])].sort((a, b) => a.createdAt.localeCompare(b.createdAt))
  const open = openRequests(task)
  const canResolve = (c: TaskComment) => !!me && (me.role === "LEADER" || task.assigneeMemberIds.includes(me.id) || c.memberId === me.id)
  const post = () => {
    const text = body.trim()
    if (!text && kind !== "approve") return
    onPost(kind, text || "확인했어요. 좋아요!")
    setBody(""); setKind("comment")
  }
  return (
    <section aria-label="피드백" className="pt-4 border-t border-slate-100">
      <div className="flex items-center gap-2 mb-3">
        <h3 className="text-sm font-semibold text-slate-900 flex items-center gap-1.5"><MessageSquare size={14} className="text-slate-500" />피드백</h3>
        <span className="text-xs text-slate-500">{list.length}</span>
        {open > 0 && <span className="ml-auto text-[11px] font-medium text-amber-800 bg-amber-50 rounded px-1.5 py-0.5">수정 요청 {open}건 남음</span>}
      </div>

      {list.length === 0 ? (
        <p className="text-xs text-slate-500 mb-3">아직 피드백이 없어요. 결과를 보고 의견이나 수정 요청을 남겨 보세요.</p>
      ) : (
        <ol className="space-y-3 mb-4">
          {list.map(c => {
            const m = getMember(project, c.memberId)
            const k = COMMENT_KIND[c.kind]
            const resolved = c.kind === "request" && c.resolvedAt
            return (
              <li key={c.id} className="flex gap-2.5">
                <Avatar member={m} size={6} />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5 flex-wrap">
                    <span className="text-[13px] font-medium text-slate-900">{m?.name ?? "알 수 없음"}</span>
                    <span className={`text-[11px] font-medium rounded px-1.5 py-px whitespace-nowrap ${resolved ? "text-slate-500 bg-slate-100 line-through" : k.cls}`}>{k.label}</span>
                    <span className="text-[11px] text-slate-400">{fmtDateTime(c.createdAt)}</span>
                  </div>
                  <p className={`text-sm mt-1 leading-relaxed whitespace-pre-wrap break-words ${resolved ? "text-slate-400" : "text-slate-700"}`}>{c.body}</p>
                  {c.kind === "request" && (resolved
                    ? <p className="text-[11px] text-emerald-700 mt-1 flex items-center gap-1"><Check size={11} />{getMember(project, c.resolvedByMemberId ?? "")?.name ?? ""} 반영 완료 · {fmtDateTime(c.resolvedAt!)}</p>
                    : canResolve(c) && <button onClick={() => onResolve(c.id)} className="mt-1.5 h-6 px-2 rounded text-[11px] font-medium text-slate-700 border border-slate-200 hover:bg-slate-50 inline-flex items-center gap-1"><Check size={11} />반영했어요</button>)}
                </div>
              </li>
            )
          })}
        </ol>
      )}

      {me ? (
        <div className="rounded-lg border border-slate-200 focus-within:border-slate-400 focus-within:ring-[3px] focus-within:ring-slate-100 transition-shadow">
          <textarea value={body} onChange={e => setBody(e.target.value)} rows={2}
            onKeyDown={e => { if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) { e.preventDefault(); post() } }}
            placeholder={kind === "request" ? "무엇을 어떻게 고치면 좋을지 구체적으로 적어 주세요" : kind === "approve" ? "한마디 남기기 (선택)" : "의견을 남겨 주세요"}
            aria-label="피드백 내용" className="w-full px-3 pt-2.5 pb-1 text-sm bg-transparent resize-none focus:outline-none placeholder:text-slate-400" />
          <div className="px-2 pb-2 space-y-2">
            <div role="radiogroup" aria-label="피드백 종류" className="grid grid-cols-3 p-[3px] rounded-lg bg-slate-100">
              {(Object.keys(COMMENT_KIND) as CommentKind[]).map(k => (
                <button key={k} type="button" role="radio" onClick={() => setKind(k)} aria-checked={kind === k}
                  className={`h-8 px-1 rounded-md text-[12.5px] leading-none font-medium whitespace-nowrap flex items-center justify-center gap-1.5 transition-all ${kind === k ? "bg-white text-slate-900 shadow-[0_1px_2px_rgba(15,15,15,0.12)]" : "text-slate-500 hover:text-slate-800"}`}>
                  <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${COMMENT_KIND[k].dot}`} />{COMMENT_KIND[k].label}
                </button>
              ))}
            </div>
            <div className="flex justify-end">
            <Btn size="sm" className="shrink-0 whitespace-nowrap" onClick={post} disabled={!body.trim() && kind !== "approve"}><Send size={12} />남기기</Btn>
            </div>
          </div>
        </div>
      ) : (
        <p className="text-xs text-slate-500">프로젝트 팀원만 피드백을 남길 수 있어요.</p>
      )}
      <p className="text-[11px] text-slate-400 mt-1.5">Ctrl/⌘ + Enter로 바로 남기기 · 수정 요청은 담당자나 팀장이 "반영했어요"로 닫을 수 있어요.</p>
    </section>
  )
}

function TaskDetailPanel({ task, project, onClose, onSave, onOpenMeeting, onOpenDeliverable, registerDirty, onComment, onResolveComment }: {
  task: Task; project: Project; onClose: () => void; onSave: (t: Task) => boolean
  onComment?: (kind: CommentKind, body: string) => void; onResolveComment?: (commentId: string) => void
  onOpenMeeting?: (id: string) => void; onOpenDeliverable?: (id: string) => void
  registerDirty?: (dirty: boolean, save: () => Promise<boolean>, discard: () => void) => void
}) {
  const [form, setForm] = useState(task)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [saveError, setSaveError] = useState("")
  const [dirty, setDirty] = useState(false)

  // Reset when task changes
  useEffect(() => {
    setForm(task)
    setDirty(false)
    setSaved(false)
    setSaveError("")
  }, [task.id])

  const update = (patch: Partial<Task>) => {
    setForm(f => ({ ...f, ...patch }))
    setDirty(true)
    setSaved(false)
    setSaveError("")
  }

  const doSave = useCallback(async (): Promise<boolean> => {
    if (!form.title.trim()) { setSaveError("업무 이름을 입력해 주세요."); return false }
    setSaving(true); setSaveError("")
    await new Promise(r => setTimeout(r, 100))
    const ok = onSave({ ...form, updatedAt: new Date().toISOString() })
    setSaving(false)
    if (ok) { setSaved(true); setDirty(false); setTimeout(() => setSaved(false), 2000); return true }
    setSaveError("저장에 실패했습니다. 브라우저 저장 공간을 확인하고 다시 시도해 주세요.")
    return false
  }, [form, onSave])

  const discard = useCallback(() => {
    setForm(task); setDirty(false); setSaveError("")
  }, [task])

  useEffect(() => {
    registerDirty?.(dirty, doSave, discard)
    return () => { registerDirty?.(false, async () => true, () => {}) }
  }, [dirty, doSave, discard, registerDirty])

  const STATUSES: TaskStatus[] = ["TODO","IN_PROGRESS","DONE"]
  const selDel = project.deliverables.find(d => d.id === form.deliverableId)
  const sourceMeeting = form.meetingId ? project.meetings.find(m => m.id === form.meetingId) : null

  return (
    <div className="flex flex-col h-full bg-white overflow-hidden">
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200 shrink-0">
        <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">업무 상세</p>
        <div className="flex items-center gap-2">
          {saved && (
            <span className="text-xs text-emerald-600 flex items-center gap-1"><Check size={11} />저장됨</span>
          )}
          {dirty && (
            <Btn size="sm" onClick={doSave} disabled={saving}>
              {saving ? <><Loader2 size={12} className="animate-spin" />저장 중</> : "저장"}
            </Btn>
          )}
          <button onClick={onClose} className="p-1.5 rounded hover:bg-slate-100" aria-label="닫기">
            <X size={15} className="text-slate-400" />
          </button>
        </div>
      </div>
      <div className="px-4 py-4 space-y-4 flex-1 overflow-y-auto">
        {saveError && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-3 py-2 rounded-lg flex items-start gap-2">
            <AlertCircle size={12} className="mt-0.5 shrink-0" />
            <div className="flex-1">
              {saveError}
              <button onClick={doSave} className="underline ml-1 font-medium">다시 시도</button>
            </div>
          </div>
        )}
        <div>
          <label className="block text-xs font-semibold text-slate-600 mb-1.5">업무 이름</label>
          <input value={form.title} onChange={e => update({ title: e.target.value })}
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
        </div>
        <div>
          <label className="block text-xs font-semibold text-slate-600 mb-1.5">상태</label>
          <div className="flex gap-1.5">
            {STATUSES.map(s => {
              const c = statusCfg(s); const sel = form.status === s
              return (
                <button key={s} onClick={() => update({ status: s })}
                  className={`flex-1 text-xs font-medium py-1.5 rounded-lg border transition-all ${sel ? `${c.bg} ${c.text} ${c.border} ring-2 ring-slate-300 ring-offset-1` : "border-slate-200 text-slate-500 hover:bg-slate-50"}`}>
                  {c.label}
                </button>
              )
            })}
          </div>
        </div>
        <div>
          <label className="block text-xs font-semibold text-slate-600 mb-1.5">설명 <span className="font-normal text-slate-400">(선택)</span></label>
          <textarea value={form.description} onChange={e => update({ description: e.target.value })} rows={2}
            placeholder="업무에 대한 추가 설명이나 맥락"
            className="w-full border border-slate-200 rounded-lg px-2.5 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-slate-300" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1.5">우선순위</label>
            <select value={form.priority} onChange={e => update({ priority: e.target.value as TaskPriority })}
              className="w-full border border-slate-200 rounded-lg px-2.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300">
              <option value="">없음</option>
              <option value="HIGH">높음</option>
              <option value="MEDIUM">보통</option>
              <option value="LOW">낮음</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-600 mb-1.5">마감일</label>
            <input type="date" value={form.dueDate} onChange={e => update({ dueDate: e.target.value })}
              className="w-full border border-slate-200 rounded-lg px-2.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
          </div>
        </div>
        <div>
          <label className="block text-xs font-semibold text-slate-600 mb-1.5">담당자</label>
          <select value={form.assigneeMemberId} onChange={e => update({ assigneeMemberId: e.target.value, assigneeMemberIds: e.target.value ? [e.target.value] : [] })}
            className="w-full border border-slate-200 rounded-lg px-2.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300">
            <option value="">미지정</option>
            {project.members.filter(m => m.role !== "OBSERVER").map(m =>
              <option key={m.id} value={m.id}>{m.name}</option>
            )}
          </select>
        </div>
        <div>
          <label className="block text-xs font-semibold text-slate-600 mb-1.5">연결 제출물</label>
          <select value={form.deliverableId} onChange={e => update({ deliverableId: e.target.value, requirementId: "" })}
            className="w-full border border-slate-200 rounded-lg px-2.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300">
            <option value="">없음</option>
            {project.deliverables.map(d => <option key={d.id} value={d.id}>{d.title}</option>)}
          </select>
          {form.deliverableId && selDel && (
            <select value={form.requirementId} onChange={e => update({ requirementId: e.target.value })}
              className="w-full border border-slate-200 rounded-lg px-2.5 py-2 text-sm mt-1.5 focus:outline-none focus:ring-2 focus:ring-slate-300">
              <option value="">요구사항 연결 안 함</option>
              {selDel.requirements.map(r =>
                <option key={r.id} value={r.id}>{r.required ? "[필수] " : ""}{r.content}</option>
              )}
            </select>
          )}
          {selDel && onOpenDeliverable && (
            <button onClick={() => onOpenDeliverable(selDel.id)}
              className="mt-1 text-xs text-slate-800 hover:underline flex items-center gap-1">
              <ExternalLink size={11} />{selDel.title} 보기
            </button>
          )}
        </div>
        <div>
          <label className="block text-xs font-semibold text-slate-600 mb-1.5">완료 기준</label>
          <textarea value={form.completionCriteria} onChange={e => update({ completionCriteria: e.target.value })} rows={3}
            placeholder="업무가 끝났다고 볼 수 있는 구체적인 조건"
            className="w-full border border-slate-200 rounded-lg px-2.5 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-slate-300" />
        </div>
        {sourceMeeting && (
          <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2.5">
            <p className="text-xs font-semibold text-slate-900 mb-1">회의에서 생성됨</p>
            <button onClick={() => onOpenMeeting?.(sourceMeeting.id)}
              className="text-xs text-slate-900 hover:underline flex items-center gap-1">
              <ExternalLink size={11} />{sourceMeeting.title} · {sourceMeeting.date}
            </button>
          </div>
        )}
        {onComment && onResolveComment && <TaskFeedback task={task} project={project} onPost={onComment} onResolve={onResolveComment} />}
        <div className="text-xs text-slate-400 pt-2 border-t border-slate-100 space-y-0.5">
          <p>마지막 수정: {fmtDateTime(form.updatedAt)}</p>
          <p>생성: {fmtDate(form.createdAt)}</p>
        </div>
      </div>
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// TASKS VIEW
// ═══════════════════════════════════════════════════════════════
function TasksView({ createSignal = 0, project, selectedTaskId, setSelectedTaskId, updateProject, onOpenMeeting, onOpenDeliverable, registerDirty }: {
  project: Project; selectedTaskId: string; setSelectedTaskId: (id: string) => void
  updateProject: (upd: (p: Project) => Project) => boolean
  onOpenMeeting?: (id: string) => void; onOpenDeliverable?: (id: string) => void
  registerDirty?: (dirty: boolean, save: () => Promise<boolean>, discard: () => void) => void
  createSignal?: number
}) {
  const [viewMode, setViewMode] = useState<"list"|"board">("list")
  const [filter, setFilter] = useState("")
  const [statusFilter, setStatusFilter] = useState<TaskStatus|"ALL">("ALL")
  const [memberFilter, setMemberFilter] = useState("")
  const [quickFilter, setQuickFilter] = useState<"all"|"mine"|"week"|"unassigned"|"unlinked">("all")
  const [showAdd, setShowAdd] = useState(false)
  useEffect(() => { if (createSignal) setShowAdd(true) }, [createSignal])
  const [addForm, setAddForm] = useState({ title:"", assigneeMemberId:"", dueDate:"", deliverableId:"", priority:"" as TaskPriority })
  const [addedCount, setAddedCount] = useState(0)
  const [addError, setAddError] = useState("")

  const me = getMyMember(project)
  const todayStr = new Date().toISOString().slice(0, 10)
  const weekLater = new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10)

  const filtered = project.tasks.filter(t => {
    if (statusFilter !== "ALL" && t.status !== statusFilter) return false
    if (memberFilter && t.assigneeMemberId !== memberFilter) return false
    if (quickFilter === "mine" && t.assigneeMemberId !== me?.id) return false
    if (quickFilter === "week" && (t.status === "DONE" || !t.dueDate || t.dueDate > weekLater)) return false
    if (quickFilter === "unassigned" && t.assigneeMemberId) return false
    if (quickFilter === "unlinked" && t.deliverableId) return false
    if (filter) {
      const q = filter.toLowerCase()
      if (!t.title.toLowerCase().includes(q) && !getMemberName(project, t.assigneeMemberId).toLowerCase().includes(q)) return false
    }
    return true
  })
  const selectedTask = project.tasks.find(t => t.id === selectedTaskId) ?? null

  const saveTask = (t: Task): boolean => {
    try {
      updateProject(p => addActivity({
        ...p,
        tasks: p.tasks.some(x => x.id === t.id) ? p.tasks.map(x => x.id === t.id ? { ...t, comments: x.comments } : x) : [...p.tasks, t],
      }, `업무 "${t.title}" 수정`))
      return true
    } catch { return false }
  }

  const postComment = (taskId: string, kind: CommentKind, body: string) => {
    const c: TaskComment = { id: uid(), memberId: MY_USER_ID, kind, body, createdAt: new Date().toISOString() }
    updateProject(p => {
      const t = p.tasks.find(x => x.id === taskId)
      return addActivity({ ...p, tasks: p.tasks.map(x => x.id === taskId ? { ...x, comments: [...(x.comments ?? []), c] } : x) },
        `업무 "${t?.title ?? ""}"에 ${COMMENT_KIND[kind].label}을(를) 남겼어요`)
    })
  }
  const resolveComment = (taskId: string, commentId: string) => {
    updateProject(p => {
      const t = p.tasks.find(x => x.id === taskId)
      return addActivity({ ...p, tasks: p.tasks.map(x => x.id === taskId ? { ...x, comments: (x.comments ?? []).map(c => c.id === commentId ? { ...c, resolvedAt: new Date().toISOString(), resolvedByMemberId: MY_USER_ID } : c) } : x) },
        `업무 "${t?.title ?? ""}" 수정 요청 반영`)
    })
  }

  const handleAdd = () => {
    if (!addForm.title.trim()) { setAddError("업무 이름을 입력해 주세요."); return }
    const task: Task = {
      id: uid(), title: addForm.title.trim(), status: "TODO",
      assigneeMemberId: addForm.assigneeMemberId,
      assigneeMemberIds: addForm.assigneeMemberId ? [addForm.assigneeMemberId] : [],
      priority: addForm.priority, description: "",
      dueDate: addForm.dueDate,
      completionCriteria: "", deliverableId: addForm.deliverableId, requirementId: "",
      meetingId: "", meetingItemId: "", updatedAt: new Date().toISOString(), createdAt: new Date().toISOString(),
    }
    updateProject(p => addActivity({ ...p, tasks: [...p.tasks, task] }, `업무 "${task.title}"을(를) 만들었어요`))
    // 담당자·기한은 유지해서 비슷한 업무를 연달아 추가하기 쉽게
    setAddForm(f => ({ ...f, title: "" }))
    setAddError(""); setAddedCount(n => n + 1)
  }

  const COLS: TaskStatus[] = ["TODO","IN_PROGRESS","DONE"]
  const doneCount = project.tasks.filter(t => t.status === "DONE").length
  const pct = project.tasks.length ? Math.round(doneCount / project.tasks.length * 100) : 0
  const toggleDone = (t: Task) => {
    const done = t.status !== "DONE"
    updateProject(p => {
      const next = { ...p, tasks: p.tasks.map(x => x.id === t.id ? { ...x, status: (done ? "DONE" : "IN_PROGRESS") as TaskStatus, updatedAt: new Date().toISOString() } : x) }
      return done ? addActivity(next, `업무 "${t.title}"을(를) 완료했어요`) : next
    })
  }

  return (
    <div className="flex h-full overflow-hidden">
      <div className={`flex flex-col overflow-hidden ${selectedTask ? "flex-1" : "w-full"}`}>
        <div className="px-6 pt-5 pb-3 bg-white shrink-0">
          <div className="flex items-end gap-3">
            <div className="min-w-0">
              <h1 className="text-[22px] font-semibold tracking-[-0.02em] text-slate-900">업무</h1>
              <p className="text-[13px] text-slate-500 mt-0.5">전체 {project.tasks.length}개 · 완료 {doneCount}개</p>
            </div>
            <div className="ml-auto hidden sm:flex items-center gap-2 pb-1">
              <div className="w-32 h-1.5 rounded-full bg-slate-100 overflow-hidden"><div className="h-full bg-slate-900 rounded-full transition-all" style={{ width: `${pct}%` }} /></div>
              <span className="text-[12px] text-slate-500 font-mono-data">{pct}%</span>
            </div>
          </div>
        </div>
        {/* Toolbar */}
        <div className="px-6 py-2 border-b border-slate-100 bg-white flex items-center gap-2 flex-wrap shrink-0">
          <div className="relative">
            <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input value={filter} onChange={e => setFilter(e.target.value)} placeholder="업무·담당자 검색"
              className="pl-8 pr-7 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-300 w-40" />
            {filter && (
              <button onClick={() => setFilter("")} className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400">
                <X size={12} />
              </button>
            )}
          </div>
          <select value={statusFilter} onChange={e => setStatusFilter(e.target.value as any)}
            className="text-sm border border-slate-200 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white">
            <option value="ALL">전체 상태</option>
            <option value="TODO">할 일</option>
            <option value="IN_PROGRESS">진행 중</option>
            <option value="DONE">업무 완료</option>
          </select>
          <select value={memberFilter} onChange={e => setMemberFilter(e.target.value)}
            className="text-sm border border-slate-200 rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white">
            <option value="">전체 담당자</option>
            {project.members.filter(m => m.role !== "OBSERVER").map(m =>
              <option key={m.id} value={m.id}>{m.name}</option>
            )}
          </select>
          <div className="flex border border-slate-200 rounded-lg overflow-hidden">
            <button onClick={() => setViewMode("list")}
              className={`px-2.5 py-1.5 transition-colors ${viewMode === "list" ? "bg-slate-100 text-slate-900 font-medium" : "text-slate-500 hover:bg-slate-50"}`}
              aria-label="목록 보기"><List size={14} /></button>
            <button onClick={() => setViewMode("board")}
              className={`px-2.5 py-1.5 transition-colors ${viewMode === "board" ? "bg-slate-100 text-slate-900 font-medium" : "text-slate-500 hover:bg-slate-50"}`}
              aria-label="보드 보기"><Columns size={14} /></button>
          </div>
          <Btn size="sm" onClick={() => setShowAdd(v => !v)} className="ml-auto"><Plus size={13} />업무 추가</Btn>
        </div>
        {/* Quick filters */}
        <div className="px-6 py-2 border-b border-slate-100 bg-white flex items-center gap-1.5 shrink-0 overflow-x-auto">
          {([["all","전체"],["mine","내 업무"],["week","이번 주"],["unassigned","담당자 미지정"],["unlinked","제출물 미연결"]] as const).map(([key, label]) => (
            <button key={key} onClick={() => setQuickFilter(key)}
              className={`shrink-0 text-xs px-2.5 py-1 rounded-full font-medium transition-colors ${quickFilter === key ? "bg-slate-800 text-white" : "bg-white border border-slate-200 text-slate-600 hover:border-slate-300"}`}>
              {label}
            </button>
          ))}
          <span className="ml-auto text-xs text-slate-400 shrink-0">{filtered.length}개</span>
        </div>

        {/* Quick add */}
        {showAdd && (() => {
          const d = (n: number) => { const x = new Date(); x.setDate(x.getDate() + n); return x.toISOString().slice(0, 10) }
          const fri = (() => { const x = new Date(); x.setDate(x.getDate() + ((5 - x.getDay() + 7) % 7 || 7)); return x.toISOString().slice(0, 10) })()
          const dueOpts: [string, string][] = [["오늘", d(0)], ["내일", d(1)], ["이번 주 금", fri], ["다음 주", d(7)]]
          const chip = (on: boolean) => `h-7 px-2.5 rounded-md text-xs inline-flex items-center gap-1.5 transition-colors ${on ? "bg-slate-800 text-white" : "text-slate-600 border border-slate-200 bg-white hover:bg-slate-50"}`
          const close = () => { setShowAdd(false); setAddError(""); setAddedCount(0) }
          return (
            <div className="px-4 py-3 border-b border-slate-200 bg-white shrink-0 animate-fade-in"
              onKeyDown={e => { if (e.key === "Escape") close() }}>
              <div className={`rounded-lg border bg-white transition-shadow ${addError ? "border-red-300" : "border-slate-200 focus-within:border-slate-400 focus-within:ring-[3px] focus-within:ring-slate-100"}`}>
                <div className="flex items-center gap-2.5 px-3 h-11">
                  <Circle size={16} className="text-slate-300 shrink-0" />
                  <input value={addForm.title} aria-label="업무 이름"
                    onChange={e => { setAddForm(f => ({ ...f, title: e.target.value })); setAddError("") }}
                    onKeyDown={e => { if (e.key === "Enter" && !e.nativeEvent.isComposing) handleAdd() }}
                    autoFocus placeholder="어떤 업무인가요? 예: 발표 자료 3~5장 디자인"
                    className="flex-1 min-w-0 text-[15px] text-slate-900 placeholder:text-slate-400 bg-transparent focus:outline-none" />
                  <kbd className="hidden sm:inline-flex items-center gap-1 text-[11px] text-slate-400 font-mono-data"><CornerDownLeft size={11} />Enter</kbd>
                </div>
                <div className="flex flex-wrap items-center gap-x-4 gap-y-2 px-3 py-2.5 border-t border-slate-100">
                  <div className="flex items-center gap-1.5" role="group" aria-label="담당자">
                    <span className="text-xs text-slate-500 w-10">담당</span>
                    {project.members.filter(m => m.role !== "OBSERVER").map(m => {
                      const on = addForm.assigneeMemberId === m.id
                      return (
                        <button key={m.id} type="button" title={m.name} aria-pressed={on}
                          onClick={() => setAddForm(f => ({ ...f, assigneeMemberId: on ? "" : m.id }))}
                          className={`rounded-full p-0.5 transition-all ${on ? "ring-2 ring-slate-800" : "opacity-60 hover:opacity-100"}`}>
                          <Avatar member={m} size={6} />
                        </button>
                      )
                    })}
                    {addForm.assigneeMemberId && <span className="text-xs text-slate-700 ml-1">{getMember(project, addForm.assigneeMemberId)?.name}</span>}
                  </div>
                  <div className="flex items-center gap-1" role="group" aria-label="기한">
                    <span className="text-xs text-slate-500 w-10">기한</span>
                    {dueOpts.map(([l, v]) => (
                      <button key={l} type="button" className={chip(addForm.dueDate === v)}
                        onClick={() => setAddForm(f => ({ ...f, dueDate: f.dueDate === v ? "" : v }))}>{l}</button>
                    ))}
                    <input type="date" value={addForm.dueDate} aria-label="기한 직접 선택"
                      onChange={e => setAddForm(f => ({ ...f, dueDate: e.target.value }))}
                      className="h-7 px-2 rounded-md border border-slate-200 text-xs text-slate-600 focus:outline-none focus:border-slate-400" />
                  </div>
                  <div className="flex items-center gap-1" role="group" aria-label="우선순위">
                    <span className="text-xs text-slate-500 w-10">중요도</span>
                    {([["HIGH", "높음", "text-red-600"], ["MEDIUM", "보통", "text-amber-500"], ["LOW", "낮음", "text-slate-400"]] as const).map(([v, l, c]) => (
                      <button key={v} type="button" className={chip(addForm.priority === v)}
                        onClick={() => setAddForm(f => ({ ...f, priority: f.priority === v ? "" : v }))}>
                        <Flag size={11} className={addForm.priority === v ? "text-white" : c} />{l}
                      </button>
                    ))}
                  </div>
                  {project.deliverables.length > 0 && (
                    <select value={addForm.deliverableId} aria-label="연결할 제출물"
                      onChange={e => setAddForm(f => ({ ...f, deliverableId: e.target.value }))}
                      className="h-7 px-2 rounded-md border border-slate-200 text-xs text-slate-600 bg-white focus:outline-none focus:border-slate-400">
                      <option value="">제출물 연결 안 함</option>
                      {project.deliverables.map(dl => <option key={dl.id} value={dl.id}>{dl.title}</option>)}
                    </select>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2 mt-2">
                {addError ? <p role="alert" className="text-xs text-red-600">{addError}</p>
                  : addedCount > 0 ? <p className="text-xs text-emerald-700 flex items-center gap-1"><Check size={12} />{addedCount}개 추가됨 · 계속 입력하면 같은 담당자·기한으로 추가돼요</p>
                  : <p className="text-xs text-slate-500">Enter로 추가 · Esc로 닫기</p>}
                <div className="ml-auto flex gap-1.5">
                  <Btn size="sm" variant="ghost" onClick={close}>{addedCount > 0 ? "완료" : "취소"}</Btn>
                  <Btn size="sm" onClick={handleAdd} disabled={!addForm.title.trim()}><Plus size={12} />추가</Btn>
                </div>
              </div>
            </div>
          )
        })()}

        {/* Content */}
        <div className="flex-1 overflow-auto bg-white">
          {project.tasks.length === 0 ? (
            <EmptyState icon={ClipboardList} title="업무가 없습니다"
              desc="업무를 추가하고 담당자와 기한을 지정하세요."
              action={<Btn onClick={() => setShowAdd(true)}><Plus size={14} />업무 추가</Btn>} />
          ) : filtered.length === 0 ? (
            <div className="text-center py-12 text-sm text-slate-400">
              조건에 맞는 업무가 없습니다.{" "}
              <button onClick={() => { setFilter(""); setStatusFilter("ALL"); setMemberFilter("") }}
                className="text-slate-800 hover:underline">초기화</button>
            </div>
          ) : viewMode === "list" ? (
            <div role="table" aria-label="업무 목록" className="min-w-[640px]">
              <div role="row" className="sticky top-0 z-10 grid grid-cols-[minmax(0,1fr)_112px_120px_96px_150px] items-center h-8 px-6 bg-white border-b border-slate-100 text-[12px] text-slate-400 pl-[52px]">
                <span role="columnheader">업무</span><span role="columnheader">상태</span><span role="columnheader">담당자</span>
                <span role="columnheader">기한</span><span role="columnheader">연결된 결과물</span>
              </div>
              {filtered.map(t => {
                const due = t.status !== "DONE" ? dueBadge(t.dueDate) : null
                const member = getMember(project, t.assigneeMemberId)
                const del = project.deliverables.find(d => d.id === t.deliverableId)
                const sel = selectedTaskId === t.id
                return (
                  <div key={t.id} role="row" tabIndex={0} onClick={() => setSelectedTaskId(sel ? "" : t.id)} aria-selected={sel}
                    onKeyDown={e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); setSelectedTaskId(sel ? "" : t.id) } }}
                    className={`group w-full grid grid-cols-[minmax(0,1fr)_112px_120px_96px_150px] items-center h-11 px-6 border-b border-slate-100 text-left cursor-pointer transition-colors focus:outline-none focus-visible:bg-slate-50 ${sel ? "bg-slate-100" : "hover:bg-slate-50"}`}>
                    <span role="cell" className="flex items-center gap-2.5 min-w-0 pr-3">
                      <button onClick={e => { e.stopPropagation(); toggleDone(t) }} aria-label={t.status === "DONE" ? "완료 취소" : "완료로 표시"}
                        className={`shrink-0 w-[18px] h-[18px] rounded-full border flex items-center justify-center transition-colors ${t.status === "DONE" ? "bg-slate-900 border-slate-900 text-white" : t.status === "IN_PROGRESS" ? "border-slate-500 border-dashed hover:border-slate-900" : "border-slate-300 hover:border-slate-900"}`}>
                        {t.status === "DONE" && <Check size={11} />}
                      </button>
                      <span className={`text-[14px] truncate ${t.status === "DONE" ? "text-slate-400 line-through decoration-slate-300" : "text-slate-900"}`}>{t.title}</span>
                      {openRequests(t) > 0 && <span className="shrink-0 text-[11px] font-medium text-amber-800 bg-amber-50 rounded px-1.5 py-px">수정 요청 {openRequests(t)}</span>}
                      {openRequests(t) === 0 && (t.comments?.length ?? 0) > 0 && <span className="shrink-0 text-[11px] text-slate-400 inline-flex items-center gap-0.5"><MessageSquare size={11} />{t.comments!.length}</span>}
                    </span>
                    <span role="cell"><StatusBadge status={t.status} /></span>
                    <span role="cell" className="flex items-center gap-1.5 min-w-0">
                      {member
                        ? <><Avatar member={member} size={5} /><span className="text-sm text-slate-700 truncate">{member.name}</span></>
                        : <span className="text-xs text-amber-700">미지정</span>}
                    </span>
                    <span role="cell" className="text-xs">
                      {due ? <span className={`px-1.5 py-0.5 rounded font-medium ${due.cls}`}>{due.label}</span>
                        : <span className="text-slate-500 font-mono-data">{t.dueDate ? t.dueDate.slice(5).replace("-", ".") : "—"}</span>}
                    </span>
                    <span role="cell" className="text-xs text-slate-500 truncate">{del?.title ?? "—"}</span>
                  </div>
                )
              })}
            </div>
          ) : (
            <div className="px-6 py-4 grid grid-cols-1 md:grid-cols-3 gap-4 items-start">
              {COLS.map(status => {
                const col = filtered.filter(t => t.status === status)
                const c = statusCfg(status)
                return (
                  <div key={status}>
                    <div className="flex items-center gap-2 px-1 h-8 mb-1">
                      <span className={`w-2 h-2 rounded-full ${c.dot}`} />
                      <span className="text-[13px] font-medium text-slate-800">{c.label}</span>
                      <span className="text-[12px] text-slate-400 font-mono-data">{col.length}</span>
                    </div>
                    <div className="space-y-2">
                      {col.map(t => {
                        const due = t.status !== "DONE" ? dueBadge(t.dueDate) : null
                        const m = getMember(project, t.assigneeMemberId)
                        const sel = selectedTaskId === t.id
                        return (
                          <button key={t.id} onClick={() => setSelectedTaskId(sel ? "" : t.id)}
                            className={`w-full bg-white border rounded-xl p-3 text-left transition-all hover:shadow-[0_2px_8px_rgba(15,23,42,0.06)] ${sel ? "border-slate-400 ring-2 ring-slate-200" : "border-slate-200 hover:border-slate-300"}`}>
                            <p className="text-[13.5px] font-medium text-slate-900 mb-2.5 leading-snug">{t.title}</p>
                            <div className="flex items-center gap-1.5 flex-wrap">
                              {m ? <Avatar member={m} size={5} /> : <span className="text-xs text-amber-600">미지정</span>}
                              {due && <span className={`ml-auto text-xs px-1 py-0.5 rounded font-medium ${due.cls}`}>{due.label}</span>}
                            </div>
                          </button>
                        )
                      })}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>

      {/* Detail panel */}
      {selectedTask && (
        <div className="w-80 shrink-0 border-l border-slate-200 overflow-hidden flex flex-col">
          <TaskDetailPanel
            task={selectedTask} project={project}
            onClose={() => setSelectedTaskId("")}
            onSave={saveTask}
            onOpenMeeting={onOpenMeeting}
            onOpenDeliverable={onOpenDeliverable}
            registerDirty={registerDirty}
            onComment={(k, b) => postComment(selectedTask.id, k, b)}
            onResolveComment={(cid) => resolveComment(selectedTask.id, cid)} />
        </div>
      )}
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// SCHEDULE VIEW
// ═══════════════════════════════════════════════════════════════
function ScheduleView({ project, openTask, openMeeting, openDeliverable }: {
  project: Project
  openTask: (id: string) => void
  openMeeting: (id: string) => void
  openDeliverable: (id: string) => void
}) {
  const today = new Date().toISOString().slice(0, 10)
  type Entry = { date: string; label: string; type:"meeting"|"task"|"deliverable"; id: string; sub?: string }
  const entries: Entry[] = []

  project.meetings.forEach(m => {
    entries.push({ date: m.date, label: m.title, type:"meeting", id: m.id, sub: m.time + (m.location ? ` · ${m.location}` : "") })
  })
  project.tasks.filter(t => t.dueDate && t.status !== "DONE").forEach(t => {
    entries.push({ date: t.dueDate, label: t.title, type:"task", id: t.id,
      sub: getMemberName(project, t.assigneeMemberId) })
  })
  project.deliverables.filter(d => d.dueDate).forEach(d => {
    entries.push({ date: d.dueDate, label: d.title, type:"deliverable", id: d.id,
      sub: d.submissionMethod || "제출 기한" })
  })

  entries.sort((a, b) => a.date.localeCompare(b.date))
  const upcoming = entries.filter(e => e.date >= today)
  const past = entries.filter(e => e.date < today)

  const typeMeta = {
    meeting:     { icon: CalendarClock, bg:"bg-slate-100",    text:"text-slate-800",    label:"회의" },
    task:        { icon: ClipboardList, bg:"bg-slate-50",   text:"text-slate-500",   label:"업무 마감" },
    deliverable: { icon: FileText,      bg:"bg-emerald-50", text:"text-emerald-600", label:"제출 기한" },
  }

  const renderEntry = (e: Entry, i: number) => {
    const m = typeMeta[e.type]
    const Icon = m.icon
    const due = e.type !== "meeting" ? dueBadge(e.date) : null
    const handler = () => {
      if (e.type === "meeting") openMeeting(e.id)
      else if (e.type === "task") openTask(e.id)
      else openDeliverable(e.id)
    }
    return (
      <button key={i} onClick={handler}
        className="w-full flex items-center gap-3 px-4 py-3 border-b border-slate-100 last:border-0 text-left hover:bg-slate-50 transition-colors">
        <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${m.bg}`}>
          <Icon size={14} className={m.text} />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-slate-800 truncate">{e.label}</p>
          <p className="text-xs text-slate-400 mt-0.5">{e.date} · {m.label}{e.sub ? ` · ${e.sub}` : ""}</p>
        </div>
        {due && <span className={`text-xs px-1.5 py-0.5 rounded font-medium shrink-0 ${due.cls}`}>{due.label}</span>}
        <ChevronRight size={13} className="text-slate-300 shrink-0" />
      </button>
    )
  }

  if (entries.length === 0) {
    return (
      <EmptyState icon={CalendarDays} title="일정이 없습니다"
        desc="회의, 업무 마감일, 제출 기한이 등록되면 날짜순으로 표시됩니다." />
    )
  }

  return (
    <div className="space-y-4">
      {upcoming.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
          <div className="px-4 py-2.5 border-b border-slate-100 bg-slate-50">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">예정 일정</p>
          </div>
          {upcoming.map(renderEntry)}
        </div>
      )}
      {past.length > 0 && (
        <div className="bg-white rounded-xl border border-slate-200 overflow-hidden opacity-60">
          <div className="px-4 py-2.5 border-b border-slate-100 bg-slate-50">
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wide">지난 일정</p>
          </div>
          {[...past].reverse().slice(0, 10).map(renderEntry)}
        </div>
      )}
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// MEETINGS VIEW
// ═══════════════════════════════════════════════════════════════
function MeetingsView({ createSignal = 0, project, selectedMeetingId, setSelectedMeetingId, updateProject, openTask, openDeliverable, onAddTask }: {
  project: Project; selectedMeetingId: string; setSelectedMeetingId: (id: string) => void
  updateProject: (upd: (p: Project) => Project) => boolean
  openTask: (id: string) => void; openDeliverable: (id: string) => void; onAddTask: (t: Task) => void
  createSignal?: number
}) {
  const [creating, setCreating] = useState(false)
  useEffect(() => { if (createSignal) setCreating(true) }, [createSignal])
  const [tab, setTab] = useState<"list"|"schedule">("list")

  if (creating) {
    return (
      <MeetingWizard project={project} onCancel={() => setCreating(false)}
        onDone={m => {
          updateProject(p => addActivity({ ...p, meetings: [m, ...p.meetings] }, `회의 "${m.title}"을(를) 잡았어요`))
          setCreating(false)
          setSelectedMeetingId(m.id)
        }} />
    )
  }

  const sel = project.meetings.find(m => m.id === selectedMeetingId)
  if (sel) {
    return (
      <MeetingDetail meeting={sel} project={project}
        onBack={() => setSelectedMeetingId("")}
        onUpdate={m => updateProject(p => addActivity({
          ...p, meetings: p.meetings.map(x => x.id === m.id ? m : x),
        }, `회의록 "${m.title}" 저장`))}
        onAddTask={t => {
          onAddTask(t)
          updateProject(p => addActivity(p, `회의에서 업무 "${t.title}" 생성`))
        }}
        openTask={openTask} />
    )
  }

  return (
    <div className="p-5 space-y-4 max-w-2xl">
      <div className="flex items-center gap-2">
        <div className="flex border border-slate-200 rounded-lg overflow-hidden">
          <button onClick={() => setTab("list")}
            className={`px-3 py-1.5 text-sm font-medium transition-colors ${tab === "list" ? "bg-slate-100 text-slate-900 font-medium" : "text-slate-600 hover:bg-slate-50"}`}>
            회의 목록
          </button>
          <button onClick={() => setTab("schedule")}
            className={`px-3 py-1.5 text-sm font-medium transition-colors ${tab === "schedule" ? "bg-slate-100 text-slate-900 font-medium" : "text-slate-600 hover:bg-slate-50"}`}>
            일정 보기
          </button>
        </div>
        <span className="text-xs text-slate-400 ml-auto">{project.meetings.length}건</span>
        <Btn size="sm" onClick={() => setCreating(true)}><Plus size={13} />새 회의</Btn>
      </div>

      {tab === "schedule" ? (
        <ScheduleView project={project}
          openTask={openTask}
          openMeeting={setSelectedMeetingId}
          openDeliverable={openDeliverable} />
      ) : project.meetings.length === 0 ? (
        <EmptyState icon={CalendarClock} title="회의가 없습니다"
          desc="새 회의를 만들어 일정과 출석 코드를 관리하고 회의록을 작성하세요."
          action={<Btn onClick={() => setCreating(true)}><Plus size={14} />새 회의 만들기</Btn>} />
      ) : (
        <div className="bg-white rounded-xl border border-slate-200 divide-y divide-slate-100">
          {[...project.meetings].sort((a, b) => b.date.localeCompare(a.date)).map(m => (
            <button key={m.id} onClick={() => setSelectedMeetingId(m.id)}
              className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-slate-50 transition-colors text-left">
              <div className="w-9 h-9 rounded-xl bg-slate-100 flex items-center justify-center shrink-0">
                <CalendarClock size={16} className="text-slate-600" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-slate-900 truncate">{m.title}</p>
                <p className="text-xs text-slate-400 mt-0.5">
                  {m.date}{m.time ? ` ${m.time}` : ""}
                  {m.location ? ` · ${m.location}` : ""}
                  {" · "}출석 {m.checkedInMemberIds.length}/{m.attendeeMemberIds.length}명
                </p>
              </div>
              {m.hasMinutes ? (
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 shrink-0">회의록 있음</span>
              ) : (
                <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-orange-50 text-orange-600 border border-orange-200 shrink-0">회의록 없음</span>
              )}
              <ChevronRight size={14} className="text-slate-300 shrink-0" />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// MEETING WIZARD
// ═══════════════════════════════════════════════════════════════
function MeetingWizard({ project, onCancel, onDone }: {
  project: Project; onCancel: () => void; onDone: (m: Meeting) => void
}) {
  const [step, setStep] = useState<1|2|3>(1)
  const [form, setForm] = useState({ title:"", location:"", date:"", time:"", useAI: false })
  const [selCandidate, setSelCandidate] = useState("")
  const [creating, setCreating] = useState(false)
  const [copied, setCopied] = useState(false)

  const nonObservers = project.members.filter(m => m.role !== "OBSERVER")

  // Generate candidates from actual project members and future dates
  const candidates = useMemo(() => {
    const base = new Date()
    const days = ["일","월","화","수","목","금","토"]
    // Find next 3 weekdays starting 7 days from now
    const result = []
    let d = new Date(base)
    d.setDate(d.getDate() + 7)
    const times = ["10:00","14:00","16:30"]
    while (result.length < 3) {
      const dw = d.getDay()
      if (dw !== 0 && dw !== 6) { // skip weekends
        const dateStr = d.toISOString().slice(0, 10)
        result.push({
          id: `c${result.length + 1}`,
          date: dateStr,
          day: days[dw],
          time: times[result.length],
          avail: nonObservers.map(m => ({ memberId: m.id, status: "unknown" as const })),
        })
      }
      d.setDate(d.getDate() + 1)
    }
    return result
  }, [project.id, project.members.length])

  const candidate = candidates.find(c => c.id === selCandidate)
  const chosenDate = form.useAI ? (candidate?.date ?? "") : form.date
  const chosenTime = form.useAI ? (candidate?.time ?? "") : form.time

  const handleCreate = async () => {
    if (!form.title.trim()) return
    setCreating(true)
    await new Promise(r => setTimeout(r, 400))
    const code = `MT${Math.floor(1000 + Math.random() * 9000)}`
    const m: Meeting = {
      id: uid(), title: form.title.trim(), date: chosenDate, time: chosenTime,
      location: form.location,
      attendeeMemberIds: nonObservers.map(m => m.id),
      checkinCode: code, checkedInMemberIds: [],
      hasMinutes: false, minutesContent: "", aiSummary: null, manualActionItems: [],
      createdAt: new Date().toISOString(),
    }
    setCreating(false)
    onDone(m)
  }

  const steps = ["기본 정보","일정 선택","확인"]

  return (
    <div className="p-5 max-w-xl">
      <div className="flex items-center gap-2 mb-5">
        <button onClick={onCancel} className="p-1.5 rounded hover:bg-slate-100 text-slate-500">
          <ArrowLeft size={15} />
        </button>
        <div className="flex items-center gap-1.5 flex-1">
          {steps.map((s, i) => (
            <div key={i} className="flex items-center gap-1.5">
              <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${step > i + 1 ? "bg-emerald-500 text-white" : step === i + 1 ? "bg-slate-100 text-slate-900 font-medium" : "bg-slate-200 text-slate-500"}`}>
                {step > i + 1 ? <Check size={12} /> : i + 1}
              </div>
              <span className={`text-xs ${step === i + 1 ? "text-slate-900 font-semibold" : "text-slate-400"}`}>{s}</span>
              {i < 2 && <ChevronRight size={12} className="text-slate-300" />}
            </div>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-4">
        {step === 1 && (
          <>
            <h2 className="text-base font-bold text-slate-900 font-display">회의 기본 정보</h2>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1.5">회의 제목 *</label>
              <input autoFocus value={form.title}
                onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                placeholder="예: 중간발표 최종 점검"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1.5 flex items-center gap-1.5">
                <MapPin size={11} className="text-slate-400" />장소
              </label>
              <input value={form.location}
                onChange={e => setForm(f => ({ ...f, location: e.target.value }))}
                placeholder="예: 도서관 3층 스터디룸"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
            </div>
            <div>
              <p className="text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
                참석자: {nonObservers.length > 0 ? nonObservers.map(m => m.name).join(", ") : "팀원이 없습니다"}
              </p>
            </div>
            <div className="flex justify-end">
              <Btn onClick={() => setStep(2)} disabled={!form.title.trim()}>
                다음 <ChevronRight size={13} />
              </Btn>
            </div>
          </>
        )}

        {step === 2 && (
          <>
            <div className="flex items-center justify-between">
              <h2 className="text-base font-bold text-slate-900 font-display">일정 선택</h2>
              {nonObservers.length > 0 && (
                <div className="flex gap-1.5">
                  <button onClick={() => setForm(f => ({ ...f, useAI: false }))}
                    className={`text-xs px-2.5 py-1.5 rounded-lg font-medium ${!form.useAI ? "bg-slate-100 text-slate-900 font-medium" : "bg-slate-100 text-slate-600 hover:bg-slate-200"}`}>
                    직접 입력
                  </button>
                  <button onClick={() => setForm(f => ({ ...f, useAI: true }))}
                    className={`text-xs px-2.5 py-1.5 rounded-lg font-medium ${form.useAI ? "bg-slate-100 text-slate-900 font-medium" : "bg-slate-100 text-slate-600 hover:bg-slate-200"}`}>
                    <Sparkles size={11} className="inline mr-1" />후보 제안
                  </button>
                </div>
              )}
            </div>

            {form.useAI ? (
              <div className="space-y-2">
                <div className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 flex items-start gap-2">
                  <Info size={12} className="text-amber-500 mt-0.5 shrink-0" />
                  아래 후보는 프로젝트 마감일 기준 7일 후부터의 평일입니다. 팀원 실제 일정은 직접 확인하세요.
                </div>
                {candidates.map(c => {
                  const sel = selCandidate === c.id
                  return (
                    <div key={c.id} onClick={() => setSelCandidate(c.id)}
                      className={`rounded-xl border-2 p-3.5 cursor-pointer transition-all ${sel ? "border-slate-400 bg-slate-100" : "border-slate-200 hover:border-slate-300"}`}>
                      <div className="flex items-center gap-2 mb-2">
                        <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center shrink-0 ${sel ? "border-slate-900 bg-slate-900" : "border-slate-300"}`}>
                          {sel && <Check size={11} className="text-white" />}
                        </div>
                        <span className="font-bold text-sm text-slate-900">{c.date} ({c.day}) {c.time}</span>
                        <span className="ml-auto text-xs text-slate-400 bg-slate-100 px-2 py-0.5 rounded">일정 미확인</span>
                      </div>
                      <div className="ml-7 space-y-1">
                        {c.avail.map(a => {
                          const member = getMember(project, a.memberId)
                          if (!member) return null
                          return (
                            <div key={a.memberId} className="flex items-center gap-2 text-xs">
                              <Avatar member={member} size={4} />
                              <span className="text-slate-700">{member.name}</span>
                              <span className="text-slate-400">일정 정보 없음</span>
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  )
                })}
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1.5">날짜</label>
                  <input type="date" value={form.date}
                    onChange={e => setForm(f => ({ ...f, date: e.target.value }))}
                    min={new Date().toISOString().slice(0, 10)}
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1.5">시작 시간</label>
                  <input type="time" value={form.time}
                    onChange={e => setForm(f => ({ ...f, time: e.target.value }))}
                    className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
                </div>
              </div>
            )}

            <div className="flex justify-between pt-1">
              <Btn variant="secondary" onClick={() => setStep(1)}><ChevronLeft size={13} />이전</Btn>
              <Btn onClick={() => setStep(3)}
                disabled={form.useAI ? !selCandidate : (!form.date || !form.time)}>
                다음 <ChevronRight size={13} />
              </Btn>
            </div>
          </>
        )}

        {step === 3 && (
          <>
            <h2 className="text-base font-bold text-slate-900 font-display">확인 및 생성</h2>
            <div className="bg-slate-50 rounded-xl p-4 space-y-2.5">
              {[
                ["제목", form.title],
                ["날짜·시간", chosenDate && chosenTime ? `${chosenDate} ${chosenTime}` : "미설정"],
                ["장소", form.location || "미설정"],
                ["참석 대상", nonObservers.map(m => m.name).join(", ") || "없음"],
              ].map(([k, v]) => (
                <div key={k} className="flex gap-3 items-start">
                  <span className="text-slate-400 w-20 shrink-0 text-xs pt-0.5">{k}</span>
                  <span className="font-medium text-slate-900 text-sm">{v}</span>
                </div>
              ))}
            </div>
            <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2.5 text-xs text-slate-500 flex items-start gap-2">
              <Info size={12} className="text-slate-400 mt-0.5 shrink-0" />
              생성 후 출석 코드가 발급됩니다. 팀원에게 공유해 출석 체크에 사용하세요.
            </div>
            <div className="flex justify-between">
              <Btn variant="secondary" onClick={() => setStep(2)}><ChevronLeft size={13} />이전</Btn>
              <Btn onClick={handleCreate} disabled={creating || !chosenDate || !chosenTime}>
                {creating ? <><Loader2 size={13} className="animate-spin" />생성 중…</> : "회의 생성"}
              </Btn>
            </div>
          </>
        )}
      </div>
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// MEETING DETAIL
// ═══════════════════════════════════════════════════════════════
function MeetingDetail({ meeting, project, onBack, onUpdate, onAddTask, openTask }: {
  meeting: Meeting; project: Project; onBack: () => void
  onUpdate: (m: Meeting) => void; onAddTask: (t: Task) => void; openTask: (id: string) => void
}) {
  const [editingMinutes, setEditingMinutes] = useState(false)
  const [draft, setDraft] = useState(meeting.minutesContent)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState("")
  const [saved, setSaved] = useState(false)
  const [showAI, setShowAI] = useState(false)
  const [checkinInput, setCheckinInput] = useState("")
  const [checkinError, setCheckinError] = useState("")
  const [checkinExpanded, setCheckinExpanded] = useState(
    meeting.checkedInMemberIds.length < meeting.attendeeMemberIds.length
  )
  const [copied, setCopied] = useState(false)
  const [newItemTask, setNewItemTask] = useState("")
  const [newItemAssignee, setNewItemAssignee] = useState("")
  const [newItemDue, setNewItemDue] = useState("")
  const [convertingId, setConvertingId] = useState<string|null>(null)

  useEffect(() => {
    setDraft(meeting.minutesContent)
    setSaved(false)
    setSaveError("")
    setEditingMinutes(false)
  }, [meeting.id])

  const handleSaveMinutes = async () => {
    setSaving(true); setSaveError("")
    await new Promise(r => setTimeout(r, 300))
    try {
      onUpdate({ ...meeting, minutesContent: draft, hasMinutes: !!draft.trim() })
      setSaving(false); setSaved(true); setEditingMinutes(false)
      setTimeout(() => setSaved(false), 3000)
    } catch {
      setSaving(false)
      setSaveError("저장에 실패했습니다. 브라우저 저장 공간을 확인해 주세요.")
    }
  }

  const handleCheckin = () => {
    const code = checkinInput.trim().toUpperCase()
    if (code !== meeting.checkinCode) { setCheckinError("코드가 맞지 않아요. 회의 진행자에게 다시 확인해 주세요."); return }
    setCheckinError("")
    const me = getMyMember(project)
    if (me && !meeting.checkedInMemberIds.includes(me.id)) {
      onUpdate({ ...meeting, checkedInMemberIds: [...meeting.checkedInMemberIds, me.id] })
    }
    setCheckinInput(""); setCheckinExpanded(false)
  }

  const convertAIItem = (item: MeetingActionItem) => {
    if (item.taskId) { openTask(item.taskId); return }
    const t: Task = {
      id: uid(), title: item.task, status: "TODO", assigneeMemberId: item.assigneeMemberId,
      assigneeMemberIds: item.assigneeMemberId ? [item.assigneeMemberId] : [],
      priority: "" as TaskPriority, description: "",
      dueDate: item.dueDate, completionCriteria: "", deliverableId: "", requirementId: "",
      meetingId: meeting.id, meetingItemId: item.id,
      updatedAt: new Date().toISOString(), createdAt: new Date().toISOString(),
    }
    onAddTask(t)
    onUpdate({
      ...meeting,
      aiSummary: meeting.aiSummary ? {
        ...meeting.aiSummary,
        actionItems: meeting.aiSummary.actionItems.map(a => a.id === item.id ? { ...a, taskId: t.id } : a),
      } : null,
    })
  }

  const addManualItem = () => {
    if (!newItemTask.trim()) return
    const item: MeetingActionItem = {
      id: uid(), task: newItemTask.trim(), assigneeMemberId: newItemAssignee,
      dueDate: newItemDue, taskId: "",
    }
    onUpdate({ ...meeting, manualActionItems: [...meeting.manualActionItems, item] })
    setNewItemTask(""); setNewItemAssignee(""); setNewItemDue("")
  }

  const convertManualItem = (item: MeetingActionItem) => {
    if (item.taskId) { openTask(item.taskId); return }
    setConvertingId(item.id)
    const t: Task = {
      id: uid(), title: item.task, status: "TODO", assigneeMemberId: item.assigneeMemberId,
      assigneeMemberIds: item.assigneeMemberId ? [item.assigneeMemberId] : [],
      priority: "" as TaskPriority, description: "",
      dueDate: item.dueDate, completionCriteria: "", deliverableId: "", requirementId: "",
      meetingId: meeting.id, meetingItemId: item.id,
      updatedAt: new Date().toISOString(), createdAt: new Date().toISOString(),
    }
    onAddTask(t)
    onUpdate({
      ...meeting,
      manualActionItems: meeting.manualActionItems.map(a => a.id === item.id ? { ...a, taskId: t.id } : a),
    })
    setTimeout(() => setConvertingId(null), 500)
  }

  const nonObservers = project.members.filter(m => m.role !== "OBSERVER")

  return (
    <div className="p-5 max-w-2xl space-y-4">
      <button onClick={onBack} className="flex items-center gap-1.5 text-sm text-slate-400 hover:text-slate-900 mb-1">
        <ChevronLeft size={14} />회의 목록으로
      </button>

      {/* Header */}
      <div className="bg-white rounded-xl border border-slate-200 p-5">
        <div className="flex items-start justify-between gap-3 mb-3">
          <h2 className="text-xl font-bold text-slate-900 font-display">{meeting.title}</h2>
          {saved && <span className="text-xs text-emerald-600 flex items-center gap-1 shrink-0"><Check size={11} />저장됨</span>}
        </div>
        <div className="flex flex-wrap gap-3 text-xs text-slate-500">
          <span className="flex items-center gap-1"><Calendar size={11} />{meeting.date}{meeting.time ? ` ${meeting.time}` : ""}</span>
          <span className="flex items-center gap-1"><MapPin size={11} />{meeting.location || "장소 미정"}</span>
          <span className="flex items-center gap-1"><Users size={11} />출석 {meeting.checkedInMemberIds.length}/{meeting.attendeeMemberIds.length}명</span>
        </div>
      </div>

      {/* Check-in */}
      <div className="bg-white rounded-xl border border-slate-200">
        <button onClick={() => setCheckinExpanded(v => !v)}
          className="w-full flex items-center justify-between px-5 py-4 text-left">
          <div className="flex items-center gap-2">
            <UserCheck size={14} className="text-slate-600" />
            <span className="text-sm font-semibold text-slate-900">출석 체크인</span>
            <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-slate-100 text-slate-900 border border-slate-300">
              {meeting.checkedInMemberIds.length}/{meeting.attendeeMemberIds.length}명
            </span>
          </div>
          {checkinExpanded ? <ChevronUp size={14} className="text-slate-400" /> : <ChevronDown size={14} className="text-slate-400" />}
        </button>
        {checkinExpanded && (
          <div className="px-5 pb-5 border-t border-slate-100 pt-4 space-y-4">
            <div className="flex items-center justify-between bg-slate-100 rounded-xl px-4 py-3">
              <div>
                <p className="text-xs text-slate-600 font-semibold uppercase tracking-wide">출석 코드</p>
                <p className="text-2xl font-bold text-slate-900 font-display tracking-widest mt-0.5">{meeting.checkinCode}</p>
              </div>
              <button onClick={() => { navigator.clipboard.writeText(meeting.checkinCode); setCopied(true); setTimeout(() => setCopied(false), 2000) }}
                className="flex items-center gap-1.5 text-sm text-slate-900 bg-white border border-slate-300 px-3 py-1.5 rounded-lg hover:bg-slate-100 font-medium">
                {copied ? <><Check size={12} className="text-emerald-600" />복사됨</> : <><Copy size={12} />복사</>}
              </button>
            </div>
            <div className="space-y-1.5">
              {meeting.attendeeMemberIds.map(id => {
                const m = getMember(project, id)
                if (!m) return null
                const checked = meeting.checkedInMemberIds.includes(id)
                return (
                  <div key={id} className={`flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm ${checked ? "bg-emerald-50" : "bg-slate-50"}`}>
                    <Avatar member={m} size={5} />
                    <span className={checked ? "text-emerald-800 font-medium flex-1" : "text-slate-600 flex-1"}>{m.name}</span>
                    {checked ? <CheckCircle size={14} className="text-emerald-500" /> : <Circle size={14} className="text-slate-300" />}
                  </div>
                )
              })}
            </div>
            <div className="flex gap-2">
              <input value={checkinInput}
                onChange={e => { setCheckinInput(e.target.value.toUpperCase()); setCheckinError("") }}
                onKeyDown={e => e.key === "Enter" && handleCheckin()}
                placeholder="회의 코드 6자리" aria-invalid={!!checkinError}
                className={`flex-1 min-w-0 border ${checkinError ? "border-red-500" : "border-slate-200"} rounded-lg px-3 py-2 text-sm font-mono uppercase focus:outline-none focus:ring-2 focus:ring-slate-300`} />
              <Btn onClick={handleCheckin}>체크인</Btn>
            </div>
            {checkinError && <p role="alert" className="text-[12px] text-red-600 mt-1.5 flex items-center gap-1"><AlertCircle size={12} />{checkinError}</p>}
          </div>
        )}
      </div>

      {/* Minutes */}
      <div className="bg-white rounded-xl border border-slate-200 p-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold text-slate-900 tracking-[-0.01em] mb-1 flex items-center gap-1.5">
            <MessageSquare size={13} className="text-slate-400" />회의록
          </h3>
          {!editingMinutes ? (
            <Btn size="sm" onClick={() => setEditingMinutes(true)}>
              <Pencil size={12} />{meeting.hasMinutes ? "수정" : "작성 시작"}
            </Btn>
          ) : (
            <div className="flex gap-1.5">
              <Btn size="sm" variant="secondary"
                onClick={() => { setEditingMinutes(false); setDraft(meeting.minutesContent); setSaveError("") }}>
                취소
              </Btn>
              <Btn size="sm" onClick={handleSaveMinutes} disabled={saving}>
                {saving ? <><Loader2 size={12} className="animate-spin" />저장 중…</> : "저장"}
              </Btn>
            </div>
          )}
        </div>
        {saveError && (
          <div className="mb-3 bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700 flex items-start gap-2">
            <AlertCircle size={13} className="text-red-500 mt-0.5 shrink-0" />
            <div>{saveError} <button onClick={handleSaveMinutes} className="underline font-medium">다시 시도</button></div>
          </div>
        )}
        {editingMinutes ? (
          <textarea value={draft} onChange={e => setDraft(e.target.value)} rows={12}
            placeholder={"## 안건 제목\n\n내용을 작성하세요.\n\n- 항목"}
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm font-mono resize-y focus:outline-none focus:ring-2 focus:ring-slate-300" />
        ) : meeting.hasMinutes ? (
          <div className="space-y-0.5">{renderMarkdown(meeting.minutesContent)}</div>
        ) : (
          <p className="text-sm text-slate-400 italic text-center py-8">
            아직 회의록이 없습니다. '작성 시작'을 눌러 내용을 기록하세요.
          </p>
        )}
      </div>

      {/* Manual action items */}
      <div className="bg-white rounded-xl border border-slate-200 p-5">
        <h3 className="text-sm font-bold text-slate-900 font-display mb-1">결정사항 · 후속 업무</h3>
        <p className="text-xs text-slate-400 mb-4">회의에서 결정한 사항을 기록하고 업무로 전환하세요. AI 없이 직접 입력합니다.</p>
        {meeting.manualActionItems.length > 0 && (
          <div className="divide-y divide-slate-100 mb-4">
            {meeting.manualActionItems.map(item => {
              const m = getMember(project, item.assigneeMemberId)
              const isConverting = convertingId === item.id
              return (
                <div key={item.id} className="py-3 flex items-start gap-3">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-slate-800">{item.task}</p>
                    {(m || item.dueDate) && (
                      <p className="text-xs text-slate-400 mt-0.5">
                        {m?.name ?? ""}{m && item.dueDate ? " · " : ""}{item.dueDate || ""}
                      </p>
                    )}
                  </div>
                  {item.taskId ? (
                    <button onClick={() => openTask(item.taskId)}
                      className="text-xs font-medium text-emerald-700 bg-emerald-50 border border-emerald-200 px-2.5 py-1.5 rounded-lg flex items-center gap-1 shrink-0 hover:bg-emerald-100">
                      <Check size={11} />업무 보기
                    </button>
                  ) : (
                    <button onClick={() => convertManualItem(item)} disabled={isConverting}
                      className="text-xs font-medium text-slate-700 bg-slate-50 border border-slate-200 px-2.5 py-1.5 rounded-lg hover:bg-slate-100 shrink-0">
                      {isConverting ? <Loader2 size={12} className="animate-spin" /> : "업무로 전환"}
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        )}
        <div className="border border-slate-200 rounded-lg p-3 space-y-2">
          <input value={newItemTask} onChange={e => setNewItemTask(e.target.value)}
            onKeyDown={e => e.key === "Enter" && addManualItem()}
            placeholder="결정사항 또는 할 일 입력"
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
          <div className="flex gap-2 flex-wrap">
            <select value={newItemAssignee} onChange={e => setNewItemAssignee(e.target.value)}
              className="flex-1 border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white">
              <option value="">담당자 선택</option>
              {nonObservers.map(m => <option key={m.id} value={m.id}>{m.name}</option>)}
            </select>
            <input type="date" value={newItemDue} onChange={e => setNewItemDue(e.target.value)}
              min={new Date().toISOString().slice(0, 10)}
              className="border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white" />
          </div>
          <Btn size="sm" onClick={addManualItem} disabled={!newItemTask.trim()}>
            <Plus size={13} />추가
          </Btn>
        </div>
      </div>

      {/* AI Summary */}
      {meeting.aiSummary && (
        <div className="bg-white rounded-xl border border-slate-200 p-5">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <Sparkles size={14} className="text-violet-500" />
              <h3 className="text-sm font-semibold text-slate-900 tracking-[-0.01em] mb-1">AI 요약 초안</h3>
              <span className="text-xs bg-violet-100 text-violet-700 px-1.5 py-0.5 rounded font-medium">검토 필요</span>
            </div>
            <button onClick={() => setShowAI(v => !v)} className="text-xs text-slate-400 hover:text-slate-900">
              {showAI ? "접기" : "펼치기"}
            </button>
          </div>
          {showAI ? (
            <div className="space-y-4 animate-fade-in">
              <div className="text-xs text-violet-700 bg-violet-50 border border-violet-200 rounded-lg px-3 py-2">
                원문 회의록과 대조하여 검토한 뒤 업무로 전환하세요. AI 제안은 자동으로 확정되지 않습니다.
              </div>
              {meeting.aiSummary.keyDecisions.length > 0 && (
                <div>
                  <p className="text-xs font-semibold text-slate-600 mb-2">결정 사항</p>
                  <ul className="space-y-1">
                    {meeting.aiSummary.keyDecisions.map((d, i) => (
                      <li key={i} className="text-sm text-slate-700 flex items-start gap-2">
                        <CheckCircle size={12} className="text-emerald-500 mt-0.5 shrink-0" />{d}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <div>
                <p className="text-xs font-semibold text-slate-600 mb-2">후속 업무 제안</p>
                <div className="space-y-2">
                  {meeting.aiSummary.actionItems.map(item => {
                    const m = getMember(project, item.assigneeMemberId)
                    return (
                      <div key={item.id} className="border border-slate-200 rounded-lg px-3 py-2.5 flex items-start gap-3">
                        <div className="flex-1">
                          <p className="text-sm font-medium text-slate-800">{item.task}</p>
                          <p className="text-xs text-slate-400 mt-0.5">
                            담당 제안: {m?.name ?? "미지정"} · {item.dueDate || "기한 없음"}
                          </p>
                        </div>
                        {item.taskId ? (
                          <button onClick={() => openTask(item.taskId)}
                            className="shrink-0 text-xs px-2.5 py-1.5 rounded-lg font-medium bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1 hover:bg-emerald-100">
                            <Check size={11} />업무 보기
                          </button>
                        ) : (
                          <button onClick={() => convertAIItem(item)}
                            className="shrink-0 text-xs px-2.5 py-1.5 rounded-lg font-medium bg-slate-100 text-slate-900 border border-slate-300 hover:bg-slate-200/70">
                            업무로 추가
                          </button>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          ) : (
            <button onClick={() => setShowAI(true)} className="text-sm text-slate-800 hover:underline">
              결정 사항 {meeting.aiSummary.keyDecisions.length}개 · 후속 업무 {meeting.aiSummary.actionItems.length}개 보기 →
            </button>
          )}
        </div>
      )}
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// FILES VIEW
// ═══════════════════════════════════════════════════════════════
function FilesView({ createSignal = 0, project, updateProject }: {
  project: Project; updateProject: (upd: (p: Project) => Project) => boolean
  createSignal?: number
}) {
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})
  const [showUpload, setShowUpload] = useState(false)
  useEffect(() => { if (createSignal) setShowUpload(true) }, [createSignal])
  const [selectedFile, setSelectedFile] = useState<File|null>(null)
  const [uploadNote, setUploadNote] = useState("")
  const [versionChoice, setVersionChoice] = useState<"new"|"version">("new")
  const [existingForVersion, setExistingForVersion] = useState<FileGroup|null>(null)
  const [uploading, setUploading] = useState(false)
  const [toast, setToast] = useState<{ msg:string; type:"success"|"error" }|null>(null)
  const [search, setSearch] = useState("")
  const fileInputRef = useRef<HTMLInputElement>(null)
  const me = getMyMember(project)
  const myId = me?.id ?? ""

  const filtered = project.files.filter(f =>
    !search || f.name.toLowerCase().includes(search.toLowerCase())
  )

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) { setSelectedFile(null); return }
    setSelectedFile(file)
    const existing = project.files.find(f => f.name === file.name)
    setExistingForVersion(existing ?? null)
    setVersionChoice(existing ? "version" : "new")
  }

  const resetUpload = () => {
    setShowUpload(false); setSelectedFile(null); setUploadNote("")
    setExistingForVersion(null); setVersionChoice("new")
    if (fileInputRef.current) fileInputRef.current.value = ""
  }

  const handleUpload = () => {
    if (!selectedFile) return
    setUploading(true)
    const reader = new FileReader()
    reader.onload = ev => {
      const dataUrl = ev.target?.result as string
      const fileDataKey = `file_${uid()}`
      const stored = storeFileData(fileDataKey, dataUrl)
      if (!stored) {
        setUploading(false)
        setToast({ msg:"브라우저 저장 공간 부족으로 파일을 저장하지 못했습니다. 저장 공간을 확보한 뒤 다시 시도해 주세요.", type:"error" })
        return
      }
      const bytes = selectedFile.size
      const sizeStr = bytes >= 1024 * 1024
        ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
        : `${Math.max(1, Math.round(bytes / 1024))} KB`

      const newVersion: FileVersion = {
        version: 1, uploadedByMemberId: myId, uploadedAt: new Date().toISOString(),
        size: sizeStr, changeNote: uploadNote || (versionChoice === "new" ? "초안 업로드" : "새 버전 업로드"),
        changeType: versionChoice === "new" ? "new" : "modified", fileDataKey,
      }

      updateProject(p => {
        let updated: Project
        if (versionChoice === "version" && existingForVersion) {
          updated = {
            ...p, files: p.files.map(f => f.id === existingForVersion.id
              ? { ...f, versions: [{ ...newVersion, version: f.versions[0].version + 1 }, ...f.versions] }
              : f),
          }
        } else {
          updated = { ...p, files: [{ id:uid(), name:selectedFile.name, category:"기타", versions:[newVersion] }, ...p.files] }
        }
        return addActivity(updated, `자료 "${selectedFile.name}" ${versionChoice === "version" ? "새 버전" : ""} 등록`)
      })
      setUploading(false)
      setToast({ msg:`"${selectedFile.name}"을 등록했습니다. 이 기기에서 다운로드할 수 있습니다.`, type:"success" })
      resetUpload()
    }
    reader.onerror = () => {
      setUploading(false)
      setToast({ msg:"파일을 읽을 수 없습니다. 다시 시도해 주세요.", type:"error" })
    }
    reader.readAsDataURL(selectedFile)
  }

  const handleDownload = (fg: FileGroup, v: FileVersion) => {
    if (!v.fileDataKey) {
      setToast({ msg:"이 파일은 예시 데이터입니다. 파일을 직접 등록하면 다운로드할 수 있습니다.", type:"error" })
      return
    }
    const dataUrl = getFileData(v.fileDataKey)
    if (!dataUrl) {
      setToast({ msg:"파일 데이터를 찾을 수 없습니다. 브라우저 데이터가 삭제됐을 수 있습니다.", type:"error" })
      return
    }
    const a = document.createElement("a"); a.href = dataUrl; a.download = fg.name; a.click()
  }

  return (
    <div className="p-5 space-y-4">
      {toast && <Toast message={toast.msg} type={toast.type} onDismiss={() => setToast(null)} />}
      <div className="flex items-center gap-2 flex-wrap">
        <div className="relative">
          <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="파일 검색"
            className="pl-8 pr-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-slate-300 w-44" />
        </div>
        <span className="text-xs text-slate-400 ml-auto">{project.files.length}개</span>
        <Btn size="sm" onClick={() => setShowUpload(v => !v)}><Upload size={13} />파일 등록</Btn>
      </div>

      {showUpload && (
        <div className="bg-white rounded-xl border border-slate-200 p-4 space-y-3">
          <p className="text-sm font-semibold text-slate-800">파일 등록</p>
          <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2.5 text-xs text-slate-600 flex items-start gap-2">
            <Info size={12} className="text-slate-400 mt-0.5 shrink-0" />
            선택한 파일은 이 브라우저에 저장되며 이 기기에서만 다운로드할 수 있습니다.
            브라우저 데이터를 지우면 삭제될 수 있습니다. 큰 파일은 저장 공간 제한에 걸릴 수 있습니다.
          </div>
          <input ref={fileInputRef} type="file" onChange={handleFileChange}
            className="block w-full text-sm text-slate-500 file:mr-3 file:py-1.5 file:px-3 file:rounded-lg file:border file:border-slate-200 file:text-xs file:font-medium file:bg-white file:text-slate-700 hover:file:bg-slate-50 cursor-pointer" />
          {selectedFile && (
            <div className="space-y-2">
              <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-700 flex items-center gap-2">
                <FileText size={12} className="text-slate-400 shrink-0" />
                <span className="font-medium">{selectedFile.name}</span>
                <span className="text-slate-400 ml-auto">
                  {selectedFile.size >= 1024 * 1024
                    ? `${(selectedFile.size / 1024 / 1024).toFixed(1)} MB`
                    : `${Math.round(selectedFile.size / 1024)} KB`}
                </span>
              </div>
              {existingForVersion && (
                <div className="space-y-1.5">
                  <p className="text-xs font-semibold text-slate-700">같은 이름의 파일이 있습니다</p>
                  <div className="flex gap-2">
                    <button onClick={() => setVersionChoice("version")}
                      className={`flex-1 text-xs py-2 rounded-lg border transition-all ${versionChoice === "version" ? "bg-slate-100 border-slate-300 text-slate-900 font-medium" : "border-slate-200 text-slate-600 hover:bg-slate-50"}`}>
                      기존 파일의 새 버전
                    </button>
                    <button onClick={() => setVersionChoice("new")}
                      className={`flex-1 text-xs py-2 rounded-lg border transition-all ${versionChoice === "new" ? "bg-slate-100 border-slate-300 text-slate-900 font-medium" : "border-slate-200 text-slate-600 hover:bg-slate-50"}`}>
                      새 자료로 등록
                    </button>
                  </div>
                </div>
              )}
              <input value={uploadNote} onChange={e => setUploadNote(e.target.value)}
                placeholder="변경 내용 메모 (선택)"
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
            </div>
          )}
          <div className="flex gap-2 justify-end">
            <Btn size="sm" variant="secondary" onClick={resetUpload}>취소</Btn>
            <Btn size="sm" onClick={handleUpload} disabled={!selectedFile || uploading}>
              {uploading ? <><Loader2 size={12} className="animate-spin" />등록 중…</> : <><Upload size={13} />등록</>}
            </Btn>
          </div>
        </div>
      )}

      {project.files.length === 0 ? (
        <EmptyState icon={FolderOpen} title="등록된 자료가 없습니다"
          desc="발표자료, 보고서 등을 등록하면 버전 이력을 관리할 수 있습니다."
          action={<Btn onClick={() => setShowUpload(true)}><Upload size={14} />첫 파일 등록</Btn>} />
      ) : filtered.length === 0 ? (
        <div className="text-center py-10 text-sm text-slate-400">검색 결과가 없습니다.</div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
          <div className="grid grid-cols-[1fr_80px_100px_110px_64px] gap-2 px-4 py-2.5 text-xs font-semibold text-slate-500 border-b border-slate-100 bg-slate-50">
            <span>파일명</span><span>최신 버전</span><span>등록자</span><span>등록 일시</span><span></span>
          </div>
          {filtered.map(f => {
            const latest = f.versions[0]
            const uploader = getMember(project, latest.uploadedByMemberId)
            const exp = !!expanded[f.id]
            const canDownload = !!latest.fileDataKey
            return (
              <div key={f.id} className="border-b border-slate-100 last:border-0">
                <div className="grid grid-cols-[1fr_80px_100px_110px_64px] gap-2 px-4 py-3 items-center hover:bg-slate-50 cursor-pointer"
                  onClick={() => setExpanded(e => ({ ...e, [f.id]: !e[f.id] }))}>
                  <div className="flex items-center gap-2 min-w-0">
                    <FileText size={14} className="text-slate-400 shrink-0" />
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-slate-900 truncate">{f.name}</p>
                      <p className="text-xs text-slate-400">{f.category}</p>
                    </div>
                  </div>
                  <span className="text-sm text-slate-900 font-bold">v{latest.version}</span>
                  <span className="text-xs text-slate-600 truncate">{uploader?.name ?? "알 수 없음"}</span>
                  <span className="text-xs text-slate-400">{fmtDate(latest.uploadedAt)}</span>
                  <div className="flex items-center gap-0.5">
                    <button onClick={e => { e.stopPropagation(); handleDownload(f, latest) }}
                      className={`p-1.5 rounded hover:bg-slate-200 transition-colors ${canDownload ? "text-slate-800" : "text-slate-300"}`}
                      aria-label="최신 버전 다운로드"
                      title={canDownload ? "다운로드" : "데모 파일 (다운로드 불가)"}>
                      <Download size={13} />
                    </button>
                    {exp ? <ChevronUp size={13} className="text-slate-400" /> : <ChevronDown size={13} className="text-slate-400" />}
                  </div>
                </div>
                {exp && (
                  <div className="bg-slate-50 border-t border-slate-100 animate-fade-in">
                    {f.versions.map(v => {
                      const vm = getMember(project, v.uploadedByMemberId)
                      const isLatest = v.version === f.versions[0].version
                      const ctCls = v.changeType === "new" ? "bg-slate-100 text-slate-900" : "bg-amber-50 text-amber-700"
                      const ctLabel = v.changeType === "new" ? "신규" : "수정됨"
                      return (
                        <div key={v.version} className="px-6 py-3 flex items-center gap-3 border-b border-slate-100 last:border-0">
                          <div className="w-14 flex items-center gap-1 shrink-0">
                            {isLatest && <span className="text-[10px] bg-slate-900 text-white px-1 py-0.5 rounded font-bold">최신</span>}
                            <span className="text-xs font-bold text-slate-500">v{v.version}</span>
                          </div>
                          <div className="flex-1 min-w-0">
                            <p className="text-xs text-slate-700">{v.changeNote}</p>
                            <div className="flex items-center gap-2 mt-0.5">
                              <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${ctCls}`}>{ctLabel}</span>
                              <span className="text-xs text-slate-400">{vm?.name ?? "알 수 없음"} · {v.size} · {fmtDateTime(v.uploadedAt)}</span>
                            </div>
                          </div>
                          <button onClick={() => handleDownload(f, v)}
                            disabled={!v.fileDataKey}
                            className={`text-xs flex items-center gap-1 shrink-0 px-2 py-1 rounded hover:bg-white transition-colors ${v.fileDataKey ? "text-slate-800" : "text-slate-300 cursor-default"}`}
                            title={!v.fileDataKey ? "데모 파일은 다운로드할 수 없습니다" : undefined}>
                            <Download size={11} />다운로드
                          </button>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
      <p className="text-xs text-slate-400 flex items-center gap-1.5">
        <Info size={11} />파일은 브라우저 로컬 저장소에 보관됩니다. 서버에는 업로드되지 않습니다.
      </p>
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// CREATE TASK FROM REQUIREMENT
// ═══════════════════════════════════════════════════════════════
function CreateTaskFromReqModal({ project, deliverable, requirement, onClose, onCreate }: {
  project: Project; deliverable: Deliverable; requirement: Requirement
  onClose: () => void; onCreate: (t: Task) => void
}) {
  const [form, setForm] = useState({
    title: requirement.content,
    assigneeMemberId: deliverable.ownerMemberId ?? "",
    dueDate: deliverable.dueDate,
    completionCriteria: "",
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")
  const titleRef = useRef<HTMLInputElement>(null)
  useEffect(() => { titleRef.current?.focus(); titleRef.current?.select() }, [])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.title.trim()) { setError("업무 이름을 입력해 주세요."); return }
    setSaving(true)
    await new Promise(r => setTimeout(r, 200))
    const t: Task = {
      id: uid(), title: form.title.trim(), status: "TODO",
      assigneeMemberId: form.assigneeMemberId,
      assigneeMemberIds: form.assigneeMemberId ? [form.assigneeMemberId] : [],
      priority: "" as TaskPriority, description: "",
      dueDate: form.dueDate,
      completionCriteria: form.completionCriteria,
      deliverableId: deliverable.id, requirementId: requirement.id,
      meetingId: "", meetingItemId: "",
      updatedAt: new Date().toISOString(), createdAt: new Date().toISOString(),
    }
    onCreate(t)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/30" onClick={onClose} />
      <form onSubmit={handleCreate} className="relative w-full max-w-md bg-white rounded-xl shadow-xl p-6 space-y-4 animate-fade-in">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-bold text-slate-900 font-display">요구사항에서 업무 만들기</h2>
          <button type="button" onClick={onClose} className="p-1.5 rounded hover:bg-slate-100">
            <X size={16} className="text-slate-500" />
          </button>
        </div>
        <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-xs text-slate-700">
          <span className="font-semibold">{deliverable.title}</span>
          {" · "}
          <span className={requirement.required ? "text-slate-900 font-semibold" : ""}>{requirement.required ? "[필수] " : ""}{requirement.content}</span>
        </div>
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-xs px-3 py-2 rounded-lg flex items-center gap-1.5">
            <AlertCircle size={11} className="shrink-0" />{error}
          </div>
        )}
        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1.5">업무 이름 *</label>
          <input ref={titleRef} value={form.title}
            onChange={e => { setForm(f => ({ ...f, title: e.target.value })); setError("") }}
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">담당자</label>
            <select value={form.assigneeMemberId} onChange={e => setForm(f => ({ ...f, assigneeMemberId: e.target.value }))}
              className="w-full border border-slate-200 rounded-lg px-2.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300">
              <option value="">미지정</option>
              {project.members.filter(m => m.role !== "OBSERVER").map(m =>
                <option key={m.id} value={m.id}>{m.name}</option>
              )}
            </select>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">마감일</label>
            <input type="date" value={form.dueDate}
              onChange={e => setForm(f => ({ ...f, dueDate: e.target.value }))}
              className="w-full border border-slate-200 rounded-lg px-2.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
          </div>
        </div>
        <div>
          <label className="block text-xs font-semibold text-slate-700 mb-1.5">완료 기준 <span className="text-slate-400 font-normal">(선택)</span></label>
          <textarea value={form.completionCriteria} onChange={e => setForm(f => ({ ...f, completionCriteria: e.target.value }))} rows={2}
            placeholder="업무가 끝났다고 볼 수 있는 구체적인 조건"
            className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-slate-300" />
        </div>
        <div className="flex justify-end gap-2">
          <Btn variant="secondary" type="button" onClick={onClose}>취소</Btn>
          <Btn type="submit" disabled={saving || !form.title.trim()}>
            {saving ? <><Loader2 size={12} className="animate-spin" />생성 중…</> : "업무 만들기"}
          </Btn>
        </div>
      </form>
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// DELIVERABLES VIEW
// ═══════════════════════════════════════════════════════════════
type DelTab = "overview"|"tasks"|"files"|"review"

function DeliverablesView({ createSignal = 0, project, selectedId, setSelectedId, updateProject, openTask }: {
  project: Project; selectedId: string; setSelectedId: (id: string) => void
  updateProject: (upd: (p: Project) => Project) => boolean; openTask: (id: string) => void
  createSignal?: number
}) {
  const [showAdd, setShowAdd] = useState(false)
  useEffect(() => { if (createSignal) setShowAdd(true) }, [createSignal])
  const [addForm, setAddForm] = useState({ title:"", dueDate:"", ownerMemberId:"", submissionMethod:"", description:"" })
  const [addError, setAddError] = useState("")
  const [savingAdd, setSavingAdd] = useState(false)
  const [editMode, setEditMode] = useState(false)
  const [editForm, setEditForm] = useState<Deliverable|null>(null)
  const [newReq, setNewReq] = useState("")
  const [reqRequired, setReqRequired] = useState(true)
  const [editReq, setEditReq] = useState<{ id:string; content:string; required:boolean }|null>(null)
  const [linkTaskId, setLinkTaskId] = useState("")
  const [toast, setToast] = useState<{ msg:string; type:"success"|"error" }|null>(null)
  const [createTaskReq, setCreateTaskReq] = useState<Requirement|null>(null)
  const [delTab, setDelTab] = useState<DelTab>("overview")
  // Review form
  const [reviewForm, setReviewForm] = useState({ fileGroupId:"", fileVersion:0, reviewerMemberId:"", comment:"" })
  const [showReviewForm, setShowReviewForm] = useState(false)
  // Submission form
  const [showSubForm, setShowSubForm] = useState(false)
  const [subForm, setSubForm] = useState({ fileGroupId:"", fileVersion:0, submissionPath:"", note:"", confirmedBy:"" })

  const showToast = (msg: string, type: "success"|"error" = "success") => setToast({ msg, type })
  const me = getMyMember(project)
  const isObserver = me?.role === "OBSERVER"

  const selectedRaw = project.deliverables.find(d => d.id === selectedId)
  const selected = selectedRaw ? {
    ...selectedRaw,
    requirements: selectedRaw.requirements ?? [],
    reviews: selectedRaw.reviews ?? [],
    submissionRecords: selectedRaw.submissionRecords ?? [],
  } : undefined
  const linkedTasks = project.tasks.filter(t => t.deliverableId === selectedId)
  const unlinkedTasks = project.tasks.filter(t => !t.deliverableId)

  useEffect(() => {
    if (selected && editMode) setEditForm({ ...selected })
  }, [selected?.id])

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!addForm.title.trim()) { setAddError("제출물 이름을 입력해 주세요."); return }
    if (!addForm.dueDate) { setAddError("제출 기한은 필수입니다."); return }
    setSavingAdd(true)
    await new Promise(r => setTimeout(r, 200))
    const d: Deliverable = {
      id: uid(), title: addForm.title.trim(), dueDate: addForm.dueDate,
      ownerMemberId: addForm.ownerMemberId, submissionMethod: addForm.submissionMethod,
      description: addForm.description, requirements: [], status: "PREPARING",
      reviews: [], submissionRecords: [],
    }
    updateProject(p => addActivity({ ...p, deliverables: [...p.deliverables, d] }, `제출물 "${d.title}"을(를) 올렸어요`))
    setSelectedId(d.id)
    setSavingAdd(false); setShowAdd(false)
    setAddForm({ title:"", dueDate:"", ownerMemberId:"", submissionMethod:"", description:"" }); setAddError("")
    showToast("제출물을 추가했습니다.")
  }

  const handleSaveEdit = () => {
    if (!editForm) return
    if (!editForm.title.trim()) { showToast("제출물 이름은 필수입니다.", "error"); return }
    if (!editForm.dueDate) { showToast("제출 기한은 필수입니다.", "error"); return }
    updateProject(p => addActivity({
      ...p, deliverables: p.deliverables.map(d => d.id === editForm.id ? editForm : d),
    }, `제출물 "${editForm.title}" 수정`))
    setEditMode(false); showToast("저장했습니다.")
  }

  const addReq = () => {
    if (!selected || !newReq.trim()) return
    const r: Requirement = { id: uid(), content: newReq.trim(), required: reqRequired }
    updateProject(p => ({
      ...p, deliverables: p.deliverables.map(d =>
        d.id === selected.id ? { ...d, requirements: [...d.requirements, r] } : d
      ),
    }))
    setNewReq(""); showToast("요구사항을 추가했습니다.")
  }

  const saveEditReq = () => {
    if (!selected || !editReq) return
    if (!editReq.content.trim()) return
    updateProject(p => ({
      ...p, deliverables: p.deliverables.map(d =>
        d.id === selected.id
          ? { ...d, requirements: d.requirements.map(r => r.id === editReq.id ? editReq : r) }
          : d
      ),
    }))
    setEditReq(null); showToast("요구사항을 수정했습니다.")
  }

  const deleteReq = (rId: string) => {
    if (!selected) return
    const hasLinked = project.tasks.some(t => t.requirementId === rId)
    if (hasLinked) {
      showToast("이 요구사항에 연결된 업무가 있습니다. 업무 연결을 먼저 해제해 주세요.", "error"); return
    }
    updateProject(p => ({
      ...p, deliverables: p.deliverables.map(d =>
        d.id === selected.id ? { ...d, requirements: d.requirements.filter(r => r.id !== rId) } : d
      ),
    }))
  }

  const handleLinkTask = () => {
    if (!selected || !linkTaskId) return
    updateProject(p => addActivity({
      ...p, tasks: p.tasks.map(t => t.id === linkTaskId
        ? { ...t, deliverableId: selected.id, updatedAt: new Date().toISOString() } : t),
    }, `업무를 "${selected.title}"에 연결`))
    setLinkTaskId(""); showToast("업무를 연결했습니다.")
  }

  const handleUnlinkTask = (taskId: string) => {
    const t = project.tasks.find(x => x.id === taskId)
    updateProject(p => addActivity({
      ...p, tasks: p.tasks.map(x => x.id === taskId
        ? { ...x, deliverableId: "", requirementId: "", updatedAt: new Date().toISOString() } : x),
    }, `업무 "${t?.title ?? ""}" 연결 해제`))
    showToast("업무 연결을 해제했습니다.")
  }

  const handleDelete = () => {
    if (!selected) return
    const hasLinked = project.tasks.some(t => t.deliverableId === selected.id)
    if (hasLinked) { showToast("연결된 업무가 있습니다. 업무 연결을 먼저 해제해 주세요.", "error"); return }
    updateProject(p => addActivity({
      ...p, deliverables: p.deliverables.filter(d => d.id !== selected.id),
    }, `제출물 "${selected.title}" 삭제`))
    setSelectedId(project.deliverables.filter(d => d.id !== selected.id)[0]?.id ?? "")
  }

  return (
    <div className="flex h-full overflow-hidden">
      {toast && <Toast message={toast.msg} type={toast.type} onDismiss={() => setToast(null)} />}
      {createTaskReq && selected && (
        <CreateTaskFromReqModal
          project={project} deliverable={selected} requirement={createTaskReq}
          onClose={() => setCreateTaskReq(null)}
          onCreate={t => {
            updateProject(p => addActivity({ ...p, tasks: [...p.tasks, t] }, `요구사항에서 업무 "${t.title}" 생성`))
            setCreateTaskReq(null)
            showToast(`업무 "${t.title}"를 만들었습니다.`)
          }} />
      )}

      {/* Left panel */}
      <div className="w-60 border-r border-slate-200 bg-white flex flex-col shrink-0">
        <div className="p-3 border-b border-slate-200 flex items-center justify-between">
          <p className="text-xs font-semibold text-slate-600">제출물</p>
          <Btn size="sm" onClick={() => setShowAdd(v => !v)}><Plus size={12} />추가</Btn>
        </div>
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {project.deliverables.length === 0 && (
            <p className="text-xs text-slate-400 text-center py-6">등록된 제출물 없음</p>
          )}
          {project.deliverables.map(d => {
            const linked = project.tasks.filter(t => t.deliverableId === d.id)
            const done = linked.filter(t => t.status === "DONE").length
            const due = dueBadge(d.dueDate)
            const sel = selectedId === d.id
            return (
              <button key={d.id} onClick={() => { setSelectedId(d.id); setEditMode(false) }}
                className={`w-full text-left rounded-lg p-2.5 transition-colors ${sel ? "bg-slate-100 border border-slate-300" : "hover:bg-slate-50 border border-transparent"}`}>
                <p className={`text-sm font-semibold truncate ${sel ? "text-slate-900" : "text-slate-800"}`}>{d.title}</p>
                <p className="text-xs text-slate-400 mt-0.5">{d.dueDate || "기한 미설정"}</p>
                <div className="flex items-center gap-2 mt-1.5 flex-wrap">
                  {due && <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${due.cls}`}>{due.label}</span>}
                  <span className="text-xs text-slate-400">업무 {linked.length} (완료 {done})</span>
                </div>
              </button>
            )
          })}
        </div>
        {showAdd && (
          <form onSubmit={handleAdd} className="p-3 border-t border-slate-200 space-y-2 bg-slate-50">
            {addError && <p className="text-xs text-red-600">{addError}</p>}
            <input autoFocus value={addForm.title}
              onChange={e => { setAddForm(f => ({ ...f, title: e.target.value })); setAddError("") }}
              placeholder="제출물 이름 *"
              className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white" />
            <input type="date" value={addForm.dueDate}
              onChange={e => { setAddForm(f => ({ ...f, dueDate: e.target.value })); setAddError("") }}
              className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white" />
            <select value={addForm.ownerMemberId} onChange={e => setAddForm(f => ({ ...f, ownerMemberId: e.target.value }))}
              className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white">
              <option value="">담당자 선택</option>
              {project.members.filter(m => m.role !== "OBSERVER").map(m =>
                <option key={m.id} value={m.id}>{m.name}</option>
              )}
            </select>
            <div className="flex gap-1.5">
              <Btn size="sm" type="submit" disabled={savingAdd} className="flex-1">
                {savingAdd ? "…" : "추가"}
              </Btn>
              <Btn size="sm" variant="ghost" type="button" onClick={() => { setShowAdd(false); setAddError("") }}>취소</Btn>
            </div>
          </form>
        )}
      </div>

      {/* Right panel */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {!selected ? (
          <div className="flex-1 overflow-y-auto p-5">
            <EmptyState icon={FileText} title="제출물을 선택하세요"
              desc="과제 안내에 나온 결과물을 제출물로 등록하고 요구사항과 업무를 연결하세요."
              action={<Btn onClick={() => setShowAdd(true)}><Plus size={14} />제출물 등록</Btn>} />
          </div>
        ) : editMode && editForm ? (
          <div className="flex-1 overflow-y-auto p-5">
            <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-4 max-w-2xl">
              <div className="flex items-center justify-between">
                <h3 className="text-base font-bold text-slate-900 font-display">제출물 수정</h3>
                <div className="flex gap-2">
                  <Btn size="sm" variant="ghost" onClick={() => setEditMode(false)}>취소</Btn>
                  <Btn size="sm" onClick={handleSaveEdit}>저장</Btn>
                </div>
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1.5">제출물 이름 *</label>
                <input value={editForm.title}
                  onChange={e => setEditForm(f => f ? { ...f, title: e.target.value } : f)}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1.5">제출 기한 *</label>
                  <input type="date" value={editForm.dueDate}
                    onChange={e => setEditForm(f => f ? { ...f, dueDate: e.target.value } : f)}
                    className="w-full border border-slate-200 rounded-lg px-2.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1.5">제출 담당자</label>
                  <select value={editForm.ownerMemberId}
                    onChange={e => setEditForm(f => f ? { ...f, ownerMemberId: e.target.value } : f)}
                    className="w-full border border-slate-200 rounded-lg px-2.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300">
                    <option value="">미지정</option>
                    {project.members.filter(m => m.role !== "OBSERVER").map(m =>
                      <option key={m.id} value={m.id}>{m.name}</option>
                    )}
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1.5">제출 경로 <span className="font-normal text-slate-400">(어디에 제출하는지)</span></label>
                <input value={editForm.submissionMethod}
                  onChange={e => setEditForm(f => f ? { ...f, submissionMethod: e.target.value } : f)}
                  placeholder="예: 학교 LMS 과제함, 이메일 제출"
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1.5">설명</label>
                <textarea value={editForm.description}
                  onChange={e => setEditForm(f => f ? { ...f, description: e.target.value } : f)} rows={2}
                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-slate-300" />
              </div>
              <div className="pt-3 border-t border-slate-200 flex justify-between items-center">
                <button onClick={handleDelete}
                  className="text-sm text-red-600 hover:text-red-800 hover:underline flex items-center gap-1">
                  <Trash2 size={13} />제출물 삭제
                </button>
              </div>
            </div>
          </div>
        ) : (
          <>
            {/* Header */}
            <div className="px-5 pt-4 pb-0 bg-white border-b border-slate-200 shrink-0">
              <div className="flex items-start justify-between gap-3 mb-3">
                <div className="flex-1 min-w-0">
                  <h2 className="text-lg font-bold text-slate-900 font-display leading-snug">{selected.title}</h2>
                  <div className="flex items-center gap-3 mt-1.5 text-sm flex-wrap">
                    <span className="flex items-center gap-1 text-slate-600">
                      <Calendar size={13} className="text-slate-400" />
                      <span className={`font-medium ${daysUntil(selected.dueDate) !== null && (daysUntil(selected.dueDate) ?? 0) < 0 ? "text-red-600" : (daysUntil(selected.dueDate) ?? 0) <= 7 ? "text-orange-600" : "text-slate-700"}`}>
                        {selected.dueDate || "기한 미설정"}
                      </span>
                      {selected.dueDate && (() => { const d = dueBadge(selected.dueDate); return d ? <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${d.cls}`}>{d.label}</span> : null })()}
                    </span>
                    <span className="text-slate-300">|</span>
                    <span className="text-slate-600">
                      제출 담당자: <span className="font-medium text-slate-800">{selected.ownerMemberId ? getMemberName(project, selected.ownerMemberId) : <span className="text-amber-600">미지정</span>}</span>
                    </span>
                    {selected.submissionMethod && (
                      <>
                        <span className="text-slate-300">|</span>
                        <span className="text-slate-500 text-xs">{selected.submissionMethod}</span>
                      </>
                    )}
                    <span className="text-slate-300">|</span>
                    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${selected.status === "SUBMITTED" ? "bg-emerald-100 text-emerald-700" : "bg-slate-100 text-slate-600"}`}>
                      {selected.status === "SUBMITTED" ? "제출 완료" : "준비 중"}
                    </span>
                  </div>
                </div>
                {!isObserver && (
                  <Btn size="sm" variant="secondary" onClick={() => { setEditMode(true); setEditForm({ ...selected }) }}>
                    <Pencil size={13} />수정
                  </Btn>
                )}
              </div>
              {/* Tabs */}
              <div className="flex gap-0 -mb-px">
                {([["overview","개요·요구사항"],["tasks","연결 업무"],["files","결과 파일"],["review","검토·제출"]] as [DelTab,string][]).map(([key, label]) => (
                  <button key={key} onClick={() => setDelTab(key)}
                    className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${delTab === key ? "border-slate-900 text-slate-900" : "border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300"}`}>
                    {label}
                    {key === "tasks" && <span className="ml-1.5 text-xs text-slate-400">({linkedTasks.length})</span>}
                    {key === "review" && (selected.reviews ?? []).filter(r => r.status === "pending").length > 0 && (
                      <span className="ml-1.5 text-xs bg-amber-100 text-amber-700 px-1 rounded">검토 필요</span>
                    )}
                    {key === "review" && (selected.submissionRecords ?? []).length > 0 && (
                      <span className="ml-1.5 text-xs bg-emerald-100 text-emerald-700 px-1 rounded">{(selected.submissionRecords ?? []).length}건 제출</span>
                    )}
                  </button>
                ))}
              </div>
            </div>

            {/* Tab content */}
            <div className="flex-1 overflow-y-auto p-5">

              {/* ── Tab: 개요·요구사항 ── */}
              {delTab === "overview" && (
                <div className="max-w-2xl space-y-5">
                  {selected.description && (
                    <p className="text-sm text-slate-600 leading-relaxed">{selected.description}</p>
                  )}
                  <div className="bg-white rounded-xl border border-slate-200 p-5">
                    <div className="flex items-center justify-between mb-3">
                      <h3 className="text-sm font-semibold text-slate-900 tracking-[-0.01em] mb-1">요구사항 ({selected.requirements.length})</h3>
                      <span className="text-xs text-slate-400">필수·선택 구분</span>
                    </div>
                    {selected.requirements.length > 0 && (
                      <div className="divide-y divide-slate-100 mb-4">
                        {selected.requirements.map(r => {
                          const linkedCount = project.tasks.filter(t => t.requirementId === r.id).length
                          if (editReq?.id === r.id) {
                            return (
                              <div key={r.id} className="py-3 space-y-2">
                                <input value={editReq.content}
                                  onChange={e => setEditReq(er => er ? { ...er, content: e.target.value } : er)}
                                  className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
                                <div className="flex items-center gap-3">
                                  <label className="flex items-center gap-1.5 text-sm text-slate-600 cursor-pointer">
                                    <input type="checkbox" checked={editReq.required}
                                      onChange={e => setEditReq(er => er ? { ...er, required: e.target.checked } : er)} className="rounded" />
                                    필수 조건
                                  </label>
                                  <Btn size="sm" onClick={saveEditReq} disabled={!editReq.content.trim()}>저장</Btn>
                                  <Btn size="sm" variant="ghost" onClick={() => setEditReq(null)}>취소</Btn>
                                </div>
                              </div>
                            )
                          }
                          return (
                            <div key={r.id} className="py-3 flex items-start gap-2">
                              <span className={`text-xs font-semibold px-1.5 py-0.5 rounded mt-0.5 shrink-0 ${r.required ? "bg-slate-200/70 text-slate-900" : "bg-slate-100 text-slate-500"}`}>
                                {r.required ? "필수" : "선택"}
                              </span>
                              <p className="text-sm text-slate-800 flex-1 leading-relaxed">{r.content}</p>
                              <div className="flex items-center gap-1 shrink-0">
                                {linkedCount > 0 && <span className="text-xs text-slate-400">업무 {linkedCount}개</span>}
                                {!isObserver && (
                                  <>
                                    <Btn size="sm" variant="secondary" onClick={() => setCreateTaskReq(r)}>
                                      <Plus size={12} />업무 만들기
                                    </Btn>
                                    <button onClick={() => setEditReq({ ...r })} className="p-1 rounded hover:bg-slate-100 text-slate-400 hover:text-slate-700" title="수정">
                                      <Pencil size={12} />
                                    </button>
                                    <button onClick={() => deleteReq(r.id)} className="p-1 rounded hover:bg-red-50 text-slate-300 hover:text-red-500" title="삭제">
                                      <Trash2 size={12} />
                                    </button>
                                  </>
                                )}
                              </div>
                            </div>
                          )
                        })}
                      </div>
                    )}
                    {!isObserver && (
                      <div className={`space-y-2 ${selected.requirements.length > 0 ? "border-t border-slate-100 pt-4" : ""}`}>
                        <input value={newReq} onChange={e => setNewReq(e.target.value)}
                          onKeyDown={e => e.key === "Enter" && addReq()}
                          placeholder="요구사항 입력 후 Enter 또는 추가 버튼"
                          className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
                        <div className="flex items-center gap-3">
                          <label className="flex items-center gap-1.5 text-sm text-slate-600 cursor-pointer">
                            <input type="checkbox" checked={reqRequired}
                              onChange={e => setReqRequired(e.target.checked)} className="rounded" />
                            필수 조건
                          </label>
                          <Btn size="sm" onClick={addReq} disabled={!newReq.trim()}>추가</Btn>
                        </div>
                      </div>
                    )}
                    {selected.requirements.length === 0 && isObserver && (
                      <p className="text-sm text-slate-400 text-center py-4">등록된 요구사항이 없습니다.</p>
                    )}
                  </div>
                </div>
              )}

              {/* ── Tab: 연결 업무 ── */}
              {delTab === "tasks" && (
                <div className="max-w-2xl space-y-4">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="text-sm font-bold text-slate-900">연결 업무 {linkedTasks.length}개</span>
                      <span className="text-sm text-slate-400 ml-2">완료 {linkedTasks.filter(t => t.status === "DONE").length}개 (업무 완료율은 제출 완료와 별개입니다)</span>
                    </div>
                    {!isObserver && (
                      <div className="flex gap-2">
                        <Btn size="sm" variant="secondary" onClick={() => setCreateTaskReq({ id:"_new", content:`${selected.title} 업무`, required:false })}>
                          <Plus size={13} />새 업무 만들기
                        </Btn>
                      </div>
                    )}
                  </div>
                  {linkedTasks.length > 0 ? (
                    <div className="bg-white rounded-xl border border-slate-200 divide-y divide-slate-100">
                      {linkedTasks.map(t => {
                        const m = getMember(project, t.assigneeMemberId)
                        const req = selected.requirements.find(r => r.id === t.requirementId)
                        const due = t.status !== "DONE" ? dueBadge(t.dueDate) : null
                        return (
                          <div key={t.id} className="py-3 px-4 flex items-center gap-3">
                            <StatusBadge status={t.status} />
                            <div className="flex-1 min-w-0">
                              <button onClick={() => openTask(t.id)}
                                className="text-sm font-medium text-slate-800 hover:text-slate-900 hover:underline truncate block text-left">
                                {t.title}
                              </button>
                              <p className="text-xs text-slate-400 mt-0.5">
                                {m?.name ?? "담당자 미지정"}
                                {t.dueDate ? ` · ${t.dueDate}` : ""}
                                {req ? ` · ${req.content.slice(0, 28)}${req.content.length > 28 ? "…" : ""}` : ""}
                              </p>
                            </div>
                            {due && <span className={`text-xs px-1.5 py-0.5 rounded font-medium shrink-0 ${due.cls}`}>{due.label}</span>}
                            {!isObserver && (
                              <button onClick={() => handleUnlinkTask(t.id)}
                                className="p-1 rounded hover:bg-slate-100 text-slate-300 hover:text-red-500 shrink-0" title="연결 해제">
                                <X size={12} />
                              </button>
                            )}
                          </div>
                        )
                      })}
                    </div>
                  ) : (
                    <div className="text-center py-10 text-slate-400 text-sm">
                      <ClipboardList size={24} className="mx-auto mb-2 text-slate-300" />
                      연결된 업무가 없습니다. 요구사항 탭에서 업무를 만들거나 아래에서 기존 업무를 연결하세요.
                    </div>
                  )}
                  {!isObserver && (
                    <div className="bg-slate-50 rounded-xl border border-slate-200 p-4 space-y-2">
                      <p className="text-xs font-semibold text-slate-600">기존 업무 연결</p>
                      <div className="flex gap-2">
                        <select value={linkTaskId} onChange={e => setLinkTaskId(e.target.value)}
                          className="flex-1 border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white">
                          <option value="">연결할 업무 선택</option>
                          {unlinkedTasks.map(t => <option key={t.id} value={t.id}>{t.title}</option>)}
                        </select>
                        <Btn size="sm" onClick={handleLinkTask} disabled={!linkTaskId}>연결</Btn>
                      </div>
                      {unlinkedTasks.length === 0 && (
                        <p className="text-xs text-slate-400">미연결 업무가 없습니다. 위에서 새 업무를 만드세요.</p>
                      )}
                    </div>
                  )}
                </div>
              )}

              {/* ── Tab: 결과 파일 ── */}
              {delTab === "files" && (
                <div className="max-w-2xl space-y-4">
                  <div className="bg-amber-50 border border-amber-200 rounded-lg px-4 py-3 text-sm text-amber-800 flex items-start gap-2">
                    <Info size={14} className="mt-0.5 shrink-0" />
                    <div>
                      자료 탭에서 업로드한 파일을 이 제출물과 연결할 수 있습니다. 검토·제출 탭에서 최종 버전을 확정하세요.
                      <br /><span className="text-xs text-amber-600">새 버전을 올려도 이전 검토 완료나 확정 버전이 자동으로 교체되지 않습니다.</span>
                    </div>
                  </div>
                  {project.files.length === 0 ? (
                    <div className="text-center py-10 text-slate-400 text-sm">
                      <FolderOpen size={24} className="mx-auto mb-2 text-slate-300" />
                      자료 탭에서 먼저 파일을 업로드한 뒤 이 제출물과 연결하세요.
                    </div>
                  ) : (
                    <div className="bg-white rounded-xl border border-slate-200 divide-y divide-slate-100">
                      {project.files.map(fg => {
                        const latest = fg.versions[0]
                        const uploader = getMember(project, latest.uploadedByMemberId)
                        return (
                          <div key={fg.id} className="px-4 py-3 flex items-center gap-3">
                            <FolderOpen size={15} className="text-slate-400 shrink-0" />
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-medium text-slate-800 truncate">{fg.name}</p>
                              <p className="text-xs text-slate-400 mt-0.5">
                                v{latest.version} · {uploader?.name ?? ""} · {fmtDate(latest.uploadedAt)} · {latest.size}
                              </p>
                            </div>
                            <span className="text-xs bg-slate-100 text-slate-600 px-2 py-0.5 rounded">{fg.category}</span>
                          </div>
                        )
                      })}
                    </div>
                  )}
                </div>
              )}

              {/* ── Tab: 검토·제출 ── */}
              {delTab === "review" && (
                <div className="max-w-2xl space-y-5">
                  {/* Prototype notice */}
                  <div className="bg-slate-50 border border-slate-200 rounded-lg px-4 py-3 text-sm text-slate-700 flex items-start gap-2">
                    <Info size={14} className="mt-0.5 shrink-0" />
                    <div>
                      검토와 제출은 팀원 사이의 기록입니다. 학교 시스템과 자동 연동되지 않으며, 제출 담당자가 외부에서 직접 제출한 사실을 이 곳에 기록하세요.
                    </div>
                  </div>

                  {/* Reviews section */}
                  <div className="bg-white rounded-xl border border-slate-200 p-5">
                    <div className="flex items-center justify-between mb-3">
                      <h3 className="text-sm font-semibold text-slate-900 tracking-[-0.01em] mb-1">검토 ({selected.reviews.length})</h3>
                      {!isObserver && (
                        <Btn size="sm" variant="secondary" onClick={() => setShowReviewForm(v => !v)}>
                          <Plus size={13} />검토 요청
                        </Btn>
                      )}
                    </div>
                    {showReviewForm && !isObserver && (
                      <div className="mb-4 p-4 bg-slate-50 rounded-lg border border-slate-200 space-y-3">
                        <p className="text-xs font-semibold text-slate-600">새 검토 요청</p>
                        <div className="grid grid-cols-2 gap-3">
                          <div>
                            <label className="block text-xs font-semibold text-slate-600 mb-1">대상 파일</label>
                            <select value={reviewForm.fileGroupId} onChange={e => setReviewForm(f => ({ ...f, fileGroupId: e.target.value, fileVersion: project.files.find(fg => fg.id === e.target.value)?.versions[0]?.version ?? 0 }))}
                              className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white">
                              <option value="">파일 선택</option>
                              {project.files.map(fg => <option key={fg.id} value={fg.id}>{fg.name} (최신 v{fg.versions[0]?.version ?? 1})</option>)}
                            </select>
                          </div>
                          <div>
                            <label className="block text-xs font-semibold text-slate-600 mb-1">검토자</label>
                            <select value={reviewForm.reviewerMemberId} onChange={e => setReviewForm(f => ({ ...f, reviewerMemberId: e.target.value }))}
                              className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white">
                              <option value="">검토자 선택</option>
                              {project.members.filter(m => m.role !== "OBSERVER").map(m => <option key={m.id} value={m.id}>{m.name}</option>)}
                            </select>
                          </div>
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-600 mb-1">검토 요청 내용 <span className="font-normal text-slate-400">(선택)</span></label>
                          <textarea value={reviewForm.comment} onChange={e => setReviewForm(f => ({ ...f, comment: e.target.value }))} rows={2}
                            placeholder="검토할 포인트나 요청 내용"
                            className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-slate-300" />
                        </div>
                        <div className="flex gap-2">
                          <Btn size="sm" onClick={() => {
                            if (!reviewForm.fileGroupId || !reviewForm.reviewerMemberId) { showToast("파일과 검토자를 선택해 주세요.", "error"); return }
                            const review: FileReview = {
                              id: uid(), fileGroupId: reviewForm.fileGroupId, fileVersion: reviewForm.fileVersion,
                              requestedByMemberId: me?.id ?? "", reviewerMemberId: reviewForm.reviewerMemberId,
                              status: "pending", comment: reviewForm.comment, createdAt: new Date().toISOString(), reviewedAt: "",
                            }
                            updateProject(p => addActivity({
                              ...p, deliverables: p.deliverables.map(d => d.id === selected.id ? { ...d, reviews: [...d.reviews, review] } : d),
                            }, `"${selected.title}"에 검토 요청`))
                            setReviewForm({ fileGroupId:"", fileVersion:0, reviewerMemberId:"", comment:"" })
                            setShowReviewForm(false); showToast("검토를 요청했습니다.")
                          }}>검토 요청 등록</Btn>
                          <Btn size="sm" variant="ghost" onClick={() => setShowReviewForm(false)}>취소</Btn>
                        </div>
                      </div>
                    )}
                    {selected.reviews.length === 0 ? (
                      <p className="text-sm text-slate-400 text-center py-6">검토 기록이 없습니다.</p>
                    ) : (
                      <div className="divide-y divide-slate-100">
                        {selected.reviews.map(rev => {
                          const fg = project.files.find(f => f.id === rev.fileGroupId)
                          const requester = getMember(project, rev.requestedByMemberId)
                          const reviewer = getMember(project, rev.reviewerMemberId)
                          const statusCls = rev.status === "approved" ? "bg-emerald-100 text-emerald-700" : rev.status === "change_requested" ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700"
                          const statusLabel = rev.status === "approved" ? "검토 완료" : rev.status === "change_requested" ? "수정 요청" : "검토 중"
                          return (
                            <div key={rev.id} className="py-3">
                              <div className="flex items-start justify-between gap-2">
                                <div className="flex-1 min-w-0">
                                  <div className="flex items-center gap-2 flex-wrap">
                                    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${statusCls}`}>{statusLabel}</span>
                                    <span className="text-xs text-slate-500">{fg?.name ?? "파일 없음"} v{rev.fileVersion}</span>
                                    <span className="text-xs text-slate-400">검토자: {reviewer?.name ?? "미지정"}</span>
                                  </div>
                                  {rev.comment && <p className="text-sm text-slate-700 mt-1.5 leading-relaxed">{rev.comment}</p>}
                                  <p className="text-xs text-slate-400 mt-1">{requester?.name ?? ""} 요청 · {fmtDate(rev.createdAt)}{rev.reviewedAt ? ` · 검토 ${fmtDate(rev.reviewedAt)}` : ""}</p>
                                </div>
                                {rev.status === "pending" && !isObserver && (
                                  <div className="flex gap-1.5 shrink-0">
                                    <Btn size="sm" variant="secondary" onClick={() => {
                                      updateProject(p => addActivity({ ...p, deliverables: p.deliverables.map(d => d.id === selected.id ? { ...d, reviews: d.reviews.map(r => r.id === rev.id ? { ...r, status:"approved", reviewedAt:new Date().toISOString() } : r) } : d) }, `"${selected.title}"을(를) 검토했어요`))
                                      showToast("검토 완료로 표시했습니다.")
                                    }}>
                                      <Check size={12} />완료
                                    </Btn>
                                    <Btn size="sm" variant="danger" onClick={() => {
                                      updateProject(p => addActivity({ ...p, deliverables: p.deliverables.map(d => d.id === selected.id ? { ...d, reviews: d.reviews.map(r => r.id === rev.id ? { ...r, status:"change_requested", reviewedAt:new Date().toISOString() } : r) } : d) }, `"${selected.title}"에 수정을 요청했어요`))
                                      showToast("수정 요청으로 표시했습니다.")
                                    }}>
                                      수정 요청
                                    </Btn>
                                  </div>
                                )}
                              </div>
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </div>

                  {/* Submission records */}
                  <div className="bg-white rounded-xl border border-slate-200 p-5">
                    <div className="flex items-center justify-between mb-3">
                      <div>
                        <h3 className="text-sm font-semibold text-slate-900 tracking-[-0.01em] mb-1">제출 기록 ({selected.submissionRecords.length})</h3>
                        <p className="text-xs text-slate-400 mt-0.5">팀원이 외부에서 직접 제출한 사실을 기록합니다</p>
                      </div>
                      {!isObserver && (
                        <Btn size="sm" onClick={() => setShowSubForm(v => !v)}>
                          <Plus size={13} />제출 기록 추가
                        </Btn>
                      )}
                    </div>
                    {showSubForm && !isObserver && (
                      <div className="mb-4 p-4 bg-slate-50 rounded-lg border border-slate-200 space-y-3">
                        <p className="text-xs font-semibold text-slate-600">제출 기록 등록</p>
                        <div className="grid grid-cols-2 gap-3">
                          <div>
                            <label className="block text-xs font-semibold text-slate-600 mb-1">제출 파일 <span className="font-normal text-slate-400">(선택)</span></label>
                            <select value={subForm.fileGroupId} onChange={e => setSubForm(f => ({ ...f, fileGroupId: e.target.value, fileVersion: project.files.find(fg => fg.id === e.target.value)?.versions[0]?.version ?? 0 }))}
                              className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white">
                              <option value="">파일 선택</option>
                              {project.files.map(fg => <option key={fg.id} value={fg.id}>{fg.name} (v{fg.versions[0]?.version})</option>)}
                            </select>
                          </div>
                          <div>
                            <label className="block text-xs font-semibold text-slate-600 mb-1">제출자</label>
                            <select value={subForm.confirmedBy} onChange={e => setSubForm(f => ({ ...f, confirmedBy: e.target.value }))}
                              className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white">
                              <option value="">선택</option>
                              {project.members.filter(m => m.role !== "OBSERVER").map(m => <option key={m.id} value={m.id}>{m.name}</option>)}
                            </select>
                          </div>
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-600 mb-1">제출 경로 <span className="font-normal text-slate-400">(어디에 제출했는지)</span></label>
                          <input value={subForm.submissionPath} onChange={e => setSubForm(f => ({ ...f, submissionPath: e.target.value }))}
                            placeholder="예: 학교 LMS 과제함 업로드"
                            className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
                        </div>
                        <div>
                          <label className="block text-xs font-semibold text-slate-600 mb-1">메모 <span className="font-normal text-slate-400">(선택)</span></label>
                          <input value={subForm.note} onChange={e => setSubForm(f => ({ ...f, note: e.target.value }))}
                            placeholder="제출 확인 번호, 스크린샷 위치 등"
                            className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
                        </div>
                        <div className="flex gap-2">
                          <Btn size="sm" onClick={() => {
                            if (!subForm.confirmedBy) { showToast("제출자를 선택해 주세요.", "error"); return }
                            const rec: SubmissionRecord = {
                              id: uid(), submittedByMemberId: subForm.confirmedBy,
                              submittedAt: new Date().toISOString(),
                              fileGroupId: subForm.fileGroupId, fileVersion: subForm.fileVersion,
                              submissionPath: subForm.submissionPath, note: subForm.note,
                            }
                            updateProject(p => addActivity({
                              ...p, deliverables: p.deliverables.map(d => d.id === selected.id ? { ...d, submissionRecords: [...d.submissionRecords, rec], status: "SUBMITTED" } : d),
                            }, `"${selected.title}" 제출 기록`))
                            setSubForm({ fileGroupId:"", fileVersion:0, submissionPath:"", note:"", confirmedBy:"" })
                            setShowSubForm(false); showToast("제출 기록을 추가했습니다.")
                          }}>기록 등록</Btn>
                          <Btn size="sm" variant="ghost" onClick={() => setShowSubForm(false)}>취소</Btn>
                        </div>
                      </div>
                    )}
                    {selected.submissionRecords.length === 0 ? (
                      <div className="text-center py-6 text-slate-400 text-sm">
                        <p>아직 제출 기록이 없습니다.</p>
                        <p className="text-xs mt-1">팀원이 외부(학교 LMS 등)에서 직접 제출한 후 '제출 기록 추가'로 기록하세요.</p>
                      </div>
                    ) : (
                      <div className="divide-y divide-slate-100">
                        {selected.submissionRecords.map(rec => {
                          const fg = project.files.find(f => f.id === rec.fileGroupId)
                          const submitter = getMember(project, rec.submittedByMemberId)
                          return (
                            <div key={rec.id} className="py-3">
                              <div className="flex items-start gap-2">
                                <CheckCircle size={14} className="text-emerald-500 mt-0.5 shrink-0" />
                                <div className="flex-1">
                                  <p className="text-sm font-medium text-slate-800">
                                    {submitter?.name ?? "알 수 없음"} 제출
                                    {fg ? ` · ${fg.name} v${rec.fileVersion}` : ""}
                                  </p>
                                  <p className="text-xs text-slate-400 mt-0.5">
                                    {fmtDateTime(rec.submittedAt)}{rec.submissionPath ? ` · ${rec.submissionPath}` : ""}
                                  </p>
                                  {rec.note && <p className="text-xs text-slate-500 mt-1">{rec.note}</p>}
                                </div>
                              </div>
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// TEAM VIEW
// ═══════════════════════════════════════════════════════════════
function InviteCard({ project }: { project: Project }) {
  const [copied, setCopied] = useState<"" | "code" | "link">("")
  const link = `${window.location.origin}${window.location.pathname}#join=${project.inviteCode}`
  const copy = async (what: "code" | "link") => {
    try { await navigator.clipboard.writeText(what === "code" ? project.inviteCode : link) } catch {}
    setCopied(what); setTimeout(() => setCopied(""), 1500)
  }
  return (
    <div className="rounded-xl border border-slate-200 bg-[#F7F6F3] p-4 flex flex-wrap items-center gap-4">
      <div className="flex-1 min-w-[200px]">
        <p className="text-[13px] font-semibold text-slate-800 flex items-center gap-1.5"><UserPlus size={14} className="text-slate-500" />팀원 초대</p>
        <p className="text-[12px] text-slate-500 mt-1 leading-relaxed">이 코드를 받은 사람은 로그인 후 <b className="font-medium text-slate-700">참여하기</b>에서 입력해 팀에 들어올 수 있습니다.</p>
      </div>
      <div className="flex items-center gap-2">
        <span className="h-9 px-3 rounded-lg bg-white border border-slate-200 flex items-center font-mono-data text-[16px] font-semibold tracking-[0.1em] text-slate-900">{project.inviteCode}</span>
        <Btn variant="secondary" onClick={() => copy("code")}>{copied === "code" ? <><Check size={13} />복사됨</> : <><Copy size={13} />코드</>}</Btn>
        <Btn variant="secondary" onClick={() => copy("link")}>{copied === "link" ? <><Check size={13} />복사됨</> : <><Link2 size={13} />링크</>}</Btn>
      </div>
    </div>
  )
}

function TeamView({ project, updateProject, openTask, initialTab }: {
  project: Project; updateProject: (upd: (p: Project) => Project) => boolean; openTask: (id: string) => void
  initialTab?: "members"|"alerts"|"activity"|"settings"
}) {
  const [tab, setTab] = useState<"members"|"alerts"|"activity"|"settings">(initialTab ?? "members")
  const [showAddMember, setShowAddMember] = useState(false)
  const [addName, setAddName] = useState("")
  const [addRole, setAddRole] = useState<MemberRole>("MEMBER")
  const [settingsForm, setSettingsForm] = useState({
    name: project.name, subject: project.subject,
    semester: project.semester, description: project.description, deadline: project.deadline,
  })
  const [savingSettings, setSavingSettings] = useState(false)
  const [settingsSaved, setSettingsSaved] = useState(false)
  const [settingsError, setSettingsError] = useState("")
  const [removeDialog, setRemoveDialog] = useState<{ memberId: string; name: string; blocked: boolean } | null>(null)

  const alerts = computeAlerts(project)
  const unread = alerts.filter(a => !a.isRead)

  const alertMeta: Record<string, { icon: any; bg: string; text: string; border: string }> = {
    inactive:   { icon: Clock,         bg:"bg-orange-50", text:"text-orange-700", border:"border-orange-200" },
    unassigned: { icon: UserCheck,     bg:"bg-amber-50",  text:"text-amber-700",  border:"border-amber-200" },
    unlinked:   { icon: Link2,         bg:"bg-slate-100",   text:"text-slate-900",   border:"border-slate-300" },
    overdue:    { icon: AlertTriangle, bg:"bg-red-50",    text:"text-red-700",    border:"border-red-200" },
  }
  const actionLabel: Record<string, string> = {
    inactive:"확인하기", unassigned:"담당자 지정", unlinked:"업무 연결", overdue:"업무 보기",
  }

  const handleAddMember = () => {
    if (!addName.trim()) return
    const m: Member = {
      id: uid(), name: addName.trim(), role: addRole,
      color: MEMBER_COLORS[project.members.length % MEMBER_COLORS.length],
      email: "", lastActiveAt: new Date().toISOString(),
    }
    updateProject(p => addActivity({ ...p, members: [...p.members, m] }, `${m.name}님을 팀원으로 추가했어요`))
    setAddName(""); setShowAddMember(false)
  }

  const handleRemoveMember = (memberId: string) => {
    const m = getMember(project, memberId)
    if (!m) return
    const hasTasks = project.tasks.some(t => t.assigneeMemberId === memberId)
    setRemoveDialog({ memberId, name: m.name, blocked: hasTasks })
  }
  const confirmRemoveMember = () => {
    if (!removeDialog || removeDialog.blocked) { setRemoveDialog(null); return }
    const { memberId, name } = removeDialog
    updateProject(p => addActivity({ ...p, members: p.members.filter(x => x.id !== memberId) }, `${name}님을 팀에서 내보냈어요`))
    setRemoveDialog(null)
  }

  const handleSaveSettings = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!settingsForm.name.trim()) return
    setSavingSettings(true); setSettingsError("")
    await new Promise(r => setTimeout(r, 300))
    try {
      updateProject(p => addActivity({ ...p, ...settingsForm }, "프로젝트 정보 수정"))
      setSavingSettings(false); setSettingsSaved(true)
      setTimeout(() => setSettingsSaved(false), 3000)
    } catch {
      setSavingSettings(false)
      setSettingsError("저장에 실패했습니다. 브라우저 저장 공간을 확인해 주세요.")
    }
  }

  const markAlertRead = (id: string) =>
    updateProject(p => ({ ...p, readAlertIds: [...new Set([...p.readAlertIds, id])] }))

  return (
    <div className="p-5 max-w-2xl space-y-4">
      {removeDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/40" onClick={() => setRemoveDialog(null)} />
          <div role="dialog" aria-modal="true" className="relative bg-white rounded-xl shadow-2xl p-6 max-w-sm w-full animate-fade-in">
            <h3 className="text-base font-bold text-slate-900 font-display mb-1">{removeDialog.blocked ? "팀원을 제거할 수 없습니다" : "팀원 제거"}</h3>
            <p className="text-sm text-slate-500 mb-5 leading-relaxed">
              {removeDialog.blocked
                ? `${removeDialog.name}님에게 배정된 업무가 있습니다. 업무 담당자를 먼저 변경해 주세요.`
                : `${removeDialog.name}님을 팀에서 제거할까요?`}
            </p>
            <div className="flex gap-2 justify-end">
              {removeDialog.blocked ? (
                <Btn onClick={() => setRemoveDialog(null)}>확인</Btn>
              ) : (
                <>
                  <Btn variant="ghost" onClick={() => setRemoveDialog(null)}>취소</Btn>
                  <Btn variant="danger" onClick={confirmRemoveMember}>제거</Btn>
                </>
              )}
            </div>
          </div>
        </div>
      )}
      <div className="flex gap-1.5 flex-wrap">
        {([
          ["members","팀원",""],
          ["alerts","활동 현황",unread.length > 0 ? String(unread.length) : ""],
          ["activity","활동 기록",""],
          ["settings","설정",""],
        ] as const).map(([t, label, badge]) => (
          <button key={t} onClick={() => setTab(t as any)}
            className={`px-3 py-2 text-sm font-medium rounded-lg transition-colors ${tab === t ? "bg-slate-100 text-slate-900 font-medium" : "text-slate-600 hover:bg-slate-100"}`}>
            {label}
            {badge && (
              <span className="ml-1.5 bg-red-500 text-white text-[10px] font-bold px-1.5 py-0.5 rounded-full">{badge}</span>
            )}
          </button>
        ))}
      </div>

      {tab === "members" && (
        <div className="space-y-4">
          {!project.isDemo && <InviteCard project={project} />}
          <div className="bg-white rounded-xl border border-slate-200 divide-y divide-slate-100">
            {project.members.map(m => {
              const myTasks = project.tasks.filter(t => t.assigneeMemberId === m.id)
              const done = myTasks.filter(t => t.status === "DONE").length
              const attendedMeetings = project.meetings.filter(mt => mt.attendeeMemberIds.includes(m.id))
              const attended = attendedMeetings.filter(mt => mt.checkedInMemberIds.includes(m.id)).length
              const daysSince = Math.floor((Date.now() - new Date(m.lastActiveAt).getTime()) / 86400000)
              const isMe = m.id === MY_USER_ID
              return (
                <div key={m.id} className="px-4 py-4 flex items-start gap-3">
                  <Avatar member={m} size={9} />
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                      <p className="text-sm font-semibold text-slate-900">{m.name}</p>
                      {isMe && <span className="text-xs bg-slate-200/70 text-slate-900 px-1.5 py-0.5 rounded font-medium">나</span>}
                      <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${m.role === "LEADER" ? "bg-slate-200/70 text-slate-900" : m.role === "OBSERVER" ? "bg-slate-100 text-slate-600" : "bg-teal-50 text-teal-700"}`}>
                        {m.role === "LEADER" ? "팀장" : m.role === "OBSERVER" ? "열람" : "팀원"}
                      </span>
                    </div>
                    <div className="grid grid-cols-3 gap-2">
                      {[
                        ["담당 업무", `${myTasks.length}개 (완료 ${done})`],
                        ["출석", `${attended}/${attendedMeetings.length}회`],
                        ["마지막 활동", daysSince === 0 ? "오늘" : `${daysSince}일 전`],
                      ].map(([k, v]) => (
                        <div key={k} className="bg-slate-50 rounded-lg p-2 text-center">
                          <p className="text-xs text-slate-400">{k}</p>
                          <p className="text-xs font-semibold text-slate-700 mt-0.5">{v}</p>
                        </div>
                      ))}
                    </div>
                    {daysSince >= 14 && (
                      <p className="text-xs text-orange-600 mt-2 flex items-center gap-1">
                        <AlertTriangle size={10} />최근 2주 활동 기록 없음
                      </p>
                    )}
                  </div>
                  {!isMe && m.role !== "LEADER" && (
                    <button onClick={() => handleRemoveMember(m.id)}
                      className="p-1.5 rounded hover:bg-red-50 text-slate-300 hover:text-red-500" title="팀에서 제거">
                      <X size={13} />
                    </button>
                  )}
                </div>
              )
            })}
          </div>
          {showAddMember ? (
            <div className="bg-white rounded-xl border border-slate-200 p-4 space-y-3">
              <p className="text-sm font-semibold text-slate-800">팀원 추가</p>
              <p className="text-xs text-slate-400">이 시제품에서는 실제 초대장이 발송되지 않습니다.</p>
              <div className="flex gap-2">
                <input value={addName} onChange={e => setAddName(e.target.value)} autoFocus
                  onKeyDown={e => e.key === "Enter" && handleAddMember()} placeholder="이름"
                  className="flex-1 border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
                <select value={addRole} onChange={e => setAddRole(e.target.value as MemberRole)}
                  className="border border-slate-200 rounded-lg px-2.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300 bg-white">
                  <option value="MEMBER">팀원</option>
                  <option value="OBSERVER">열람</option>
                </select>
              </div>
              <div className="flex gap-2 justify-end">
                <Btn size="sm" variant="secondary" onClick={() => { setShowAddMember(false); setAddName("") }}>취소</Btn>
                <Btn size="sm" onClick={handleAddMember} disabled={!addName.trim()}>추가</Btn>
              </div>
            </div>
          ) : (
            <Btn variant="secondary" onClick={() => setShowAddMember(true)} className="w-full justify-center">
              <UserPlus size={14} />팀원 추가
            </Btn>
          )}
        </div>
      )}

      {tab === "alerts" && (
        <div className="space-y-3">
          <div className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-3 text-xs text-slate-500 flex items-start gap-2">
            <Info size={12} className="text-slate-400 mt-0.5 shrink-0" />
            알림은 현재 프로젝트 상태에서 자동으로 계산됩니다.
            담당자를 지정하거나 업무를 완료하면 해당 알림이 사라집니다.
          </div>
          {alerts.length === 0 ? (
            <div className="text-center py-10 text-sm text-slate-400">확인이 필요한 항목이 없습니다.</div>
          ) : alerts.map(a => {
            const meta = alertMeta[a.type]
            const Icon = meta.icon
            return (
              <div key={a.id} className={`rounded-xl border p-4 transition-opacity ${a.isRead ? "opacity-60 border-slate-200 bg-white" : `${meta.bg} border-l-4 ${meta.border} bg-white`}`}>
                <div className="flex items-start gap-3">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${meta.bg} border ${meta.border}`}>
                    <Icon size={14} className={meta.text} />
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-0.5">
                      <p className="text-sm font-semibold text-slate-900">{a.title}</p>
                      {!a.isRead && <span className="w-2 h-2 rounded-full bg-slate-800 shrink-0" />}
                    </div>
                    <p className="text-xs text-slate-500 mb-2 leading-relaxed">{a.detail}</p>
                    <div className="flex items-center gap-3">
                      {a.relatedTaskId && (
                        <button onClick={() => { markAlertRead(a.id); openTask(a.relatedTaskId) }}
                          className="text-xs font-semibold text-slate-800 hover:underline">
                          {actionLabel[a.type]} →
                        </button>
                      )}
                      {!a.isRead && (
                        <button onClick={() => markAlertRead(a.id)} className="text-xs text-slate-400 hover:text-slate-600">
                          읽음 처리
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {tab === "activity" && (
        <div className="bg-white rounded-xl border border-slate-200">
          {project.activityLog.length === 0 ? (
            <div className="py-10 text-center text-sm text-slate-400">아직 기록된 활동이 없습니다.</div>
          ) : project.activityLog.map(a => {
            const m = getMember(project, a.memberId)
            return (
              <div key={a.id} className="px-4 py-3 flex items-start gap-3 border-b border-slate-100 last:border-0">
                <Avatar member={m} size={6} />
                <div className="flex-1">
                  <p className="text-sm text-slate-800">{a.description}</p>
                  <p className="text-xs text-slate-400 mt-0.5">{m?.name ?? "나"} · {fmtDateTime(a.createdAt)}</p>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {tab === "settings" && (
        <form onSubmit={handleSaveSettings} className="bg-white rounded-xl border border-slate-200 p-5 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-900 tracking-[-0.01em] mb-1">프로젝트 정보</h3>
            {settingsSaved && <span className="text-xs text-emerald-600 flex items-center gap-1"><Check size={11} />저장됨</span>}
          </div>
          {settingsError && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-3 py-2 rounded-lg flex items-center gap-2">
              <AlertCircle size={13} className="shrink-0" />{settingsError}
            </div>
          )}
          {project.isDemo && (
            <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2.5 text-xs text-slate-700 flex items-start gap-2">
              <Info size={12} className="mt-0.5 shrink-0" />예시 프로젝트입니다. 수정해도 시제품 내에서만 반영됩니다.
            </div>
          )}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">프로젝트 이름 *</label>
            <input value={settingsForm.name}
              onChange={e => setSettingsForm(f => ({ ...f, name: e.target.value }))}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1.5">과목명</label>
              <input value={settingsForm.subject}
                onChange={e => setSettingsForm(f => ({ ...f, subject: e.target.value }))}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1.5">학기</label>
              <input value={settingsForm.semester}
                onChange={e => setSettingsForm(f => ({ ...f, semester: e.target.value }))}
                className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
            </div>
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">프로젝트 기한</label>
            <input type="date" value={settingsForm.deadline}
              onChange={e => setSettingsForm(f => ({ ...f, deadline: e.target.value }))}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-slate-300" />
          </div>
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">설명</label>
            <textarea value={settingsForm.description}
              onChange={e => setSettingsForm(f => ({ ...f, description: e.target.value }))} rows={3}
              className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-slate-300" />
          </div>
          <div className="flex justify-end">
            <Btn type="submit" disabled={savingSettings || !settingsForm.name.trim()}>
              {savingSettings ? <><Loader2 size={13} className="animate-spin" />저장 중…</> : "저장"}
            </Btn>
          </div>
        </form>
      )}
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// PROJECT WORKSPACE
// ═══════════════════════════════════════════════════════════════
function ProjectWorkspace({ project, projects, onBack, onSwitch, updateProject }: {
  project: Project; projects: Project[]; onBack: () => void; onSwitch: (id: string) => void
  updateProject: (upd: (p: Project) => Project) => boolean
}) {
  const [view, setView] = useState<ProjectView>(() => {
    const hash = readHash()
    const [, v] = hash.split("/")
    return (PROJECT_VIEWS.includes(v as ProjectView) ? v : "overview") as ProjectView
  })
  const [selectedTaskId, setSelectedTaskId] = useState("")
  const [selectedDeliverableId, setSelectedDeliverableId] = useState(project.deliverables[0]?.id ?? "")
  const [selectedMeetingId, setSelectedMeetingId] = useState("")
  const [showNotifications, setShowNotifications] = useState(false)
  const [teamInitialTab, setTeamInitialTab] = useState<"members"|"alerts"|"activity"|"settings"|"">()

  // Dirty state management
  const isDirtyRef = useRef(false)
  const pendingSaveRef = useRef<(() => Promise<boolean>)|null>(null)
  const pendingDiscardRef = useRef<(() => void)|null>(null)
  const [showUnsaved, setShowUnsaved] = useState(false)
  const [pendingNav, setPendingNav] = useState<(() => void)|null>(null)
  const [unsavedSaving, setUnsavedSaving] = useState(false)

  const registerDirty = useCallback((dirty: boolean, save: () => Promise<boolean>, discard: () => void) => {
    isDirtyRef.current = dirty
    if (dirty) { pendingSaveRef.current = save; pendingDiscardRef.current = discard }
  }, [])

  // Warn on browser tab close when there are unsaved changes
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (isDirtyRef.current) { e.preventDefault(); e.returnValue = "" }
    }
    window.addEventListener("beforeunload", handler)
    return () => window.removeEventListener("beforeunload", handler)
  }, [])

  const guardedNav = useCallback((action: () => void) => {
    if (isDirtyRef.current) {
      setPendingNav(() => action)
      setShowUnsaved(true)
    } else {
      action()
    }
  }, [])

  const handleUnsavedSave = async () => {
    setUnsavedSaving(true)
    const ok = await pendingSaveRef.current?.()
    setUnsavedSaving(false)
    if (ok !== false) {
      isDirtyRef.current = false
      setShowUnsaved(false)
      pendingNav?.(); setPendingNav(null)
    }
  }

  const handleUnsavedDiscard = () => {
    pendingDiscardRef.current?.()
    isDirtyRef.current = false
    setShowUnsaved(false)
    pendingNav?.(); setPendingNav(null)
  }

  // Sync hash
  useEffect(() => {
    window.location.hash = `${project.id}/${view}`
  }, [project.id, view])

  // Listen to hash changes (browser back/forward)
  useEffect(() => {
    const handler = () => {
      const hash = window.location.hash.slice(1)
      const [pid, v] = hash.split("/")
      if (pid !== project.id) return
      if (PROJECT_VIEWS.includes(v as ProjectView)) {
        setView(v as ProjectView)
      }
    }
    window.addEventListener("hashchange", handler)
    return () => window.removeEventListener("hashchange", handler)
  }, [project.id])

  const changeView = useCallback((v: ProjectView) => {
    guardedNav(() => {
      setView(v)
      if (v !== "tasks") { setSelectedTaskId(""); isDirtyRef.current = false }
      if (v !== "meetings") setSelectedMeetingId("")
    })
  }, [guardedNav])

  const openTask = useCallback((id: string) => {
    guardedNav(() => { setSelectedTaskId(id); setView("tasks") })
  }, [guardedNav])

  const openDeliverable = useCallback((id: string) => {
    guardedNav(() => { setSelectedDeliverableId(id); setView("deliverables") })
  }, [guardedNav])

  const openMeeting = useCallback((id: string) => {
    guardedNav(() => { setSelectedMeetingId(id); setView("meetings") })
  }, [guardedNav])

  const handleBack = useCallback(() => guardedNav(onBack), [guardedNav, onBack])
  const handleSwitch = useCallback((id: string) => guardedNav(() => {
    isDirtyRef.current = false; onSwitch(id)
  }), [guardedNav, onSwitch])

  // Guarded task selection (handles dirty state when switching tasks)
  const handleSetSelectedTaskId = useCallback((id: string) => {
    guardedNav(() => { setSelectedTaskId(id) })
  }, [guardedNav])

  const me = getMyMember(project)
  const myName = me?.name ?? "나"
  const alertCount = useMemo(() => computeAlerts(project).filter(a => !a.isRead).length, [project])

  const [mobileNav, setMobileNav] = useState(false)
  const [createSig, setCreateSig] = useState<{ view: ProjectView | null; n: number }>({ view: null, n: 0 })
  const sigFor = (v: ProjectView) => createSig.view === v ? createSig.n : 0
  const viewTitles: Record<ProjectView, string> = {
    overview:"프로젝트 홈", tasks:"업무", meetings:"일정·회의", files:"자료", deliverables:"제출물", team:"팀원", settings:"설정", analytics:"기여도",
  }

  return (
    <div className="flex h-screen overflow-hidden bg-white">
      {showUnsaved && (
        <UnsavedChangesDialog saving={unsavedSaving}
          onSave={handleUnsavedSave}
          onDiscard={handleUnsavedDiscard}
          onCancel={() => { setShowUnsaved(false); setPendingNav(null) }} />
      )}
      <ProjectSidebar project={project} view={view} setView={changeView}
        alertCount={alertCount} onBack={handleBack} onSwitch={handleSwitch} projects={projects}
        mobileOpen={mobileNav} onCloseMobile={() => setMobileNav(false)}
        onCreate={v => { changeView(v); setCreateSig(c => ({ view: v, n: c.n + 1 })) }} />
      <div className="md:ml-60 flex-1 min-w-0 flex flex-col overflow-hidden">
        <TopBar title={`${project.name} · ${viewTitles[view]}`}
          alertCount={alertCount} onBell={() => setShowNotifications(v => !v)} myName={myName} onMenu={() => setMobileNav(true)} />
        <main className="flex-1 overflow-hidden bg-white pb-[calc(56px+env(safe-area-inset-bottom))] md:pb-0">
          {view === "overview" && (
            <div className="h-full overflow-y-auto">
              <OverviewView project={project} setView={changeView}
                openTask={openTask} openDeliverable={openDeliverable} openMeeting={openMeeting}
                openAlerts={() => { setTeamInitialTab("alerts"); changeView("team") }}
                updateProject={updateProject} />
            </div>
          )}
          {view === "tasks" && (
            <TasksView project={project} createSignal={sigFor("tasks")}
              selectedTaskId={selectedTaskId}
              setSelectedTaskId={handleSetSelectedTaskId}
              updateProject={updateProject}
              onOpenMeeting={openMeeting}
              onOpenDeliverable={openDeliverable}
              registerDirty={registerDirty} />
          )}
          {view === "meetings" && (
            <div className="h-full overflow-y-auto">
              <MeetingsView project={project} createSignal={sigFor("meetings")}
                selectedMeetingId={selectedMeetingId}
                setSelectedMeetingId={setSelectedMeetingId}
                updateProject={updateProject}
                openTask={openTask}
                openDeliverable={openDeliverable}
                onAddTask={t => updateProject(p => ({ ...p, tasks: [...p.tasks, t] }))} />
            </div>
          )}
          {view === "files" && (
            <div className="h-full overflow-y-auto">
              <FilesView project={project} createSignal={sigFor("files")} updateProject={updateProject} />
            </div>
          )}
          {view === "deliverables" && (
            <DeliverablesView project={project} createSignal={sigFor("deliverables")}
              selectedId={selectedDeliverableId}
              setSelectedId={setSelectedDeliverableId}
              updateProject={updateProject}
              openTask={openTask} />
          )}
          {view === "analytics" && <ContributionView project={project} />}
          {(view === "team" || view === "settings") && (
            <div className="h-full overflow-y-auto">
              <TeamView key={view} project={project} updateProject={updateProject} openTask={openTask} initialTab={view === "settings" ? "settings" : (teamInitialTab || undefined)} />
            </div>
          )}
        </main>
      </div>
      <nav aria-label="하단 메뉴" className="md:hidden fixed bottom-0 inset-x-0 z-30 bg-white/95 backdrop-blur border-t border-slate-200 pb-[env(safe-area-inset-bottom)]">
        <div className="grid grid-cols-5 h-14">
          {([["overview", LayoutDashboard, "홈"], ["tasks", ClipboardList, "업무"], ["meetings", CalendarClock, "회의"], ["team", Users, "팀"]] as const).map(([v, I, l]) => (
            <button key={v} onClick={() => changeView(v)} aria-current={view === v ? "page" : undefined}
              className={`flex flex-col items-center justify-center gap-0.5 text-[11px] font-medium ${view === v ? "text-slate-900" : "text-slate-400"}`}>
              <I size={20} strokeWidth={view === v ? 2.2 : 1.8} />{l}
            </button>
          ))}
          <button onClick={() => setMobileNav(true)} className="flex flex-col items-center justify-center gap-0.5 text-[11px] font-medium text-slate-400">
            <MenuIcon size={20} strokeWidth={1.8} />더보기
          </button>
        </div>
      </nav>
      {showNotifications && (
        <NotificationPanel project={project}
          onClose={() => setShowNotifications(false)}
          onMarkRead={id => updateProject(p => ({ ...p, readAlertIds: [...new Set([...p.readAlertIds, id])] }))}
          onNavigate={taskId => { openTask(taskId); setShowNotifications(false) }} />
      )}
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════
// APP ROOT
// ═══════════════════════════════════════════════════════════════
export default function App() {
  const [session, setSession] = useState(getSession)
  useEffect(() => onAuthChange(() => { refreshMyUserId(); setSession(getSession()) }), [])
  return (
    session ? <MainApp key={session.email} /> : <PreAuth />
  )
}

function MainApp() {
  const [projects, setProjects] = useState<Project[]>(loadProjects)
  const [currentProjectId, setCurrentProjectId] = useState<string|null>(() => {
    const hash = readHash()
    if (hash.startsWith("join=")) return null
    const [pid] = hash.split("/")
    return pid || null
  })
  const [showCreate, setShowCreate] = useState(false)
  const [joinCode, setJoinCode] = useState<string | null>(() => {
    const m = readHash().match(/^join=([A-Za-z0-9]+)/)
    return m ? m[1].toUpperCase() : null
  })
  const [saveError, setSaveError] = useState("")

  // Persist on every project change
  useEffect(() => {
    const ok = saveProjects(projects)
    setSaveError(ok ? "" : "변경 내용을 저장하지 못했습니다. 브라우저 저장 공간을 확인해 주세요.")
  }, [projects])

  // Hash-based navigation: handle browser back/forward
  useEffect(() => {
    const handler = () => {
      const hash = window.location.hash.slice(1)
      const [pid] = hash.split("/")
      if (!pid || pid.startsWith("join=")) { setCurrentProjectId(null); return }
      if (projects.find(p => p.id === pid)) setCurrentProjectId(pid)
    }
    window.addEventListener("hashchange", handler)
    return () => window.removeEventListener("hashchange", handler)
  }, [projects])

  const updateProject = useCallback((id: string, upd: (p: Project) => Project) => {
    setProjects(prev => prev.map(p => p.id === id ? upd(p) : p))
  }, [])

  const openProject = (id: string) => { setCurrentProjectId(id) }

  const handleCreate = (p: Project) => {
    setProjects(prev => [...prev, p])
    setCurrentProjectId(p.id)
    setShowCreate(false)
  }

  const handleBack = () => {
    setCurrentProjectId(null)
    window.location.hash = ""
  }

  const handleSwitch = (id: string) => {
    setCurrentProjectId(id)
  }

  const currentProject = currentProjectId
    ? projects.find(p => p.id === currentProjectId) ?? null
    : null

  // If hash points to a valid project, ensure it's loaded
  useEffect(() => {
    if (!currentProjectId) return
    if (!projects.find(p => p.id === currentProjectId)) {
      setCurrentProjectId(null)
    }
  }, [currentProjectId, projects])

  if (currentProject) {
    return (
      <>
        {saveError && (
          <div className="fixed top-0 left-0 right-0 z-[200] bg-red-700 text-white text-sm px-4 py-2.5 flex items-center gap-2 justify-center">
            <AlertCircle size={14} className="shrink-0" />
            <span>{saveError}</span>
            <button onClick={() => setSaveError("")} className="ml-4 underline text-red-200">닫기</button>
          </div>
        )}
        <ProjectWorkspace
          key={currentProjectId}
          project={currentProject}
          projects={projects}
          onBack={handleBack}
          onSwitch={handleSwitch}
          updateProject={(upd) => { updateProject(currentProjectId!, upd); return true }}
        />
      </>
    )
  }

  return (
    <>
      {saveError && (
        <div className="fixed top-0 left-0 right-0 z-[200] bg-red-700 text-white text-sm px-4 py-2.5 flex items-center gap-2 justify-center">
          <AlertCircle size={14} className="shrink-0" />{saveError}
          <button onClick={() => setSaveError("")} className="ml-4 underline text-red-200">닫기</button>
        </div>
      )}
      <ProjectList projects={projects} onOpen={openProject} onCreateClick={() => setShowCreate(true)} onJoinClick={() => setJoinCode("")} />
      {showCreate && <CreateProjectModal onClose={() => setShowCreate(false)} onCreate={handleCreate} />}
      {joinCode !== null && (
        <JoinProjectModal projects={projects} initialCode={joinCode}
          onClose={() => { setJoinCode(null); if (window.location.hash.startsWith("#join=")) history.replaceState(null, "", window.location.pathname) }}
          onJoin={(id) => {
            const s = getSession()
            setProjects(prev => prev.map(p => {
              if (p.id !== id || getMyMember(p)) return p
              const m: Member = { id: MY_USER_ID, name: s?.name ?? "새 팀원", role: s?.role === "PROFESSOR" ? "OBSERVER" : "MEMBER",
                color: MEMBER_COLORS[p.members.length % MEMBER_COLORS.length], email: s?.email ?? "", lastActiveAt: new Date().toISOString() }
              return addActivity({ ...p, members: [...p.members, m] }, "초대 코드로 팀에 참여했어요")
            }))
            setJoinCode(null); setCurrentProjectId(id); window.location.hash = id
          }} />
      )}
    </>
  )
}

const isAuthHash = () => typeof location !== "undefined" && /^#(login|signup|join=)/.test(location.hash)
function PreAuth() {
  const [auth, setAuth] = useState(isAuthHash)
  const [k, setK] = useState(0)
  useEffect(() => {
    const on = () => { setAuth(isAuthHash()); setK(x => x + 1) }
    window.addEventListener("hashchange", on)
    return () => window.removeEventListener("hashchange", on)
  }, [])
  if (auth) return <AuthScreen key={k} />
  return <Landing go={to => { location.hash = to; window.scrollTo(0, 0) }} />
}
