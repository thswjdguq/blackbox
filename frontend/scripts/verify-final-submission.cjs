const fs=require('fs'),ts=require('typescript'),assert=require('assert/strict'),React=require('react');
function nodes(t){return !t||typeof t!=='object'?[]:Array.isArray(t)?t.flatMap(nodes):[t,...nodes(t.props?.children)];}
function harness(initial,role='LEADER'){
  let cursor=0,state=[],effects=[],tree,remote=initial,cleanups=[],changed=0;const writes=[],dirty=[];
  let resolve,reject;
  const api={get:async()=>({data:remote}),put:(url,body)=>{writes.push({url,body});return new Promise((yes,no)=>{resolve=yes;reject=no;});}};
  const hooks={...React,useState:v=>{const i=cursor++;if(!(i in state))state[i]=typeof v==='function'?v():v;return[state[i],v=>state[i]=typeof v==='function'?v(state[i]):v];},useRef:v=>{const i=cursor++;if(!(i in state))state[i]={current:v};return state[i];},useEffect:f=>effects.push(f),useCallback:f=>f};
  const m={exports:{}};const code=ts.transpileModule(fs.readFileSync('src/components/deliverable/FinalSubmissionPanel.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText;
  new Function('require','module','exports',code)(n=>({react:hooks,'@/lib/api':api,'@/lib/apiError':{apiError:()=> '저장 실패'},'@/lib/sessionCache':{sessionGeneration:()=>0},'@/components/FileDownloadLink':{__esModule:true,default:()=>null}}[n]??require(n)),m,m.exports);
  const props={projectId:'p',deliverableId:'d',isLeader:role==='LEADER',canWrite:role!=='OBSERVER',submissionMethod:null,onChanged(){changed++;},onReview(){},onOverview(){},onDirty:v=>dirty.push(v)};
  const render=()=>{cursor=0;effects=[];tree=m.exports.default(props);};
  render();const settle=async()=>{cleanups=effects.map(f=>f()).filter(f=>typeof f==='function');await new Promise(setImmediate);render();};
  return{render,settle,writes,dirty,effects:()=>effects,remote:v=>remote=v,unmount:()=>cleanups.forEach(f=>f()),changed:()=>changed,all:()=>nodes(tree),find:p=>nodes(tree).find(p),success:v=>resolve({data:v}),fail:()=>reject(Error('offline'))};
}
global.window={addEventListener(){},removeEventListener(){},confirm:()=>true};global.document={addEventListener(){},removeEventListener(){}};
async function main(){
  const file={fileId:'f',fileName:'최종.txt',version:3,uploaderName:'민지',shortHash:'abcdefg'};
  const ready={status:'IN_REVIEW',canConfirm:true,soleContributor:false,checks:[],candidate:{reviewId:'r',file},confirmation:null,submission:null};
  const h=harness(ready);await h.settle();
  let button=h.find(n=>n.type==='button'&&n.props.children==='이 버전을 최종본으로 확정');assert.equal(button.props.disabled,true);await button.props.onClick();assert.equal(h.writes.length,0);
  h.find(n=>n.type==='input'&&n.props.type==='checkbox').props.onChange({target:{checked:true}});h.render();
  button=h.find(n=>n.type==='button'&&n.props.children==='이 버전을 최종본으로 확정');const first=button.props.onClick();await button.props.onClick();assert.equal(h.writes.length,1);
  h.fail();await first;h.render();assert.ok(h.find(n=>n.props?.role==='alert'));assert.equal(h.find(n=>n.type==='input'&&n.props.type==='checkbox').props.checked,true,'확정 실패 입력 유지');
  h.remote({...ready,candidate:{...ready.candidate,reviewId:'r2',file:{...file,fileId:'f2'}}});await h.find(n=>n.type==='button'&&n.props.children==='정보 다시 확인').props.onClick();h.render();h.effects()[0]();h.render();
  assert.equal(h.find(n=>n.type==='input'&&n.props.type==='checkbox').props.checked,false,'후보 변경 후 재확인 필수');
  const member=harness(ready,'MEMBER');await member.settle();assert.ok(!member.find(n=>n.type==='button'&&n.props.children==='이 버전을 최종본으로 확정'));
  const confirmed={...ready,status:'CONFIRMED',canConfirm:false,candidate:null,confirmation:{file,confirmedBy:{name:'민지'},confirmedAt:new Date().toISOString(),fileStillLatest:true}};
  const submit=harness(confirmed);await submit.settle();
  const form=submit.find(n=>n.type==='form');await form.props.onSubmit({preventDefault(){}});assert.equal(submit.writes.length,0,'학교 제출 확인 전 기록 차단');
  submit.find(n=>n.type==='textarea').props.onChange({target:{value:'접수 확인 메모'}});
  submit.find(n=>n.type==='input'&&n.props.type==='checkbox').props.onChange({target:{checked:true}});submit.render();
  const save=submit.find(n=>n.type==='form').props.onSubmit;const pending=save({preventDefault(){}});await new Promise(setImmediate);assert.equal(submit.writes.length,1);
  submit.fail();await pending;await new Promise(setImmediate);submit.render();assert.equal(submit.find(n=>n.type==='textarea').props.value,'접수 확인 메모');
  const observer=harness(confirmed,'OBSERVER');await observer.settle();assert.equal(observer.find(n=>n.type==='fieldset').props.disabled,true);
  await observer.find(n=>n.type==='form').props.onSubmit({preventDefault(){}});assert.equal(observer.writes.length,0);
  const late=harness(ready);await late.settle();late.find(n=>n.type==='input'&&n.props.type==='checkbox').props.onChange({target:{checked:true}});late.render();const lateWrite=late.find(n=>n.type==='button'&&n.props.children==='이 버전을 최종본으로 확정').props.onClick();late.unmount();late.success(confirmed);await lateWrite;assert.equal(late.changed(),0,'닫힌 화면의 늦은 응답 제외');
  console.log('최종 제출 UI 검사 통과: 팀장 확정·동의 전 차단·중복 클릭·실패 입력 유지·실제 제출 확인·관찰자 쓰기 차단');
}
main().catch(e=>{console.error(e);process.exitCode=1;});
