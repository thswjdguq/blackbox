const fs=require('fs'),ts=require('typescript'),assert=require('assert/strict');
const m={exports:{}};
new Function('require','module','exports',ts.transpileModule(fs.readFileSync('src/lib/scoreItems.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2017}}).outputText)(require,m,m.exports);
const {levelItems,extraItems,reviewItem,columns,countText,sortScores}=m.exports;
const base={userId:'u1',name:'최현우',email:'',taskParticipated:true,meetingParticipated:false,fileParticipated:true,actionParticipated:false,participationLevel:'PARTIAL',calculatedAt:'2026-11-15T07:00:00.000000Z'};
const item=(code,count,extra={})=>({code,label:code,description:code+' 설명',count,passed:count>0,countsTowardLevel:true,...extra});
const items=[item('TASK_DONE',3),item('MEETING_ATTENDANCE',1,{total:4,passed:false}),item('FILE_UPLOAD',2),item('ACTION_ITEM',0),
  item('REVIEW_ACTIVITY',5,{countsTowardLevel:false,details:[{code:'REVIEW_DECISION',label:'회차 결정',count:1}]})];

// items가 없는 옛 응답: 네 불리언으로 네 칸, 횟수 없음
const legacy=levelItems({...base,items:null});
assert.deepEqual(legacy.map(i=>i.code),['TASK_DONE','MEETING_ATTENDANCE','FILE_UPLOAD','ACTION_ITEM']);
assert.deepEqual(legacy.map(i=>i.passed),[true,false,true,false]);
assert.ok(legacy.every(i=>countText(i)===null),'옛 응답은 횟수 글 없음');
assert.deepEqual(extraItems({...base}),[]);assert.equal(reviewItem({...base,items:undefined}),null);

// 새 응답: 판정 항목 넷, 판정 밖 하나(검토 참여)
const now={...base,items};
assert.deepEqual(levelItems(now).map(i=>i.code),['TASK_DONE','MEETING_ATTENDANCE','FILE_UPLOAD','ACTION_ITEM']);
assert.deepEqual(extraItems(now).map(i=>i.code),['REVIEW_ACTIVITY']);
assert.equal(reviewItem(now).count,5);

// 횟수 글: 회의는 n/total, 회의가 없으면 "회의 없음", 나머지는 n건
assert.equal(countText(items[1]),'1/4');assert.equal(countText({count:0,total:0}),'회의 없음');assert.equal(countText(items[0]),'3건');

// 모르는 code: 버리지 않고 countsTowardLevel로 자리를 나눈다(K-40 2장)
const future={...base,userId:'u2',name:'김민지',items:[...items,item('PRESENTATION',1),item('PEER_NOTE',2,{countsTowardLevel:false})]};
assert.ok(levelItems(future).some(i=>i.code==='PRESENTATION'));assert.ok(extraItems(future).some(i=>i.code==='PEER_NOTE'));

// 칸: 행마다 항목이 달라도(계산 시점 차이) 합쳐서 빠지지 않는다
const cols=columns([{...base,items:null},now,future],levelItems).map(c=>c.code);
assert.deepEqual(cols,['TASK_DONE','MEETING_ATTENDANCE','FILE_UPLOAD','ACTION_ITEM','PRESENTATION']);
assert.equal(columns([{...base,items:null},now],levelItems)[0].description,'담당 업무 1개 이상 완료','먼저 나온 행의 설명');
assert.deepEqual(columns([now,future],extraItems).map(c=>c.code),['REVIEW_ACTIVITY','PEER_NOTE']);

// 순서: 받은 순서와 관계없이 이름순, 같은 이름은 id순
const a={...base,userId:'b',name:'박유나'},b={...base,userId:'a',name:'박유나'},c={...base,userId:'c',name:'김민지'};
assert.deepEqual(sortScores([a,c,b]).map(s=>s.userId),['c','a','b']);
assert.deepEqual(sortScores([b,a,c]).map(s=>s.userId),['c','a','b']);
console.log('기여도 항목 검사 통과: 옛 응답 네 칸, 판정 안팎 항목, 회의 횟수 글, 모르는 code, 칸 합치기, 이름순');
