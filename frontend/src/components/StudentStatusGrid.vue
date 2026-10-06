<template>
  <div class="pro-card p-6">
    <div class="flex flex-wrap items-center justify-between gap-3 mb-5">
      <div class="flex items-center gap-2.5">
        <div class="w-2.5 h-2.5 rounded-full bg-indigo-600 ring-4 ring-indigo-50"></div>
        <span class="text-base font-bold text-slate-900 tracking-tight">学生实时专注与在座状态矩阵</span>
        <span class="text-xs px-2.5 py-0.5 rounded-full bg-slate-100 border border-slate-200 text-slate-600 font-mono font-medium">
          本班 {{ enrolledStudentsCount }} 人 <span v-if="auditingStudentsCount > 0" class="text-purple-600 font-semibold">· 旁听 {{ auditingStudentsCount }} 人</span>
        </span>
      </div>

      <!-- 状态筛选器 (现代悬浮药丸分段器) -->
      <div class="flex items-center gap-1 text-xs bg-slate-100/90 p-1.5 rounded-xl border border-slate-200/80 shadow-inner">
        <button
          v-for="tab in filterTabs"
          :key="tab.key"
          @click="activeFilter = tab.key"
          :class="[
            'px-2.5 py-1 rounded-lg transition-all cursor-pointer select-none font-medium',
            activeFilter === tab.key
              ? 'bg-white text-indigo-950 font-bold shadow-xs'
              : 'text-slate-600 hover:text-slate-900 hover:bg-slate-200/60'
          ]"
        >
          {{ tab.label }} ({{ getFilterCount(tab.key) }})
        </button>
      </div>
    </div>

    <!-- 学生网格卡片 -->
    <div v-if="filteredStudents.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3.5">
      <div
        v-for="s in filteredStudents"
        :key="s.studentId"
        :class="[
          'relative p-3.5 rounded-2xl border transition-all duration-200 flex flex-col items-center text-center shadow-subtle hover:shadow-card hover:-translate-y-0.5 group',
          s.isAuditing
            ? 'bg-purple-50/40 border-purple-200/80 hover:border-purple-300'
            : s.poseState === 'UP'
            ? 'bg-emerald-50/30 border-emerald-200/80 hover:border-emerald-300'
            : s.poseState === 'DOWN'
            ? 'bg-rose-50/40 border-rose-200/80 hover:border-rose-300'
            : 'bg-slate-50/60 border-slate-200 opacity-60'
        ]"
      >
        <!-- 旁听/非本班角标 -->
        <span
          v-if="s.isAuditing"
          class="absolute top-2 left-2 text-[9px] px-1.5 py-0.2 rounded-full bg-purple-100 text-purple-700 border border-purple-200 font-semibold"
        >
          非本班旁听
        </span>

        <!-- 头像与在线呼吸灯 -->
        <div class="relative mb-2 mt-1">
          <div
            class="w-13 h-13 rounded-full overflow-hidden border-2 flex items-center justify-center font-bold text-sm bg-slate-100 text-slate-700 shadow-2xs group-hover:scale-105 transition-transform"
            :class="s.isAuditing ? 'border-purple-400' : s.present ? 'border-indigo-400' : 'border-slate-200'"
          >
            <img
              v-if="s.avatarUrl"
              :src="s.avatarUrl"
              :alt="s.name"
              class="w-full h-full object-cover"
            />
            <span v-else>{{ s.name.charAt(0) }}</span>
          </div>

          <!-- 右下角状态圆点 -->
          <span
            v-if="s.present"
            :class="[
              'absolute bottom-0 right-0 w-3.5 h-3.5 rounded-full border-2 border-white shadow-xs',
              s.isAuditing ? 'bg-purple-500' : s.poseState === 'UP' ? 'bg-emerald-500' : 'bg-rose-500'
            ]"
          ></span>
        </div>

        <!-- 姓名与学号 -->
        <h4 class="text-xs font-bold text-slate-900 truncate max-w-full tracking-tight">{{ s.name }}</h4>
        <span class="text-[10px] text-slate-400 font-mono mt-0.5">{{ s.studentId }}</span>

        <!-- 姿态标签徽章 -->
        <div class="mt-2 w-full space-y-1">
          <span
            v-if="s.isAuditing"
            class="inline-block w-full py-0.5 text-[10px] font-semibold rounded-lg bg-purple-100/80 text-purple-800 border border-purple-200/80"
          >
            旁听 ({{ s.poseState === 'UP' ? '抬头' : '低头' }})
          </span>
          <span
            v-else-if="s.poseState === 'UP'"
            class="inline-block w-full py-0.5 text-[10px] font-semibold rounded-lg bg-emerald-50 text-emerald-700 border border-emerald-200"
          >
            抬头专注 (UP)
          </span>
          <span
            v-else-if="s.poseState === 'DOWN'"
            class="inline-block w-full py-0.5 text-[10px] font-semibold rounded-lg bg-rose-50 text-rose-700 border border-rose-200"
          >
            低头预警 (DOWN)
          </span>
          <span
            v-else
            class="inline-block w-full py-0.5 text-[10px] font-medium rounded-lg bg-slate-100 text-slate-400 border border-slate-200"
          >
            未出勤 (ABSENT)
          </span>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="py-12 text-center text-slate-400 text-xs">
      暂无符合当前筛选条件的学生记录
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { StudentRealtimeStatusVO } from '../api/types'

const props = defineProps<{
  students: StudentRealtimeStatusVO[]
}>()

const activeFilter = ref<'all' | 'present' | 'auditing' | 'up' | 'down' | 'absent'>('all')

const filterTabs = [
  { key: 'all', label: '全部名单' },
  { key: 'present', label: '本班在场' },
  { key: 'auditing', label: '非本班/旁听' },
  { key: 'up', label: '抬头专注' },
  { key: 'down', label: '低头预警' },
  { key: 'absent', label: '缺勤名单' }
] as const

const enrolledStudentsCount = computed(() => {
  return props.students.filter(s => !s.isAuditing).length
})

const auditingStudentsCount = computed(() => {
  return props.students.filter(s => s.isAuditing).length
})

const getFilterCount = (key: typeof activeFilter.value) => {
  switch (key) {
    case 'present':
      return props.students.filter(s => !s.isAuditing && s.present).length
    case 'auditing':
      return props.students.filter(s => s.isAuditing).length
    case 'up':
      return props.students.filter(s => s.poseState === 'UP').length
    case 'down':
      return props.students.filter(s => s.poseState === 'DOWN').length
    case 'absent':
      return props.students.filter(s => !s.isAuditing && (!s.present || s.poseState === 'ABSENT')).length
    case 'all':
    default:
      return props.students.length
  }
}

const filteredStudents = computed(() => {
  switch (activeFilter.value) {
    case 'present':
      return props.students.filter(s => !s.isAuditing && s.present)
    case 'auditing':
      return props.students.filter(s => s.isAuditing)
    case 'up':
      return props.students.filter(s => s.poseState === 'UP')
    case 'down':
      return props.students.filter(s => s.poseState === 'DOWN')
    case 'absent':
      return props.students.filter(s => !s.isAuditing && (!s.present || s.poseState === 'ABSENT'))
    case 'all':
    default:
      return props.students
  }
})
</script>
