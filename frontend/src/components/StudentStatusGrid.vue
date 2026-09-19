<template>
  <div class="minimal-card p-5">
    <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
      <div class="flex items-center gap-2">
        <span class="text-base font-semibold text-slate-900">学生实时专注与在座状态矩阵</span>
        <span class="text-xs px-2.5 py-0.5 rounded-full bg-slate-100 border border-slate-200 text-slate-600 font-mono">
          本班 {{ enrolledStudentsCount }} 人 <span v-if="auditingStudentsCount > 0" class="text-purple-600 font-semibold">· 旁听 {{ auditingStudentsCount }} 人</span>
        </span>
      </div>

      <!-- 状态筛选器 (极简药丸分段器) -->
      <div class="flex items-center gap-1 text-xs bg-slate-100 p-1 rounded-lg border border-slate-200">
        <button
          v-for="tab in filterTabs"
          :key="tab.key"
          @click="activeFilter = tab.key"
          :class="[
            'px-2.5 py-1 rounded-md transition-all cursor-pointer',
            activeFilter === tab.key
              ? 'bg-white text-slate-900 font-semibold shadow-subtle'
              : 'text-slate-600 hover:text-slate-900 hover:bg-slate-200/50'
          ]"
        >
          {{ tab.label }} ({{ getFilterCount(tab.key) }})
        </button>
      </div>
    </div>

    <!-- 学生网格卡片 -->
    <div v-if="filteredStudents.length > 0" class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
      <div
        v-for="s in filteredStudents"
        :key="s.studentId"
        :class="[
          'relative p-3 rounded-xl border transition-all duration-200 flex flex-col items-center text-center shadow-subtle hover:shadow-card',
          s.isAuditing
            ? 'bg-purple-50/50 border-purple-200 hover:border-purple-300'
            : s.poseState === 'UP'
            ? 'bg-emerald-50/40 border-emerald-200 hover:border-emerald-300'
            : s.poseState === 'DOWN'
            ? 'bg-rose-50/40 border-rose-200 hover:border-rose-300'
            : 'bg-slate-50 border-slate-200 opacity-60'
        ]"
      >
        <!-- 旁听/非本班角标 -->
        <span
          v-if="s.isAuditing"
          class="absolute top-2 left-2 text-[9px] px-1.5 py-0.2 rounded bg-purple-100 text-purple-700 border border-purple-200 font-semibold"
        >
          非本班旁听
        </span>

        <!-- 头像与在线呼吸灯 -->
        <div class="relative mb-2 mt-1">
          <div
            class="w-12 h-12 rounded-full overflow-hidden border flex items-center justify-center font-bold text-sm bg-slate-100 text-slate-700"
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
              'absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-white',
              s.isAuditing ? 'bg-purple-500' : s.poseState === 'UP' ? 'bg-emerald-500' : 'bg-rose-500'
            ]"
          ></span>
        </div>

        <!-- 姓名与学号 -->
        <h4 class="text-sm font-semibold text-slate-900 truncate max-w-full">{{ s.name }}</h4>
        <span class="text-[11px] text-slate-500 font-mono mt-0.5">{{ s.studentId }}</span>

        <!-- 姿态标签徽章 -->
        <div class="mt-2 w-full space-y-1">
          <span
            v-if="s.isAuditing"
            class="inline-block w-full py-0.5 text-[10px] font-medium rounded bg-purple-100/70 text-purple-800 border border-purple-200 font-semibold"
          >
            旁听人员 ({{ s.poseState === 'UP' ? '抬头' : '低头' }})
          </span>
          <span
            v-else-if="s.poseState === 'UP'"
            class="inline-block w-full py-0.5 text-[10px] font-medium rounded bg-emerald-50 text-emerald-700 border border-emerald-200"
          >
            抬头专注 (UP)
          </span>
          <span
            v-else-if="s.poseState === 'DOWN'"
            class="inline-block w-full py-0.5 text-[10px] font-medium rounded bg-rose-50 text-rose-700 border border-rose-200 font-semibold"
          >
            低头预警 (DOWN)
          </span>
          <span
            v-else
            class="inline-block w-full py-0.5 text-[10px] font-medium rounded bg-slate-100 text-slate-500 border border-slate-200"
          >
            未出勤 (ABSENT)
          </span>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="py-12 text-center text-slate-400 text-sm">
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
