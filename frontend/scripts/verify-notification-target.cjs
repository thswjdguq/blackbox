const fs=require('fs'),ts=require('typescript'),assert=require('assert/strict');
const m={exports:{}};
const code=ts.transpileModule(fs.readFileSync('src/lib/notificationTarget.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2017}}).outputText;
new Function('require','module','exports',code)(require,m,m.exports);
const {notificationAction,notificationButton,samePath}=m.exports;
const P='p1';
const tgt=(o)=>({kind:'TASK',taskId:null,deliverableId:null,reviewId:null,messageId:null,commentId:null,fileId:null,...o});
const base={id:'n',type:'TASK_FILE_UPLOADED',projectId:P,targetAvailable:true,message:'민지님이 ‘자료 정리’ 업무의 결과 확인을 요청했습니다.',target:tgt({taskId:'t9',messageId:'m9',fileId:'f9'}),path:'/projects/p1/board?task=t9',
  taskId:'old',messageId:'old',fileId:'old',deliverableId:null,commentId:null};

// TASK: 그 자리에서 업무 창, 위치는 target의 값만(옛 칸 taskId·messageId·fileId는 쓰지 않음)
assert.deepEqual(notificationAction(base,P),{kind:'task',taskId:'t9',messageId:'m9',fileId:'f9'});
assert.equal(notificationButton(base).open,'관련 업무 열기');
assert.deepEqual(notificationAction({...base,targetAvailable:false},P),{kind:'none'});
// DELIVERABLE_REVIEW: path로. #comment-가 path에 있으면 그대로, 없으면 commentId로 한 번만
const r={...base,target:tgt({kind:'DELIVERABLE_REVIEW',deliverableId:'d1',commentId:'c1'}),path:'/projects/p1/deliverables/d1?tab=review',deliverableId:'other'};
assert.deepEqual(notificationAction(r,P),{kind:'path',href:'/projects/p1/deliverables/d1?tab=review#comment-c1'});
assert.deepEqual(notificationAction({...r,path:'/projects/p1/deliverables/d1?tab=review#comment-c1'},P),{kind:'path',href:'/projects/p1/deliverables/d1?tab=review#comment-c1'});
assert.equal(notificationButton(r).open,'검토 열기');assert.equal(notificationButton(r).gone,'삭제된 대상입니다.');
// path가 없으면 옛 칸(deliverableId)으로 이동을 만들지 않는다
assert.deepEqual(notificationAction({...r,path:null},P),{kind:'none'});
// 모르는 kind: 같은 프로젝트의 path면 이동, 없으면 표시만
assert.deepEqual(notificationAction({...base,target:tgt({kind:'NEW_KIND'}),path:'/projects/p1/meetings'},P),{kind:'path',href:'/projects/p1/meetings'});
assert.deepEqual(notificationAction({...base,target:tgt({kind:'UNKNOWN'}),path:null},P),{kind:'none'});
// 다른 프로젝트·다른 사이트·이상한 경로는 이동하지 않는다
for(const bad of ['/projects/p2/board','https://evil.example/projects/p1/x','//evil.example','/projects/p1//x','/projects/p1/\\x','/projects/p1/x\n','/dashboard'])assert.equal(samePath(bad,P),false,bad);
assert.deepEqual(notificationAction({...r,path:'/projects/p2/deliverables/d1?tab=review'},P),{kind:'none'});
// dot segment: 원문은 /projects/p1/로 시작해도 정규화하면 다른 프로젝트·대시보드다(B #202 리뷰 재현 세 경우와 변형)
for(const bad of ['/projects/p1/../p2/board','/projects/p1/%2e%2e/p2/board','/projects/p1/%2E%2e/p2/board','/projects/p1/.%2e/p2/board','/projects/p1/../../dashboard','/projects/p1/./board','/projects/p1/board/..','/projects/p1/board/%2e%2e?tab=review'])
  {assert.equal(samePath(bad,P),false,bad);assert.deepEqual(notificationAction({...r,path:bad},P),{kind:'none'},bad);}
// 정상 경로의 query·anchor는 그대로, 점이 경로 칸 전체가 아니면(파일 이름 등) 막지 않는다
for(const ok of ['/projects/p1/deliverables/d1?tab=review#comment-c1','/projects/p1/board?task=t9','/projects/p1/vault/report.v2.pdf','/projects/p1/board?next=../x'])
  assert.equal(samePath(ok,P),true,ok);
// 필수 칸이 빠지거나 모양이 틀린 응답: 오류 없이 이동을 막고, 옛 칸(taskId)으로 대신 열지 않는다
const {notificationBroken,BROKEN_NOTICE}=m.exports;
const broken=[{...base,target:null},{...base,target:undefined},{...base,target:{taskId:'t9'}},{...base,message:undefined},{...base,message:'  '},{...base,path:undefined},{...base,path:5}];
for(const b of broken){assert.equal(notificationBroken(b),true,JSON.stringify(b));assert.deepEqual(notificationAction(b,P),{kind:'none'});assert.equal(notificationButton(b).gone,'');}
assert.equal(notificationBroken(base),false);assert.equal(notificationBroken({...r,path:null}),false);
assert.ok(BROKEN_NOTICE.includes('새로고침'));
console.log('알림 대상 검사 통과: 서버 target·path만 따름, #comment 한 번만, 모르는 kind, 다른 프로젝트·사이트 경로 막기, 빠진 칸은 이동 막기');
