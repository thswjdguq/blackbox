// Run inside the local frontend container; all data stays in the verification DB.
const http=require('http'),assert=require('assert/strict'),{randomUUID}=require('crypto');
const host=process.env.VERIFY_BACKEND||'teamplayui-deadline-verify';
async function call(s,method,path,body){return new Promise((resolve,reject)=>{
 const bytes=body===undefined?null:Buffer.from(JSON.stringify(body));
 const headers={Cookie:Object.entries(s.cookies).map(([k,v])=>`${k}=${v}`).join('; ')};
 if(bytes){headers['Content-Type']='application/json';headers['Content-Length']=bytes.length;}
 const req=http.request({hostname:host,port:8080,path:'/api'+path,method,headers},res=>{
  for(const c of res.headers['set-cookie']??[]){const p=c.split(';')[0],i=p.indexOf('=');s.cookies[p.slice(0,i)]=p.slice(i+1);}
  let raw='';res.setEncoding('utf8');res.on('data',c=>raw+=c);res.on('end',()=>{let data;try{data=JSON.parse(raw)}catch{data=raw}resolve({status:res.statusCode,data})});
 });req.setTimeout(15000,()=>req.destroy(new Error('timeout')));req.on('error',reject);req.end(bytes);
})}
async function ok(s,m,p,b){const r=await call(s,m,p,b);assert.ok(r.status>=200&&r.status<300,`${m} ${p}: ${r.status}`);return r.data}
async function account(name){const s={cookies:{}};await ok(s,'POST','/auth/signup',{name,email:`deadline-${randomUUID()}@example.invalid`,password:randomUUID()+'Aa!9',affiliation:{source:'SELF_REPORTED',schoolName:'가상대학교',departmentName:'경영학과'}});return s}
(async()=>{
 const leader=await account('시간 검증 팀장'),observer=await account('시간 검증 관찰자'),outsider=await account('시간 검증 외부인');
 const project=await ok(leader,'POST','/projects',{name:'[검증용] 선택 마감 시간',startDate:'2026-10-20',endDate:'2026-11-06'});
 const joined=await ok(observer,'POST','/projects/join',{inviteCode:project.inviteCode});
 await ok(leader,'PATCH',`/projects/${project.id}/members/${joined.memberId}/role`,{role:'OBSERVER'});
 const path=`/projects/${project.id}/deliverables`,payload={title:'마케팅 제안서',description:'가상 데이터',dueDate:'2026-11-05',submissionMethod:'LMS',ownerId:null};
 const legacy=await ok(leader,'POST',path,payload);assert.equal(legacy.dueTime,null);
 const timed=await ok(leader,'POST',path,{...payload,dueTime:'18:00'});assert.equal(timed.dueTime,'18:00');
 assert.equal((await ok(leader,'GET',path+'/'+timed.id)).dueTime,'18:00');
 const edit=await ok(leader,'PUT',path+'/'+timed.id,{...payload,dueTime:'23:59'});assert.equal(edit.dueTime,'23:59');
 const clear=await ok(leader,'PUT',path+'/'+timed.id,{...payload,dueTime:null});assert.equal(clear.dueTime,null);
 assert.equal((await ok(leader,'PUT',path+'/'+timed.id,{...payload,dueTime:'00:00'})).dueTime,'00:00');
 for(const dueTime of ['', '24:00','18:60','9:00','18:00:30'])assert.equal((await call(leader,'POST',path,{...payload,dueTime})).status,400);
 assert.equal((await call(observer,'POST',path,{...payload,dueTime:'18:00'})).status,403);
 assert.equal((await ok(observer,'GET',path)).find(d=>d.id===timed.id).dueTime,'00:00');
 assert.equal((await call(outsider,'GET',path)).status,403);
 assert.equal((await call({cookies:{}},'GET',path)).status,401);
 const other=await ok(outsider,'POST','/projects',{name:'[검증용] 시간 경계'});
 assert.equal((await call(outsider,'GET',`/projects/${other.id}/deliverables/${timed.id}`)).status,404);
 console.log('실제 인증 API·DB 통과: 날짜만/시간 저장·조회·변경·해제·자정·오류 시간·관찰자·비회원·타 프로젝트 경계');
 console.log(`검증 프로젝트: ${project.id}`);
})().catch(e=>{console.error(e.message);process.exitCode=1});
