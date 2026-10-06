<template>
  <div class="pro-card p-5 relative overflow-hidden group hover:border-slate-300 transition-all duration-200">
    <!-- 右上角微妙的渐变微光氛围 -->
    <div :class="['absolute -top-10 -right-10 w-24 h-24 rounded-full blur-2xl opacity-40 transition-opacity group-hover:opacity-70 pointer-events-none', glowBgStyle]"></div>

    <div class="flex items-center justify-between relative z-10">
      <div>
        <p class="text-xs font-semibold text-slate-500 tracking-wide">{{ title }}</p>
        <div class="mt-2 flex items-baseline gap-2">
          <span class="text-2xl sm:text-3xl font-extrabold tracking-tight text-slate-900 font-mono tabular-nums">{{ value }}</span>
          <span v-if="unit" class="text-xs font-semibold text-slate-500">{{ unit }}</span>
        </div>
        <p v-if="subtitle" class="mt-1 text-xs text-slate-500 flex items-center gap-1.5 font-medium">
          <span>{{ subtitle }}</span>
        </p>
      </div>

      <div :class="['w-12 h-12 rounded-2xl flex items-center justify-center shadow-xs transition-transform group-hover:scale-105', iconBoxStyle]">
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
    variant?: 'cyan' | 'green' | 'blue' | 'warning' | 'danger' | 'purple'
  }>(),
  {
    variant: 'cyan'
  }
)

const glowBgStyle = computed(() => {
  switch (props.variant) {
    case 'purple': return 'bg-purple-400'
    case 'green': return 'bg-emerald-400'
    case 'blue': return 'bg-indigo-400'
    case 'warning': return 'bg-amber-400'
    case 'danger': return 'bg-rose-400'
    case 'cyan':
    default: return 'bg-sky-400'
  }
})

const iconBoxStyle = computed(() => {
  switch (props.variant) {
    case 'purple':
      return 'bg-purple-50 border border-purple-100/80 text-purple-600 group-hover:bg-purple-100/80'
    case 'green':
      return 'bg-emerald-50 border border-emerald-100/80 text-emerald-600 group-hover:bg-emerald-100/80'
    case 'blue':
      return 'bg-indigo-50 border border-indigo-100/80 text-indigo-600 group-hover:bg-indigo-100/80'
    case 'warning':
      return 'bg-amber-50 border border-amber-100/80 text-amber-600 group-hover:bg-amber-100/80'
    case 'danger':
      return 'bg-rose-50 border border-rose-100/80 text-rose-600 group-hover:bg-rose-100/80'
    case 'cyan':
    default:
      return 'bg-sky-50 border border-sky-100/80 text-sky-600 group-hover:bg-sky-100/80'
  }
})
</script>
