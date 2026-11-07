// Regenerate the standalone guide from the two shareable Markdown documents.
const fs = require('node:fs'), path = require('node:path');
const dir = path.resolve(__dirname, '../docs/team/demo/20261019');
const benchmark = fs.readFileSync(path.join(dir, 'Flow 벤치마킹.md'), 'utf8');
const storyboard = fs.readFileSync(path.join(dir, '시연 스토리보드.md'), 'utf8');
const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const inline = s => esc(s).replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, label, url) => /^https?:/.test(url) ? `<a href="${url}" target="_blank" rel="noopener">${label} ↗</a>` : label).replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>').replace(/`([^`]+)`/g, '<code>$1</code>');
function markdown(s) {
  let out = '', table = false, code = false;
  for (const line of s.split(/\r?\n/)) {
    if (line.startsWith('```')) { if (table) { out += '</tbody></table></div>'; table = false; } out += code ? '</pre>' : '<pre>'; code = !code; continue; }
    if (code) { out += esc(line) + '\n'; continue; }
    if (line.startsWith('|')) {
      if (/^\|[\s:|-]+$/.test(line)) continue;
      const cells = line.split('|').slice(1, -1).map(x => x.trim());
      if (!table) { out += '<div class="table-wrap"><table><thead><tr>' + cells.map(c => '<th>' + inline(c) + '</th>').join('') + '</tr></thead><tbody>'; table = true; }
      else out += '<tr>' + cells.map(c => '<td>' + inline(c) + '</td>').join('') + '</tr>';
      continue;
    }
    if (table) { out += '</tbody></table></div>'; table = false; }
    if (!line.trim()) continue;
    const h = line.match(/^(#{1,3}) (.+)/);
    if (h) out += `<h${h[1].length + 1}>${inline(h[2])}</h${h[1].length + 1}>`;
    else if (line.startsWith('- [ ] ')) out += '<label class="check"><input type="checkbox"> <span>' + inline(line.slice(6)) + '</span></label>';
    else if (line.startsWith('- ')) out += '<p class="bullet">• ' + inline(line.slice(2)) + '</p>';
    else if (line.startsWith('> ')) out += '<blockquote>' + inline(line.slice(2)) + '</blockquote>';
    else out += '<p>' + inline(line) + '</p>';
  }
  if (table) out += '</tbody></table></div>';
  return out;
}
const scenes = storyboard.split(/\r?\n/).filter(x => /^\| S\d\d /.test(x)).map(x => x.split('|').slice(1, -1).map(x => x.trim()));
if (scenes.length !== 12 || scenes.some(s => s.length !== 5)) throw Error('Expected 12 scenes with five columns');
// These are explanatory captions, not exact UI labels or screenshots.
const flows = [
  ['제품 소개·프로젝트 예시', '로그인', '현재 계정 이름 확인'],
  ['상권 분석 보고서', '제출 기한·담당자·필수 조건', '연결 업무 확인'],
  ['합성 과제 설명', 'AI 계획 제안', '사람이 검토·적용'],
  ['팀 업무 · 목록', '담당 B · 기한 · 완료 기준', '최종 제출물 연결'],
  ['현재 로그인 B', '내 업무 / 전체 업무', '내가 해야 할 업무 확인'],
  ['업무 결과 올리기 · v1', '대화·피드백', '팀원에게 검토 부탁 댓글'],
  ['A 피드백 → B 알림', 'B 수정 v2·답변', 'A에게 답변·파일 알림'],
  ['B 결과 확인 요청', 'A가 기준·결과 확인', '업무 완료 · DONE'],
  ['C의 최종 파일 피드백', 'A 수정·해결 사유·새 검토', 'C가 v2 검토 승인'],
  ['필수 조건·승인 확인', '승인된 최종 후보 v2', 'A가 특정 버전 확정'],
  ['학교에 직접 제출', '경로·시각·확인 내용 기록', '촬영에서는 합성 예시 표시'],
  ['업무·파일·피드백 이력', '활동·기여 기록', '기여도 논의의 근거'],
];
const cards = scenes.map((s, i) => `<article class="scene" id="scene-${i + 1}" ${i ? 'hidden' : ''} aria-labelledby="title-${i + 1}">
<div class="scene-head"><b class="eyebrow">SCENE ${String(i + 1).padStart(2, '0')} / 12</b><span>${esc(s[0].split(' · ')[1])}</span></div>
<h2 id="title-${i + 1}">${esc(s[1])}</h2>
<div class="wireframe"><aside><b>Team<br>Blackbox</b><span>알림</span><span>최종 제출물</span><span>팀 업무</span><span>피드백</span><span>회의</span><small>자료 · 활동 · 설정</small></aside><div class="canvas"><p class="caption">현재 기능 관계를 설명하는 개략도 · 실제 캡처 아님</p>${flows[i].map((f, n) => `<div class="mock ${n === 2 ? 'action' : ''}"><small>0${n + 1}</small>${esc(f)}</div>`).join('<div class="down">↓</div>')}<p class="caption">그림의 축약 문구 대신 아래 실제 촬영 지시를 따라 조작하세요.</p></div></div>
<div class="directions"><section><h3>화면에서 할 일</h3><p>${esc(s[2])}</p></section><section><h3>확인할 결과</h3><p>${esc(s[3])}</p></section></div>
<blockquote><small>바로 읽는 내레이션</small><p>${esc(s[4])}</p></blockquote>
<p class="note">cdae9636 기준 · ${i === 2 ? 'AI는 별도 합성 프로젝트 권장. 실제 응답 대기 시간은 촬영 시간에 추가됩니다.' : i === 10 ? '합성 시연과 실제 학교 제출 사실을 구분합니다.' : i === 6 ? '알림 약 10초·피드백 목록 약 30초 갱신. 필요하면 새로고침합니다.' : '역할·권한·사전 조건은 촬영 대본 전문을 확인하세요.'}</p></article>`).join('\n');
const html = `<!doctype html><html lang="ko"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Team Blackbox · Flow 벤치마킹과 엑스포 시연 가이드</title><style>
:root{--ink:#172a3a;--muted:#596b79;--line:#dce4e9;--blue:#2256d8;--pale:#eef3ff}*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:#f2f5f8;color:var(--ink);font:16px/1.65 'Malgun Gothic','Apple SD Gothic Neo',sans-serif}a{color:var(--blue)}button,a,input{outline-offset:4px}button{font:inherit;cursor:pointer}button:disabled{opacity:.4;cursor:default}header{background:#132838;color:white;padding:48px max(24px,calc((100vw - 1120px)/2)) 38px}.eyebrow{font-size:12px;letter-spacing:.12em;color:#698ae1}header .eyebrow{color:#9cb9ff}header h1{font-size:clamp(26px,4vw,42px);line-height:1.3;margin:10px 0 14px}header p{max-width:850px;color:#d0dde7;margin:0}.meta{display:flex;flex-wrap:wrap;gap:10px;margin-top:22px}.pill{font-size:12px;padding:5px 11px;border:1px solid #61778a;border-radius:20px}main{max-width:1168px;padding:24px;margin:auto}.topnav{display:flex;gap:9px;flex-wrap:wrap;margin-bottom:24px}.topnav a,.print,.step{border:1px solid var(--line);border-radius:9px;padding:9px 14px;background:white;color:var(--ink);text-decoration:none;font-size:14px}.print{margin-left:auto}.intro,.section{background:white;border:1px solid var(--line);border-radius:16px;padding:28px;margin-bottom:24px}.intro h2{margin:0 0 12px;font-size:23px}.intro p{margin:8px 0}.intro strong{color:var(--blue)}.flow{display:flex;gap:8px;flex-wrap:wrap;margin-top:20px}.flow span{background:var(--pale);border-radius:7px;padding:7px 12px;font-size:13px}.navigator{display:flex;gap:7px;flex-wrap:wrap;margin-bottom:14px}.step{padding:7px 11px}.step[aria-current=step]{background:var(--blue);color:white;border-color:var(--blue)}.scene{background:white;border:1px solid var(--line);border-radius:16px;padding:30px}.scene-head{display:flex;justify-content:space-between;gap:12px;font-size:13px;color:var(--muted)}.scene h2{font-size:27px;line-height:1.35;margin:12px 0 22px}.wireframe{display:grid;grid-template-columns:155px 1fr;border:1px solid #ccd7e0;border-radius:12px;overflow:hidden;background:#f7f9fb}.wireframe aside{background:#192f40;color:#cddce8;padding:20px;display:flex;flex-direction:column;gap:13px;font-size:12px}.wireframe aside b{color:white;line-height:1.25;margin-bottom:10px;font-size:16px}.wireframe aside small{font-size:10px;color:#a2b4c2}.canvas{padding:18px 22px}.caption{font-size:10px;color:var(--muted);margin:0 0 12px}.caption:last-child{margin:15px 0 0}.mock{background:white;border:1px solid var(--line);border-radius:7px;padding:14px 15px;font-size:14px}.mock small{display:inline-block;color:#8293a1;font-size:10px;margin-right:12px}.mock.action{background:var(--pale);border-color:#c6d4ff;color:#234cad;font-weight:bold}.down{color:#a7b6c4;text-align:center;line-height:22px}.directions{display:grid;grid-template-columns:1fr 1fr;gap:22px;margin-top:22px}.directions h3{font-size:13px;color:var(--muted);margin:0 0 6px}.directions p{font-size:14px;margin:0}blockquote{margin:22px 0 0;border-left:3px solid var(--blue);background:#f3f6ff;padding:15px 19px;font-size:15px}blockquote small{font-size:11px;color:var(--muted)}blockquote p{margin:5px 0 0}.note{font-size:12px;color:var(--muted);margin:18px 0 0}.controls{display:flex;gap:10px;align-items:center;justify-content:space-between;margin:16px 0 30px}.controls button{background:white;border:1px solid var(--line);border-radius:8px;padding:9px 18px}.controls span{font-size:12px;color:var(--muted)}.document{font-size:14px}.document h2{font-size:22px;margin:10px 0 16px}.document h3{font-size:18px;margin-top:30px}.document h4{font-size:16px}.document p{margin:10px 0}.table-wrap{overflow:auto;border:1px solid var(--line);border-radius:9px;margin:18px 0}table{border-collapse:collapse;width:100%;font-size:12px}th,td{text-align:left;vertical-align:top;padding:12px;border-bottom:1px solid var(--line);min-width:100px}th{background:#eef3f7}tr:last-child td{border-bottom:0}.check{display:flex;gap:8px;margin:12px 0}.check input{margin-top:6px;accent-color:var(--blue)}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#eff3f6;padding:18px;border-radius:8px;font-size:11px}code{background:#eff3f6;padding:2px 4px;border-radius:4px}footer{font-size:12px;color:var(--muted);padding:10px 0 30px}[hidden]{display:none}
@media(max-width:640px){header{padding:30px 20px}main{padding:14px}.intro,.section,.scene{padding:20px}.wireframe{grid-template-columns:90px 1fr}.wireframe aside{padding:12px 8px;gap:12px;font-size:10px}.wireframe aside b{font-size:12px}.canvas{padding:12px}.mock{font-size:12px;padding:10px}.directions{grid-template-columns:1fr;gap:15px}.scene h2{font-size:23px}.print{margin-left:0}}
@media print{@page{size:A4;margin:13mm}html,body{background:white;color:#111;font-size:11px}header{padding:20px;background:white;color:#111;border-bottom:2px solid #172a3a}header p,header .eyebrow{color:#333}header h1{font-size:26px}.pill{border-color:#bbb}main{padding:0;max-width:none}.topnav,.navigator,.controls{display:none}.scene[hidden]{display:block!important}.scene{break-inside:avoid;break-before:page;padding:18px;border:1px solid #aaa;margin:12px 0}.intro,.section{border:0;border-radius:0;padding:16px 0}.scene h2{font-size:23px}.scene p{font-size:12px}.wireframe aside{background:#e8edf3;color:#222}.wireframe aside b{color:#222}.wireframe{grid-template-columns:125px 1fr}.document{font-size:11px}.document table{font-size:10px}.document th,.document td{padding:7px;min-width:0}tr{break-inside:avoid}a{color:#111;text-decoration:underline}blockquote{border-color:#333}.section{break-before:page}footer{font-size:10px}}
</style></head><body><header><div class="eyebrow">TEAM BLACKBOX · EXPO DEMO KIT</div><h1>일을 진행하면,<br>팀의 기록이 함께 남습니다.</h1><p>Flow 벤치마킹 · 4인 팀 시연 스토리보드 · 촬영 대본<br>내 업무에서 피드백, 최종본 확정과 제출 기록까지.</p><div class="meta"><span class="pill">2026.10.19</span><span class="pill">shinguexpo · cdae9636</span><span class="pill">12장면 · 약 6분 20초 + 대기</span><span class="pill">개략도 / 실제 캡처 아님</span></div></header><main>
<nav class="topnav" aria-label="자료 탐색"><a href="#story">시연 스토리보드</a><a href="#benchmark">Flow 벤치마킹</a><a href="#script">촬영 대본·준비</a><button class="print" id="print">인쇄 / PDF 저장</button></nav>
<section class="intro"><h2>팀원에게 먼저 전달할 한 문장</h2><p><strong>“Flow의 업무 탐색과 알림 패턴을 참고하고, 대학생 팀의 최종 제출 과정을 중심으로 재구성했습니다.”</strong></p><p>업무 완료, 최종 파일 승인, 팀장 확정, 학교 제출 사실 기록은 서로 다른 단계입니다. 영상은 실제 서비스에서 촬영하고 아래 그림은 설명용으로 사용하세요.</p><div class="flow">${['내 업무', '결과 파일', '피드백·알림', '팀원 확인', '최종본 검토·확정', '제출 기록'].map(x => '<span>' + x + '</span>').join('')}</div></section>
<section id="story" aria-label="시연 스토리보드"><div class="navigator" role="group" aria-label="장면 선택">${scenes.map((s, i) => `<button class="step" data-step="${i}" aria-controls="scene-${i + 1}" ${!i ? 'aria-current="step"' : ''}>${String(i + 1).padStart(2, '0')}</button>`).join('')}</div>${cards}<div class="controls"><button id="previous" disabled>← 이전 장면</button><span id="position" aria-live="polite">1 / 12 · 좌우 방향키로 이동</span><button id="next">다음 장면 →</button></div></section>
<section id="benchmark" class="section document">${markdown(benchmark)}</section><section id="script" class="section document">${markdown(storyboard)}</section><footer>독립 HTML · 외부 라이브러리/폰트/로그인 불필요 · 비밀번호·키 미포함<br>로컬: http://127.0.0.3:13000/login · 공개 HTTPS 최신 배포/실기기 접속은 별도 확인 대상입니다.</footer></main><script>
(()=>{let current=0;const cards=[...document.querySelectorAll('.scene')],buttons=[...document.querySelectorAll('[data-step]')];function select(i){current=Math.max(0,Math.min(cards.length-1,i));cards.forEach((c,n)=>{c.hidden=n!==current});buttons.forEach((b,n)=>{if(n===current)b.setAttribute('aria-current','step');else b.removeAttribute('aria-current')});document.getElementById('previous').disabled=current===0;document.getElementById('next').disabled=current===cards.length-1;document.getElementById('position').textContent=(current+1)+' / '+cards.length+' · 좌우 방향키로 이동'}buttons.forEach(b=>b.addEventListener('click',()=>select(Number(b.dataset.step))));document.getElementById('previous').addEventListener('click',()=>select(current-1));document.getElementById('next').addEventListener('click',()=>select(current+1));document.addEventListener('keydown',e=>{if(['INPUT','TEXTAREA','SELECT','BUTTON'].includes(e.target.tagName)||e.ctrlKey||e.metaKey||e.altKey||e.shiftKey)return;if(e.key==='ArrowLeft'){e.preventDefault();select(current-1)}if(e.key==='ArrowRight'){e.preventDefault();select(current+1)}});document.getElementById('print').addEventListener('click',()=>window.print());})();
</script></body></html>`;
fs.writeFileSync(path.join(dir, '시연 가이드.html'), html, 'utf8');
console.log(`Built ${scenes.length} scenes (${Buffer.byteLength(html)} bytes)`);
