/* Read-only relationship checks. Does not query account passwords or store tokens. */
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const checks = [
  ['course_count', 'SELECT COUNT(*) FROM t_course'],
  ['offering_count', 'SELECT COUNT(*) FROM t_course_offering'],
  ['course_major_missing_or_inconsistent', `SELECT COUNT(*) FROM t_course c LEFT JOIN t_major m ON m.id=c.major_id WHERE m.id IS NULL OR NOT(BINARY c.major_code <=> BINARY m.major_code)`],
  ['course_department_not_linked_to_major', `SELECT COUNT(*) FROM t_course c JOIN t_major m ON m.id=c.major_id WHERE NOT(BINARY c.department <=> BINARY m.department) AND NOT EXISTS(SELECT 1 FROM t_major_department d WHERE d.major_id=m.id AND BINARY d.department=BINARY c.department)`],
  ['major_department_orphan', 'SELECT COUNT(*) FROM t_major_department d LEFT JOIN t_major m ON m.id=d.major_id WHERE m.id IS NULL'],
  ['offering_course_orphan', 'SELECT COUNT(*) FROM t_course_offering o LEFT JOIN t_course c ON c.id=o.course_id WHERE c.id IS NULL'],
  ['offering_legacy_major_both_null', 'SELECT COUNT(*) FROM t_course_offering WHERE major_id IS NULL AND major_code IS NULL'],
  ['offering_nonempty_major_disagrees_with_course', `SELECT COUNT(*) FROM t_course_offering o JOIN t_course c ON c.id=o.course_id WHERE (o.major_id IS NOT NULL AND NOT(o.major_id <=> c.major_id)) OR (NULLIF(TRIM(o.major_code),'') IS NOT NULL AND NOT(BINARY o.major_code <=> BINARY c.major_code))`],
  ['offering_primary_teacher_missing', `SELECT COUNT(*) FROM t_course_offering o LEFT JOIN t_teacher t ON BINARY t.teacher_code=BINARY o.teacher_code WHERE t.id IS NULL`],
  ['offering_teacher_relation_orphan_or_stale_code', `SELECT COUNT(*) FROM t_course_offering_teacher r LEFT JOIN t_course_offering o ON o.id=r.offering_id LEFT JOIN t_teacher t ON t.id=r.teacher_id WHERE o.id IS NULL OR t.id IS NULL OR NOT(BINARY r.teacher_code <=> BINARY t.teacher_code)`],
  ['teacher_account_code_orphan', `SELECT COUNT(*) FROM t_user_account u LEFT JOIN t_teacher t ON BINARY t.teacher_code=BINARY u.teacher_code WHERE u.role='TEACHER' AND t.id IS NULL`],
  ['enrollment_orphan', 'SELECT COUNT(*) FROM t_offering_student_enrollment e LEFT JOIN t_course_offering o ON o.id=e.offering_id LEFT JOIN student s ON s.id=e.student_id WHERE o.id IS NULL OR s.id IS NULL'],
  ['schedule_offering_orphan', 'SELECT COUNT(*) FROM t_course_schedule s LEFT JOIN t_course_offering o ON o.id=s.offering_id WHERE o.id IS NULL'],
  ['attendance_offering_orphan', 'SELECT COUNT(*) FROM t_attendance_session a LEFT JOIN t_course_offering o ON o.id=a.offering_id WHERE o.id IS NULL'],
  ['evaluation_offering_orphan', 'SELECT COUNT(*) FROM t_supervision_evaluation e LEFT JOIN t_course_offering o ON o.id=e.offering_id WHERE o.id IS NULL'],
  ['evaluation_supervisor_account_missing', 'SELECT COUNT(*) FROM t_supervision_evaluation e LEFT JOIN t_user_account u ON u.id=e.supervisor_user_id WHERE e.supervisor_user_id IS NOT NULL AND u.id IS NULL'],
  ['resource_course_orphan', 'SELECT COUNT(*) FROM t_course_resource r LEFT JOIN t_course c ON c.id=r.course_id WHERE c.id IS NULL'],
  ['micro_slice_course_orphan', 'SELECT COUNT(*) FROM t_micro_teaching_slice r LEFT JOIN t_course c ON c.id=r.course_id WHERE c.id IS NULL'],
  ['syllabus_course_orphan', 'SELECT COUNT(*) FROM t_course_syllabus s LEFT JOIN t_course c ON c.id=s.course_id WHERE c.id IS NULL'],
  ['indicator_course_or_syllabus_inconsistent', 'SELECT COUNT(*) FROM t_graduation_indicator i LEFT JOIN t_course c ON c.id=i.course_id LEFT JOIN t_course_syllabus s ON s.id=i.syllabus_id WHERE c.id IS NULL OR (i.syllabus_id IS NOT NULL AND (s.id IS NULL OR NOT(i.course_id <=> s.course_id)))'],
  ['duplicate_indicator_codes_within_syllabus', 'SELECT COUNT(*) FROM (SELECT syllabus_id,indicator_code FROM t_graduation_indicator WHERE syllabus_id IS NOT NULL GROUP BY syllabus_id,indicator_code HAVING COUNT(*)>1) x'],
  ['duplicate_syllabus_versions', 'SELECT COUNT(*) FROM (SELECT course_id,version FROM t_course_syllabus GROUP BY course_id,version HAVING COUNT(*)>1) x'],
  ['nonrecommended_syllabi_missing_catalog', `SELECT COUNT(*) FROM t_course_syllabus s JOIN t_course c ON c.id=s.course_id WHERE COALESCE(s.plan_version,'')<>'RECOMMENDED-12' AND NOT EXISTS(SELECT 1 FROM t_training_indicator t WHERE BINARY t.major_code=BINARY c.major_code AND BINARY t.plan_version=BINARY COALESCE(s.plan_version,s.version))`],
  ['course_prerequisite_exact_self_reference', `SELECT COUNT(*) FROM t_course WHERE BINARY TRIM(prerequisites)=BINARY course_code OR BINARY TRIM(prerequisites)=BINARY course_name`],
];
function sql(query) {
  if (!/^SELECT\b/i.test(query.trim())) throw new Error('Only SELECT allowed');
  const result = spawnSync('docker', ['exec', '-i', 'classroom-mysql', 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot classroom_ai --batch --raw --skip-column-names'], { input: query + ';', encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 });
  if (result.status !== 0) throw new Error(result.stderr);
  return result.stdout.trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
}
const results = checks.map(([name, query]) => ({ name, count: sql(`SELECT JSON_OBJECT('count',(${query}))`)[0].count, query }));
const courses = sql("SELECT JSON_OBJECT('id',id,'code',course_code,'name',course_name,'prerequisites',prerequisites) FROM t_course ORDER BY id");
const missingCatalogBindings = sql(`SELECT JSON_OBJECT('syllabusId',s.id,'courseCode',c.course_code,'syllabusVersion',s.version,'planVersion',s.plan_version,'status',s.status,'isLatest',s.id=(SELECT x.id FROM t_course_syllabus x WHERE x.course_id=s.course_id ORDER BY x.created_at DESC,x.id DESC LIMIT 1)) FROM t_course_syllabus s JOIN t_course c ON c.id=s.course_id WHERE COALESCE(s.plan_version,'')<>'RECOMMENDED-12' AND NOT EXISTS(SELECT 1 FROM t_training_indicator t WHERE BINARY t.major_code=BINARY c.major_code AND BINARY t.plan_version=BINARY COALESCE(s.plan_version,s.version))`);
const recommendedMappings = sql(`SELECT JSON_OBJECT('id',i.id,'code',i.indicator_code,'category',i.requirement_category) FROM t_graduation_indicator i JOIN t_course_syllabus s ON s.id=i.syllabus_id WHERE s.plan_version='RECOMMENDED-12'`);
const categories = ['工程知识','问题分析','设计/开发解决方案','研究','使用现代工具','工程与社会','环境和可持续发展','职业规范','个人和团队','沟通','项目管理','终身学习'];
const recommendedCategoryMismatches = recommendedMappings.filter(i => {
  const number = Number(String(i.code).split('-')[0]);
  return number < 1 || number > 12 || i.category !== categories[number - 1];
});
// Same delimiters and bracket compatibility as CourseArchiveRules. Resolve only current local records.
const prerequisiteProblems = [];
const edges = new Map();
for (const course of courses) {
  edges.set(course.id, []);
  for (const part of (course.prerequisites || '').split(/[,;，；、]/).map(s => s.trim()).filter(Boolean)) {
    const clean = part.replaceAll('《','').replaceAll('》','').trim();
    const matches = courses.filter(c => [part,clean].includes(c.code) || [part,clean,`《${clean}》`].includes(c.name));
    if (clean === '无') prerequisiteProblems.push({courseId:course.id, courseCode:course.code, reference:part, kind:'no_prerequisite_marker_rejected_by_validator'});
    else if (!matches.length) prerequisiteProblems.push({courseId:course.id, courseCode:course.code, reference:part, kind:'reference_missing_in_current_database'});
    else if (matches.length > 1) prerequisiteProblems.push({courseId:course.id, courseCode:course.code, reference:part, kind:'ambiguous_reference', matchedCourseIds:matches.map(c => c.id)});
    if (matches.some(c => c.id === course.id)) prerequisiteProblems.push({courseId:course.id, courseCode:course.code, reference:part, kind:'self_reference'});
    edges.get(course.id).push(...matches.map(c => c.id));
  }
}
const cycles = new Set();
function visit(id, trail) {
  const start = trail.indexOf(id);
  if (start >= 0) {
    const members = trail.slice(start);
    if (members.length > 1) cycles.add([...members].sort((a,b) => a-b).join(','));
    return;
  }
  for (const next of edges.get(id) || []) visit(next,[...trail,id]);
}
for (const course of courses) visit(course.id,[]);
const result = { baseline: '68255ad', observedAt: new Date().toISOString(), method: 'SELECT only; live classroom_ai database; no writes', checks: results, missingCatalogBindings, recommendedMappingCount:recommendedMappings.length, recommendedCategoryMismatches, prerequisiteReferences: courses, prerequisiteProblems, multiCourseCycles:[...cycles].map(s => s.split(',').map(Number)) };
fs.writeFileSync(path.join(__dirname, 'live-data-audit.json'), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(results.map(({name,count}) => ({name,count})), null, 2));
console.log(JSON.stringify({missingCatalogBindings,recommendedMappingCount:result.recommendedMappingCount,recommendedCategoryMismatches,prerequisiteProblems,multiCourseCycles:result.multiCourseCycles},null,2));
