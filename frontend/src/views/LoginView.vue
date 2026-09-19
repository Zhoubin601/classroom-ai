<template>
  <div class="min-h-screen bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900 flex flex-col justify-center items-center px-4 py-8 relative overflow-hidden font-sans select-none">
    <!-- 背景光效装饰 -->
    <div class="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] h-[700px] bg-indigo-500/10 rounded-full blur-3xl pointer-events-none"></div>
    <div class="absolute -bottom-32 -left-32 w-96 h-96 bg-blue-500/10 rounded-full blur-2xl pointer-events-none"></div>
    <div class="absolute top-10 right-10 w-80 h-80 bg-purple-500/10 rounded-full blur-3xl pointer-events-none"></div>

    <div :class="['w-full relative z-10 space-y-5 transition-all duration-300', showQuickLogin ? 'max-w-7xl' : 'max-w-md']">
      <!-- 平台品牌徽标与主标题 -->
      <div class="text-center space-y-2">
        <div class="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-indigo-600 text-white shadow-xl shadow-indigo-600/30 border border-indigo-400/30 mb-1">
          <GraduationCap class="w-8 h-8" />
        </div>
        <h1 class="text-2xl font-bold tracking-tight text-white flex items-center justify-center gap-2">
          <span>爱教学 · 数字化教学质量管理平台</span>
        </h1>
        <div class="flex items-center justify-center gap-2">
          <span class="text-xs px-2.5 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 font-medium">
            东北大学软件学院
          </span>
          <span class="text-xs text-indigo-200/70">
            JWT + Spring Security 敏捷鉴权系统
          </span>
        </div>
      </div>

      <!-- 快捷登录通道开关栏 -->
      <div class="flex flex-wrap items-center justify-between gap-3 bg-slate-800/80 backdrop-blur-md border border-slate-700/70 px-5 py-3 rounded-2xl shadow-xl">
        <div class="flex items-center gap-2.5">
          <div class="w-7 h-7 rounded-lg bg-amber-500/20 border border-amber-500/30 flex items-center justify-center text-amber-400">
            <Sparkles class="w-4 h-4" />
          </div>
          <div>
            <div class="flex items-center gap-2">
              <span class="text-xs font-bold text-white tracking-wide">全系教师与督导快捷体验通道</span>
              <span class="text-[10px] px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 font-mono">
                8位专任教师 + 2位管理专家
              </span>
            </div>
            <p class="text-[11px] text-slate-400 mt-0.5 hidden sm:block">
              显式呈现全系8位主讲教师多课程与同一时段并行排课，支持一键免密直登工作台或回填表单
            </p>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <span class="text-xs font-medium" :class="showQuickLogin ? 'text-indigo-400' : 'text-slate-400'">
            {{ showQuickLogin ? '快捷面板：已开启' : '快捷面板：已折叠' }}
          </span>
          <button
            type="button"
            @click="toggleQuickLogin"
            :title="showQuickLogin ? '点击隐藏快捷卡片面板' : '点击展开全量教师快捷登录面板'"
            class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none"
            :class="showQuickLogin ? 'bg-indigo-600' : 'bg-slate-600'"
          >
            <span
              class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-lg ring-0 transition duration-200 ease-in-out"
              :class="showQuickLogin ? 'translate-x-5' : 'translate-x-0'"
            />
          </button>
        </div>
      </div>

      <!-- 主区域：双栏 (开启快捷通道时) 或 单栏居中 (折叠时) -->
      <div :class="['transition-all duration-300', showQuickLogin ? 'grid grid-cols-1 lg:grid-cols-12 gap-6 items-start' : 'max-w-md mx-auto']">
        
        <!-- 左侧 / 居中：登录 / 督导注册卡片 -->
        <div :class="[showQuickLogin ? 'lg:col-span-4' : 'w-full']" class="bg-white/95 backdrop-blur-md rounded-2xl p-6 shadow-2xl border border-white/20 space-y-5 text-slate-800">
          <!-- 顶部 Tab 切换：账号登录 VS 督导专家在线注册 -->
          <div class="flex items-center p-1 bg-slate-100 rounded-xl border border-slate-200">
            <button
              type="button"
              @click="activeMode = 'login'; errorMessage = ''"
              :class="[
                'flex-1 py-1.5 text-xs font-semibold rounded-lg transition-all text-center cursor-pointer',
                activeMode === 'login' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500 hover:text-slate-800'
              ]"
            >
              教务账号登录
            </button>
            <button
              type="button"
              @click="activeMode = 'register'; errorMessage = ''"
              :class="[
                'flex-1 py-1.5 text-xs font-semibold rounded-lg transition-all text-center cursor-pointer flex items-center justify-center gap-1',
                activeMode === 'register' ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500 hover:text-slate-800'
              ]"
            >
              <ShieldCheck class="w-3.5 h-3.5 text-indigo-600" />
              督导专家注册
            </button>
          </div>

          <div class="border-b border-slate-100 pb-3 flex items-center justify-between">
            <div>
              <h2 class="text-base font-bold text-slate-900">
                {{ activeMode === 'login' ? '教务身份认证' : '督导专家线上建档注册' }}
              </h2>
              <p class="text-xs text-slate-500 mt-0.5">
                {{ activeMode === 'login' ? '支持教师、教研室主任及督导登录' : '自主注册后通过 JWT 授权直入督导工作台' }}
              </p>
            </div>
            <span class="text-[11px] px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200 font-mono font-medium">
              JWT + Security
            </span>
          </div>

          <!-- 快速填入成功提示气泡 -->
          <div v-if="fillNotice" class="p-2.5 rounded-xl bg-indigo-50 border border-indigo-200 text-indigo-700 text-xs flex items-center justify-between animate-pulse">
            <div class="flex items-center gap-1.5">
              <Check class="w-3.5 h-3.5 text-indigo-600 shrink-0" />
              <span>{{ fillNotice }}</span>
            </div>
            <button @click="fillNotice = ''" class="text-indigo-400 hover:text-indigo-600 text-xs">✕</button>
          </div>

          <!-- 错误提示 -->
          <div v-if="errorMessage" class="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2 animate-shake">
            <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
            <span>{{ errorMessage }}</span>
          </div>

          <!-- 1. 登录表单 -->
          <form v-if="activeMode === 'login'" @submit.prevent="handleLogin" class="space-y-4 text-xs">
            <div>
              <label class="block font-semibold text-slate-700 mb-1.5">登录账号 (Username)</label>
              <div class="relative">
                <input
                  v-model="loginForm.username"
                  type="text"
                  required
                  autocomplete="username"
                  placeholder="如 guojun, director, supervisor 等"
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl pl-9 pr-3 py-2.5 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition shadow-inner"
                />
                <User class="w-4 h-4 text-slate-400 absolute left-3 top-3" />
              </div>
            </div>

            <div>
              <label class="block font-semibold text-slate-700 mb-1.5">登录密码 (Password)</label>
              <div class="relative">
                <input
                  v-model="loginForm.password"
                  :type="showPassword ? 'text' : 'password'"
                  required
                  autocomplete="current-password"
                  placeholder="请输入登录密码 (默认 123456)"
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl pl-9 pr-10 py-2.5 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition shadow-inner"
                />
                <Lock class="w-4 h-4 text-slate-400 absolute left-3 top-3" />
                <button
                  type="button"
                  @click="showPassword = !showPassword"
                  class="absolute right-3 top-2.5 text-slate-400 hover:text-slate-600 text-xs cursor-pointer"
                >
                  {{ showPassword ? '隐藏' : '显示' }}
                </button>
              </div>
            </div>

            <button
              type="submit"
              :disabled="loading"
              class="w-full py-2.5 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 text-white font-semibold text-xs shadow-lg shadow-indigo-600/30 transition flex items-center justify-center gap-2 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
            >
              <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
              <span v-else>立即验证并登录</span>
              <ArrowRight v-if="!loading" class="w-3.5 h-3.5" />
            </button>
          </form>

          <!-- 2. 督导注册表单 -->
          <form v-else @submit.prevent="handleSupervisorRegister" class="space-y-3.5 text-xs">
            <div>
              <label class="block font-semibold text-slate-700 mb-1">督导登录账号 (Username, 唯一)</label>
              <div class="relative">
                <input
                  v-model="registerForm.username"
                  type="text"
                  required
                  placeholder="如 supervisor_chen"
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl pl-9 pr-3 py-2 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
                />
                <User class="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              </div>
            </div>

            <div>
              <label class="block font-semibold text-slate-700 mb-1">督导专家真实姓名 (Real Name)</label>
              <div class="relative">
                <input
                  v-model="registerForm.realName"
                  type="text"
                  required
                  placeholder="如 陈建国 (督导专家)"
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl pl-9 pr-3 py-2 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
                />
                <BadgeCheck class="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              </div>
            </div>

            <div class="grid grid-cols-2 gap-2.5">
              <div>
                <label class="block font-semibold text-slate-700 mb-1">登录密码</label>
                <input
                  v-model="registerForm.password"
                  type="password"
                  required
                  placeholder="至少6位密码"
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
                />
              </div>
              <div>
                <label class="block font-semibold text-slate-700 mb-1">确认密码</label>
                <input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  required
                  placeholder="重复输入密码"
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
                />
              </div>
            </div>

            <div>
              <label class="block font-semibold text-slate-700 mb-1">所属督导组 / 单位</label>
              <input
                v-model="registerForm.department"
                placeholder="校教学质量监控与督导评估中心"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
              />
            </div>

            <div>
              <label class="block font-semibold text-slate-700 mb-1">授权监督专业代码 (分号分隔)</label>
              <input
                v-model="registerForm.authorizedMajors"
                placeholder="SE;CS (软件工程与计算机科学)"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner font-mono"
              />
            </div>

            <div class="p-2.5 bg-indigo-50 border border-indigo-200 rounded-xl text-indigo-800 text-[11px] leading-relaxed">
              <b>权限隔离原则：</b> 注册后授予 <code>SUPERVISOR</code> 角色，直入督导工作台，具备全院总课表查阅与打分权，与任课教师大纲维护相互隔离。
            </div>

            <button
              type="submit"
              :disabled="loading"
              class="w-full py-2.5 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white font-semibold text-xs shadow-lg shadow-emerald-600/30 transition flex items-center justify-center gap-2 cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
            >
              <span v-if="loading" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
              <span v-else>立即完成注册并进入督导工作台</span>
              <ShieldCheck v-if="!loading" class="w-3.5 h-3.5" />
            </button>
          </form>

          <!-- 折叠状态下的微型提示 -->
          <div v-if="!showQuickLogin && activeMode === 'login'" class="pt-3 border-t border-slate-100 text-center">
            <button
              type="button"
              @click="toggleQuickLogin"
              class="text-xs text-indigo-600 hover:text-indigo-800 font-medium inline-flex items-center gap-1 cursor-pointer"
            >
              <Sparkles class="w-3.5 h-3.5 text-amber-500" />
              展开 8 位教师与督导一键快捷登录面板 ➔
            </button>
          </div>
        </div>

        <!-- 右侧：全系专任教师与督导一键直登矩阵面板 (开启时展示) -->
        <div v-if="showQuickLogin" class="lg:col-span-8 bg-white/95 backdrop-blur-md rounded-2xl p-6 shadow-2xl border border-white/20 space-y-4 text-slate-800">
          <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
            <div>
              <div class="flex items-center gap-2">
                <div class="w-7 h-7 rounded-lg bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600">
                  <Building2 class="w-4 h-4" />
                </div>
                <h3 class="text-base font-bold text-slate-900">
                  全系专任教师 & 教学管理快捷登录矩阵
                </h3>
              </div>
              <p class="text-xs text-slate-500 mt-0.5">
                点击【一键直登】即刻以该身份认证并跳转对应工作台；点击【填入】可回填左侧表单
              </p>
            </div>

            <!-- 分类过滤 Tab -->
            <div class="flex items-center p-1 bg-slate-100 rounded-xl border border-slate-200 text-xs">
              <button
                v-for="cat in categoryTabs"
                :key="cat.key"
                type="button"
                @click="selectedCategory = cat.key"
                :class="[
                  'px-2.5 py-1 rounded-lg font-medium transition cursor-pointer',
                  selectedCategory === cat.key ? 'bg-white text-indigo-700 shadow-xs font-semibold' : 'text-slate-600 hover:text-slate-900'
                ]"
              >
                {{ cat.label }} ({{ cat.count }})
              </button>
            </div>
          </div>

          <!-- 教师与管理人员卡片网格 -->
          <div class="grid grid-cols-1 md:grid-cols-2 gap-3.5 max-h-[580px] overflow-y-auto pr-1">
            <div
              v-for="acc in filteredAccounts"
              :key="acc.username"
              class="rounded-xl border p-3.5 transition-all hover:shadow-md flex flex-col justify-between space-y-3"
              :class="[
                acc.role === 'DIRECTOR'
                  ? 'bg-amber-50/60 border-amber-200 hover:border-amber-300'
                  : acc.role === 'SUPERVISOR'
                  ? 'bg-emerald-50/60 border-emerald-200 hover:border-emerald-300'
                  : 'bg-slate-50/80 border-slate-200 hover:border-indigo-300 hover:bg-indigo-50/30'
              ]"
            >
              <!-- 顶部身份信息 -->
              <div>
                <div class="flex items-start justify-between gap-2">
                  <div class="flex items-center gap-2.5">
                    <div
                      class="w-9 h-9 rounded-xl flex items-center justify-center font-bold text-sm shadow-xs"
                      :class="acc.avatarStyle"
                    >
                      {{ acc.name.substring(0, 1) }}
                    </div>
                    <div>
                      <div class="flex items-center gap-1.5">
                        <span class="font-bold text-sm text-slate-900">{{ acc.name }}</span>
                        <span class="text-[10px] px-1.5 py-0.2 rounded font-medium border" :class="acc.tagStyle">
                          {{ acc.title }}
                        </span>
                      </div>
                      <span class="text-[11px] text-slate-500 block">{{ acc.department }}</span>
                    </div>
                  </div>

                  <!-- 账号凭证微标 -->
                  <div class="text-right font-mono text-[11px] text-slate-400 bg-white/80 border border-slate-200 px-2 py-0.5 rounded-lg">
                    <span>账号: <b class="text-slate-700">{{ acc.username }}</b></span>
                  </div>
                </div>

                <!-- 并行排课高亮标注 -->
                <div v-if="acc.parallelNote" class="mt-2 text-[10px] px-2 py-0.5 rounded bg-purple-50 text-purple-700 border border-purple-200 flex items-center gap-1 font-medium">
                  <Clock class="w-3 h-3 text-purple-600 shrink-0" />
                  <span>{{ acc.parallelNote }}</span>
                </div>

                <!-- 负责主讲课程列表 (每位老师2门课) -->
                <div v-if="acc.courses && acc.courses.length > 0" class="mt-2.5 space-y-1.5">
                  <div class="text-[10px] font-semibold text-slate-500 flex items-center gap-1">
                    <BookOpen class="w-3 h-3 text-indigo-500" />
                    主讲课程与排课时段 ({{ acc.courses.length }}门)：
                  </div>
                  <div
                    v-for="(c, cIdx) in acc.courses"
                    :key="cIdx"
                    class="bg-white/90 border border-slate-200/80 rounded-lg p-1.5 text-[11px] space-y-0.5 shadow-2xs"
                  >
                    <div class="flex items-center justify-between font-semibold text-slate-800">
                      <span>{{ c.name }}</span>
                      <span class="font-mono text-[10px] text-indigo-600 bg-indigo-50 px-1 rounded">{{ c.code }}</span>
                    </div>
                    <div class="text-[10px] text-slate-500 flex items-center justify-between">
                      <span class="flex items-center gap-1 text-slate-600">
                        <Clock class="w-2.5 h-2.5 text-slate-400" /> {{ c.time }}
                      </span>
                      <span class="flex items-center gap-1 text-slate-600">
                        <MapPin class="w-2.5 h-2.5 text-slate-400" /> {{ c.classroom }}
                      </span>
                    </div>
                    <div class="text-[10px] text-slate-400 flex items-center gap-1">
                      <Users class="w-2.5 h-2.5 text-slate-400" /> {{ c.className }}
                    </div>
                  </div>
                </div>

                <!-- 管理人员职责描述 -->
                <div v-if="acc.desc" class="mt-2 text-[11px] text-slate-600 leading-relaxed bg-white/70 p-2 rounded-lg border border-slate-200/60">
                  {{ acc.desc }}
                </div>
              </div>

              <!-- 底部操作按钮 -->
              <div class="flex items-center gap-2 pt-2 border-t border-slate-200/60">
                <button
                  type="button"
                  @click="quickLogin(acc.username, acc.password)"
                  :disabled="loading && loggingInUser === acc.username"
                  class="flex-1 py-1.5 px-3 rounded-lg text-xs font-semibold text-white shadow-xs transition flex items-center justify-center gap-1 cursor-pointer disabled:opacity-60"
                  :class="[
                    acc.role === 'DIRECTOR'
                      ? 'bg-amber-600 hover:bg-amber-700'
                      : acc.role === 'SUPERVISOR'
                      ? 'bg-emerald-600 hover:bg-emerald-700'
                      : 'bg-indigo-600 hover:bg-indigo-700'
                  ]"
                >
                  <span v-if="loading && loggingInUser === acc.username" class="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
                  <Zap v-else class="w-3.5 h-3.5" />
                  <span>一键直登</span>
                </button>

                <button
                  type="button"
                  @click="fillAccount(acc.username, acc.password, acc.name)"
                  class="py-1.5 px-3 rounded-lg text-xs font-medium text-slate-700 bg-white hover:bg-slate-100 border border-slate-200 transition cursor-pointer"
                  title="回填此账号至左侧登录框"
                >
                  填入
                </button>
              </div>
            </div>
          </div>
        </div>

      </div>

      <!-- 底部版权与架构提示 -->
      <div class="text-center text-[11px] text-indigo-200/60 space-y-1 pt-2">
        <p>东北大学软件学院《软件项目管理》· 第二组 “爱教学” 研发团队</p>
        <p class="font-mono text-[10px]">Spring Boot 3.3 + Spring Security + JJWT 0.12.5 + MySQL 8.0 (8个班级/80名学生)</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  GraduationCap,
  User,
  Lock,
  ArrowRight,
  AlertCircle,
  Sparkles,
  ShieldCheck,
  BadgeCheck,
  Zap,
  BookOpen,
  Clock,
  Building2,
  MapPin,
  Users,
  Check
} from 'lucide-vue-next'
import { authApi } from '../api'

const emit = defineEmits<{
  (e: 'login-success', user: any): void
}>()

const activeMode = ref<'login' | 'register'>('login')

// 快捷登录面板开关，从 localStorage 读取持久化配置，默认展开 (true)
const showQuickLogin = ref(localStorage.getItem('classroom_show_quick_login') !== 'false')
const toggleQuickLogin = () => {
  showQuickLogin.value = !showQuickLogin.value
  localStorage.setItem('classroom_show_quick_login', String(showQuickLogin.value))
}

const loginForm = ref({
  username: '',
  password: ''
})

const registerForm = ref({
  username: '',
  realName: '',
  password: '',
  confirmPassword: '',
  department: '校教学质量监控与督导评估中心',
  authorizedMajors: 'SE;CS'
})

const showPassword = ref(false)
const loading = ref(false)
const loggingInUser = ref('')
const errorMessage = ref('')
const fillNotice = ref('')

// 分类筛选器
const selectedCategory = ref<'ALL' | 'TEACHER' | 'ADMIN'>('ALL')

interface QuickAccount {
  username: string
  password: string
  name: string
  title: string
  department: string
  role: 'TEACHER' | 'DIRECTOR' | 'SUPERVISOR'
  avatarStyle: string
  tagStyle: string
  parallelNote?: string
  courses?: {
    code: string
    name: string
    time: string
    classroom: string
    className: string
  }[]
  desc?: string
}

// 8 位任课教师 + 2 位管理人员 (李主任、张督导)
const quickAccounts: QuickAccount[] = [
  {
    username: 'guojun',
    password: '123456',
    name: '郭军',
    title: '教授 / 专任教师',
    department: '软件工程教研室',
    role: 'TEACHER',
    avatarStyle: 'bg-indigo-100 text-indigo-700 border border-indigo-200',
    tagStyle: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    parallelNote: '周三第3-4节与董晓梅、刘博并行授课',
    courses: [
      { code: 'CS3001', name: '软件项目管理', time: '周三 第3-4节', classroom: '文管 A447', className: '软件工程2024级2班' },
      { code: 'CS1001', name: '软件工程导论', time: '周一 第5-6节', classroom: '文管 B210', className: '计算机科学与技术2024级2班' },
      { code: 'CS4002', name: '敏捷软件工程实训', time: '周五 第3-4节', classroom: '创新工场 101', className: '软件工程2024级1班' }
    ]
  },
  {
    username: 'jiangly',
    password: '123456',
    name: '姜琳颖',
    title: '副教授 / 专任教师',
    department: '计算机系统结构教研室',
    role: 'TEACHER',
    avatarStyle: 'bg-sky-100 text-sky-700 border border-sky-200',
    tagStyle: 'bg-sky-50 text-sky-700 border-sky-200',
    parallelNote: '周二第1-2节与陈立新并行授课',
    courses: [
      { code: 'CS2002', name: '计算机组成原理', time: '周二 第1-2节', classroom: '信息馆 B201', className: '计算机科学与技术2024级1班' },
      { code: 'CS2003', name: '数字逻辑与系统设计', time: '周四 第1-2节', classroom: '信息馆 A305', className: '人工智能2024级1班' }
    ]
  },
  {
    username: 'zhaogs',
    password: '123456',
    name: '赵广生',
    title: '讲师 / 专任教师',
    department: '基础软件教研室',
    role: 'TEACHER',
    avatarStyle: 'bg-emerald-100 text-emerald-700 border border-emerald-200',
    tagStyle: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    courses: [
      { code: 'CS2001', name: '数据结构与算法', time: '周一 第3-4节', classroom: '知行楼 201', className: '计算机科学与技术2024级2班' },
      { code: 'CS1002', name: 'C++高级程序设计', time: '周四 第3-4节', classroom: '机房 302', className: '软件工程2024级2班' }
    ]
  },
  {
    username: 'wangwei',
    password: '123456',
    name: '王伟',
    title: '副教授 / 专任教师',
    department: '系统软件教研室',
    role: 'TEACHER',
    avatarStyle: 'bg-blue-100 text-blue-700 border border-blue-200',
    tagStyle: 'bg-blue-50 text-blue-700 border-blue-200',
    courses: [
      { code: 'CS3002', name: '操作系统原理', time: '周四 第5-6节', classroom: '信息馆 405', className: '人工智能2024级2班' },
      { code: 'CS3008', name: '嵌入式Linux系统', time: '周二 第7-8节', classroom: '研创楼 203', className: '信息安全2024级1班' }
    ]
  },
  {
    username: 'dongxm',
    password: '123456',
    name: '董晓梅',
    title: '副教授 / 专任教师',
    department: '人工智能教研室',
    role: 'TEACHER',
    avatarStyle: 'bg-fuchsia-100 text-fuchsia-700 border border-fuchsia-200',
    tagStyle: 'bg-fuchsia-50 text-fuchsia-700 border-fuchsia-200',
    parallelNote: '周三第3-4节与郭军、刘博并行授课',
    courses: [
      { code: 'AI3001', name: '人工智能导论', time: '周三 第3-4节', classroom: '信息馆 B102', className: '人工智能2024级1班' },
      { code: 'AI3002', name: '机器学习与模式识别', time: '周五 第5-6节', classroom: '信息馆 C402', className: '数据科学与大数据技术2024级1班' }
    ]
  },
  {
    username: 'liubo',
    password: '123456',
    name: '刘博',
    title: '副教授 / 专任教师',
    department: '软件工程教研室',
    role: 'TEACHER',
    avatarStyle: 'bg-violet-100 text-violet-700 border border-violet-200',
    tagStyle: 'bg-violet-50 text-violet-700 border-violet-200',
    parallelNote: '周三第3-4节与郭军、董晓梅并行授课',
    courses: [
      { code: 'SE3002', name: '敏捷开发与人机协同', time: '周三 第3-4节', classroom: '知行楼 302', className: '软件工程2024级1班' },
      { code: 'SE3003', name: 'DevOps与持续交付', time: '周二 第3-4节', classroom: '创客空间 201', className: '软件工程2024级2班' }
    ]
  },
  {
    username: 'chenlx',
    password: '123456',
    name: '陈立新',
    title: '教授 / 专任教师',
    department: '网络空间安全教研室',
    role: 'TEACHER',
    avatarStyle: 'bg-rose-100 text-rose-700 border border-rose-200',
    tagStyle: 'bg-rose-50 text-rose-700 border-rose-200',
    parallelNote: '周二第1-2节与姜琳颖并行授课',
    courses: [
      { code: 'CS3003', name: '计算机网络与安全', time: '周二 第1-2节', classroom: '信息馆 A208', className: '信息安全2024级1班' },
      { code: 'SEC3001', name: '信息安全攻防实践', time: '周四 第7-8节', classroom: '网安靶场 102', className: '计算机科学与技术2024级1班' }
    ]
  },
  {
    username: 'sunzg',
    password: '123456',
    name: '孙志刚',
    title: '讲师 / 专任教师',
    department: '数据科学教研室',
    role: 'TEACHER',
    avatarStyle: 'bg-amber-100 text-amber-700 border border-amber-200',
    tagStyle: 'bg-amber-50 text-amber-700 border-amber-200',
    courses: [
      { code: 'DS2001', name: '数据库系统实现', time: '周五 第1-2节', classroom: '数理馆 103', className: '数据科学与大数据技术2024级1班' },
      { code: 'DS3001', name: '分布式大数据计算', time: '周三 第7-8节', classroom: '智算中心 204', className: '人工智能2024级2班' }
    ]
  },
  {
    username: 'director',
    password: '123456',
    name: '李主任',
    title: '教研室主任',
    department: '软件工程教研室',
    role: 'DIRECTOR',
    avatarStyle: 'bg-amber-100 text-amber-800 border border-amber-300',
    tagStyle: 'bg-amber-100 text-amber-800 border-amber-300',
    desc: '统管全系培养方案审核、排课防冲突统筹、工程教育认证12项指标点达成度矩阵监管。'
  },
  {
    username: 'supervisor',
    password: '123456',
    name: '张督导',
    title: '校教学督导专家',
    department: '校教学质量监控与督导评估中心',
    role: 'SUPERVISOR',
    avatarStyle: 'bg-emerald-100 text-emerald-800 border border-emerald-300',
    tagStyle: 'bg-emerald-100 text-emerald-800 border-emerald-300',
    desc: '负责全院课表检索、深入随堂听课 (BOPPPS四维评价)、教学质量红黄预警、课件免密抽检。'
  }
]

const categoryTabs = computed(() => [
  { key: 'ALL' as const, label: '全部账号', count: quickAccounts.length },
  { key: 'TEACHER' as const, label: '专任教师', count: quickAccounts.filter(a => a.role === 'TEACHER').length },
  { key: 'ADMIN' as const, label: '管理与督导', count: quickAccounts.filter(a => a.role !== 'TEACHER').length }
])

const filteredAccounts = computed(() => {
  if (selectedCategory.value === 'ALL') return quickAccounts
  if (selectedCategory.value === 'TEACHER') return quickAccounts.filter(a => a.role === 'TEACHER')
  return quickAccounts.filter(a => a.role !== 'TEACHER')
})

const fillAccount = (u: string, p: string, name?: string) => {
  activeMode.value = 'login'
  loginForm.value.username = u
  loginForm.value.password = p
  errorMessage.value = ''
  fillNotice.value = name ? `已填入【${name}】的登录凭证 (${u})` : `已填入账号 (${u})`
  setTimeout(() => {
    fillNotice.value = ''
  }, 4000)
}

const quickLogin = async (u: string, p: string) => {
  activeMode.value = 'login'
  loginForm.value.username = u
  loginForm.value.password = p
  loggingInUser.value = u
  await handleLogin()
}

const handleLogin = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const user = await authApi.login({
      username: loginForm.value.username.trim(),
      password: loginForm.value.password.trim()
    })
    emit('login-success', user)
  } catch (err: any) {
    const msg = err.response?.data?.message || err.message || '登录失败，请检查用户名和密码'
    errorMessage.value = msg
  } finally {
    loading.value = false
    loggingInUser.value = ''
  }
}

const handleSupervisorRegister = async () => {
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致，请核对'
    return
  }
  if (registerForm.value.password.length < 6) {
    errorMessage.value = '密码长度不能少于6位'
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const user = await authApi.registerSupervisor({
      username: registerForm.value.username.trim(),
      password: registerForm.value.password.trim(),
      realName: registerForm.value.realName.trim(),
      department: registerForm.value.department.trim(),
      authorizedMajors: registerForm.value.authorizedMajors.trim()
    })
    emit('login-success', user)
  } catch (err: any) {
    const msg = err.response?.data?.message || err.message || '督导注册失败'
    errorMessage.value = msg
  } finally {
    loading.value = false
  }
}
</script>
