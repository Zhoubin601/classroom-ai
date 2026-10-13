<template>
 <Teleport to="body"><div v-if="open" class="ui-modal-backdrop" @keydown="key">
  <section ref="panel" role="dialog" aria-modal="true" :aria-labelledby="titleId" tabindex="-1" :class="['ui-modal',size]">
   <header class="ui-modal-heading"><h2 :id="titleId">{{ title }}</h2><button type="button" aria-label="关闭弹窗" @click="close"><X aria-hidden="true" /></button></header>
   <div class="ui-modal-body"><slot /></div><footer v-if="$slots.footer" class="ui-modal-footer"><slot name="footer" /></footer>
  </section>
 </div></Teleport>
</template>
<script setup lang="ts">
import {ref,watch,nextTick,onUnmounted} from 'vue'
import {X} from 'lucide-vue-next'
import {useNavigation} from '../ui/context'
const props=withDefaults(defineProps<{open:boolean;title:string;size?:string;unguarded?:boolean}>(),{size:''})
const emit=defineEmits<{(e:'close'):void}>(),nav=useNavigation(),panel=ref<HTMLElement>();const titleId=`modal-${Math.random().toString(36).slice(2)}`
let previous:HTMLElement|null=null, release:()=>void=()=>{}
const focusable=()=>[...(panel.value?.querySelectorAll<HTMLElement>('button,input,select,textarea,a[href],[tabindex="0"]')||[])].filter(e=>!e.hasAttribute('disabled')&&e.getClientRects().length>0)
async function close(){if(props.unguarded)emit('close');else await nav.request(()=>emit('close'))}
function key(e:KeyboardEvent){if(e.key==='Escape'){e.preventDefault();e.stopPropagation();close()}if(e.key==='Tab'){const items=focusable(),i=items.indexOf(document.activeElement as HTMLElement);if(!items.length){e.preventDefault();panel.value?.focus()}else if(e.shiftKey&&i<=0){e.preventDefault();items[items.length-1]?.focus()}else if(!e.shiftKey&&(i===items.length-1||i<0)){e.preventDefault();items[0].focus()}}}
watch(()=>props.open,async open=>{
 if(!open){release();await nextTick();if(previous?.isConnected)previous.focus();return}
 previous=document.activeElement as HTMLElement;await nextTick();const target=panel.value?.closest('.ui-modal-backdrop');const nodes=[...document.body.children].filter(e=>e!==target&&e instanceof HTMLElement) as HTMLElement[];const states=nodes.map(e=>[e,e.inert] as const),overflow=document.body.style.overflow
 nodes.forEach(e=>e.inert=true);document.body.style.overflow='hidden';release=()=>{states.forEach(([e,inert])=>e.inert=inert);document.body.style.overflow=overflow};(focusable()[0]||panel.value)?.focus()
},{immediate:true,flush:'post'})
onUnmounted(release)
</script>
