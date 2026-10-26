const fs = require('fs'), ts = require('typescript'), assert = require('assert/strict'), React = require('react');
function load(file, mocks) {
  const m = { exports: {} };
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText;
  new Function('require', 'module', 'exports', code)(n => mocks[n] ?? require(n), m, m.exports); return m.exports.default;
}
function nodes(t) { return !t || typeof t !== 'object' ? [] : Array.isArray(t) ? t.flatMap(nodes) : [t, ...nodes(t.props?.children)]; }
function harness(file, props, mocks) {
  let state = [], cursor = 0, effects = [], tree;
  const hooks = { ...React,
    useState(initial) { const i = cursor++; if (!(i in state)) state[i] = initial; return [state[i], v => { state[i] = typeof v === 'function' ? v(state[i]) : v; }]; },
    useRef(initial) { const i = cursor++; if (!(i in state)) state[i] = { current: initial }; return state[i]; },
    useCallback: f => f, useEffect: f => effects.push(f), useLayoutEffect() {},
  };
  const Component = load(file, { ...mocks, react: hooks });
  const render = () => { cursor = 0; effects = []; tree = Component(props); return tree; };
  return { render, effects: () => effects, all: () => nodes(tree), find: predicate => nodes(tree).find(predicate) };
}
global.window = { dispatchEvent() {}, addEventListener() {}, removeEventListener() {}, setInterval() { return 1; }, clearInterval() {} };
global.document = { hidden: false, addEventListener() {}, removeEventListener() {} };
async function main() {
  const task = { id: 't', projectId: 'p', title: '조사', status: 'IN_PROGRESS', assignees: [], priority: 'MEDIUM', completionRequestedAt: 'now' };
  const drafts = [], busy = []; let calls = 0, reject, finish;
  const panel = harness('src/components/kanban/TaskCompletionPanel.tsx', { task, disabled: false, onChanged() {}, onBusy: b => busy.push(b), onDraftChange: d => drafts.push(d) }, {
    '@/lib/api': { get: async () => ({ data: { task, checks: [], canReview: true, canApprove: true } }), put: () => { calls++; return new Promise((resolve, nope) => { finish = resolve; reject = nope; }); } },
    '@/lib/apiError': { apiError: () => '저장 실패' }, '@/hooks/useMyRole': { useMyRole: () => 'MEMBER' }, '@/lib/sessionCache': { sessionGeneration: () => 0 }, '@/lib/api/member': { getMembers: async () => ({ data: [] }) }, '@/lib/reviewers': { eligibleReviewers: () => [] },
  });
  panel.render(); panel.effects().forEach(f => f()); await new Promise(setImmediate); panel.render();
  panel.find(n => n.type === 'button' && n.props.children === '보완 요청').props.onClick(); panel.render();
  panel.find(n => n.type === 'textarea').props.onChange({ target: { value: '출처를 추가해 주세요' } }); panel.render();
  panel.effects()[0](); assert.equal(drafts.at(-1), true);
  const send = panel.find(n => n.type === 'button' && n.props.children === '보완 내용 전달').props.onClick;
  const first = send(); await send(); assert.equal(calls, 1, '같은 렌더에서 중복 클릭도 1회 요청');
  reject(Error('offline')); await first; panel.render();
  assert.equal(panel.find(n => n.type === 'textarea').props.value, '출처를 추가해 주세요');
  assert.ok(panel.find(n => n.props?.role === 'alert'));
  const retry = panel.find(n => n.type === 'button' && n.props.children === '보완 내용 전달').props.onClick();
  finish({ data: { ...task, completionRequestedAt: null } }); await retry; panel.render(); panel.effects()[0]();
  assert.equal(drafts.at(-1), false); assert.equal(busy.at(-1), false);

  let closed = 0;
  const modal = harness('src/components/kanban/TaskModal.tsx', { mode: 'edit', task, members: [], onClose: () => closed++, onCreate() {}, onUpdate() {}, onDelete() {} }, {
    '@/types/task': { KANBAN_COLUMNS: [] }, '@/components/SmartDateInput': 'DateInput', '@/lib/apiError': {},
    './TaskResources': 'Resources', './TaskCompletionPanel': 'Completion', './TaskSteps': 'Steps', './TaskDiscussion': 'Discussion', '@/lib/due': {},
  });
  modal.render();
  assert.ok(modal.find(n => n.type === 'nav' && n.props['aria-label'] === '업무 안에서 바로 이동'));
  const ordered = modal.all().map(n => n.type);
  assert.ok(ordered.indexOf('Resources') < ordered.indexOf('Completion'));
  assert.ok(ordered.indexOf('Completion') < ordered.indexOf('Discussion'));
  modal.find(n => n.type === 'Completion').props.onDraftChange(true); modal.render();
  modal.find(n => n.type === 'button' && n.props['aria-label'] === '닫기').props.onClick(); modal.render();
  assert.equal(closed, 0); assert.ok(modal.find(n => n.props?.role === 'alert'), '보완 사유 작성 중 닫기 확인');
  let created;
  const simple = harness('src/components/kanban/TaskModal.tsx', { mode: 'create', task: null, members: [], onClose() {}, onCreate: async value => { created = value; }, onUpdate() {}, onDelete() {} }, {
    '@/types/task': { KANBAN_COLUMNS: [] }, '@/components/SmartDateInput': 'DateInput', '@/lib/apiError': {},
    './TaskResources': 'Resources', './TaskCompletionPanel': 'Completion', './TaskSteps': 'Steps', './TaskDiscussion': 'Discussion', '@/lib/due': {},
  });
  simple.render();
  const advanced = simple.find(n => n.type === 'details'); assert.ok(advanced); assert.ok(!advanced.props.open);
  assert.ok(nodes(advanced).some(n => n.props?.id === 'task-predecessor'));
  assert.ok(!nodes(advanced).some(n => n.props?.id === 'completion-criteria'), '결과 기준은 기본 화면에 유지');
  simple.find(n => n.type === 'input' && n.props.maxLength === 255).props.onChange({target:{value:'자료 조사'}}); simple.render();
  await simple.find(n => n.type === 'form').props.onSubmit({preventDefault(){}});
  assert.equal(created.title,'자료 조사'); assert.equal(created.attachmentRequired,false);
  assert.equal(created.predecessorId,undefined); assert.equal(created.completionCriteria,'');
  assert.deepEqual(created.assigneeIds,[]); assert.equal(created.dueDate,undefined);
  console.log('업무 생성 간소화 검사 통과: 추가 설정 기본 접힘·제목만 생성·선택 조건 강제 없음');
  console.log('업무 동선 검사 통과: 확인 영역 순서·바로 이동·보완 입력 보호·실패 보존·중복 요청 방지');
}
main().catch(e => { console.error(e); process.exitCode = 1; });
