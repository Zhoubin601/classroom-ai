/* Guarded repair of the verified CS3003 sample defect. No credentials are logged. */
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const evidence = path.resolve(__dirname, '../docs/20261007-association-fixes-evidence-v1');
const tables = ['t_course','t_major','t_major_department','t_course_offering','t_course_offering_teacher',
  't_offering_student_enrollment','t_course_schedule','t_course_syllabus','t_graduation_indicator',
  't_training_indicator','t_course_resource','t_micro_teaching_slice','t_course_content_revision',
  't_attendance_session','t_supervision_evaluation'];
function sql(query) {
  const result = spawnSync('docker', ['exec','-i','classroom-mysql','sh','-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot classroom_ai --batch --raw --skip-column-names'],
    {input:query,encoding:'utf8',maxBuffer:8*1024*1024});
  if (result.status !== 0) throw new Error(result.stderr);
  return result.stdout.trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
}
function objectExpression(table) {
  const columns = sql(`SELECT JSON_OBJECT('name',COLUMN_NAME) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='${table}' ORDER BY ORDINAL_POSITION;`).map(c=>c.name);
  if (!columns.length || columns.some(c=>!/^\w+$/.test(c))) throw new Error('Unexpected table schema: '+table);
  return 'JSON_OBJECT('+columns.map(c=>`'${c}',\`${c}\``).join(',')+')';
}
function snapshot() {
  return Object.fromEntries(tables.map(table=>[table, sql(`SELECT JSON_OBJECT('id',id,'hash',SHA2(CAST(${objectExpression(table)} AS CHAR),256)) FROM \`${table}\` ORDER BY id;`)]));
}
const courseObject = objectExpression('t_course');
if (process.argv.includes('--verify-deployed')) {
  const expected=JSON.parse(fs.readFileSync(path.join(evidence,'live-data-repair.json'),'utf8'));
  const actual=snapshot();
  const changedTables=tables.filter(table=>JSON.stringify(expected.businessRows[table])!==JSON.stringify(actual[table]));
  const result={observedAt:new Date().toISOString(),method:'SELECT only after backend update',unchanged:changedTables.length===0,changedTables,
    tableCounts:Object.fromEntries(tables.map(table=>[table,actual[table].length]))};
  fs.writeFileSync(path.join(evidence,'post-deploy-data-check.json'),JSON.stringify(result,null,2)+'\n');
  console.log(JSON.stringify(result));
  if(changedTables.length) throw new Error('Business data changed after deployment; needs review');
  process.exit(0);
}
const target = () => sql(`SELECT ${courseObject} FROM t_course WHERE course_code='CS3003';`);
const beforeCourse = target();
if (beforeCourse.length!==1) throw new Error('Expected one CS3003 course');
const course = beforeCourse[0];
if (course.course_name!=='计算机网络与安全' || course.prerequisites!=='《计算机网络与安全》')
  throw new Error('Course no longer matches the verified defect; no changes made');
const before = snapshot();
console.log(JSON.stringify({mode:process.argv.includes('--apply')?'apply':'preview',courseId:course.id,courseCode:course.course_code,
  oldPrerequisites:course.prerequisites,newPrerequisites:'',tableCounts:Object.fromEntries(tables.map(t=>[t,before[t].length]))}));
if (!process.argv.includes('--apply')) process.exit(0);
fs.mkdirSync(evidence,{recursive:true});
const backupFile=path.join(evidence,'live-data-before.json');
fs.writeFileSync(backupFile,JSON.stringify({observedAt:new Date().toISOString(),course,businessRows:before},null,2)+'\n',{flag:'wx'});
const expectedHash=before.t_course.find(row=>row.id===course.id).hash;
const affected=sql(`START TRANSACTION;
  UPDATE t_course SET prerequisites='' WHERE id=${Number(course.id)} AND BINARY course_code=BINARY 'CS3003'
    AND SHA2(CAST(${courseObject} AS CHAR),256)='${expectedHash}';
  SELECT JSON_OBJECT('changed',ROW_COUNT()); COMMIT;`)[0].changed;
if (affected!==1) throw new Error('Guard rejected repair; backup retained');
const after=snapshot(), afterCourse=target()[0];
const changedTables=tables.filter(table=>JSON.stringify(before[table])!==JSON.stringify(after[table]));
const unchangedCourses=before.t_course.filter(row=>row.id!==course.id);
const allowedFields=new Set(['prerequisites','updated_at','updated_by']);
const changedFields=Object.keys(course).filter(key=>JSON.stringify(course[key])!==JSON.stringify(afterCourse[key]));
const checks={onlyCourseTableChanged:JSON.stringify(changedTables)===JSON.stringify(['t_course']),
  otherCoursesUnchanged:JSON.stringify(unchangedCourses)===JSON.stringify(after.t_course.filter(row=>row.id!==course.id)),
  noUnexpectedFieldChanges:changedFields.every(key=>allowedFields.has(key)),prerequisiteCleared:afterCourse.prerequisites===''};
fs.writeFileSync(path.join(evidence,'live-data-repair.json'),JSON.stringify({observedAt:new Date().toISOString(),affected,changedFields,checks,
  beforePrerequisites:course.prerequisites,afterCourse,businessRows:after},null,2)+'\n',{flag:'wx'});
if (Object.values(checks).some(value=>!value)) throw new Error('Post-repair verification needs review');
console.log(JSON.stringify({affected,changedFields,checks}));
