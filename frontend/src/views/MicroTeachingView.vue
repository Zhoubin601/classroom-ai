<template>
<section class="space-y-5">
 <div class="pro-card p-6"><h1 class="text-xl font-bold">授权微格片段</h1><p class="text-slate-600">{{ canWrite ? '在所属课程权限内导入视频并维护片段。' : '在授权专业内回看片段。' }} 教学环节与四维评分独立。</p></div>
 <p v-if="error" role="alert" class="bg-rose-50 p-4 text-rose-800">{{ error }} <button v-if="!courseReady" :disabled="loading||busy" @click="load">重试加载课程片段</button></p><p v-if="message" role="status">{{ message }}</p>
 <label class="block">课程<select :value="courseId" :disabled="busy||loading" @change="changeCourse" data-filter aria-label="微格课程"><option v-for="c in courses" :key="c.id" :value="c.id">{{ c.courseName }}</option></select></label>
 <p v-if="loading" role="status">正在加载课程片段…</p>
 <div v-if="canWrite" class="pro-card p-6 space-y-5">
  <form @submit.prevent="upload" class="grid sm:grid-cols-2 gap-4"><fieldset :disabled="busy||!courseReady" class="contents">
   <h2 class="sm:col-span-2 font-bold">导入受控视频资产</h2>
   <label>资产教学环节<select v-model="source.bopppsStage" required><option value="">请选择环节</option><option v-for="(label,key) in stages" :key="key" :value="key">{{ label }}</option></select></label>
   <label>来源类型<select v-model="source.sourceType"><option value="PLATFORM_UPLOAD">平台上传</option><option value="AUTHORIZED_IMPORT">授权来源导入</option></select></label>
   <label>来源说明<input v-model.trim="source.sourceDescription" required maxlength="128" /></label>
   <label v-if="source.sourceType==='AUTHORIZED_IMPORT'">来源引用<input v-model.trim="source.sourceReference" required maxlength="512" placeholder="来源编号或HTTP(S)地址，仅记录不下载" /></label>
   <label>授权声明<textarea v-model.trim="source.authorizationDeclaration" required maxlength="1000"></textarea></label>
   <label>MP4/WebM文件<input ref="fileInput" type="file" accept="video/mp4,video/webm" @change="selectFile" required /></label>
   <button :disabled="busy||!file">导入并读取服务端时长</button>
  </fieldset></form>
  <form @submit.prevent="mount" class="grid sm:grid-cols-2 gap-4"><fieldset :disabled="busy||!courseReady" class="contents">
   <h2 class="sm:col-span-2 font-bold">{{ editingId ? '修改原资产片段' : '挂载片段' }}</h2>
   <label>受控资产<select v-model="assetId" :disabled="!!editingId && !!originalAssetId" required><option value="">请选择资产</option><option v-for="a in assets" :key="a.id" :value="a.id">资产{{ a.id }} · {{ a.sourceDescription }} · {{ a.durationMs }}毫秒</option></select></label>
   <label>片段标题<input v-model.trim="title" required maxlength="255" /></label>
   <label>教学环节<select v-model="stage" required><option value="">请选择环节</option><option v-for="(label,key) in stages" :key="key" :value="key">{{ label }}</option></select></label>
   <label>起点（分:秒.毫秒）<input v-model="startText" placeholder="0:00.000" required /></label>
   <label>终点（分:秒.毫秒）<input v-model="endText" placeholder="0:01.000" required /></label>
   <details class="sm:col-span-2"><summary>高级分析选项</summary><label>分析元数据JSON（可选，最大32KiB；需管理员复核）<textarea v-model="analysis" rows="4"></textarea></label></details>
   <button :disabled="busy">{{ editingId ? '保存片段修改' : '挂载片段' }}</button><button type="button" @click="nav.request(()=>{reset();sliceGuard.reset()})">新建片段</button>
  </fieldset></form>
 </div>
 <div class="pro-card p-5 space-y-4"><h2 class="font-bold">课程片段</h2><p v-if="courseReady&&!slices.length">暂无片段。</p>
  <article v-for="slice in slices" :key="slice.id" class="border rounded-xl p-4 space-y-2"><h3 class="font-bold">{{ slice.videoTitle }}</h3><p>{{ stages[slice.bopppsStage] || '标签待补录' }} · {{ slice.assetId ? slice.startMs+'～'+slice.endMs+'毫秒' : '资料未提供/未验证' }} · 来源{{ slice.sourceAgent || '资料未提供' }}</p><p v-if="slice.analysisJson">{{ slice.analysisReviewedAt ? '已复核分析' : '待复核分析' }}：{{ slice.analysisJson }}</p>
   <button @click="play(slice)" :disabled="busy||!courseReady||!slice.assetId">回看片段</button><button v-if="canWrite" :disabled="busy||!courseReady" @click="edit(slice)">修改片段</button><button v-if="canWrite" :disabled="busy||!courseReady" @click="remove(slice)">移除记录</button><button v-if="admin && slice.analysisJson" :disabled="busy||!courseReady" @click="review(slice)">复核分析数据</button>
  </article>
 </div>
 <div v-if="videoUrl" class="pro-card p-5"><h2>{{ playingTitle }}</h2><video ref="player" :src="videoUrl" controls class="w-full max-h-[36rem]" @loadedmetadata="begin" @timeupdate="bound" @seeking="seek" @seeked="seek" @error="mediaError"></video><p>仅在授权片段区间内回看。来源视频未经自动切片。</p></div>
</section>
</template>
<script setup lang="ts">
import {useNavigation,useEditGuard,useFeedback,usePreference} from '../ui/context'
import {formatTime,parseTime} from '../ui/time'

import {computed,ref,onMounted,onUnmounted} from 'vue'
import {courseApi,sprint3Api} from '../api'
const props=defineProps<{loggedInUser:any}>()
const admin=computed(()=>props.loggedInUser?.role==='ADMIN'),canWrite=computed(()=>['ADMIN','DIRECTOR','TEACHER'].includes(props.loggedInUser?.role))
const base=computed(()=>admin.value?'/api/v1/admin':'/api/v1/resources')
const courses=ref<any[]>([]),courseId=usePreference<number|undefined>('micro','course',undefined),slices=ref<any[]>([]),assets=ref<any[]>([])
const error=ref(''),message=ref(''),busy=ref(false),loading=ref(false),loadedCourseId=ref<number>(),file=ref<File>(),fileInput=ref<HTMLInputElement>(),assetId=ref<number|''>(''),title=ref(''),stage=ref(''),analysis=ref(''),editingId=ref<number>(),expectedVersion=ref<number>(),originalAssetId=ref<number>()
const blankSource=()=>({sourceType:'PLATFORM_UPLOAD',bopppsStage:'',sourceDescription:'',sourceReference:'',authorizationDeclaration:''})
const source=ref(blankSource())
const startText=ref(formatTime(0)),endText=ref(formatTime(1))
const courseReady=computed(()=>!loading.value&&!!courseId.value&&loadedCourseId.value===courseId.value)
const stages:Record<string,string>={B:'导入',O:'目标',P1:'前测',P2:'参与式学习',P3:'后测',S:'总结'}
const player=ref<HTMLVideoElement>(),videoUrl=ref(''),playingTitle=ref('')
let interval:{startMs:number,endMs:number}|undefined,generation=0,playGeneration=0,playAbort:AbortController|undefined,disposed=false
function clearVideo(){
 playGeneration++;playAbort?.abort();playAbort=undefined;player.value?.pause()
 if(videoUrl.value.startsWith('blob:'))URL.revokeObjectURL(videoUrl.value)
 videoUrl.value='';playingTitle.value='';interval=undefined
}
async function load(){
 const g=++generation,id=courseId.value,url=base.value
 clearVideo();error.value='';loadedCourseId.value=undefined;slices.value=[];assets.value=[]
 if(!id||disposed)return false
 loading.value=true
 try{
  const [rows,controlled]=await Promise.all([
   sprint3Api.get(admin.value?url+'/micro-slices':url+'/micro-slices/course/'+id,admin.value?{courseId:id}:undefined),
   canWrite.value?sprint3Api.get(url+'/micro-assets',{courseId:id}):Promise.resolve([])
  ])
  if(disposed||g!==generation||id!==courseId.value)return false
  slices.value=rows;assets.value=controlled;loadedCourseId.value=id;return true
 }catch(e:any){if(!disposed&&g===generation)error.value=e.message||'课程片段加载失败';return false}
 finally{if(g===generation)loading.value=false}
}
function requireCourse(){if(!courseReady.value)throw new Error('请先成功加载当前课程片段')}
function requireSlice(s:any){requireCourse();if(!slices.value.some(row=>row.id===s.id))throw new Error('片段已变化，请刷新后重试')}
function clearFile(){file.value=undefined;if(fileInput.value)fileInput.value.value=''}
function selectFile(e:Event){file.value=(e.target as HTMLInputElement).files?.[0]}
async function run(fn:()=>Promise<void>){if(busy.value||disposed)return;const finishSaving=nav.startSaving();busy.value=true;error.value='';message.value='';try{await fn()}catch(e:any){if(!disposed)error.value=e.message}finally{busy.value=false;finishSaving()}}
const upload=()=>{
 const sliceWasDirty=sliceGuard.dirty(),sliceSubmitted=sliceGuard.capture()
 return run(async()=>{
  requireCourse();if(!file.value)throw new Error('请选择课程及文件')
  const data=new FormData();data.append('file',file.value);data.append('courseId',String(courseId.value));Object.entries(source.value).forEach(([k,v])=>data.append(k,v))
  const a=await sprint3Api.upload(base.value+'/micro-assets',data);if(disposed)return
  clearFile();uploadGuard.reset()
  if(!sliceWasDirty&&sliceGuard.capture()===sliceSubmitted&&!editingId.value){assetId.value=a.id;startText.value=formatTime(0);endText.value=formatTime(a.durationMs);sliceGuard.reset()}
  const refreshed=await load();if(!disposed)message.value=refreshed?'资产已导入，时长来自服务端探测':'资产已导入，请重试加载课程片段'
 })
}
const mount=()=>run(async()=>{
 requireCourse();const start=parseTime(startText.value),end=parseTime(endText.value)
 if(!Number.isFinite(start)||!Number.isFinite(end))throw new Error('时间格式应为分:秒.毫秒')
 const d={courseId:courseId.value,assetId:assetId.value,videoTitle:title.value,bopppsStage:stage.value,startMs:start,endMs:end,expectedVersion:expectedVersion.value,analysisMetadata:analysis.value.trim()?JSON.parse(analysis.value):undefined}
 if(editingId.value)await sprint3Api.put(base.value+'/micro-slices/'+editingId.value,d);else await sprint3Api.post(base.value+'/micro-slices',d)
 if(disposed)return;reset();sliceGuard.reset();const refreshed=await load()
 if(!disposed)message.value=refreshed?'片段保存成功':'片段已保存，请重试加载课程片段'
})
function applyEdit(s:any){requireSlice(s);originalAssetId.value=s.assetId;editingId.value=s.id;expectedVersion.value=s.recordVersion;assetId.value=s.assetId||'';title.value=s.videoTitle;stage.value=s.bopppsStage;startText.value=formatTime(s.startMs??0);endText.value=formatTime(s.endMs??1);analysis.value=s.analysisJson||'';sliceGuard.reset()}
function edit(s:any){return nav.request(()=>applyEdit(s))}
function reset(){editingId.value=undefined;expectedVersion.value=undefined;originalAssetId.value=undefined;assetId.value='';title.value='';stage.value='';analysis.value='';startText.value=formatTime(0);endText.value=formatTime(1)}
const remove=(s:any)=>run(async()=>{
 requireSlice(s);if(!await confirm('移除此片段记录？受控资产会保留。'))return
 if(disposed)return;requireSlice(s);await sprint3Api.remove(base.value+'/micro-slices/'+s.id)
 if(disposed)return;if(editingId.value===s.id){reset();sliceGuard.reset()}await load()
})
const review=(s:any)=>run(async()=>{requireSlice(s);await sprint3Api.post('/api/v1/admin/micro-slices/'+s.id+'/review');if(disposed)return;const refreshed=await load();message.value=refreshed?'分析数据已人工复核':'分析数据已复核，请重试加载课程片段'})
async function play(s:any){
 clearVideo();error.value='';const g=playGeneration;playAbort=new AbortController()
 try{
  requireSlice(s);const blob=await sprint3Api.blob(base.value+'/micro-slices/'+s.id+'/video',undefined,playAbort.signal)
  if(disposed||g!==playGeneration)return
  interval={startMs:s.startMs,endMs:s.endMs};playingTitle.value=s.videoTitle;videoUrl.value=URL.createObjectURL(blob)
 }catch(e:any){if(!disposed&&g===playGeneration)error.value=e.message}
}
function begin(){if(player.value&&interval)player.value.currentTime=interval.startMs/1000}
function seek(){const p=player.value,i=interval;if(!p||!i)return;const low=i.startMs/1000,high=(i.endMs-1)/1000;if(p.currentTime<low)p.currentTime=low;else if(p.currentTime>high){p.pause();p.currentTime=high}}
function bound(){const p=player.value,i=interval;if(p&&i&&p.currentTime>=i.endMs/1000){p.pause();p.currentTime=(i.endMs-1)/1000}}
function mediaError(){error.value='视频无法解码，请检查受控资产'}

const nav=useNavigation(),{notify,confirm}=useFeedback()
const uploadGuard=useEditGuard('视频导入',()=>({...source.value,file:file.value?.name||''}),v=>{const {file:ignored,...rest}=v;source.value=rest as any;clearFile()},()=>canWrite.value)
const sliceGuard=useEditGuard('微格片段',()=>({editingId:editingId.value,expectedVersion:expectedVersion.value,originalAssetId:originalAssetId.value,assetId:assetId.value,title:title.value,stage:stage.value,startText:startText.value,endText:endText.value,analysis:analysis.value}),v=>{editingId.value=v.editingId;expectedVersion.value=v.expectedVersion;originalAssetId.value=v.originalAssetId;assetId.value=v.assetId;title.value=v.title;stage.value=v.stage;startText.value=v.startText;endText.value=v.endText;analysis.value=v.analysis},()=>canWrite.value)

async function changeCourse(e:Event){const select=e.target as HTMLSelectElement,next=Number(select.value);select.value=String(courseId.value||'');if(next===courseId.value)return;await nav.request(async()=>{courseId.value=next;reset();sliceGuard.reset();source.value=blankSource();clearFile();uploadGuard.reset();await load()})}

onMounted(async()=>{try{const rows=admin.value?await sprint3Api.get('/api/v1/admin/courses'):await courseApi.getAll();if(disposed)return;courses.value=rows;if(!courses.value.some(c=>c.id===courseId.value))courseId.value=courses.value[0]?.id;await load()}catch(e:any){if(!disposed)error.value=e.message}})
onUnmounted(()=>{disposed=true;generation++;clearVideo()})
</script>
<style scoped>label{display:block;font-size:.875rem}input,select,textarea{display:block;width:100%;padding:.5rem;border:1px solid #cbd5e1;border-radius:.5rem;margin-top:.35rem}button{padding:.5rem .8rem;border:1px solid #cbd5e1;border-radius:.5rem;margin-right:.5rem}button:disabled{opacity:.5}</style>
