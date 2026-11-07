const fs=require('fs'),ts=require('typescript'),assert=require('assert/strict');
const React=require('react'),{renderToStaticMarkup}=require('react-dom/server');
const source=fs.readFileSync('src/components/kanban/TaskCompletionPanel.tsx','utf8');
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText;
const old={id:'task',projectId:'project',status:'IN_PROGRESS',completionRequestedAt:null};
function render(task,completion) {
  let index=0; const state=[completion,'',false,false,'']; const module={exports:{}};
  const mockedRequire=name=>name==='react'?{...React,useState:()=>[state[index++],()=>{}],useEffect:()=>{},useCallback:f=>f,useRef:v=>({current:v})}:
    name==='@/hooks/useMyRole'?{useMyRole:()=> 'LEADER'}:name==='@/lib/api'?{default:{}}:name==='@/lib/apiError'?{apiError:()=>''}:require(name);
  new Function('require','module','exports',compiled)(mockedRequire,module,module.exports);
  return renderToStaticMarkup(React.createElement(module.exports.default,{task,disabled:false,onChanged:()=>{},onBusy:()=>{}}));
}
const pending={...old,completionRequestedAt:'2026-10-12T12:00:00Z',completionRequestedByName:'메시'};
const view={task:pending,checks:[],canRequest:false,canApprove:true,canReview:true};
const fresh=render(old,view);
assert.ok(fresh.includes('확인하고 완료'),'오래된 보드 업무에서도 최신 요청의 확인 버튼을 표시해야 함');
assert.ok(fresh.includes('메시'));assert.ok(!fresh.includes('결과 제출하고 확인 요청'));
const done=render(pending,{...view,task:{...pending,status:'DONE',completionRequestedAt:null,completionApprovedAt:'now',completionApprovedByName:'호날두'},canReview:false,canApprove:false});
assert.ok(done.includes('호날두'));assert.ok(!done.includes('확인하고 완료'));
const self=render(old,{...view,canReview:false,canApprove:false});
assert.ok(self.includes('본인 결과를 완료 처리할 수 없습니다'));assert.ok(!self.includes('확인하고 완료'));
console.log('완료 확인 화면 회귀 통과: 오래된 업무 + 최신 요청, 최신 완료, 본인 확인 차단');
