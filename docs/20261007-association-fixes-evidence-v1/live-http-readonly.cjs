// Login tokens remain in memory; this smoke test performs GET business requests only.
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
function sql(query) {
  assert.match(query,/^SELECT /);
  const result=spawnSync('docker',['exec','-i','classroom-mysql','sh','-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot classroom_ai --batch --raw --skip-column-names'],
    {input:query+';',encoding:'utf8'});
  if(result.status!==0) throw new Error(result.stderr);
  return result.stdout.trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
}
async function main() {
  assert.ok(process.env.ASSOCIATION_TEST_PASSWORD,'Provide the documented demo password in the process environment');
  const accounts=sql("SELECT JSON_OBJECT('username',username,'role',role,'department',department) FROM t_user_account WHERE role='DIRECTOR' OR username='guojun' ORDER BY id");
  const matrixCounts=sql('SELECT JSON_OBJECT(\'courseId\',course_id,\'count\',COUNT(*)) FROM t_graduation_indicator WHERE syllabus_id IN (SELECT s.id FROM t_course_syllabus s WHERE s.id=(SELECT x.id FROM t_course_syllabus x WHERE x.course_id=s.course_id ORDER BY x.created_at DESC,x.id DESC LIMIT 1)) GROUP BY course_id');
  const checks=[],seen=new Set();let teacherDenied=false;
  const base='http://127.0.0.1:8080';
  assert.equal((await fetch(base+'/api/v1/auth/me')).status,401);
  for(const account of accounts) {
    const login=await fetch(base+'/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},
      body:JSON.stringify({username:account.username,password:process.env.ASSOCIATION_TEST_PASSWORD})});
    assert.equal(login.status,200);const session=await login.json();assert.equal(session.code,200);
    const headers={Authorization:'Bearer '+session.data.token};
    async function read(endpoint) {
      const response=await fetch(base+endpoint,{headers});assert.equal(response.status,200,endpoint);
      const body=await response.json();assert.equal(body.code,200,endpoint);return body.data;
    }
    const me=await read('/api/v1/auth/me');assert.equal(me.role,account.role);assert.equal(me.department,account.department);
    const courses=await read('/api/v1/courses');
    const offerings=await read('/api/v1/courses/offerings');
    if(account.role==='DIRECTOR') {
      const majors=await read('/api/v1/majors');assert.ok(majors.length>0);
      for(const course of courses) {
        assert.equal(course.department,account.department);seen.add(course.id);
        const syllabus=await read(`/api/v1/syllabus/course/${course.id}/latest`);assert.ok(syllabus);
        const mappings=await read(`/api/v1/syllabus/course/${course.id}/indicators`);
        assert.equal(mappings.length,matrixCounts.find(item=>item.courseId===course.id)?.count||0);
        const catalog=await read(`/api/v1/syllabus/plans/${course.majorCode}/${syllabus.planVersion}/indicators`);
        assert.ok(catalog.length>0);
      }
      checks.push({role:account.role,courseCount:courses.length,majorCount:majors.length,offeringCount:offerings.length});
    } else {
      assert.ok(offerings.length>0);
      for(const offering of offerings) {
        const current=await read('/api/v1/attendance/current?offeringId='+offering.id);
        assert.ok(!current||current.offering.id===offering.id);
        await read('/api/v1/attendance/offering/'+offering.id);
      }
      const allIds=sql("SELECT JSON_OBJECT('id',id) FROM t_course_offering ORDER BY id");
      const foreign=allIds.find(row=>!offerings.some(item=>item.id===row.id));
      if(foreign) {assert.equal((await fetch(base+'/api/v1/attendance/current?offeringId='+foreign.id,{headers})).status,403);teacherDenied=true;}
      checks.push({role:account.role,offeringCount:offerings.length,foreignAttendanceDenied:teacherDenied});
    }
  }
  assert.equal(seen.size,24);assert.equal(teacherDenied,true);
  const result={observedAt:new Date().toISOString(),method:'Live backend/MySQL; login then GET only; no business writes or saved credentials',
    checkedCourses:seen.size,unauthenticatedProfile401:true,checks};
  fs.writeFileSync(path.join(__dirname,'live-http-readonly-result.json'),JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify(result));
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
