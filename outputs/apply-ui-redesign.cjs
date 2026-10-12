const fs = require('fs');
const path = require('path');
const root = 'C:/blackbox/teamplay-ui';
const edit = (p, fn) => { const file = path.join(root,p); fs.writeFileSync(file,fn(fs.readFileSync(file,'utf8'))); };
function walk(dir) { for (const e of fs.readdirSync(dir,{withFileTypes:true})) { const p=path.join(dir,e.name); if(e.isDirectory()) walk(p); else if(p.endsWith('.tsx')) { let s=fs.readFileSync(p,'utf8'); s=s.replaceAll('파일 금고','프로젝트 자료').replaceAll('증거 패키지','활동 보고서').replaceAll('태스크 완료율','업무 완료율').replaceAll('대시보드로 돌아가기','내 프로젝트로 돌아가기');
// Existing dark-mode choices stay available, but this isolated UI starts in light mode.
s=s.replaceAll('localStorage.getItem("theme")','localStorage.getItem("teamplay-ui-theme")').replaceAll('localStorage.setItem("theme",','localStorage.setItem("teamplay-ui-theme",').replaceAll('localStorage.getItem(\'theme\')','localStorage.getItem(\'teamplay-ui-theme\')').replaceAll('?? "dark"','?? "light"').replaceAll('|| "dark"','|| "light"').replaceAll("||'dark'","||'light'").replaceAll('const [isDark, setIsDark] = useState(true)','const [isDark, setIsDark] = useState(false)');
fs.writeFileSync(p,s); } } }
walk(path.join(root,'frontend/src'));
edit('frontend/src/app/globals.css',s=>s.replace('--bb-bg:        255 255 255','--bb-bg:        249 249 248').replace('--bb-surface:   248 250 252','--bb-surface:   255 255 255').replace('--bb-surface2:  241 245 249','--bb-surface2:  241 241 239').replace('--bb-border:    226 232 240','--bb-border:    233 233 231').replace('--bb-primary:   99 102 241','--bb-primary:   35 131 226').replace('--bb-primary-h: 79 70 229','--bb-primary-h: 31 111 197').replace('--bb-text:      15 23 42','--bb-text:      55 53 47').replace('--bb-text2:     100 116 139','--bb-text2:     95 94 91').replace('--bb-sidebar:   248 250 252','--bb-sidebar:   241 241 239').replace('font-family: Inter, system-ui','font-family: "Pretendard Variable", "Malgun Gothic", Inter, system-ui') + `
/* Bright workspace surfaces use the existing tokens, preserving dark mode. */
body { letter-spacing: -0.015em; }
button, a, input, select, textarea { -webkit-tap-highlight-color: transparent; }
:focus-visible { outline: 2px solid rgb(var(--bb-primary)); outline-offset: 3px; }
main > .max-w-5xl { max-width: 1280px; margin-inline: auto; }
html:not(.dark) .text-indigo-400, html:not(.dark) .text-teal-400, html:not(.dark) .text-green-400 { color: #167567; }
html:not(.dark) .text-red-400 { color: #c4302f; }
html:not(.dark) .text-orange-400, html:not(.dark) .text-amber-400, html:not(.dark) .text-yellow-400 { color: #946800; }
html:not(.dark) .bg-indigo-600 { background-color: #2383e2; }
html:not(.dark) .hover\\:bg-indigo-700:hover { background-color: #1f6fc5; }
`);
edit('frontend/src/components/Sidebar.tsx',s=>s.replace('label: "프로젝트 홈"','label: "한눈에 보기"').replace('label: "업무 보드"','label: "업무"').replace('label: "회의록"','label: "회의 · 회의록"').replace('label: "기여도"','label: "팀 활동 · 기여도"').replaceAll('group: "진행"','group: "프로젝트 진행"').replaceAll('group: "함께"','group: "팀 협업"').replaceAll('group: "기록"','group: "자료와 기록"').replace('px-6 py-5 border-b','px-5 py-6 border-b').replace('py-2 rounded-lg text-sm transition-colors','py-2.5 rounded-lg text-sm transition-colors'));
edit('frontend/src/app/projects/[projectId]/page.tsx',s=>{
s=s.replace('import NextStepCard', 'import ProjectWorkflow from "@/components/project/ProjectWorkflow";\nimport NextStepCard');
s=s.replace('<OnboardingBanner projectId={projectId} />','<ProjectWorkflow projectId={projectId} />');
s=s.replace('bg-bb-surface border border-bb-border rounded-2xl p-6 mb-6','border-b border-bb-border pb-7 mb-7');
// A task ratio is not evidence of project health or submission readiness.
s=s.replace(/<span className=\{`flex items-center gap-1\.5 text-xs font-semibold px-3 py-1 rounded-full border \$\{health\.cls\}`\}>[\s\S]*?<\/span>/,'<span className="text-xs px-2.5 py-1 rounded-md bg-bb-surface2 text-bb-text2">프로젝트 공간</span>');
s=s.replace('팀플 종료 — 활동 보고서 발급','팀 활동 내보내기').replace('회의록 전체 · 기여도 PDF · 프로젝트 자료 이력을 하나의 ZIP으로 묶어 교수님께 제출하세요.','회의록 · 기여도 · 자료 이력을 ZIP으로 내려받습니다. 과제 제출은 제출물별 검토와 확정 후 학교 제출처에서 진행하세요.');
return s;
});
edit('frontend/src/app/dashboard/page.tsx',s=>s.replace('<h1 className="text-2xl font-bold text-bb-text">내 프로젝트</h1>','<p className="text-xs font-semibold tracking-widest text-bb-primary mb-2">TEAM WORKSPACE</p><h1 className="text-3xl font-bold text-bb-text">함께 만드는 결과물, 한곳에서.</h1>').replace('참여 중인 프로젝트 {projects.length}개','내 프로젝트에서 할 일을 확인하고, 검토부터 제출까지 이어가세요.').replace('hover:bg-slate-800/60','hover:bg-bb-surface2').replace('group-hover:text-white','group-hover:text-bb-text'));
fs.mkdirSync(path.join(root,'docs/ui-redesign'),{recursive:true});
fs.writeFileSync(path.join(root,'docs/ui-redesign/PLAN.md'),`# 별도 UI 개선 작업\n\n- 기준: 98d073b (현행 시연 복사본). 원격 최신 main 여부는 이번 작업에서 재조회하지 않음.\n- 폴더: C:/blackbox/teamplay-ui, 브랜치: codex/ui-flow-redesign\n- 사용자 직접 요청: 기존 시연 환경을 보존하고 ZIP 시안 스타일로 UI/메뉴/흐름 개선.\n- 허용: frontend 화면·스타일, 이 작업 문서, 별도 로컬 실행 설정. C 소유 영역 변경은 사용자 요청 범위이며 향후 통합 전 C와 비교 리뷰 필요.\n- 제외: 백엔드·API·DB 계약 변경, 운영 데이터 수정, 원격 push/merge.\n- 시안의 localStorage 인증/저장/승인 로직을 가져오지 않는다. 기존 실제 API와 권한을 사용한다.\n- 순서: 공통 스타일/메뉴 → 홈과 진입점 → 업무/제출물 가독성 → 타입/빌드 및 실제 브라우저 확인.\n- 명칭: 파일 금고 → 프로젝트 자료, 증거 패키지 → 활동 보고서. URL/엔티티 명칭은 호환성을 위해 유지.\n- 최종 제출 기능 전체 재설계는 별도 단계. 이번 UI가 서버 미구현 기능을 구현한 것처럼 표시하지 않는다.\n`);
