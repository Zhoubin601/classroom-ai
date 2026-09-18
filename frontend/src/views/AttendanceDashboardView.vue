<template>
  <div class="space-y-6">
    <!-- 顶部状态栏与课堂排课关联选择器 -->
    <div class="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-card flex flex-wrap items-center justify-between gap-4">
      <div class="flex items-center gap-4">
        <div class="w-11 h-11 rounded-xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600">
          <Video class="w-5 h-5" />
        </div>
        <div>
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-slate-900 flex items-center gap-2.5">
              课堂智能考勤与态势监控大屏
              <span v-if="isMonitoring" class="inline-flex items-center gap-1.5 text-[11px] px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-medium">
                <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                摄像头推断采集中
              </span>
              <span v-else class="inline-flex items-center gap-1.5 text-[11px] px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-600 border border-slate-200 font-medium">
                <span class="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
                待机就绪
              </span>
            </h1>
          </div>
          <p class="text-xs text-slate-500 mt-0.5 font-sans">
            推断引擎: InsightFace (ArcFace 512维) + MediaPipe (solvePnP) | 毫秒级 1:N 考勤人脸识别
          </p>
        </div>
      </div>

      <!-- 关联当前开课与班级 -->
      <div class="flex flex-wrap items-center gap-3">
        <div class="flex items-center gap-2 bg-slate-50 px-3 py-1.5 rounded-xl border border-slate-200 text-xs text-slate-600">
          <span>当前授课班级：</span>
          <select 
            v-model="selectedOfferingId" 
            @change="onOfferingChange" 
            class="bg-white border border-slate-200 text-slate-800 font-medium focus:outline-none focus:border-indigo-500 cursor-pointer rounded-lg px-2.5 py-1 text-xs shadow-xs"
          >
            <option 
              v-for="off in offeringList" 
              :key="off.id" 
              :value="off.id"
              class="bg-white text-slate-800 py-1.5"
            >
              {{ off.course?.courseName || '课程' }} - {{ off.teacherName }} ({{ off.className }}, {{ off.studentCount }}人)
            </option>
          </select>
        </div>

        <!-- 班级花名册与选人入班按钮 -->
        <button
          @click="openClassStudentModal"
          class="px-3.5 py-1.5 rounded-xl text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200 hover:bg-slate-200 flex items-center gap-1.5 transition shadow-xs"
          title="查看班级花名册或从总库选入/移出学生"
        >
          <Users class="w-3.5 h-3.5 text-slate-500" />
          班级成员选拔 ({{ overview.totalRegistered }}人)
        </button>

        <!-- 核心按钮：启动/停止摄像头智能考勤 -->
        <button
          @click="toggleMonitor"
          :disabled="isMonitorStarting"
          :class="[
            'px-4 py-2 rounded-xl text-xs font-medium flex items-center gap-2 transition shadow-xs',
            isMonitoring
              ? 'bg-rose-50 text-rose-700 border border-rose-200 hover:bg-rose-100'
              : 'bg-indigo-600 hover:bg-indigo-700 text-white'
          ]"
        >
          <span v-if="isMonitorStarting" class="w-3.5 h-3.5 rounded-full border-2 border-current border-t-transparent animate-spin"></span>
          <Square v-else-if="isMonitoring" class="w-3.5 h-3.5" />
          <Camera v-else class="w-3.5 h-3.5" />
          {{ isMonitorStarting ? '正在拉起推断引擎...' : isMonitoring ? '停止摄像头监控' : '打开摄像头开启考勤' }}
        </button>

        <!-- 一键下课归档考勤结果 -->
        <button
          @click="finishAndArchiveAttendance"
          class="px-3.5 py-2 rounded-xl text-xs font-medium bg-slate-900 hover:bg-slate-800 text-white shadow-xs transition flex items-center gap-1.5"
        >
          <Archive class="w-3.5 h-3.5" />
          结束考勤并归档下课
        </button>

        <!-- 备用按钮：模拟推流 -->
        <button
          @click="toggleSimulation"
          :disabled="isMonitoring"
          :class="[
            'px-3 py-1.5 rounded-xl text-xs font-medium transition border',
            isSimulating ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-slate-100 text-slate-600 border-slate-200 hover:bg-slate-200'
          ]"
        >
          {{ isSimulating ? '暂停模拟' : '演示模拟流' }}
        </button>
      </div>
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
        subtitle="当前排课班额"
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
        title="低头走神"
        :value="overview.lookdownCount"
        unit="人"
        subtitle="Pitch 姿态 < -10°"
        :variant="overview.lookdownCount > 0 ? 'danger' : 'green'"
      />
      <MetricCard
        title="未到/缺勤"
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

    <!-- 2. 画中画视频感知视窗 + ECharts 抬头率与注意力流动波形图 -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-5">
      <!-- 左侧：实时摄像头 AI 感知推断视窗 (占 5 列) -->
      <div class="lg:col-span-5 bg-white border border-slate-200/80 rounded-2xl p-4 flex flex-col justify-between h-[380px] relative overflow-hidden shadow-card">
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-2">
            <Video class="w-4 h-4 text-indigo-600" />
            <h3 class="text-xs font-semibold text-slate-900 tracking-wide">摄像头实时人脸考勤与姿态 HUD 视窗</h3>
          </div>
          <span v-if="isMonitoring" class="text-[10px] px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-200 font-mono font-medium">
            LIVE 1:N 识别
          </span>
          <span v-else class="text-[10px] px-2 py-0.5 rounded-md bg-slate-100 text-slate-600 border border-slate-200 font-medium">
            待命中
          </span>
        </div>

        <!-- 监控开启时的实时画面 (MJPEG 流) -->
        <div class="flex-1 w-full bg-slate-950 rounded-xl overflow-hidden relative flex items-center justify-center border border-slate-900 shadow-inner">
          <img
            v-if="isMonitoring"
            :src="videoFeedUrl"
            alt="AI 视觉感知推断流"
            class="w-full h-full object-cover rounded-xl"
            @error="onVideoFeedError"
          />

          <!-- 未开启摄像头时的待机面板 -->
          <div v-else class="flex flex-col items-center justify-center text-center p-6 space-y-3">
            <div class="w-12 h-12 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-center text-slate-400 shadow-inner">
              <Camera class="w-6 h-6 text-slate-400" />
            </div>
            <div>
              <p class="text-xs font-semibold text-slate-200">摄像头考勤感知视窗处于就绪状态</p>
              <p class="text-[11px] text-slate-400 mt-1 max-w-xs">
                点击上方【打开摄像头开启考勤】唤起 InsightFace 识别引擎，现场画中画实时渲染人脸框与 3D 姿态角
              </p>
            </div>
          </div>
        </div>

        <!-- 底部推断参数条 -->
        <div class="flex items-center justify-between mt-2 pt-2 border-t border-slate-100 text-[10px] text-slate-500 font-mono">
          <span>分辨率: 1280x720</span>
          <span>FPS: {{ isMonitoring ? '24~30 FPS' : '0' }}</span>
          <span class="text-indigo-600 font-medium">ArcFace 向量比对阈值: 0.42</span>
        </div>
      </div>

      <!-- 右侧：抬头率与在座人数时序折线图 (占 7 列) -->
      <div class="lg:col-span-7 bg-white border border-slate-200/80 rounded-2xl p-4 flex flex-col justify-between h-[380px] shadow-card">
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-2">
            <TrendingUp class="w-4 h-4 text-indigo-600" />
            <h3 class="text-xs font-semibold text-slate-900 tracking-wide">课堂抬头率与出勤人数时序波形 (LookUp Trend)</h3>
          </div>
          <span class="text-[10px] text-slate-400">每 1.2 秒平滑流动更新</span>
        </div>
        <div class="flex-1 w-full min-h-[300px]">
          <FocusTrendChart :data="trendData" />
        </div>
      </div>
    </div>

    <!-- 3. 学生实时在座考勤与姿态网格卡片 -->
    <StudentStatusGrid :students="studentsStatus" />

    <!-- 弹窗：开课班级学生管理与从总库选人入班 -->
    <div v-if="showStudentModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm">
      <div class="bg-white border border-slate-200/80 rounded-2xl w-full max-w-3xl p-6 shadow-modal space-y-4 max-h-[85vh] flex flex-col">
        <!-- 弹窗头部 -->
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <div>
            <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
              <Users class="w-5 h-5 text-indigo-600" />
              班级学生花名册与选拔管理
              <span class="text-xs px-2.5 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200 font-mono">
                {{ offeringList.find(o => o.id === selectedOfferingId)?.className }}
              </span>
            </h3>
            <p class="text-xs text-slate-500 mt-0.5">
              从人脸底库中自由挑选学生加入本课程班级，或移出班级。数据实时与后端数据库严格同步。
            </p>
          </div>
          <button @click="showStudentModal = false" class="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition">
            <X class="w-5 h-5" />
          </button>
        </div>

        <!-- 标签切换 -->
        <div class="flex items-center gap-2 border-b border-slate-200 pb-2 text-xs">
          <button
            @click="activeModalTab = 'enrolled'"
            :class="['px-3 py-1.5 rounded-lg font-medium transition flex items-center gap-1.5', activeModalTab === 'enrolled' ? 'bg-indigo-50 text-indigo-700 border border-indigo-200' : 'text-slate-500 hover:text-slate-800']"
          >
            <span>本班已有学生名单 ({{ currentOfferingStudents.length }}人)</span>
          </button>
          <button
            @click="activeModalTab = 'add'"
            :class="['px-3 py-1.5 rounded-lg font-medium transition flex items-center gap-1.5', activeModalTab === 'add' ? 'bg-indigo-50 text-indigo-700 border border-indigo-200' : 'text-slate-500 hover:text-slate-800']"
          >
            <UserPlus class="w-3.5 h-3.5" />
            <span>从总档案库选入学生 (可选 {{ availableStudents.length }}人)</span>
          </button>
        </div>

        <!-- 内容区域 1: 当前班级学生列表 -->
        <div v-if="activeModalTab === 'enrolled'" class="flex-1 overflow-y-auto space-y-2 pr-1">
          <div v-if="currentOfferingStudents.length === 0" class="py-12 text-center text-slate-400 text-xs">
            当前班级暂无学生，请切换至【从总档案库选入学生】进行挑选
          </div>
          <div
            v-for="s in currentOfferingStudents"
            :key="s.studentId"
            class="flex items-center justify-between p-2.5 bg-slate-50 border border-slate-200/80 rounded-xl hover:border-slate-300 transition"
          >
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-xs overflow-hidden">
                <img v-if="s.avatarUrl" :src="s.avatarUrl" class="w-full h-full object-cover" />
                <span v-else>{{ s.name?.charAt(0) }}</span>
              </div>
              <div>
                <div class="flex items-center gap-2">
                  <span class="text-xs font-bold text-slate-900">{{ s.name }}</span>
                  <span class="text-[10px] text-slate-500">({{ s.gender || '未知' }})</span>
                </div>
                <span class="text-[11px] font-mono text-indigo-600">{{ s.studentId }}</span>
              </div>
            </div>

            <button
              @click="handleRemoveStudentFromClass(s.studentId, s.name)"
              class="px-2.5 py-1 text-xs text-rose-600 hover:text-rose-700 hover:bg-rose-50 rounded-lg transition font-medium"
            >
              移出班级
            </button>
          </div>
        </div>

        <!-- 内容区域 2: 从总库选入学生 -->
        <div v-if="activeModalTab === 'add'" class="flex-1 overflow-y-auto space-y-2 pr-1">
          <div v-if="availableStudents.length === 0" class="py-12 text-center text-slate-400 text-xs">
            总档案库中暂无其他待分配或属于其他班级的候选学生
          </div>
          <div
            v-for="s in availableStudents"
            :key="s.studentId"
            class="flex items-center justify-between p-2.5 bg-slate-50 border border-slate-200/80 rounded-xl hover:border-slate-300 transition"
          >
            <div class="flex items-center gap-3">
              <input
                type="checkbox"
                :value="s.studentId"
                v-model="selectedStudentsToAdd"
                class="rounded border-slate-300 text-indigo-600 focus:ring-0 cursor-pointer"
              />
              <div class="w-8 h-8 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-xs overflow-hidden">
                <img v-if="s.avatarUrl" :src="s.avatarUrl" class="w-full h-full object-cover" />
                <span v-else>{{ s.name?.charAt(0) }}</span>
              </div>
              <div>
                <div class="flex items-center gap-2">
                  <span class="text-xs font-bold text-slate-900">{{ s.name }}</span>
                  <span class="text-[10px] text-slate-500">({{ s.gender || '未知' }})</span>
                  <span class="text-[10px] px-1.5 py-0.2 rounded bg-slate-200 text-slate-600">
                    当前: {{ s.className || '未分配' }}
                  </span>
                </div>
                <span class="text-[11px] font-mono text-indigo-600">{{ s.studentId }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div class="flex items-center justify-between border-t border-slate-100 pt-3 text-xs">
          <span class="text-slate-500">
            班级当前人数：<b class="text-slate-900">{{ currentOfferingStudents.length }}</b> 人
          </span>
          <div class="flex items-center gap-2">
            <button
              v-if="activeModalTab === 'add'"
              @click="handleAddStudentsToClass"
              :disabled="selectedStudentsToAdd.length === 0"
              :class="['px-4 py-2 rounded-xl font-medium transition flex items-center gap-1.5 shadow-xs', selectedStudentsToAdd.length > 0 ? 'bg-indigo-600 hover:bg-indigo-700 text-white' : 'bg-slate-100 text-slate-400 cursor-not-allowed']"
            >
              <Check class="w-3.5 h-3.5" />
              确认选入选中的 {{ selectedStudentsToAdd.length }} 人
            </button>
            <button @click="showStudentModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium rounded-xl transition">
              完成 / 关闭
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { Video, Users, Camera, Archive, Square, TrendingUp, X, UserPlus, Check } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import FocusTrendChart from '../components/FocusTrendChart.vue'
import StudentStatusGrid from '../components/StudentStatusGrid.vue'
import { visualApi, courseApi, attendanceApi } from '../api'
import type { DashboardOverviewVO, FocusTrendPointVO, StudentRealtimeStatusVO, CourseOffering } from '../api/types'
import { attendanceMetrics, lookupRatio } from '../api/metrics.mjs'

const props = defineProps<{
  initialOfferingId?: number | null
}>()

const overview = ref<DashboardOverviewVO>({
  totalRegistered: 0,
  currentPresent: 0,
  currentAbsent: 0,
  attendanceRate: 0,
  realtimeLookupRate: 0,
  lookdownCount: 0,
  focusLevel: '待启动',
  lastUpdateTime: ''
})

const trendData = ref<FocusTrendPointVO[]>([])
const studentsStatus = ref<StudentRealtimeStatusVO[]>([])

const offeringList = ref<CourseOffering[]>([])
const selectedOfferingId = ref<number>(1)
const currentSessionId = ref<number | null>(null)

const isMonitoring = ref(false)
const isMonitorStarting = ref(false)
const isSimulating = ref(false)
const videoFeedUrl = ref('/api/visual/video-feed')

let pollTimer: number | null = null
let simulationTimer: number | null = null

const showStudentModal = ref(false)
const currentOfferingStudents = ref<any[]>([])
const availableStudents = ref<any[]>([])
const selectedStudentsToAdd = ref<string[]>([])
const activeModalTab = ref<'enrolled' | 'add'>('enrolled')

const loadOfferings = async () => {
  try {
    offeringList.value = await courseApi.getOfferings()
    if (offeringList.value.length > 0) {
      if (props.initialOfferingId && offeringList.value.some(o => o.id === props.initialOfferingId)) {
        selectedOfferingId.value = props.initialOfferingId
      } else if (!selectedOfferingId.value || !offeringList.value.some(o => o.id === selectedOfferingId.value)) {
        selectedOfferingId.value = offeringList.value[0].id
      }
    }
  } catch (e) {
    console.error('加载开课列表失败', e)
  }
}

const onOfferingChange = async () => {
  if (isMonitoring.value) {
    if (confirm('正在进行当前课堂的摄像头智能监控，切换至其他班级将自动停止当前推断流，是否继续切换？')) {
      await visualApi.stopMonitor()
      isMonitoring.value = false
      if (currentSessionId.value) {
        await attendanceApi.finish({
          sessionId: currentSessionId.value,
          actualCount: overview.value.currentPresent,
          avgLookupRate: overview.value.realtimeLookupRate
        })
        currentSessionId.value = null
      }
    } else {
      return
    }
  }
  // 查询新班级是否有进行中的考勤会话
  try {
    const sessions = await attendanceApi.getByOffering(selectedOfferingId.value)
    const active = sessions.find((s: any) => s.status === 'ACTIVE')
    currentSessionId.value = active ? active.id : null
  } catch (e) {
    currentSessionId.value = null
  }
  await fetchDashboardData()
}

watch(() => props.initialOfferingId, async (newId) => {
  if (newId && newId !== selectedOfferingId.value) {
    selectedOfferingId.value = newId
    await onOfferingChange()
  }
})

const fetchDashboardData = async () => {
  try {
    const [ov, tr, st] = await Promise.all([
      visualApi.getOverview(selectedOfferingId.value),
      visualApi.getTrend(),
      visualApi.getStudentsStatus(selectedOfferingId.value)
    ])
    if (ov) {
      overview.value = ov
    }
    if (tr) trendData.value = tr
    if (st) studentsStatus.value = st
  } catch (e) {
    console.error('获取大屏数据失败', e)
  }
}

const openClassStudentModal = async () => {
  if (!selectedOfferingId.value) return
  await loadOfferingStudentData()
  showStudentModal.value = true
}

const loadOfferingStudentData = async () => {
  try {
    const res = await courseApi.getOfferingStudents(selectedOfferingId.value)
    currentOfferingStudents.value = res.enrolled || []
    availableStudents.value = res.available || []
    selectedStudentsToAdd.value = []
  } catch (e) {
    console.error('获取班级学生名册失败', e)
  }
}

const handleAddStudentsToClass = async () => {
  if (selectedStudentsToAdd.value.length === 0) {
    alert('请先勾选需要选入本班的学生！')
    return
  }
  try {
    await courseApi.addStudentsToOffering(selectedOfferingId.value, selectedStudentsToAdd.value)
    alert(`成功选入 ${selectedStudentsToAdd.value.length} 名学生进入本课程班级！`)
    await loadOfferings()
    await loadOfferingStudentData()
    await fetchDashboardData()
    activeModalTab.value = 'enrolled'
  } catch (e: any) {
    alert('选入失败: ' + (e.message || '未知错误'))
  }
}

const handleRemoveStudentFromClass = async (studentId: string, name: string) => {
  if (!confirm(`确认将学生【${name} (${studentId})】从当前课程班级移出？`)) return
  try {
    await courseApi.removeStudentFromOffering(selectedOfferingId.value, studentId)
    await loadOfferings()
    await loadOfferingStudentData()
    await fetchDashboardData()
  } catch (e: any) {
    alert('移出失败: ' + (e.message || '未知错误'))
  }
}

const toggleMonitor = async () => {
  if (isMonitoring.value) {
    // 停止监控
    try {
      await visualApi.stopMonitor()
      isMonitoring.value = false
    } catch (e) {
      console.error(e)
    }
  } else {
    // 启动摄像头考勤
    isMonitorStarting.value = true
    try {
      if (isSimulating.value) toggleSimulation()
      // 1. 在后端创建/关联 AttendanceSession
      const session = await attendanceApi.start({
        offeringId: selectedOfferingId.value,
        weekNumber: 2
      })
      if (session) currentSessionId.value = session.id

      // 2. 调起摄像头视觉监控推断
      await visualApi.startMonitor()
      isMonitoring.value = true
      videoFeedUrl.value = `/api/visual/video-feed?t=${Date.now()}`
    } catch (e: any) {
      alert('启动摄像头监控失败: ' + (e.message || '请检查摄像头连接'))
    } finally {
      isMonitorStarting.value = false
    }
  }
}

const finishAndArchiveAttendance = async () => {
  if (!currentSessionId.value) { alert('当前没有可归档的考勤会话'); return }
  if (!confirm('确认结束本次课堂考勤并归档下课？')) return

  try {
    if (isSimulating.value) toggleSimulation()
    if (isMonitoring.value) {
      await visualApi.stopMonitor()
      isMonitoring.value = false
    }

    if (currentSessionId.value) {
      await attendanceApi.finish({
        sessionId: currentSessionId.value,
        actualCount: overview.value.currentPresent,
        avgLookupRate: overview.value.realtimeLookupRate
      })
      currentSessionId.value = null
    }

    alert('课堂考勤已成功结束并归档至课程历史档案！实到人次：' + overview.value.currentPresent + '，出勤率：' + overview.value.attendanceRate + '%')
  } catch (e: any) {
    alert('归档失败: ' + (e.message || '未知错误'))
  }
}

const toggleSimulation = () => {
  isSimulating.value = !isSimulating.value
  if (isSimulating.value) {
    simulationTimer = window.setInterval(async () => {
      try {
        const detected = Math.floor(Math.random() * 5) + 88
        const lookup = Math.floor(detected * (0.8 + Math.random() * 0.15))
        await visualApi.reportStream({
          sessionId: 'sim-' + Date.now(),
          detectedPersonCount: detected,
          lookupCount: lookup,
          lookdownCount: detected - lookup,
          lookupRate: lookupRatio(lookup, detected),
          presentStudentIds: ['20246085', '20246074', '20245727', '20245796'],
          studentPoses: {
            '20246085': 'UP',
            '20246074': 'UP',
            '20245727': Math.random() > 0.2 ? 'UP' : 'DOWN',
            '20245796': 'UP'
          }
        })
      } catch (error) { console.error('模拟推流失败', error) }
    }, 1500)
  } else if (simulationTimer) {
    clearInterval(simulationTimer)
    simulationTimer = null
  }
}

const onVideoFeedError = () => {
  // 视频流加载中重试
}

onMounted(async () => {
  await loadOfferings()
  try {
    const session = await attendanceApi.getCurrent()
    if (session && !props.initialOfferingId) {
      currentSessionId.value = session.id
      selectedOfferingId.value = session.offering.id
    }
    isMonitoring.value = await visualApi.getMonitorStatus()
  } catch (error) { console.error('恢复考勤会话失败', error) }
  await fetchDashboardData()
  pollTimer = window.setInterval(fetchDashboardData, 1200)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (simulationTimer) clearInterval(simulationTimer)
})
</script>
