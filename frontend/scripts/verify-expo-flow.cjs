// 전용 통합 시연 서버 전용. 합성 자료만 추가하며 기존 자료를 삭제하지 않는다.
const assert = require('assert/strict'), fs = require('fs'), path = require('path');
const base = process.env.EXPO_API_BASE || 'http://127.0.0.1:18083/api';
assert.ok(['http://127.0.0.1:18083/api', 'http://127.0.0.3:13000/api'].includes(base), '전용 시연 서버만 사용');
async function api(user, route, body, method = body ? 'POST' : 'GET', expected = 200) {
  const res = await fetch(base + route, { method, headers: { ...(body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), ...(user.cookie ? { Cookie: user.cookie } : {}) }, body: body ? body instanceof FormData ? body : JSON.stringify(body) : undefined });
  const cookies = res.headers.getSetCookie(); if (cookies.length) user.cookie = cookies.map(v => v.split(';')[0]).join('; ');
  const text = await res.text();
  assert.equal(res.status, expected, `${method} ${route}: ${res.status} ${res.status !== expected ? text.slice(0, 250) : ''}`);
  return text ? JSON.parse(text) : null;
}
function file(name, content) { const body = new FormData(); body.append('file', new Blob([content], { type: 'text/plain' }), name); return body; }
async function main() {
  const stamp = Date.now(), password = 'ExpoLocal-2026-Only!';
  const users = [];
  for (const [i,name] of ['김민지','최현우','박유나','이지호','전시관람객','외부검증'].entries()) {
    const user = { name, email: `expo-${stamp}-${i}@example.test` };
    await api(user, '/auth/signup', { name, email: user.email, password }, 'POST', 201);
    user.id = (await api(user, '/auth/profile')).id; users.push(user);
  }
  const [leader,worker,reviewer,peer,observer,outsider] = users;
  const project = await api(leader, '/projects', { name: '엑스포 · 지역 상권 분석 4인 팀', description: '실제 개인정보 없는 시연용 합성 자료', endDate: '2026-10-31' }, 'POST', 201);
  const root = `/projects/${project.id}`;
  for (const user of users.slice(1,5)) {
    const member = await api(user, '/projects/join', { inviteCode: project.inviteCode }, 'POST', 201);
    if (user === observer) await api(leader, `${root}/members/${member.memberId}/role`, { role: 'OBSERVER' }, 'PATCH');
  }
  await api(outsider, `${root}/notifications`, undefined, 'GET', 403);
  await api(observer, `${root}/tasks`, { title: '관찰자 작성 차단' }, 'POST', 403);
  const deliverable = await api(leader, `${root}/deliverables`, { title: '상권 분석 최종 보고서', dueDate: '2026-10-23', submissionMethod: '학교 LMS 과제 게시판', ownerId: leader.id }, 'POST', 201);
  const d = `${root}/deliverables/${deliverable.id}`;
  const requirement = await api(leader, `${d}/requirements`, { content: '조사 결과와 출처를 포함한다', required: true }, 'POST', 201);
  const task = await api(leader, `${root}/tasks`, { title: '현장 조사 결과 정리', status: 'IN_PROGRESS', priority: 'MEDIUM', dueDate: '2026-10-20', assigneeIds: [worker.id], completionCriteria: '비교표와 출처 포함', deliverableId: deliverable.id, requirementId: requirement.id }, 'POST', 201);
  const t = `${root}/tasks/${task.id}`;
  assert.ok((await api(worker, `${root}/notifications`)).items.some(i => i.type === 'TASK_ASSIGNED'));
  const upload1 = await api(worker, `${t}/files`, file('현장조사.txt','상권 비교표 초안'));
  const messages = `${t}/messages`;
  const question = await api(worker,messages,{body:'비교 항목을 확인해주세요',recipientId:reviewer.id,fileId:upload1.file.id});
  await api(reviewer,messages,{body:'출처도 함께 정리하면 좋겠습니다',parentId:question.id});
  await api(worker,`${t}/completion-request`,{});
  const cancelView = await api(worker,`${t}/completion`); assert.equal(cancelView.canCancel,true);
  await api(peer,`${t}/completion-cancel`,{reason:'권한 없는 취소'},'POST',403);
  await api(worker,`${t}/completion-cancel`,{reason:'출처를 보완하고 다시 요청하겠습니다'});
  assert.equal((await api(worker,t)).completionRequestedAt,null);
  await api(worker,`${t}/completion-request`,{});
  await api(reviewer,`${t}/completion-decision`,{approved:false,reason:'출처를 추가해 주세요'},'PUT');
  await api(worker,`${t}/files`,file('현장조사.txt','상권 비교표와 출처 포함 최종 결과'));
  await api(worker,`${t}/completion-request`,{});
  await api(worker,`${t}/completion-decision`,{approved:true,criteriaConfirmed:true},'PUT',403);
  const done = await api(reviewer,`${t}/completion-decision`,{approved:true,criteriaConfirmed:true},'PUT'); assert.equal(done.status,'DONE');
  const allTask = await api(leader,`${root}/tasks`,{title:'담당자 조정 검증',status:'IN_PROGRESS',completionCriteria:'모두 확인',assigneeIds:users.slice(0,4).map(u=>u.id)},'POST',201);
  const all = `${root}/tasks/${allTask.id}`;
  const allView = await api(worker,`${all}/completion`); assert.ok(allView.checks.some(c=>c.code==='REVIEWER'&&!c.passed)); assert.equal(allView.canRequest,false);
  await api(worker,`${all}/completion-request`,{},'POST',409);
  await api(leader,`${all}/assignees`,{assigneeIds:[worker.id]},'PUT');
  const finalFile = await api(leader,`${root}/files`,file('상권분석_최종.txt','현장 조사·설문 결과·비교 분석 및 출처를 통합한 시연 최종 보고서'));
  const round = await api(leader,`${d}/reviews`,{fileId:finalFile.id},'POST',201);
  await api(leader,`${d}/reviews/${round.id}/decision`,{decision:'APPROVED'},'PUT',403);
  const comment = await api(reviewer,`${d}/reviews/${round.id}/comments`,{content:'출처 확인 완료 후 해결해 주세요'},'POST',201);
  await api(reviewer,`${d}/reviews/${round.id}/decision`,{decision:'APPROVED'},'PUT',409);
  await api(leader,`${d}/reviews/${round.id}/comments/${comment.id}/resolution`,{},'PUT');
  await api(reviewer,`${d}/reviews/${round.id}/decision`,{decision:'APPROVED'},'PUT');
  await api(leader,`${d}/requirements/${requirement.id}/assessment`,{},'PUT');
  const ready = await api(leader,`${d}/confirmation`); assert.equal(ready.canConfirm,true); assert.equal(ready.candidate.file.fileId,finalFile.id);
  await api(worker,`${d}/confirmation`,{reviewId:round.id},'PUT',403);
  const confirmed = await api(leader,`${d}/confirmation`,{reviewId:round.id},'PUT'); assert.equal(confirmed.status,'CONFIRMED');
  await api(leader,`${root}/tasks`,{title:'확정 후 추가 불가',deliverableId:deliverable.id},'POST',409);
  await api(observer,`${d}/submission`,{channel:'LMS',submittedAt:new Date().toISOString()},'PUT',403);
  const submission={channel:'학교 LMS · 합성 시연 기록',submittedAt:new Date().toISOString(),note:'실제 학교 제출이 아닌 엑스포 기능 검증용 기록'};
  const submitted=await api(leader,`${d}/submission`,submission,'PUT'); assert.equal(submitted.status,'SUBMITTED');
  assert.deepEqual((await api(leader,`${d}/submission`,submission,'PUT')).submission,submitted.submission);
  const active = await api(leader,`${root}/deliverables`,{title:'발표 슬라이드 · 시연 중 이어서 작업',dueDate:'2026-10-24'},'POST',201);
  await api(leader,`${root}/deliverables/${active.id}/requirements`,{content:'핵심 분석 결과 3개를 제시한다',required:true},'POST',201);
  await api(leader,`${root}/tasks`,{title:'슬라이드에 조사 사진 정리',assigneeIds:[peer.id],deliverableId:active.id,completionCriteria:'사진과 설명 3개',dueDate:'2026-10-22'},'POST',201);
  const notifications = await api(reviewer,`${root}/notifications`);
  assert.ok(notifications.items.some(i=>i.type==='TASK_FILE_UPLOADED'));
  const n=notifications.items[0]; const read=await api(reviewer,`${root}/notifications/${n.id}/read`,undefined,'PUT');
  assert.deepEqual(await api(reviewer,`${root}/notifications/${n.id}/read`,undefined,'PUT'),read);
  assert.equal((await api(observer,`${root}/notifications`)).unreadCount,0);
  const health=await api(leader,`${root}/agent/status`); assert.equal(health.stages.find(s=>s.deliverableId===deliverable.id).stage,'SUBMITTED');
  const access={createdAt:new Date().toISOString(),base:'http://127.0.0.3:13000',projectId:project.id,projectUrl:`http://127.0.0.3:13000/projects/${project.id}`,finalDeliverableId:deliverable.id,activeDeliverableId:active.id,password,accounts:users.slice(0,5).map((u,i)=>({name:u.name,email:u.email,role:i===0?'LEADER':i===4?'OBSERVER':'MEMBER'}))};
  fs.writeFileSync(path.resolve('../../outputs/expo-20261016/demo-access.json'),JSON.stringify(access,null,2));
  console.log('엑스포 실제 HTTP 시나리오 통과: 4인+관찰자·배정·파일·의견·취소·보완·완료·전원담당 예방·최종 검토·확정·제출 기록·잠금·단계 판정');
  console.log('합성 시연 계정 안내: C:/blackbox/outputs/expo-20261016/demo-access.json');
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
