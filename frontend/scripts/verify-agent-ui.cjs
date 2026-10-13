const fs=require('fs'),path=require('path'),assert=require('assert/strict'),ts=require('typescript');
const React=require('react'),{renderToStaticMarkup}=require('react-dom/server');
const project='11111111-1111-4111-8111-111111111111',task='22222222-2222-4222-8222-222222222222';
function loader(mocks={}) {
  const cache=new Map();
  function load(name) {
    if(Object.hasOwn(mocks,name))return mocks[name];
    if(!name.startsWith('@/'))return require(name);
    const stem=path.resolve('src',name.slice(2));const file=[stem+'.ts',stem+'.tsx'].find(fs.existsSync);
    if(!file)throw Error('Missing module '+name);
    if(cache.has(file))return cache.get(file).exports;
    const module={exports:{}};cache.set(file,module);
    const code=ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true,target:ts.ScriptTarget.ES2022}}).outputText;
    const resolve=child=>child.startsWith('.')?load('@/'+path.relative(path.resolve('src'),path.resolve(path.dirname(file),child)).replaceAll('\\','/')):load(child);
    new Function('require','module','exports',code)(resolve,module,module.exports);return module.exports;
  }
  return load;
}
const run=loader({'@/lib/api':{default:{}}})('@/lib/agent/run');
const events=[];const feed=run.createEventParser(e=>events.push(e));
const wire=':start\r\n\r\nevent:step\r\ndata: {"label":"업무 조회","tool":"tasks"}\r\n\r\nevent: text\ndata: {"text":"메시 업무 확인"}\n\nevent:done\ndata: {"runId":"test","proposalIds":[]}\n\n';
for(const c of wire)feed(c);
assert.deepEqual(events.map(e=>e.event),['step','text','done']);assert.equal(events[1].data.text,'메시 업무 확인');
const {parseAnswer}=loader()('@/lib/agent/answer');
const link=href=>parseAnswer(`[업무](${href})`,project)[0].parts[0];
assert.equal(link(`/projects/${project}/board?task=${task}`).href,`/projects/${project}/board?task=${task}`);
for(const href of ['https://example.com','javascript:alert(1)',`/projects/${project}/../../profile`,`/projects/${task}/board`])assert.equal(typeof link(href),'string');

const status={available:true,unavailableReason:null,skills:[{skill:'ASK',label:'물어보기',allowed:true},{skill:'STATUS_BRIEF',label:'지금 상태 살펴보기',allowed:true}],canDecide:false,stages:[],pendingProposalCount:0};
let remote=status,release;const apiMocks={
 getAgentStatus:async()=>({data:remote}),getAgentOrigins:async()=>({data:{deliverableIds:[],requirementIds:[],taskIds:[]}}),getProposals:async()=>({data:[]})
};
const storeLoad=loader({'@/lib/api/agent':apiMocks,'@/lib/api/deliverable':{getDeliverables:async()=>({data:[]})},'@/lib/agent/run':{AgentRunError:run.AgentRunError,runAgent:async(_p,_s,_i,on)=>new Promise(resolve=>{release=()=>{on({event:'text',data:{text:'이전 계정 답'}});on({event:'done',data:{runId:'old',proposalIds:[]}});resolve();};})}});
const store=storeLoad('@/lib/store/agentStore').useAgentStore;
function renderComponent(module,props,state) {
  const load=loader({
    'react':{...React,useEffect:()=>{},useLayoutEffect:()=>{}},
    'next/navigation':{usePathname:()=>`/projects/${project}/board`},
    'next/link':{__esModule:true,default:({children,...p})=>React.createElement('a',p,children)},
    '@/lib/store/agentStore':{useAgentStore:selector=>selector(state)},
  });return renderToStaticMarkup(React.createElement(load(module).default,props));
}
async function main(){
  await store.getState().loadStatus(project,true);assert.equal(store.getState().status[project].available,true);
  const before=store.getState();
  let html=renderComponent('@/components/agent/AgentDock',{}, {...before,open:true,status:{},statusErrors:{[project]:'연결 확인 실패'}});
  assert.ok(html.includes('지금은 AI 실행을 사용할 수 없습니다'));assert.ok(!html.includes('id="agent-question"'));
  html=renderComponent('@/components/agent/AgentDock',{}, {...before,open:true});assert.ok(html.includes('id="agent-question"'));assert.ok(html.includes('지금 상태 살펴보기'));
  const p={id:'proposal',kind:'TASKS',status:'PENDING',title:'조사 업무',rationale:'필수 요구사항 연결',edited:false,requestedBy:{userId:task,name:'메시'},createdAt:'2026-10-13T00:00:00Z',decision:null,results:null,content:{deliverableId:task,tasks:[{title:'출처 조사',completionCriteria:'출처 3개',dueDate:null,requirementId:null}]}};
  html=renderComponent('@/components/agent/ProposalCard',{projectId:project,proposal:p,status,defaultOpen:true,onNavigate:()=>{}},before);
  assert.ok(!html.includes('채택 — 업무'));assert.ok(!html.includes('고치기'));assert.ok(html.includes('조사 업무'));
  const plan={...p,kind:'DELIVERABLE_PLAN',content:{deliverable:{title:'중간 발표',dueDate:null,description:null,submissionMethod:null},requirements:[],tasks:[]}};
  html=renderComponent('@/components/agent/ProposalCard',{projectId:project,proposal:plan,status:{...status,canDecide:true},defaultOpen:true,onNavigate:()=>{}},before);
  assert.ok(html.includes('기한을 채워야 채택할 수 있습니다'));assert.ok(/<button[^>]*disabled=""[^>]*>채택/.test(html));assert.ok(html.includes('고치기'));
  const pending=store.getState().run(project,'ASK',{question:'확인'},'확인');assert.equal(store.getState().runs[project][0].state,'running');
  storeLoad('@/lib/sessionCache').resetSessionCaches();release();await pending;
  assert.equal(store.getState().open,false);assert.deepEqual(store.getState().runs,{});assert.deepEqual(store.getState().status,{});
  console.log('AI UI 검증 통과: 한 글자씩 나뉜 SSE/CRLF, 내부 링크 경계, 미연결 안내, 활성 질문창, 관찰자 채택 차단, 계정 전환 후 늦은 실행 응답 차단');
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
