const fs=require('fs'),ts=require('typescript'),assert=require('assert/strict');
const m={exports:{}};
new Function('require','module','exports',ts.transpileModule(fs.readFileSync('src/lib/loginRedirect.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2017}}).outputText)(require,m,m.exports);
const {safeNext,loginHref,afterLogin}=m.exports;

// 같은 사이트의 경로만(이메일 링크의 두 모양)
assert.equal(safeNext('/projects/p1/board?task=t1'),'/projects/p1/board?task=t1');
assert.equal(safeNext('/projects/p1/deliverables/d1?tab=review'),'/projects/p1/deliverables/d1?tab=review');
// 다른 사이트로 보내는 모양은 버린다
for(const bad of ['//evil.example','/\\evil.example','https://evil.example/x','http:/evil','javascript:alert(1)','evil','', null, undefined,'/x\nSet-Cookie:a','/'+'a'.repeat(2001)])assert.equal(safeNext(bad),null,String(bad));
// 로그인·가입·루트로 돌아가지 않는다
for(const loop of ['/','/login','/login?next=/x','/signup','/signup/x'])assert.equal(safeNext(loop),null,loop);
assert.equal(safeNext('/loginx'),'/loginx','접두어만 같은 다른 경로는 허용');

// 로그인 주소: 지금 주소를 next에 담는다
assert.equal(loginHref('/projects/p1/board?task=t1'),'/login?next=%2Fprojects%2Fp1%2Fboard%3Ftask%3Dt1');
assert.equal(loginHref('/login'),'/login');assert.equal(loginHref('/'),'/login');
// 로그인 뒤: 안전한 next, 아니면 대시보드
assert.equal(afterLogin('?next=%2Fprojects%2Fp1%2Fboard%3Ftask%3Dt1'),'/projects/p1/board?task=t1');
for(const q of ['','?next=','?next=%2F%2Fevil.example','?next=https%3A%2F%2Fevil.example','?next=%2Flogin'])assert.equal(afterLogin(q),'/dashboard',q);
console.log('로그인 뒤 돌아가기 검사 통과: 같은 사이트 경로만, 다른 사이트·로그인 화면 거르기, next 담기·꺼내기');
