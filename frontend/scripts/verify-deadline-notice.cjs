const fs = require('fs'), ts = require('typescript'), assert = require('assert/strict');
const React = require('react'), { renderToStaticMarkup } = require('react-dom/server');
function load(path, resolve = require) {
  const source = fs.readFileSync(path, 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', compiled)(resolve, module, module.exports);
  return module.exports;
}
const due = load('src/lib/due.ts');
const now = new Date(2026, 9, 14, 23, 59);
for (const [date, tone, label] of [
  ['2026-10-13', 'overdue', '1일 초과'], ['2026-10-14', 'today', '오늘 마감'],
  ['2026-10-15', 'soon', '임박 · D-1'], ['2026-10-17', 'soon', '임박 · D-3'],
  ['2026-10-18', 'later', '여유 · D-4'],
]) {
  assert.equal(due.dueTone(date, 3, now), tone);
  assert.equal(due.dueLabel(date, now), label);
}
assert.equal(due.daysLeft('2027-01-01', new Date(2026, 11, 31)), 1);
assert.equal(due.daysLeft('2028-03-01', new Date(2028, 1, 29)), 1);
assert.equal(due.daysLeft('2026-10-14', new Date(2026, 9, 15, 0, 0)), -1);
const Notice = load('src/components/kanban/DeadlineNotice.tsx', name => {
  if (name === 'react') return { ...React, useState: () => [now, () => {}], useEffect: () => {} };
  if (name === '@/lib/due') return due;
  if (name === 'next/link') return props => React.createElement('a', props);
  return require(name);
}).default;
const task = (id, dueDate, status = 'IN_PROGRESS') => ({ id, title: id, projectId: 'project', dueDate, status, assignees: [{ name: '담당자', userId: 'user' }] });
const html = renderToStaticMarkup(React.createElement(Notice, { tasks: [
  task('late', '2026-10-11'), task('today', '2026-10-14'), task('soon', '2026-10-17'),
  task('later', '2026-10-18'), task('completed', '2026-10-01', 'DONE'), task('no-date', null),
] }));
assert.ok(html.includes('3일 초과'));
assert.ok(html.includes('확인이 필요한 업무 3개'));
assert.ok(html.includes('/projects/project/board?task=late'));
assert.ok(html.includes('담당자'));
assert.ok(!html.includes('task=completed'));
assert.ok(!html.includes('task=later'));
assert.ok(!html.includes('task=no-date'));
const empty = renderToStaticMarkup(React.createElement(Notice, { tasks: [task('done', '2026-10-01', 'DONE')] }));
assert.ok(empty.includes('기한을 초과한 미완료 업무가 없습니다'));
console.log('마감 검사 통과: 자정·월/연도 경계·윤년, 1/3/4일, 완료·기한 없음 제외, 담당자·업무 링크·빈 상태');
