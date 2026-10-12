const fs=require('fs'),path=require('path');
const root='C:/blackbox/teamplay-ui';
function edit(p,fn){const f=path.join(root,p);fs.writeFileSync(f,fn(fs.readFileSync(f,'utf8')).replace(/\r\n/g,'\n').trimEnd()+'\n');}
edit('frontend/src/app/projects/[projectId]/page.tsx',s=>s
 .replace('import { useIntegrationStatus } from "@/hooks/useIntegrationStatus";\n','')
 .replace(/const ONBOARDING_DISMISS_KEY[\s\S]*?(?=\/\/ ── 타입)/,'')
 .replace(/\/\/ ── 건강도 계산[\s\S]*?(?=\/\/ ── 멤버 아바타)/,'')
 .replace('  const health     = getHealth(alerts, tasks);\n','')
 .replaceAll('태스크','업무'));
edit('frontend/src/app/projects/[projectId]/board/page.tsx',s=>s.replaceAll('overflow-visible','overflow-hidden'));
for(const p of ['deliverables','meetings','vault']) edit(`frontend/src/app/projects/[projectId]/${p}/page.tsx`,s=>s);
edit('frontend/src/app/dashboard/page.tsx',s=>s
 .replace('  const [projects, setProjects]', '  const [query, setQuery] = useState("");\n  const [projects, setProjects]')
 .replace('        {/* Project Grid */}',`        <div className="mt-8 mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-base font-semibold">참여 중인 프로젝트 <span className="text-bb-text2 font-normal ml-1">{projects.length}</span></h2>
          <input aria-label="프로젝트 검색" value={query} onChange={e => setQuery(e.target.value)} placeholder="프로젝트 · 과목 검색" className="w-full sm:w-64 rounded-lg border border-bb-border bg-bb-surface px-3 py-2 text-sm placeholder:text-bb-text2" />
        </div>
        {!loading && !loadError && projects.length > 0 && !projects.some(p => (p.name + " " + (p.courseName ?? "")).toLowerCase().includes(query.trim().toLowerCase())) && <p role="status" className="py-8 text-sm text-bb-text2">검색 결과가 없습니다. 다른 이름으로 찾아보세요.</p>}
        {/* Project Grid */}`)
 .replace('{projects.map((project) => (','{projects.filter(p => (p.name + " " + (p.courseName ?? "")).toLowerCase().includes(query.trim().toLowerCase())).map((project) => ('));
edit('frontend/src/app/globals.css',s=>s.replaceAll('/* #FFFFFF */','/* workspace canvas */').replaceAll('/* #F8FAFC */','/* workspace surface */').replace('/* #6366F1  indigo-500 */','/* primary action */').replace('/* #4F46E5  indigo-600 */','/* primary hover */'));
