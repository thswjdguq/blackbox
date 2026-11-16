const fs=require('fs'),ts=require('typescript'),assert=require('assert/strict');
const m={exports:{}};
const code=ts.transpileModule(fs.readFileSync('src/lib/notificationTarget.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2017}}).outputText;
new Function('require','module','exports',code)(n=>n==='@/types/notification'?{notificationLabels:{TASK_FILE_UPLOADED:'결과 파일 등록'}}:require(n),m,m.exports);
const {notificationAction,notificationText,notificationButton,samePath}=m.exports;
const P='p1';
const base={id:'n',type:'TASK_FILE_UPLOADED',projectId:P,actorName:'민지',taskId:'t1',taskTitle:'자료 정리',fileId:'f1',fileName:'a.txt',fileVersion:1,messageId:'m1',createdAt:'',readAt:null,targetAvailable:true};
const tgt=(o)=>({kind:'TASK',taskId:null,deliverableId:null,reviewId:null,messageId:null,commentId:null,fileId:null,...o});

// 옛 응답(새 칸 없음): 지금까지의 규칙 그대로
assert.deepEqual(notificationAction(base,P),{kind:'task',taskId:'t1',messageId:'m1',fileId:'f1'});
assert.deepEqual(notificationAction({...base,deliverableId:'d1',commentId:'c1'},P),{kind:'path',href:'/projects/p1/deliverables/d1?tab=review#comment-c1'});
assert.equal(notificationText(base),'민지님 · 결과 파일 등록');
assert.equal(notificationButton(base).open,'관련 업무 열기');
assert.deepEqual(notificationAction({...base,targetAvailable:false},P),{kind:'none'});

// 새 응답: TASK는 그 자리에서 업무 창, target의 위치를 쓴다
const t1={...base,message:'민지님이 ‘자료 정리’ 업무의 결과 확인을 요청했습니다.',target:tgt({taskId:'t9',messageId:'m9',fileId:null}),path:'/projects/p1/board?task=t9'};
assert.deepEqual(notificationAction(t1,P),{kind:'task',taskId:'t9',messageId:'m9',fileId:'f1'});
assert.equal(notificationText(t1),t1.message);
// DELIVERABLE_REVIEW는 path로. #comment-가 path에 있으면 그대로, 없으면 commentId로 한 번만 붙인다
const r={...base,target:tgt({kind:'DELIVERABLE_REVIEW',deliverableId:'d1',commentId:'c1'}),path:'/projects/p1/deliverables/d1?tab=review'};
assert.deepEqual(notificationAction(r,P),{kind:'path',href:'/projects/p1/deliverables/d1?tab=review#comment-c1'});
assert.deepEqual(notificationAction({...r,path:'/projects/p1/deliverables/d1?tab=review#comment-c1'},P),{kind:'path',href:'/projects/p1/deliverables/d1?tab=review#comment-c1'});
assert.equal(notificationButton(r).open,'검토 열기');assert.equal(notificationButton(r).gone,'삭제된 대상입니다.');
// 모르는 kind: 같은 프로젝트의 path면 이동, path가 없으면 표시만
assert.deepEqual(notificationAction({...base,target:tgt({kind:'NEW_KIND'}),path:'/projects/p1/meetings'},P),{kind:'path',href:'/projects/p1/meetings'});
assert.deepEqual(notificationAction({...base,target:tgt({kind:'UNKNOWN'}),path:null},P),{kind:'none'});
// 다른 프로젝트·다른 사이트·이상한 경로는 이동하지 않는다
for(const bad of ['/projects/p2/board','https://evil.example/projects/p1/x','//evil.example','/projects/p1//x','/projects/p1/\\x','/projects/p1/x\n','/dashboard'])assert.equal(samePath(bad,P),false,bad);
assert.deepEqual(notificationAction({...r,path:'/projects/p2/deliverables/d1?tab=review'},P),{kind:'none'});
console.log('알림 대상 검사 통과: 옛 응답 그대로, 서버 message·target·path, #comment 한 번만, 모르는 kind, 다른 프로젝트·사이트 경로 막기');
