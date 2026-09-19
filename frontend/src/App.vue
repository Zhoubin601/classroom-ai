<template>
  <!-- 未登录状态：展示全屏现代化教务登录界面 (从 MySQL 查验) -->
  <LoginView v-if="!currentUser" @login-success="handleLoginSuccess" />

  <!-- 已登录状态：展示平台主界面 -->
  <div v-else class="min-h-screen bg-slate-50 text-slate-900 flex flex-col font-sans selection:bg-indigo-100 selection:text-indigo-900">
    <!-- 顶部状态提示条 -->
    <div class="bg-indigo-950 text-white px-6 py-2 text-xs flex flex-wrap items-center justify-between gap-3 shadow-inner">
      <div class="flex items-center gap-3">
        <span class="text-indigo-200">
          东北大学软件学院《软件项目管理》· 课程底座数字化管理平台
        </span>
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
        </div>
      </div>

      <!-- 中间：多角色工作台导航切换 Tab -->
      <nav class="flex items-center p-1 bg-slate-100/90 rounded-xl border border-slate-200/80">
        <button
          v-for="t in visibleNavTabs"
          :key="t.key"
          @click="activeTab = t.key"
          :class="[
            'px-3.5 py-1.5 rounded-lg text-xs transition-all flex items-center gap-1.5 cursor-pointer',
            activeTab === t.key
              ? 'bg-white text-slate-900 shadow-sm font-semibold'
              : 'text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 font-medium'
          ]"
        >
          <component :is="t.iconComp" class="w-3.5 h-3.5" :class="activeTab === t.key ? 'text-indigo-600' : 'text-slate-400'" />
          {{ t.label }}
        </button>
      </nav>

      <!-- 右侧：当前登录身份、时间、服务状态与登出 -->
      <div class="flex items-center gap-3">
        <!-- 登录用户信息卡片 -->
        <div class="flex items-center gap-2 text-xs bg-slate-50 border border-slate-200 px-3 py-1.5 rounded-xl shadow-subtle">
          <div class="w-6 h-6 rounded-full bg-indigo-100 border border-indigo-200 flex items-center justify-center text-indigo-700 font-bold text-[11px]">
            {{ currentUser?.realName?.substring(0, 1) || '用' }}
          </div>
          <div class="flex items-center gap-1.5">
            <span class="font-bold text-slate-900">{{ currentUser?.realName }}</span>
            <span class="text-[10px] px-1.5 py-0.5 rounded bg-indigo-100 text-indigo-700 font-medium">
              {{ currentUser?.role === 'DIRECTOR' ? '教研室主任' : (currentUser?.role === 'TEACHER' ? '任课教师' : '教学督导') }}
            </span>
            <span v-if="currentUser?.department" class="text-slate-400 text-[11px] hidden lg:inline">({{ currentUser.department }})</span>
            <span v-if="currentUser?.authorizedMajors" class="text-amber-700 bg-amber-50 text-[10px] px-1.5 py-0.5 rounded border border-amber-200 font-mono">授权:{{ currentUser.authorizedMajors }}</span>
          </div>
          <button
            @click="handleLogout"
            class="text-rose-600 hover:text-rose-700 hover:bg-rose-50 px-2 py-0.5 rounded text-xs font-semibold ml-2 border-l border-slate-200 transition cursor-pointer"
            title="退出当前教务会话并回到登录页"
          >
            退出登录
          </button>
        </div>

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
      <DirectorDeskView v-if="activeTab === 'director' && currentUser?.role === 'DIRECTOR'" />
      <TeacherDeskView v-else-if="activeTab === 'teacher' && currentUser?.role === 'TEACHER'" :logged-in-user="currentUser" />
      <SupervisorDeskView v-else-if="activeTab === 'supervisor' && currentUser?.role === 'SUPERVISOR'" @jump-to-attendance="handleJumpToAttendance" />
      <AttendanceDashboardView v-else-if="activeTab === 'attendance'" :initial-offering-id="targetOfferingId" :logged-in-user="currentUser" />
      <StudentManageView v-else-if="activeTab === 'students' && currentUser?.role === 'DIRECTOR'" />
      <div v-else class="minimal-card p-12 text-center text-rose-600 font-bold space-y-2">
        <p class="text-base">403 权限拒绝：您当前角色 ({{ currentUser?.role }}) 无权进入该工作台</p>
        <p class="text-xs text-slate-500 font-normal">系统已启用 Spring Security + JJWT 鉴权，角色间严格物理隔离</p>
      </div>
    </main>

    <!-- 底部状态条 -->
    <footer class="border-t border-slate-200 bg-white py-3 px-6 text-xs text-slate-500 flex flex-wrap justify-between items-center">
      <div class="font-medium text-slate-600">东北大学软件学院《软件项目管理》· 第二组 “爱教学”数字化平台</div>
      <div class="flex items-center gap-4 text-slate-500">
        <span>架构: Spring Boot 3.3 + JPA + Redis + MySQL 8.0</span>
        <span class="text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200 font-medium">Spring Security + JJWT 严格鉴权就绪</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
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
import LoginView from './views/LoginView.vue'
import DirectorDeskView from './views/DirectorDeskView.vue'
import TeacherDeskView from './views/TeacherDeskView.vue'
import SupervisorDeskView from './views/SupervisorDeskView.vue'
import AttendanceDashboardView from './views/AttendanceDashboardView.vue'
import StudentManageView from './views/StudentManageView.vue'

type TabKey = 'director' | 'teacher' | 'supervisor' | 'attendance' | 'students'

const runMode = ref<'full'>('full')
const activeTab = ref<TabKey>('director')
const targetOfferingId = ref<number | null>(null)
const currentUser = ref<any>(null)

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
  if (!currentUser.value) return []
  const role = currentUser.value.role
  const tabs = allNavTabs

  // 严格按用户角色隔离工作台
  if (role === 'SUPERVISOR') {
    return tabs.filter(t => t.key === 'supervisor' || t.key === 'attendance')
  } else if (role === 'TEACHER') {
    return tabs.filter(t => t.key === 'teacher' || t.key === 'attendance')
  } else if (role === 'DIRECTOR') {
    return tabs.filter(t => t.key === 'director' || t.key === 'attendance' || t.key === 'students')
  }
  return tabs
})

// 防越权看门狗：若非法篡改 activeTab，强制拦截重置
watch(activeTab, (newTab) => {
  if (!currentUser.value) return
  const role = currentUser.value.role
  if (role === 'SUPERVISOR' && (newTab === 'teacher' || newTab === 'director' || newTab === 'students')) {
    alert('【安全拦截】教学督导专家严禁访问学生人脸档案库、任课教师或教研室主任工作台！学生档案库归教研室主任统一管辖。系统已自动重置回督导工作台。')
    activeTab.value = 'supervisor'
  } else if (role === 'TEACHER' && (newTab === 'director' || newTab === 'supervisor' || newTab === 'students')) {
    alert('【安全拦截】任课教师严禁访问学生人脸档案库、教研室主任或督导工作台！系统已自动重置回教师工作台。')
    activeTab.value = 'teacher'
  }
})

const setInitialTab = (user: any) => {
  if (!user) return
  if (user.role === 'DIRECTOR') {
    activeTab.value = 'director'
  } else if (user.role === 'TEACHER') {
    activeTab.value = 'teacher'
  } else if (user.role === 'SUPERVISOR') {
    activeTab.value = 'supervisor'
  }
}

const handleLoginSuccess = (user: any) => {
  currentUser.value = user
  setInitialTab(user)
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
    setInitialTab(me)
  } catch {
    currentUser.value = null
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
