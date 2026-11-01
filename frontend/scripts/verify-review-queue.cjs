// 대시보드 "확인할 결과"(ReviewQueue): 늦게 온 옛 응답·계정 변경 뒤 응답을 버리고, 빈 프로젝트에서 바로 비우는지 확인한다
const fs = require('fs'), ts = require('typescript'), assert = require('assert/strict'), React = require('react');

function load(file, mocks) {
  const m = { exports: {} };
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText;
  new Function('require', 'module', 'exports', code)(n => mocks[n] ?? require(n), m, m.exports); return m.exports.default;
}
function harness(file, mocks) {
  let state = [], cursor = 0, effects = [], cleanups = [], ran = new Set();
  const hooks = { ...React,
    useState(initial) { const i = cursor++; if (!(i in state)) state[i] = initial; return [state[i], v => { state[i] = typeof v === 'function' ? v(state[i]) : v; }]; },
    useRef(initial) { const i = cursor++; if (!(i in state)) state[i] = { current: initial }; return state[i]; },
    useCallback(f, deps) { const i = cursor++; const prev = state[i]; if (prev && deps.every((d, k) => d === prev.deps[k])) return prev.f; state[i] = { f, deps }; return f; },
    useEffect(f, deps) { const i = cursor++; const prev = state[i]; if (prev && deps.every((d, k) => d === prev.deps[k])) return; state[i] = { deps }; effects.push([i, f]); },
  };
  const Component = load(file, { ...mocks, react: hooks });
  let tree;
  return {
    render(props) { cursor = 0; effects = []; tree = Component(props); for (const [i, f] of effects) { if (cleanups[i]) cleanups[i](); cleanups[i] = f() || null; } return tree; },
    unmount() { cleanups.forEach(c => c && c()); },
    text() { const out = []; const walk = t => { if (t == null || typeof t === 'boolean') return; if (typeof t === 'string' || typeof t === 'number') { out.push(String(t)); return; } if (Array.isArray(t)) return t.forEach(walk); walk(t.props?.children); }; walk(tree); return out.join(' '); },
  };
}

const listeners = {};
global.window = { addEventListener: (e, f) => { (listeners[e] ??= new Set()).add(f); }, removeEventListener: (e, f) => listeners[e]?.delete(f), setInterval: () => 1, clearInterval() {} };
global.document = { hidden: false };
const fire = (e) => [...(listeners[e] ?? [])].forEach(f => f());
const tick = () => new Promise(setImmediate);

// 조정 가능한 가짜 API: 요청마다 열린 약속을 남기고, 시험이 원하는 순서로 끝낸다
let generation = 0;
const pending = [];
const api = { get: (url) => new Promise((resolve, reject) => pending.push({ url, resolve, reject })) };
const answer = (match, data) => { const i = pending.findIndex(p => p.url.includes(match)); assert.ok(i >= 0, `열린 요청 없음: ${match}`); const [p] = pending.splice(i, 1); p.resolve({ data }); };
const mocks = { '@/lib/api': api, '@/lib/sessionCache': { sessionGeneration: () => generation }, 'next/link': 'a', 'lucide-react': { ChevronRight: 'i', ClipboardCheck: 'i', RefreshCw: 'i' } };

const task = (id, title, assignee) => ({ id, title, projectId: 'p', status: 'IN_PROGRESS', assignees: [{ userId: assignee }], completionRequestedAt: '2026-10-26T00:00:00Z', completionRequestedByName: '최현우' });
const projects = [{ id: 'p', name: '시험 프로젝트', myRole: 'MEMBER' }];

async function main() {
  // 1. 정상: 확인할 수 있는 요청 하나
  const q = harness(process.env.RQ_FILE || 'src/components/dashboard/ReviewQueue.tsx', mocks);
  q.render({ projects }); await tick();
  answer('/auth/profile', { id: 'me' }); await tick();
  answer('/projects/p/tasks', [task('t1', '첫 요청', 'other')]); await tick();
  answer('/tasks/t1/completion', { canReview: true }); await tick();
  q.render({ projects });
  assert.ok(q.text().includes('첫 요청'), '확인할 요청이 보여야 함');

  // 2. 응답 역전: 먼저 시작한 조회가 나중에 끝나도 최신 조회 결과가 남는다
  fire('focus'); await tick();                 // 조회 A 시작
  fire('task-feedback-changed'); await tick(); // 조회 B 시작
  // B 먼저 끝남: 요청이 처리돼 비어 있음
  const bProfile = pending.filter(p => p.url.includes('/auth/profile')).pop(); pending.splice(pending.indexOf(bProfile), 1); bProfile.resolve({ data: { id: 'me' } }); await tick();
  const bTasks = pending.filter(p => p.url.includes('/projects/p/tasks')).pop(); pending.splice(pending.indexOf(bTasks), 1); bTasks.resolve({ data: [] }); await tick();
  // A 나중에 끝남: 이미 지난 조회라 다음 단계(업무 목록)로 가지 않고, 화면도 바뀌지 않는다
  answer('/auth/profile', { id: 'me' }); await tick();
  assert.equal(pending.length, 0, '지난 조회는 이어서 요청하지 않음');
  q.render({ projects });
  assert.ok(!q.text().includes('첫 요청'), '늦게 끝난 옛 조회가 최신 결과를 덮으면 안 됨');

  // 3. 계정 변경: 조회 중에 세션이 바뀌면 그 응답은 버린다
  fire('focus'); await tick();
  generation++;
  answer('/auth/profile', { id: 'me' }); await tick();
  assert.equal(pending.filter(p => p.url.includes('/projects/p/tasks')).length, 0, '세션이 바뀐 뒤에는 이어서 조회하지 않음');
  q.render({ projects });
  assert.ok(!q.text().includes('첫 요청'));

  // 4. 프로젝트가 없어지면 진행 중인 조회를 버리고 바로 비운다
  fire('focus'); await tick();
  q.render({ projects: [] }); await tick();
  answer('/auth/profile', { id: 'me' }); await tick();
  q.render({ projects: [] });
  assert.equal(pending.filter(p => p.url.includes('/projects/p/tasks')).length, 0, '빈 프로젝트에서는 옛 조회를 이어가지 않음');
  assert.equal(q.text(), '', '빈 프로젝트면 아무것도 그리지 않음');

  // 5. 화면을 떠나면 갱신 이벤트를 해제한다
  q.unmount();
  assert.equal((listeners.focus?.size ?? 0) + (listeners['task-feedback-changed']?.size ?? 0), 0, '언마운트 뒤 이벤트 해제');
  console.log('확인할 결과 검사 통과: 정상 표시, 응답 역전 시 최신 유지, 계정 변경 뒤 응답 폐기, 빈 프로젝트 즉시 비움, 이벤트 해제');
}
main().catch(e => { console.error(e); process.exit(1); });
