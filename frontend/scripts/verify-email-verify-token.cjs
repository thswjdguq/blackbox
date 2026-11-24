const fs=require('fs'),ts=require('typescript'),assert=require('assert/strict');
const m={exports:{}};
const code=ts.transpileModule(fs.readFileSync('src/lib/emailVerifyToken.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2017}}).outputText;
new Function('require','module','exports',code)(require,m,m.exports);
const {tokenFromHash,stashToken,clearToken,VERIFY_PATH}=m.exports;
const store=()=>{const d=new Map();return {getItem:k=>d.has(k)?d.get(k):null,setItem:(k,v)=>d.set(k,String(v)),removeItem:k=>d.delete(k),d};};

// fragment에서만 토큰을 읽는다
assert.equal(tokenFromHash('#token=abc.DEF-123_x'),'abc.DEF-123_x');
assert.equal(tokenFromHash('token=abc'),'abc');
for(const bad of ['','#','#tok=abc','#token=','#token=a%20b','#token='+'x'.repeat(513)])assert.equal(tokenFromHash(bad),null,bad);

// 주소의 토큰을 탭 저장소로 옮기고 주소에서 fragment를 지운다(토큰 없는 /email/verify로)
const s=store();let replaced=null;
assert.equal(stashToken({hash:'#token=t1'},s,u=>{replaced=u;}),'t1');
assert.equal(replaced,VERIFY_PATH);assert.equal(VERIFY_PATH,'/email/verify');assert.ok(!replaced.includes('t1'));
// 로그인 뒤 돌아온 화면(주소에 토큰 없음)은 저장소의 토큰을 쓰고 주소는 건드리지 않는다
replaced=null;assert.equal(stashToken({hash:''},s,u=>{replaced=u;}),'t1');assert.equal(replaced,null);
// 새 링크가 오면 새 토큰으로 바꾼다
assert.equal(stashToken({hash:'#token=t2'},s,()=>{}),'t2');assert.equal(stashToken({hash:''},s,()=>{}),'t2');
// 결과가 정해지면 지운다
clearToken(s);assert.equal(stashToken({hash:''},s,()=>{}),null);
// 저장소를 못 써도(사생활 보호 모드 등) 이번 화면에서는 주소의 토큰을 쓴다
const broken={getItem(){throw new Error('x');},setItem(){throw new Error('x');},removeItem(){throw new Error('x');}};
assert.equal(stashToken({hash:'#token=t3'},broken,()=>{}),'t3');assert.equal(stashToken({hash:''},broken,()=>{}),null);clearToken(broken);
// B 리뷰 2: 잘못된 새 링크면 이전 탭 토큰을 대신 쓰지 않고, 주소의 token 칸은 유효 여부와 상관없이 지운다
const s2=store();stashToken({hash:'#token=old-ok'},s2,()=>{});
for(const bad of ['#token=invalid%20value','#token=','#token='+'y'.repeat(600)]){
  stashToken({hash:'#token=old-ok'},s2,()=>{});let rep=null;
  assert.equal(stashToken({hash:bad},s2,u=>{rep=u;}),null,bad);assert.equal(rep,VERIFY_PATH,bad);
  assert.equal(stashToken({hash:''},s2,()=>{}),null,'이전 토큰도 지움 '+bad);
}
// token 칸이 없는 주소(로그인 뒤 돌아옴)에서만 저장해 둔 토큰을 쓴다
stashToken({hash:'#token=keep'},s2,()=>{});assert.equal(stashToken({hash:'#other=1'},s2,()=>{}),'keep');
// B 리뷰 3: 저장소 자체가 없을 때(null) — 새 링크 토큰은 이번 화면에서 쓰고, 돌아온 화면은 null
assert.equal(stashToken({hash:'#token=t4'},null,()=>{}),'t4');assert.equal(stashToken({hash:''},null,()=>{}),null);clearToken(null);
// 주소 바꾸기가 실패해도 토큰 판단은 그대로
assert.equal(stashToken({hash:'#token=t5'},store(),()=>{throw new Error('replace');}),'t5');
// 실제 window.location처럼 replace 뒤 hash가 바뀌어도 받아 둔 토큰을 쓴다
{const live={hash:'#token=live-1'};const s3=store();assert.equal(stashToken(live,s3,()=>{live.hash='';}),'live-1');assert.equal(stashToken({hash:''},s3,()=>{}),'live-1');}
console.log('이메일 확인 토큰 검사 통과: fragment만, 탭 저장소로 옮기고 주소에서 지움, 로그인 뒤 재사용, 결과 뒤 삭제, 저장소 실패, 잘못된 새 링크는 옛 토큰을 쓰지 않음, 저장소 없음');
