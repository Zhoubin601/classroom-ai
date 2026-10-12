<template>
<section class="space-y-5">
 <div class="pro-card p-6"><h1 class="text-xl font-bold">授权微格片段（US-12）</h1><p class="text-slate-600">{{ canWrite ? '在所属课程权限内导入视频并维护片段。' : '在授权专业内回看片段。' }} 教学环节与四维评分独立。</p></div>
 <p v-if="error" role="alert" class="bg-rose-50 p-4 text-rose-800">{{ error }}</p><p v-if="message" role="status">{{ message }}</p>
 <label class="block">课程<select v-model="courseId" @change="load" aria-label="微格课程"><option v-for="c in courses" :key="c.id" :value="c.id">{{ c.courseName }}</option></select></label>
 <div v-if="canWrite" class="pro-card p-6 space-y-5">
  <form @submit.prevent="upload" class="grid sm:grid-cols-2 gap-4">
   <h2 class="sm:col-span-2 font-bold">导入受控视频资产</h2>
   <label>资产教学环节<select v-model="source.bopppsStage" required><option value="">请选择环节</option><option v-for="(label,key) in stages" :key="key" :value="key">{{ label }}</option></select></label>
   <label>来源类型<select v-model="source.sourceType"><option value="PLATFORM_UPLOAD">平台上传</option><option value="AUTHORIZED_IMPORT">授权来源导入</option></select></label>
   <label>来源说明<input v-model.trim="source.sourceDescription" required maxlength="128" /></label>
   <label v-if="source.sourceType==='AUTHORIZED_IMPORT'">来源引用<input v-model.trim="source.sourceReference" required maxlength="512" placeholder="来源编号或HTTP(S)地址，仅记录不下载" /></label>
   <label>授权声明<textarea v-model.trim="source.authorizationDeclaration" required maxlength="1000"></textarea></label>
   <label>MP4/WebM文件<input type="file" accept="video/mp4,video/webm" @change="selectFile" required /></label>
   <button :disabled="busy||!file">导入并读取服务端时长</button>
  </form>
  <form @submit.prevent="mount" class="grid sm:grid-cols-2 gap-4">
   <h2 class="sm:col-span-2 font-bold">{{ editingId ? '修改原资产片段' : '挂载片段' }}</h2>
   <label>受控资产<select v-model="assetId" :disabled="!!editingId && !!originalAssetId" required><option value="">请选择资产</option><option v-for="a in assets" :key="a.id" :value="a.id">资产{{ a.id }} · {{ a.sourceDescription }} · {{ a.durationMs }}毫秒</option></select></label>
   <label>片段标题<input v-model.trim="title" required maxlength="255" /></label>
   <label>教学环节<select v-model="stage" required><option value="">请选择环节</option><option v-for="(label,key) in stages" :key="key" :value="key">{{ label }}</option></select></label>
   <label>起点（毫秒）<input v-model.number="startMs" type="number" min="0" step="1" required /></label>
   <label>终点（毫秒）<input v-model.number="endMs" type="number" min="1" step="1" required /></label>
   <label class="sm:col-span-2">分析元数据JSON（可选，最大32KiB；需管理员复核）<textarea v-model="analysis" rows="4"></textarea></label>
   <button :disabled="busy">{{ editingId ? '保存片段修改' : '挂载片段' }}</button><button type="button" @click="reset">新建片段</button>
  </form>
 </div>
 <div class="pro-card p-5 space-y-4"><h2 class="font-bold">课程片段</h2><p v-if="!slices.length">暂无片段。</p>
  <article v-for="slice in slices" :key="slice.id" class="border rounded-xl p-4 space-y-2"><h3 class="font-bold">{{ slice.videoTitle }}</h3><p>{{ stages[slice.bopppsStage] || '标签待补录' }} · {{ slice.assetId ? slice.startMs+'～'+slice.endMs+'毫秒' : '资料未提供/未验证' }} · 来源{{ slice.sourceAgent || '资料未提供' }}</p><p v-if="slice.analysisJson">{{ slice.analysisReviewedAt ? '已复核分析' : '待复核分析' }}：{{ slice.analysisJson }}</p>
   <button @click="play(slice)" :disabled="!slice.assetId">回看片段</button><button v-if="canWrite" @click="edit(slice)">修改片段</button><button v-if="canWrite" @click="remove(slice)">移除记录</button><button v-if="admin && slice.analysisJson" @click="review(slice)">复核分析数据</button>
  </article>
 </div>
 <div v-if="videoUrl" class="pro-card p-5"><h2>{{ playingTitle }}</h2><video ref="player" :src="videoUrl" controls class="w-full max-h-[36rem]" @loadedmetadata="begin" @timeupdate="bound" @seeking="seek" @seeked="seek" @error="mediaError"></video><p>仅在授权片段区间内回看。来源视频未经自动切片。</p></div>
</section>
</template>
<script setup lang="ts">
import {computed,ref,onMounted,onUnmounted} from 'vue'
import {courseApi,sprint3Api} from '../api'
const props=defineProps<{loggedInUser:any}>()
const admin=computed(()=>props.loggedInUser?.role==='ADMIN'),canWrite=computed(()=>['ADMIN','DIRECTOR','TEACHER'].includes(props.loggedInUser?.role))
const base=computed(()=>admin.value?'/api/v1/admin':'/api/v1/resources')
const courses=ref<any[]>([]),courseId=ref<number>(),slices=ref<any[]>([]),assets=ref<any[]>([])
const error=ref(''),message=ref(''),busy=ref(false),file=ref<File>(),assetId=ref<number|''>(''),title=ref(''),stage=ref(''),startMs=ref(0),endMs=ref(1),analysis=ref(''),editingId=ref<number>(),expectedVersion=ref<number>(),originalAssetId=ref<number>()
const source=ref({sourceType:'PLATFORM_UPLOAD',bopppsStage:'',sourceDescription:'',sourceReference:'',authorizationDeclaration:''})
const stages:Record<string,string>={B:'导入',O:'目标',P1:'前测',P2:'参与式学习',P3:'后测',S:'总结'}
const player=ref<HTMLVideoElement>(),videoUrl=ref(''),playingTitle=ref('');let interval:{startMs:number,endMs:number}|undefined,generation=0
function clearVideo(){if(videoUrl.value.startsWith('blob:'))URL.revokeObjectURL(videoUrl.value);videoUrl.value='';interval=undefined}
async function load(){const g=++generation;clearVideo();error.value='';try{if(!courseId.value)return;const rows=await sprint3Api.get(admin.value?base.value+'/micro-slices':base.value+'/micro-slices/course/'+courseId.value,admin.value?{courseId:courseId.value}:undefined);if(g!==generation)return;slices.value=rows;if(canWrite.value)assets.value=await sprint3Api.get(base.value+'/micro-assets',{courseId:courseId.value})}catch(e:any){error.value=e.message}}
function selectFile(e:Event){file.value=(e.target as HTMLInputElement).files?.[0]}
async function run(fn:()=>Promise<void>){if(busy.value)return;busy.value=true;error.value='';message.value='';try{await fn()}catch(e:any){error.value=e.message}finally{busy.value=false}}
const upload=()=>run(async()=>{if(!file.value||!courseId.value)throw new Error('请选择课程及文件');const data=new FormData();data.append('file',file.value);data.append('courseId',String(courseId.value));Object.entries(source.value).forEach(([k,v])=>data.append(k,v));const a=await sprint3Api.upload(base.value+'/micro-assets',data);await load();assetId.value=a.id;startMs.value=0;endMs.value=a.durationMs;message.value='资产已导入，时长来自服务端探测'})
const mount=()=>run(async()=>{const d={courseId:courseId.value,assetId:assetId.value,videoTitle:title.value,bopppsStage:stage.value,startMs:startMs.value,endMs:endMs.value,expectedVersion:expectedVersion.value,analysisMetadata:analysis.value.trim()?JSON.parse(analysis.value):undefined};if(editingId.value)await sprint3Api.put(base.value+'/micro-slices/'+editingId.value,d);else await sprint3Api.post(base.value+'/micro-slices',d);await load();reset();message.value='片段保存成功'})
function edit(s:any){originalAssetId.value=s.assetId;editingId.value=s.id;expectedVersion.value=s.recordVersion;assetId.value=s.assetId||'';title.value=s.videoTitle;stage.value=s.bopppsStage;startMs.value=s.startMs||0;endMs.value=s.endMs||1;analysis.value=s.analysisJson||''}
function reset(){editingId.value=undefined;expectedVersion.value=undefined;title.value='';stage.value='';analysis.value=''}
const remove=(s:any)=>run(async()=>{if(!confirm('移除此片段记录？受控资产会保留。'))return;await sprint3Api.remove(base.value+'/micro-slices/'+s.id);await load()})
const review=(s:any)=>run(async()=>{await sprint3Api.post('/api/v1/admin/micro-slices/'+s.id+'/review');await load();message.value='分析数据已人工复核'})
async function play(s:any){clearVideo();const g=generation;try{const blob=await sprint3Api.blob(base.value+'/micro-slices/'+s.id+'/video');if(g!==generation)return;interval={startMs:s.startMs,endMs:s.endMs};playingTitle.value=s.videoTitle;videoUrl.value=URL.createObjectURL(blob)}catch(e:any){error.value=e.message}}
function begin(){if(player.value&&interval)player.value.currentTime=interval.startMs/1000}
function seek(){const p=player.value,i=interval;if(!p||!i)return;const low=i.startMs/1000,high=(i.endMs-1)/1000;if(p.currentTime<low)p.currentTime=low;else if(p.currentTime>high){p.pause();p.currentTime=high}}
function bound(){const p=player.value,i=interval;if(p&&i&&p.currentTime>=i.endMs/1000){p.pause();p.currentTime=(i.endMs-1)/1000}}
function mediaError(){error.value='视频无法解码，请检查受控资产'}
onMounted(async()=>{try{courses.value=admin.value?await sprint3Api.get('/api/v1/admin/courses'):await courseApi.getAll();courseId.value=courses.value[0]?.id;await load()}catch(e:any){error.value=e.message}})
onUnmounted(()=>{generation++;clearVideo()})
</script>
<style scoped>label{display:block;font-size:.875rem}input,select,textarea{display:block;width:100%;padding:.5rem;border:1px solid #cbd5e1;border-radius:.5rem;margin-top:.35rem}button{padding:.5rem .8rem;border:1px solid #cbd5e1;border-radius:.5rem;margin-right:.5rem}button:disabled{opacity:.5}</style>
