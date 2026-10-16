from pathlib import Path
import json,re,html,base64,hashlib,shutil,textwrap,unicodedata,statistics,xml.etree.ElementTree as ET
from PIL import Image
E=Path(__file__).resolve().parent; ROOT=E.parents[1]; NAME='20261016-exp1-exp4-feature-trace-v1'
STAGE=E/'delivery-stage'; IMG=STAGE/(NAME+'-images'); IMG.mkdir(parents=True,exist_ok=True)
D=json.loads((E/'catalog.json').read_text(encoding='utf-8')); B=json.loads((E/'baseline.json').read_text(encoding='utf-8'))
esc=lambda x:html.escape(str(x),quote=True)
def readjson(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def asset(name,content):
 (IMG/name).write_text(content,encoding='utf-8');(E/'diagram-sources').mkdir(exist_ok=True)
 if name.startswith(('chain','route','overview','example')):(E/'diagram-sources'/name).write_text(content,encoding='utf-8')
 return name
def svgstart(w,h,title):return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" role="img" aria-label="{esc(title)}"><defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto"><path d="M0,0 L8,3 L0,6 Z" fill="#6366f1"/></marker></defs><rect width="100%" height="100%" rx="20" fill="#f8fafc"/><g font-family="Microsoft YaHei, Noto Sans CJK SC, sans-serif">'
def txt(x,y,t,size=17,color='#243047',weight='normal'):return f'<text x="{x}" y="{y}" font-size="{size}" fill="{color}" font-weight="{weight}">{esc(t)}</text>'
def lines(x,y,text,width=26,size=17,color='#243047',limit=4):
 chunks=[]; current=''; units=0
 for char in text:
  if char=='\n':
   if current:chunks.append(current)
   current='';units=0;continue
  cost=2 if unicodedata.east_asian_width(char) in 'WF' else 1
  if units+cost>width and current:chunks.append(current);current='';units=0
  current+=char;units+=cost
 if current:chunks.append(current)
 if len(chunks)>limit:chunks=chunks[:limit];chunks[-1]=chunks[-1][:-4]+'…'
 return ''.join(txt(x,y+i*(size+9),t,size,color) for i,t in enumerate(chunks))
def arrow(x1,y1,x2,y2):return f'<path d="M{x1},{y1} L{x2},{y2}" stroke="#6366f1" stroke-width="2.5" fill="none" marker-end="url(#arrow)"/>'
def box(x,y,w,h,label,detail,num=None):
 s=f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="12" fill="white" stroke="#cbd5e1"/>'
 s+=txt(x+18,y+31,(str(num)+'  ' if num else '')+label,18,'#3730a3','bold')
 return s+lines(x+18,y+62,detail,width=max(16,int((w-38)/10)),size=16,limit=max(2,int((h-60)/25)))
groups=[('课程底座',[1,2,3,5,6,4],'主任 → 教师 → 主任 → 督导 → 教师'),('教学资源',[7,8,10,9,11,12],'教师 → 同室教师/督导 → 主任 → 管理员'),('评价与改进',[20,13,14,21],'管理员 → 督导 → 主任 → 教师'),('统计与分析',[15,16,17,19,18],'督导 → 主任 → 教师 → 督导 → 主任')]
byid={d['n']:d for d in D}
s=svgstart(1280,770,'实验一21条故事全景')+txt(36,48,'从课程底座到教学改进：21 条需求，4 条业务路径',28,'#172554','bold')
for j,(g,ids,roles) in enumerate(groups):
 x=32+j*312;s+=f'<rect x="{x}" y="80" width="294" height="645" rx="14" fill="white" stroke="#cbd5e1"/>'
 s+=txt(x+18,116,f'0{j+1}  {g}',22,'#4338ca','bold')
 for k,n in enumerate(ids):s+=txt(x+18,161+k*77,f'US-{n:02}',17,'#6366f1','bold')+lines(x+18,185+k*77,byid[n]['title'],23,16,limit=2)
 s+=lines(x+18,675,roles,23,14,'#475569',2)
s+='</g></svg>';asset('overview.svg',s)
for j,(g,ids,roles) in enumerate(groups,1):
 s=svgstart(1280,560,g+'业务操作路径')+txt(32,46,f'路径 {j}｜{g}',27,'#172554','bold')+txt(32,78,roles,17,'#475569')
 for k,n in enumerate(ids):
  x=32+(k%3)*418;y=112+(k//3)*210;d=byid[n]
  s+=box(x,y,382,174,d['id']+' '+d['title'],d['steps'][0]+'；'+d['steps'][-1],k+1)
  if k%3<2 and k+1<len(ids):s+=arrow(x+385,y+85,x+413,y+85)
  if k==2 and len(ids)>3:s+=f'<path d="M1240,286 L1240,306 L13,306 L13,407 L30,407" fill="none" stroke="#6366f1" stroke-width="2.5" marker-end="url(#arrow)"/>'
 s+=txt(32,540,'前提：各测试夹具相互隔离；箭头表示业务依赖与演示次序，具体写权限以角色范围和当前状态为准。',15,'#475569')+'</g></svg>'
 asset(f'route-{j}.svg',s)

suite=[]
for f in sorted((E/'backend-full-xml').glob('TEST-*.xml')):
 root=ET.parse(f).getroot()
 for c in root.findall('testcase'):
  state='失败' if c.find('failure') is not None or c.find('error') is not None else '跳过' if c.find('skipped') is not None else '本次通过'
  suite.append({'class':c.attrib['classname'],'name':c.attrib['name'],'seconds':c.attrib.get('time'),'result':state,'evidence':str(f.relative_to(ROOT)).replace('\\','/')})
assert len(suite)==284 and all(t['result']=='本次通过' for t in suite)
assert len(readjson(E/'sprint2-browser/results.json'))==7
FINAL=E/'browser-final'
assert (FINAL/'supplement/results.json').exists(),'Final supplemental flow has not completed'
supp=readjson(FINAL/'supplement/results.json');assert not supp['pageErrors']
browser=readjson(FINAL/'sprint3-browser/browser-results.json');assert browser['passed']==8 and not browser['pageErrors']
fixture=readjson(FINAL/'sprint3-browser/fixture-ids.json')
catalog_checks=[]
def srcref(a):return f"{a['path']}:{a['line']}"
def p_url(p,line=None):return '/'+str(p).replace('\\','/')+(f':{line}' if line else '')
def link(a,label=None):return f'<a class="source-link" href="{esc(Path(a["path"]).as_uri()+'#L'+str(a["line"]))}">{esc(label or a["file"])}:{a["line"]}</a>'
def mdlink(a,label=None):return f'[{label or a["file"]}](<{p_url(a["path"],a["line"])}>)'
def filelink(p,label=None):return f'<a href="{esc((ROOT/p).as_uri())}">{esc(label or p)}</a>'
def imghtml(name,alt,cls='diagram'):
 p=IMG/name;data=base64.b64encode(p.read_bytes()).decode()
 mime='image/svg+xml' if p.suffix=='.svg' else 'image/png'
 return f'<figure class="{cls}"><button type="button" class="zoom" aria-label="放大：{esc(alt)}"><img loading="lazy" src="data:{mime};base64,{data}" alt="{esc(alt)}"/></button><figcaption>{esc(alt)} · 点击放大，Esc 关闭</figcaption></figure>'
def mdimg(name,alt):return f'![{alt}]({NAME}-images/{name})\n'
for d in D:
 if d['n']==6:
  perf=readjson(E/'legacy-browser/us0406-query-performance.json')
  d['points'].append('本轮检索实测：3门课程、3班次、2教师、95合成学生、2专业；20次顺序请求，中位数'+str(round(statistics.median(perf['milliseconds']),2))+'ms，P95 '+str(round(perf['p95'],2))+'ms；中位数按20个观测的中间两值平均重算，P95沿用最近秩。仅为本地小样本，非生产容量承诺。')
 checks=[]
 for cls,name in d['testSelectors']:
  hits=[t for t in suite if t['class'].split('.')[-1]==cls and t['name']==name]
  assert hits,(d['id'],cls,name)
  for t in hits:
   p=ROOT/'backend/src/test/java'/Path(*t['class'].split('.')).with_suffix('.java')
   method=t['name'].split('(')[0]
   ls=p.read_text(encoding='utf-8').splitlines();idx=[i for i,l in enumerate(ls,1) if re.search(r'\b'+re.escape(method)+r'\s*\(',l)]
   assert idx,(p,method)
   t={**t,'file':str(p.relative_to(ROOT)).replace('\\','/'),'path':str(p),'line':idx[0]}
   checks.append(t)
 d['checks']=checks
 source=(FINAL/d['shot']) if (FINAL/d['shot']).exists() else E/d['shot']
 assert source.exists(),source
 d['screenshotSource']=str(source.relative_to(ROOT)).replace('\\','/')
 # Formal screenshots are exact PNG copies, with separate SVG labels around them.
 png=f'shot-{d["n"]:02}.png';shutil.copyfile(source,IMG/png)
 w,h=Image.open(source).size
 s=svgstart(w,h+180,d['id']+'关键页面')+txt(26,36,d['id']+'｜'+d['title'],25,'#172554','bold')
 s+=lines(26,65,'① 操作入口：'+d['steps'][0],max(50,int(w/17)),17,limit=2)
 s+=f'<image x="0" y="105" width="{w}" height="{h}" href="data:image/png;base64,{base64.b64encode(source.read_bytes()).decode()}"/>'
 caption={'1':'预检错误清单阻止错误数据确认入库','7':'上传入口与字段；成功及失败断言见测试记录','8':'多选标签与教研室共享开关，可独立维护','9':'受控PDF实际渲染，限时票据与水印同时生效','15':'所选学期下课程覆盖统计及班次入口；分子分母由明细追溯'}.get(str(d['n']),d['normal'])
 s+=lines(26,h+136,'② 结果要点：'+caption,max(50,int(w/17)),17,limit=2)+'</g></svg>'
 d['shotAsset']=asset(f'shot-{d["n"]:02}-annotated.svg',s)
 anchors=d['anchors']; layers=[('页面 / 事件',anchors[0]['file'].split('/')[-1]+':'+str(anchors[0]['line'])+'\n'+anchors[0]['symbol']),('前端 API',anchors[1]['symbol']+'\n'+d['apis'][0]),('HTTP / Controller',anchors[2]['file'].split('/')[-1]+':'+str(anchors[2]['line'])+'\n'+anchors[2]['symbol'].split('(')[0].split()[-1]),('业务校验',anchors[3]['file'].split('/')[-1]+':'+str(anchors[3]['line'])+'\n'+anchors[3]['symbol']),('持久化 / 数据',d['storage']),('状态 / 页面结果',d['change'])]
 s=svgstart(1280,590,d['id']+'实现链路')+txt(32,46,d['id']+'｜'+d['title']+'：从点击到数据再到回显',26,'#172554','bold')
 coords=[(30,85),(450,85),(870,85),(870,335),(450,335),(30,335)]
 for k,((label,detail),(x,y)) in enumerate(zip(layers,coords)):
  s+=box(x,y,380,205,label,detail,k+1)
  if k<2:s+=arrow(x+383,y+100,x+413,y+100)
  elif k==2:s+=arrow(x+190,y+209,x+190,y+245)
  elif k in [3,4]:s+=arrow(x-3,y+100,x-37,y+100)
 s+=txt(32,574,'定位口径：路径、方法名与行号来自当前exp4源码；详细接口、持久化实体和测试方法见正文。',15,'#475569')+'</g></svg>'
 d['chainAsset']=asset(f'chain-{d["n"]:02}.svg',s)
 for part in d['acceptance'].strip('。').split('；'):
  catalog_checks.append({'us':d['id'],'criterion':part,'result':'本次软件检查通过','checks':[t['class'].split('.')[-1]+'#'+t['name'] for t in checks],'screenshot':d['screenshotSource']})
(E/'requirements-coverage.json').write_text(json.dumps(catalog_checks,ensure_ascii=False,indent=2),encoding='utf-8')
(E/'executed-junit-tests.json').write_text(json.dumps(suite,ensure_ascii=False,indent=2),encoding='utf-8')
(E/'verified-catalog.json').write_text(json.dumps(D,ensure_ascii=False,indent=2),encoding='utf-8')

intro='本报告将实验一正式项目规划中的21条用户故事，逐项对应到exp4的页面操作、源码、接口、数据变化与本轮隔离实测。四条路径用于组织讲解，21条故事逐项保留原编号。'
boundary='范围仅含US-01～US-21；US-05归Sprint 2，但在课程底座路径讲解。摄像头、人脸考勤及现场设备性能不在本报告范围。原件表5/6/7为需求基线，当前实现不自动增加验收要求。'
evidence_text='本轮前端23项通过并完成生产构建；后端重新打包。完整隔离数据库回归284项通过，失败/错误/跳过均为0；Sprint 2正式入口7套通过；Sprint 1真实浏览器覆盖3角色、5故事、10个子流程；Sprint 3真实业务8组通过。不同检查存在覆盖重叠，不能相加为功能数。'
limits='测试使用本地初始化样例、合成指标、合成名单及纯合成视频。默认24小时分支先验证审核至发布时间相差86400秒，再推进隔离记录发布时间；另测0小时自然发布。未真实等待24小时。自然人正式批准、真实会议、学校正式培养方案资料未提供。'
issues=[('展示口径','TeacherDeskView.vue中的“BOPPPS 教学模型”和“BOPPPS 进阶教改建议”容易让人把四维评分等同BOPPPS；四维实际为教学态度、内容、方法、效果。当前仅记录，未修改业务代码。'),('上传配置提示','教师端setResourceFile仍固定以100MB作本地预检查，真实上传由服务端可配置策略决定。非默认PDF-only/1024字节实测生效；演示其他配置时应以服务器错误信息为准。'),('范围与格式','US-18原件要求质量报表导出，没有指定PDF；当前出口为CSV/XLSX。US-12是授权片段和元数据接口，不证明自动切片或自动课堂分析。'),('运行实例','源码基线不证明日常8080/5173实例已部署同版。测试期间观察到原有三个日常容器停止；本轮没有向其发送启停、重建或数据库写入命令，停止原因未由本轮证据确定。'),('补充脚本重试','补充截图第一次被未保存修改离开保护阻止；第二次错误等待了教师反馈页不展示的听课主题。均为采集脚本问题，诊断保留在过程目录；修正后新隔离环境完成，业务代码未改。')]
normal_inputs={1:'有效CSV编码BROWSER-IMPORT，专业SE，学分3，学时48；错误示例学分-1',2:'课程简介、合成考核、合成目标；发布v1后再次保存草稿',3:'文管A447，95名合成学生，主讲＋协同教师及周次/节次',4:'2026秋季及不存在学期；两个班次同一学生用于区分累计人次',5:'2条合成指标，独立planVersion及两个大纲版本',6:'SE＋学期＋协同教师ID＋课程关键词；另测未授权专业',7:'有效PDF/DOCX/PPTX；损坏内容；超过100MB；PDF-only/1024字节配置',8:'理论、实验、讨论等白名单多选与空标签',9:'已登录督导、绑定申请人的票据；到期/异账号/撤销票据',10:'同室共享/私有、外室资源与唯一关键词',11:'同seriesId首版/新版、固定历史ID及旧expectedVersion',12:'2秒纯合成MP4；500–1500ms片段；负值/反向/超时区间',13:'四维明确0分/缺失值、至少3条不同亮点及已发布模板',14:'默认24h、0h配置、审核通过/驳回及到期前后',15:'选定学期有效课程、重复评价、无效班次及评价ID明细',16:'低分阈值75与原始均分74.99；无权重/缺失维度',17:'本人到期反馈、2个样本、100分归一化；明确0分与缺失值',18:'同筛选page=1,size=1，导出CSV/XLSX全量与评价ID',19:'专业核心/公共选修/未分类；审核标签、未标注及其他学期',20:'25/25/25/25、小数权重、总和不100及旧revision',21:'建议Practice in groups；ACCEPTED/ADJUSTED；行动、反馈、证据及历史revision'}
def checks_html(d):
 return '<ul class="checks">'+''.join(f'<li><span class="pass">本次通过</span> {link(t,t["class"].split(".")[-1]+"#"+t["name"])} · {filelink(t["evidence"],"XML结果")}</li>' for t in d['checks'])+'</ul>'
def check_md(d):return '\n'.join('- 本次通过：'+mdlink(t,t['class'].split('.')[-1]+'#'+t['name'])+'；证据：'+t['evidence'] for t in d['checks'])
def list_html(items):return '<ul>'+''.join('<li>'+esc(t)+'</li>' for t in items)+'</ul>'
def table(headers,rows):return '<div class="table-scroll"><table><thead><tr>'+''.join('<th>'+esc(h)+'</th>' for h in headers)+'</tr></thead><tbody>'+''.join('<tr>'+''.join('<td>'+str(c)+'</td>' for c in row)+'</tr>' for row in rows)+'</tbody></table></div>'
def mdtable(headers,rows):
 def clean(s):return str(s).replace('|','／').replace('\n','<br>')
 return '\n|'+'|'.join(headers)+'|\n|'+'|'.join('---' for _ in headers)+'|\n'+''.join('|'+'|'.join(clean(x) for x in r)+'|\n' for r in rows)+'\n'

overview_rows=[(f'<a href="#{d["id"]}">{d["id"]}</a>',esc(d['title']),esc(d['role']),esc(d['group']),'<span class="pass">核心软件本轮通过</span>') for d in D]
H=[f'<section id="intro"><p class="eyebrow">实验一 → EXP4 · 需求追踪与实测图解</p><h1>21 条需求，<br>沿业务路径讲清实现。</h1><p class="lead">{intro}</p><div class="kpis"><div><b>21</b><span>用户故事</span></div><div><b>4</b><span>业务路径</span></div><div><b>284</b><span>隔离后端测试通过</span></div><div><b>21</b><span>关键页面标注图</span></div></div><p class="meta">2026-10-16 · Asia/Shanghai · exp4 / {B["head"][:7]}</p><div class="note">{boundary}</div><h2>本轮证据与边界</h2><p>{evidence_text}</p><p>{limits}</p></section>']
M=[f'# 实验一需求在 exp4 中的实现路径、测试与图文汇总\n\n2026-10-16 · Asia/Shanghai · exp4 / `{B["head"]}`\n\n{intro}\n\n{boundary}\n\n{evidence_text}\n\n{limits}\n']
H+=['<section id="overview"><h2>功能全景</h2>'+imghtml('overview.svg','21条需求的业务全景')+table(['需求','功能','角色','业务路径','本轮结论'],overview_rows)+'</section>']
M+=['## 功能全景\n'+mdimg('overview.svg','21条需求的业务全景')+mdtable(['需求','功能','角色','业务路径','本轮结论'],[(d['id'],d['title'],d['role'],d['group'],'核心软件本轮通过') for d in D])]
example_steps=[('管理员','模板管理 → 模板草稿 → 保存并发布25/25/25/25','Sprint3Workspace.publish → POST /admin/templates/{id}/publish → EvaluationTemplateService.publish','s3_template_version新增不可变版本；模板ID '+str(fixture['templateId'])),('督导','对SYN-CORE班次暂存评价，写入独立建议，再正式提交','POST /supervisions → AdviceService.receive → submitEvaluation','核心样例评价ID '+str(fixture['evaluations'][0])+'；模板及weight_snapshot固定；状态PENDING_REVIEW'),('主任','教研室主任工作台 → 评价审核 → 通过','POST /supervisions/{id}/review → SupervisionServiceImpl.reviewEvaluation','APPROVED_PENDING；reviewedAt、publishTime与审核日志保存'),('教师不可见阶段','教师请求评价详情并查询雷达','GET /supervisions/{id} → 403；GET /sprint3/radar → 尚未纳入','督导身份与尚未到期内容没有提前反馈给教师'),('隔离时钟推进','仅更新本次测试评价的publish_time至过去','核对默认86400秒后，通过隔离SQL推进；0h配置另行自然发布','不更改系统时钟；未真实等待24小时'),('教师','统计与反馈 → 评价分析 → 改进行动 → 保存行动反馈','GET /sprint3/suggestions/mine → PUT /suggestions/{id}/my-feedback → AdviceService.feedback','建议Practice in groups；行动及Synthetic reflection collected落库，修订历史保留')]
H+=['<section id="example"><h2>一条完整路径：评价到教学改进</h2><p>下表串联同一SYN-CORE评价样例，ID来自本轮隔离夹具；模板创建、分析展示与行动保存经过真实浏览器，评价/独立建议初始输入由真实API构造。补充浏览器另实测了评价表单提交和主任审核，截图注明为补充样例，不混写为相同记录。</p>'+table(['角色/阶段','页面与操作','实现链路（API省略共同前缀/api/v1）','数据变化'],[(esc(a),esc(b),esc(c),esc(d)) for a,b,c,d in example_steps])+imghtml('shot-13-annotated.svg','补充样例：督导填写评价表单')+imghtml('shot-14-annotated.svg','补充样例：审核及到期后的教师反馈')+imghtml('shot-21-annotated.svg','核心样例：教师保存建议行动反馈')+'</section>']
M+=['## 一条完整路径：评价到教学改进\n\n核心样例为同一SYN-CORE评价。模板创建、分析展示、行动保存走真实浏览器；评价和独立建议初始输入通过真实API构造。补充截图另外覆盖评价表单与主任审核，属于另一明确标记的合成记录。\n'+mdtable(['角色/阶段','页面操作','实现链路（省略共同前缀/api/v1）','数据变化'],example_steps)+mdimg('shot-13-annotated.svg','补充样例：督导填写评价')+mdimg('shot-14-annotated.svg','补充样例：教师匿名反馈')+mdimg('shot-21-annotated.svg','核心样例：教师行动反馈')]
for j,(g,ids,roles) in enumerate(groups,1):
 H+= [f'<section id="route-{j}"><p class="eyebrow">业务路径 0{j}</p><h2>{g}</h2><p>{esc(roles)}</p>'+imghtml(f'route-{j}.svg',g+'操作次序与角色切换')+'</section>']
 M+=[f'## 路径{j}：{g}\n\n{roles}\n\n'+mdimg(f'route-{j}.svg',g+'操作次序与角色切换')]
 for n in ids:
  d=byid[n];prefix=f'TC-{n:02}'
  # Normal walkthrough and branch designs are bound to executed assertions, not claimed as manual executions.
  cases=[(prefix+'-N','正常路径',normal_inputs[n],'依次执行本项页面步骤；核对结果并回查数据。',d['normal'],'本次端到端及关联自动化通过'),(prefix+'-B1','异常/边界',d['edges'][0],'构造该边界输入或状态，发起相同操作，检查拒绝/过滤结果与数据不被误写。',d['edges'][0],'本次关联自动化通过'),(prefix+'-B2','权限/状态',d['edges'][-1],'切换授权或状态后重试；同时核对UI和HTTP/数据库结果。',d['edges'][-1],'本次关联自动化通过')]
  d['caseDesigns']=cases
  rows=[]
  for a in d['anchors']:rows.append((link(a),f'<code>{esc(a["symbol"])}</code>',esc(a['why'])))
  h=f'<article id="{d["id"]}" class="story" data-role="{esc(d["role"])}"><div class="story-head"><div><p class="eyebrow">{d["id"]} · 原件表{d["table"]}第{d["row"]}行</p><h3>{esc(d["title"])}</h3></div><span class="role">{esc(d["role"])}</span></div><p class="status">核心软件本轮验证通过 · 待人工正式签收</p><h4>需求原文与验收标准</h4><blockquote>{esc(d["story"])}</blockquote><p class="acceptance">{esc(d["acceptance"])}</p><h4>页面点哪里</h4><ol>'+''.join('<li>'+esc(x)+'</li>' for x in d['steps'])+'</ol><p><strong>前置及输入：</strong>'+esc(normal_inputs[n])+'。使用该角色授权数据；各套测试夹具独立。</p>'+imghtml(d['shotAsset'],d['id']+'关键页面：入口与结果标注','screenshot')+'<h4>从页面到数据的实现链路</h4>'+imghtml(d['chainAsset'],d['id']+'六层实现链路')+'<p><strong>HTTP接口：</strong></p>'+list_html(d['apis'])+'<p><strong>存储：</strong>'+esc(d['storage'])+'</p><p><strong>数据变化：</strong>'+esc(d['change'])+'</p>'+table(['关键源码位置','方法/锚点','职责和要点'],rows)+'<h4>测试用例与本轮结果</h4><p>下列N/B用例用于照着演示与复测；实际执行载体为后列JUnit方法及浏览器脚本，不把用例设计编号计为另外执行次数。</p>'+table(['编号','场景','输入/条件','步骤','预期结果','实测关联'],[(esc(a),esc(b),esc(c),esc(step),esc(expect),esc(result)) for a,b,c,step,expect,result in cases])+checks_html(d)+'<p class="small">页面原始证据：'+filelink(d['screenshotSource'])+'</p><h4>答辩时要指出的要点</h4>'+list_html(d['points'])+'</article>'
  H.append(h)
  M.append(f'### {d["id"]} {d["title"]}\n\n角色：{d["role"]}。原件表{d["table"]}第{d["row"]}行。核心软件本轮验证通过；人工正式签收另行办理。\n\n**需求原文：** {d["story"]}\n\n**验收标准：** {d["acceptance"]}\n\n**页面操作：**\n\n'+'\n'.join(f'{i}. {x}' for i,x in enumerate(d['steps'],1))+f'\n\n**前置及输入：** {normal_inputs[n]}。使用该角色授权数据，各套测试夹具独立。\n\n'+mdimg(d['shotAsset'],d['id']+'关键页面：入口与结果标注')+'\n**实现链路：**\n\n'+mdimg(d['chainAsset'],d['id']+'六层实现链路')+'\n**HTTP接口：**\n\n'+'\n'.join('- `'+a+'`' for a in d['apis'])+'\n\n**存储：** '+d['storage']+'\n\n**数据变化：** '+d['change']+'\n'+mdtable(['关键源码位置','方法/锚点','职责与要点'],[(mdlink(a),a['symbol'],a['why']) for a in d['anchors']])+'\n**用例设计与本轮实测关联：** N/B编号是复测用例设计，实际执行载体为下面的JUnit方法及浏览器脚本，不另计执行次数。\n'+mdtable(['编号','场景','输入/条件','步骤','预期结果','实测关联'],cases)+'\n'+check_md(d)+'\n\n页面原始证据：'+d['screenshotSource']+'\n\n**讲解要点：**\n\n'+'\n'.join('- '+x for x in d['points'])+'\n')

H+=['<section id="tests"><h2>测试结果与逐条验收覆盖</h2><p>'+evidence_text+'</p><p>284项属于现有整套后端回归，包含少量范围外防回归测试；本报告只展开21条需求。不将重复引用的测试重复计数。</p>'+table(['检查','本轮结果','证据'],[('前端单元','23通过，0失败/跳过',filelink(str((E/'frontend-tests.log').relative_to(ROOT)))),('前后端构建','当前源码重新构建成功',filelink(str((E/'backend-package.log').relative_to(ROOT)))),('完整隔离后端','284通过，0失败/错误/跳过',filelink(str((E/'backend-full.log').relative_to(ROOT)))),('Sprint 1真实浏览器','3角色、5故事、10子流程通过',filelink(str((E/'legacy-browser/us0203-real-browser.log').relative_to(ROOT)))),('Sprint 2正式入口','7套全部PASS',filelink(str((E/'sprint2-browser/results.json').relative_to(ROOT)))),('Sprint 3真实业务','8组通过；页面错误0',filelink(str((FINAL/'sprint3-browser/browser-results.json').relative_to(ROOT)))),('补充页面闭环','课程错误预检、内容发布、评价提交/审核/延迟脱敏及覆盖明细通过',filelink(str((FINAL/'supplement/results.json').relative_to(ROOT))))])+f'<h3>原件验收标准拆解：{len(catalog_checks)}项</h3>'+table(['需求','原文验收点','本轮判断'],[(f'<a href="#{c["us"]}">{c["us"]}</a>',esc(c['criterion']),esc(c['result'])) for c in catalog_checks])+'<details><summary>查看284项实际执行登记</summary>'+table(['测试类','方法','结果','秒'],[(esc(t['class'].split('.')[-1]),esc(t['name']),esc(t['result']),esc(t['seconds'])) for t in suite])+'</details></section>']
M+=['## 测试结果与逐条验收覆盖\n\n'+evidence_text+'\n\n284项为整套后端防回归结果，包含少量本报告范围外检查；逐需求仅展开关联项。相同方法被多条需求引用不重复计数。\n\n实际登记：`'+str((E/'executed-junit-tests.json').relative_to(ROOT))+'`；逐验收点映射：`'+str((E/'requirements-coverage.json').relative_to(ROOT))+'`。\n'+mdtable(['需求','原文验收点','本轮判断'],[(c['us'],c['criterion'],c['result']) for c in catalog_checks])]
H+=['<section id="issues"><h2>问题、边界与讲解提醒</h2>'+table(['事项','复核说明'],[(esc(a),esc(b)) for a,b in issues])+'</section>']
M+=['### 284项实际执行登记\n'+mdtable(['测试类','方法','结果','秒'],[(t['class'].split('.')[-1],t['name'],t['result'],t['seconds']) for t in suite])]
M+=['## 问题、边界与讲解提醒\n'+mdtable(['事项','复核说明'],issues)]
protection=readjson(E/'protection-cleanup.json');qa=readjson(E/'report-qa.json');exports=readjson(E/'export-structure-check.json')
assert not protection['changedProtectedFiles'] and qa['passed']==9
quality='交付检查通过：298个受保护文件指纹及HEAD保持不变，原始资料和业务源码未改写；专用测试端口无监听，本轮临时容器已清理。报告9组离线检查通过，0外部HTTP请求、0页面脚本错误；390px宽度无整页横向溢出。实际下载的XLSX包含2个工作表，CSV/XLSX均包含同筛选下3门合成课程。'
H+=['<section id="delivery-qa"><h2>文件与交付核验</h2><p>'+quality+'</p><p>验收结论：21条需求具备本轮核心软件验证证据；BOPPPS展示口径、现场设备能力、真实业务资料与自然人正式签收按前述边界复核。本任务未修复业务代码、未部署或提交推送。</p>'+filelink(str((E/'protection-cleanup.json').relative_to(ROOT)),'原件源码保护与清理记录')+' · '+filelink(str((E/'report-qa.json').relative_to(ROOT)),'离线报告QA')+' · '+filelink(str((E/'export-structure-check.json').relative_to(ROOT)),'实际导出文件核验')+'</section>']
M+=['## 文件与交付核验\n\n'+quality+'\n\n21条需求具备本轮核心软件验证证据；BOPPPS展示口径、现场设备能力、真实业务资料和自然人正式签收按前述边界复核。未修复业务代码、未部署或提交推送。\n']
source_path=next(p for p in B['files'] if p.endswith('第二组_项目规划方案.docx'))
sources=[('实验一正式项目规划',source_path,'表5/6/7用户故事及表16完成定义；只读核对，原件SHA256 '+B['files'][source_path]),('当前实现',str(ROOT),'exp4 / '+B['head']+'；精确源码行号见每项需求'),('本轮隔离证据',str(E),'本轮构建、JUnit XML、三个Sprint及补充浏览器结果；历史记录仅参考'),('规范与长期口径',str(ROOT/'AGENTS.md'),'目录规则；memory-bank中已确认的合成培养方案、授权与正式签收边界'),('技能','officecli-docx；ui-ux-pro-max','原件只读解析；离线图文报告可读性、导航和对比度。minimax-docx当前未安装。')]
H+=['<section id="sources"><h2>来源索引与复测入口</h2><p>引用日期均为2026-10-16。未使用外部联网业务资料。源码链接用于本工作区定位；离线HTML图文自包含，但源码和日志链接在其他电脑需要相同目录或随项目查阅。</p>'+table(['来源','文件或位置','用途'],[(esc(a),esc(b),esc(c)) for a,b,c in sources])+'<h3>复测入口</h3><pre><code>'+esc('npm.cmd --prefix frontend test\nnpm.cmd --prefix frontend run build\nbackend/.tools/apache-maven-3.9.6/bin/mvn.cmd -f backend/pom.xml -o package\nscripts/run-sprint2-acceptance.ps1 -EvidenceDir <新的空证据目录> -BackendMode Docker\n')+'</code></pre><p>全量与截图采集脚本在本轮过程目录保存；复测必须先选新的证据目录、检查专用端口并构建当前JAR。不要直接重复执行会覆盖本轮证据的采集副本。</p></section>']
M+=['## 来源索引与复测入口\n\n引用日期2026-10-16。未使用外部联网业务资料。源码定位链接依赖当前工作区；HTML正文与图片可离线独立阅读。\n'+mdtable(['来源','文件或位置','用途'],sources)+'\n复测：`npm.cmd --prefix frontend test`；`npm.cmd --prefix frontend run build`；本轮隔离脚本及执行日志见过程证据目录。必须使用新的证据目录，避免覆盖本轮结果。\n']
CSS='''*{box-sizing:border-box}html{scroll-behavior:smooth;scroll-padding-top:24px}body{overflow-wrap:anywhere;margin:0;background:#f3f5f9;color:#243047;font:16px/1.8 "Microsoft YaHei","Noto Sans CJK SC",sans-serif}a{color:#4338ca;text-underline-offset:4px;overflow-wrap:anywhere}a:hover{color:#1e1b4b}button,input,select{font:inherit}button,a,input,select{outline-offset:4px}button:focus-visible,a:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid #6366f1}aside{position:fixed;inset:0 auto 0 0;width:274px;padding:28px 22px;background:#fff;border-right:1px solid #dce1ed;overflow:auto;z-index:5}aside h2{font-size:20px;color:#312e81;margin:0 0 4px}.subtitle{font-size:13px;color:#52617a}aside label{display:block;font-size:14px;margin-top:18px}input,select{width:100%;border:1px solid #94a3b8;background:white;border-radius:8px;padding:9px;margin-top:5px;color:#172554}aside nav{margin-top:22px}aside nav a{display:block;padding:6px 8px;margin:2px 0;text-decoration:none;border-radius:5px;font-size:14px}aside nav a:hover{background:#eef2ff}aside .us-link{font-size:13px;padding-left:15px}main{margin-left:274px;padding:38px clamp(20px,4vw,70px);max-width:1660px}section,.story{background:#fff;border:1px solid #dce1ed;border-radius:16px;padding:clamp(22px,3vw,42px);margin-bottom:28px;scroll-margin-top:24px}.story{border-top:4px solid #6366f1}h1,h2,h3,h4{color:#172554;line-height:1.4;letter-spacing:-.02em}h1{font-size:clamp(32px,4vw,56px);margin:14px 0 22px}h2{font-size:28px;margin:0 0 22px}h3{font-size:25px;margin:0 0 14px}h4{font-size:19px;margin:30px 0 10px}.eyebrow{color:#4338ca;font-size:13px;font-weight:bold;letter-spacing:.08em;margin:0 0 8px}.lead{max-width:900px;font-size:19px;color:#475569}.meta,.small{color:#52617a;font-size:13px}.kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:30px 0}.kpis div{background:#f5f6ff;border:1px solid #dce0ff;border-radius:12px;padding:18px}.kpis b{display:block;font-size:34px;color:#4338ca}.kpis span{font-size:13px}.note{background:#eef2ff;border-left:4px solid #6366f1;padding:16px 20px;border-radius:5px}.story-head{display:flex;justify-content:space-between;align-items:flex-start;gap:15px}.role{border:1px solid #c7d2fe;background:#eef2ff;padding:4px 12px;border-radius:16px;color:#3730a3;font-size:13px;white-space:nowrap}.status{color:#166534;font-size:14px}.pass{color:#166534;font-size:13px;white-space:nowrap}blockquote{margin:0;padding:15px 20px;background:#f8fafc;border-left:3px solid #94a3b8}.acceptance{font-weight:500}.table-scroll{overflow:auto;margin:18px 0}table{border-collapse:collapse;width:100%;font-size:14px;text-align:left}th{background:#f1f5f9;color:#172554;font-weight:600;white-space:nowrap}th,td{padding:12px 13px;border-bottom:1px solid #dce1ed;vertical-align:top}td{min-width:95px;overflow-wrap:anywhere}td:first-child{min-width:100px}tr:hover td{background:#fbfcff}.source-link{font:12px/1.7 Consolas,monospace;overflow-wrap:anywhere}.checks li{margin:10px 0}.checks .source-link{font-size:13px}.diagram,.screenshot{margin:24px 0}.zoom{display:block;border:1px solid #dce1ed;padding:0;background:#f8fafc;width:100%;border-radius:12px;overflow:hidden;cursor:zoom-in}.zoom img{display:block;width:100%;height:auto}.screenshot .zoom img{max-height:780px;object-fit:contain}.zoom:hover{border-color:#6366f1}figcaption{font-size:13px;color:#52617a;margin-top:7px}code{font-family:Consolas,monospace;font-size:13px;overflow-wrap:anywhere}pre{overflow:auto;background:#f1f5f9;border-radius:10px;padding:20px;white-space:pre-wrap}details{border:1px solid #dce1ed;border-radius:8px;padding:14px;margin:18px 0}summary{cursor:pointer;font-weight:600;color:#3730a3}ol,ul{padding-left:25px}li{margin:8px 0}[hidden]{display:none!important}#match-count{font-size:13px;color:#475569;margin:12px 0}#empty{background:#fff;padding:24px;border-radius:12px}dialog{max-width:96vw;max-height:96vh;border:0;border-radius:12px;padding:14px;background:#fff}dialog::backdrop{background:#0f172acc}dialog img{display:block;max-width:90vw;max-height:84vh;object-fit:contain}dialog button{display:block;margin:0 0 8px auto;border:1px solid #94a3b8;border-radius:7px;padding:5px 15px;background:white;cursor:pointer}footer{color:#52617a;font-size:13px;margin:25px 0}@media(max-width:1000px){aside{position:relative;width:auto;border-right:0;border-bottom:1px solid #dce1ed}aside nav{max-height:190px;overflow:auto}main{margin:0;padding:20px}aside .us-link{display:inline-block}h1 br{display:none}.kpis{grid-template-columns:repeat(2,1fr)}}@media(max-width:540px){body{font-size:16px}.story-head{display:block}.role{display:inline-block;margin:5px 0}.kpis{gap:8px}.kpis div{padding:12px}section,.story{padding:20px}h2{font-size:25px}.diagram img{min-width:760px}.diagram .zoom{overflow:auto}td{font-size:14px}}@media print{aside,dialog{display:none}main{margin:0;padding:0}.story,section{border:0;break-before:auto;box-shadow:none}.story{break-before:page}.zoom{border:0}.screenshot .zoom img{max-height:none}details{display:block}a{color:#243047}h2,h3,h4{break-after:avoid}figure{break-inside:avoid}}'''
CSS+='#reset{min-height:44px;padding:7px 13px;border:1px solid #c7d2fe;border-radius:8px;background:#eef2ff;color:#3730a3;cursor:pointer}'
JS='''const q=document.getElementById('search'),role=document.getElementById('role'),stories=[...document.querySelectorAll('.story')],count=document.getElementById('match-count');function filter(){const s=q.value.trim().toLowerCase(),r=role.value;let n=0;for(const a of stories){const ok=(!s||a.innerText.toLowerCase().includes(s))&&(!r||a.dataset.role.includes(r));a.hidden=!ok;document.querySelector('a[href="#'+a.id+'"]').hidden=!ok;if(ok)n++}count.textContent='匹配 '+n+' / 21 条需求';document.getElementById('empty').hidden=n>0}q.addEventListener('input',filter);role.addEventListener('change',filter);document.getElementById('reset').addEventListener('click',()=>{q.value='';role.value='';filter()});for(const a of document.querySelectorAll('a[href^="#US-"]'))a.addEventListener('click',()=>{const target=document.querySelector(a.getAttribute('href'));if(target?.hidden){q.value='';role.value='';filter()}});const dlg=document.getElementById('viewer'),big=dlg.querySelector('img');document.querySelectorAll('.zoom').forEach(b=>b.addEventListener('click',()=>{big.src=b.querySelector('img').src;big.alt=b.querySelector('img').alt;dlg.showModal()}));dlg.querySelector('button').addEventListener('click',()=>dlg.close());dlg.addEventListener('click',e=>{if(e.target===dlg)dlg.close()});filter();'''
nav='<a href="#intro">版本与范围</a><a href="#overview">功能全景</a><a href="#example">完整业务示例</a>'+''.join(f'<a href="#route-{j}">0{j} {g}</a>'+''.join(f'<a class="us-link" href="#US-{n:02}">US-{n:02} {esc(byid[n]["title"])}</a>' for n in ids) for j,(g,ids,roles) in enumerate(groups,1))+'<a href="#tests">测试与验收覆盖</a><a href="#issues">问题与讲解提醒</a><a href="#delivery-qa">文件与交付核验</a><a href="#sources">来源与复测入口</a>'
page=f'<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="light"><title>实验一 → exp4｜21条需求实现路径与实测图解</title><style>{CSS}</style></head><body><aside aria-label="报告目录"><h2>需求 → 实现 → 验证</h2><p class="subtitle">爱教学 · 实验一至 exp4<br>答辩讲解 / 源码追踪 / 隔离实测</p><label for="search">检索需求、功能或接口</label><input id="search" type="search" placeholder="例如 US-14、模板、preview"><label for="role">按角色筛选功能</label><select id="role"><option value="">全部角色</option><option>主任</option><option>教师</option><option>督导</option><option>管理员</option></select><p id="match-count" aria-live="polite"></p><button id="reset" type="button">清除筛选</button><nav>{nav}</nav></aside><main><p id="empty" hidden>没有匹配需求，请修改关键词或清除筛选。</p>'+''.join(H)+'<footer>正式图文报告 v1 · 所有图与关键页面内嵌，无外部字体、脚本或图片依赖。</footer></main><dialog id="viewer" aria-label="图片放大查看"><button type="button">关闭</button><img alt="放大图片"></dialog><script>'+JS+'</script></body></html>'
(STAGE/(NAME+'.html')).write_text(page,encoding='utf-8');(STAGE/(NAME+'.md')).write_text('\n\n'.join(M),encoding='utf-8')
(E/'report-manifest.json').write_text(json.dumps({'stories':21,'criteria':len(catalog_checks),'backendTests':len(suite),'casesDesigned':sum(len(d['caseDesigns']) for d in D),'figures':len(list(IMG.glob('*.svg'))),'screenshots':len(list(IMG.glob('*.png'))),'sourceAnchors':sum(len(d['anchors']) for d in D),'outputs':[NAME+'.html',NAME+'.md',NAME+'-images'],'head':B['head']},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(readjson(E/'report-manifest.json'),ensure_ascii=False,indent=2))
