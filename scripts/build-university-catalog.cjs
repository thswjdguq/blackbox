// Official download JSON pages -> compact, deterministic offline catalog.
// Usage: node scripts/build-university-catalog.cjs <directory containing page-N.json>
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const dir = process.argv[2];
if (!dir) throw new Error('Pass the official download page directory');
const pageNames = fs.readdirSync(dir).filter(n => /^page-\d+\.json$/.test(n)).sort((a,b) => Number(a.match(/\d+/)[0])-Number(b.match(/\d+/)[0]));
const rows = pageNames.flatMap(n => JSON.parse(fs.readFileSync(path.join(dir,n),'utf8')));
if (!rows.length || JSON.parse(fs.readFileSync(path.join(dir,pageNames.at(-1)),'utf8')).length >= 10000) throw new Error('Download incomplete: fetch through the final page');
const year = Math.max(...rows.map(r=>Number(r.YR)||0));
const id = (prefix,text) => prefix+crypto.createHash('sha256').update(text).digest('hex').slice(0,20);
const schools = new Map();
for (const r of rows) {
  if (Number(r.YR)!==year || !['학사','전문학사','학석사통합'].includes(r.DEG_CRSE_CRS_NM) || r.SCHL_SE_NM.includes('대학원') || !['기존','신설','변경','통합','분리'].includes(r.SCSBJT_STTS_NM)) continue;
  const name=r.SCHL_NM?.trim(), department=r.SCSBJT_NM?.trim();
  if (!name || !department) throw new Error('Missing school/department');
  const key=JSON.stringify([name,r.CTPV_NM,r.SCHL_SE_NM]);
  if (!schools.has(key)) schools.set(key,{id:id('sch_',key),name,region:r.CTPV_NM,type:r.SCHL_SE_NM,departments:new Map()});
  const school=schools.get(key);
  school.departments.set(department,{id:id('dep_',school.id+'\n'+department),name:department});
}
const catalog={source:'한국대학교육협의회 / 공공데이터포털 전국대학별학과정보표준데이터',sourceUrl:'https://www.data.go.kr/data/15107737/standard.do',downloadedAt:new Date().toISOString().slice(0,10),year,asOf:[...new Set(rows.map(r=>r.CRTR_YMD).filter(Boolean))].sort().at(-1),rawRowCount:rows.length,schools:[...schools.values()].map(s=>({...s,departments:[...s.departments.values()].sort((a,b)=>a.name.localeCompare(b.name,'ko'))})).sort((a,b)=>a.name.localeCompare(b.name,'ko')||a.region.localeCompare(b.region,'ko'))};
const target=path.join(__dirname,'../backend/src/main/resources/catalog/universities.json');
fs.mkdirSync(path.dirname(target),{recursive:true});
fs.writeFileSync(target,JSON.stringify(catalog));
console.log(JSON.stringify({year,asOf:catalog.asOf,rawRows:rows.length,schools:catalog.schools.length,departments:catalog.schools.reduce((n,s)=>n+s.departments.length,0)}));
