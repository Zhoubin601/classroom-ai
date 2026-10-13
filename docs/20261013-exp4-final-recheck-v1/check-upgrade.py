import os, subprocess, time, urllib.request, json
from pathlib import Path

ROOT = Path('/workspace/classroom-ai')
EVIDENCE = ROOT / 'docs/20261013-exp4-final-recheck-v1'
CONTAINER = 'exp4-recheck-mysql'
def sql(query):
    return subprocess.check_output(['docker','exec',CONTAINER,'mysql','--default-character-set=utf8mb4','-uroot','-N','-e',query], text=True).strip()

sql('DROP DATABASE IF EXISTS exp4_upgrade; CREATE DATABASE exp4_upgrade CHARACTER SET utf8mb4;')
for source, target in [('runtime/sprint3-test/legacy-schema.sql','legacy.sql'), ('scripts/deploy/mysql/init/06_exp3_sprint2.sql','sprint2.sql')]:
    subprocess.run(['docker','cp',str(ROOT/source),CONTAINER+':/tmp/'+target],check=True)
    subprocess.run(['docker','exec','-i',CONTAINER,'mysql','--default-character-set=utf8mb4','-uroot','exp4_upgrade'],input=(ROOT/source).read_text(),text=True,check=True)
env = os.environ.copy()
env.update(SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13319/exp4_upgrade?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai',SPRING_DATASOURCE_USERNAME='root',SPRING_DATASOURCE_PASSWORD='',SPRING_DATA_REDIS_PORT='16319',APP_SEED_DEMO='false',CLASSROOM_FFPROBE='/usr/bin/ffprobe',CLASSROOM_UPLOAD_DIR=str(ROOT/'runtime/exp4-final-recheck/upgrade-uploads'))
proof=[]
for number in [1,2]:
    with (EVIDENCE/f'upgrade-{number}.log').open('w') as logfile:
        p=subprocess.Popen(['java','-Xmx384m','-Duser.timezone=Asia/Shanghai','-jar',str(ROOT/'backend/target/classroom-backend-0.0.1-SNAPSHOT.jar'),'--server.port=18090'],cwd=ROOT,env=env,stdout=logfile,stderr=subprocess.STDOUT)
        try:
            for attempt in range(60):
                if p.poll() is not None:raise RuntimeError(f'Upgrade backend exited {p.returncode}')
                try:
                    with urllib.request.urlopen('http://127.0.0.1:18090/api/v1/auth/csrf',timeout=2) as response:
                        if response.status==200:break
                except Exception:time.sleep(1)
            else:raise RuntimeError('Upgrade backend readiness timeout')
            for attempt in range(30):
                result=sql('USE exp4_upgrade; SELECT COUNT(DISTINCT series_id), COUNT(*) FROM t_course_resource; SELECT COUNT(*) FROM s3_advice; SELECT COUNT(*) FROM t_supervision_evaluation WHERE template_version_id IS NULL AND weight_snapshot IS NULL;')
                if result=='2\t2\n1\n1':break
                if p.poll() is not None:raise RuntimeError('Backend exited during migration')
                time.sleep(1)
            assert result=='2\t2\n1\n1',result
            proof.append({'pass':number,'independentSeries':2,'resources':2,'wholeTextAdvice':1,'unknownWeightEvaluations':1})
            print('Upgrade pass',number,'OK',flush=True)
        finally:
            p.terminate()
            try:p.wait(timeout=15)
            except subprocess.TimeoutExpired:p.kill();p.wait()
(EVIDENCE/'upgrade-results.json').write_text(json.dumps(proof,indent=2))
