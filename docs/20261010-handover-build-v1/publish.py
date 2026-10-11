import csv, hashlib, json, re, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image
import audit
from build import STAGE, FIGDIR, PREFIX

ROOT,WORK=audit.ROOT,audit.WORK
OUT=ROOT/'output'
MANIFEST='20261010-handover-manifest-v1.json'
ZIP='20261010-developer-handover-materials-v1.zip'

def main():
    before=json.loads((WORK/'protected-before.json').read_text(encoding='utf-8'))
    after=audit.protected()
    changes=[p for p in set(before)|set(after) if before.get(p)!=after.get(p)]
    assert not changes, changes
    head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    assert head=='7464f9cf5875ec3e416d313e11c9838f5a2bec15'
    qa=json.loads((WORK/'visual-qa.json').read_text(encoding='utf-8'))
    assert not qa['errors'] and not qa['externalRequests']
    assert all(not f['overflow'] and not f['nodeOverflow'] for f in qa['figureChecks'])
    assert not qa['desktop']['duplicateIds'] and not qa['desktop']['brokenAnchors']
    assert qa['emptyFiltering']==0 and qa['filtering']['visible']>0
    assert all(x['pageWidth']<=x['viewport'] for x in [qa['desktop'],qa['mobile'],qa['landscape']]+qa['additionalWidths'])
    csvfiles=list(STAGE.glob('*.csv'))
    for p in csvfiles:
        with p.open(encoding='utf-8-sig',newline='') as f:
            rows=list(csv.reader(f)); assert len(rows)>1
            assert all(len(r)==len(rows[0]) for r in rows),p
            assert all(not v.startswith(('=','+','-','@')) for row in rows for v in row),p
    with (STAGE/'20261010-source-index-v1.csv').open(encoding='utf-8-sig',newline='') as f:
        for row in csv.DictReader(f):
            assert audit.digest(ROOT/row['项目相对路径'])==row['SHA256'],row
    for svg in (STAGE/FIGDIR).glob('*.svg'):
        ET.parse(svg)
        png=svg.with_suffix('.png')
        with Image.open(png) as image:
            image.verify()
        assert svg.with_suffix('.mmd').exists()
    md=(STAGE/(PREFIX+'.md')).read_text(encoding='utf-8')
    for link in re.findall(r'!\[[^\]]*\]\(([^)]+)\)',md):
        assert (STAGE/link).is_file(),link
    text=(STAGE/(PREFIX+'.html')).read_text(encoding='utf-8')
    for link in re.findall(r'href="([^"]+)"',text):
        if not link.startswith('#'): assert (STAGE/link).is_file(),link
    allfiles=sorted(p for p in STAGE.rglob('*') if p.is_file() and p.name!=MANIFEST)
    assert len(allfiles)==21,len(allfiles)
    for p in allfiles:
        assert not (OUT/p.relative_to(STAGE)).exists(), 'Existing output; use a new version: '+str(p.name)
    assert not (OUT/MANIFEST).exists() and not (OUT/ZIP).exists()
    manifest={'title':'爱教学开发维护交接材料','date':'2026-10-10','timezone':'Asia/Shanghai','version':'v1',
              'branch':'fix-exp3','head':head,'scope':'当前本地源码、配置、已确认口径与有日期的既有验收报告',
              'counts':{'diagrams':5,'permission_operations':38,'backend_mapping_annotations':97,'vite_local_route_entries':8,'source_entities':24,'source_locations':57},
              'verification':{'source_routes_and_line_markers':True,'source_index_hashes':True,'protected_project_files_unchanged':len(before),'raw_unchanged':True,'svg_xml_and_png_integrity':True,'offline_html_browser_checked':True,'html_external_requests':0,'widths_checked':[375,768,812,1024,1440],'filter_and_anchor_checks':True,'diagrams_visually_reviewed':True},
              'not_executed':['本轮业务测试','服务运行版本查询','部署','数据库备份/恢复','多人识别准确率评估'],
              'review_items':['实际运行版本与源码基线一致性','接手负责人、验收日期与目标机器配置','多人光照识别效果、历史外部微格地址','Sprint2正式评审/回顾与自然人批准证据'],
              'files':[{'path':p.relative_to(STAGE).as_posix(),'bytes':p.stat().st_size,'sha256':audit.digest(p)} for p in allfiles]}
    (STAGE/MANIFEST).write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    allfiles.append(STAGE/MANIFEST)
    for p in allfiles:
        target=OUT/p.relative_to(STAGE)
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(p,target)
    with zipfile.ZipFile(OUT/ZIP,'x',compression=zipfile.ZIP_DEFLATED) as z:
        for p in sorted(allfiles): z.write(p,p.relative_to(STAGE).as_posix())
    with zipfile.ZipFile(OUT/ZIP) as z:
        assert z.testzip() is None
        assert len(z.namelist())==22
        for p in allfiles:
            assert z.read(p.relative_to(STAGE).as_posix())==p.read_bytes()
    for p in allfiles: assert audit.digest(OUT/p.relative_to(STAGE))==audit.digest(p)
    result={'passed':True,'delivered_files':len(allfiles),'zip_entries':22,'zip_bytes':(OUT/ZIP).stat().st_size,'protected_files_unchanged':len(before),'raw_hash':after['raw/README.md'],'zip_sha256':audit.digest(OUT/ZIP),'output':str(OUT),'head':head}
    (WORK/'delivery-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False))

if __name__=='__main__': main()
