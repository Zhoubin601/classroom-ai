<template>
  <div class="min-h-screen bg-slate-50 text-slate-900 flex flex-col font-sans selection:bg-indigo-100 selection:text-indigo-900">
    <!-- 顶部模式与身份控制条 (Sprint 1 敏捷体验模式专用) -->
    <div class="bg-indigo-900 text-white px-6 py-2 text-xs flex flex-wrap items-center justify-between gap-3 shadow-inner">
      <div class="flex items-center gap-3">
        <span class="px-2 py-0.5 rounded bg-indigo-700 text-indigo-100 font-mono font-bold tracking-wide">
          Sprint 1 交付基线
        </span>
        <span class="text-indigo-200 hidden md:inline">
          东北大学软件工程《软件项目管理》实验二 · “蜂鸟”迭代团队
        </span>
        <div class="flex items-center bg-indigo-950/80 rounded-lg p-0.5 border border-indigo-700">
          <button
            @click="runMode = 'lab2'"
            :class="[
              'px-2.5 py-1 rounded text-[11px] font-medium transition',
              runMode === 'lab2' ? 'bg-indigo-600 text-white font-semibold shadow-xs' : 'text-indigo-300 hover:text-white'
            ]"
            title="隐藏硬件与摄像头依赖，专注 US-01/02/03/04/06 课程底座"
          >
            实验二·敏捷无硬件模式
          </button>
          <button
            @click="runMode = 'full'"
            :class="[
              'px-2.5 py-1 rounded text-[11px] font-medium transition',
              runMode === 'full' ? 'bg-indigo-600 text-white font-semibold shadow-xs' : 'text-indigo-300 hover:text-white'
            ]"
            title="开启完整视觉考勤与人脸感知功能"
          >
            完整视觉多媒体模式
          </button>
        </div>
      </div>

      <!-- 快速身份切换与当前鉴权状态 -->
      <div class="flex items-center gap-2">
        <span class="text-indigo-300">快速切换角色：</span>
        <button
          v-for="u in presetUsers"
          :key="u.username"
          @click="quickLogin(u.username, u.password)"
          :class="[
            'px-2 py-1 rounded text-[11px] border transition',
            currentUser?.username === u.username
              ? 'bg-emerald-600 border-emerald-400 text-white font-bold'
              : 'bg-indigo-800/80 border-indigo-700 text-indigo-200 hover:bg-indigo-700 hover:text-white'
          ]"
        >
          {{ u.label }}
        </button>
        <div v-if="currentUser" class="flex items-center gap-1.5 ml-2 pl-2 border-l border-indigo-700">
          <span class="text-emerald-300 font-semibold">[{{ currentUser.realName }}]</span>
          <span v-if="currentUser.authorizedMajors" class="text-indigo-300 text-[10px] font-mono">({{ currentUser.authorizedMajors }})</span>
          <button @click="handleLogout" class="text-rose-300 hover:text-rose-100 underline text-[11px] ml-1">登出</button>
        </div>
        <span v-else class="text-amber-300 text-[11px] ml-1">未登录(访客)</span>
      </div>
    </div>

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
          <p class="text-[11px] text-slate-500">
            {{ runMode === 'lab2' ? '实验二专属环境：课程导入(US-01) ➔ 大纲修订(US-02) ➔ 防冲突排课(US-03) ➔ 人次统计(US-04) ➔ 督导检索(US-06)' : '阶段1查课程 ➔ 阶段2善督导 ➔ 阶段3优课堂 · 全链路数字化闭环' }}
          </p>
        </div>
      </div>

      <!-- 中间：多角色工作台导航切换 Tab (受运行模式过滤) -->
      <nav class="flex items-center p-1 bg-slate-100/90 rounded-xl border border-slate-200/80">
        <button
          v-for="t in visibleNavTabs"
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
      <div class="font-medium text-slate-600">东北大学软件学院《软件项目管理》· 第二组 “爱教学”数字化平台 (Sprint 1 蜂鸟交付)</div>
      <div class="flex items-center gap-4 text-slate-500">
        <span>架构: Spring Boot 3.3 + JPA + Redis</span>
        <span>运行环境: {{ runMode === 'lab2' ? '实验二纯净底座 (免硬件)' : '完整智能硬件' }}</span>
        <span class="text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">Session认证 + CSRF防御 就绪</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  GraduationCap,
  Briefcase,
  BookOpen,
  ShieldCheck,
  Video,
  Users,
  Clock
} from 'lucide-vue-next'
import { courseApi, authApi } from './api'
import DirectorDeskView from './views/DirectorDeskView.vue'
import TeacherDeskView from './views/TeacherDeskView.vue'
import SupervisorDeskView from './views/SupervisorDeskView.vue'
import AttendanceDashboardView from './views/AttendanceDashboardView.vue'
import StudentManageView from './views/StudentManageView.vue'

type TabKey = 'director' | 'teacher' | 'supervisor' | 'attendance' | 'students'

const runMode = ref<'lab2' | 'full'>('lab2')
const activeTab = ref<TabKey>('director')
const targetOfferingId = ref<number | null>(null)
const currentUser = ref<any>(null)

const presetUsers = [
  { username: 'director', password: 'password123', label: '张教学(主任)', defaultTab: 'director' as TabKey },
  { username: 'guo.jun', password: 'password123', label: '郭军(软工老师)', defaultTab: 'teacher' as TabKey },
  { username: 'wang.wei', password: 'password123', label: '王伟(AI老师)', defaultTab: 'teacher' as TabKey },
  { username: 'supervisor.se', password: 'password123', label: '王督导(SE专业)', defaultTab: 'supervisor' as TabKey },
  { username: 'supervisor.cs', password: 'password123', label: '李督导(CS专业)', defaultTab: 'supervisor' as TabKey }
]

const handleJumpToAttendance = (offeringId: number) => {
  targetOfferingId.value = offeringId
  activeTab.value = 'attendance'
}

const allNavTabs = [
  { key: 'director' as TabKey, label: '教研室主任工作台', iconComp: Briefcase },
  { key: 'teacher' as TabKey, label: '任课教师工作台', iconComp: BookOpen },
  { key: 'supervisor' as TabKey, label: '教学督导工作台', iconComp: ShieldCheck },
  { key: 'attendance' as TabKey, label: '课堂智能考勤大屏', iconComp: Video },
  { key: 'students' as TabKey, label: '学生人脸档案库', iconComp: Users }
]

const visibleNavTabs = computed(() => {
  if (runMode.value === 'lab2') {
    return allNavTabs.filter(t => t.key === 'director' || t.key === 'teacher' || t.key === 'supervisor')
  }
  return allNavTabs
})

const quickLogin = async (username: string, password: string) => {
  try {
    const user = await authApi.login({ username, password })
    currentUser.value = user
    await authApi.getCsrf()
    const preset = presetUsers.find(p => p.username === username)
    if (preset) {
      activeTab.value = preset.defaultTab
    }
  } catch (e: any) {
    console.error('快速登录失败', e)
  }
}

const handleLogout = async () => {
  try {
    await authApi.logout()
  } catch {}
  currentUser.value = null
}

const checkAuth = async () => {
  try {
    await authApi.getCsrf()
    const me = await authApi.getMe()
    currentUser.value = me
  } catch {
    // 默认以主任身份自动登录便于免配置体验
    await quickLogin('director', 'password123')
  }
}

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
  checkAuth()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (healthTimer) clearInterval(healthTimer)
})
</script>


