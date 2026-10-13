"""Build an explicit, deterministic synthetic teaching dataset without changing schema.

plan is read-only; apply requires a verified backup, reviewed baseline and a local
development/test target. Existing accounts, biometrics, locked syllabi, published
course content and existing evaluations/attendance records are never overwritten.
"""
import argparse
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess

spec = importlib.util.spec_from_file_location('dc', Path(__file__).with_name('data-completeness.py'))
dc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dc)
MARK = 'DEMO-DATA-20261012-v1'
LABEL = '【合成演示】'
COURSES = [('SE', 'CS1001'), ('CS', 'CS2002'), ('AI', 'AI3001'), ('DS', 'DS3001'), ('SEC', 'SEC3001')]
CATEGORIES = ['工程知识', '问题分析', '设计/开发解决方案', '研究', '使用现代工具', '工程与社会', '环境和可持续发展', '职业规范', '个人和团队', '沟通', '项目管理', '终身学习']


class Expr(str):
    pass


def literal(value):
    if isinstance(value, Expr):
        return value
    if value is None:
        return 'NULL'
    if isinstance(value, (int, float)):
        return str(value)
    return dc.quote(value)


def insert(table, values, condition):
    return 'INSERT INTO ' + dc.ident(table) + ' (' + ','.join(dc.ident(c) for c in values) + ') SELECT ' + ','.join(literal(v) for v in values.values()) + ' WHERE NOT EXISTS(SELECT 1 FROM ' + dc.ident(table) + ' WHERE ' + condition + ');'


def ref(table, condition):
    return Expr('(SELECT id FROM ' + dc.ident(table) + ' WHERE ' + condition + ')')


def generate(target, uploads, selected_majors=None):
    selected_majors = selected_majors or [major for major, code in COURSES]
    selected_courses = [(major, code) for major, code in COURSES if major in selected_majors]
    template = dc.rows("SELECT JSON_OBJECT('id',id,'attitude',attitude,'content',content,'method',method,'effect',effect) FROM s3_template_version ORDER BY id LIMIT 1", **target)
    if not template or any(float(template[0][k]) != 25 for k in ('attitude','content','method','effect')):
        raise ValueError('This seed requires the existing four-25-point template; changed weights need a revised plan')
    courses = dc.rows("SELECT JSON_OBJECT('id',id,'code',course_code,'name',course_name,'major',major_code,'major_id',major_id,'dept',department,'description',description,'objectives',objectives,'assessment',assessment_method) FROM t_course ORDER BY id", **target)
    teachers = dc.rows("SELECT JSON_OBJECT('id',t.id,'code',t.teacher_code,'name',t.teacher_name,'dept',t.department,'account',u.id,'username',u.username) FROM t_teacher t JOIN t_user_account u ON BINARY u.teacher_code=BINARY t.teacher_code AND u.role='TEACHER'", **target)
    supervisors = dc.rows("SELECT JSON_OBJECT('id',id,'username',username,'name',real_name,'majors',authorized_majors) FROM t_user_account WHERE role='SUPERVISOR'", **target)
    directors = dc.rows("SELECT JSON_OBJECT('id',id,'username',username,'dept',department) FROM t_user_account WHERE role='DIRECTOR'", **target)
    if not supervisors:
        raise ValueError('No existing authorized supervisor; no privileged account will be created')
    known = {c['code']: c for c in courses}
    if len(known) != len(courses):
        raise ValueError('Duplicate course business keys')
    statements = [
        "UPDATE t_teacher t JOIN t_user_account u ON BINARY u.teacher_code=BINARY t.teacher_code AND u.role='TEACHER' SET t.user_id=u.id WHERE t.user_id IS NULL AND (SELECT COUNT(*) FROM t_user_account x WHERE BINARY x.teacher_code=BINARY t.teacher_code AND x.role='TEACHER')=1 AND BINARY t.department=BINARY u.department;",
        "UPDATE t_course_offering o JOIN t_course c ON c.id=o.course_id SET o.major_id=COALESCE(o.major_id,c.major_id),o.major_code=IF(NULLIF(TRIM(o.major_code),'') IS NULL,c.major_code,o.major_code) WHERE o.status<>'FINISHED' AND COALESCE(o.is_snapshot_frozen,0)=0 AND (o.major_id IS NULL OR NULLIF(TRIM(o.major_code),'') IS NULL) AND (o.major_id IS NULL OR o.major_id=c.major_id) AND (NULLIF(TRIM(o.major_code),'') IS NULL OR BINARY o.major_code=BINARY c.major_code);",
        "UPDATE t_course_offering SET is_snapshot_frozen=0 WHERE is_snapshot_frozen IS NULL AND status<>'FINISHED';",
        "INSERT INTO t_course_offering_teacher(offering_id,teacher_id,teacher_code,teacher_name,role_in_offering) SELECT o.id,t.id,t.teacher_code,t.teacher_name,'PRIMARY' FROM t_course_offering o JOIN t_teacher t ON BINARY t.teacher_code=BINARY o.teacher_code WHERE COALESCE(o.is_snapshot_frozen,0)=0 AND o.status<>'FINISHED' AND NOT EXISTS(SELECT 1 FROM t_course_offering_teacher r WHERE r.offering_id=o.id AND r.role_in_offering='PRIMARY') AND NOT EXISTS(SELECT 1 FROM t_course_offering_teacher r WHERE r.offering_id=o.id AND r.teacher_id=t.id);",
    ]
    planned = [{'table': 't_teacher', 'operation': '补齐唯一工号且教研室一致的user_id'}, {'table': 't_course_offering', 'operation': '补齐未冻结在读班次专业和false冻结标记'}, {'table': 't_course_offering_teacher', 'operation': '依据已有主讲工号补齐PRIMARY关系'}]
    # File facts may be derived from bytes; missing/non-local legacy sources remain unresolved.
    file_facts = []
    for r in dc.rows("SELECT JSON_OBJECT('id',id,'url',file_url,'bytes',file_size_bytes,'hash',content_hash,'display_size',file_size) FROM t_course_resource", **target):
        name = str(r['url']).removeprefix('/uploads/resources/')
        path = uploads / 'resources' / name
        exists = r['url'].startswith('/uploads/resources/') and '/' not in name and '\\' not in name and path.is_file()
        fact = {'resource_id': r['id'], 'file_url': r['url'], 'exists': bool(exists)}
        if exists:
            size = path.stat().st_size
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            fact.update({'bytes': size, 'sha256': digest, 'existing_size_matches': r['bytes'] in (None, size)})
            # Do not silently bless an existing hash mismatch.
            if r['hash'] is not None and r['hash'] != digest:
                raise ValueError('Existing resource content hash mismatch: ' + str(r['id']))
            statements.append(f"UPDATE t_course_resource SET file_size_bytes=COALESCE(file_size_bytes,{size}),content_hash=COALESCE(content_hash,{dc.quote(digest)}) WHERE id={r['id']};")
            displayed=re.fullmatch(r'\s*([0-9.]+)\s*(B|KB|MB|GB)\s*',r['display_size'] or '',re.I)
            units={'B':1,'KB':1024,'MB':1024**2,'GB':1024**3}
            approximate=float(displayed[1])*units[displayed[2].upper()] if displayed else None
            # Keep normal rounded UI sizes. Repair only absent or materially misleading metadata.
            misleading=approximate is None or abs(approximate-size)>max(1024,size*.05)
            fact.update({'display_size_before':r['display_size'],'display_size_materially_wrong':misleading})
            if misleading:
                actual_display=f'{size/1024:.1f} KB' if size>=1024 else f'{size} B'
                statements.append(f"UPDATE t_course_resource SET file_size={dc.quote(actual_display)} WHERE id={r['id']} AND file_size<=>{literal(r['display_size'])} AND content_hash={dc.quote(digest)};")
        file_facts.append(fact)
    # Extend the persisted recommendation catalog using the exact existing Java template.
    for major in sorted({c['major'] for c in courses if c['major']}):
        for i, category in enumerate(CATEGORIES, 1):
            key = f"major_code={dc.quote(major)} AND plan_version='RECOMMENDED-12' AND indicator_code='{i}-1'"
            statements.append(insert('t_training_indicator', {'major_code': major, 'plan_version': 'RECOMMENDED-12', 'indicator_code': f'{i}-1', 'requirement_category': category, 'indicator_description': category + '的课程支撑要求（推荐草案，请按课程实际内容修订）'}, key))
    # Reuse course-specific goals already present in the latest syllabus. Unknown formal
    # assessment facts become labelled private drafts, not changes to published t_course.
    for c in courses:
        key = f"course_id={c['id']} AND status='DRAFT'"
        if c['objectives'] is not None and c['assessment'] is not None:
            continue
        syllabi = dc.rows("SELECT JSON_OBJECT('goals',course_goals) FROM t_course_syllabus WHERE course_id=" + str(c['id']) + " ORDER BY created_at DESC,id DESC LIMIT 1", **target)
        goals = c['objectives'] or (syllabi[0]['goals'] if syllabi and syllabi[0]['goals'] else '解释课程核心概念；通过案例分析并验证学习结果。')
        statements.append(insert('t_course_content_revision', {'course_id': c['id'], 'description': LABEL + MARK + ' 教学内容建议草稿。原简介：' + (c['description'] or '资料未提供'), 'objectives': LABEL + '建议，非正式培养方案：\n' + goals, 'assessment_method': c['assessment'] or LABEL + '建议采用概念测验30%、案例或实验40%、综合说明30%；权重为脚本演示设定，须任课教师确认后另行发布。', 'status': 'DRAFT', 'editor_name': LABEL + '数据治理工具', 'publish_version': 0, 'lock_version': 0, 'version': 0, 'created_at': Expr('NOW(6)'), 'updated_at': Expr('NOW(6)')}, key))
    scenes = []
    for index, (major, code) in enumerate(COURSES):
        if major not in selected_majors:
            continue
        c = known[code]
        if c['major'] != major:
            raise ValueError('Course major mismatch for ' + code)
        assigned = dc.rows("SELECT JSON_OBJECT('code',teacher_code) FROM t_course_offering WHERE course_id=" + str(c['id']) + ' ORDER BY id LIMIT 1', **target)
        candidates = [t for t in teachers if assigned and t['code'] == assigned[0]['code'] and t['dept'] == c['dept']]
        if len(candidates) != 1:
            raise ValueError('Cannot reliably resolve existing teacher for ' + code)
        t = candidates[0]
        authorized = [s for s in supervisors if major in (s['majors'] or '').split(';')]
        dirs = [x for x in directors if x['dept'] == c['dept']]
        if not authorized or not dirs:
            raise ValueError('Supervisor/director scope unavailable for ' + code)
        sup, director = authorized[0], dirs[0]
        class_name = LABEL + MARK + '-' + major
        for number in range(1, 13):
            sid = f'DEMO-DATA-{major}-{number:03}'
            statements.append(insert('student', {'student_id': sid, 'name': f'合成示例-{major}-{number:03}', 'gender': '未提供', 'class_name': class_name, 'avatar_url': '', 'created_at': Expr('NOW(6)'), 'updated_at': Expr('NOW(6)')}, 'student_id=' + dc.quote(sid)))
        # Saturday periods are independent of existing weekdays, and still checked globally.
        variants = [('2026-2027秋季', 'IN_PROGRESS', '', '2026-10-10', 6)]
        if index == 0:
            variants += [('2025-2026春季', 'FINISHED', '-HISTORY', '2026-06-20', 16), ('2026-2027春季', 'PENDING', '-FUTURE', None, None)]
        for term, status, suffix, date, week in variants:
            cls = class_name + suffix
            cond = 'class_name=' + dc.quote(cls) + ' AND academic_term=' + dc.quote(term) + f" AND course_id={c['id']}"
            offering = ref('t_course_offering', cond)
            room = '合成演示教室-' + major
            values = {'course_id': c['id'], 'academic_term': term, 'teacher_name': t['name'], 'teacher_code': t['code'], 'major_id': c['major_id'], 'major_code': major, 'class_name': cls, 'student_count': 12, 'is_snapshot_frozen': 1 if status == 'FINISHED' else 0, 'status': status, 'created_at': Expr('NOW(6)'), 'updated_at': Expr('NOW(6)')}
            if status == 'FINISHED':
                # A labelled synthetic snapshot, never described as actual human archival.
                values.update({'snapshot_student_count': 12, 'archived_at': date + ' 18:00:00', 'archived_by': MARK})
            statements.append(insert('t_course_offering', values, cond))
            statements.append(insert('t_course_offering_teacher', {'offering_id': offering, 'teacher_id': t['id'], 'teacher_code': t['code'], 'teacher_name': t['name'], 'role_in_offering': 'PRIMARY'}, f'offering_id={offering} AND teacher_id={t["id"]}'))
            if index == 0 and status == 'IN_PROGRESS':
                assistants = [x for x in teachers if x['dept'] == c['dept'] and x['id'] != t['id']]
                if assistants:
                    assistant = assistants[0]
                    statements.append(insert('t_course_offering_teacher', {'offering_id': offering, 'teacher_id': assistant['id'], 'teacher_code': assistant['code'], 'teacher_name': assistant['name'], 'role_in_offering': 'ASSISTANT'}, f'offering_id={offering} AND teacher_id={assistant["id"]}'))
            for number in range(1, 13):
                sid = f'DEMO-DATA-{major}-{number:03}'
                student = ref('student', 'student_id=' + dc.quote(sid))
                statements.append(insert('t_offering_student_enrollment', {'offering_id': offering, 'student_id': student, 'student_number': sid, 'student_name': f'合成示例-{major}-{number:03}', 'admin_class_name': class_name}, f'offering_id={offering} AND student_id={student}'))
            statements.append(insert('t_course_schedule', {'offering_id': offering, 'classroom': room, 'week_range': '1-16周(全)', 'start_week': 1, 'end_week': 16, 'day_of_week': 6, 'start_period': 1 + index * 2, 'end_period': 2 + index * 2, 'created_at': Expr('NOW(6)'), 'updated_at': Expr('NOW(6)')}, f'offering_id={offering}'))
            if status == 'FINISHED':
                snapshot = Expr("JSON_OBJECT('offeringId',id,'courseCode'," + dc.quote(code) + ",'courseName'," + dc.quote(c['name']) + ",'academicTerm',academic_term,'primaryTeacher',teacher_name,'teachers',JSON_ARRAY(teacher_name),'className',class_name,'classroom'," + dc.quote(room) + ",'studentCount',12,'status','FINISHED','isSnapshotFrozen',JSON_EXTRACT('true','$'),'archivedAt',archived_at,'archivedBy',archived_by)")
                statements.append('UPDATE t_course_offering SET history_snapshot=' + snapshot + ' WHERE ' + cond + ' AND history_snapshot IS NULL;')
            if date:
                for lesson in range(2):
                    actual = 11 - lesson
                    start = dt.datetime.fromisoformat(date + ' 08:00:00') + dt.timedelta(hours=index * 2, minutes=lesson * 50)
                    end = start + dt.timedelta(minutes=45)
                    absent = ','.join(f'DEMO-DATA-{major}-{n:03}' for n in range(actual + 1, 13))
                    statements.append(insert('t_attendance_session', {'offering_id': offering, 'week_number': week, 'classroom': room, 'expected_count': 12, 'actual_count': actual, 'attendance_rate': round(actual * 100 / 12, 1), 'avg_lookup_rate': 82 - index * 3 - lesson * 2, 'status': 'FINISHED', 'operator_name': LABEL + '数据治理工具', 'operator_role': 'TEACHER', 'operator_title': '合成历史统计，非真实考勤', 'start_time': str(start), 'end_time': str(end), 'absent_student_ids': absent, 'created_at': Expr('NOW(6)'), 'updated_at': Expr('NOW(6)')}, f'offering_id={offering} AND start_time={dc.quote(str(start))}'))
                    session = ref('t_attendance_session', f'offering_id={offering} AND start_time={dc.quote(str(start))}')
                    for minute in (0, 15, 30, 45):
                        tm = start + dt.timedelta(minutes=minute)
                        down = 2 + (minute // 15) % 2
                        statements.append(insert('classroom_record', {'session_id': Expr("CONCAT('ATT_'," + session + ')'), 'course_name': c['name'], 'class_name': cls, 'total_expected': 12, 'actual_present': actual, 'attendance_rate': actual / 12, 'lookup_rate': (actual - down) / actual, 'look_down_count': down, 'record_time': str(tm)}, "session_id=CONCAT('ATT_'," + session + ') AND record_time=' + dc.quote(str(tm))))
            if not suffix:
                scenes.append({'major': major, 'course': c, 'teacher': t, 'supervisor': sup, 'director': director, 'class': cls, 'room': room, 'offering': offering, 'offering_condition': cond, 'index': index})
    for scene in scenes:
        c, t, sup, director, index = (scene[k] for k in ('course', 'teacher', 'supervisor', 'director', 'index'))
        # DRAFT, PENDING_REVIEW, APPROVED_PENDING and released historical snapshots.
        statuses = ['PUBLISHED', 'PUBLISHED', 'APPROVED_PENDING', 'PENDING_REVIEW', 'DRAFT']
        status = statuses[index]
        topic = LABEL + MARK + '-' + scene['major'] + ' 教学案例观察'
        condition = f'offering_id={scene["offering"]} AND listen_topic={dc.quote(topic)}'
        evaluation = ref('t_supervision_evaluation', condition)
        score = 22 - index
        values = {'offering_id': scene['offering'], 'supervisor_name': LABEL + '状态示例（非真人听课）', 'supervisor_user_id': sup['id'], 'evaluate_date': '2026-10-10' if index < 2 else '2026-10-12', 'listen_topic': topic, 'score_attitude': score, 'score_content': score, 'score_method': score - 1, 'score_effect': score - 1, 'total_score': score * 4 - 2, 'highlights': LABEL + '课程目标分阶段展示\n' + LABEL + '案例步骤解释清楚\n' + LABEL + '课堂提问引导学生表达', 'suggestions': LABEL + '增加案例实践和阶段测验，以验证学习目标；该评语为脚本合成，非实际督导结论。', 'status': status, 'record_version': 0, 'template_version_id': Expr('(SELECT id FROM s3_template_version ORDER BY id LIMIT 1)'), 'weight_snapshot': '{"attitude":25,"content":25,"method":25,"effect":25}', 'problem_tags_json': '["教学互动","学习效果"]', 'problem_classification_version': '示例分类-v1', 'created_at': Expr('NOW(6)'), 'updated_at': Expr('NOW(6)'), 'touched_at': Expr('NOW(6)')}
        if status != 'DRAFT':
            values['submit_time'] = '2026-10-10 10:00:00' if index < 2 else Expr('NOW(6)')
        if status in ('PUBLISHED', 'APPROVED_PENDING'):
            values.update({'reviewed_by': director['id'], 'reviewed_at': '2026-10-10 11:00:00' if index < 2 else Expr('NOW(6)'), 'publish_time': '2026-10-11 11:00:00' if index < 2 else Expr('DATE_ADD(NOW(6),INTERVAL 24 HOUR)'), 'review_note': LABEL + MARK + '自动构造审核状态示例，不表示该账号本人实施过审核。'})
        statements.append(insert('t_supervision_evaluation', values, condition))
        for action, prior, to_status, actor, timestamp in ([('SAVE_DRAFT', None, 'DRAFT', sup, '2026-10-10 09:00:00' if index < 2 else Expr('NOW(6)'))] + ([] if status == 'DRAFT' else [('SUBMIT', 'DRAFT', 'PENDING_REVIEW', sup, values['submit_time'])]) + ([] if status not in ('PUBLISHED', 'APPROVED_PENDING') else [('APPROVE', 'PENDING_REVIEW', 'APPROVED_PENDING', director, values['reviewed_at'])])):
            statements.append(insert('t_evaluation_audit_log', {'evaluation_id': evaluation, 'actor_id': actor['id'], 'actor_username': actor['username'], 'action': action, 'from_status': prior, 'to_status': to_status, 'occurred_at': timestamp}, f'evaluation_id={evaluation} AND action={dc.quote(action)}'))
        statements.append(insert('s3_advice', {'evaluation_id': evaluation, 'created_by': sup['id'], 'default_generated': 1, 'revision': 0, 'text': values['suggestions'], 'evidence_json': '[]', 'stage_tags_json': '["P2","P3"]', 'touched_at': Expr('NOW(6)')}, f'evaluation_id={evaluation}'))
        if status == 'PUBLISHED':
            advice = ref('s3_advice', f'evaluation_id={evaluation}')
            feedback = ref('s3_action_feedback', f'suggestion_id={advice} AND teacher_user_id={t["account"]}')
            data = {'suggestion_id': advice, 'teacher_user_id': t['account'], 'decision': 'ACCEPTED', 'action_text': LABEL + MARK + ' 小组案例练习与阶段测验示例', 'feedback_text': LABEL + '示例反馈：12个合成学生中10个完成练习。此数据不表示真实教师行动。', 'evidence_json': '[]', 'stage_tags_json': '["P2","P3"]', 'revision': 1, 'touched_at': '2026-10-11 12:00:00'}
            statements.append(insert('s3_action_feedback', data, f'suggestion_id={advice} AND teacher_user_id={t["account"]}'))
            snapshot = {'suggestionId': str(advice), 'teacherUserId': t['account'], 'decision': 'ACCEPTED', 'actionText': data['action_text'], 'feedbackText': data['feedback_text'], 'evidenceJson': '[]', 'stageTagsJson': '["P2","P3"]', 'revision': 1, 'synthetic': True, 'source': MARK}
            # Build actual IDs using JSON_OBJECT, rather than putting SQL into snapshot text.
            snapshot_expr = Expr("JSON_OBJECT('id'," + feedback + ",'suggestionId'," + advice + ",'teacherUserId'," + str(t['account']) + ",'decision','ACCEPTED','actionText'," + dc.quote(data['action_text']) + ",'feedbackText'," + dc.quote(data['feedback_text']) + ",'evidenceJson','[]','stageTagsJson','[\"P2\",\"P3\"]','revision',1,'synthetic',JSON_EXTRACT('true','$'),'source'," + dc.quote(MARK) + ')')
            statements.append(insert('s3_feedback_revision', {'feedback_id': feedback, 'actor_id': t['account'], 'record_version': 1, 'occurred_at': '2026-10-11 12:00:00', 'snapshot_json': snapshot_expr}, f'feedback_id={feedback} AND record_version=1'))
        # One real, deterministic PDF per major; two versions for SE.
        for version in range(1, 3 if index == 0 else 2):
            name = f'{MARK}-{scene["major"]}-v{version}.pdf'
            file = uploads / 'resources' / name
            if not file.is_file():
                raise ValueError('Create and verify sample file before planning: ' + name)
            digest = hashlib.sha256(file.read_bytes()).hexdigest()
            resource_key = 'file_url=' + dc.quote('/uploads/resources/' + name)
            resource = ref('t_course_resource', resource_key)
            first = ref('t_course_resource', 'file_url=' + dc.quote('/uploads/resources/' + f'{MARK}-{scene["major"]}-v1.pdf'))
            if version == 1:
                statements.append("INSERT INTO s3_resource_series(course_id,access_epoch,next_number,removed,revision,shared,touched_at) SELECT " + str(c['id']) + ",0,2,0,0,1,NOW(6) WHERE NOT EXISTS(SELECT 1 FROM t_course_resource WHERE " + resource_key + ');')
                series = Expr('LAST_INSERT_ID()')
            else:
                series = Expr('(SELECT series_id FROM t_course_resource WHERE id=' + first + ')')
            statements.append(insert('t_course_resource', {'course_id': c['id'], 'chapter': LABEL + '案例实践与学习目标', 'resource_name': LABEL + c['name'] + f'案例练习-v{version}.pdf', 'file_type': 'PDF', 'file_url': '/uploads/resources/' + name, 'file_size': str(file.stat().st_size) + ' B', 'file_size_bytes': file.stat().st_size, 'content_hash': digest, 'tag': '理论' if version == 1 else '实验', 'tags': '理论,讨论' if version == 1 else '实验,研讨', 'version': 'v' + str(version), 'version_number': version, 'series_id': series, 'is_public': 1, 'dynamic_watermark': LABEL + MARK, 'uploader_teacher': LABEL + '数据治理工具', 'created_at': Expr('NOW(6)'), 'updated_at': Expr('NOW(6)')}, resource_key))
            statements.append(f'UPDATE s3_resource_series SET current_version_id={resource},next_number={version + 1} WHERE id=(SELECT series_id FROM t_course_resource WHERE id={resource}) AND (current_version_id IS NULL OR current_version_id={first})' + (';' if version == 1 else f' AND next_number<={version + 1};'))
        video = uploads / 'micro' / (MARK + '.mp4')
        if not video.is_file():
            raise ValueError('Create synthetic video before planning')
        if index == 0:
            stored = video.name
            asset_key = 'stored_name=' + dc.quote(stored)
            asset = ref('s3_video_asset', asset_key)
            statements.append(insert('s3_video_asset', {'course_id': c['id'], 'stored_name': stored, 'sha256': hashlib.sha256(video.read_bytes()).hexdigest(), 'duration_ms': 4000, 'source_type': 'PLATFORM_UPLOAD', 'boppps_stage': 'P2', 'source_description': LABEL + '纯色色块教学环节演示，无真人影像', 'source_reference': MARK, 'authorization_declaration': LABEL + '由脚本生成色块视频，仅为演示；不含个人或第三方素材。', 'submitted_by': t['account'], 'submitted_at': Expr('NOW(6)')}, asset_key))
            for stage, begin, end in [('B', 0, 1000), ('P2', 1000, 3000), ('S', 3000, 4000)]:
                title = LABEL + MARK + '-' + stage
                statements.append(insert('t_micro_teaching_slice', {'course_id': c['id'], 'asset_id': asset, 'video_title': title, 'boppps_stage': stage, 'duration_seconds': (end - begin) // 1000, 'slice_url': '/uploads/micro/' + stored, 'start_ms': begin, 'end_ms': end, 'source_agent': LABEL + '纯色合成视频', 'recorded_date': '2026-10-12', 'classroom': scene['room'], 'record_version': 0, 'created_at': Expr('NOW(6)'), 'touched_at': Expr('NOW(6)')}, f'course_id={c["id"]} AND video_title={dc.quote(title)}'))
    body = '\n'.join(statements)
    # Temp assertion turns lock acquisition failure into a hard SQL error.
    student_scope=' OR '.join('student_id LIKE '+dc.quote('DEMO-DATA-'+major+'-%') for major in selected_majors)
    offering_scope="class_name LIKE '%" + MARK + "%' AND major_code IN("+','.join(dc.quote(m) for m in selected_majors)+')'
    guards = [
        'SELECT COUNT(*)='+str(12*len(selected_courses))+' FROM student WHERE '+student_scope,
        'SELECT COUNT(*)='+str(len(selected_courses)+(2 if 'SE' in selected_majors else 0))+' FROM t_course_offering WHERE '+offering_scope,
        "SELECT COUNT(*)=0 FROM t_course_offering o WHERE class_name LIKE '%" + MARK + "%' AND (student_count<>12 OR student_count<>(SELECT COUNT(*) FROM t_offering_student_enrollment e WHERE e.offering_id=o.id))",
        "SELECT COUNT(*)=0 FROM t_attendance_session a JOIN t_course_offering o ON o.id=a.offering_id WHERE o.class_name LIKE '%" + MARK + "%' AND (a.status<>'FINISHED' OR a.end_time<a.start_time OR a.actual_count>a.expected_count OR ABS(a.attendance_rate-a.actual_count*100.0/a.expected_count)>0.11)",
        "SELECT COUNT(*)=0 FROM t_supervision_evaluation WHERE listen_topic LIKE '%" + MARK + "%' AND reviewed_at IS NOT NULL AND publish_time<DATE_ADD(reviewed_at,INTERVAL 24 HOUR)",
        "SELECT COUNT(*)=0 FROM face_feature WHERE student_id LIKE 'DEMO-DATA-%'",
    ]
    assertions = '\n'.join('INSERT INTO demo_seed_assert VALUES((' + q + '));' for q in guards)
    wrapped = "SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;\nCREATE TEMPORARY TABLE demo_seed_assert(v INT CHECK(v=1));\nINSERT INTO demo_seed_assert VALUES(GET_LOCK('" + MARK + "',10));\nSTART TRANSACTION;\n" + body + '\n' + assertions + "\nCOMMIT;\nDO RELEASE_LOCK('" + MARK + "');\n"
    return wrapped, {'source': MARK, 'synthetic': True, 'selected_courses': [c for m,c in selected_courses], 'scenes': [{'major': s['major'], 'course_code': s['course']['code'], 'class': s['class'], 'term': '2026-2027秋季'} for s in scenes], 'planned_repairs': planned, 'existing_file_facts': file_facts, 'no_new_accounts': True, 'no_biometric_data': True, 'no_existing_content_overwrite': True, 'synthetic_events_use_existing_scoped_account_ids': True, 'event_note': 'All new evaluation/audit/feedback events are labelled synthetic snapshots; no claim of real human activity.', 'sql_sha256': hashlib.sha256(wrapped.encode()).hexdigest()}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('command', choices=['plan', 'apply'])
    p.add_argument('--container', default='classroom-mysql')
    p.add_argument('--database', default='classroom_ai')
    p.add_argument('--majors', default='SE,CS,AI,DS,SEC', help='Explicit existing authorized major codes; never expands account scope')
    p.add_argument('--environment', choices=['local-development', 'isolated-test'])
    p.add_argument('--uploads', default=str(dc.ROOT / 'runtime/uploads'))
    p.add_argument('--evidence-dir', required=True)
    p.add_argument('--backup-evidence')
    args = p.parse_args()
    majors=args.majors.split(',')
    if len(set(majors))!=len(majors) or not set(majors)<={m for m,c in COURSES} or not all(majors):
        raise ValueError('Invalid major selection')
    target = {'container': args.container, 'database': args.database}
    evidence = Path(args.evidence_dir)
    statement, manifest = generate(target, Path(args.uploads).resolve(),majors)
    evidence.mkdir(parents=True, exist_ok=True)
    if args.command == 'plan':
        (evidence / 'planned-seed.sql').write_text(statement, encoding='utf-8')
        dc.save(evidence / 'seed-plan.json', manifest)
        print(json.dumps({'source': MARK, 'statements': statement.count(';'), 'read_only': True}))
        return
    if not args.environment:
        raise ValueError('Explicit non-production environment required')
    if args.environment == 'isolated-test' and not args.container.startswith('classroom-data-test-'):
        raise ValueError('Invalid isolated target')
    if args.environment == 'local-development':
        if args.container != 'classroom-mysql' or args.database != 'classroom_ai':
            raise ValueError('Only the inspected local Compose target is permitted')
        inspected = subprocess.check_output(['docker', 'inspect', '--format', '{{index .Config.Labels "com.docker.compose.project"}}', args.container], encoding='utf-8').strip()
        if inspected != 'classroom-ai-demo':
            raise ValueError('Local Compose identity mismatch')
        if not args.backup_evidence:
            raise ValueError('Verified encrypted backup is required')
        backup = json.loads(Path(args.backup_evidence).read_text(encoding='utf-8'))
        file = Path(backup['encrypted_file'])
        if not backup['decrypt_roundtrip'] or backup['source'] != args.container or hashlib.sha256(file.read_bytes()).hexdigest() != backup['encrypted_sha256']:
            raise ValueError('Backup verification failed')
    # A reviewed plan is required. Regeneration detects file/data-dependent drift.
    planned = evidence / 'planned-seed.sql'
    if not planned.is_file() or planned.read_text(encoding='utf-8') != statement:
        raise ValueError('Plan missing or changed; run plan and inspect before apply')
    before = dc.audit(**target)
    dc.save(evidence / 'apply-before.json', before)
    repair_values = {}
    for table, fields in {'t_teacher':['user_id'], 't_course_offering':['major_id','major_code','is_snapshot_frozen'], 't_course_resource':['file_size_bytes','content_hash','file_size']}.items():
        pairs = ["'id',id"] + [dc.quote(f) + ',' + dc.ident(f) + ('+0' if f == 'is_snapshot_frozen' else '') for f in fields]
        repair_values[table] = {str(r['id']):r for r in dc.rows('SELECT JSON_OBJECT(' + ','.join(pairs) + ') FROM ' + dc.ident(table), **target)}
    dc.sql(statement, **target)
    after = dc.audit(**target)
    dc.save(evidence / 'apply-after.json', after)
    original = {(r['table'], str(r['key'])): r['sha256'] for r in before['row_hashes']}
    final = {(r['table'], str(r['key'])): r['sha256'] for r in after['row_hashes']}
    changes = [{'table': t, 'key': k, 'before_sha256': sha, 'after_sha256': final.get((t,k))} for (t,k),sha in original.items() if final.get((t,k)) != sha]
    added = [{'table': t, 'key': k, 'sha256': sha} for (t,k),sha in final.items() if (t,k) not in original]
    allowed = {'t_teacher','t_course_offering','t_course_resource'}
    assert all(c['table'] in allowed and c['after_sha256'] is not None for c in changes), 'Unexpected existing-row modification'
    previous = {x['name']: x.get('count') for x in before['checks'] if 'count' in x}
    assert not [x for x in after['checks'] if 'error' in x or x.get('count',0)>previous.get(x['name'],0)], 'New association errors'
    manifest.update({'changes': changes, 'added': added, 'repair_before_values': {t:{c['key']:repair_values[t][c['key']] for c in changes if c['table']==t} for t in repair_values}, 'count_delta': {t: after['counts'][t]-n for t,n in before['counts'].items()}, 'validation_no_new_issues': True, 'existing_accounts_and_biometrics_unchanged': True})
    dc.save(evidence / 'applied-manifest.json', manifest)
    print(json.dumps({'count_delta': manifest['count_delta'], 'modified_existing_rows': len(changes), 'added_rows': len(added), 'no_new_issues': True}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
