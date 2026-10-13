"""One-time, checked migration helpers. Evidence/backup precedes invocation."""
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[1]
def read(path):return (ROOT/path).read_text(encoding='utf-8-sig')
def write(path,text):(ROOT/path).write_text(text,encoding='utf-8')
def div_end(s,start):
    depth=0
    for token in re.finditer(r'<div\b[^>]*>|</div>',s[start:]):
        depth+= -1 if token.group().startswith('</') else 1
        if depth==0:return start+token.end(),start+token.start()
    raise ValueError('unbalanced div')
def modalize(s):
    pattern=r'<div\s+v-if="([^"]+)"\s+class="fixed inset-0[^\"]*"[^>]*>'
    for m in list(re.finditer(pattern,s))[::-1]:
        end,close=div_end(s,m.start());content=s[m.end():close].strip()
        if content.startswith('<div'):
            inner_open=re.match(r'<div\b[^>]*>',content);inner_end,inner_close=div_end(content,0)
            if not content[inner_end:].strip():content=content[inner_open.end():inner_close]
        expr=m.group(1); title_match=re.search(r'<h[23][^>]*>(.*?)</h[23]>',content,re.S)
        title=re.sub(r'<[^>]+>|\{\{.*?\}\}', '',title_match.group(1)).strip() if title_match else '操作详情'
        title=re.sub(r'\s+',' ',title).replace('"','') or '操作详情'
        close_expr='closePreview()' if 'Preview' in expr else expr+' = false'
        if expr=='editingResource':close_expr='editingResource = null'
        s=s[:m.start()]+f'<UiModal :open="!!({expr})" title="{title}" @close="{close_expr}">'+content+'</UiModal>'+s[end:]
    return s
def import_ui(s,extra=''):
    return s.replace('<script setup lang="ts">','<script setup lang="ts">\nimport FeatureSelector from \'../components/FeatureSelector.vue\'\nimport UiModal from \'../components/UiModal.vue\'\nimport { useNavigation, usePreference, useEditGuard, useFeedback, useDomEdits } from \'../ui/context\'\n'+extra,1)
def fn(s,name,new):
    match=re.search(r'const '+name+r' = async[^\n]*=> \{',s)
    if not match:raise ValueError(name)
    end=s.index('\n}\n',match.end())+2
    return s[:match.start()]+new+s[end:]
def common(s):
    s=s.replace('alert(', 'notify(').replace('!confirm(', '!await confirm(')
    # Technical story labels are tracked in source/evidence, not business labels.
    template,rest=s.split('<script setup',1)
    template=re.sub(r'\s*[（(]US-[\d/～~、-]+[）)]','',template)
    template=template.replace('保存并更新 MySQL','保存指标').replace('直接更新 MySQL student 表','更新学生班级')
    return template+'<script setup'+rest

def teacher():
    path='frontend/src/views/TeacherDeskView.vue';s=read(path)
    s=import_ui(s,"import { useRadar } from '../ui/useRadar'\nimport type { SaveResult } from '../ui/context'\n")
    s=s.replace('  <div class="space-y-6">','  <div class="space-y-6" ref="viewRoot">',1)
    s=s.replace('    <OfferingHistoryPanel />','')
    s=s.replace('v-model="currentTeacher" @change="onTeacherChange"',':value="currentTeacher" @change="requestTeacherChange($event)" data-filter')
    s=s.replace('v-model="selectedOfferingId" @change="onOfferingSelectChange"',':value="selectedOfferingId" @change="requestOfferingChange($event)" data-filter aria-label="当前主讲课程"')
    s=s.replace('    <!-- 主体：左右分栏 -->','    <FeatureSelector v-model="feature" :items="features" id="teacher" label="教师工作功能" />\n    <section v-if="feature===\'history\'" role="tabpanel" id="teacher-panel-history" aria-labelledby="teacher-tab-history"><OfferingHistoryPanel /></section>\n    <!-- 主体：左右分栏 -->')
    s=s.replace('<div class="lg:col-span-7 space-y-6">','<div v-show="feature===\'materials\'||feature===\'shared\'" class="lg:col-span-12 space-y-6" role="tabpanel" :id="`teacher-panel-${feature}`" :aria-labelledby="`teacher-tab-${feature}`">',1)
    for comment,condition in [('<!-- 课件教案上传管理','materials'),('<!-- US-10：跨课程','shared'),('<!-- 在线课程简介','materials')]:
        start=s.index(comment);match=re.search(r'<div class="pro-card p-6[^\"]*">',s[start:]);idx=start+match.start()
        s=s[:idx]+s[idx:].replace('<div class=',f'<div v-show="feature===\'{condition}\'" class=',1)
    s=s.replace('<div class="lg:col-span-5 space-y-6">','<div v-if="feature===\'feedback\'" class="lg:col-span-12 space-y-6" role="tabpanel" id="teacher-panel-feedback" aria-labelledby="teacher-tab-feedback">',1)
    start=s.index('<!-- 底部整宽卡片');idx=s.index('<div class="pro-card p-6',start)
    s=s[:idx]+s[idx:].replace('<div class=', '<div v-show="feature===\'indicators\'" role="tabpanel" id="teacher-panel-indicators" aria-labelledby="teacher-tab-indicators" class=',1)
    # Keep DOM fallback away from explicit content editor and course/filter controls.
    s=s.replace('v-model="contentForm.', 'data-edit-managed v-model="contentForm.')
    s=s.replace('@click="loadCourseContent"','@click="refreshCourseContent"')
    s=s.replace('@click="triggerResourceFileInput"','role="button" tabindex="0" aria-label="选择课件文件" @keydown.enter.prevent="triggerResourceFileInput" @keydown.space.prevent="triggerResourceFileInput" @click="triggerResourceFileInput"')
    s=s.replace("const selectedOfferingId = ref<number | null>(null)","const selectedOfferingId = usePreference<number | null>('teacher','offering',null)")
    s=s.replace('  selectedOfferingId.value = null\n  await loadCourse()', '  await loadCourse()')
    s=s.replace('let radarChart: echarts.ECharts | null = null','let radarChart: echarts.ECharts | null = null\nconst managedRadar=useRadar(radarChartRef, chart=>{radarChart=chart;renderRadarChart()})')
    s=s.replace('    renderRadarChart()','    managedRadar.render()',1)
    s=s.replace("  if (!radarChart) {\n    radarChart = echarts.init(radarChartRef.value)\n  }","  if (!radarChart) return")
    s=s.replace("  radarChart.setOption(option)","  radarChart.setOption({...option,animation:false},true)")
    s=s.replace("onUnmounted(() => { closePreview(); radarChart?.dispose(); radarChart = null })","onUnmounted(() => { disposed=true; contentLoadSequence++; closePreview(); radarChart = null })")
    s=s.replace('<div ref="radarChartRef"','<p class="text-sm text-slate-600">归一化维度均分换算至25分刻度；不等同于总分。</p><p v-if="!radarData || [radarData.attitudeScore,radarData.contentScore,radarData.methodScore,radarData.effectScore].some(v=>v==null)" class="ui-empty">缺少有效维度，请查看可用维度数据；不补零。</p><div ref="radarChartRef"')
    s=s.replace("{{ radarData?.evaluationCount ? radarData.overallScore + ' 分' : '暂无评价' }}","{{ radarData?.overallScore != null ? radarData.overallScore + ' 分' : '暂无可比总分' }}")
    s=fn(s,'loadCourseContent',r'''const loadCourseContent = async (adopt = false) => {
  const requestSequence=++contentLoadSequence,courseId=currentCourse.value?.id
  if(!courseId)return
  try {
    const [draft,published]=await Promise.all([courseContentApi.getDraft(courseId),courseContentApi.getPublished(courseId)])
    if(disposed || requestSequence!==contentLoadSequence || currentCourse.value?.id!==courseId)return
    const next={description:draft.description||'',assessmentMethod:draft.assessmentMethod||'',objectives:draft.objectives||'',lockVersion:draft.lockVersion??0,publishVersion:draft.publishVersion??0}
    if(contentGuard.dirty()&&!adopt){latestContentSnapshot.value={draft,published,form:next};notify('已取得最新草稿，本地编辑完整保留');return}
    draftRevision.value=draft;publishedRevision.value=published;contentForm.value=next;contentConflictMsg.value='';latestContentSnapshot.value=null;contentGuard.reset()
  } catch(error:any){if(!disposed&&requestSequence===contentLoadSequence)contentConflictMsg.value=error.response?.data?.message||error.message||'草稿加载失败'}
}''')
    s=fn(s,'handleSaveDraft',r'''const handleSaveDraft = async ():Promise<SaveResult> => {
  const courseId=currentCourse.value?.id
  if(isSavingDraft.value||isPublishingContent.value||!courseId||!draftRevision.value||draftRevision.value.courseId!==courseId||contentConflictMsg.value)return {status:'invalid'}
  const submitted=contentGuard.capture();isSavingDraft.value=true
  try{
    const updated=await courseContentApi.saveDraft(courseId,{...contentForm.value,draftId:draftRevision.value.id})
    if(disposed||currentCourse.value?.id!==courseId)return {status:'stale'}
    draftRevision.value=updated;contentForm.value.lockVersion=updated.lockVersion??updated.version??0;contentGuard.saved(submitted);notify('课程草稿已保存');return {status:'success'}
  }catch(err:any){const conflict=err.response?.status===409||err.status===409;contentConflictMsg.value=err.response?.data?.message||err.message||'草稿保存失败';notify(contentConflictMsg.value);if(conflict)await loadCourseContent();return {status:conflict?'conflict':'failed'}}
  finally{isSavingDraft.value=false}
}''')
    # Publishing is explicit, never invoked by leave protection.
    s=s.replace("    publishedRevision.value = published\n", "    contentGuard.saved(submitted)\n    publishedRevision.value = published\n")
    start=s.index('const handlePublishContent');idx=s.index('  isPublishingContent.value = true',start)
    s=s[:idx]+"  const submitted=contentGuard.capture()\n"+s[idx:]
    s=s.replace("if (currentCourse.value?.id !== courseId) return","if (disposed || currentCourse.value?.id !== courseId) return")
    # Declarations are below all form refs; callbacks run only after setup.
    extra=r'''
const nav=useNavigation(),{notify,confirm}=useFeedback(),feature=usePreference('teacher','feature','materials'),viewRoot=ref<HTMLElement>()
const fallbackEdits=useDomEdits(viewRoot,'教师工作台表单')
const features=[{key:'materials',label:'课程资料',description:'编辑简介、考核与目标，维护课件',iconComp:BookOpen},{key:'shared',label:'共享备课',description:'检索教研室可访问的备课资源',iconComp:Folder},{key:'indicators',label:'指标矩阵',description:'查看大纲版本和培养方案支撑',iconComp:Target},{key:'feedback',label:'教学反馈',description:'查看维度评分与匿名教学建议',iconComp:Target},{key:'history',label:'开课历史',description:'追溯历史班次与授课人次',iconComp:BookOpen}]
let disposed=false
const latestContentSnapshot=ref<any>(null)
const contentGuard=useEditGuard('课程资料',()=>({description:contentForm.value.description,assessmentMethod:contentForm.value.assessmentMethod,objectives:contentForm.value.objectives}),value=>{Object.assign(contentForm.value,value)},()=>true,handleSaveDraft)
const uploadGuard=useEditGuard('课件上传',()=>({form:uploadForm.value,file:selectedResourceFile.value?.name||''}),value=>{uploadForm.value=value.form;clearSelectedResourceFile()},()=>showUploadModal.value)
const indicatorGuard=useEditGuard('指标点编辑',()=>indicatorForm.value,value=>{indicatorForm.value=value},()=>showIndicatorModal.value)
const resourceGuard=useEditGuard('资源标签与共享',()=>editingResource.value,value=>{editingResource.value=value},()=>!!editingResource.value)
async function requestOfferingChange(event:Event){const select=event.target as HTMLSelectElement,next=Number(select.value);select.value=String(selectedOfferingId.value||'');await nav.request(async()=>{selectedOfferingId.value=next;await onOfferingSelectChange();fallbackEdits.reset()})}
async function requestTeacherChange(event:Event){const select=event.target as HTMLSelectElement,next=select.value;select.value=currentTeacher.value;await nav.request(async()=>{currentTeacher.value=next;selectedOfferingId.value=null;await onTeacherChange();fallbackEdits.reset()})}
async function refreshCourseContent(){await loadCourseContent()}
async function adoptLatestContent(){await nav.request(async()=>{if(latestContentSnapshot.value){const {draft,published,form}=latestContentSnapshot.value;draftRevision.value=draft;publishedRevision.value=published;contentForm.value=form;latestContentSnapshot.value=null;contentConflictMsg.value='';contentGuard.reset()}else await loadCourseContent(true)})}
'''
    s=s.replace('onMounted(async () => {',extra+'\nonMounted(async () => {',1)
    s=s.replace('    <!-- 主体：左右分栏 -->','    <div v-if="latestContentSnapshot" class="ui-alert"><p>最新服务端草稿已取得，本地输入仍保留。</p><button class="ui-button" @click="adoptLatestContent">放弃本地修改并采用最新草稿</button></div>\n    <!-- 主体：左右分栏 -->')
    s=common(modalize(s))
    # All explicit modal close buttons use the same guard as Esc.
    for expr in ['showUploadModal = false','showIndicatorModal = false','editingResource = null']:
        s=s.replace('@click="'+expr+'"','@click="nav.request(()=>{'+expr+'})"')
    write(path,s)

def resultify(s,name,guard,close):
    match=re.search(r'const '+name+r' = async[^\n]*=> \{',s)
    if not match:raise ValueError(name)
    end=s.index('\n}\n',match.end())+2;body=s[match.start():end]
    body=re.sub(r'\breturn\s*(?=\n|\r\n|})',"return {status:'invalid'}",body)
    body=body.replace('  try {',f'  const submitted={guard}.capture()\n  try {{',1)
    body=body.replace(close,f"{guard}.saved(submitted)\n    if({guard}.dirty())return {{status:'success'}}\n    {close}\n    return {{status:'success'}}",1)
    # Refresh operations following the early success are moved before closing.
    body=body.replace("return {status:'success'}\n    loadCourses()","loadCourses()\n    return {status:'success'}").replace("return {status:'success'}\n    await loadSupervisors()","await loadSupervisors()\n    return {status:'success'}")
    catch=re.search(r'\} catch \((\w+): any\) \{',body)
    if catch:
        e=catch.group(1);last=body.rfind('\n  }');body=body[:last]+f"\n    return {{status:{e}.response?.status===409?'conflict':'failed'}}"+body[last:]
    return s[:match.start()]+body+s[end:]

def roles():
    for role,file in [('director','DirectorDeskView'),('supervisor','SupervisorDeskView')]:
        path=f'frontend/src/views/{file}.vue';s=import_ui(read(path));s=s.replace('<div class="space-y-6">','<div class="space-y-6" ref="viewRoot">',1)
        start=s.index('    <!-- 子导航') if role=='director' else s.index('        v-for="tab in tabs"')
        if role=='supervisor':start=s.rfind('<div',0,start)
        else:start=s.index('<div',start)
        end,_=div_end(s,start)
        s=s[:start]+f'<FeatureSelector v-model="activeTab" :items="tabs" id="{role}" label="工作功能" />'+s[end:]
        s=s.replace("const activeTab = ref('courses')","const activeTab = usePreference('director','feature','courses')").replace("const activeTab = ref('timetable')","const activeTab = usePreference('supervisor','feature','timetable')")
        entries=({'courses':('课程档案','维护课程基础信息与导入导出'),'schedules':('开课排课','安排班次、教室与授课时段'),'indicators':('指标矩阵','维护培养方案和大纲支撑'),'supervisors':('督导授权','维护专家账号与专业范围'),'reviews':('评价审核','审核督导评价与延迟反馈')} if role=='director' else {'timetable':('听课日程','查看课表，安排随堂听课'),'search':('课程检索','组合筛选授权课程与班次'),'alerts':('质量预警','查看需人工复核的教学提醒')})
        for key,(label,desc) in entries.items():
            s=re.sub(r"(\{ key: '"+key+r"', label: )'[^']*'",r"\1'"+label+"', description: '"+desc+"'",s)
            s=s.replace(f'v-if="activeTab === \'{key}\'"',f'v-if="activeTab === \'{key}\'" role="tabpanel" id="{role}-panel-{key}" aria-labelledby="{role}-tab-{key}"')
        s=common(modalize(s))
        # Filters are preferences, never unfinished edits.
        for prefix in (['courseFilter','coursePageSize','selectedCourseIdForIndicator','selectedSyllabusId'] if role=='director' else ['scheduleFilter','filterParams','timetableMode']):
            s=re.sub(r'(v-model(?:\.number)?="'+prefix+r'[^\"]*")',r'data-filter \1',s)
        s=s.replace('onMounted(', 'onMounted(',1)
        extra="\nconst nav=useNavigation(),{notify,confirm}=useFeedback(),viewRoot=ref<HTMLElement>()\nconst fallbackEdits=useDomEdits(viewRoot,'工作台表单')\n"
        if role=='director':
            extra+="""const courseGuard=useEditGuard('课程档案',()=>currentCourseForm.value,v=>{currentCourseForm.value=v},()=>showCourseModal.value,handleSaveCourse)
const indicatorGuard=useEditGuard('指标点',()=>indicatorForm.value,v=>{indicatorForm.value=v},()=>showIndicatorModal.value)
const supervisorGuard=useEditGuard('督导账号',()=>createSupervisorForm.value,v=>{createSupervisorForm.value=v},()=>showCreateSupervisorModal.value,handleCreateSupervisor)
const majorsGuard=useEditGuard('督导授权',()=>editSupervisorSelectedMajors.value,v=>{editSupervisorSelectedMajors.value=v},()=>showEditMajorsModal.value,handleUpdateSupervisorMajors)
"""
            s=resultify(s,'handleSaveCourse','courseGuard','showCourseModal.value = false')
            s=resultify(s,'handleCreateSupervisor','supervisorGuard','showCreateSupervisorModal.value = false')
            s=resultify(s,'handleUpdateSupervisorMajors','majorsGuard','showEditMajorsModal.value = false')
            s=s.replace("if (confirm('确认删除该课程档案？'))", "if (await confirm('确认删除该课程档案？'))")
            s=s.replace('const courseFilter = ref(',"const courseFilter = usePreference('director','courseFilter',")
            # Dedicated error state for actual list failure.
            s=s.replace('const allMajors = ref',"const courseLoadError=ref('')\nconst allMajors = ref",1)
            idx=s.index('const loadCourses');tail=s[idx:];tail=tail.replace('  try {',"  courseLoadError.value=''\n  try {",1).replace('console.error(',"courseLoadError.value='课程加载失败，请重试'\n    console.error(",1);s=s[:idx]+tail
            s=s.replace('<FeatureSelector','<p v-if="courseLoadError" role="alert" class="ui-alert">{{ courseLoadError }} <button @click="loadCourses">重试</button></p>\n    <FeatureSelector',1)
        else:
            extra+="""const evaluationGuard=useEditGuard('督导评价草稿',()=>({form:evalForm.value,template:selectedTemplate.value,tags:problemTags.value}),v=>{evalForm.value=v.form;selectedTemplate.value=v.template;problemTags.value=v.tags},()=>showEvaluateModal.value,()=>handleSaveEvaluation(true))
"""
            s=resultify(s,'handleSaveEvaluation','evaluationGuard','showEvaluateModal.value = false')
            s=s.replace("activeTab = 'search'; filterParams.keyword = alert.courseCode; loadOfferings()", "nav.request(()=>{activeTab = 'search'; filterParams.keyword = alert.courseCode; loadOfferings()})")
            s=s.replace('const scheduleFilter = ref(',"const scheduleFilter = usePreference('supervisor','scheduleFilter',")
        s=s.replace('onMounted(',extra+'\nonMounted(',1)
        for expr in re.findall(r'@click="(show\w+Modal = false)"',s):s=s.replace('@click="'+expr+'"','@click="nav.request(()=>{'+expr+'})"')
        write(path,s)

def support():
    # Login retains existing authentication/demo identities without persisting preferences.
    path='frontend/src/views/LoginView.vue';s=read(path)
    s=s.replace('bg-gradient-to-br from-slate-950 via-slate-900 to-indigo-950','login-page bg-slate-50')
    s=s.replace('bg-slate-900/80','bg-white').replace('border-slate-800/80','border-slate-200').replace('text-indigo-200/70','text-slate-600').replace('text-indigo-200/60','text-slate-600').replace('text-indigo-300','text-indigo-700')
    s=s.replace('tracking-tight text-white','tracking-tight text-slate-900').replace('font-bold text-white tracking-wide','font-bold text-slate-900 tracking-wide')
    s=s.replace("const showQuickLogin = ref(localStorage.getItem('classroom_show_quick_login') !== 'false')","const showQuickLogin = ref(false)")
    s=s.replace("  localStorage.setItem('classroom_show_quick_login', String(showQuickLogin.value))",'')
    s=s.replace('JWT + Spring Security 敏捷鉴权体系','教师 · 主任 · 督导').replace('JWT + Security','统一登录')
    s=re.sub(r'<p class="font-mono text-\[10px\]">Spring Boot.*?</p>','<details><summary>系统信息</summary><p>Spring Boot · Vue · MySQL · Redis</p></details>',s)
    write(path,s)
    path='frontend/src/views/MicroTeachingView.vue';s=read(path).replace("import {computed,ref,onMounted,onUnmounted}","import {computed,ref,onMounted,onUnmounted}")
    s=s.replace('<script setup lang="ts">',"<script setup lang=\"ts\">\nimport {useNavigation,useEditGuard,useFeedback,usePreference} from '../ui/context'\nimport {formatTime,parseTime} from '../ui/time'\n",1)
    s=s.replace('v-model="courseId" @change="load"',':value="courseId" @change="changeCourse" data-filter')
    s=s.replace('起点（毫秒）<input v-model.number="startMs" type="number" min="0" step="1"', '起点（分:秒.毫秒）<input v-model="startText" placeholder="0:00.000"')
    s=s.replace('终点（毫秒）<input v-model.number="endMs" type="number" min="1" step="1"', '终点（分:秒.毫秒）<input v-model="endText" placeholder="0:01.000"')
    s=s.replace('<label class="sm:col-span-2">分析元数据JSON','<details class="sm:col-span-2"><summary>高级分析选项</summary><label>分析元数据JSON').replace('v-model="analysis" rows="4"></textarea></label>','v-model="analysis" rows="4"></textarea></label></details>')
    extra="""
const nav=useNavigation(),{notify,confirm}=useFeedback()
const uploadGuard=useEditGuard('视频导入',()=>({...source.value,file:file.value?.name||''}),v=>{const {file:ignored,...rest}=v;source.value=rest as any;file.value=undefined},()=>canWrite.value)
const sliceGuard=useEditGuard('微格片段',()=>({assetId:assetId.value,title:title.value,stage:stage.value,startMs:startMs.value,endMs:endMs.value,analysis:analysis.value}),v=>{assetId.value=v.assetId;title.value=v.title;stage.value=v.stage;startMs.value=v.startMs;endMs.value=v.endMs;analysis.value=v.analysis},()=>canWrite.value)
const startText=computed({get:()=>formatTime(startMs.value),set:v=>{startMs.value=parseTime(v)}}),endText=computed({get:()=>formatTime(endMs.value),set:v=>{endMs.value=parseTime(v)}})
async function changeCourse(e:Event){const select=e.target as HTMLSelectElement,next=Number(select.value);select.value=String(courseId.value||'');await nav.request(async()=>{courseId.value=next;await load();reset();sliceGuard.reset();uploadGuard.reset()})}
"""
    s=s.replace('onMounted(',extra+'\nonMounted(',1)
    s=s.replace('const upload=()=>run(async()=>{','const upload=()=>{const submitted=uploadGuard.capture();return run(async()=>{',1).replace("message.value='资产已导入，时长来自服务端探测'})","uploadGuard.saved(submitted);sliceGuard.reset();message.value='资产已导入，时长来自服务端探测'})}")
    s=s.replace('await load();reset();message.value=\'片段保存成功\'','await load();reset();sliceGuard.reset();message.value=\'片段保存成功\'')
    s=s.replace("if(!confirm(","if(!await confirm(")
    s=s.replace('function edit(s:any){','function applyEdit(s:any){').replace("analysis.value=s.analysisJson||''}","analysis.value=s.analysisJson||'';sliceGuard.reset()}\nfunction edit(s:any){return nav.request(()=>applyEdit(s))}")
    s=s.replace('@click="reset"','@click="nav.request(()=>{reset();sliceGuard.reset()})"')
    s=common(s);write(path,s)
    path='frontend/src/components/ResourceVersionsPanel.vue';s=read(path)
    s=s.replace('<script setup lang="ts">',"<script setup lang=\"ts\">\nimport {useNavigation,useEditGuard} from '../ui/context'\n",1)
    s=s.replace('v-model="selected" @change="history"',':value="selected" @change="changeResource"')
    extra="""
const nav=useNavigation()
const fileGuard=useEditGuard('资源新版上传',()=>({name:file.value?.name||'',size:file.value?.size||0}),()=>{file.value=undefined})
async function changeResource(e:Event){const select=e.target as HTMLSelectElement,next=select.value?Number(select.value):'';select.value=String(selected.value);await nav.request(async()=>{selected.value=next;await history()})}
"""
    s=s.replace('onMounted(',extra+'\nonMounted(',1)
    s=s.replace("await load();message.value='新版本已保存，历史文件保留'","file.value=undefined;fileGuard.reset();await load();message.value='新版本已保存，历史文件保留'")
    s=s.replace('@click="load"','@click="nav.request(load)"').replace('资源历史版本（US-11）','资源历史版本');s=s.replace('<table>','<div class="ui-table-scroll"><table>').replace('</table>','</table></div>');write(path,s)
    path='frontend/src/components/OfferingScheduleBoard.vue';s=read(path)
    s=s.replace('<script setup lang="ts">',"<script setup lang=\"ts\">\nimport UiModal from './UiModal.vue'\nimport {useNavigation,useEditGuard,useFeedback} from '../ui/context'\nimport type {SaveResult} from '../ui/context'\n",1)
    s=modalize(s).replace('@close="modal = false"',"@close=\"modal = ''\"")
    s=s.replace('        role="dialog"\n        aria-modal="true"\n','')
    s=s.replace("async function save() {", "async function save():Promise<SaveResult> {")
    s=s.replace('  if (busy.value) return\n  busy.value = true','  if (busy.value) return {status:\'invalid\'}\n  const submitted=scheduleGuard.capture()\n  busy.value = true',1)
    s=s.replace("    modal.value = ''\n    await refresh()", "    scheduleGuard.saved(submitted)\n    if(scheduleGuard.dirty())return {status:'success'}\n    modal.value = ''\n    await refresh()\n    return {status:'success'}",1)
    start=s.index('async function save()') if 'async function save()' in s else s.index('async function save():');end=s.index('async function archive',start);part=s[start:end].replace('    fail(e)\n','    fail(e)\n    return {status:(e as any).response?.status===409?\'conflict\':\'failed\'}\n');s=s[:start]+part+s[end:]
    s=s.replace("if (!confirm(","if (!await confirm(")
    s=s.replace('@click="modal = \'\'"', '@click="nav.request(()=>{modal = \'\'})"')
    extra="""
const nav=useNavigation(),{notify,confirm}=useFeedback()
const scheduleGuard=useEditGuard('班次与排课',()=>({offering:offeringForm.value,schedule:scheduleForm.value}),v=>{offeringForm.value=v.offering;scheduleForm.value=v.schedule},()=>!!modal.value,save)
"""
    s=s.replace('onMounted(',extra+'\nonMounted(',1);write(path,common(s))
    path='frontend/src/views/StudentManageView.vue';s=import_ui(read(path));s=s.replace('<div class="space-y-6">','<div class="space-y-6" ref="viewRoot">',1);s=modalize(s)
    s=s.replace('@close="showRegisterModal = false"','@close="closeRegisterModal()"').replace('@close="showSearchModal = false"','@close="closeSearchModal()"').replace('@close="showAssignModal && targetStudent = false"','@close="showAssignModal = false"')
    extra="""
const nav=useNavigation(),{notify,confirm}=useFeedback(),viewRoot=ref<HTMLElement>()
useDomEdits(viewRoot,'学生档案表单')
const registerGuard=useEditGuard('学生登记',()=>registerForm.value,v=>{registerForm.value=v},()=>showRegisterModal.value)
const assignGuard=useEditGuard('学生班级',()=>targetClassName.value,v=>{targetClassName.value=v},()=>showAssignModal.value)
"""
    s=s.replace('onMounted(',extra+'\nonMounted(',1)
    s=s.replace('@click="closeRegisterModal"','@click="nav.request(closeRegisterModal)"').replace('@click="closeSearchModal"','@click="nav.request(closeSearchModal)"').replace('@click="showAssignModal = false"','@click="nav.request(()=>{showAssignModal = false})"')
    s=common(s);write(path,s)

if __name__=='__main__':
    import sys
    if len(sys.argv)>1 and sys.argv[1]=='roles':roles()
    elif len(sys.argv)>1 and sys.argv[1]=='support':support()
    else:teacher()
