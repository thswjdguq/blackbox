const fs = require('fs'), ts = require('typescript'), assert = require('assert/strict');
const React = require('react'), { renderToStaticMarkup } = require('react-dom/server');
function load(file, mocks) {
  const module = { exports: {} };
  const code = ts.transpileModule(fs.readFileSync(file, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText;
  new Function('require', 'module', 'exports', code)(name => mocks[name] ?? require(name), module, module.exports);
  return module.exports;
}
async function main() {
  let reset, calls = [];
  const api = { get: () => new Promise((resolve, reject) => calls.push({ resolve, reject })) };
  const { useProfileStore: store } = load('src/lib/store/profileStore.ts', { '@/lib/api': api, '@/lib/sessionCache': { onSessionReset: fn => { reset = fn; } } });
  const first = store.getState().load(); const duplicate = store.getState().load();
  assert.equal(calls.length, 1); assert.equal(first, duplicate);
  reset();
  const second = store.getState().load();
  calls[0].resolve({ data: { id: 'old', name: '이전 계정', email: 'old@example.test' } });
  await first; assert.equal(store.getState().profile, null, '늦은 이전 계정 응답 제외');
  calls[1].resolve({ data: { id: 'new', name: '김민지', email: 'minji@example.test', role: 'STUDENT' } });
  await second; assert.equal(store.getState().profile.name, '김민지');
  reset(); const stale = store.getState().load();
  store.getState().update({ id: 'new', name: '김수정', email: 'minji@example.test', role: 'STUDENT' });
  calls[2].resolve({ data: { id: 'new', name: '김민지' } }); await stale;
  assert.equal(store.getState().profile.name, '김수정', '저장한 이름을 이전 조회가 덮어쓰지 않음');
  const Identity = load('src/components/AccountIdentity.tsx', {
    react: { ...React, useEffect() {} }, 'next/link': 'a', '@/lib/store/profileStore': { useProfileStore: () => store.getState() },
  }).default;
  const html = renderToStaticMarkup(React.createElement(Identity));
  assert.ok(html.includes('김수정')); assert.ok(html.includes('minji@example.test')); assert.ok(html.includes('김수')); assert.ok(html.includes('/profile/settings'));
  const compact = renderToStaticMarkup(React.createElement(Identity, { compact: true }));
  assert.ok(compact.includes('aria-label="김수정 (minji@example.test)'));
  reset(); const failed = store.getState().load(); calls[3].reject(Error('offline')); await failed;
  const failure = renderToStaticMarkup(React.createElement(Identity));
  assert.ok(failure.includes('계정 정보 다시 확인')); assert.ok(!failure.includes('김수정'));
  const retry = store.getState().load(); assert.equal(calls.length, 5); calls[4].resolve({ data: { id: 'last', name: '새 계정', email: 'new@example.test' } }); await retry;
  console.log('계정 표시 검사 통과: 중복 조회 방지·계정 전환·이름 수정·지연 응답·오류 재시도·모바일 접근성');
}
main().catch(e => { console.error(e); process.exitCode = 1; });
