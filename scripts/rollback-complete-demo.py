"""Generate a guarded rollback for one applied seed manifest.

Default is SQL generation only. Applying to the inspected local development database
requires --apply and --environment local-development. Changed seed rows or external
dependencies cause a hard transaction failure. Upload files and credentials are kept.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import subprocess

spec=importlib.util.spec_from_file_location('dc',Path(__file__).with_name('data-completeness.py'))
dc=importlib.util.module_from_spec(spec)
spec.loader.exec_module(dc)

ORDER=['s3_feedback_revision','s3_action_feedback','s3_advice','t_evaluation_audit_log','t_supervision_evaluation','classroom_record','t_attendance_session','t_offering_student_enrollment','t_course_offering_teacher','t_course_schedule','t_course_offering','t_micro_teaching_slice','s3_video_asset','t_resource_access_log','t_course_resource','s3_resource_series','t_course_content_revision','t_training_indicator','student']
LINKS=[('t_offering_student_enrollment','student_id','student'),('t_attendance_session','offering_id','t_course_offering'),('t_course_schedule','offering_id','t_course_offering'),('t_course_offering_teacher','offering_id','t_course_offering'),('t_offering_student_enrollment','offering_id','t_course_offering'),('t_supervision_evaluation','offering_id','t_course_offering'),('s3_advice','evaluation_id','t_supervision_evaluation'),('t_evaluation_audit_log','evaluation_id','t_supervision_evaluation'),('s3_action_feedback','suggestion_id','s3_advice'),('s3_feedback_revision','feedback_id','s3_action_feedback'),('t_micro_teaching_slice','asset_id','s3_video_asset'),('t_resource_access_log','resource_id','t_course_resource'),('t_course_resource','series_id','s3_resource_series')]


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--manifest',required=True)
    p.add_argument('--baseline',required=True)
    p.add_argument('--sql-file',required=True)
    p.add_argument('--container',default='classroom-mysql')
    p.add_argument('--environment',choices=['local-development','isolated-test'])
    p.add_argument('--apply',action='store_true')
    p.add_argument('--evidence')
    args=p.parse_args()
    manifest=json.loads(Path(args.manifest).read_text(encoding='utf-8'))
    baseline=json.loads(Path(args.baseline).read_text(encoding='utf-8'))
    if manifest.get('source')!='DEMO-DATA-20261012-v1' or 'repair_before_values' not in manifest:
        raise ValueError('A verified manifest with original repair values is required')
    allrows=manifest['added']+manifest['changes']
    if any(not str(r['key']).isdigit() for r in allrows):
        raise ValueError('Unexpected primary key')
    statements=["CREATE TEMPORARY TABLE rollback_assert(v INT CHECK(v=1));","INSERT INTO rollback_assert VALUES(GET_LOCK('DEMO-DATA-20261012-v1',10));",'START TRANSACTION;']
    for r in allrows:
        cols=[dc.ident(c['column']) for c in baseline['columns'] if c['table']==r['table']]
        digest="SHA2(CAST(JSON_ARRAY("+','.join(cols)+") AS CHAR),256)"
        expected=r.get('after_sha256',r.get('sha256'))
        statements.append('INSERT INTO rollback_assert SELECT COUNT(*)=1 FROM '+dc.ident(r['table'])+' WHERE id='+r['key']+' AND '+digest+'='+dc.quote(expected)+';')
    owned={t:[r['key'] for r in manifest['added'] if r['table']==t] for t in ORDER}
    for child,col,parent in LINKS:
        parents=owned.get(parent,[])
        if not parents:continue
        exclusion=' AND id NOT IN('+','.join(owned[child])+')' if owned.get(child) else ''
        statements.append('INSERT INTO rollback_assert SELECT COUNT(*)=0 FROM '+dc.ident(child)+' WHERE '+dc.ident(col)+' IN('+','.join(parents)+')'+exclusion+';')
    if owned.get('student'):
        statements.append('INSERT INTO rollback_assert SELECT COUNT(*)=0 FROM face_feature f JOIN student s ON BINARY s.student_id=BINARY f.student_id WHERE s.id IN('+','.join(owned['student'])+');')
    if owned.get('t_attendance_session'):
        exclusion=' AND id NOT IN('+','.join(owned['classroom_record'])+')' if owned.get('classroom_record') else ''
        statements.append('INSERT INTO rollback_assert SELECT COUNT(*)=0 FROM classroom_record WHERE session_id IN(SELECT CONCAT(\'ATT_\',id) FROM t_attendance_session WHERE id IN('+','.join(owned['t_attendance_session'])+'))'+exclusion+';')
    if owned.get('s3_resource_series'):
        statements.append('UPDATE s3_resource_series SET current_version_id=NULL WHERE id IN('+','.join(owned['s3_resource_series'])+');')
    for table in ORDER:
        if owned[table]:
            statements.append('DELETE FROM '+dc.ident(table)+' WHERE id IN('+','.join(owned[table])+');')
    for table,records in manifest['repair_before_values'].items():
        for key,values in records.items():
            assignments=','.join(dc.ident(k)+'='+('NULL' if v is None else str(v) if isinstance(v,(int,float)) else dc.quote(v)) for k,v in values.items() if k!='id')
            statements.append('UPDATE '+dc.ident(table)+' SET '+assignments+' WHERE id='+key+';')
    statements.extend(['COMMIT;',"DO RELEASE_LOCK('DEMO-DATA-20261012-v1');"])
    content='\n'.join(statements)+'\n'
    output=Path(args.sql_file)
    if output.exists() and output.read_text(encoding='utf-8')!=content:
        raise FileExistsError('Rollback output already exists with different content')
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(content,encoding='utf-8')
    if args.apply:
        if args.environment=='isolated-test':
            if not args.container.startswith('classroom-data-test-'):raise ValueError('Wrong isolated target')
        elif args.environment=='local-development':
            if args.container!='classroom-mysql':raise ValueError('Wrong local target')
            project=subprocess.check_output(['docker','inspect','--format','{{index .Config.Labels "com.docker.compose.project"}}',args.container],encoding='utf-8').strip()
            if project!='classroom-ai-demo':raise ValueError('Refuse unknown local Compose environment')
        else:raise ValueError('Explicit target environment required')
        before=dc.audit(container=args.container,database='classroom_ai')
        dc.sql(content,container=args.container,database='classroom_ai')
        after=dc.audit(container=args.container,database='classroom_ai')
        dc.save(args.evidence,{'applied':True,'deleted_seed_rows':len(manifest['added']),'restored_existing_rows':len(manifest['changes']),'counts':after['counts'],'row_hashes':after['row_hashes']})
    print(json.dumps({'generated_sql':str(output),'applied':args.apply,'seed_owned_rows':len(manifest['added'])}))


if __name__=='__main__':main()
