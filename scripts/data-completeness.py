"""Explicit, opt-in Classroom AI data audit/seed tool. Never runs on application startup.

Uses the existing Docker MySQL client; credentials stay in the container environment.
Audit outputs contain aggregate counts and row hashes, never passwords or biometric data.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def sql(query, container='classroom-mysql', database='classroom_ai'):
    if not re.fullmatch(r'[A-Za-z0-9_-]+', container) or not re.fullmatch(r'[A-Za-z0-9_]+', database):
        raise ValueError('Invalid target identifier')
    command = ['docker', 'exec', '-i', container, 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names ' + database]
    result = subprocess.run(command, input=query.encode('utf-8'), capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    return result.stdout.decode('utf-8').strip()


def rows(query, **target):
    return [json.loads(line) for line in sql(query, **target).splitlines() if line.strip()]


def quote(value):
    return "'" + str(value).replace('\\', '\\\\').replace("'", "''") + "'"


def ident(value):
    if not re.fullmatch(r'[A-Za-z0-9_]+', value):
        raise ValueError('Invalid SQL identifier')
    return '`' + value + '`'


def save(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')


def audit(**target):
    database = target.get('database', 'classroom_ai')
    tables = rows("SELECT JSON_OBJECT('table',TABLE_NAME,'engine',ENGINE) FROM information_schema.TABLES WHERE TABLE_SCHEMA=" + quote(database) + " AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME", **target)
    columns = rows("SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,'key',COLUMN_KEY,'extra',EXTRA,'collation',COLLATION_NAME) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=" + quote(database) + " ORDER BY TABLE_NAME,ORDINAL_POSITION", **target)
    constraints = rows("SELECT JSON_OBJECT('table',TABLE_NAME,'name',CONSTRAINT_NAME,'column',COLUMN_NAME,'ref_table',REFERENCED_TABLE_NAME,'ref_column',REFERENCED_COLUMN_NAME) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=" + quote(database), **target)
    queries = []
    fingerprints = []
    for table in tables:
        name = table['table']
        fields = [c for c in columns if c['table'] == name]
        queries.append("SELECT JSON_OBJECT('table'," + quote(name) + ",'count',COUNT(*)) FROM " + ident(name))
        for field in fields:
            c = ident(field['column'])
            empty = "SUM(TRIM(" + c + ")='')" if re.search(r'char|text|enum|json', field['type']) else '0'
            queries.append("SELECT JSON_OBJECT('table'," + quote(name) + ",'column'," + quote(field['column']) + ",'null_count',COALESCE(SUM(" + c + " IS NULL),0),'empty_count',COALESCE(" + empty + ",0)) FROM " + ident(name))
        # One digest per row: sensitive values are never returned by the server.
        expr = "SHA2(CAST(JSON_ARRAY(" + ','.join(ident(c['column']) for c in fields) + ") AS CHAR),256)"
        pk = next((c['column'] for c in fields if c['key'] == 'PRI'), None)
        if pk:
            fingerprints.append("SELECT JSON_OBJECT('table'," + quote(name) + ",'key'," + ident(pk) + ",'sha256'," + expr + ") FROM " + ident(name) + ' ORDER BY ' + ident(pk))
    values = rows(';\n'.join(queries) + ';', **target)
    counts = {v['table']: v['count'] for v in values if 'count' in v}
    completeness = [v for v in values if 'column' in v]
    checks = association_checks({t['table'] for t in tables}, **target)
    return {'target': target, 'server': rows("SELECT JSON_OBJECT('version',VERSION(),'database',DATABASE(),'checked_at',NOW())", **target)[0], 'tables': tables, 'counts': counts, 'columns': columns, 'constraints': constraints, 'completeness': completeness, 'checks': checks, 'row_hashes': rows(';\n'.join(fingerprints) + ';', **target)}


def association_checks(tables, **target):
    checks = []
    # Validate every physical FK from the actual schema, not just expected entities.
    database = target.get('database', 'classroom_ai')
    for fk in rows("SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'ref_table',REFERENCED_TABLE_NAME,'ref_column',REFERENCED_COLUMN_NAME) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=" + quote(database) + " AND REFERENCED_TABLE_NAME IS NOT NULL", **target):
        q = 'SELECT COUNT(*) FROM ' + ident(fk['table']) + ' a LEFT JOIN ' + ident(fk['ref_table']) + ' b ON a.' + ident(fk['column']) + '=b.' + ident(fk['ref_column']) + ' WHERE a.' + ident(fk['column']) + ' IS NOT NULL AND b.' + ident(fk['ref_column']) + ' IS NULL'
        checks.append(('fk:' + fk['table'] + '.' + fk['column'], q))
    checks += [
        ('course_major_code', 'SELECT COUNT(*) FROM t_course c JOIN t_major m ON m.id=c.major_id WHERE NOT(c.major_code <=> m.major_code)'),
        ('course_department_scope', "SELECT COUNT(*) FROM t_course c JOIN t_major m ON m.id=c.major_id WHERE NOT(c.department <=> m.department) AND NOT EXISTS(SELECT 1 FROM t_major_department d WHERE d.major_id=m.id AND d.department=c.department)"),
        ('offering_major_missing', 'SELECT COUNT(*) FROM t_course_offering WHERE major_id IS NULL OR major_code IS NULL'),
        ('offering_major_mismatch', 'SELECT COUNT(*) FROM t_course_offering o JOIN t_course c ON c.id=o.course_id WHERE (o.major_id IS NOT NULL AND NOT(o.major_id <=> c.major_id)) OR (o.major_code IS NOT NULL AND NOT(o.major_code <=> c.major_code))'),
        ('offering_primary_teacher', "SELECT COUNT(*) FROM t_course_offering o WHERE NOT EXISTS(SELECT 1 FROM t_course_offering_teacher r WHERE r.offering_id=o.id AND r.teacher_code=o.teacher_code AND r.role_in_offering='PRIMARY')"),
        ('offering_teacher_orphan', 'SELECT COUNT(*) FROM t_course_offering_teacher r LEFT JOIN t_teacher t ON t.id=r.teacher_id LEFT JOIN t_course_offering o ON o.id=r.offering_id WHERE t.id IS NULL OR o.id IS NULL OR NOT(r.teacher_code <=> t.teacher_code)'),
        ('enrollment_orphan', 'SELECT COUNT(*) FROM t_offering_student_enrollment e LEFT JOIN student s ON s.id=e.student_id LEFT JOIN t_course_offering o ON o.id=e.offering_id WHERE s.id IS NULL OR o.id IS NULL'),
        ('enrollment_denormalized_mismatch', 'SELECT COUNT(*) FROM t_offering_student_enrollment e JOIN student s ON s.id=e.student_id WHERE NOT(e.student_number <=> s.student_id) OR NOT(e.student_name <=> s.name) OR NOT(e.admin_class_name <=> s.class_name)'),
        ('enrollment_count_mismatch', "SELECT COUNT(*) FROM t_course_offering o WHERE o.student_count<>(SELECT COUNT(*) FROM t_offering_student_enrollment e WHERE e.offering_id=o.id)"),
        ('frozen_count_mismatch', "SELECT COUNT(*) FROM t_course_offering o WHERE o.is_snapshot_frozen=1 AND (NOT(o.snapshot_student_count <=> o.student_count) OR o.snapshot_student_count<>(SELECT COUNT(*) FROM t_offering_student_enrollment e WHERE e.offering_id=o.id))"),
        ('archive_incomplete', "SELECT COUNT(*) FROM t_course_offering WHERE status='FINISHED' AND (is_snapshot_frozen<>1 OR archived_at IS NULL OR history_snapshot IS NULL)"),
        ('syllabus_mapping_mismatch', 'SELECT COUNT(*) FROM t_graduation_indicator i LEFT JOIN t_course_syllabus s ON s.id=i.syllabus_id WHERE i.syllabus_id IS NOT NULL AND (s.id IS NULL OR s.course_id<>i.course_id)'),
        ('duplicate_syllabus_version', 'SELECT COUNT(*) FROM (SELECT course_id,version FROM t_course_syllabus GROUP BY course_id,version HAVING COUNT(*)>1) x'),
        ('duplicate_indicator', 'SELECT COUNT(*) FROM (SELECT syllabus_id,indicator_code FROM t_graduation_indicator WHERE syllabus_id IS NOT NULL GROUP BY syllabus_id,indicator_code HAVING COUNT(*)>1) x'),
        ('duplicate_enrollment', 'SELECT COUNT(*) FROM (SELECT offering_id,student_id FROM t_offering_student_enrollment GROUP BY offering_id,student_id HAVING COUNT(*)>1) x'),
        ('attendance_invalid', "SELECT COUNT(*) FROM t_attendance_session WHERE actual_count<0 OR actual_count>expected_count OR attendance_rate<0 OR attendance_rate>100 OR avg_lookup_rate<0 OR avg_lookup_rate>100 OR (status='FINISHED' AND (start_time IS NULL OR end_time IS NULL OR end_time<start_time)) OR (status='ACTIVE' AND end_time IS NOT NULL) OR ABS(attendance_rate-IF(expected_count=0,0,actual_count*100.0/expected_count))>0.11"),
        ('attendance_offering_orphan', 'SELECT COUNT(*) FROM t_attendance_session a LEFT JOIN t_course_offering o ON o.id=a.offering_id WHERE o.id IS NULL'),
        # classroom_record persists ratios (0..1); attendance sessions expose percent (0..100).
        ('record_statistics_invalid', 'SELECT COUNT(*) FROM classroom_record WHERE actual_present<0 OR actual_present>total_expected OR attendance_rate<0 OR attendance_rate>1 OR lookup_rate<0 OR lookup_rate>1 OR look_down_count<0 OR look_down_count>actual_present OR ABS(attendance_rate-IF(total_expected=0,0,actual_present*1.0/total_expected))>0.0011'),
        ('evaluation_unreviewed_published', "SELECT COUNT(*) FROM t_supervision_evaluation WHERE status IN ('PUBLISHED','APPROVED_PENDING') AND (reviewed_by IS NULL OR reviewed_at IS NULL)"),
        ('evaluation_scores_invalid', "SELECT COUNT(*) FROM t_supervision_evaluation WHERE score_attitude NOT BETWEEN 0 AND 25 OR score_content NOT BETWEEN 0 AND 25 OR score_method NOT BETWEEN 0 AND 25 OR score_effect NOT BETWEEN 0 AND 25 OR ABS(total_score-(score_attitude+score_content+score_method+score_effect))>0.01"),
        ('evaluation_offering_orphan', 'SELECT COUNT(*) FROM t_supervision_evaluation e LEFT JOIN t_course_offering o ON o.id=e.offering_id WHERE o.id IS NULL'),
        ('evaluation_release_before_delay', "SELECT COUNT(*) FROM t_supervision_evaluation WHERE reviewed_at IS NOT NULL AND publish_time IS NOT NULL AND publish_time<DATE_ADD(reviewed_at,INTERVAL 24 HOUR)"),
        ('evaluation_supervisor_orphan', "SELECT COUNT(*) FROM t_supervision_evaluation e LEFT JOIN t_user_account u ON u.id=e.supervisor_user_id WHERE e.supervisor_user_id IS NOT NULL AND (u.id IS NULL OR u.role<>'SUPERVISOR')"),
        ('teacher_account_orphan', "SELECT COUNT(*) FROM t_user_account u LEFT JOIN t_teacher t ON t.teacher_code=u.teacher_code WHERE u.role='TEACHER' AND t.id IS NULL"),
        ('face_student_orphan', 'SELECT COUNT(*) FROM face_feature f LEFT JOIN student s ON s.student_id=f.student_id WHERE s.id IS NULL'),
        ('schedule_invalid', 'SELECT COUNT(*) FROM t_course_schedule WHERE start_week<1 OR end_week<start_week OR day_of_week NOT BETWEEN 1 AND 7 OR start_period<1 OR end_period<start_period'),
        ('schedule_room_conflict', 'SELECT COUNT(*) FROM t_course_schedule a JOIN t_course_schedule b ON a.id<b.id AND a.classroom=b.classroom AND a.day_of_week=b.day_of_week AND a.start_week<=b.end_week AND b.start_week<=a.end_week AND a.start_period<=b.end_period AND b.start_period<=a.end_period JOIN t_course_offering oa ON oa.id=a.offering_id JOIN t_course_offering ob ON ob.id=b.offering_id WHERE oa.academic_term=ob.academic_term'),
        ('schedule_teacher_conflict', 'SELECT COUNT(*) FROM t_course_schedule a JOIN t_course_schedule b ON a.id<b.id AND a.day_of_week=b.day_of_week AND a.start_week<=b.end_week AND b.start_week<=a.end_week AND a.start_period<=b.end_period AND b.start_period<=a.end_period JOIN t_course_offering oa ON oa.id=a.offering_id JOIN t_course_offering ob ON ob.id=b.offering_id WHERE oa.academic_term=ob.academic_term AND EXISTS(SELECT 1 FROM t_course_offering_teacher ta JOIN t_course_offering_teacher tb ON ta.teacher_id=tb.teacher_id WHERE ta.offering_id=oa.id AND tb.offering_id=ob.id)'),
        ('course_content_orphan', 'SELECT COUNT(*) FROM t_course_content_revision r LEFT JOIN t_course c ON c.id=r.course_id WHERE c.id IS NULL'),
        ('major_department_orphan', 'SELECT COUNT(*) FROM t_major_department r LEFT JOIN t_major m ON m.id=r.major_id WHERE m.id IS NULL'),
        ('teacher_user_mismatch', "SELECT COUNT(*) FROM t_teacher t LEFT JOIN t_user_account u ON u.id=t.user_id WHERE t.user_id IS NOT NULL AND (u.id IS NULL OR u.role<>'TEACHER' OR BINARY u.teacher_code<>BINARY t.teacher_code OR BINARY u.department<>BINARY t.department)"),
        ('advice_evaluation_orphan', 'SELECT COUNT(*) FROM s3_advice a LEFT JOIN t_supervision_evaluation e ON e.id=a.evaluation_id WHERE e.id IS NULL'),
        ('feedback_advice_or_teacher_orphan', "SELECT COUNT(*) FROM s3_action_feedback f LEFT JOIN s3_advice a ON a.id=f.suggestion_id LEFT JOIN t_user_account u ON u.id=f.teacher_user_id WHERE a.id IS NULL OR u.id IS NULL OR u.role<>'TEACHER'"),
        ('feedback_revision_orphan', 'SELECT COUNT(*) FROM s3_feedback_revision r LEFT JOIN s3_action_feedback f ON f.id=r.feedback_id WHERE f.id IS NULL'),
        ('resource_series_mismatch', 'SELECT COUNT(*) FROM t_course_resource r LEFT JOIN s3_resource_series s ON s.id=r.series_id WHERE r.series_id IS NOT NULL AND (s.id IS NULL OR s.course_id<>r.course_id)'),
        ('resource_current_mismatch', 'SELECT COUNT(*) FROM s3_resource_series s LEFT JOIN t_course_resource r ON r.id=s.current_version_id WHERE s.current_version_id IS NOT NULL AND (r.id IS NULL OR r.series_id<>s.id OR r.course_id<>s.course_id)'),
        ('video_course_orphan', 'SELECT COUNT(*) FROM s3_video_asset v LEFT JOIN t_course c ON c.id=v.course_id WHERE c.id IS NULL'),
        ('slice_asset_range_invalid', 'SELECT COUNT(*) FROM t_micro_teaching_slice s LEFT JOIN s3_video_asset v ON v.id=s.asset_id WHERE s.asset_id IS NOT NULL AND (v.id IS NULL OR v.course_id<>s.course_id OR s.start_ms<0 OR s.end_ms<=s.start_ms OR s.end_ms>v.duration_ms)'),
        ('synthetic_face_features', "SELECT COUNT(*) FROM face_feature WHERE student_id LIKE 'DEMO-DATA-%'"),
        ('duplicate_feedback', 'SELECT COUNT(*) FROM (SELECT suggestion_id,teacher_user_id FROM s3_action_feedback GROUP BY suggestion_id,teacher_user_id HAVING COUNT(*)>1) x'),
        ('duplicate_student_number', 'SELECT COUNT(*) FROM (SELECT student_id FROM student GROUP BY student_id HAVING COUNT(*)>1) x'),
    ]
    result = []
    for name, query in checks:
        # The actual legacy tables mix unicode_ci and 0900_ai_ci; compare business keys exactly.
        query = re.sub(r'(?<!BINARY )(\b\w+\.(?:major_code|department|teacher_code|student_id|student_number|student_name|admin_class_name|name|class_name)\b)', r'BINARY \1', query)
        used = set(re.findall(r'(?:FROM|JOIN)\s+([a-z][a-z0-9_]*)', query, re.I))
        if used <= tables:
            try:
                result.append({'name': name, 'count': int(sql(query, **target)), 'query': query})
            except RuntimeError as error:
                result.append({'name': name, 'error': str(error), 'query': query})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['audit'])
    parser.add_argument('--container', default='classroom-mysql')
    parser.add_argument('--database', default='classroom_ai')
    parser.add_argument('--evidence', required=True)
    args = parser.parse_args()
    result = audit(container=args.container, database=args.database)
    save(args.evidence, result)
    print(json.dumps({'counts': result['counts'], 'issues': [c for c in result['checks'] if c.get('count', 0) or 'error' in c]}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
