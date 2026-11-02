const fs = require('fs'), ts = require('typescript'), assert = require('assert/strict');
const React = require('react'), { renderToStaticMarkup } = require('react-dom/server');
function load(file, mocks = {}) {
  const module = { exports: {} };
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText;
  new Function('require', 'module', 'exports', code)(name => mocks[name] ?? require(name), module, module.exports);
  return module.exports;
}
const views = load('src/lib/taskView.ts');
const types = load('src/types/task.ts');
const storage = new Map();
global.localStorage = { getItem: k => storage.get(k) ?? null, setItem: (k, v) => storage.set(k, v) };
const task = (id, status, users, extras = {}) => ({ id, projectId: 'p', title: id, status, assignees: users.map(userId => ({ userId, name: userId })), dueDate: '2026-10-20', completionRequestedAt: null, deliverableId: 'd', ...extras });
const tasks = [task('mine', 'TODO', ['me']), task('other', 'IN_PROGRESS', ['other']), task('done', 'DONE', ['me']), task('unassigned', 'TODO', []), task('review', 'IN_PROGRESS', ['other'], { completionRequestedAt: 'now' })];
function nodes(tree) {
  if (!tree || typeof tree !== 'object') return [];
  if (Array.isArray(tree)) return tree.flatMap(nodes);
  return [tree, ...nodes(tree.props?.children)];
}
function harness(role = 'MEMBER', query = '', overrides = {}) {
  let cursor = 0, effectCursor = 0, effects = [], state = [], dependencies = [], tree;
  const patchCalls = [], changes = [];
  const props = { projectId: 'p', userId: 'me', role, initialTasks: tasks, members: [], scoreMap: {}, readOnly: role === 'OBSERVER', onClearAssignee() {}, onResetFilters() {}, onTasksChange: t => changes.push(t) };
  const api = { get: async url => ({ data: url.endsWith('/completion') ? { canReview: true } : [] }), patch: async (url, body) => { patchCalls.push(body); return { data: { ...tasks[0], status: body.status } }; }, ...overrides };
  const hooks = { ...React,
    useState: initial => { const i = cursor++; if (!(i in state)) state[i] = typeof initial === 'function' ? initial() : initial; return [state[i], next => { state[i] = typeof next === 'function' ? next(state[i]) : next; }]; },
    useRef: initial => { const i = cursor++; if (!(i in state)) state[i] = { current: initial }; return state[i]; },
    useEffect: (fn, deps) => { const i = effectCursor++; if (!dependencies[i] || deps.some((d, j) => d !== dependencies[i][j])) { dependencies[i] = deps; effects.push(fn); } },
    useMemo: fn => fn(), useCallback: fn => fn,
  };
  const Board = load('src/components/kanban/KanbanBoard.tsx', {
    react: hooks, 'next/link': 'a', 'next/navigation': { useSearchParams: () => new URLSearchParams(query) },
    '@/components/agent/AgentButton': 'AgentButton', '@/components/agent/AiBadge': { useAiOrigins: () => ({ task: new Set() }) },
    '@/lib/store/agentStore': { useAgentStore: fn => fn({ recordsVersion: 0 }) },
    '@/lib/apiError': { apiError: (_e, fallback) => fallback, PartialSaveError: Error }, '@/lib/api': api,
    '@/types/task': types, '@/lib/taskView': views, './KanbanColumn': 'KanbanColumn', './TaskModal': 'TaskModal', './TaskListView': 'TaskListView',
  }).default;
  const render = () => { cursor = 0; effectCursor = 0; tree = Board(props); return tree; };
  const settle = async () => { for (let i = 0; i < 5; i++) { render(); const batch = effects; effects = []; batch.forEach(fn => fn()); await new Promise(resolve => setImmediate(resolve)); } render(); };
  return { render, settle, props, patchCalls, changes, find: type => nodes(tree).find(n => n.type === type), all: () => nodes(tree) };
}
async function main() {
  assert.deepEqual(views.defaultTaskView('MEMBER'), { view: 'list', scope: 'mine', showDone: false });
  for (const role of ['LEADER', 'OBSERVER']) assert.equal(views.defaultTaskView(role).scope, 'all');
  assert.equal(views.restoreTaskView('{bad', 'MEMBER').scope, 'mine');
  assert.equal(views.restoreTaskView('{"view":"board","scope":"mine","showDone":true}', 'OBSERVER').scope, 'all');
  const member = harness(); await member.settle();
  assert.deepEqual(member.find('TaskListView').props.tasks.map(t => t.id), ['mine']);
  const leader = harness('LEADER'); await leader.settle();
  assert.equal(leader.find('TaskListView').props.tasks.length, 4);
  const linked = harness('MEMBER', 'deliverable=d'); await linked.settle();
  assert.equal(linked.find('TaskListView').props.tasks.length, 4, '제출물 링크는 다른 담당자의 연결 업무도 표시');
  const deep = harness('MEMBER', 'task=done'); await deep.settle();
  assert.equal(deep.find('TaskModal').props.task.id, 'done', '완료 숨김 상태에서도 직접 링크 열기');
  const move = member.find('TaskListView').props.onMove;
  await move('mine', 'IN_PROGRESS'); member.render();
  assert.deepEqual(member.patchCalls, [{ status: 'IN_PROGRESS' }]);
  assert.equal(member.changes[0][0].status, 'IN_PROGRESS');
  await member.find('TaskListView').props.onMove('mine', 'DONE'); member.render();
  assert.equal(member.patchCalls.length, 1, '완료 API 우회 금지');
  assert.equal(member.find('TaskModal').props.task.id, 'mine');
  const failed = harness('MEMBER', '', { patch: async () => { throw Error('offline'); } }); await failed.settle();
  await failed.find('TaskListView').props.onMove('mine', 'IN_PROGRESS'); failed.render();
  assert.equal(failed.find('TaskListView').props.tasks[0].status, 'TODO');
  assert.ok(failed.all().some(n => n.props?.role === 'alert'));
  const observer = harness('OBSERVER'); await observer.settle();
  await observer.find('TaskListView').props.onMove('mine', 'IN_PROGRESS');
  assert.equal(observer.patchCalls.length, 0);
  const reviewButton = leader.all().find(n => n.type === 'button' && n.props.children?.[0] === '내가 검토할 업무');
  reviewButton.props.onClick(); leader.render();
  assert.deepEqual(leader.find('TaskListView').props.tasks.map(t => t.id), ['review']);
  const remembered = harness('LEADER'); await remembered.settle();
  assert.deepEqual(remembered.find('TaskListView').props.tasks.map(t => t.id), ['review']);
  assert.ok(storage.has('task-view-v2:me:p:LEADER'));
  assert.ok(!storage.has('task-view-v2:other:p:LEADER'));
  const deniedReview = harness('LEADER', '', { get: async url => ({ data: url.endsWith('/completion') ? { canReview: false } : [] }) });
  await deniedReview.settle();
  assert.equal(deniedReview.find('TaskListView').props.tasks.length, 0, '서버가 검토 권한을 주지 않은 업무 제외');
  const failedReview = harness('LEADER', '', { get: async url => { if (url.endsWith('/completion')) throw Error('offline'); return { data: [] }; } });
  await failedReview.settle();
  assert.ok(failedReview.all().some(n => n.props?.role === 'alert'));
  assert.equal(failedReview.find('TaskListView'), undefined, '검토 조회 실패를 업무 없음으로 표시하지 않음');
  let finish;
  const concurrent = harness('MEMBER', '', { patch: async () => new Promise(resolve => { finish = resolve; }) });
  await concurrent.settle();
  const first = concurrent.find('TaskListView').props.onMove('mine', 'IN_PROGRESS');
  let secondFinished = false;
  await concurrent.find('TaskListView').props.onMove('mine', 'IN_PROGRESS').then(() => { secondFinished = true; });
  assert.ok(secondFinished, '진행 중 동일 요청은 추가 발송하지 않음');
  finish({ data: { ...tasks[0], status: 'IN_PROGRESS' } }); await first;
  const Control = load('src/components/kanban/TaskStatusControl.tsx', { '@/types/task': types }).default;
  for (const t of [tasks[2], tasks[4]]) {
    const html = renderToStaticMarkup(React.createElement(Control, { task: t, onMove: async () => {}, onEdit() {} }));
    assert.ok(!html.includes('<select'), '완료·검토 대기 상태 잠금');
  }
  const html = renderToStaticMarkup(React.createElement(Control, { task: tasks[0], onMove: async () => {}, onEdit() {} }));
  assert.ok(html.includes('완료 요청')); assert.ok(!html.includes('value="DONE"'));
  console.log('업무 보기 검사 통과: 역할 기본값, 저장 복원·계정 분리, 제출물·업무 직접 링크, 상태 저장·실패 보존, 완료 우회 차단, 관찰자·검토 권한');
}
main().catch(e => { console.error(e); process.exitCode = 1; });
