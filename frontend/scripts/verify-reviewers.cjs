// 확인해 줄 수 있는 팀원 계산(lib/reviewers.ts)이 K-58 REVIEWER 기준과 맞는지, 동명이인을 잘못 빼지 않는지 확인한다
const fs = require('fs'), ts = require('typescript'), assert = require('assert/strict');
const code = ts.transpileModule(fs.readFileSync('src/lib/reviewers.ts', 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const m = { exports: {} }; new Function('require', 'module', 'exports', code)(require, m, m.exports);
const { eligibleReviewers } = m.exports;

const member = (userId, name, role = 'MEMBER') => ({ memberId: 'm' + userId, userId, name, email: '', role, joinedAt: '' });
const team = [member('a', '김민지', 'LEADER'), member('b', '최현우'), member('c', '박유나'), member('d', '이지호'), member('o', '관람객', 'OBSERVER')];
const task = (over) => ({ assignees: [{ userId: 'b', name: '최현우' }], createdBy: 'a', completionRequestedAt: null, completionRequestedByName: null, ...over });
const names = (list) => list && list.map((x) => x.name).join(',');

// 요청 전: 담당자(b)와 지금 요청하려는 나(b), 관찰자를 뺀다
assert.equal(names(eligibleReviewers(task({}), team, 'b')), '김민지,박유나,이지호');
// 요청 전, 팀장(a)이 대신 요청하려는 경우: 나(a)도 빠진다
assert.equal(names(eligibleReviewers(task({}), team, 'a')), '박유나,이지호');
// 담당자가 없으면 만든 사람(a)이 수행자
assert.equal(names(eligibleReviewers(task({ assignees: [] }), team, 'c')), '최현우,이지호');
// 요청 중: ID로 뺀다. 같은 이름(김민지)의 다른 팀원은 그대로 남는다
const twin = [...team, member('b2', '김민지')];
assert.equal(names(eligibleReviewers(task({ completionRequestedAt: 'now', completionRequestedBy: 'a', completionRequestedByName: '김민지' }), twin, 'c')), '박유나,이지호,김민지');
// 요청 중, ID가 없으면 누구인지 모르므로 이름으로 추측하지 않고 목록을 내지 않는다
assert.equal(eligibleReviewers(task({ completionRequestedAt: 'now', completionRequestedBy: null, completionRequestedByName: '김민지' }), team, 'c'), null);
console.log('확인 팀원 계산 검사 통과: 요청 전 나·담당자·관찰자 제외, 담당자 없을 때 만든 사람, 요청 중 ID로만 제외(동명이인 유지), ID 없으면 목록 생략');
