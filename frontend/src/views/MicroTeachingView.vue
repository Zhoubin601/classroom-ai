<template>
  <section class="space-y-5">
    <div class="pro-card p-6 space-y-3">
      <h1 class="text-xl font-bold text-slate-900">微格教学视频</h1>
      <p class="text-sm text-slate-500">按课程查看教学视频与 BOPPPS 环节。{{ canWrite ? '可上传、挂载或删除关联课程的视频。' : '在授权专业范围内预览课程视频。' }}</p>
      <label class="block text-sm font-medium">选择课程
        <select v-model="courseId" @change="load" aria-label="微格课程" class="mt-2 w-full rounded-xl border border-slate-200 p-3">
          <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.courseCode }} · {{ course.courseName }}</option>
        </select>
      </label>
    </div>
    <p v-if="error" role="alert" class="rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-800">{{ error }}</p>
    <p v-if="message" role="status" class="rounded-xl bg-emerald-50 p-3 text-emerald-800">{{ message }}</p>
    <form v-if="canWrite" @submit.prevent="upload" class="pro-card p-6 grid gap-4 md:grid-cols-2">
      <h2 class="md:col-span-2 text-base font-bold">上传视频</h2>
      <label class="text-sm">视频标题<input v-model="title" required maxlength="255" placeholder="填写微格视频标题" class="mt-1 w-full rounded-lg border border-slate-200 p-2" /></label>
      <label class="text-sm">教学环节<select v-model="stage" aria-label="教学环节" class="mt-1 w-full rounded-lg border border-slate-200 p-2"><option v-for="(label,key) in stages" :key="key" :value="key">{{ label }}</option></select></label>
      <label class="text-sm">视频文件（MP4 / WebM，最大100MB）<input type="file" accept="video/mp4,video/webm,.mp4,.webm" @change="selectFile" class="mt-2 block w-full" /></label>
      <label class="text-sm">时长（秒）<input v-model.number="duration" type="number" min="1" required aria-label="视频时长" class="mt-1 w-full rounded-lg border border-slate-200 p-2" /></label>
      <button :disabled="busy || !file || !courseId" class="rounded-xl bg-indigo-600 px-4 py-2 text-white disabled:opacity-50">{{ busy ? '正在上传…' : '上传微格视频' }}</button>
      <details class="text-sm"><summary class="cursor-pointer p-2">挂载已有视频地址</summary><label class="block mt-2">视频地址<input v-model="externalUrl" placeholder="https://…" class="mt-1 w-full rounded-lg border border-slate-200 p-2" /></label><button type="button" @click="mount" :disabled="busy" class="mt-2 rounded-lg border border-indigo-200 px-3 py-2 text-indigo-700">挂载视频地址</button></details>
    </form>
    <div class="grid gap-5 lg:grid-cols-2">
      <div class="pro-card p-5 space-y-3">
        <h2 class="font-bold">课程视频（{{ slices.length }}）</h2>
        <p v-if="loading" role="status" class="text-sm text-slate-500">正在读取视频…</p>
        <p v-else-if="!slices.length" class="text-sm text-slate-500">本课程暂无微格视频。</p>
        <article v-for="slice in slices" :key="slice.id" class="rounded-xl border border-slate-200 p-4 space-y-2">
          <h3 class="font-medium">{{ slice.videoTitle }}</h3>
          <p class="text-xs text-slate-500">{{ stages[slice.bopppsStage] || slice.bopppsStage }} · {{ slice.durationSeconds }} 秒</p>
          <div class="flex gap-3"><button @click="play(slice)" class="rounded-lg bg-indigo-50 px-3 py-2 text-sm text-indigo-700">播放视频</button><button v-if="canWrite" @click="remove(slice)" class="rounded-lg px-3 py-2 text-sm text-rose-700">删除视频</button></div>
        </article>
      </div>
      <div class="pro-card p-5 space-y-3">
        <h2 class="font-bold">{{ playingTitle || '视频预览' }}</h2>
        <video v-if="videoUrl" :key="videoUrl" :src="videoUrl" controls playsinline controlslist="nodownload" @error="mediaError" @playing="message = '视频正在播放'" data-testid="micro-video" class="w-full rounded-xl bg-slate-950" />
        <p v-else class="text-sm text-slate-500">选择课程视频后点击播放。</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { courseApi, microTeachingApi } from '../api'
import type { Course } from '../api/types'
const props = defineProps<{ loggedInUser: any }>()
const canWrite = computed(() => ['DIRECTOR','TEACHER'].includes(props.loggedInUser?.role))
const courses=ref<Course[]>([]),courseId=ref<number|null>(null),slices=ref<any[]>([])
const title=ref(''),stage=ref('P2'),duration=ref(1),file=ref<File|null>(null),externalUrl=ref('')
const busy=ref(false),loading=ref(false),error=ref(''),message=ref(''),videoUrl=ref(''),playingTitle=ref('')
const stages: Record<string,string>={B:'导入',O:'目标',P1:'前测',P2:'参与式学习',P3:'后测',S:'总结'}
let generation=0
const clearVideo=()=>{ if(videoUrl.value.startsWith('blob:')) URL.revokeObjectURL(videoUrl.value);videoUrl.value='';playingTitle.value='' }
const load=async()=>{
  const gen=++generation,id=courseId.value;clearVideo();slices.value=[];error.value='';message.value='';loading.value=true
  try{if(id){const rows=await microTeachingApi.list(id);if(gen===generation)slices.value=rows}}
  catch(e:any){if(gen===generation)error.value=e.message||'读取视频失败'}finally{if(gen===generation)loading.value=false}
}
const selectFile=async(event:Event)=>{
  const selected=(event.target as HTMLInputElement).files?.[0];file.value=selected||null
  if(!selected)return
  const url=URL.createObjectURL(selected),probe=document.createElement('video');probe.preload='metadata';probe.src=url
  probe.onloadedmetadata=()=>{if(Number.isFinite(probe.duration)&&probe.duration>0)duration.value=Math.ceil(probe.duration);URL.revokeObjectURL(url)}
  probe.onerror=()=>URL.revokeObjectURL(url)
}
const upload=async()=>{
  if(!file.value||!courseId.value||busy.value)return;busy.value=true;error.value=''
  try{await microTeachingApi.upload(courseId.value,title.value,stage.value,duration.value,file.value);await load();message.value='微格视频已上传'}catch(e:any){error.value=e.message||'上传失败'}finally{busy.value=false}
}
const mount=async()=>{
  if(!courseId.value||busy.value)return
  try{const url=new URL(externalUrl.value);if(!['https:','http:'].includes(url.protocol)||!title.value.trim()||duration.value<1)throw new Error('请填写视频标题、有效时长和HTTP(S)视频地址')
    busy.value=true;await microTeachingApi.mount({courseId:courseId.value,videoTitle:title.value,bopppsStage:stage.value,durationSeconds:duration.value,sliceUrl:url.href});await load();message.value='视频地址已挂载'
  }catch(e:any){error.value=e.message||'挂载失败'}finally{busy.value=false}
}
const play=async(slice:any)=>{
  const gen=generation;clearVideo();error.value='';message.value='正在加载视频…'
  try{
    let url:string
    if(slice.sliceUrl?.startsWith('/uploads/micro/'))url=URL.createObjectURL(await microTeachingApi.video(slice.id))
    else{const external=new URL(slice.sliceUrl,location.origin);if(!['http:','https:'].includes(external.protocol))throw new Error('视频地址不受支持');url=external.href}
    if(gen!==generation){if(url.startsWith('blob:'))URL.revokeObjectURL(url);return}
    videoUrl.value=url;playingTitle.value=slice.videoTitle;message.value='视频已加载，点击播放器开始播放'
  }catch(e:any){message.value='';error.value=e.message||'视频读取失败，请检查来源地址'}
}
const mediaError=()=>{message.value='';error.value='视频无法解码或地址不可用，请更换有效的MP4/WebM视频'}
const remove=async(slice:any)=>{if(!canWrite.value||!confirm(`删除视频「${slice.videoTitle}」的课程记录？`))return;try{await microTeachingApi.remove(slice.id);await load();message.value='视频记录已删除'}catch(e:any){error.value=e.message}}
onMounted(async()=>{try{courses.value=await courseApi.getAll();courseId.value=courses.value[0]?.id||null;await load()}catch(e:any){error.value=e.message}})
onUnmounted(()=>{generation++;clearVideo()})
</script>
