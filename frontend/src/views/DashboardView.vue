<template>
  <div class="space-y-6">
    <!-- 顶部状态栏与视觉督导控制器 -->
    <div class="pro-card p-6 bg-gradient-to-r from-white via-slate-50/60 to-indigo-50/20 border border-slate-200/80 shadow-card-hover rounded-2xl flex flex-wrap items-center justify-between gap-5">
      <div class="flex items-center gap-4">
        <div class="w-13 h-13 rounded-2xl bg-gradient-to-br from-indigo-500 to-navy-700 flex items-center justify-center text-white shadow-md shadow-indigo-500/20 ring-4 ring-indigo-50">
          <Video class="w-6 h-6" />
        </div>
        <div>
          <div class="flex items-center gap-3">
            <span class="text-xl font-bold tracking-tight text-slate-900">课堂实时感知与视觉督导推断流</span>
            <span
              v-if="isMonitoring"
              class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200/80 shadow-2xs"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
              摄像头实时督导中
            </span>
            <span
              v-else-if="isSimulating"
              class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200/80 shadow-2xs"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse"></span>
              演示模拟流
            </span>
            <span
              v-else
              class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-600 border border-slate-200"
            >
              <span class="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
              待机就绪
            </span>
          </div>
          <p class="text-xs text-slate-500 font-sans mt-1">
            推断引擎: InsightFace + MediaPipe (solvePnP) | 目标硬件: RTX 4060 / Jetson | 最近上报: {{ overview.lastUpdateTime || '等待数据' }}
          </p>
        </div>
      </div>

      <!-- 快捷操作与督导监控控制 -->
      <div class="flex items-center gap-3">
        <!-- 核心按钮：启动/停止真实摄像头视觉督导 -->
        <button
          @click="toggleMonitor"
          :disabled="isMonitorStarting"
          :class="[
            'px-4 py-2 rounded-xl text-xs font-semibold flex items-center gap-2 transition shadow-xs cursor-pointer',
            isMonitoring
              ? 'bg-rose-50 text-rose-700 border border-rose-200 hover:bg-rose-100'
              : 'bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-700 hover:to-indigo-800 text-white shadow-indigo-500/20'
          ]"
        >
          <span v-if="isMonitorStarting" class="w-3.5 h-3.5 rounded-full border-2 border-current border-t-transparent animate-spin"></span>
          <Square v-else-if="isMonitoring" class="w-3.5 h-3.5" />
          <Camera v-else class="w-3.5 h-3.5" />
          {{ isMonitorStarting ? '正在拉起推断引擎...' : isMonitoring ? '停止视觉督导监控' : '开始智能视觉督导 (启动监控)' }}
        </button>

        <!-- 备用按钮：模拟推流 -->
        <button
          @click="toggleSimulation"
          :disabled="isMonitoring"
          :class="[
            'px-3.5 py-2 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition border shadow-2xs cursor-pointer',
            isSimulating
              ? 'bg-amber-50 text-amber-800 border-amber-200'
              : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-50 disabled:opacity-40'
          ]"
          title="脱离摄像头时自动模拟班级学生姿态流动"
        >
          <span class="inline-block w-2 h-2 rounded-full" :class="isSimulating ? 'bg-amber-500 animate-pulse' : 'bg-slate-400'"></span>
          {{ isSimulating ? '暂停模拟流' : '模拟推流 (无摄像头)' }}
        </button>

        <button
          @click="fetchDashboardData"
          class="px-3.5 py-2 rounded-xl text-xs font-semibold bg-white hover:bg-slate-50 text-slate-700 border border-slate-200 flex items-center gap-1.5 transition shadow-2xs cursor-pointer"
        >
          <RefreshCw class="w-3.5 h-3.5 text-slate-500" />
          刷新
        </button>
      </div>
    </div>

    <!-- 督导启动/停止状态提示条 -->
    <div v-if="monitorToastMsg" class="p-4 rounded-2xl bg-indigo-50/80 border border-indigo-200 text-indigo-950 text-xs flex items-center justify-between shadow-2xs">
      <div class="flex items-center gap-2.5">
        <Sparkles class="w-4 h-4 text-indigo-600 flex-shrink-0" />
        <span class="font-bold">{{ monitorToastMsg }}</span>
      </div>
      <span class="text-[11px] text-indigo-700 font-semibold font-mono">大屏数据每 1.2 秒实时自动更新</span>
    </div>

    <!-- 1. 核心大盘指标卡片 -->
    <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
      <MetricCard
        title="实到人数"
        :value="overview.currentPresent"
        unit="人"
        :subtitle="`出勤率 ${overview.attendanceRate}%`"
        variant="blue"
      />
      <MetricCard
        title="应到总数"
        :value="overview.totalRegistered"
        unit="人"
        subtitle="班级底库建档总数"
        variant="cyan"
      />
      <MetricCard
        title="实时抬头率"
        :value="overview.realtimeLookupRate"
        unit="%"
        :subtitle="overview.focusLevel"
        variant="green"
      />
      <MetricCard
        title="低头预警"
        :value="overview.lookdownCount"
        unit="人"
        subtitle="Pitch 姿态 < -10°"
        :variant="overview.lookdownCount > 0 ? 'danger' : 'green'"
      />
      <MetricCard
        title="缺勤人数"
        :value="overview.currentAbsent"
        unit="人"
        subtitle="未识别人脸学生"
        variant="warning"
      />
      <MetricCard
        title="专注度评级"
        :value="overview.focusLevel.split(' ')[0]"
        subtitle="基于时序加权评估"
        variant="cyan"
      />
    </div>

    <!-- 2. 机器人实时视觉推断感知流 + ECharts 抬头率与注意力流动波形图 -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-5">
      <!-- 左侧：实时摄像头 AI 感知推断视窗 (占 5 列) -->
      <div class="lg:col-span-5 pro-card p-5 flex flex-col justify-between h-[390px] relative overflow-hidden">
        <div class="flex items-center justify-between mb-3">
          <div class="flex items-center gap-2">
            <div class="w-6 h-6 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <Bot class="w-3.5 h-3.5" />
            </div>
            <h3 class="text-xs font-bold text-slate-900 tracking-wide">具身智能机器人·摄像头感知视窗</h3>
          </div>
          <span v-if="isMonitoring" class="text-[10px] px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-mono font-bold shadow-2xs">
            LIVE 实时推断
          </span>
          <span v-else class="text-[10px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 border border-slate-200 font-medium">
            待机中
          </span>
        </div>

        <!-- 监控开启时的实时画面 -->
        <div class="flex-1 w-full bg-slate-950 rounded-xl overflow-hidden relative flex items-center justify-center border border-slate-900 shadow-inner">
          <img
            v-if="isMonitoring"
            :src="videoFeedUrl"
            alt="AI 视觉感知推断流"
            class="w-full h-full object-cover rounded-xl"
            @error="onVideoFeedError"
          />
          <!-- 待机或加载占位 -->
          <div v-else class="flex flex-col items-center justify-center p-6 text-center space-y-3">
            <div class="w-14 h-14 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center text-slate-400 shadow-inner ring-4 ring-slate-900/50">
              <Camera class="w-7 h-7 text-slate-400" />
            </div>
            <div>
              <p class="text-xs font-bold text-slate-200">视觉督导引擎未连接</p>
              <p class="text-[11px] text-slate-400 mt-1.5 max-w-xs leading-relaxed">
                点击上方【开始智能视觉督导】按钮，系统将自动唤醒摄像头，在此呈现 512维人脸比对与 3D 姿态角 HUD 画面
              </p>
            </div>
            <button
              @click="toggleMonitor"
              :disabled="isMonitorStarting"
              class="px-4 py-2 rounded-xl text-xs font-semibold bg-indigo-600 hover:bg-indigo-700 text-white transition shadow-sm cursor-pointer"
            >
              立即调起摄像头督导
            </button>
          </div>
        </div>

        <div class="flex items-center justify-between text-[10px] text-slate-500 mt-2.5 px-1 pt-2.5 border-t border-slate-100 font-mono">
          <span>AI 算法: InsightFace (buffalo_l)</span>
          <span class="text-indigo-600 font-bold">姿态标准: Pitch ≥ -10° 抬头</span>
        </div>
      </div>

      <!-- 右侧：ECharts 抬头率与注意力流动波形图 (占 7 列) -->
      <div class="lg:col-span-7 pro-card p-5 flex flex-col justify-between h-[390px]">
        <FocusTrendChart :data="trendData" />
      </div>
    </div>

    <!-- 3. 学生实时在座与姿态状态矩阵 -->
    <StudentStatusGrid :students="studentsStatus" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Video, Square, Camera, RefreshCw, Bot, Sparkles } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import FocusTrendChart from '../components/FocusTrendChart.vue'
import StudentStatusGrid from '../components/StudentStatusGrid.vue'
import { visualApi } from '../api'
import type { DashboardOverviewVO, FocusTrendPointVO, StudentRealtimeStatusVO } from '../api/types'

const overview = ref<DashboardOverviewVO>({
  totalRegistered: 0,
  currentPresent: 0,
  currentAbsent: 0,
  attendanceRate: 0,
  realtimeLookupRate: 0,
  lookdownCount: 0,
  focusLevel: '计算中...',
  lastUpdateTime: ''
})

const trendData = ref<FocusTrendPointVO[]>([])
const studentsStatus = ref<StudentRealtimeStatusVO[]>([])

let pollTimer: number | null = null
let simTimer: number | null = null
const isSimulating = ref(false)
const isMonitoring = ref(false)
const isMonitorStarting = ref(false)
const monitorToastMsg = ref('')
const videoFeedUrl = ref('/api/visual/video-feed')

const onVideoFeedError = () => {
  setTimeout(() => {
    if (isMonitoring.value) {
      videoFeedUrl.value = `/api/visual/video-feed?t=${Date.now()}`
    }
  }, 1500)
}

// 拉取最新大屏数据
const fetchDashboardData = async () => {
  try {
    const [ov, tr, st] = await Promise.all([
      visualApi.getOverview(),
      visualApi.getTrend(),
      visualApi.getStudentsStatus()
    ])
    if (ov) overview.value = ov
    if (tr) trendData.value = tr
    if (st) studentsStatus.value = st
  } catch (err) {
    console.warn('拉取大屏数据异常，请确保后端服务 (8080) 正在运行', err)
  }
}

// 切换真实摄像头智能视觉督导
const toggleMonitor = async () => {
  if (isMonitoring.value) {
    try {
      const msg = await visualApi.stopMonitor()
      isMonitoring.value = false
      monitorToastMsg.value = msg || '视觉督导监控已停止'
      setTimeout(() => { monitorToastMsg.value = '' }, 3000)

      // 恢复常规刷新周期
      if (pollTimer) clearInterval(pollTimer)
      pollTimer = window.setInterval(fetchDashboardData, 2500)
    } catch (err) {
      console.error('停止督导异常:', err)
    }
  } else {
    // 若正在模拟则先暂停模拟
    if (isSimulating.value) {
      toggleSimulation()
    }

    isMonitorStarting.value = true
    try {
      const msg = await visualApi.startMonitor()
      isMonitoring.value = true
      videoFeedUrl.value = `/api/visual/video-feed?t=${Date.now()}`
      monitorToastMsg.value = msg || '视觉督导已开启！已在桌面调起摄像头推断窗口，大屏视频流与数据正在实时同步！'
      setTimeout(() => { monitorToastMsg.value = '' }, 5000)

      // 开启督导后加速大屏轮询为 1.2s，与 Python 上报周期精准对齐！
      if (pollTimer) clearInterval(pollTimer)
      pollTimer = window.setInterval(fetchDashboardData, 1200)
    } catch (err: any) {
      console.error('启动督导异常:', err)
      alert('启动视觉督导失败，请确认摄像头未被其他软件占用。')
    } finally {
      isMonitorStarting.value = false
    }
  }
}

// 模拟机器人视觉端推流上报（方便无摄像头或展示时使用）
const simulateStreamFrame = async () => {
  if (!studentsStatus.value || studentsStatus.value.length === 0) return

  // 随机让 85%~95% 的人抬头，部分人低头
  const poses: Record<string, string> = {}
  const presentIds: string[] = []
  let upCount = 0
  let downCount = 0

  studentsStatus.value.forEach(s => {
    // 假设 90% 的概率在座
    const isPresent = Math.random() > 0.1
    if (isPresent) {
      presentIds.push(s.studentId)
      // 80% 概率抬头，20% 低头
      const isUp = Math.random() > 0.25
      if (isUp) {
        poses[s.studentId] = 'UP'
        upCount++
      } else {
        poses[s.studentId] = 'DOWN'
        downCount++
      }
    }
  })

  const lookupRate = presentIds.length > 0 ? upCount / presentIds.length : 0.0

  try {
    await visualApi.reportStream({
      sessionId: 'DEMO_SESSION_SIM',
      courseName: '基于具身机器人的智能课堂分析',
      className: '高一(1)班',
      detectedPersonCount: presentIds.length,
      lookupCount: upCount,
      lookdownCount: downCount,
      lookupRate: parseFloat(lookupRate.toFixed(3)),
      presentStudentIds: presentIds,
      studentPoses: poses
    })
    // 立即拉取更新
    fetchDashboardData()
  } catch (e) {
    console.error('模拟上报失败:', e)
  }
}

const toggleSimulation = () => {
  if (isSimulating.value) {
    if (simTimer) clearInterval(simTimer)
    simTimer = null
    isSimulating.value = false
  } else {
    isSimulating.value = true
    simulateStreamFrame()
    simTimer = window.setInterval(simulateStreamFrame, 3000)
  }
}

onMounted(async () => {
  await fetchDashboardData()
  // 检查督导推断运行状态
  isMonitoring.value = await visualApi.getMonitorStatus()

  // 定时刷新大屏
  const interval = isMonitoring.value ? 1200 : 2500
  pollTimer = window.setInterval(fetchDashboardData, interval)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (simTimer) clearInterval(simTimer)
})
</script>
