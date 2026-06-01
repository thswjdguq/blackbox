'use strict';
const path = require('path');
const PptxGenJS = require('pptxgenjs');
const pptx = new PptxGenJS();
pptx.layout = 'LAYOUT_16x9';

const W = 10, H = 5.63;
const C = {
  indigo:'3730A3', indigoM:'6366F1', indigoL:'EEF2FF',
  teal:'0D9488',   tealL:'CCFBF1',
  white:'FFFFFF',  gray1:'F8FAFC', gray2:'F1F5F9', border:'CBD5E1',
  dark:'0F172A',   mid:'334155',   light:'64748B',
  amber:'D97706',  amberL:'FEF3C7',
  green:'059669',  greenL:'ECFDF5',
  red:'DC2626',    redL:'FEF2F2',
  violet:'7C3AED', violetL:'F5F3FF',
  cyan:'0891B2',   cyanL:'ECFEFF',
};
const IMG = n => path.join(__dirname, n);
function makeShadow() {
  return { type:'outer', blur:5, offset:3, angle:45, color:'000000', opacity:0.12 };
}
function hdr(s, title, sub) {
  s.addShape('rect', { x:0, y:0, w:W, h:0.68, fill:{color:C.indigo} });
  s.addShape('rect', { x:0, y:0.68, w:W, h:0.033, fill:{color:C.teal} });
  s.addText(title, { x:0.4, y:0, w:sub?6.8:9, h:0.68,
    fontSize:21, bold:true, color:C.white, fontFace:'Malgun Gothic', valign:'middle' });
  if (sub) s.addText(sub, { x:7.6, y:0, w:2.1, h:0.68,
    fontSize:9, color:'A5B4FC', fontFace:'Calibri', valign:'middle', align:'right' });
}
function ftr(s, pg) {
  s.addShape('rect', { x:0, y:H-0.26, w:W, h:0.26, fill:{color:C.dark} });
  s.addText('Team Blackbox  ·  손에 손잡고', { x:0.3, y:H-0.26, w:6, h:0.26,
    fontSize:8, color:'94A3B8', fontFace:'Malgun Gothic', valign:'middle' });
  s.addText(`${pg}  /  16`, { x:8.5, y:H-0.26, w:1.2, h:0.26,
    fontSize:8, color:'94A3B8', fontFace:'Calibri', valign:'middle', align:'right' });
}

// ── SLIDE 1: 표지 ────────────────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  s.addShape('rect', { x:0, y:0, w:3.55, h:H, fill:{color:C.indigo} });
  s.addShape('rect', { x:3.55, y:0, w:0.05, h:H, fill:{color:C.teal} });
  s.addText('Team', { x:0.2, y:1.05, w:3.15, h:0.52,
    fontSize:26, color:'B0C4DE', fontFace:'Calibri', align:'center' });
  s.addText('Blackbox', { x:0.2, y:1.52, w:3.15, h:0.88,
    fontSize:40, bold:true, color:C.white, fontFace:'Calibri', align:'center' });
  s.addShape('rect', { x:0.55, y:2.52, w:2.35, h:0.04, fill:{color:C.teal} });
  s.addText('송승준  ·  송병철  ·  손정협  ·  손정효', { x:0.1, y:2.65, w:3.3, h:0.45,
    fontSize:10, color:'B0C4DE', fontFace:'Malgun Gothic', align:'center' });
  s.addText('컴퓨터소프트웨어과  ·  손에 손잡고', { x:0.1, y:3.08, w:3.3, h:0.35,
    fontSize:9, color:'7B90B8', fontFace:'Malgun Gothic', align:'center' });
  s.addText('프로젝트\n중간 발표', { x:3.75, y:0.85, w:5.95, h:1.55,
    fontSize:38, bold:true, color:C.indigo, fontFace:'Malgun Gothic', valign:'middle' });
  s.addShape('rect', { x:3.75, y:2.5, w:5.95, h:0.52, fill:{color:C.indigoL} });
  s.addText('팀 프로젝트 기여도 자동 증빙 플랫폼', { x:3.82, y:2.5, w:5.8, h:0.52,
    fontSize:14, color:C.indigo, fontFace:'Malgun Gothic', valign:'middle' });
  s.addShape('rect', { x:3.75, y:3.15, w:3.8, h:0.04, fill:{color:C.teal} });
  s.addText('컴퓨터소프트웨어과  ·  프로젝트 구현  ·  2026', { x:3.75, y:3.28, w:5.9, h:0.38,
    fontSize:11, color:C.light, fontFace:'Malgun Gothic' });
  s.addShape('rect', { x:0, y:H-0.26, w:W, h:0.26, fill:{color:C.dark} });
  s.addText('Team Blackbox  ·  손에 손잡고', { x:0.3, y:H-0.26, w:5, h:0.26,
    fontSize:8, color:'94A3B8', fontFace:'Malgun Gothic', valign:'middle' });
}

// ── SLIDE 2: 목차 ────────────────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  hdr(s, '목차');
  ftr(s, 2);
  const items = [
    ['01','프로젝트 개요','문제 정의 + 해결 방향'],
    ['02','기술 스택','Frontend · Backend · DB · 인프라'],
    ['03','구현 환경/도구','IDE · 컨테이너 · 협업 도구'],
    ['04','Software Architecture','시스템 구성도'],
    ['05','Use Case Diagram','전체 기능 & 역할'],
    ['06','전체 기능 진척도','MVP 구현 현황'],
    ['07','핵심 기능 소개','Hash Vault · AI · Score Engine'],
    ['08','화면 캡처','UI 시연'],
    ['09','ERD','Entity Relationship Diagram'],
    ['10','특장점','기존 PMS 대비 차별점'],
    ['11','당면 문제 & 극복방안','이슈 해결 과정'],
    ['12','향후 일정','9~15주차 계획'],
  ];
  const rH = 0.38, gap = 0.07, sx = 0.3, sy = 0.82, cW = 4.55;
  items.forEach(([num, title, sub], i) => {
    const col = i % 2, row = Math.floor(i / 2);
    const x = sx + col * (cW + 0.3), y = sy + row * (rH + gap);
    s.addShape('rect', { x, y, w:cW, h:rH,
      fill:{color: i%2===0 ? C.indigoL : C.gray1}, line:{color:C.border, width:0.5} });
    s.addShape('rect', { x, y, w:0.52, h:rH, fill:{color:C.indigo} });
    s.addText(num, { x, y, w:0.52, h:rH,
      fontSize:10, bold:true, color:C.white, fontFace:'Calibri', align:'center', valign:'middle' });
    s.addText(title, { x:x+0.58, y:y+0.02, w:cW-1.35, h:rH*0.52,
      fontSize:11, bold:true, color:C.dark, fontFace:'Malgun Gothic', valign:'bottom' });
    s.addText(sub, { x:x+0.58, y:y+rH*0.52, w:cW-1.35, h:rH*0.46,
      fontSize:8.5, color:C.light, fontFace:'Malgun Gothic', valign:'top' });
  });
}

// ── SLIDE 3: 프로젝트 개요 ────────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  hdr(s, '프로젝트 개요', '프로젝트 주제 및 내용');
  ftr(s, 3);
  // Problem box
  s.addShape('rect', { x:0.3, y:0.85, w:4.35, h:0.42, fill:{color:C.red} });
  s.addText('문제 정의', { x:0.3, y:0.85, w:4.35, h:0.42,
    fontSize:13, bold:true, color:C.white, fontFace:'Malgun Gothic', align:'center', valign:'middle' });
  s.addShape('rect', { x:0.3, y:1.27, w:4.35, h:2.85,
    fill:{color:C.redL}, line:{color:C.red, width:1}, shadow:makeShadow() });
  s.addText([
    {text:'팀원 기여도를 공정하게 증명할 수단 부재', options:{bullet:true, paraSpaceBefore:3}},
    {text:'카카오톡·노션·깃허브 활동이 분산돼 통합 증거 없음', options:{bullet:true, paraSpaceBefore:3}},
    {text:'무임승차 감지 및 교수 검증 수단 없음', options:{bullet:true, paraSpaceBefore:3}},
    {text:'회의 내용이 태스크로 연결되지 않아 실행력 저하', options:{bullet:true, paraSpaceBefore:3}},
  ], { x:0.45, y:1.32, w:4.1, h:2.7,
    fontSize:12, color:C.dark, fontFace:'Malgun Gothic', valign:'top', margin:6 });
  // Arrow
  s.addText('→', { x:4.75, y:2.4, w:0.5, h:0.5,
    fontSize:24, bold:true, color:C.teal, align:'center', valign:'middle' });
  // Solution box
  s.addShape('rect', { x:5.35, y:0.85, w:4.35, h:0.42, fill:{color:C.indigo} });
  s.addText('해결 방향', { x:5.35, y:0.85, w:4.35, h:0.42,
    fontSize:13, bold:true, color:C.white, fontFace:'Malgun Gothic', align:'center', valign:'middle' });
  s.addShape('rect', { x:5.35, y:1.27, w:4.35, h:2.85,
    fill:{color:C.indigoL}, line:{color:C.indigo, width:1}, shadow:makeShadow() });
  s.addText([
    {text:'SHA-256 Hash로 파일 무결성 자동 고정 → 조작 불가 증거', options:{bullet:true, paraSpaceBefore:3}},
    {text:'Claude AI로 회의록 요약 + 액션아이템 → 칸반 자동 생성', options:{bullet:true, paraSpaceBefore:3}},
    {text:'플랫폼 내부 활동 기반 기여도 자동 산출 + 경보 시스템', options:{bullet:true, paraSpaceBefore:3}},
    {text:'Notion/Google Calendar 연동으로 업무 자동화', options:{bullet:true, paraSpaceBefore:3}},
  ], { x:5.5, y:1.32, w:4.1, h:2.7,
    fontSize:12, color:C.dark, fontFace:'Malgun Gothic', valign:'top', margin:6 });
  // Tagline
  s.addShape('rect', { x:0.3, y:4.28, w:9.4, h:0.55, fill:{color:C.indigoL} });
  s.addText('"우리가 데이터를 만들지 않는다. 외부 시스템이 이미 기록한 데이터를 읽어올 뿐이다."', {
    x:0.3, y:4.28, w:9.4, h:0.55,
    fontSize:12, italic:true, color:C.indigo, fontFace:'Malgun Gothic', align:'center', valign:'middle' });
}

// ── SLIDE 4: 기술 스택 ────────────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  hdr(s, '기술 스택', 'Tech Stack');
  ftr(s, 4);
  const cats = [
    { label:'Frontend',  color:C.indigo,  bg:C.indigoL,  items:['Next.js 16.2','TypeScript','Tailwind CSS','Recharts','dnd-kit'] },
    { label:'Backend',   color:C.green,   bg:C.greenL,   items:['Java 17','Spring Boot 3.x','Spring Security','JWT Auth','JPA + Flyway'] },
    { label:'Database',  color:C.amber,   bg:C.amberL,   items:['PostgreSQL 16','Flyway V1~V14','14개 테이블'] },
    { label:'인프라',    color:C.teal,    bg:C.tealL,    items:['Docker Compose','Nginx','HTTPS / SSL'] },
    { label:'외부 API',  color:C.violet,  bg:C.violetL,  items:['Claude API','Notion API','Google Calendar API'] },
  ];
  const cW=1.82, gap=0.1, sx=0.3, rH=0.52, hH=0.42;
  cats.forEach((cat, i) => {
    const x = sx + i*(cW+gap);
    s.addShape('rect', { x, y:0.83, w:cW, h:hH, fill:{color:cat.color} });
    s.addText(cat.label, { x, y:0.83, w:cW, h:hH,
      fontSize:11, bold:true, color:C.white, fontFace:'Malgun Gothic', align:'center', valign:'middle' });
    cat.items.forEach((item, j) => {
      const iy = 0.83 + hH + j*rH;
      s.addShape('rect', { x, y:iy, w:cW, h:rH,
        fill:{color: j%2===0 ? cat.bg : C.white}, line:{color:cat.color, width:0.5} });
      s.addText(item, { x:x+0.04, y:iy, w:cW-0.08, h:rH,
        fontSize:10.5, color:C.dark, fontFace:'Malgun Gothic', align:'center', valign:'middle' });
    });
  });
}

// ── SLIDE 5: 구현 환경/도구 ───────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  hdr(s, '구현 환경 / 도구', 'Development Environment');
  ftr(s, 5);
  const tools = [
    { name:'IntelliJ IDEA',   desc:'Backend Java / Spring Boot 개발',        color:C.indigo },
    { name:'VS Code',         desc:'Frontend Next.js / TypeScript 개발',     color:C.indigoM },
    { name:'Docker Desktop',  desc:'전체 스택 컨테이너화 · docker compose up', color:C.teal },
    { name:'GitHub',          desc:'버전 관리 / 브랜치 전략 / PR 관리',        color:C.dark },
    { name:'Postman',         desc:'REST API 테스트 & 문서화',                color:C.amber },
    { name:'pgAdmin',         desc:'PostgreSQL DB 관리 · Flyway 마이그레이션', color:C.green },
  ];
  const cW=4.5, rH=0.78, gap=0.18;
  tools.forEach((tool, i) => {
    const col=i%2, row=Math.floor(i/2);
    const x=0.3+col*(cW+0.4), y=0.85+row*(rH+gap);
    s.addShape('rect', { x, y, w:cW, h:rH,
      fill:{color:C.gray1}, line:{color:C.border, width:0.8}, shadow:makeShadow() });
    s.addShape('rect', { x, y, w:0.07, h:rH, fill:{color:tool.color} });
    s.addText(tool.name, { x:x+0.17, y:y+0.07, w:cW-0.25, h:0.32,
      fontSize:14, bold:true, color:C.dark, fontFace:'Malgun Gothic', valign:'bottom' });
    s.addText(tool.desc, { x:x+0.17, y:y+0.4, w:cW-0.25, h:0.3,
      fontSize:11, color:C.light, fontFace:'Malgun Gothic', valign:'top' });
  });
}

// ── SLIDE 6: Software Architecture (full image) ───────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  s.addImage({ path:IMG('slide_architecture.png'), x:0, y:0, w:W, h:H });
}

// ── SLIDE 7: Use Case Diagram (full image) ────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  s.addImage({ path:IMG('slide_usecase.png'), x:0, y:0, w:W, h:H });
}

// ── SLIDE 8: 진척도 ───────────────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  hdr(s, '전체 기능 대비 진척도', '중간 발표 기준');
  ftr(s, 8);
  const data = [
    ['인프라 구축',           100, C.teal   ],
    ['인증 / 프로젝트 관리',   95, C.indigo ],
    ['칸반 보드',              95, C.indigoM],
    ['회의록 / AI 연동',      100, C.violet ],
    ['Hash Vault',            100, C.red    ],
    ['Score Engine / 경보',   100, C.cyan   ],
    ['AI / Notion 연동',      100, C.teal   ],
    ['Google Calendar',        90, C.green  ],
  ];
  const barMaxW=5.8, barH=0.34, gap=0.1, lW=2.55, barX=2.95, sy=0.88;
  data.forEach(([label, pct, color], i) => {
    const y = sy + i*(barH+gap);
    s.addText(label, { x:0.3, y, w:lW, h:barH,
      fontSize:11.5, color:C.dark, fontFace:'Malgun Gothic', valign:'middle', align:'right' });
    s.addShape('rect', { x:barX, y:y+0.04, w:barMaxW, h:barH-0.08,
      fill:{color:C.gray2}, line:{color:C.border, width:0.4} });
    s.addShape('rect', { x:barX, y:y+0.04, w:barMaxW*pct/100, h:barH-0.08,
      fill:{color:color} });
    s.addText(`${pct}%`, { x:barX+barMaxW+0.12, y, w:0.58, h:barH,
      fontSize:11.5, bold:true, color:color, fontFace:'Calibri', valign:'middle' });
  });
  const avg = Math.round(data.reduce((s,[,p])=>s+p,0)/data.length);
  s.addShape('rect', { x:0.3, y:4.7, w:9.4, h:0.44,
    fill:{color:C.indigoL}, line:{color:C.indigo, width:0.8} });
  s.addText(`전체 평균 진척도: ${avg}%  ·  MVP 완성 (중간 발표 기준)`, {
    x:0.3, y:4.7, w:9.4, h:0.44,
    fontSize:13, bold:true, color:C.indigo, fontFace:'Malgun Gothic', align:'center', valign:'middle' });
}

// ── SLIDE 9: 핵심 기능 소개 ───────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  hdr(s, '핵심 기능 소개', 'Key Features');
  ftr(s, 9);
  const features = [
    { title:'Hash Vault',       sub:'조작 불가 증거',           color:C.red,    bg:C.redL,
      pts:['SHA-256 해시 자동 고정','변조 감지 & 이력 추적','버전 타임라인 + ZIP 제출'] },
    { title:'AI 회의록 자동화',  sub:'Claude API 연동',          color:C.violet, bg:C.violetL,
      pts:['회의록 AI 요약 생성','액션아이템 자동 추출','Notion 자동 내보내기'] },
    { title:'참여 여부 분석',   sub:'Score Engine',             color:C.cyan,   bg:C.cyanL,
      pts:['활동 기반 기여도 산출','FULL/PARTIAL/NONE 분류','불균형·벼락치기 경보'] },
    { title:'AI 일정 추천',     sub:'Claude + Google Calendar', color:C.green,  bg:C.greenL,
      pts:['팀원 캘린더 freeBusy 조회','AI 최적 시간 추천','GCal 이벤트 자동 등록'] },
  ];
  const cW=2.2, cH=3.9, sx=0.3, gap=0.26, cy=0.86;
  features.forEach((f, i) => {
    const x = sx + i*(cW+gap);
    s.addShape('rect', { x, y:cy, w:cW, h:cH,
      fill:{color:f.bg}, line:{color:f.color, width:1.2}, shadow:makeShadow() });
    s.addShape('rect', { x, y:cy, w:cW, h:0.68, fill:{color:f.color} });
    s.addText(f.title, { x:x+0.04, y:cy+0.02, w:cW-0.08, h:0.4,
      fontSize:12, bold:true, color:C.white, fontFace:'Malgun Gothic', align:'center', valign:'middle' });
    s.addText(f.sub, { x:x+0.04, y:cy+0.4, w:cW-0.08, h:0.28,
      fontSize:8.5, color:'DDE5FF', fontFace:'Malgun Gothic', align:'center' });
    f.pts.forEach((pt, j) => {
      const py = cy+0.8+j*1.0;
      s.addShape('rect', { x:x+0.1, y:py, w:cW-0.2, h:0.82,
        fill:{color:C.white}, line:{color:f.color, width:0.5} });
      s.addText(pt, { x:x+0.12, y:py, w:cW-0.24, h:0.82,
        fontSize:11, color:C.dark, fontFace:'Malgun Gothic', align:'center', valign:'middle', wrap:true });
    });
  });
}

// ── SLIDE 10: 화면 캡처 (1) ──────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  hdr(s, '화면 캡처 — 주요 화면 (1)', 'UI Screenshot');
  ftr(s, 10);
  const shots = [
    { path:IMG('screenshot/프로젝트홈.png'), label:'프로젝트 홈 대시보드' },
    { path:IMG('screenshot/칸반보드.png'),   label:'칸반 보드' },
    { path:IMG('screenshot/회의록2.png'),    label:'AI 회의록' },
  ];
  const iW=2.88, iH=1.62, sx=0.3, gap=0.27;
  shots.forEach((shot, i) => {
    const x = sx + i*(iW+gap);
    s.addShape('rect', { x, y:0.84, w:iW, h:iH+0.35,
      fill:{color:C.gray1}, line:{color:C.border, width:0.8}, shadow:makeShadow() });
    s.addImage({ path:shot.path, x:x+0.06, y:0.9, w:iW-0.12, h:iH });
    s.addText(shot.label, { x, y:0.84+iH+0.1, w:iW, h:0.25,
      fontSize:11, bold:true, color:C.dark, fontFace:'Malgun Gothic', align:'center', valign:'middle' });
  });
}

// ── SLIDE 11: 화면 캡처 (2) ──────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  hdr(s, '화면 캡처 — 주요 화면 (2)', 'UI Screenshot');
  ftr(s, 11);
  const shots = [
    { path:IMG('screenshot/hashvault.png'),  label:'Hash Vault' },
    { path:IMG('screenshot/기여도.png'),     label:'기여도 분석' },
    { path:IMG('screenshot/ai일정추천.png'), label:'AI 일정 추천' },
  ];
  const iW=2.88, iH=1.62, sx=0.3, gap=0.27;
  shots.forEach((shot, i) => {
    const x = sx + i*(iW+gap);
    s.addShape('rect', { x, y:0.84, w:iW, h:iH+0.35,
      fill:{color:C.gray1}, line:{color:C.border, width:0.8}, shadow:makeShadow() });
    s.addImage({ path:shot.path, x:x+0.06, y:0.9, w:iW-0.12, h:iH });
    s.addText(shot.label, { x, y:0.84+iH+0.1, w:iW, h:0.25,
      fontSize:11, bold:true, color:C.dark, fontFace:'Malgun Gothic', align:'center', valign:'middle' });
  });
}

// ── SLIDE 12: ERD (full image) ────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  s.addImage({ path:IMG('slide_erd.png'), x:0, y:0, w:W, h:H });
}

// ── SLIDE 13: 특장점 ──────────────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  hdr(s, '특장점', '기존 PMS 대비 차별점');
  ftr(s, 13);
  const feats = [
    { num:'01', title:'조작 불가 증거',    color:C.red,
      desc:'SHA-256 해시 고정 + 변조 자동 감지.\n회의록·파일·활동 모두 외부 시스템 기록 기반 → 수동 조작 원천 차단.' },
    { num:'02', title:'AI 완전 자동화',    color:C.violet,
      desc:'회의록 → AI 요약 → 액션아이템 → 칸반 태스크 → Notion 동기화까지 원클릭 완성.' },
    { num:'03', title:'학생 전용 설계',    color:C.indigo,
      desc:'Jira·Asana는 기업용 · 교수 뷰 없음 · 무임승차 탐지 부재.\nTeam Blackbox는 참여 증빙 + AI 경보 내장.' },
    { num:'04', title:'팀플 증거 패키지',  color:C.teal,
      desc:'회의록 + 활동 이력 + PDF 리포트 + Vault 해시 이력 → ZIP 한 번에 교수 제출.' },
  ];
  feats.forEach((f, i) => {
    const row=Math.floor(i/2), col=i%2;
    const x=0.3+col*4.9, y=0.86+row*2.15;
    s.addShape('rect', { x, y, w:4.6, h:1.9,
      fill:{color:C.gray1}, line:{color:f.color, width:1.5}, shadow:makeShadow() });
    s.addShape('rect', { x, y, w:0.58, h:1.9, fill:{color:f.color} });
    s.addText(f.num, { x, y:y+0.05, w:0.58, h:0.48,
      fontSize:15, bold:true, color:C.white, fontFace:'Calibri', align:'center', valign:'middle' });
    s.addText(f.title, { x:x+0.66, y:y+0.1, w:3.75, h:0.42,
      fontSize:14, bold:true, color:f.color, fontFace:'Malgun Gothic' });
    s.addText(f.desc, { x:x+0.66, y:y+0.52, w:3.75, h:1.22,
      fontSize:11.5, color:C.mid, fontFace:'Malgun Gothic', wrap:true });
  });
}

// ── SLIDE 14: 당면 문제 & 극복방안 ───────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  hdr(s, '당면 문제 및 극복방안', 'Challenges & Solutions');
  ftr(s, 14);
  const issues = [
    { no:'1', problem:'Chrome HTTPS (HSTS) 이슈',
      cause:'SSL 설정 후 Chrome이 localhost HSTS 캐싱으로 접속 불가',
      sol:'nginx/Dockerfile에서 자체 서명 인증서 자동 생성 + HSTS 헤더 제거로 해결' },
    { no:'2', problem:'칸반 드래그앤드롭 동작 불가',
      cause:'dnd-kit listeners 이벤트 바인딩 위치 오류로 드래그 시작 안 됨',
      sol:'listeners를 카드 최상위 div로 이동 후 정상 동작 확인' },
    { no:'3', problem:'Google Calendar OAuth 401 오류',
      cause:'Cloud Console OAuth 클라이언트 ID와 서버 환경변수 불일치',
      sol:'Cloud Console OAuth 클라이언트 재설정 진행 중 (90% 완료)' },
  ];
  issues.forEach((iss, i) => {
    const y = 0.87 + i*1.46;
    s.addShape('rect', { x:0.3, y, w:9.4, h:1.3,
      fill:{color:C.gray1}, line:{color:C.border, width:0.8}, shadow:makeShadow() });
    s.addShape('rect', { x:0.3, y, w:0.48, h:1.3, fill:{color:C.indigo} });
    s.addText(iss.no, { x:0.3, y, w:0.48, h:1.3,
      fontSize:18, bold:true, color:C.white, fontFace:'Calibri', align:'center', valign:'middle' });
    s.addText(iss.problem, { x:0.86, y:y+0.08, w:8.7, h:0.34,
      fontSize:13, bold:true, color:C.red, fontFace:'Malgun Gothic' });
    s.addText('원인: '+iss.cause, { x:0.86, y:y+0.42, w:8.7, h:0.3,
      fontSize:11, color:C.mid, fontFace:'Malgun Gothic' });
    s.addShape('rect', { x:0.86, y:y+0.75, w:8.7, h:0.38, fill:{color:C.indigoL} });
    s.addText('해결: '+iss.sol, { x:0.95, y:y+0.75, w:8.6, h:0.38,
      fontSize:11, bold:true, color:C.indigo, fontFace:'Malgun Gothic', valign:'middle' });
  });
}

// ── SLIDE 15: 향후 일정 ───────────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.white };
  hdr(s, '향후 일정 계획', '9 ~ 15주차');
  ftr(s, 15);
  const sched = [
    { week:'9주차',     title:'코드 안정화 + 통합 테스트',  color:C.teal,   done:true  },
    { week:'10~11주차', title:'GitHub App 연동',           color:C.indigo, done:false },
    { week:'12주차',    title:'Google Drive 연동',         color:C.indigoM,done:false },
    { week:'13주차',    title:'피어리뷰 + AI 고도화',      color:C.violet, done:false },
    { week:'14주차',    title:'최종 발표 준비',            color:C.amber,  done:false },
    { week:'15주차',    title:'최종 발표',                color:C.red,    done:false },
  ];
  const rH=0.54, gap=0.1, lW=1.55, barW=6.5, barX=0.3+lW+0.18, sy=0.88;
  sched.forEach((item, i) => {
    const y = sy + i*(rH+gap);
    s.addShape('rect', { x:0.3, y, w:lW, h:rH,
      fill:{color: item.done ? item.color : C.gray2},
      line:{color:item.color, width:0.8} });
    s.addText(item.week, { x:0.3, y, w:lW, h:rH,
      fontSize:11, bold:true, color: item.done ? C.white : item.color,
      fontFace:'Malgun Gothic', align:'center', valign:'middle' });
    s.addShape('rect', { x:barX, y, w:barW, h:rH,
      fill:{color: item.done ? C.indigoL : C.gray1},
      line:{color:item.color, width: item.done ? 1.3 : 0.5} });
    s.addText(item.title+(item.done ? '  ✓ 완료':''), { x:barX+0.15, y, w:barW-0.3, h:rH,
      fontSize:13, bold:item.done, color: item.done ? C.indigo : C.mid,
      fontFace:'Malgun Gothic', valign:'middle' });
    s.addShape('rect', { x:barX+barW+0.12, y:y+0.08, w:0.82, h:rH-0.16,
      fill:{color: item.done ? item.color : C.gray2},
      line:{color:item.color, width:0.5} });
    s.addText(item.done ? '완료' : '예정', {
      x:barX+barW+0.12, y:y+0.08, w:0.82, h:rH-0.16,
      fontSize:10, bold:item.done, color: item.done ? C.white : C.light,
      fontFace:'Malgun Gothic', align:'center', valign:'middle' });
  });
}

// ── SLIDE 16: 마무리 ──────────────────────────────────────────────────────────
{
  const s = pptx.addSlide();
  s.background = { color:C.indigo };
  s.addShape('rect', { x:0, y:0, w:W, h:0.07, fill:{color:C.teal} });
  s.addShape('rect', { x:0, y:H-0.07, w:W, h:0.07, fill:{color:C.teal} });
  s.addText('감사합니다', { x:0, y:1.3, w:W, h:1.05,
    fontSize:52, bold:true, color:C.white, fontFace:'Malgun Gothic', align:'center' });
  s.addText('Team Blackbox', { x:0, y:2.45, w:W, h:0.6,
    fontSize:26, color:'B0C4DE', fontFace:'Calibri', align:'center' });
  s.addText('팀 프로젝트 기여도 자동 증빙 플랫폼', { x:0, y:3.05, w:W, h:0.48,
    fontSize:15, color:C.tealL, fontFace:'Malgun Gothic', align:'center' });
  s.addShape('rect', { x:3.5, y:3.65, w:3, h:0.04, fill:{color:C.teal} });
  s.addText('송승준  ·  송병철  ·  손정협  ·  손정효', { x:0, y:3.78, w:W, h:0.45,
    fontSize:13, color:'B0C4DE', fontFace:'Malgun Gothic', align:'center' });
  s.addText('컴퓨터소프트웨어과  ·  손에 손잡고', { x:0, y:4.2, w:W, h:0.38,
    fontSize:11, color:'6B7DB0', fontFace:'Malgun Gothic', align:'center' });
}

// ── Save ──────────────────────────────────────────────────────────────────────
pptx.writeFile({ fileName: path.join(__dirname, 'Team_Blackbox_중간발표.pptx') })
  .then(() => console.log('Saved: md/Team_Blackbox_중간발표.pptx'))
  .catch(err => { console.error(err); process.exit(1); });
