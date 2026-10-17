<template>
<div class="pro-card p-5 space-y-4"><h3 class="font-bold">资源历史版本</h3>
<p v-if="error" role="alert" class="text-rose-700">{{ error }}</p><p v-if="message" role="status">{{ message }}</p>
<label>当前资源<select :value="selected" :disabled="navigationBusy||busy||listPending" @change="changeResource"><option value="">请选择资源</option><option v-for="r in resources" :value="r.id" :key="r.id">{{ r.course.courseName }} · {{ r.resourceName }} · {{ r.version }}</option></select></label>
<button :disabled="navigationBusy||busy" @click="nav.request(load)">刷新资源</button><p v-if="!resources.length">暂无可见资源。</p>
<p v-if="listPending||historyPending" role="status">正在加载资源历史…</p>
<template v-if="data"><p>系列{{ data.series.id }} · 当前版本ID {{ data.series.currentVersionId }} · 系列记录版本 {{ data.series.revision }}</p>
<div class="ui-table-scroll"><table><thead><tr><th>固定资源ID</th><th>版本</th><th>上传时间</th><th>操作</th></tr></thead><tbody><tr v-for="v in data.versions" :key="v.id"><td>{{ v.id }}</td><td>{{ v.version }} {{ v.id===data.series.currentVersionId?'（当前）':'' }}</td><td>{{ v.createdAt }}</td><td><button :disabled="!contextReady" @click="preview(v)">授权水印预览</button><button v-if="role==='DIRECTOR' && v.id!==data.series.currentVersionId" :disabled="!contextReady" @click="nav.request(()=>selectVersion(v))">设为当前版</button></td></tr></tbody></table></div>
<form v-if="role==='DIRECTOR' || writableCourses.includes(data.versions[0]?.course.id)" @submit.prevent="upload" class="space-y-3">
<p data-testid="version-upload-policy" role="status">{{ uploadPolicyDescription }}</p>
<p v-if="uploadPolicyError" role="alert" class="text-rose-700">{{ uploadPolicyError }} <button type="button" @click="reloadUploadPolicy">重新加载上传规则</button></p>
<label>上传此系列的新版本<input ref="versionFileInput" type="file" :disabled="!contextReady||!uploadPolicyReady" @change="choose" :accept="uploadAccept" required /></label><button :disabled="!contextReady||!uploadPolicyReady||!file">上传新版并设为当前</button></form>
</template>
<div v-if="previewUrl" class="space-y-2"><button @click="closePreview">关闭预览</button><ProtectedPdfPreview :src="previewUrl" /><p>每5秒复核权限；撤销后关闭预览。</p></div>
</div>
</template>
<script setup lang="ts">
import {useNavigation,useEditGuard,usePreference,useNavigationBusy} from '../ui/context'

import {ref,computed,onMounted,onUnmounted,watch} from 'vue'
import {resourceApi,sprint3Api,courseApi} from '../api'
import {useResourceUploadPolicy} from '../ui/useResourceUploadPolicy'
import ProtectedPdfPreview from './ProtectedPdfPreview.vue'
const props=defineProps<{role:string}>()
const writableCourses=ref<number[]>([])
const resources=ref<any[]>([]),selected=usePreference<number|''>('versions-'+props.role,'selected',''),data=ref<any>(),error=ref(''),message=ref(''),busy=ref(false),file=ref<File>(),previewUrl=ref('')
const listPending=ref(false),historyPending=ref(false),loadedResource=ref<number>()
const versionFileInput=ref<HTMLInputElement>()
const {policy:uploadPolicy,error:uploadPolicyError,ready:uploadPolicyReady,accept:uploadAccept,
 description:uploadPolicyDescription,reload:reloadUploadPolicy,validate:validateUploadFile}=useResourceUploadPolicy()
let disposed=false,listRequest=0,historyRequest=0,previewRequest=0
let listController:AbortController|undefined,historyController:AbortController|undefined
let timer:ReturnType<typeof setInterval>|undefined
const contextReady=computed(()=>!disposed&&!busy.value&&!listPending.value&&!historyPending.value&&loadedResource.value===Number(selected.value)&&!!data.value?.versions?.some((v:any)=>v.id===loadedResource.value))
function clearHistory(){historyRequest++;historyController?.abort();historyController=undefined;historyPending.value=false;loadedResource.value=undefined;data.value=null;closePreview()}
async function load(){
 const request=++listRequest;listController?.abort();const controller=new AbortController();listController=controller
 clearHistory();listPending.value=true;error.value='';message.value=''
 try{
  const rows=await sprint3Api.get('/api/v1/resources',undefined,controller.signal)
  if(disposed||request!==listRequest)return
  resources.value=rows
  // Enable a new selection while remembered history is loading on mount.
  listPending.value=false
  if(selected.value)await history()
 }catch(e:any){if(!disposed&&request===listRequest&&!controller.signal.aborted)error.value=e.message}
 finally{if(request===listRequest){listPending.value=false;listController=undefined}}
}
async function history(){
 clearHistory();const id=Number(selected.value),request=historyRequest
 error.value='';message.value='';file.value=undefined;fileGuard.reset()
 if(!selected.value||disposed)return
 const controller=new AbortController();historyController=controller;historyPending.value=true
 try{
  const result=await sprint3Api.get('/api/v1/resources/'+id+'/history',undefined,controller.signal)
  if(disposed||request!==historyRequest||id!==Number(selected.value))return
  if(!result?.series||!result.versions?.some((v:any)=>v.id===id))throw new Error('资源历史与当前选择不一致，请刷新资源')
  data.value=result;loadedResource.value=id
  // Search lists current versions; a selected historical ID remains valid after a switch.
  if(!resources.value.some((r:any)=>r.id===id))resources.value.push(result.versions.find((v:any)=>v.id===id))
 }catch(e:any){if(!disposed&&request===historyRequest&&!controller.signal.aborted)error.value=e.message}
 finally{if(request===historyRequest){historyPending.value=false;historyController=undefined}}
}
async function selectVersion(v:any){
 if(!contextReady.value||!data.value.versions.some((item:any)=>item.id===v.id))return
 const context=data.value,finishSaving=nav.startSaving();busy.value=true;error.value=''
 try{await sprint3Api.put('/api/v1/resources/series/'+context.series.id+'/current',{versionId:v.id,expectedVersion:context.series.revision});if(!disposed)await load()}
 catch(e:any){if(!disposed)error.value=e.message}
 finally{busy.value=false;finishSaving()}
}
function choose(e:Event){const input=e.target as HTMLInputElement,candidate=input.files?.[0];error.value='';if(!candidate){file.value=undefined;return}const issue=validateUploadFile(candidate);if(issue){file.value=undefined;input.value='';error.value=issue;return}file.value=candidate}
async function upload(){if(!file.value||!contextReady.value)return;const issue=validateUploadFile(file.value);if(issue){error.value=issue;return}const context=data.value,submittedFile=file.value,finishSaving=nav.startSaving();busy.value=true;error.value='';try{const current=context.versions.find((v:any)=>v.id===context.series.currentVersionId);if(!current)throw new Error('当前版本不存在，请刷新资源');const form=new FormData();form.append('file',submittedFile);form.append('courseId',String(current.course.id));form.append('chapter',current.chapter);form.append('resourceName',current.resourceName);form.append('tags',(current.tags||[]).join(','));form.append('isPublic',String(context.series.shared));form.append('seriesId',String(context.series.id));form.append('expectedVersion',String(context.series.revision));await sprint3Api.upload('/api/v1/resources/upload',form);if(disposed)return;file.value=undefined;if(versionFileInput.value)versionFileInput.value.value='';fileGuard.reset();await load();if(!disposed)message.value='新版本已保存，历史文件保留'}catch(e:any){if(!disposed)error.value=e.message}finally{busy.value=false;finishSaving()}}
function closePreview(){previewRequest++;if(timer)clearInterval(timer);timer=undefined;if(previewUrl.value.startsWith('blob:'))URL.revokeObjectURL(previewUrl.value);previewUrl.value=''}
async function preview(v:any){if(!contextReady.value||!data.value.versions.some((item:any)=>item.id===v.id))return;closePreview();const request=previewRequest;try{const blob=await resourceApi.getPreviewPdf(v.id);if(disposed||request!==previewRequest)return;previewUrl.value=URL.createObjectURL(blob);timer=setInterval(async()=>{try{await sprint3Api.get('/api/v1/resources/'+v.id)}catch{if(!disposed&&request===previewRequest){closePreview();error.value='共享授权已撤销，预览关闭'}}},5000)}catch(e:any){if(!disposed&&request===previewRequest)error.value=e.message}}

const navigationBusy=useNavigationBusy(),nav=useNavigation()
const fileGuard=useEditGuard('资源新版上传',()=>({name:file.value?.name||'',size:file.value?.size||0}),()=>{file.value=undefined})
watch(uploadPolicy,policy=>{if(policy&&file.value){const issue=validateUploadFile(file.value);if(issue){file.value=undefined;if(versionFileInput.value)versionFileInput.value.value='';error.value=issue}}})
async function changeResource(e:Event){const select=e.target as HTMLSelectElement,next=select.value?Number(select.value):'';select.value=String(selected.value);await nav.request(async()=>{selected.value=next;await history()})}

onMounted(async()=>{try{const courses=await courseApi.getAll();if(disposed)return;writableCourses.value=courses.map(c=>c.id);await load()}catch(e:any){if(!disposed)error.value=e.message}})
onUnmounted(()=>{disposed=true;listRequest++;listController?.abort();clearHistory()})
</script>
<style scoped>select,input{border:1px solid #cbd5e1;border-radius:.5rem;padding:.5rem}button{padding:.4rem .7rem;border:1px solid #cbd5e1;border-radius:.5rem;margin-right:.5rem}td,th{padding:.5rem;border-bottom:1px solid #e2e8f0;text-align:left}table{width:100%}</style>

