<template>
  <div class="minimal-card p-5 relative overflow-hidden group">
    <div class="flex items-center justify-between">
      <div>
        <p class="text-xs font-medium text-slate-500 uppercase tracking-wider">{{ title }}</p>
        <div class="mt-2 flex items-baseline gap-2">
          <span class="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900 font-mono tabular-nums">{{ value }}</span>
          <span v-if="unit" class="text-sm font-medium text-slate-500">{{ unit }}</span>
        </div>
        <p v-if="subtitle" class="mt-1 text-xs text-slate-500 flex items-center gap-1">
          <span>{{ subtitle }}</span>
        </p>
      </div>

      <div :class="['w-11 h-11 rounded-xl flex items-center justify-center transition-colors', iconBoxStyle]">
        <slot name="icon">
          <BarChart3 class="w-5 h-5" />
        </slot>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BarChart3 } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    title: string
    value: string | number
    unit?: string
    subtitle?: string
    variant?: 'cyan' | 'green' | 'blue' | 'warning' | 'danger'
  }>(),
  {
    variant: 'cyan'
  }
)

const iconBoxStyle = computed(() => {
  switch (props.variant) {
    case 'green':
      return 'bg-emerald-50 border border-emerald-100 text-emerald-600 group-hover:bg-emerald-100/70'
    case 'blue':
      return 'bg-indigo-50 border border-indigo-100 text-indigo-600 group-hover:bg-indigo-100/70'
    case 'warning':
      return 'bg-amber-50 border border-amber-100 text-amber-600 group-hover:bg-amber-100/70'
    case 'danger':
      return 'bg-rose-50 border border-rose-100 text-rose-600 group-hover:bg-rose-100/70'
    case 'cyan':
    default:
      return 'bg-sky-50 border border-sky-100 text-sky-600 group-hover:bg-sky-100/70'
  }
})
</script>

