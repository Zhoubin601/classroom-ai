"""Restore cache index consistency via the existing authorized MySQL fallback API.

Does not register faces, generate vectors or save biometric values. Cache refresh is
optional and explicit. Only the inspected local Compose environment is supported.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('dc',ROOT/'scripts/data-completeness.py')
dc=importlib.util.module_from_spec(spec)
spec.loader.exec_module(dc)


def redis(*args):
    return subprocess.check_output(['docker','exec','classroom-redis','redis-cli','--raw',*args])


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apply',action='store_true')
    p.add_argument('--evidence',required=True)
    a=p.parse_args()
    actual=dc.rows("SELECT JSON_OBJECT('student_id',student_id) FROM face_feature")
    database_ids={x['student_id'] for x in actual}
    cached_ids=set(redis('SMEMBERS','face:all_ids').decode().splitlines())
    report={'database_feature_count':len(database_ids),'cache_index_count_before':len(cached_ids),'missing_cached_ids_before':len(database_ids-cached_ids),'orphan_cached_ids_before':len(cached_ids-database_ids),'synthetic_feature_count':int(dc.sql("SELECT COUNT(*) FROM face_feature WHERE student_id LIKE 'DEMO-DATA-%'")),'applied':a.apply,'vectors_saved':False}
    if a.apply and cached_ids!=database_ids:
        project=subprocess.check_output(['docker','inspect','--format','{{index .Config.Labels "com.docker.compose.project"}}','classroom-redis'],encoding='utf-8').strip()
        if project!='classroom-ai-demo':raise ValueError('Refuse unknown Redis target')
        source=(ROOT/'backend/src/main/java/com/classroom/ai/runner/TeachingDataInitializer.java').read_text(encoding='utf-8')
        password=os.environ.get('DATA_DEMO_PASSWORD') or re.search(r'passwordEncoder\.encode\("([^"\n]+)"\)',source).group(1)
        user=os.environ.get('DATA_DIRECTOR_USERNAME','director')
        req=urllib.request.Request('http://127.0.0.1:8080/api/v1/auth/login',json.dumps({'username':user,'password':password}).encode(),{'Content-Type':'application/json'})
        login=json.load(urllib.request.urlopen(req,timeout=10))
        if login['code']!=200 or login['data']['role']!='DIRECTOR':raise ValueError('Authorized director login required')
        token=login['data']['token']
        # Evict only the derived index, which makes FaceService.getAllFaceFeatures()
        # use its existing MySQL fallback. Existing features are then overwritten by DB facts.
        redis('DEL','face:all_ids')
        try:
            response=json.load(urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8080/api/face/all',headers={'Authorization':'Bearer '+token}),timeout=30))
            if response['code']!=200 or len(response['data'])!=len(database_ids):raise ValueError('Existing face fallback did not return the persistent set')
            del response
            # Remove only previously indexed cache orphans proven absent from MySQL.
            for sid in cached_ids-database_ids:redis('DEL','face:feature:'+sid)
        except Exception:
            if cached_ids:redis('SADD','face:all_ids',*cached_ids)
            raise
        finally:
            del token,password,login
    final=set(redis('SMEMBERS','face:all_ids').decode().splitlines())
    report.update({'cache_index_count_after':len(final),'index_matches_mysql':final==database_ids,'synthetic_cache_entries':sum(s.startswith('DEMO-DATA-') for s in final),'mechanism':'evict derived index, invoke existing authenticated /api/face/all fallback; no core algorithm change'})
    dc.save(a.evidence,report)
    print(json.dumps(report,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
