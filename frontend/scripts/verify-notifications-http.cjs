// 전용 bb_b31 서버(127.0.0.1:18082)에만 합성 계정/프로젝트를 생성한다. 개발 DB 사용 금지.
const assert = require('assert/strict');
const base = 'http://127.0.0.1:18082/api';
async function request(user, path, body, method = body ? 'POST' : 'GET', expected = 200) {
  const response = await fetch(base + path, { method, headers: { ...(body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), ...(user.cookie ? { Cookie: user.cookie } : {}) }, body: body ? body instanceof FormData ? body : JSON.stringify(body) : undefined });
  const cookies = response.headers.getSetCookie(); if (cookies.length) user.cookie = cookies.map(v => v.split(';')[0]).join('; ');
  assert.equal(response.status, expected, `${method} ${path}: HTTP status`);
  if (expected >= 400 || expected === 204) return;
  return response.json();
}
async function main() {
  const stamp = Date.now(); const people = [];
  for (let i = 0; i < 4; i++) {
    const user = {};
    await request(user, '/auth/signup', { email: `notification-${stamp}-${i}@example.test`, name: `검증팀원${i}`, password: `Test-only-${stamp}-password` }, 'POST', 201);
    user.id = (await request(user, '/auth/profile')).id; people.push(user);
  }
  const [leader, worker, reviewer, peer] = people;
  const project = await request(leader, '/projects', { name: `K54 HTTP 검증 ${stamp}` }, 'POST', 201);
  for (const user of people.slice(1)) await request(user, '/projects/join', { inviteCode: project.inviteCode }, 'POST', 201);
  const root = `/projects/${project.id}`;
  const task = await request(leader, `${root}/tasks`, { title: 'HTTP 알림 검증', status: 'IN_PROGRESS', priority: 'MEDIUM', assigneeIds: [worker.id], completionCriteria: '출처 포함' }, 'POST', 201);
  const path = `${root}/tasks/${task.id}`;
  const file = new FormData(); file.append('file', new Blob(['K54 synthetic HTTP check'], { type: 'text/plain' }), `k54-${stamp}.txt`);
  await request(worker, `${path}/files`, file, 'POST', 200);
  const opinion = await request(worker, `${path}/messages`, { body: '출처를 확인해 주세요', recipientId: reviewer.id });
  await request(reviewer, `${path}/messages`, { body: '확인했습니다', parentId: opinion.id });
  await request(worker, `${path}/completion-request`, {}, 'POST');
  const before = await request(reviewer, `${root}/notifications`);
  assert.ok(before.items.some(i => i.type === 'TASK_FILE_UPLOADED'));
  assert.ok(before.items.some(i => i.type === 'TASK_OPINION_REQUESTED'));
  assert.ok(before.items.some(i => i.type === 'TASK_COMPLETION_REQUESTED'));
  for (const item of before.items) {
    for (const key of ['id','type','projectId','actorName','taskId','taskTitle','fileId','fileName','fileVersion','messageId','createdAt','readAt','targetAvailable']) assert.ok(Object.hasOwn(item, key), key);
    assert.match(item.createdAt, /Z$/); assert.equal(item.readAt, null);
  }
  const id = before.items[0].id;
  const read = await request(reviewer, `${root}/notifications/${id}/read`, undefined, 'PUT');
  assert.match(read.readAt, /Z$/);
  assert.deepEqual(await request(reviewer, `${root}/notifications/${id}/read`, undefined, 'PUT'), read);
  await request(peer, `${root}/notifications/${id}/read`, undefined, 'PUT', 404);
  await request(reviewer, `${root}/notifications?limit=nope`, undefined, 'GET', 400);
  await request(reviewer, `${root}/notifications?cursor=`, undefined, 'GET', 400);
  const first = await request(reviewer, `${root}/notifications?limit=1`);
  const second = await request(reviewer, `${root}/notifications?limit=1&cursor=${encodeURIComponent(first.nextCursor)}`);
  assert.notEqual(first.items[0].id, second.items[0].id);
  await request(reviewer, `${path}/completion-decision`, { approved: false, reason: '출처 보완' }, 'PUT');
  const workerInbox = await request(worker, `${root}/notifications`);
  assert.ok(workerInbox.items.some(i => i.type === 'TASK_OPINION_REPLIED'));
  assert.ok(workerInbox.items.some(i => i.type === 'TASK_REVISION_REQUESTED'));
  assert.ok(!workerInbox.items.some(i => i.type === 'TASK_FILE_UPLOADED'));
  console.log('실제 HTTP 검증 통과: 4인 합성 계정, 5종 알림, JSON null 키·UTC, 읽음 멱등·개인 권한, 잘못된 query 400, cursor 페이지');
}
main().catch(e => { console.error(e.message); process.exitCode = 1; });
