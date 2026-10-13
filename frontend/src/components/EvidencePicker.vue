<template>
 <fieldset class="ui-evidence"><legend>{{ label || '关联证据（可选）' }}</legend>
  <p class="text-sm text-slate-600">可关联同课程的评价、资源历史版本或已验证视频片段，最多10项。</p>
  <div v-for="(item,index) in refs" :key="index" class="ui-evidence-row">
   <span v-if="item.message" class="text-rose-700">证据不可访问 · 请明确移除或替换</span><span v-else>{{ typeNames[item.type] }} #{{ item.id }}<span v-if="item.startMs!=null"> · {{ item.startMs }}～{{ item.endMs }}毫秒</span></span>
   <div><button type="button" @click="replaceIndex=index">替换</button> <button type="button" @click="remove(index)">移除</button></div>
  </div>
  <p v-if="replaceIndex>=0" role="status">正在替换第{{ replaceIndex+1 }}项证据 <button type="button" @click="replaceIndex=-1">取消替换</button></p>
  <label>证据类型<select v-model="type" @change="load" aria-label="证据类型"><option v-for="(name,key) in typeNames" :key="key" :value="key">{{ name }}</option></select></label>
  <label>筛选证据<input v-model="keyword" placeholder="输入名称关键词" @keydown.enter.prevent="load" /></label>
  <button type="button" class="ui-button" @click="load" :disabled="busy||!evaluationId">{{ busy?'查询中…':'查询候选' }}</button>
  <p v-if="error" role="alert" class="text-rose-700">{{ error }} <button type="button" @click="load">重试</button></p>
  <template v-else-if="loaded"><label>可选证据<select v-model="selected" aria-label="可选证据"><option value="">请选择证据</option><option v-for="c in candidates" :key="c.id" :value="c.id">{{ c.label }}</option></select></label><p v-if="!candidates.length">该类型暂无可引用证据。</p></template>
  <button type="button" class="ui-button" :disabled="busy||!selected||(replaceIndex<0&&refs.length>=10)" @click="add">{{ replaceIndex>=0?'确认替换':'添加证据' }}</button>
  <p v-if="refs.some(r=>r.message)" role="alert" class="text-rose-700">请处理不可访问证据后再保存。</p>
 </fieldset>
</template>
<script setup lang="ts">
import {computed,ref,watch,onUnmounted} from 'vue'
import {sprint3Api} from '../api'
const props=defineProps<{modelValue:any; evaluationId:number|string;label?:string}>(),emit=defineEmits<{(e:'update:modelValue',value:any[]):void}>()
const refs=computed<any[]>(()=>{try{const r=typeof props.modelValue==='string'?JSON.parse(props.modelValue):props.modelValue;return Array.isArray(r)?r:[{message:'证据不可访问'}]}catch{return [{message:'证据不可访问'}]}})
const typeNames={EVALUATION:'评价',RESOURCE_VERSION:'资源历史版本',MICRO_SLICE:'微格片段'},type=ref('EVALUATION'),keyword=ref(''),selected=ref<number|''>(''),candidates=ref<any[]>([]),error=ref(''),busy=ref(false),loaded=ref(false),replaceIndex=ref(-1);let generation=0
async function load(){const request=++generation;busy.value=true;error.value='';loaded.value=false;selected.value='';if(!props.evaluationId){busy.value=false;return}try{const rows=await sprint3Api.get('/api/v1/supervisions/'+props.evaluationId+'/evidence-candidates',{type:type.value,keyword:keyword.value});if(request!==generation)return;candidates.value=rows;loaded.value=true}catch(e:any){if(request===generation)error.value=e.message||'候选加载失败'}finally{if(request===generation)busy.value=false}}
function remove(index:number){emit('update:modelValue',refs.value.filter((_,i)=>i!==index));replaceIndex.value=-1}
function add(){const candidate=candidates.value.find(c=>c.id===Number(selected.value));if(!candidate)return;const next=[...refs.value],item={type:candidate.type,id:candidate.id};if(replaceIndex.value>=0)next[replaceIndex.value]=item;else if(!next.some(x=>x.type===item.type&&x.id===item.id))next.push(item);emit('update:modelValue',next);replaceIndex.value=-1;selected.value=''}
watch(()=>props.evaluationId,()=>{replaceIndex.value=-1;load()},{immediate:true});onUnmounted(()=>generation++)
</script>
