const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const source = fs.readFileSync('C:/blackbox/outputs/A31-review-seed.mjs', 'utf8').replace(/^#![^\n]*\n/, '').replace(/^import .*;\r?$/gm, '');
const projects = [];
let joinCalls = 0;
async function run() {
  const messages = [];
  const context = {
    process: { argv: ['node','seed','--base','http://local.test','--count','1'], exit: code => {throw new Error('exit '+code);}, env: {} },
    console: { log: (...args) => messages.push(args.join(' ')), error: () => {} },
    mkdirSync() {}, writeFileSync() {}, homedir: () => '/virtual', join: (...args) => args.join('/'),
    Buffer, FormData, Blob, Date,
    fetch: async (url, req) => {
      const path = new URL(url).pathname;
      let status = 200, result = {};
      if (path === '/api/projects' && req.method === 'GET') result = projects;
      else if (path === '/api/projects' && req.method === 'POST') {
        result = {id:'fixture', name:JSON.parse(req.body).name, inviteCode:'fixture'};
        projects.push(result);
      } else if (path === '/api/projects/join') { joinCalls++; status=503; result={detail:'injected outage'}; }
      return {status, headers:{getSetCookie:()=>[]}, text:async()=>JSON.stringify(result)};
    }
  };
  try { await vm.runInNewContext('(async()=>{'+source+'})()',context); return {ok:true,messages}; }
  catch(error) { return {ok:false,error:error.message}; }
}
(async()=>{
  const first=await run(), second=await run();
  assert.equal(first.ok,false); assert.equal(second.ok,true); assert.equal(joinCalls,1);
  assert(second.messages.some(s=>s.includes('이미 있어 만들지 않았습니다')));
  console.log(JSON.stringify({firstFailed:first.ok===false,secondReportedSuccess:second.ok,projects:projects.length,joinCalls,partialProjectSkipped:true}));
})();

