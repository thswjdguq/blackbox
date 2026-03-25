# Team Blackbox — Frontend Design Skill

## 이 파일의 목적
Claude Code가 UI를 만들 때 참조하는 디자인 가이드.
모든 컴포넌트는 이 파일의 규칙을 따른다.

---

## 디자인 철학
> "데이터는 복잡하지만, UI는 단순해야 한다."
- 불필요한 장식 없이 정보 전달에 집중
- 한 화면에 한 가지 핵심 메시지
- 교수님도 학생도 처음 봐도 바로 이해 가능한 UI

---

## 컬러 팔레트

```
Primary Background:  #0F172A  (딥 네이비)
Secondary Background:#1E293B  (카드 배경)
Border:              #334155  (구분선)

Primary Text:        #F1F5F9  (흰색에 가까운)
Secondary Text:      #94A3B8  (회색)

포인트 컬러 (1개만 사용):
Accent:              #3B82F6  (블루)
Accent Hover:        #2563EB

상태 컬러:
Success:             #22C55E  (🟢 정상)
Warning:             #EAB308  (🟡 주의)
Orange:              #F97316  (🟠 경고)
Danger:              #EF4444  (🔴 위험)
```

---

## 타이포그래피

```
폰트: Inter (Google Fonts) 또는 시스템 기본 sans-serif

크기:
- 페이지 제목:    text-2xl font-bold      (24px)
- 섹션 제목:      text-lg font-semibold   (18px)
- 본문:           text-sm                 (14px)
- 보조 텍스트:    text-xs text-slate-400  (12px)

절대 사용 금지:
- text-3xl 이상 (너무 큼)
- font-black (너무 두꺼움)
```

---

## 컴포넌트 규칙

### 카드
```tsx
// 항상 이 구조 사용
<div className="bg-slate-800 border border-slate-700 rounded-xl p-6">
  {/* 내용 */}
</div>
```

### 버튼
```tsx
// Primary 버튼
<button className="bg-blue-600 hover:bg-blue-700 text-white 
                   px-4 py-2 rounded-lg text-sm font-medium 
                   transition-colors">
  버튼
</button>

// Secondary 버튼
<button className="bg-slate-700 hover:bg-slate-600 text-slate-200 
                   px-4 py-2 rounded-lg text-sm font-medium 
                   transition-colors">
  버튼
</button>
```

### 배지 (상태 표시)
```tsx
// 건강도 지표
🟢 <span className="bg-green-500/20 text-green-400 px-2 py-1 rounded-full text-xs">정상</span>
🟡 <span className="bg-yellow-500/20 text-yellow-400 px-2 py-1 rounded-full text-xs">주의</span>
🟠 <span className="bg-orange-500/20 text-orange-400 px-2 py-1 rounded-full text-xs">경고</span>
🔴 <span className="bg-red-500/20 text-red-400 px-2 py-1 rounded-full text-xs">위험</span>
```

### 테이블
```tsx
<table className="w-full">
  <thead>
    <tr className="border-b border-slate-700">
      <th className="text-left text-xs text-slate-400 font-medium pb-3">항목</th>
    </tr>
  </thead>
  <tbody className="divide-y divide-slate-700/50">
    <tr className="hover:bg-slate-700/30 transition-colors">
      <td className="py-3 text-sm text-slate-200">내용</td>
    </tr>
  </tbody>
</table>
```

### 입력 필드
```tsx
<input className="w-full bg-slate-700 border border-slate-600 
                  rounded-lg px-4 py-2 text-sm text-slate-200 
                  placeholder-slate-400
                  focus:outline-none focus:border-blue-500 
                  transition-colors" />
```

---

## 레이아웃 규칙

### 전체 레이아웃
```
┌─ Sidebar (w-64) ──┬─ Main Content ────────────────┐
│                   │                               │
│  로고             │  페이지 헤더                   │
│  ───────          │  ─────────────────────────    │
│  네비게이션       │  콘텐츠 영역                   │
│                   │                               │
└───────────────────┴───────────────────────────────┘
```

### 사이드바
```tsx
<aside className="w-64 bg-slate-900 border-r border-slate-700 
                  h-screen fixed left-0 top-0">
```

### 메인 콘텐츠
```tsx
<main className="ml-64 min-h-screen bg-slate-950 p-8">
```

### 그리드 카드 레이아웃
```tsx
// 통계 카드 4개
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">

// 팀 목록 카드
<div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
```

---

## 페이지별 핵심 UI 요소

### 로그인/회원가입 페이지
- 화면 중앙 카드 1개
- 로고 + 프로젝트명 상단 표시
- 이메일/비밀번호 입력 필드
- 역할 선택 (학생/교수) — 회원가입 시만

### 프로젝트 목록 (학생 메인)
- 상단: "내 프로젝트" 제목 + "새 프로젝트" 버튼
- 프로젝트 카드 그리드
- 각 카드: 프로젝트명, 팀원 수, 진행률 바, 건강도 배지

### 칸반 보드
- 3컬럼 고정 레이아웃 (TODO / IN_PROGRESS / DONE)
- 드래그 중 카드에 파란 테두리 효과
- 컬럼 헤더에 태스크 개수 배지

### 교수 대시보드
- 상단 통계 카드 4개 (전체팀/정상/주의/위험)
- 팀 목록 테이블 (팀명, 팀원수, 진행률, 건강도, 마지막활동)
- 클릭 시 팀 상세 슬라이드 패널

### 기여도 차트 (Recharts)
```tsx
// 항상 이 색상 배열 사용
const COLORS = ['#3B82F6', '#22C55E', '#EAB308', '#EF4444']

<BarChart>
  <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
  <XAxis stroke="#94A3B8" />
  <YAxis stroke="#94A3B8" />
  <Tooltip 
    contentStyle={{ 
      backgroundColor: '#1E293B', 
      border: '1px solid #334155' 
    }} 
  />
</BarChart>
```

### Hash Vault 파일 이력
- 타임라인 형식 (세로 선 + 점)
- 각 항목: 파일명, 업로드 시간, 해시값 앞 8자리
- 변조 감지 시 🔴 빨간 테두리 + 경고 아이콘

---

## 절대 하지 말 것

```
❌ 흰 배경 사용 (라이트 모드 금지)
❌ 그라데이션 배경 남용
❌ 애니메이션 과도하게 사용
❌ 한 화면에 너무 많은 정보
❌ 모달 안에 모달
❌ 텍스트에 여러 색상 혼용
❌ 아이콘 없이 텍스트만으로 네비게이션
```

---

## 아이콘

```
lucide-react 사용 (이미 설치됨)

주요 아이콘:
- 대시보드:    LayoutDashboard
- 프로젝트:    FolderKanban
- 칸반:        Kanban
- 회의록:      FileText
- 파일:        Files
- 기여도:      BarChart2
- 경보:        AlertTriangle
- 설정:        Settings
- 로그아웃:    LogOut
- 팀원:        Users
- 교수:        GraduationCap
```

---

## Claude Code 활용 예시

UI 작업 시 항상 이 형식으로 요청:

```
frontend/claude.md 와 frontend-design.md 읽고

[페이지명] 페이지 만들어줘.

필요한 기능:
- [기능1]
- [기능2]

API 연결:
- GET /api/[엔드포인트]

이 파일의 디자인 규칙 전부 적용해줘.
```
