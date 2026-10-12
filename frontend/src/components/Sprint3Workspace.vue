<template>
  <section class="space-y-5">
    <div class="pro-card p-6"><h2 class="text-xl font-bold">统计与反馈 · Sprint 3</h2>
      <p class="text-sm text-slate-600 mt-2">{{ role === 'ADMIN' ? '管理已发布模板和授权微格资产。' : '统计以课程权限和评价可见状态为依据；预警需人工复核。' }}</p>
    </div>
    <p v-if="error" role="alert" class="rounded-xl bg-rose-50 p-4 text-rose-800">{{ error }} <button @click="load" class="underline">重试</button></p>
    <p v-if="message" role="status" class="rounded-xl bg-emerald-50 p-3 text-emerald-800">{{ message }}</p>
    <p v-if="busy" role="status">正在处理…</p>
    <template v-if="role === 'ADMIN'">
      <div class="pro-card p-6 space-y-4">
        <h3 class="font-bold">四维模板管理（US-20）</h3>
        <form @submit.prevent="saveTemplate" class="grid sm:grid-cols-2 gap-4">
          <label>模板名称<input v-model.trim="draft.name" required maxlength="128" /></label>
          <label>适用场景<input v-model.trim="draft.scenario" required maxlength="128" /></label>
          <label class="sm:col-span-2">四维说明<textarea v-model.trim="draft.descriptions" required maxlength="2000"></textarea></label>
          <label v-for="axis in axes" :key="axis.key">{{ axis.label }}权重<input v-model.number="draft[axis.key]" type="number" min="0" max="100" step=".001" required /></label>
          <p>权重合计：{{ axes.reduce((sum, a) => sum + Number(draft[a.key]), 0) }}%，必须为100%。</p>
          <button :disabled="busy" class="primary">{{ draft.id ? '保存草稿修改' : '新建草稿' }}</button>
        </form>
        <div v-for="item in drafts" :key="item.id" class="border rounded-xl p-3 flex flex-wrap justify-between gap-3">
          <span>{{ item.name }} · {{ item.scenario }} · 草稿版本 {{ item.revision }} · {{ item.publishedId ? '已发布（不可修改）' : '未发布' }}</span>
          <button v-if="!item.publishedId" @click="editDraft(item)">编辑</button>
          <button v-if="!item.publishedId" @click="publish(item)" :disabled="busy">发布不可变版本</button>
        </div>
      </div>
      <div class="pro-card p-6"><h3 class="font-bold">已发布版本</h3><p v-for="item in templates" :key="item.id">{{ item.name }} · 版本ID {{ item.id }} · {{ item.attitude }}/{{ item.content }}/{{ item.method }}/{{ item.effect }}%
        <button @click="copyTemplate(item)" class="ml-3 underline">基于此版本新建草稿</button></p></div>
    </template>
    <template v-else>
      <form @submit.prevent="load" class="pro-card p-5 grid sm:grid-cols-4 gap-4">
        <label>学期<input v-model.trim="filter.term" placeholder="留空采用最新可见学期" /></label>
        <label v-if="role === 'SUPERVISOR'">课程类别<select v-model="category" aria-label="课程类别"><option value="">全部类别</option><option v-for="c in categories" :key="c" :value="c">{{ c }}</option></select></label>
        <label v-else>课程类别<input v-model.trim="filter.courseType" placeholder="留空查询全部类别" /></label>
        <label v-if="role !== 'SUPERVISOR'">课程关键词<input v-model.trim="filter.keyword" /></label>
        <label v-if="role === 'DIRECTOR'">教师姓名（筛选）<input v-model.trim="filter.teacher" /></label>
        <button class="primary" :disabled="busy">查询</button>
      </form>
      <template v-if="role === 'DIRECTOR'">
        <div class="pro-card p-5 space-y-3">
          <h3 class="font-bold">质量汇总（US-18）</h3><p>快照：{{ report?.asOf }} · 共 {{ report?.total ?? 0 }} 条。页面分页，下载全部筛选结果。</p>
          <div class="flex gap-3"><button @click="download('xlsx')">下载Excel</button><button @click="download('csv')">下载CSV</button></div>
          <div class="overflow-auto"><table><thead><tr><th>课程</th><th>类别</th><th>模板</th><th>审核样本</th><th>有效总分样本</th><th>均分</th><th>旧版原分</th><th>来源评价ID</th></tr></thead><tbody><tr v-for="(r,i) in report?.rows || []" :key="i"><td>{{ r.courseCode }} {{ r.courseName }}</td><td>{{ r.courseType }}</td><td>{{ r.template }}</td><td>{{ r.evaluationCount }}</td><td>{{ r.validTotalCount }}</td><td>{{ r.average ?? '暂无' }}</td><td>{{ r.legacyAverage ?? '暂无' }}</td><td>{{ r.evaluationIds.join(', ') }}</td></tr></tbody></table></div>
          <p v-if="!report?.rows?.length">所选范围暂无数据。</p>
          <div class="flex gap-4"><button :disabled="page <= 1" @click="page--; load()">上一页</button><span>第{{ page }}页</span><button :disabled="page * 20 >= (report?.total || 0)" @click="page++; load()">下一页</button></div>
        </div>
        <div class="pro-card p-5 space-y-3"><h3 class="font-bold">质量预警（US-16）</h3><p>演示配置触发，需人工复核，不作为考核结论。</p><p v-if="!alerts.length">所选范围暂无预警。</p><article v-for="(a,i) in alerts" :key="i" class="border rounded-xl p-3"><b>{{ a.courseName }} · {{ a.alertType }}</b><p>{{ a.alertMessage }} · 触发值 {{ a.currentScore ?? '无评分' }} · 样本 {{ a.evaluationCount }} · 阈值 {{ a.threshold ?? '零覆盖' }}</p><p>{{ a.thresholdSource }} · 评价ID {{ a.evaluationIds?.join(', ') || '无' }}</p></article></div>
      </template>
      <template v-if="role === 'SUPERVISOR'">
        <div class="pro-card p-5 space-y-3"><h3 class="font-bold">维护独立建议（US-21）</h3><p>在督导工作台暂存评价草稿后，可添加多条建议。建议随父评价提交与审核。</p>
          <label>本人可编辑评价<select v-model="adviceParent" @change="loadAdvice"><option value="">请选择草稿或驳回评价</option><option v-for="e in editableEvaluations" :key="e.id" :value="e.id">评价{{ e.id }} · {{ e.offering.course.courseName }} · {{ e.status }}</option></select></label>
          <article v-for="a in editableAdvice" :key="a.id" class="border rounded p-3">建议{{ a.id }}：{{ a.text }}<button @click="editAdvice(a)" class="ml-3">编辑建议</button></article>
          <form v-if="adviceParent" @submit.prevent="saveAdvice" class="space-y-3"><label>建议文本<textarea v-model.trim="adviceForm.text" required maxlength="2000"></textarea></label><label>证据引用JSON（评价、资源历史版本或微格片段）<textarea v-model="adviceForm.evidence"></textarea></label><div class="flex gap-3"><label v-for="stage in stages" :key="stage"><input type="checkbox" v-model="adviceForm.stageTags" :value="stage" />{{ stage }}</label></div><button :disabled="busy">{{ adviceForm.id ? '保存建议修改' : '新增独立建议' }}</button><button type="button" @click="resetAdvice">填写新建议</button></form>
        </div>
        <div class="pro-card p-5 space-y-4"><h3 class="font-bold">按课程类别查看问题分布（US-19）</h3><p>{{ problems?.rule }}</p>
          <p v-if="!problems?.groups?.length">所选类别没有有效开课课程。</p>
          <article v-for="group in problems?.groups || []" :key="group.courseType" class="border rounded-xl p-4 space-y-3">
            <h4 class="font-bold">{{ group.courseType }}</h4><p>课程 {{ group.courseCount }} · 审核样本 {{ group.sampleCount }} · 已标注 {{ group.taggedCount }} · 未标注 {{ group.untaggedCount }}</p>
            <p v-if="!group.sampleCount">本类别暂无审核评价样本。</p>
            <div v-for="tag in group.tags" :key="tag.name" class="grid grid-cols-[6rem_1fr_6rem] gap-3 items-center"><span>{{ tag.name }}</span><div class="bg-slate-100 rounded"><div class="h-3 bg-blue-600 rounded" :style="{width: tag.percentage + '%'}"></div></div><span>{{ tag.count }} / {{ tag.percentage }}%</span></div>
            <details><summary>查看数据表与来源明细</summary><table><thead><tr><th>标签</th><th>评价数</th><th>占比</th></tr></thead><tbody><tr v-for="tag in group.tags" :key="tag.name"><td>{{ tag.name }}</td><td>{{ tag.count }}</td><td>{{ tag.percentage }}%</td></tr></tbody></table><p v-for="d in group.details" :key="d.evaluationId">评价 {{ d.evaluationId }} · {{ d.courseCode }} · {{ d.courseType }} · {{ d.tags.join('、') || '未标注' }} · {{ d.classificationVersion }}</p></details>
          </article>
        </div>
      </template>
      <template v-if="role === 'TEACHER'">
        <div class="pro-card p-5 space-y-4"><h3 class="font-bold">本人评价汇总（US-17）</h3><p>审核且反馈时间已到的样本：{{ radar?.sampleCount ?? 0 }}。未知权重单列，缺失项不补零。</p><div ref="radarElement" class="h-72"></div>
          <table><thead><tr><th>维度</th><th>归一化得分</th><th>有效样本</th></tr></thead><tbody><tr v-for="a in radar?.axes || []" :key="a.name"><td>{{ axes.find(x=>x.key===a.name)?.label }}</td><td>{{ a.value ?? '暂无/不适用' }}</td><td>{{ a.sampleCount }}</td></tr></tbody></table>
          <p v-for="(g,i) in radar?.groups || []" :key="i">{{ g.courseName }} · {{ g.template }} · 总分均值 {{ g.average ?? '暂无' }} · 旧版原分 {{ g.legacyAverage ?? '暂无' }} · 有效样本 {{ g.validTotalCount }}</p>
          <details><summary>查看脱敏评价明细</summary><p v-for="d in radar?.details || []" :key="d.evaluationId">评价{{ d.evaluationId }} · 模板{{ d.templateVersionId ?? '旧版未知' }} · 原分{{ d.scores }} · {{ d.highlights }} · {{ d.suggestions }}</p></details>
        </div>
        <div class="pro-card p-5 space-y-4"><h3 class="font-bold">建议到行动（US-21）</h3><p v-if="!suggestions.length">暂无对本人开放的建议。</p>
          <form v-for="s in suggestions" :key="s.id" @submit.prevent="saveFeedback(s)" class="border rounded-xl p-4 space-y-3">
            <b>{{ s.courseName }} · 建议{{ s.id }}</b><p>{{ s.text }}</p><p>证据：{{ s.evidenceRefs }}</p>
            <label>处理决定<select v-model="s.edit.decision"><option value="ACCEPTED">接受</option><option value="ADJUSTED">调整</option></select></label>
            <label>行动内容<textarea v-model.trim="s.edit.actionText" required maxlength="2000"></textarea></label>
            <label>实践反馈<textarea v-model.trim="s.edit.feedbackText" maxlength="4000"></textarea></label>
            <label>证据引用JSON（可选）<textarea v-model="s.edit.evidence" placeholder='[{"type":"EVALUATION","id":1}]'></textarea></label>
            <div class="flex flex-wrap gap-3"><label v-for="stage in stages" :key="stage"><input type="checkbox" v-model="s.edit.stageTags" :value="stage" />{{ stage }}</label></div>
            <button class="primary" :disabled="busy">保存行动反馈</button><details><summary>修改历史（服务端追加）</summary><p v-for="h in s.history" :key="h.recordVersion">版本{{ h.recordVersion }} · {{ h.occurredAt }} · {{ h.snapshotJson }}</p></details>
          </form>
        </div>
      </template>
      <ResourceVersionsPanel v-if="role === 'DIRECTOR' || role === 'TEACHER'" :role="role" />
    </template>
  </section>
</template>
<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { sprint3Api } from '../api'
import ResourceVersionsPanel from './ResourceVersionsPanel.vue'
const props=defineProps<{role:string}>()
const role=props.role
const axes=[{key:'attitude',label:'教学态度'},{key:'content',label:'教学内容'},{key:'method',label:'教学方法'},{key:'effect',label:'教学效果'}]
const stages=['B','O','P1','P2','P3','S']
const busy=ref(false),error=ref(''),message=ref(''),templates=ref<any[]>([]),drafts=ref<any[]>([])
const draft=ref<any>({name:'',scenario:'随堂督导',descriptions:'教学态度、教学内容、教学方法、教学效果',attitude:25,content:25,method:25,effect:25})
const filter=ref({term:'',courseType:'',keyword:'',teacher:''}),category=ref(''),categories=ref<string[]>([]),page=ref(1)
const report=ref<any>(),radar=ref<any>(),problems=ref<any>(),alerts=ref<any[]>([]),suggestions=ref<any[]>([])
const editableEvaluations=ref<any[]>([]),editableAdvice=ref<any[]>([]),adviceParent=ref<number|''>('')
const adviceForm=ref<any>({text:'',evidence:'[]',stageTags:[]})
const resetAdvice=()=>{adviceForm.value={text:'',evidence:'[]',stageTags:[]}}
const loadAdvice=()=>run(async()=>{editableAdvice.value=adviceParent.value?await sprint3Api.get('/api/v1/supervisions/'+adviceParent.value+'/suggestions'):[];resetAdvice()})
const editAdvice=(a:any)=>{adviceForm.value={id:a.id,expectedVersion:a.recordVersion,text:a.text,evidence:JSON.stringify(a.evidenceRefs),stageTags:a.stageTags||[]}}
const saveAdvice=()=>run(async()=>{const a=adviceForm.value;await sprint3Api.post('/api/v1/supervisions/'+adviceParent.value+'/suggestions',{id:a.id,expectedVersion:a.expectedVersion,text:a.text,evidenceRefs:JSON.parse(a.evidence||'[]'),stageTags:a.stageTags});editableAdvice.value=await sprint3Api.get('/api/v1/supervisions/'+adviceParent.value+'/suggestions');resetAdvice();message.value='建议已保存；请重新打开评价草稿，以读取最新记录版本'})
const radarElement=ref<HTMLDivElement>();let chart:echarts.ECharts|undefined
function params(){return {...filter.value,courseType:role==='SUPERVISOR'&&category.value!=='未分类'?category.value:filter.value.courseType,uncategorized:role==='SUPERVISOR'&&category.value==='未分类'}}
async function run(fn:()=>Promise<void>){if(busy.value)return;busy.value=true;error.value='';message.value='';try{await fn()}catch(e:any){error.value=e.message||'操作失败'}finally{busy.value=false}}
async function fetchData(){
 if(role==='ADMIN'){[templates.value,drafts.value]=await Promise.all([sprint3Api.get('/api/v1/sprint3/templates'),sprint3Api.get('/api/v1/admin/templates')]);return}
 if(role==='DIRECTOR'){[report.value,alerts.value]=await Promise.all([sprint3Api.get('/api/v1/sprint3/quality',{...params(),page:page.value}),sprint3Api.get('/api/v1/sprint3/alerts',params())])}
 if(role==='SUPERVISOR'){editableEvaluations.value=(await sprint3Api.get('/api/v1/supervisions')).filter((e:any)=>['DRAFT','REJECTED'].includes(e.status));problems.value=await sprint3Api.get('/api/v1/sprint3/problems',params());categories.value=problems.value.categories}
 if(role==='TEACHER'){
  [radar.value,suggestions.value]=await Promise.all([sprint3Api.get('/api/v1/sprint3/radar',params()),sprint3Api.get('/api/v1/sprint3/suggestions/mine')])
  suggestions.value.forEach(s=>{s.edit={decision:s.feedback?.decision||'ACCEPTED',actionText:s.feedback?.actionText||'',feedbackText:s.feedback?.feedbackText||'',evidence:s.feedback?.evidenceJson||'[]',stageTags:JSON.parse(s.feedback?.stageTagsJson||'[]')}})
  await nextTick();if(radarElement.value){chart||=echarts.init(radarElement.value);const a=radar.value.axes;chart.setOption({radar:{indicator:axes.map(x=>({name:x.label,max:100}))},series:[{type:'radar',data:a.every((x:any)=>x.value!=null)?[{value:a.map((x:any)=>x.value),name:'归一化维度均分'}]:[]}],title:{text:a.some((x:any)=>x.value==null)?'缺少有效维度，请查看数据表':'',textStyle:{fontSize:12}}},true)}
 }
}
const load=()=>run(fetchData)
const editDraft=(d:any)=>{draft.value={...d,expectedVersion:d.revision}}
const copyTemplate=(d:any)=>{draft.value={name:d.name,scenario:d.scenario,descriptions:d.descriptions,attitude:d.attitude,content:d.content,method:d.method,effect:d.effect,basedOnVersionId:d.id}}
const saveTemplate=()=>run(async()=>{const d=draft.value;const body={expectedVersion:d.expectedVersion,name:d.name,scenario:d.scenario,descriptions:d.descriptions,attitude:d.attitude,content:d.content,method:d.method,effect:d.effect,basedOnVersionId:d.basedOnVersionId};if(d.id)await sprint3Api.put('/api/v1/admin/templates/'+d.id,body);else await sprint3Api.post('/api/v1/admin/templates',body);await fetchData();message.value='模板草稿已保存'})
const publish=(d:any)=>run(async()=>{await sprint3Api.post('/api/v1/admin/templates/'+d.id+'/publish',{expectedVersion:d.revision});await fetchData();message.value='已生成不可变发布版本'})
const saveFeedback=(s:any)=>run(async()=>{await sprint3Api.put('/api/v1/suggestions/'+s.id+'/my-feedback',{expectedVersion:s.feedback?.revision||0,decision:s.edit.decision,actionText:s.edit.actionText,feedbackText:s.edit.feedbackText,evidenceRefs:JSON.parse(s.edit.evidence||'[]'),stageTags:s.edit.stageTags});await fetchData();message.value='行动反馈与修改历史已保存'})
const download=(format:string)=>run(async()=>{const blob=await sprint3Api.blob('/api/v1/sprint3/quality/export',{...params(),format});const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='teaching-quality.'+format;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)})
onMounted(load);onUnmounted(()=>chart?.dispose())
</script>
<style scoped>
label{display:block;font-size:.875rem}input:not([type=checkbox]),select,textarea{display:block;width:100%;border:1px solid #cbd5e1;border-radius:.5rem;padding:.5rem;margin-top:.35rem}button{border:1px solid #cbd5e1;border-radius:.5rem;padding:.4rem .8rem;font-size:.875rem}button.primary{background:#1e3a5f;color:white}button:disabled{opacity:.5}th,td{padding:.6rem;border-bottom:1px solid #e2e8f0;text-align:left;white-space:nowrap}table{width:100%;font-size:.875rem}summary{cursor:pointer}
</style>

