const fs=require('fs'),ts=require('typescript'),assert=require('assert/strict'),React=require('react');
function nodes(t){return !t||typeof t!=='object'?[]:Array.isArray(t)?t.flatMap(nodes):[t,...nodes(t.props?.children)];}
function harness(initial,role='LEADER'){
  let cursor=0,state=[],effects=[],tree,remote=initial,cleanups=[],changed=0;const writes=[],dirty=[];
  let resolve,reject;
  const api={get:async()=>({data:remote}),put:(url,body)=>{writes.push({url,body});return new Promise((yes,no)=>{resolve=yes;reject=no;});},post:(url,body)=>{writes.push({url,body});return new Promise((yes,no)=>{resolve=yes;reject=no;});}};
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
  const confirmed={...ready,status:'CONFIRMED',canConfirm:false,candidate:null,confirmation:{reviewId:'r',roundNo:2,file,confirmedBy:{name:'민지'},confirmedAt:new Date().toISOString(),fileStillLatest:true}};
  const submit=harness(confirmed);await submit.settle();
  const form=submit.find(n=>n.type==='form');await form.props.onSubmit({preventDefault(){}});assert.equal(submit.writes.length,0,'학교 제출 확인 전 기록 차단');
  submit.find(n=>n.type==='textarea').props.onChange({target:{value:'접수 확인 메모'}});
  submit.find(n=>n.type==='input'&&n.props.type==='checkbox').props.onChange({target:{checked:true}});submit.render();
  const save=submit.find(n=>n.type==='form').props.onSubmit;const pending=save({preventDefault(){}});await new Promise(setImmediate);assert.equal(submit.writes.length,1);
  submit.fail();await pending;await new Promise(setImmediate);submit.render();assert.equal(submit.find(n=>n.type==='textarea').props.value,'접수 확인 메모');
  const observer=harness(confirmed,'OBSERVER');await observer.settle();assert.equal(observer.find(n=>n.type==='fieldset').props.disabled,true);
  await observer.find(n=>n.type==='form').props.onSubmit({preventDefault(){}});assert.equal(observer.writes.length,0);
  const late=harness(ready);await late.settle();late.find(n=>n.type==='input'&&n.props.type==='checkbox').props.onChange({target:{checked:true}});late.render();const lateWrite=late.find(n=>n.type==='button'&&n.props.children==='이 버전을 최종본으로 확정').props.onClick();late.unmount();late.success(confirmed);await lateWrite;assert.equal(late.changed(),0,'닫힌 화면의 늦은 응답 제외');
  // 확정 취소 (K-20 0.3)
  const text=n=>[n.props?.children].flat(Infinity).filter(v=>typeof v==='string'||typeof v==='number').join('');
  const cancelButton=t=>t.find(n=>n.type==='button'&&n.props.children==='확정 취소하기');
  assert.ok(!cancelButton(member),'확정 전에는 취소 버튼 없음');
  const memberConfirmed=harness(confirmed,'MEMBER');await memberConfirmed.settle();assert.ok(!cancelButton(memberConfirmed),'팀원에게 취소 버튼 없음');
  const submitted=harness({...confirmed,status:'SUBMITTED',submission:{channel:'LMS',submittedAt:new Date().toISOString(),note:null,recordedBy:{name:'민지'},recordedAt:new Date().toISOString()}});await submitted.settle();assert.ok(!cancelButton(submitted),'제출 기록 뒤 취소 버튼 없음');
  const c=harness(confirmed);await c.settle();cancelButton(c).props.onClick();c.render();
  const cancelForm=()=>c.find(n=>n.type==='form'&&n.props['aria-label']==='확정 취소');
  assert.ok(c.all().some(n=>text(n).includes('완료된 연결 업무는 그대로 완료입니다. 고치려면 업무를 재개하세요')),'완료 업무 안내');
  await cancelForm().props.onSubmit({preventDefault(){}});assert.equal(c.writes.length,0,'사유 없이 취소 차단');
  c.find(n=>n.type==='textarea'&&n.props['aria-label']==='확정 취소 사유').props.onChange({target:{value:'  표지 오타  '}});c.render();
  c.effects()[1]();assert.equal(c.dirty.at(-1),true,'사유 입력은 저장 전 입력');
  const firstCancel=cancelForm().props.onSubmit({preventDefault(){}});await cancelForm().props.onSubmit({preventDefault(){}});
  assert.equal(c.writes.length,1,'중복 클릭 한 번만');assert.deepEqual(c.writes[0],{url:'/projects/p/deliverables/d/confirmation/cancellation',body:{reviewId:'r',reason:'표지 오타'}});
  c.fail();await firstCancel;c.render();assert.ok(c.find(n=>n.props?.role==='alert'));
  assert.equal(c.find(n=>n.type==='textarea'&&n.props['aria-label']==='확정 취소 사유').props.value,'  표지 오타  ','실패 시 사유 유지');
  const cancelled={...ready,status:'DRAFT',canConfirm:false,candidate:null,checks:[{code:'APPROVED_FILE',passed:false,detail:'2회차의 확정을 취소했습니다. 고친 파일로 새 회차를 열어주세요'}],
    cancellations:[{reviewId:'r',roundNo:2,confirmedBy:{name:'민지'},confirmedAt:new Date().toISOString(),cancelledBy:{name:'민지'},cancelledAt:new Date().toISOString(),reason:'표지 오타'}]};
  c.remote(confirmed);await c.find(n=>n.type==='button'&&n.props.children==='정보 다시 확인').props.onClick();c.render();
  const retry=cancelForm().props.onSubmit({preventDefault(){}});await new Promise(setImmediate);assert.equal(c.writes.length,2);c.success(cancelled);await retry;c.render();
  assert.ok(!cancelForm()&&!cancelButton(c),'취소 뒤 취소 창 닫힘');assert.equal(c.changed(),1);
  assert.ok(c.all().some(n=>n.props?.role==='status'&&text(n).includes('2회차의 확정을 취소했습니다')),'취소 완료 안내');
  assert.ok(c.all().some(n=>n.type==='summary'&&text(n).includes('확정 취소 기록 1건')),'취소 기록 표시');
  assert.ok(c.all().some(n=>text(n).includes('사유: 표지 오타')));
  assert.ok(c.find(n=>n.type==='button'&&n.props.children==='이 버전을 최종본으로 확정'),'취소 뒤 확정 준비 목록으로 돌아감');
  // 이미 취소한 회차로 보낸 취소가 200 + 다른 회차의 새 확정을 받으면: 취소했다고 하지 않고 창과 사유를 둔다
  const s=harness(confirmed);await s.settle();cancelButton(s).props.onClick();s.render();
  s.find(n=>n.type==='textarea'&&n.props['aria-label']==='확정 취소 사유').props.onChange({target:{value:'오타'}});s.render();
  const stale=s.find(n=>n.type==='form'&&n.props['aria-label']==='확정 취소').props.onSubmit({preventDefault(){}});await new Promise(setImmediate);
  s.success({...confirmed,confirmation:{...confirmed.confirmation,reviewId:'r3',roundNo:3},cancellations:cancelled.cancellations});await stale;s.render();
  assert.ok(s.all().some(n=>n.props?.role==='status'&&text(n).includes('2회차 확정은 이미 취소됐고, 그 사이 3회차로 다시 확정됐습니다')),'다시 확정 안내');
  assert.ok(s.all().some(n=>n.type==='h3'&&text(n).includes('3회차 확정 취소')),'창은 새 확정 기준으로 남음');
  assert.equal(s.find(n=>n.type==='textarea'&&n.props['aria-label']==='확정 취소 사유').props.value,'오타','사유 유지');
  const old=harness({...confirmed,cancellations:undefined});await old.settle();assert.ok(!old.all().some(n=>n.type==='summary'),'0.3 이전 응답 호환');
  console.log('최종 제출 UI 검사 통과: 팀장 확정·동의 전 차단·중복 클릭·실패 입력 유지·실제 제출 확인·관찰자 쓰기 차단·확정 취소(팀장만·사유 필수·중복 클릭·실패 유지·그 사이 다시 확정·취소 기록)');
}
main().catch(e=>{console.error(e);process.exitCode=1;});
