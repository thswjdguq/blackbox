const fs=require('fs'),ts=require('typescript'),assert=require('assert/strict'),React=require('react');
const nodes=t=>!t||typeof t!=='object'?[]:Array.isArray(t)?t.flatMap(nodes):[t,...nodes(t.props?.children)];
const text=t=>t==null?'':Array.isArray(t)?t.map(text).join(''):typeof t==='object'?text(t.props?.children):String(t);
let state=[],cursor=0,effects=[],routes=[],calls=[];
const hooks={...React,useState:v=>{const i=cursor++;if(!(i in state))state[i]=v;return[state[i],v=>{state[i]=typeof v==='function'?v(state[i]):v;}];},useEffect:f=>effects.push(f)};
const item={kind:'REVIEW',taskId:null,taskTitle:'최종 보고서',messageId:'comment',body:'근거 보완',fromName:'팀원',createdAt:'2026-10-19T00:00:00Z',status:'RESOLVED',response:'3쪽에 출처 추가',deliverableId:'deliverable',reviewId:'review'};
const utility={exports:{}};
new Function('exports',ts.transpileModule(fs.readFileSync('src/lib/feedback.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS}}).outputText)(utility.exports);
for(const [kind,status,expected] of [['OPINION','ANSWERED',true],['REVIEW','RESOLVED',true],['COMMENT','RESPONDED',false],['OPINION','RESPONDED',false],['REVIEW','REFLECTION_PENDING',false],['REVIEW','OPEN',false],['REVISION',undefined,false],['COMPLETION',undefined,false]]) assert.equal(utility.exports.isFeedbackFinished({kind,status}),expected,`${kind}/${status}`);
const mocks={react:hooks,'next/navigation':{useParams:()=>({projectId:'p'}),useRouter:()=>({push:u=>routes.push(u)})},'next/link':'a',
 '@/components/Sidebar':()=>null,'@/components/project/ProjectNav':()=>null,'@/components/kanban/TaskModal':()=>null,'@/lib/feedback':utility.exports,
 '@/hooks/useFeedbackInbox':{useFeedbackInbox:()=>({data:{received:[item],sent:[item],revisions:[]},refresh(){}})},
 '@/lib/api':{get:async u=>{calls.push(u);return {data:u==='/auth/profile'?{id:'me'}:[{userId:'me',role:'MEMBER'}]};}},'@/lib/apiError':{apiError:()=>''}};
const m={exports:{}};
const code=ts.transpileModule(fs.readFileSync('src/app/projects/[projectId]/feedback/page.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText;
new Function('require','module','exports',code)(n=>mocks[n]??require(n),m,m.exports);
function render(){cursor=0;effects=[];return m.exports.default();}
async function main(){
 render();effects.forEach(f=>f());await new Promise(setImmediate);
 let tree=render();assert.ok(text(tree).includes('최종 제출물 피드백'));assert.ok(text(tree).includes('3쪽에 출처 추가'));
 const archive=nodes(tree).find(n=>n.type==='details');assert.ok(archive);assert.ok(!archive.props.open);assert.ok(text(archive).includes('완료된 피드백 1'));
 assert.ok(!text(nodes(tree).find(n=>n.type==='section')).includes('최종 보고서'));
 const card=nodes(tree).find(n=>n.type==='button'&&text(n).includes('최종 보고서'));
 assert.equal(card.props.disabled,false);await card.props.onClick();
 assert.equal(routes[0],'/projects/p/deliverables/deliverable?tab=review#comment-comment');
 assert.ok(!calls.some(u=>u.includes('/tasks/null')));
 nodes(tree).find(n=>n.props?.role==='tab'&&text(n).includes('보낸 피드백')).props.onClick();tree=render();
 assert.ok(text(tree).includes('반영 기록 있음'));assert.ok(text(tree).includes('3쪽에 출처 추가'));
 console.log('PASS: 완료 기록 접힘·진행 영역 분리·답글과 반영 구분·받은/보낸 기록·원문 이동');
}
main().catch(e=>{console.error(e);process.exitCode=1;});
