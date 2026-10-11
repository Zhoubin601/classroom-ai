from pathlib import Path
import csv, hashlib, html, json, re, shutil, zipfile
import audit, content, diagrams

ROOT, WORK = audit.ROOT, audit.WORK
STAGE = WORK / 'staging'
PREFIX = '20261010-developer-handover-v1'
FIGDIR = '20261010-handover-diagrams-v1'

def writecsv(path, header, rows):
    with path.open('w', encoding='utf-8-sig', newline='') as f:
        w=csv.writer(f); w.writerow(header); w.writerows(rows)

def inline(text):
    tokens=[]
    def keep(m):
        tokens.append('<code>'+html.escape(m.group(1))+'</code>')
        return '\x00%d\x00'%(len(tokens)-1)
    text=re.sub(r'`([^`]+)`',keep,text)
    text=html.escape(text)
    text=re.sub(r'\*\*(.+?)\*\*',r'<strong>\1</strong>',text)
    text=re.sub(r'\[(S\d+)\]',r'<a class="source-ref" href="#\1">[\1]</a>',text)
    text=re.sub(r'\x00(\d+)\x00',lambda m: tokens[int(m.group(1))],text)
    return text

def render(markdown):
    lines=markdown.splitlines(); out=[]; nav=[]; i=0; section=0; opened=False
    while i < len(lines):
        line=lines[i]
        if not line.strip(): i+=1; continue
        if line.startswith('# '): out.append('<h1>'+inline(line[2:])+'</h1>'); i+=1; continue
        if line.startswith('## '):
            if opened: out.append('</section>')
            section+=1; sid='section-%02d'%section; title=line[3:]
            nav.append((sid,title)); out.append(f'<section id="{sid}"><h2>{inline(title)}</h2>'); opened=True; i+=1; continue
        if line.startswith('```'):
            language=line[3:]; i+=1; code=[]
            while i<len(lines) and not lines[i].startswith('```'): code.append(lines[i]); i+=1
            out.append('<pre><code class="language-'+html.escape(language)+'">'+html.escape('\n'.join(code))+'</code></pre>'); i+=1; continue
        image=re.fullmatch(r'!\[([^\]]+)\]\(([^)]+)\)',line)
        if image:
            svg=(STAGE/image.group(2)).read_text(encoding='utf-8')
            out.append('<figure>'+svg+'<figcaption>'+html.escape(image.group(1))+' · <a href="'+html.escape(image.group(2))+'">打开可缩放SVG</a></figcaption></figure>'); i+=1; continue
        if line.startswith('| '):
            rows=[]
            while i<len(lines) and lines[i].startswith('| '):
                row=[x.strip() for x in lines[i].strip().strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?',x) for x in row): rows.append(row)
                i+=1
            perms=rows[0][0].startswith('编号 / 操作')
            if perms:
                out.append('<label class="filter" for="permission-filter">筛选权限操作、角色或范围<input id="permission-filter" type="search" placeholder="例如：草稿、原文件、考勤" autocomplete="off"></label><p id="filter-result" aria-live="polite"></p>')
            out.append('<div class="table-wrap" tabindex="0" role="region" aria-label="'+('权限矩阵' if perms else '资料表格')+'"><table'+(' id="permission-table"' if perms else '')+'><thead><tr>'+''.join('<th scope="col">'+inline(c)+'</th>' for c in rows[0])+'</tr></thead><tbody>')
            for row in rows[1:]:
                sid=' id="'+row[0]+'"' if re.fullmatch(r'S\d+',row[0]) else ''
                out.append('<tr'+sid+'>'+''.join('<td>'+inline(c)+'</td>' for c in row)+'</tr>')
            out.append('</tbody></table></div>'); continue
        if line.startswith('- '):
            out.append('<ul>')
            while i<len(lines) and lines[i].startswith('- '): out.append('<li>'+inline(lines[i][2:])+'</li>'); i+=1
            out.append('</ul>'); continue
        paragraph=[line]; i+=1
        while i<len(lines) and lines[i].strip() and not lines[i].startswith(('#','| ','- ','```','![')): paragraph.append(lines[i]); i+=1
        out.append('<p>'+inline(' '.join(paragraph))+'</p>')
    if opened: out.append('</section>')
    return '\n'.join(out),nav

CSS='''
:root{--ink:#1e293b;--muted:#475569;--line:#e2e8f0;--paper:#fff;--bg:#f8fafc;--blue:#2563eb}*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.85 "Microsoft YaHei","PingFang SC",system-ui,sans-serif}a{color:#1d4ed8;text-underline-offset:4px}a:hover{color:#1e40af}a:focus-visible,input:focus-visible,button:focus-visible,.table-wrap:focus-visible{outline:3px solid #2563eb;outline-offset:4px}.skip{position:fixed;left:20px;top:-80px;z-index:20;background:#fff;padding:12px}.skip:focus{top:12px}aside{position:fixed;inset:0 auto 0 0;width:250px;padding:28px 22px;border-right:1px solid var(--line);overflow:auto;background:#fff}.brand{font-size:22px;font-weight:700;color:var(--ink);margin-bottom:6px}.meta{font-size:13px;line-height:1.7;color:var(--muted);word-break:break-all}.badge{display:inline-block;color:#1e40af;background:#eff6ff;border:1px solid #bfdbfe;padding:3px 9px;border-radius:6px;margin:14px 0 20px;font-size:12px}nav a{display:block;padding:10px 8px;border-radius:6px;text-decoration:none;font-size:14px;line-height:1.55;color:var(--muted)}nav a:hover{background:#eff6ff;color:#1d4ed8}main{margin-left:250px;padding:56px 44px 90px;max-width:1600px}.tools{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:25px}.tools a,button{font:inherit;font-size:14px;display:inline-block;border:1px solid #cbd5e1;border-radius:7px;background:#fff;color:#334155;padding:8px 13px;text-decoration:none;cursor:pointer}h1{font-size:40px;line-height:1.35;letter-spacing:-1px;margin:0 0 16px}h2{font-size:26px;line-height:1.5;margin:0 0 20px;border-bottom:1px solid var(--line);padding-bottom:15px}section{background:#fff;border:1px solid var(--line);border-radius:12px;padding:30px;margin:28px 0;scroll-margin-top:24px}p,ul{margin:15px 0}li{margin:9px 0}code{font:13px/1.7 Consolas,"Microsoft YaHei",monospace;background:#f1f5f9;border-radius:4px;padding:2px 4px;overflow-wrap:anywhere}pre{background:#0f172a;color:#e2e8f0;padding:20px;border-radius:8px;overflow:auto;line-height:1.7}pre code{color:inherit;background:none;padding:0;white-space:pre}.table-wrap{overflow:auto;max-width:100%;margin:22px 0;border:1px solid var(--line);border-radius:8px}table{border-collapse:collapse;width:100%;min-width:660px;font-size:14px;line-height:1.8}th,td{padding:13px 14px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}th{background:#f1f5f9;font-weight:700;white-space:nowrap}tbody tr:nth-child(even){background:#fafcff}tbody tr:hover{background:#eff6ff}#permission-table{min-width:1150px}#permission-table td:first-child{min-width:185px}#permission-table td:nth-child(5){min-width:260px}#permission-table td:last-child{min-width:100px}tr[hidden]{display:none}figure{margin:24px 0;border:1px solid var(--line);border-radius:8px;overflow:hidden;background:#fff}figure svg{display:block;width:100%;height:auto}figcaption{padding:10px 18px;background:#f8fafc;font-size:13px;color:var(--muted)}.source-ref{white-space:nowrap;font-size:12px}label.filter{display:block;margin-top:20px;font-size:14px;font-weight:700}input[type=search]{display:block;width:100%;max-width:470px;min-height:44px;margin-top:9px;border:1px solid #94a3b8;border-radius:7px;padding:9px 13px;font:inherit;font-weight:400}#filter-result{font-size:13px;color:var(--muted)}footer{color:var(--muted);font-size:13px;margin:30px 0 0}@media(max-width:1000px){aside{position:static;width:auto;border-right:0;border-bottom:1px solid var(--line)}nav{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:2px}main{margin-left:0;padding:28px 20px 60px}}@media(max-width:600px){h1{font-size:28px}h2{font-size:21px}section{padding:18px;margin:20px 0}main{padding:25px 12px 50px}aside{padding:20px 14px}nav a{font-size:12px}body{font-size:15px}figure{border-radius:4px}}@media(prefers-reduced-motion:reduce){html{scroll-behavior:auto}*{transition:none!important}}@media print{aside,.tools,.skip,label.filter,#filter-result{display:none}main{margin:0;padding:0;max-width:none}body{background:#fff;font-size:10pt}h1{font-size:22pt}h2{font-size:16pt}section{border:0;padding:0;margin:24px 0;break-before:auto}figure{break-inside:avoid}.table-wrap{overflow:visible;border:0}table,#permission-table{min-width:0;font-size:8pt}th,td{padding:5px;overflow-wrap:anywhere}pre{white-space:pre-wrap}a{color:inherit}section h2{break-after:avoid}}
'''

def main():
    STAGE.mkdir(exist_ok=True)
    (STAGE/FIGDIR).mkdir(exist_ok=True)
    inv=json.loads((WORK/'code-inventory.json').read_text(encoding='utf-8'))
    assert inv['head']=='7464f9cf5875ec3e416d313e11c9838f5a2bec15'
    known={e['method']+' '+e['path'] for e in inv['endpoints']}
    plugintext=(ROOT/'frontend/dev/camera-plugin.mjs').read_text(encoding='utf-8')
    for p in content.PERMISSIONS:
        for route in p['routes']:
            if p['layer']=='后端': assert route in known, route
            else: assert route.split(' ',1)[1] in plugintext, route
    figs=diagrams.make(); paths=[]
    for d in figs:
        base='20261010-'+d.slug+'-v1'
        (STAGE/FIGDIR/(base+'.svg')).write_text(d.svg(),encoding='utf-8')
        (STAGE/FIGDIR/(base+'.mmd')).write_text(d.mermaid(),encoding='utf-8')
        (WORK/(base+'-layout.json')).write_text(json.dumps(d.__dict__,ensure_ascii=False,indent=2),encoding='utf-8')
        paths.append(FIGDIR+'/'+base+'.svg')
    md=content.document(paths)
    (STAGE/(PREFIX+'.md')).write_text(md,encoding='utf-8')
    body,nav=render(md)
    links=''.join(f'<a href="#{id}">{html.escape(title)}</a>' for id,title in nav)
    htmltext='<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>爱教学｜开发维护交接材料</title><style>'+CSS+'</style></head><body><a class="skip" href="#main">跳到正文</a><aside><div class="brand">爱教学 · 开发交接</div><div class="meta">Classroom AI Demo<br>2026-10-10 / v1<br>fix-exp3 / 7464f9c</div><span class="badge">当前源码核对版</span><nav aria-label="交接目录">'+links+'</nav></aside><main id="main"><div class="tools"><a href="'+PREFIX+'.md">Markdown说明</a><a href="20261010-permission-matrix-v1.csv">权限CSV</a><a href="'+FIGDIR+'/20261010-runtime-architecture-v1.svg">架构原图</a><button type="button" onclick="window.print()">打印</button></div>'+body+'<footer>本材料离线可读；正文内嵌SVG。未包含账号凭据、数据库、人脸向量和真人摄像头截图。</footer></main><script>const q=document.getElementById("permission-filter"),rows=[...document.querySelectorAll("#permission-table tbody tr")],result=document.getElementById("filter-result");function filter(){const word=q.value.trim().toLowerCase();let count=0;rows.forEach(row=>{row.hidden=!row.textContent.toLowerCase().includes(word);if(!row.hidden)count++});result.textContent="显示 "+count+" / "+rows.length+" 项操作"}q.addEventListener("input",filter);filter();</script></body></html>'
    (STAGE/(PREFIX+'.html')).write_text(htmltext,encoding='utf-8')
    byid={s['id']:s for s in content.SOURCES}
    def locations(p):
        return '; '.join(byid[id]['path']+':'+str(byid[id]['line']) for id in p['refs'])
    writecsv(STAGE/'20261010-permission-matrix-v1.csv',['编号','操作','执行层','HTTP方法与路径','主任','教师','督导','状态与边界','依据编号','源码文件与行号','基线','核对日期'],[
        [p['id'],p['action'],p['layer'],'; '.join(p['routes']),p['director'],p['teacher'],p['supervisor'],p['conditions'],','.join(p['refs']),locations(p),inv['head'],'2026-10-10'] for p in content.PERMISSIONS])
    endpoints=[[e['method'],e['path'],'Java后端',e['source'],e['line']] for e in inv['endpoints']]
    for p in content.PERMISSIONS:
        if p['layer']=='Vite本地':
            for route in p['routes']:
                method,path=route.split(' ',1)
                line=next(i+1 for i,t in enumerate(plugintext.splitlines()) if "['"+path+"'" in t)
                endpoints.append([method,path,'Vite本地','frontend/dev/camera-plugin.mjs',line])
    writecsv(STAGE/'20261010-endpoint-index-v1.csv',['HTTP方法','路径','执行层','源码文件','行号'],endpoints)
    writecsv(STAGE/'20261010-entity-index-v1.csv',['实体','表名','字段类型与名称','JPA关联','源码文件'],[
        [e['entity'],e['table'],'; '.join(t+' '+n for t,n in e['fields']),'; '.join(j[0]+' -> '+j[1]+'.'+j[2] for j in e['joins']),e['source']] for e in inv['entities']])
    writecsv(STAGE/'20261010-source-index-v1.csv',['编号','项目相对路径','核对行号','SHA256','引用日期'],[
        [s['id'],s['path'],s['line'],audit.digest(ROOT/s['path']),'2026-10-10'] for s in content.SOURCES])
    (WORK/'alignment-summary.json').write_text(json.dumps({'head':inv['head'],'permission_operations':len(content.PERMISSIONS),'backend_annotation_entries':len(inv['endpoints']),'local_plugin_entries':len(endpoints)-len(inv['endpoints']),'entities':len(inv['entities']),'source_locations':len(content.SOURCES),'all_matrix_routes_exist':True,'source_markers_exist':True,'scope':'源码核对；非运行测试'},ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'stage':str(STAGE),'permissions':len(content.PERMISSIONS),'sources':len(content.SOURCES),'figures':len(figs)},ensure_ascii=False))

if __name__=='__main__': main()
