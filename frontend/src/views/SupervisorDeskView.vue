<template>
  <div class="space-y-6">
    <!-- 顶部督导工作台状态栏 -->
    <div class="minimal-card p-6 flex flex-wrap items-center justify-between gap-4">
      <div class="flex items-center gap-4">
        <div class="w-12 h-12 rounded-xl bg-amber-50 border border-amber-100 flex items-center justify-center text-amber-600 shadow-subtle">
          <ShieldCheck class="w-6 h-6" />
        </div>
        <div>
          <div class="flex items-center gap-3">
            <h1 class="text-xl font-bold text-slate-900">教学督导工作台</h1>
            <span class="text-xs px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 font-medium">校/院两级督导专家专属</span>
          </div>
          <p class="text-xs text-slate-500 mt-1">负责待督导课程复合检索、课件教案免密预审、BOPPPS随堂打分、全院覆盖率巡检与红黄质量预警</p>
        </div>
      </div>
      <div class="flex items-center gap-2 text-xs">
        <span class="text-slate-500">当前督导身份：</span>
        <span class="px-3 py-1.5 rounded-xl bg-slate-50 border border-slate-200 text-slate-800 font-semibold shadow-subtle">沈越 (校级教学督导)</span>
      </div>
    </div>

    <!-- 全院督导覆盖率动态大屏指标卡片 (US-15) -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
      <div class="minimal-card p-5">
        <span class="text-xs text-slate-500 font-medium">全院开设课程总数</span>
        <div class="text-2xl font-bold text-slate-900 font-mono mt-1">{{ dashboardMetrics?.totalCourses ?? 0 }} 门</div>
        <span class="text-[11px] text-slate-500 mt-1 block">覆盖计算机/软件工程全专业</span>
      </div>
      <div class="minimal-card p-5">
        <span class="text-xs text-slate-500 font-medium">已督导听课覆盖门数</span>
        <div class="text-2xl font-bold text-emerald-600 font-mono mt-1">{{ dashboardMetrics?.supervisedCourses ?? 0 }} 门</div>
        <span class="text-[11px] text-emerald-700 font-medium mt-1 block">累计开展听课 {{ dashboardMetrics?.totalEvaluations ?? 0 }} 次</span>
      </div>
      <div class="minimal-card p-5">
        <span class="text-xs text-slate-500 font-medium">督导覆盖率动态百分比 (US-15)</span>
        <div class="text-3xl font-bold text-indigo-600 font-mono mt-1">{{ dashboardMetrics ? (dashboardMetrics.coverageRate ?? 0).toFixed(1) : '0.0' }}%</div>
        <div class="w-full bg-slate-100 h-1.5 rounded-full mt-2 overflow-hidden">
          <div class="bg-indigo-600 h-full rounded-full transition-all duration-500" :style="{ width: `${dashboardMetrics?.coverageRate ?? 0}%` }"></div>
        </div>
      </div>
      <div class="minimal-card p-5">
        <span class="text-xs text-slate-500 font-medium">待巡检覆盖课程</span>
        <div class="text-2xl font-bold text-amber-600 font-mono mt-1">{{ dashboardMetrics?.pendingCourses ?? 0 }} 门</div>
        <span class="text-[11px] text-amber-700 font-medium mt-1 block">需督导组优先排期进班</span>
      </div>
    </div>

    <!-- 子导航标签 -->
    <div class="flex items-center gap-1.5 border-b border-slate-200 pb-3">
      <button 
        v-for="tab in tabs" 
        :key="tab.key" 
        @click="activeTab = tab.key"
        :class="['px-3.5 py-1.5 rounded-xl text-xs font-medium transition flex items-center gap-1.5', 
                 activeTab === tab.key ? 'bg-amber-50 text-amber-800 border border-amber-200 font-semibold shadow-subtle' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100']"
      >
        <component :is="tab.iconComp" class="w-3.5 h-3.5" :class="activeTab === tab.key ? 'text-amber-600' : 'text-slate-400'" />
        {{ tab.label }}
      </button>
    </div>

    <!-- Tab 1: 待督导课程复合检索与听评课 (US-06 / US-13) -->
    <div v-if="activeTab === 'search'" class="space-y-4">
      <div class="minimal-card p-5">
        <div class="flex flex-wrap items-center justify-between gap-4 mb-4">
          <!-- 复合检索条件 (US-06) -->
          <div class="flex flex-wrap items-center gap-2.5">
            <div class="relative">
              <input 
                v-model="filterParams.keyword" 
                @input="loadOfferings" 
                placeholder="按课程名 / 代码检索..." 
                class="bg-white border border-slate-200 rounded-xl pl-8 pr-3 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 w-48 shadow-subtle"
              />
              <Search class="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2.5" />
            </div>
            <input 
              v-model="filterParams.teacher" 
              @input="loadOfferings" 
              placeholder="按教师检索 (如 郭军)..." 
              class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 w-44 shadow-subtle"
            />
            <select v-model="filterParams.term" @change="loadOfferings" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-indigo-500 shadow-subtle">
              <option value="">全部学期</option>
              <option v-for="t in availableTerms" :key="t" :value="t">{{ t }}</option>
            </select>
            <select v-model="filterParams.className" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-indigo-500 shadow-subtle">
              <option value="">全部教学班级</option>
              <option v-for="c in availableClasses" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <span class="text-xs text-slate-500">检索响应时间 <b class="text-emerald-600 font-semibold">&lt; 150ms</b></span>
        </div>

        <!-- 课程开课列表 -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div v-for="off in filteredOfferings" :key="off.id" class="p-4 bg-white border border-slate-200 rounded-xl hover:border-indigo-300 hover:shadow-card transition shadow-subtle space-y-3">
            <div class="flex items-start justify-between">
              <div>
                <span class="text-[10px] px-2 py-0.5 rounded bg-indigo-50 text-indigo-700 border border-indigo-100 font-mono">
                  {{ off.course.courseCode }} · {{ off.academicTerm }}
                </span>
                <h3 class="text-sm font-bold text-slate-900 mt-1">{{ off.course.courseName }}</h3>
                <p class="text-xs text-slate-500 mt-0.5">任课教师：<b class="text-slate-800">{{ off.teacherName }}</b> | 班级：<span class="text-indigo-600 font-medium">{{ off.className }}</span> ({{ off.studentCount }} 人额)</p>
              </div>
              <span class="px-2.5 py-0.5 rounded-full text-[10px] bg-slate-100 text-slate-600 border border-slate-200">
                {{ off.course.courseType }}
              </span>
            </div>

            <!-- 操作按钮组 -->
            <div class="flex items-center justify-between pt-2 border-t border-slate-100 gap-2">
              <button @click="openPreviewResources(off.course.id)" class="text-xs text-indigo-600 hover:text-indigo-800 flex items-center gap-1 font-medium">
                <FileText class="w-3.5 h-3.5" /> 课件免密预审
              </button>
              <div class="flex items-center gap-2">
                <button 
                  @click="enterLiveSupervision(off.id)" 
                  class="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-xl text-xs font-semibold flex items-center gap-1 transition-all"
                  title="进班查看实时考勤与姿态监控大屏"
                >
                  <Video class="w-3.5 h-3.5" /> 进班实时督导
                </button>
                <button @click="openEvaluateForm(off)" class="px-3.5 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-semibold shadow-subtle flex items-center gap-1 transition">
                  <Edit3 class="w-3.5 h-3.5" /> 随堂评价 (US-13)
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 2: 质量预警中心 (US-16: 零覆盖黄标 / 低分红标) -->
    <div v-if="activeTab === 'alerts'" class="space-y-4">
      <div class="minimal-card p-5">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h2 class="text-sm font-semibold text-slate-900 flex items-center gap-2">
              <AlertTriangle class="w-4 h-4 text-amber-600" /> 教学质量预警中心 (US-16)
            </h2>
            <p class="text-xs text-slate-500 mt-0.5">
              系统自动根据督导覆盖率（&lt;30% 标黄）与综合听课均分（&lt;75分 标红）触发预警提示
            </p>
          </div>
          <span class="text-xs px-3 py-1 bg-rose-50 text-rose-700 border border-rose-200 rounded-xl font-semibold">
            当前存在 {{ alertList.length }} 项需关注预警
          </span>
        </div>

        <div class="space-y-3">
          <div 
            v-for="alert in alertList" 
            :key="alert.courseCode" 
            :class="['p-4 rounded-xl border flex items-start justify-between gap-4 transition shadow-subtle', 
                     alert.alertLevel === 'RED' ? 'bg-rose-50/40 border-rose-200' : 'bg-amber-50/40 border-amber-200']"
          >
            <div class="flex items-start gap-3">
              <div class="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5"
                :class="alert.alertLevel === 'RED' ? 'bg-rose-100 text-rose-600' : 'bg-amber-100 text-amber-600'">
                <AlertCircle class="w-5 h-5" />
              </div>
              <div>
                <div class="flex items-center gap-2">
                  <span class="text-sm font-bold text-slate-900">{{ alert.courseName }} ({{ alert.courseCode }})</span>
                  <span :class="['text-[10px] px-2 py-0.5 rounded-full font-semibold', 
                                alert.alertLevel === 'RED' ? 'bg-rose-100 text-rose-800 border border-rose-200' : 'bg-amber-100 text-amber-800 border border-amber-200']">
                    {{ alert.alertLevel === 'RED' ? '红色低分预警 (<75分)' : '黄色零覆盖率预警 (<30%)' }}
                  </span>
                </div>
                <p class="text-xs text-slate-700 mt-1">{{ alert.alertMessage }}</p>
                <p class="text-xs text-slate-500 mt-1">负责教师/教研室：<b class="text-slate-800">{{ alert.teacherName }}</b> ({{ alert.department }})</p>
                <div class="mt-2 text-xs text-indigo-700 flex items-center gap-1 font-medium">
                  <span>建议：</span>{{ alert.suggestAction }}
                </div>
              </div>
            </div>

            <div class="text-right whitespace-nowrap">
              <span class="text-xs font-mono text-slate-500 block">当前均分：<b class="text-slate-900 font-bold">{{ alert.currentScore ? alert.currentScore + '分' : '暂无' }}</b></span>
              <span class="text-[11px] text-slate-400 block mt-1">已督导 {{ alert.evaluationCount }} 次</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗：BOPPPS 四维 100 分制随堂听课打分表单 (US-13 / US-14) -->
    <div v-if="showEvaluateModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-2xl p-6 shadow-modal space-y-4 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between border-b border-slate-200 pb-3">
          <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
            <Edit3 class="w-4 h-4 text-amber-600" /> 填报随堂听课结构化评价表 (BOPPPS 四维模型)
          </h3>
          <button @click="showEvaluateModal = false" class="text-slate-400 hover:text-slate-600 text-lg">✕</button>
        </div>

        <div class="p-3 bg-slate-50 border border-slate-200 rounded-xl text-xs space-y-1">
          <p class="text-slate-900 font-bold">{{ currentOfferingForEval?.course.courseName }} - {{ currentOfferingForEval?.teacherName }} 老师</p>
          <p class="text-slate-500">听课班级：{{ currentOfferingForEval?.className }} · 班额：{{ currentOfferingForEval?.studentCount }} 人</p>
        </div>

        <!-- 基本信息输入 -->
        <div class="grid grid-cols-2 gap-3 text-xs">
          <div>
            <label class="text-slate-600 block mb-1">听课教学章节/主题</label>
            <input v-model="evalForm.listenTopic" placeholder="如 第三讲：需求估算与WBS分解" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">听课日期</label>
            <input v-model="evalForm.evaluateDate" type="date" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
        </div>

        <!-- BOPPPS 四维 100 分打分项 (每项 0-25 分) -->
        <div class="space-y-3 bg-slate-50 p-4 rounded-xl border border-slate-200">
          <h4 class="text-xs font-bold text-indigo-700 flex items-center justify-between">
            <span>BOPPPS 四维打分项 (每项 25 分，合计 100 分满分)</span>
            <span class="text-sm font-bold text-slate-900 font-mono">当前总计：{{ calcTotalScore }} / 100 分</span>
          </h4>

          <div class="grid grid-cols-2 gap-3 text-xs">
            <div>
              <div class="flex justify-between text-slate-600 mb-1">
                <span>1. 教学态度 (0-25分)</span>
                <b class="text-indigo-600 font-mono">{{ evalForm.scoreAttitude }} 分</b>
              </div>
              <input v-model.number="evalForm.scoreAttitude" type="range" min="0" max="25" step="0.5" class="w-full accent-indigo-600" />
            </div>
            <div>
              <div class="flex justify-between text-slate-600 mb-1">
                <span>2. 教学内容 (0-25分)</span>
                <b class="text-indigo-600 font-mono">{{ evalForm.scoreContent }} 分</b>
              </div>
              <input v-model.number="evalForm.scoreContent" type="range" min="0" max="25" step="0.5" class="w-full accent-indigo-600" />
            </div>
            <div>
              <div class="flex justify-between text-slate-600 mb-1">
                <span>3. 教学方法 (0-25分)</span>
                <b class="text-indigo-600 font-mono">{{ evalForm.scoreMethod }} 分</b>
              </div>
              <input v-model.number="evalForm.scoreMethod" type="range" min="0" max="25" step="0.5" class="w-full accent-indigo-600" />
            </div>
            <div>
              <div class="flex justify-between text-slate-600 mb-1">
                <span>4. 教学效果 (0-25分)</span>
                <b class="text-indigo-600 font-mono">{{ evalForm.scoreEffect }} 分</b>
              </div>
              <input v-model.number="evalForm.scoreEffect" type="range" min="0" max="25" step="0.5" class="w-full accent-indigo-600" />
            </div>
          </div>
        </div>

        <!-- 质性评语 (US-14) -->
        <div class="space-y-3 text-xs">
          <div>
            <label class="text-slate-600 block mb-1">课堂教学亮点 (限 500 字)</label>
            <textarea v-model="evalForm.highlights" rows="2" maxlength="500" placeholder="例如：教学组织严密，能够结合实际敏捷项目案例启发学生..." class="w-full bg-white border border-slate-200 rounded-xl p-3 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"></textarea>
          </div>
          <div>
            <label class="text-slate-600 block mb-1">针对性改进建议 (限 500 字)</label>
            <textarea v-model="evalForm.suggestions" rows="2" maxlength="500" placeholder="例如：建议在课后作业中进一步增加甘特图与工期缓冲池实训演练..." class="w-full bg-white border border-slate-200 rounded-xl p-3 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"></textarea>
          </div>
        </div>

        <!-- 24 小时脱敏流转规则提醒 (US-14) -->
        <div class="p-3 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-[11px] flex items-center gap-2">
          <ShieldCheck class="w-4 h-4 text-amber-600 flex-shrink-0" />
          <span>依据项目规范，正式提交后将进入 <b>24小时延迟脱敏流转期</b>，脱敏归档后对任课教师公开，防止激化师生矛盾。</span>
        </div>

        <!-- 按钮操作组 -->
        <div class="flex items-center justify-end gap-2.5 pt-2">
          <button @click="showEvaluateModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition">取消</button>
          <button @click="handleSaveEvaluation(true)" class="px-4 py-2 bg-slate-200 hover:bg-slate-300 text-slate-800 rounded-xl text-xs font-semibold transition">暂存草稿 (US-13)</button>
          <button @click="handleSaveEvaluation(false)" class="px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition">正式提交 (开启24h脱敏)</button>
        </div>
      </div>
    </div>

    <!-- 弹窗：听课前课件在线免密预览 (US-09) -->
    <div v-if="showPreviewModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-2xl p-6 shadow-modal space-y-4">
        <div class="flex items-center justify-between border-b border-slate-200 pb-3">
          <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
            <FileText class="w-4 h-4 text-indigo-600" /> 听课前课件大纲免密预审
          </h3>
          <button @click="showPreviewModal = false" class="text-slate-400 hover:text-slate-600 text-lg">✕</button>
        </div>

        <div v-if="courseResources.length === 0" class="p-8 text-center text-xs text-slate-400">
          该课程任课教师尚未挂载课件资源
        </div>
        <div v-else class="space-y-2 max-h-72 overflow-y-auto">
          <div v-for="r in courseResources" :key="r.id" class="p-3 bg-slate-50 border border-slate-200 rounded-xl flex items-center justify-between text-xs">
            <div>
              <span class="font-semibold text-slate-900">{{ r.resourceName }}</span>
              <div class="text-[11px] text-slate-500 mt-0.5">章节：{{ r.chapter }} · 环节：{{ r.tag }} · 大小：{{ r.fileSize }}</div>
            </div>
            <span class="px-2.5 py-1 bg-emerald-50 text-emerald-700 text-[10px] font-medium rounded-lg border border-emerald-200">
              免密已授权 · 动态水印
            </span>
          </div>
        </div>

        <div class="text-right pt-2">
          <button @click="showPreviewModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold transition">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ShieldCheck,
  Search,
  AlertTriangle,
  FileText,
  Video,
  Edit3,
  AlertCircle
} from 'lucide-vue-next'
import { courseApi, supervisionApi, resourceApi } from '../api'
import type { CourseOffering, SupervisionDashboardVO, SupervisionAlertVO, CourseResource } from '../api/types'

const emit = defineEmits<{
  (e: 'jump-to-attendance', offeringId: number): void
}>()

const activeTab = ref('search')
const tabs = [
  { key: 'search', label: '待督导目标课程多维检索 (US-06)', iconComp: Search },
  { key: 'alerts', label: '教学质量预警中心 (US-16)', iconComp: AlertTriangle }
]


const offeringList = ref<CourseOffering[]>([])
const filterParams = ref({ keyword: '', teacher: '', term: '', className: '' })
const availableTerms = ref<string[]>([])

const availableClasses = computed(() => {
  return Array.from(new Set(offeringList.value.map(o => o.className).filter(Boolean)))
})

const filteredOfferings = computed(() => {
  return offeringList.value.filter(o => {
    if (filterParams.value.className && o.className !== filterParams.value.className) return false
    return true
  })
})

const enterLiveSupervision = (offeringId: number) => {
  emit('jump-to-attendance', offeringId)
}

const dashboardMetrics = ref<SupervisionDashboardVO | null>(null)
const alertList = ref<SupervisionAlertVO[]>([])

const showEvaluateModal = ref(false)
const currentOfferingForEval = ref<CourseOffering | null>(null)
const evalForm = ref({
  listenTopic: '',
  evaluateDate: new Date().toISOString().split('T')[0],
  scoreAttitude: 24.0,
  scoreContent: 23.5,
  scoreMethod: 23.0,
  scoreEffect: 23.5,
  highlights: '',
  suggestions: ''
})

const calcTotalScore = computed(() => {
  const f = evalForm.value
  return (f.scoreAttitude + f.scoreContent + f.scoreMethod + f.scoreEffect).toFixed(1)
})

const showPreviewModal = ref(false)
const courseResources = ref<CourseResource[]>([])

const loadOfferings = async () => {
  try {
    const list = await courseApi.getOfferings(
      filterParams.value.term || undefined,
      filterParams.value.teacher || undefined,
      filterParams.value.keyword || undefined
    )
    offeringList.value = list
    if (availableTerms.value.length === 0 && list.length > 0) {
      availableTerms.value = Array.from(new Set(list.map(o => o.academicTerm).filter(Boolean)))
    }
  } catch (e) {
    console.error('加载开课列表失败', e)
  }
}

const loadAnalytics = async () => {
  try {
    dashboardMetrics.value = await supervisionApi.getDashboard()
    alertList.value = await supervisionApi.getAlerts()
  } catch (e) {
    console.error('加载督导分析失败', e)
  }
}

const openPreviewResources = async (courseId: number) => {
  try {
    courseResources.value = await resourceApi.search({ courseId })
    showPreviewModal.value = true
  } catch (e) {
    alert('获取课件资源失败')
  }
}

const openEvaluateForm = (off: CourseOffering) => {
  currentOfferingForEval.value = off
  evalForm.value = {
    listenTopic: '随堂听评课',
    evaluateDate: new Date().toISOString().split('T')[0],
    scoreAttitude: 24.0,
    scoreContent: 23.5,
    scoreMethod: 23.0,
    scoreEffect: 23.5,
    highlights: '教师思路清晰，学生课堂抬头率高，互动热烈。',
    suggestions: '建议在关键节点继续深化启发式提问。'
  }
  showEvaluateModal.value = true
}

const handleSaveEvaluation = async (isDraft: boolean) => {
  try {
    const payload = {
      offeringId: currentOfferingForEval.value?.id,
      supervisorName: '沈越 (校级教学督导)',
      evaluateDate: evalForm.value.evaluateDate,
      listenTopic: evalForm.value.listenTopic,
      scoreAttitude: evalForm.value.scoreAttitude,
      scoreContent: evalForm.value.scoreContent,
      scoreMethod: evalForm.value.scoreMethod,
      scoreEffect: evalForm.value.scoreEffect,
      highlights: evalForm.value.highlights,
      suggestions: evalForm.value.suggestions,
      isDraft
    }

    const res = await supervisionApi.submit(payload)
    showEvaluateModal.value = false
    alert(res.message || '评价提交成功！')
    loadAnalytics()
  } catch (e: any) {
    alert(e.response?.data?.message || '提交评价失败')
  }
}

onMounted(() => {
  loadOfferings()
  loadAnalytics()
})
</script>
