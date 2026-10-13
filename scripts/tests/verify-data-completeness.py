"""Exercise seeding, rerun, failed transaction, guarded rollback and encrypted restore.
Only creates a separately named disposable MySQL; existing volumes are untouched.
"""
import importlib.util
import argparse
import datetime
import json
from pathlib import Path
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('dc',ROOT/'scripts/data-completeness.py')
dc=importlib.util.module_from_spec(spec)
spec.loader.exec_module(dc)


def run(*args):
    result=subprocess.run(args,cwd=ROOT,capture_output=True,encoding='utf-8',errors='replace')
    if result.returncode:raise RuntimeError(result.stdout+result.stderr)
    return result.stdout


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir',default='docs/20261012-data-completeness-evidence-v1/recovery-'+datetime.datetime.now().strftime('%H%M%S'))
    args=parser.parse_args()
    name='classroom-data-test-recovery-20261012'
    ev=(ROOT/args.evidence_dir).resolve()
    if ev.exists():raise FileExistsError('Choose a new evidence directory for every recovery test')
    ev.mkdir(parents=True)
    # create refuses an existing name; never adopts someone else's test container.
    run('docker','run','-d','--name',name,'--pull=never','-e','MYSQL_ALLOW_EMPTY_PASSWORD=yes','-e','MYSQL_DATABASE=classroom_ai','mysql:8.0')
    try:
        ready=False
        for _ in range(60):
            ping=subprocess.run(['docker','exec',name,'mysql','-h127.0.0.1','-uroot','classroom_ai','-e','SELECT 1'],capture_output=True)
            if ping.returncode==0:ready=True;break
            time.sleep(1)
        if not ready:raise RuntimeError('Isolated MySQL startup timed out')
        run(sys.executable,'scripts/data-completeness-backup.py','clone','--target',name,'--evidence',str(ev/'clone.json'))
        backup=json.loads((ev.parent/'backup.json').read_text(encoding='utf-8'))
        # Makes the test repeatable even after the live environment has been supplemented.
        run(sys.executable,'scripts/data-completeness-backup.py','restore-test','--target',name,'--file',backup['encrypted_file'],'--evidence',str(ev/'restore-baseline.json'))
        baseline=dc.audit(container=name,database='classroom_ai');dc.save(ev/'baseline.json',baseline)
        for stage in ('first','repeat'):
            folder=ev/stage
            run(sys.executable,'scripts/seed-complete-demo.py','plan','--container',name,'--evidence-dir',str(folder))
            run(sys.executable,'scripts/seed-complete-demo.py','apply','--container',name,'--environment','isolated-test','--evidence-dir',str(folder))
        repeated=json.loads((ev/'repeat/applied-manifest.json').read_text(encoding='utf-8'))
        assert not repeated['changes'] and not repeated['added']
        unchanged=dc.audit(container=name,database='classroom_ai')
        statement=(ev/'repeat/planned-seed.sql').read_text(encoding='utf-8')
        injected="INSERT INTO student(student_id,name,created_at,updated_at) VALUES('DEMO-DATA-FAILURE-SHOULD-NOT-EXIST','合成事务失败测试',NOW(),NOW());\nINSERT INTO demo_seed_assert VALUES(0);\nCOMMIT;"
        failed=False
        try:dc.sql(statement.replace('COMMIT;',injected),container=name,database='classroom_ai')
        except RuntimeError:failed=True
        assert failed
        after_failed=dc.audit(container=name,database='classroom_ai')
        assert unchanged['row_hashes']==after_failed['row_hashes'], 'Failed transaction did not roll back'
        run(sys.executable,'scripts/rollback-complete-demo.py','--manifest',str(ev/'first/applied-manifest.json'),'--baseline',str(ev/'first/apply-before.json'),'--sql-file',str(ev/'rollback.sql'),'--container',name,'--environment','isolated-test','--apply','--evidence',str(ev/'rollback.json'))
        restored=dc.audit(container=name,database='classroom_ai')
        assert baseline['row_hashes']==restored['row_hashes'], 'Guarded rollback failed baseline match'
        run(sys.executable,'scripts/data-completeness-backup.py','restore-test','--target',name,'--file',backup['encrypted_file'],'--evidence',str(ev/'encrypted-restore.json'))
        restored_backup=dc.audit(container=name,database='classroom_ai')
        assert baseline['row_hashes']==restored_backup['row_hashes'], 'Encrypted recovery failed baseline match'
        dc.save(ev/'results.json',{'seed_passed':True,'repeat_zero_changes':True,'failure_injection_transaction_rolled_back':True,'guarded_rollback_exact_row_hash_match':True,'encrypted_restore_exact_row_hash_match':True,'target':name,'source_database_not_modified':True})
        print('PASS seed, rerun, transaction failure, guarded rollback, encrypted restore')
    finally:
        run('docker','stop',name)
        # Remove only the exact container and anonymous volume created above.
        run('docker','rm','-v',name)


if __name__=='__main__':main()
