// main에 이미 배포된 migration의 파일명·내용을 원본 Git blob과 비교한다.
const {execFileSync}=require('node:child_process');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const base='960934adda04de32da4a7570e80a606b146312d8';
const prefix='backend/src/main/resources/db/migration/';
const git=(args)=>execFileSync('git',args,{cwd:root});
const files=git(['ls-tree','-r','--name-only',base,'--',prefix]).toString().trim().split('\n');
for(const file of files){
  const expected=git(['show',`${base}:${file}`]).toString().replace(/\r\n/g,'\n');
  const actual=fs.readFileSync(path.join(root,file),'utf8').replace(/\r\n/g,'\n');
  assert.equal(actual,expected,`이미 적용된 main migration 변경: ${file}`);
}
const versions=fs.readdirSync(path.join(root,prefix)).filter(f=>/^V.*\.sql$/.test(f)).map(f=>f.split('__')[0].slice(1).replaceAll('_','.'));
assert.ok(versions.every(v=>/^[1-9][0-9]*$/.test(v)),'main 통합본에는 정수 migration 번호만 허용');
assert.equal(new Set(versions).size,versions.length,'migration 번호 중복');
assert.ok(!fs.existsSync(path.join(root,prefix,'V29__agent_proposals.sql')),'중복 AI 생성 SQL');
console.log(`PASS: main V1~V25 ${files.length}개 파일 보존, ${versions.length}개 migration 번호 중복 없음`);
