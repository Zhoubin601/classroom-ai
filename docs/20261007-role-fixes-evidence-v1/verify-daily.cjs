const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {request}=require(process.env.PLAYWRIGHT_MODULE);
const rows=[];
async function main(){const context=await request.newContext();try{
 for(const who of ['director','guojun','supervisor']){
  const auth=await context.post('http://127.0.0.1:8080/api/v1/auth/login',{data:{username:who,password:process.env.ROLE_TEST_PASSWORD}});
  const login=await auth.json();assert.equal(login.code,200,who+' login');
  const headers={Authorization:'Bearer '+login.data.token};
  // The local supervisor's current grants can differ from the isolated seed.
  const foreignIds=[];
  for(const id of JSON.parse(process.env.ROLE_TEST_DAILY_OFFERING_IDS)){
   const core=await context.get('http://127.0.0.1:8080/api/v1/courses/offerings/'+id,{headers});
   if(core.status()===403){foreignIds.push(id);break;}
  }
  const testId=foreignIds[0]||10;
  const core=await context.get('http://127.0.0.1:8080/api/v1/courses/offerings/'+testId,{headers});
  const expected=core.status();assert.ok([200,403].includes(expected));
  for(const route of ['overview','trend','students/status']){
   const r=await context.get('http://127.0.0.1:8080/api/visual/'+route+'?offeringId='+testId,{headers});assert.equal(r.status(),expected,who+' '+route);
   rows.push({actor:who,route,offeringId:testId,coreHttp:expected,http:r.status(),currentGrantChecked:true});
  }
  const offers=await context.get('http://127.0.0.1:8080/api/v1/courses/offerings',{headers});
  const own=(await offers.json()).data.find(o=>!o.isSnapshotFrozen);assert.ok(own);
  const r=await context.get(`http://127.0.0.1:8080/api/v1/courses/offerings/${own.id}/students`,{headers});const roster=(await r.json()).data;
  assert.equal(r.status(),200);assert.ok(roster.enrolled.length>0);
  if(who!=='director')assert.equal(roster.available.length,0);else assert.ok(roster.available.length>0);
  const overview=await context.get(`http://127.0.0.1:8080/api/visual/overview?offeringId=${own.id}`,{headers});const ov=await overview.json();
  assert.equal(overview.status(),200);assert.equal(ov.data.totalRegistered,roster.enrolled.length);
  rows.push({actor:who,authorizedOffering:own.id,enrolled:roster.enrolled.length,available:roster.available.length,overviewTotal:ov.data.totalRegistered});
 }
 fs.writeFileSync(path.join(__dirname,'daily-readonly-results.json'),JSON.stringify({observedAt:new Date().toISOString(),mode:'8080 local deployment; real authentication and GET only; no business writes',result:'PASS',rows},null,2));
 console.log('PASS local deployed backend: three-role scope and roster read-only checks');
}finally{await context.dispose();}}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
