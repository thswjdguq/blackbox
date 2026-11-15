const fs=require('fs'),ts=require('typescript'),assert=require('assert/strict');
const m={exports:{}};
new Function('require','module','exports',ts.transpileModule(fs.readFileSync('src/lib/briefText.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2017}}).outputText)(require,m,m.exports);
const {normalizeBrief,briefLength,defaultBriefTitle,problem}=m.exports;

// K-31 2.5: NFC, 줄바꿈 \n, 앞뒤의 유니코드 공백(전각 공백, 줄바꿈 없는 공백)과 BOM 제거. 줄 안은 그대로
const nfd='중간 보고서'.normalize('NFD');
assert.notEqual(nfd.length,'중간 보고서'.length);
assert.equal(normalizeBrief(`﻿　 ${nfd}\r\n둘째  줄\r셋째\n\n　`),'중간 보고서\n둘째  줄\n셋째');
assert.equal(normalizeBrief(' \t\n '),'');
assert.equal(briefLength(`${nfd}\r\n`),'중간 보고서'.length,'서버와 같은 글자 수(풀어쓴 자모를 더 세지 않음)');
assert.equal(normalizeBrief('줄 안의  공백과\n\n빈 줄은 그대로'),'줄 안의  공백과\n\n빈 줄은 그대로');

// 기본 제목: 첫 줄, 60자를 넘으면 앞 60자 + "…"
assert.equal(defaultBriefTitle('　[캡스톤] 중간 보고서 안내\n본문'),'[캡스톤] 중간 보고서 안내');
const long='가'.repeat(61);assert.equal(defaultBriefTitle(long),'가'.repeat(60)+'…');assert.equal(defaultBriefTitle('가'.repeat(60)),'가'.repeat(60));

// ProblemDetail에서 code와 확장 칸
const p=problem({response:{status:409,data:{detail:'먼저 고쳤습니다',code:'BRIEF_CHANGED',brief:{id:'b',version:4}}}});
assert.equal(p.status,409);assert.equal(p.code,'BRIEF_CHANGED');assert.equal(p.brief.version,4);assert.equal(p.detail,'먼저 고쳤습니다');
assert.equal(problem({response:{status:500,data:'oops'}}).code,undefined);assert.equal(problem(new Error('네트워크')),null);
console.log('안내문 글 검사 통과: 정규화(NFC·줄바꿈·앞뒤 공백·BOM), 서버 기준 글자 수, 기본 제목, 오류 code');
