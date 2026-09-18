<template>
  <div class="min-h-screen bg-slate-50 text-slate-900 flex flex-col font-sans selection:bg-indigo-100 selection:text-indigo-900">
    <!-- 顶部全局导航条 (极简浅色磨砂) -->
    <header class="border-b border-slate-200 bg-white/95 backdrop-blur-md sticky top-0 z-40 px-6 py-2.5 flex flex-wrap items-center justify-between gap-4 shadow-subtle">
      <!-- 左侧：系统品牌与标识 -->
      <div class="flex items-center gap-3">
        <div class="w-9 h-9 rounded-xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600 shadow-subtle">
          <GraduationCap class="w-5 h-5" />
        </div>
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-base font-bold tracking-tight text-slate-900">爱教学 · 教学质量数字化管理平台</h1>
            <span class="text-[11px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 border border-slate-200 font-medium">
              东北大学软件学院
            </span>
          </div>
          <p class="text-[11px] text-slate-500">阶段1查课程 ➔ 阶段2善督导 ➔ 阶段3优课堂 · 全链路数字化闭环</p>
        </div>
      </div>

      <!-- 中间：5 大多角色专属工作台导航切换 Tab -->
      <nav class="flex items-center p-1 bg-slate-100/90 rounded-xl border border-slate-200/80">
        <button
          v-for="t in navTabs"
          :key="t.key"
          @click="activeTab = t.key"
          :class="[
            'px-3.5 py-1.5 rounded-lg text-xs transition-all flex items-center gap-1.5',
            activeTab === t.key
              ? 'bg-white text-slate-900 shadow-sm font-semibold'
              : 'text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 font-medium'
          ]"
        >
          <component :is="t.iconComp" class="w-3.5 h-3.5" :class="activeTab === t.key ? 'text-indigo-600' : 'text-slate-400'" />
          {{ t.label }}
        </button>
      </nav>

      <!-- 右侧：时间与服务探活 -->
      <div class="flex items-center gap-2.5">
        <div class="hidden sm:flex items-center gap-1.5 text-xs font-mono px-3 py-1.5 rounded-lg bg-slate-50 border border-slate-200 text-slate-600">
          <Clock class="w-3.5 h-3.5 text-slate-400" />
          <span>{{ currentTime }}</span>
        </div>
        <div
          class="flex items-center gap-2 text-xs px-3 py-1.5 rounded-lg border font-medium"
          :class="serviceOnline ? 'bg-emerald-50 border-emerald-200 text-emerald-700' : 'bg-amber-50 border-amber-200 text-amber-700'"
        >
          <span class="w-2 h-2 rounded-full" :class="serviceOnline ? 'bg-emerald-500' : 'bg-amber-500 animate-pulse'"></span>
          <span>{{ serviceOnline === null ? '检查连接中...' : serviceOnline ? '教务中枢在线' : '后端暂未连接' }}</span>
        </div>
      </div>
    </header>

    <!-- 主体内容区 -->
    <main class="flex-1 max-w-[1680px] w-full mx-auto p-4 sm:p-6">
      <DirectorDeskView v-if="activeTab === 'director'" />
      <TeacherDeskView v-else-if="activeTab === 'teacher'" />
      <SupervisorDeskView v-else-if="activeTab === 'supervisor'" @jump-to-attendance="handleJumpToAttendance" />
      <AttendanceDashboardView v-else-if="activeTab === 'attendance'" :initial-offering-id="targetOfferingId" />
      <StudentManageView v-else-if="activeTab === 'students'" />
    </main>

    <!-- 底部状态条 -->
    <footer class="border-t border-slate-200 bg-white py-3 px-6 text-xs text-slate-500 flex flex-wrap justify-between items-center">
      <div class="font-medium text-slate-600">东北大学软件学院《软件项目管理》· 第二组 “爱教学”数字化平台</div>
      <div class="flex items-center gap-4 text-slate-500">
        <span>架构: Spring Boot 3.3 + JPA + Redis</span>
        <span>视觉引擎: InsightFace ArcFace 512D + MediaPipe</span>
        <span class="text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">合规红线: 物理隔离已就绪</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import {
  GraduationCap,
  Briefcase,
  BookOpen,
  ShieldCheck,
  Video,
  Users,
  Clock
} from 'lucide-vue-next'
import { courseApi } from './api'
import DirectorDeskView from './views/DirectorDeskView.vue'
import TeacherDeskView from './views/TeacherDeskView.vue'
import SupervisorDeskView from './views/SupervisorDeskView.vue'
import AttendanceDashboardView from './views/AttendanceDashboardView.vue'
import StudentManageView from './views/StudentManageView.vue'

type TabKey = 'director' | 'teacher' | 'supervisor' | 'attendance' | 'students'

const activeTab = ref<TabKey>('director')
const targetOfferingId = ref<number | null>(null)

const handleJumpToAttendance = (offeringId: number) => {
  targetOfferingId.value = offeringId
  activeTab.value = 'attendance'
}

const navTabs = [
  { key: 'director' as TabKey, label: '教研室主任工作台', iconComp: Briefcase },
  { key: 'teacher' as TabKey, label: '任课教师工作台', iconComp: BookOpen },
  { key: 'supervisor' as TabKey, label: '教学督导工作台', iconComp: ShieldCheck },
  { key: 'attendance' as TabKey, label: '课堂智能考勤大屏', iconComp: Video },
  { key: 'students' as TabKey, label: '学生人脸档案库', iconComp: Users }
]

const currentTime = ref('')

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour12: false })
}

let timer: number | null = null
let healthTimer: number | null = null
const serviceOnline = ref<boolean | null>(null)
const checkService = async () => {
  try {
    await courseApi.getAll()
    serviceOnline.value = true
  } catch {
    serviceOnline.value = false
  }
}

onMounted(() => {
  updateTime()
  timer = window.setInterval(updateTime, 1000)
  checkService()
  healthTimer = window.setInterval(checkService, 15000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (healthTimer) clearInterval(healthTimer)
})
</script>

