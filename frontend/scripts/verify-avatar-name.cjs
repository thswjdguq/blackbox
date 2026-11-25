const fs=require('fs'),ts=require('typescript'),assert=require('assert/strict');
const m={exports:{}};
new Function('require','module','exports',ts.transpileModule(fs.readFileSync('src/lib/avatarName.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2017}}).outputText)(require,m,m.exports);
const {avatarName}=m.exports;

// 한글 이름: 성을 빼고 이름 앞 두 글자
assert.equal(avatarName('김민지'),'민지');
assert.equal(avatarName('최현우'),'현우');
assert.equal(avatarName('이호'),'호');
assert.equal(avatarName('김'),'김');
// 두 글자 성은 네 글자 이상일 때만. 세 글자는 한 글자 성으로 본다
assert.equal(avatarName('남궁민수'),'민수');
assert.equal(avatarName('제갈공명'),'공명');
assert.equal(avatarName('황보람'),'보람');
assert.equal(avatarName('선우진'),'우진');
// 성 뒤 이름이 세 글자 이상이면 앞 두 글자
assert.equal(avatarName('김아름별'),'아름');
// 공백은 무시
assert.equal(avatarName(' 김 민지 '),'민지');
// 한글이 아니면 예전처럼 앞 두 글자 대문자
assert.equal(avatarName('alex kim'),'AL');
assert.equal(avatarName('Kim민지'),'KI');
// 동그라미가 작은 곳은 한 글자
assert.equal(avatarName('김민지',1),'민');
// 빈 값
assert.equal(avatarName(''),'');
assert.equal(avatarName(null),'');
console.log('얼굴 이름 검사 통과: 성 빼기, 두 글자 성(네 글자 이상), 두 글자 이름, 공백, 한글 아님, 한 글자, 빈 값');
