const fs = require('node:fs'), ts = require('typescript'), assert = require('node:assert/strict');
let state = [], cursor = 0, effects = [], value = '', validity = '';
const hooks = {
  useState(initial) { const i = cursor++; if (!(i in state)) state[i] = initial; return [state[i], next => { state[i] = typeof next === 'function' ? next(state[i]) : next; }]; },
  useRef(initial) { const i = cursor++; if (!(i in state)) state[i] = {current: initial}; return state[i]; },
  useEffect(fn) { effects.push(fn); },
};
const moduleValue = {exports: {}};
const code = ts.transpileModule(fs.readFileSync('src/components/SmartDateInput.tsx', 'utf8'), {compilerOptions: {module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX}}).outputText;
new Function('require', 'module', 'exports', code)(name => name === 'react' ? hooks : require(name), moduleValue, moduleValue.exports);
const {dateInputError: error, default: DateInput} = moduleValue.exports;
for (const args of [['', '', ''], ['2026', '10', '19'], ['2024', '2', '29'], ['2000', '2', '29'], ['2026', '1', '1', '0', '0', true]]) assert.equal(error(...args), '');
for (const args of [['202', '1', '1'], ['2026', '22', '1'], ['2026', '2', '30'], ['1900', '2', '29'], ['2026', '4', '31'], ['2026', '1', '0'], ['2026', '', '1'], ['2026', '1', '1', '24', '00', true]]) assert.ok(error(...args));
function nodes(tree) { if (!tree || typeof tree !== 'object') return []; if (Array.isArray(tree)) return tree.flatMap(nodes); return [tree, ...nodes(tree.props?.children)]; }
function render() {
  cursor = 0; effects = [];
  const tree = DateInput({value, onChange: next => {value = next;}});
  for (const n of nodes(tree)) if (n.type === 'input') n.ref.current = {focus() {}, setCustomValidity(message) {validity = message;}};
  effects.forEach(fn => fn());
  return tree;
}
function enter(label, text) { const field = nodes(render()).find(n => n.props?.['aria-label'] === label); field.props.onChange({target: {value: text}}); render(); }
render(); enter('연도', '2026'); enter('월', '22'); enter('일', '12');
assert.equal(value, '', '잘못된 날짜는 API 값으로 내보내지 않음'); assert.ok(validity, '브라우저 폼 전송도 차단');
assert.ok(nodes(render()).some(n => n.props?.role === 'alert'));
enter('월', '10'); assert.equal(value, '2026-10-12'); assert.equal(validity, '');
enter('일', '99'); assert.equal(value, '2026-10-12'); assert.ok(validity, '이전 유효값이 있어도 잘못 고친 날짜의 폼 제출은 차단');
enter('일', '19'); assert.equal(value, '2026-10-19'); assert.equal(validity, '');
enter('연도', ''); enter('월', ''); enter('일', ''); assert.equal(value, ''); assert.equal(validity, '', '선택 날짜는 전체 지우기 가능');
console.log('날짜 입력 검사 통과: 윤년·월별 일수·불완전 입력·시간 범위·오류 표시·잘못된 전송 차단·수정 후 복구');
