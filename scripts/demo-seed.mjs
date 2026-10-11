#!/usr/bin/env node
// 시연용 자료 넣기
//
// 무엇을 하는가
//   떠 있는 서버에 지어낸 시연 프로젝트를 API로 만든다. 화면에서 손으로 만드는 것과 같은 요청을 보낸다.
//   - 계정 넷: 김팀장(팀장), 이팀원·박팀원(팀원), 최교수(관찰자). 이미 있으면 그대로 로그인해 쓴다
//   - 프로젝트: 제출물 둘, 요구사항 넷, 업무 다섯, 파일 둘, 충족 확인 하나
//   - 시연 중에 올릴 수정본 파일을 이 PC에 만들어 둔다
//   본 시연용과 리허설용을 따로 만든다. 리허설용은 계정도 따로다(주소에 .r 이 붙는다).
//   같은 계정으로 두 프로젝트를 만들면 대시보드의 "내 업무"와 알림에 두 프로젝트의 것이 섞여 보인다
//
// 사용 (Node 20 이상. 따로 설치할 것은 없다)
//   node scripts/demo-seed.mjs                         # https://localhost 에 본 시연용과 리허설용
//   node scripts/demo-seed.mjs --count 1               # 본 시연용만
//   node scripts/demo-seed.mjs --base https://localhost:18443 --password '바꿀-비밀번호'
//   node scripts/demo-seed.mjs --again                 # 이미 있어도 하나씩 더 만든다
//
// 알아 둘 것
//   - 실제 팀의 프로젝트에는 손대지 않는다. 새 프로젝트를 만들 뿐이다
//   - 서버의 .env 에 NOTION_API_KEY 와 NOTION_PARENT_PAGE_ID 가 있으면 파일을 올릴 때마다 그 Notion 페이지 아래에
//     "업로드 파일 이력" 쪽이 생긴다. 이 스크립트는 프로젝트마다 파일을 둘 올린다
//   - 같은 이름의 프로젝트가 이미 있는 계정은 건너뛴다. 실수로 두 번 돌려 겹치지 않게 하려는 것이다.
//     --again 을 주면 또 만들지만, 그 계정의 화면에 같은 이름의 프로젝트가 둘 보인다
//   - 기한은 돌린 날을 기준으로 잡는다(중간 보고서 6일 뒤, "API 설계 문서"는 어제). 시연 당일에 돌린다
//   - Discord 알림은 웹훅을 넣은 프로젝트만 보내므로 새로 만든 프로젝트에서는 나가지 않는다

import { mkdirSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

const args = process.argv.slice(2);
const option = (name, fallback) => {
  const at = args.indexOf(`--${name}`);
  return at >= 0 && args[at + 1] ? args[at + 1] : fallback;
};
const BASE = option("base", "https://localhost").replace(/\/$/, "");
const PASSWORD = option("password", "Demo-pass-2026");
const COUNT = Number(option("count", "2"));
const AGAIN = args.includes("--again");
const PROJECT_NAME = "캡스톤 디자인 — 우리 동네 재활용 안내 앱";
const DRAFT_NAME = "중간보고서_초안.pdf";

// 로컬 nginx는 스스로 서명한 인증서를 쓴다. 이 PC 안의 서버일 때만 인증서 확인을 끈다
if (/^https:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(BASE)) {
  process.removeAllListeners("warning");   // Node가 같은 내용을 영어 두 줄로 알리는 것을 아래 한 줄로 대신한다
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
  console.log("이 PC의 서버라 인증서 확인을 끄고 요청합니다(스스로 서명한 인증서).");
}

const PEOPLE = [
  { key: "leader", name: "김팀장", id: "kim" },
  { key: "member", name: "이팀원", id: "lee" },
  { key: "idle", name: "박팀원", id: "park" },
  { key: "professor", name: "최교수", id: "choi" },
];
// 첫 묶음이 본 시연용이고 그 뒤는 리허설용이다. 묶음마다 계정과 프로젝트가 따로다
const SETS = Array.from({ length: COUNT }, (_, i) => ({
  label: i === 0 ? "본 시연용" : `리허설용${i > 1 ? ` ${i}` : ""}`,
  projectName: i === 0 ? PROJECT_NAME : `${PROJECT_NAME} (리허설${i > 1 ? ` ${i}` : ""})`,
  people: PEOPLE.map((p) => ({ ...p, email: `${p.id}${i === 0 ? "" : i === 1 ? ".r" : `.r${i}`}@demo.example.com` })),
}));

// 오늘에서 n일 뒤의 날짜(한국 시간)
const day = (n) => new Date(Date.now() + n * 86_400_000 + 9 * 3_600_000).toISOString().slice(0, 10);

function fail(message) {
  console.error(`\n중단: ${message}`);
  process.exit(1);
}

/** 한 사람의 로그인 상태(쿠키)를 들고 요청을 보낸다 */
function session(person) {
  const cookies = new Map();
  async function send(method, path, body, form) {
    let res;
    try {
      res = await fetch(`${BASE}/api${path}`, {
        method,
        headers: {
          ...(cookies.size && { cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join("; ") }),
          ...(body && { "Content-Type": "application/json" }),
        },
        body: form ?? (body ? JSON.stringify(body) : undefined),
      });
    } catch (e) {
      fail(`${BASE} 에 닿지 못했습니다. 컨테이너가 모두 떠 있는지 확인하세요 (${e.cause?.code ?? e.message})`);
    }
    for (const line of res.headers.getSetCookie?.() ?? []) {
      const [pair] = line.split(";");
      cookies.set(pair.slice(0, pair.indexOf("=")), pair.slice(pair.indexOf("=") + 1));
    }
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* 본문 없음 */ }
    return { status: res.status, json, detail: json?.detail ?? text.slice(0, 200) };
  }
  /** 성공해야 하는 요청. 실패하면 무엇을 하다 멈췄는지 알리고 끝낸다 */
  async function must(what, method, path, body, form) {
    const r = await send(method, path, body, form);
    if (r.status >= 400) fail(`${person.name}: ${what} 실패 (${r.status} ${r.detail})`);
    return r.json;
  }
  return { person, send, must };
}

async function signIn(person) {
  const s = session(person);
  const signup = await s.send("POST", "/auth/signup", { email: person.email, name: person.name, password: PASSWORD });
  const login = await s.send("POST", "/auth/login", { email: person.email, password: PASSWORD });
  if (login.status !== 200) {
    fail(`${person.name}(${person.email}) 로그인 실패 (${login.status} ${login.detail}). ` +
      "이 주소로 이미 가입돼 있고 비밀번호가 다르면 --password 로 그 비밀번호를 주세요");
  }
  return { ...s, created: signup.status < 300 };
}

/** 열어 볼 수 있는 한 쪽짜리 PDF. line이 다르면 내용(해시)이 다르다 */
function pdf(line) {
  const stream = `BT /F1 18 Tf 72 720 Td (${line}) Tj ET`;
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
    `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
  ];
  let out = "%PDF-1.4\n";
  const offsets = objects.map((body, i) => {
    const at = out.length;
    out += `${i + 1} 0 obj\n${body}\nendobj\n`;
    return at;
  });
  const xref = out.length;
  out += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  out += offsets.map((at) => `${String(at).padStart(10, "0")} 00000 n \n`).join("");
  out += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(out, "latin1");
}

async function upload(who, projectId, fileName, content) {
  const form = new FormData();
  form.append("file", new Blob([content], { type: "application/pdf" }), fileName);
  return who.must(`파일 "${fileName}" 올리기`, "POST", `/projects/${projectId}/files`, undefined, form);
}

async function seedProject(people, name) {
  const { leader, member, idle, professor } = people;
  const project = await leader.must("프로젝트 만들기", "POST", "/projects", { name });
  const P = `/projects/${project.id}`;
  for (const who of [member, idle, professor]) {
    await who.must("초대 코드로 참여", "POST", "/projects/join", { inviteCode: project.inviteCode });
  }
  const members = await leader.must("멤버 목록", "GET", `${P}/members`);
  const of = (who) => members.find((m) => m.email === who.person.email);
  await leader.must("최교수를 관찰자로", "PATCH", `${P}/members/${of(professor).memberId}/role`, { role: "OBSERVER" });

  const report = await leader.must("제출물 만들기", "POST", `${P}/deliverables`, {
    title: "중간 보고서", dueDate: day(6), submissionMethod: "학교 LMS 과제함", description: "문제 정의와 요구사항 분석까지",
  });
  await leader.must("제출물 만들기", "POST", `${P}/deliverables`, {
    title: "최종 발표 자료", dueDate: day(30), submissionMethod: "발표 당일 USB 제출",
  });
  const D = `${P}/deliverables/${report.id}`;
  const wanted = [
    ["문제 정의와 대상 사용자를 적는다", true],
    ["사용자 인터뷰 3건 이상의 결과를 넣는다", true],
    ["요구사항 분석표를 넣는다", true],
    ["분량은 10쪽 안팎", false],
  ];
  for (const [content, required] of wanted) {
    await leader.must("요구사항 만들기", "POST", `${D}/requirements`, { content, required });
  }
  const { requirements } = await leader.must("제출물 조회", "GET", D);
  const requirementId = (content) => requirements.find((r) => r.content === content).id;

  const task = (fields) => leader.must(`업무 "${fields.title}" 만들기`, "POST", `${P}/tasks`,
    { priority: "MEDIUM", deliverableId: report.id, ...fields });
  await task({ title: "문제 정의 초안 작성", status: "DONE", dueDate: day(-3), assigneeIds: [of(member).userId],
    requirementId: requirementId(wanted[0][0]), completionCriteria: "대상 사용자와 문제를 한 쪽으로 정리" });
  await task({ title: "사용자 인터뷰 정리", status: "DONE", dueDate: day(-2), assigneeIds: [of(leader).userId],
    requirementId: requirementId(wanted[1][0]), completionCriteria: "인터뷰 3건을 비교표로 정리" });
  await task({ title: "요구사항 분석표 작성", status: "IN_PROGRESS", dueDate: day(3), priority: "HIGH",
    assigneeIds: [of(member).userId], requirementId: requirementId(wanted[2][0]) });
  await task({ title: "API 설계 문서", status: "TODO", dueDate: day(-1), priority: "HIGH", assigneeIds: [of(idle).userId] });
  await task({ title: "참고 문헌 정리", status: "TODO", dueDate: day(4) });

  await upload(member, project.id, DRAFT_NAME, pdf("Midterm report - draft v1"));
  // 팀장도 파일을 올려 둔다. 업무만 끝낸 사람은 "미참여"로 잡혀 팀장에게 무임승차 경보가 뜬다
  await upload(leader, project.id, "과제안내문.pdf", pdf("Assignment brief"));
  await leader.must("첫 요구사항 충족 확인", "PUT", `${D}/requirements/${requirementId(wanted[0][0])}/assessment`);
  // 기여도 화면이 처음부터 채워져 있게 한 번 계산해 둔다
  await leader.must("기여도 계산", "POST", `${P}/scores/recalculate`);
  return project;
}

async function main() {
  if (!Number.isInteger(COUNT) || COUNT < 1 || COUNT > 3) fail("--count 는 1에서 3 사이여야 합니다");
  if (typeof fetch !== "function" || typeof FormData !== "function") fail("Node 20 이상이 필요합니다");
  console.log(`서버: ${BASE}`);

  const made = [];
  for (const set of SETS) {
    console.log(`\n${set.label}`);
    const people = {};
    for (const person of set.people) {
      people[person.key] = await signIn(person);
      console.log(`  계정 ${person.name} ${person.email} ${people[person.key].created ? "(새로 만듦)" : "(이미 있어 그대로 씀)"}`);
    }
    const mine = await people.leader.must("프로젝트 목록", "GET", "/projects");
    const existing = mine.filter((p) => p.name === set.projectName);
    if (existing.length && !AGAIN) {
      console.log(`  프로젝트가 이미 있어 만들지 않았습니다. 또 만들려면 --again`);
      made.push({ ...set, id: existing[0].id, skipped: true });
      continue;
    }
    const project = await seedProject(people, set.projectName);
    console.log(`  프로젝트 "${set.projectName}"`);
    made.push({ ...set, id: project.id });
  }

  // 시연 중에 올릴 수정본. 이름이 같아야 새 버전으로 들어간다
  const dir = join(homedir(), "blackbox-demo");
  mkdirSync(dir, { recursive: true });
  const revised = join(dir, DRAFT_NAME);
  writeFileSync(revised, pdf("Midterm report - revised v2"));

  console.log("\n끝났습니다.");
  console.log(`  비밀번호(모든 계정): ${PASSWORD}`);
  for (const set of made) {
    console.log(`  ${set.label}${set.skipped ? "(전부터 있던 것)" : ""}: ${BASE}/projects/${set.id}`);
    console.log(`    ${set.people.filter((p) => p.key !== "idle").map((p) => `${p.name} ${p.email}`).join(" / ")}`);
  }
  console.log(`  시연 중에 올릴 수정본: ${revised}`);
  if (made.length > 1) console.log("  연습은 리허설용 계정으로 하고, 본 시연용 프로젝트는 시연까지 건드리지 않습니다.");
}

await main();
