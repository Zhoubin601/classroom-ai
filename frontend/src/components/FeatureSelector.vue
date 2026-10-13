<template>
  <section class="feature-selector" ref="container" aria-label="功能选择">
    <div class="feature-selector-heading"><span>工作功能</span><button type="button" :aria-expanded="!compact" @click="toggleCompact">{{ compact ? '展开功能说明' : '收起功能说明' }}</button></div>
    <div role="tablist" :aria-label="label" :class="['feature-grid', {compact}]">
      <button v-for="(item,index) in items" :key="item.key" :ref="el => buttons[index] = el as HTMLButtonElement" type="button"
        role="tab" :id="`${id}-tab-${item.key}`" :aria-controls="`${id}-panel-${item.key}`" :aria-selected="modelValue === item.key"
        :tabindex="index === focusIndex ? 0 : -1" :class="['feature-card',{selected:modelValue === item.key}]"
        :disabled="navigationBusy" @focus="focusIndex=index" @keydown="key($event,index)" @click="select(item.key)">
        <span class="feature-icon"><component :is="item.iconComp || LayoutGrid" aria-hidden="true" /></span>
        <span class="feature-copy"><span class="feature-title">{{ item.label }}<span v-if="item.count != null" class="feature-count">{{ item.count }}</span></span><span v-if="!compact" class="feature-description">{{ item.description }}</span></span>
        <span v-if="modelValue === item.key" class="feature-current">当前功能</span>
      </button>
    </div>
  </section>
</template>
<script setup lang="ts">
import {ref,watch,nextTick,inject} from 'vue'
import {LayoutGrid} from 'lucide-vue-next'
import {nextFeatureIndex} from '../ui/navigation.mjs'
import {useNavigation,usePreference,navigationBusyKey} from '../ui/context'
const props=defineProps<{modelValue:string;items:{key:string;label:string;description?:string;iconComp?:any;count?:number}[];id:string;label?:string}>()
const emit=defineEmits<{(e:'update:modelValue',value:string):void}>()
const nav=useNavigation(), compact=usePreference(props.id,'compact',false),container=ref<HTMLElement>(),buttons=ref<HTMLButtonElement[]>([]),focusIndex=ref(Math.max(0,props.items.findIndex(x=>x.key===props.modelValue)))
const navigationBusy=inject(navigationBusyKey,ref(false))
watch(()=>props.modelValue,value=>{focusIndex.value=Math.max(0,props.items.findIndex(x=>x.key===value))})
function select(value:string){if(value!==props.modelValue)nav.request(()=>emit('update:modelValue',value))}
async function toggleCompact(){const focused=buttons.value.indexOf(document.activeElement as HTMLButtonElement);compact.value=!compact.value;await nextTick();if(focused>=0)buttons.value[focused]?.focus()}
function key(event:KeyboardEvent,index:number){
 if(!['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Home','End'].includes(event.key))return
 event.preventDefault();const grid=container.value?.querySelector('.feature-grid');const columns=grid&&!compact.value?getComputedStyle(grid).gridTemplateColumns.split(' ').length:1
 const next=nextFeatureIndex(index,event.key,props.items.length,columns,compact.value);focusIndex.value=next;buttons.value[next]?.focus()
}
</script>
