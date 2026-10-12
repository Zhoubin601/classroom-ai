<template>
<div class="pro-card p-5 space-y-4"><h3 class="font-bold">资源历史版本（US-11）</h3>
<p v-if="error" role="alert" class="text-rose-700">{{ error }}</p><p v-if="message" role="status">{{ message }}</p>
<label>当前资源<select v-model="selected" @change="history"><option value="">请选择资源</option><option v-for="r in resources" :value="r.id" :key="r.id">{{ r.course.courseName }} · {{ r.resourceName }} · {{ r.version }}</option></select></label>
<button @click="load">刷新资源</button><p v-if="!resources.length">暂无可见资源。</p>
<template v-if="data"><p>系列{{ data.series.id }} · 当前版本ID {{ data.series.currentVersionId }} · 系列记录版本 {{ data.series.revision }}</p>
<table><thead><tr><th>固定资源ID</th><th>版本</th><th>上传时间</th><th>操作</th></tr></thead><tbody><tr v-for="v in data.versions" :key="v.id"><td>{{ v.id }}</td><td>{{ v.version }} {{ v.id===data.series.currentVersionId?'（当前）':'' }}</td><td>{{ v.createdAt }}</td><td><button @click="preview(v)">授权水印预览</button><button v-if="role==='DIRECTOR' && v.id!==data.series.currentVersionId" @click="selectVersion(v)">设为当前版</button></td></tr></tbody></table>
<form v-if="role==='DIRECTOR' || writableCourses.includes(data.versions[0]?.course.id)" @submit.prevent="upload" class="space-y-3"><label>上传此系列的新版本<input type="file" @change="choose" accept=".ppt,.pptx,.doc,.docx,.pdf" required /></label><button :disabled="busy||!file">上传新版并设为当前</button></form>
</template>
<div v-if="previewUrl" class="space-y-2"><button @click="closePreview">关闭预览</button><ProtectedPdfPreview :src="previewUrl" /><p>每5秒复核权限；撤销后关闭预览。</p></div>
</div>
</template>
<script setup lang="ts">
import {ref,onMounted,onUnmounted} from 'vue'
import {resourceApi,sprint3Api,courseApi} from '../api'
import ProtectedPdfPreview from './ProtectedPdfPreview.vue'
defineProps<{role:string}>()
const writableCourses=ref<number[]>([])
const resources=ref<any[]>([]),selected=ref<number|''>(''),data=ref<any>(),error=ref(''),message=ref(''),busy=ref(false),file=ref<File>(),previewUrl=ref('')
let previewRequest=0
let timer:ReturnType<typeof setInterval>|undefined
async function load(){try{resources.value=await resourceApi.search({});if(selected.value)await history()}catch(e:any){error.value=e.message}}
async function history(){closePreview();error.value='';if(!selected.value){data.value=null;return}try{data.value=await sprint3Api.get('/api/v1/resources/'+selected.value+'/history')}catch(e:any){error.value=e.message;data.value=null}}
async function selectVersion(v:any){try{await sprint3Api.put('/api/v1/resources/series/'+data.value.series.id+'/current',{versionId:v.id,expectedVersion:data.value.series.revision});await load()}catch(e:any){error.value=e.message}}
function choose(e:Event){file.value=(e.target as HTMLInputElement).files?.[0]}
async function upload(){if(!file.value||busy.value)return;busy.value=true;error.value='';try{const current=data.value.versions.find((v:any)=>v.id===data.value.series.currentVersionId);const form=new FormData();form.append('file',file.value);form.append('courseId',String(current.course.id));form.append('chapter',current.chapter);form.append('resourceName',current.resourceName);form.append('tags',(current.tags||[]).join(','));form.append('isPublic',String(data.value.series.shared));form.append('seriesId',String(data.value.series.id));form.append('expectedVersion',String(data.value.series.revision));await sprint3Api.upload('/api/v1/resources/upload',form);await load();message.value='新版本已保存，历史文件保留'}catch(e:any){error.value=e.message}finally{busy.value=false}}
function closePreview(){previewRequest++;if(timer)clearInterval(timer);timer=undefined;if(previewUrl.value.startsWith('blob:'))URL.revokeObjectURL(previewUrl.value);previewUrl.value=''}
async function preview(v:any){closePreview();const request=previewRequest;try{const blob=await resourceApi.getPreviewPdf(v.id);if(request!==previewRequest)return;previewUrl.value=URL.createObjectURL(blob);timer=setInterval(async()=>{try{await sprint3Api.get('/api/v1/resources/'+v.id)}catch{closePreview();error.value='共享授权已撤销，预览关闭'}},5000)}catch(e:any){error.value=e.message}}
onMounted(async()=>{try{writableCourses.value=(await courseApi.getAll()).map(c=>c.id);await load()}catch(e:any){error.value=e.message}});onUnmounted(closePreview)
</script>
<style scoped>select,input{border:1px solid #cbd5e1;border-radius:.5rem;padding:.5rem}button{padding:.4rem .7rem;border:1px solid #cbd5e1;border-radius:.5rem;margin-right:.5rem}td,th{padding:.5rem;border-bottom:1px solid #e2e8f0;text-align:left}table{width:100%}</style>

