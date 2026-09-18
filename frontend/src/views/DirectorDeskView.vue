<template>
  <div class="space-y-6">
    <!-- 顶部状态栏 -->
    <div class="minimal-card p-6 flex flex-wrap items-center justify-between gap-4">
      <div>
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600 shadow-subtle">
            <Briefcase class="w-5 h-5" />
          </div>
          <div>
            <h1 class="text-xl font-bold tracking-tight text-slate-900 flex items-center gap-2.5">
              教研室主任工作台
              <span class="text-xs px-2.5 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200 font-medium">管理中心 (Director Portal)</span>
            </h1>
            <p class="text-xs text-slate-500 mt-1">负责本专业全量课程档案规范底座、统筹排课冲突防范、工程教育认证 12 项指标点审查与年度质量分析报表导出</p>
          </div>
        </div>
      </div>
      <div class="flex items-center gap-2.5">
        <a :href="supervisionApi.getExportReportUrl()" download class="px-3.5 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-semibold flex items-center gap-1.5 shadow-subtle transition cursor-pointer">
          <Download class="w-3.5 h-3.5" /> 导出年度质量报表 (CSV/Excel)
        </a>
        <button @click="openAddCourseModal" class="px-3.5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold flex items-center gap-1.5 shadow-subtle transition">
          <Plus class="w-3.5 h-3.5" /> 新增专业课程档案
        </button>
      </div>
    </div>

    <!-- 子导航标签 -->
    <div class="flex items-center gap-1.5 border-b border-slate-200 pb-3">
      <button 
        v-for="tab in tabs" 
        :key="tab.key" 
        @click="activeTab = tab.key"
        :class="['px-3.5 py-1.5 rounded-xl text-xs font-medium transition flex items-center gap-1.5', 
                 activeTab === tab.key ? 'bg-indigo-50 text-indigo-700 border border-indigo-200 font-semibold shadow-subtle' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100']"
      >
        <component :is="tab.iconComp" class="w-3.5 h-3.5" :class="activeTab === tab.key ? 'text-indigo-600' : 'text-slate-400'" />
        {{ tab.label }}
      </button>
    </div>

    <!-- Tab 1: 全量课程档案管理 (US-01 / US-02) -->
    <div v-if="activeTab === 'courses'" class="space-y-4">
      <div class="minimal-card p-5">
        <div class="flex flex-wrap items-center justify-between gap-4 mb-4">
          <div class="flex items-center gap-3">
            <div class="relative">
              <input 
                v-model="courseFilter.keyword" 
                @input="loadCourses" 
                placeholder="搜索课程名称 / 代码 / 先修课程..." 
                class="bg-white border border-slate-200 rounded-xl pl-8 pr-3 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 w-64 shadow-subtle"
              />
              <Search class="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2.5" />
            </div>
            <select 
              v-model="courseFilter.courseType" 
              @change="loadCourses"
              class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-indigo-500 shadow-subtle"
            >
              <option value="">全部课程性质</option>
              <option value="专业核心课">专业核心课</option>
              <option value="专业基础课">专业基础课</option>
              <option value="通识必修课">通识必修课</option>
            </select>
          </div>
          <span class="text-xs text-slate-500">共检索到 <b class="text-indigo-600 font-bold">{{ courseList.length }}</b> 门标准化课程档案</span>
        </div>

        <!-- 课程表格 -->
        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs text-slate-700">
            <thead class="bg-slate-50 text-slate-600 uppercase font-semibold border-b border-slate-200">
              <tr>
                <th class="py-3 px-4">课程代码</th>
                <th class="py-3 px-4">课程名称</th>
                <th class="py-3 px-4">院系教研室</th>
                <th class="py-3 px-4">学分 / 学时</th>
                <th class="py-3 px-4">课程性质</th>
                <th class="py-3 px-4">先修关系</th>
                <th class="py-3 px-4 text-right">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr v-for="c in courseList" :key="c.id" class="hover:bg-slate-50/80 transition-colors">
                <td class="py-3 px-4 font-mono font-semibold text-indigo-600">{{ c.courseCode }}</td>
                <td class="py-3 px-4 font-semibold text-slate-900">
                  {{ c.courseName }}
                </td>
                <td class="py-3 px-4 text-slate-500">{{ c.department }}</td>
                <td class="py-3 px-4 text-slate-600">{{ c.credits }} 学分 / {{ c.hours }}h (理论{{ c.theoryHours }} + 实验{{ c.practiceHours }})</td>
                <td class="py-3 px-4">
                  <span class="px-2 py-0.5 rounded-full text-[10px] bg-slate-100 text-slate-600 border border-slate-200">
                    {{ c.courseType }}
                  </span>
                </td>
                <td class="py-3 px-4 text-slate-500 truncate max-w-xs">{{ c.prerequisites || '无' }}</td>
                <td class="py-3 px-4 text-right space-x-2">
                  <button @click="viewCourseDetail(c)" class="text-indigo-600 hover:text-indigo-800 font-medium">大纲</button>
                  <button @click="editCourse(c)" class="text-amber-600 hover:text-amber-800 font-medium">编辑</button>
                  <button @click="removeCourse(c.id)" class="text-rose-600 hover:text-rose-800 font-medium">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Tab 2: 集中排课统筹与冲突检测看板 (US-03) -->
    <div v-if="activeTab === 'schedules'" class="space-y-4">
      <div class="minimal-card p-5">
        <div class="flex flex-wrap items-center justify-between gap-4 mb-5">
          <div>
            <h2 class="text-sm font-semibold text-slate-900 flex items-center gap-2">
              <Calendar class="w-4 h-4 text-indigo-600" /> 开课排课统筹看板 (支持周次/教室/人次智能联动与防冲突检测)
            </h2>
            <p class="text-xs text-slate-500 mt-0.5">联动文管 A447、信息馆 B201 等教室与各教师班额，防冲突算法实时守护</p>
          </div>
          <button @click="openAddScheduleModal" class="px-3.5 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition flex items-center gap-1">
            <Plus class="w-3.5 h-3.5" /> 新增排课调度
          </button>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div v-for="s in scheduleList" :key="s.id" class="bg-white border border-slate-200 rounded-xl p-4 hover:border-indigo-300 hover:shadow-card transition shadow-subtle">
            <div class="flex items-start justify-between">
              <div>
                <span class="text-[10px] px-2 py-0.5 rounded bg-indigo-50 text-indigo-700 border border-indigo-100 font-mono">
                  {{ s.offering?.course?.courseCode }}
                </span>
                <h3 class="text-sm font-bold text-slate-900 mt-1.5">{{ s.offering?.course?.courseName }}</h3>
                <p class="text-xs text-slate-500 mt-0.5">任课教师：<b class="text-slate-800">{{ s.offering?.teacherName }}</b> ({{ s.offering?.className }})</p>
              </div>
              <span class="text-xs px-2 py-1 bg-slate-100 text-slate-600 border border-slate-200 rounded-lg font-mono">
                {{ s.offering?.studentCount }} 人额
              </span>
            </div>

            <div class="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-600">
              <div class="flex items-center gap-1.5">
                <span class="text-emerald-700 font-semibold bg-emerald-50 px-2 py-0.5 rounded border border-emerald-100">{{ s.classroom }}</span>
              </div>
              <div class="text-right">
                <span class="text-slate-600">周{{ s.dayOfWeek }} 第{{ s.startPeriod }}-{{ s.endPeriod }}节</span>
                <div class="text-[10px] text-slate-400 font-mono">({{ s.weekRange }})</div>
              </div>
            </div>

            <div class="mt-3 text-right">
              <button @click="removeSchedule(s.id)" class="text-rose-600 hover:text-rose-700 text-[11px] font-medium">取消排课</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 3: 工程教育认证 12 条毕业要求指标点矩阵 (US-05) -->
    <div v-if="activeTab === 'indicators'" class="space-y-4">
      <div class="minimal-card p-5">
        <div class="flex flex-wrap items-center justify-between gap-4 mb-4">
          <div>
            <h2 class="text-sm font-semibold text-slate-900 flex items-center gap-2">
              <Target class="w-4 h-4 text-indigo-600" /> 东北大学工程教育专业认证 12 项毕业要求指标点矩阵
            </h2>
            <p class="text-xs text-slate-500 mt-0.5">审查并锁定专业培养方案 12 项通用标准指标点对课程大纲的支撑度 (H强/M中/L弱)</p>
          </div>
          <div class="flex items-center gap-2">
            <div class="flex items-center gap-1.5 bg-slate-50 border border-slate-200 rounded-xl px-3 py-1.5 shadow-subtle">
              <span class="text-xs text-slate-500">选择审查课程:</span>
              <select 
                v-model="selectedCourseIdForIndicator" 
                @change="loadIndicatorsForSelectedCourse" 
                class="bg-transparent text-xs text-indigo-700 font-semibold focus:outline-none min-w-[200px] cursor-pointer"
              >
                <option v-if="courseList.length === 0" value="" disabled class="text-slate-400">
                  加载课程中...
                </option>
                <option 
                  v-for="c in courseList" 
                  :key="c.id" 
                  :value="c.id" 
                  class="text-slate-700 py-1.5"
                >
                  {{ c.courseCode }} - {{ c.courseName }}
                </option>
              </select>
            </div>
            <button @click="lockCurrentSyllabus" class="px-3.5 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition flex items-center gap-1.5">
              <Lock class="w-3.5 h-3.5" /> 审查锁定本版大纲
            </button>
          </div>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs text-slate-700">
            <thead class="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200">
              <tr>
                <th class="py-3 px-4">指标点编号</th>
                <th class="py-3 px-4">毕业要求大项</th>
                <th class="py-3 px-4">指标点分解表述</th>
                <th class="py-3 px-4">支撑权重</th>
                <th class="py-3 px-4">对应课程目标</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr v-if="indicatorList.length === 0">
                <td colspan="5" class="py-12 text-center text-slate-400">
                  <div class="flex flex-col items-center justify-center gap-2">
                    <FileText class="w-6 h-6 text-slate-300" />
                    <span>该课程暂未录入毕业要求指标点矩阵（当前《软件项目管理》已配置完整认证指标点）</span>
                  </div>
                </td>
              </tr>
              <tr v-for="ind in indicatorList" :key="ind.id" class="hover:bg-slate-50/80">
                <td class="py-3 px-4 font-mono font-semibold text-indigo-600">{{ ind.indicatorCode }}</td>
                <td class="py-3 px-4 text-slate-900 font-medium">{{ ind.requirementCategory }}</td>
                <td class="py-3 px-4 text-slate-600 leading-relaxed">{{ ind.indicatorDescription }}</td>
                <td class="py-3 px-4">
                  <span :class="['px-2.5 py-1 rounded-md text-[10px] font-bold font-mono', 
                                ind.supportWeight === 'H' ? 'bg-rose-50 text-rose-700 border border-rose-200' : 
                                (ind.supportWeight === 'M' ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'bg-slate-100 text-slate-600 border border-slate-200')]">
                    {{ ind.supportWeight }} ({{ ind.supportWeight === 'H' ? '强支撑' : (ind.supportWeight === 'M' ? '中等' : '弱支撑') }})
                  </span>
                </td>
                <td class="py-3 px-4 text-slate-500 font-mono">{{ ind.targetGoal || '目标1' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 弹窗：新增/编辑课程 -->
    <div v-if="showCourseModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-xl p-6 shadow-modal space-y-4">
        <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
          <BookOpen class="w-4 h-4 text-indigo-600" /> {{ currentCourseForm.id ? '编辑课程档案' : '录入新课程档案 (US-01)' }}
        </h3>
        <div class="grid grid-cols-2 gap-3 text-xs">
          <div>
            <label class="text-slate-600 block mb-1">课程代码 (唯一)</label>
            <input v-model="currentCourseForm.courseCode" placeholder="如 CS3001" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">课程名称</label>
            <input v-model="currentCourseForm.courseName" placeholder="如 软件项目管理" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">学分</label>
            <input v-model.number="currentCourseForm.credits" type="number" step="0.5" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">总学时</label>
            <input v-model.number="currentCourseForm.hours" type="number" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">教研室</label>
            <input v-model="currentCourseForm.department" placeholder="如 软件工程教研室" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">课程性质</label>
            <select v-model="currentCourseForm.courseType" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle">
              <option value="专业核心课">专业核心课</option>
              <option value="专业基础课">专业基础课</option>
              <option value="专业选修课">专业选修课</option>
            </select>
          </div>
          <div class="col-span-2">
            <label class="text-slate-600 block mb-1">先修关系说明</label>
            <input v-model="currentCourseForm.prerequisites" placeholder="如 《软件工程导论》" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div class="col-span-2">
            <label class="text-slate-600 block mb-1">课程简介与教学目标 (US-02)</label>
            <textarea v-model="currentCourseForm.description" rows="3" class="w-full bg-white border border-slate-200 rounded-xl p-3 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"></textarea>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2.5 pt-2">
          <button @click="showCourseModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition">取消</button>
          <button @click="handleSaveCourse" class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition">保存并入库</button>
        </div>
      </div>
    </div>

    <!-- 弹窗：新增排课调度 (含冲突拦截) -->
    <div v-if="showScheduleModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-lg p-6 shadow-modal space-y-4">
        <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
          <Calendar class="w-4 h-4 text-indigo-600" /> 新增排课调度 (US-03)
        </h3>
        <div class="space-y-3 text-xs">
          <div>
            <label class="text-slate-600 block mb-1">选择开课班次</label>
            <select v-model="currentScheduleForm.offeringId" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle">
              <option v-for="off in offeringList" :key="off.id" :value="off.id">
                {{ off.course.courseName }} - {{ off.teacherName }} ({{ off.className }}, {{ off.studentCount }}人)
              </option>
            </select>
          </div>
          <div>
            <label class="text-slate-600 block mb-1">上课教室 (如 文管 A447, 信息馆 B201)</label>
            <input v-model="currentScheduleForm.classroom" placeholder="文管 A447" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div class="grid grid-cols-3 gap-2">
            <div>
              <label class="text-slate-600 block mb-1">星期几</label>
              <select v-model.number="currentScheduleForm.dayOfWeek" class="w-full bg-white border border-slate-200 rounded-xl px-2 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle">
                <option :value="1">周一</option>
                <option :value="2">周二</option>
                <option :value="3">周三</option>
                <option :value="4">周四</option>
                <option :value="5">周五</option>
              </select>
            </div>
            <div>
              <label class="text-slate-600 block mb-1">起始节次</label>
              <input v-model.number="currentScheduleForm.startPeriod" type="number" min="1" max="12" class="w-full bg-white border border-slate-200 rounded-xl px-2 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle" />
            </div>
            <div>
              <label class="text-slate-600 block mb-1">结束节次</label>
              <input v-model.number="currentScheduleForm.endPeriod" type="number" min="1" max="12" class="w-full bg-white border border-slate-200 rounded-xl px-2 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle" />
            </div>
          </div>
        </div>

        <div v-if="scheduleErrorMessage" class="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-center gap-2">
          <AlertCircle class="w-4 h-4 text-rose-600 flex-shrink-0" />
          <span>{{ scheduleErrorMessage }}</span>
        </div>

        <div class="flex items-center justify-end gap-2.5 pt-2">
          <button @click="showScheduleModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition">取消</button>
          <button @click="handleSaveSchedule" class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition">校验并排课</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  Briefcase,
  Download,
  Plus,
  Search,
  Calendar,
  Target,
  Lock,
  BookOpen,
  FileText,
  AlertCircle
} from 'lucide-vue-next'
import { courseApi, scheduleApi, syllabusApi, supervisionApi } from '../api'
import type { Course, CourseSchedule, GraduationIndicator, CourseOffering } from '../api/types'

const activeTab = ref('courses')
const tabs = [
  { key: 'courses', label: '专业全量课程档案 (US-01)', iconComp: BookOpen },
  { key: 'schedules', label: '开课排课统筹看板 (US-03)', iconComp: Calendar },
  { key: 'indicators', label: '12项毕业要求指标点矩阵 (US-05)', iconComp: Target }
]


const courseList = ref<Course[]>([])
const scheduleList = ref<CourseSchedule[]>([])
const offeringList = ref<CourseOffering[]>([])
const indicatorList = ref<GraduationIndicator[]>([])

const courseFilter = ref({ keyword: '', courseType: '' })
const selectedCourseIdForIndicator = ref<number | ''>('')

const showCourseModal = ref(false)
const currentCourseForm = ref<any>({})

const showScheduleModal = ref(false)
const currentScheduleForm = ref<any>({ dayOfWeek: 3, startPeriod: 3, endPeriod: 4, startWeek: 1, endWeek: 16, classroom: '文管 A447' })
const scheduleErrorMessage = ref('')

const loadCourses = async () => {
  try {
    const list = await courseApi.getAll(courseFilter.value.keyword, courseFilter.value.courseType)
    courseList.value = Array.isArray(list) ? list : []
    if (courseList.value.length > 0) {
      const match = courseList.value.find(c => c.id === selectedCourseIdForIndicator.value)
      if (!match) {
        selectedCourseIdForIndicator.value = courseList.value[0].id
      }
      await loadIndicatorsForSelectedCourse()
    } else {
      indicatorList.value = []
    }
  } catch (e) {
    console.error('加载课程列表失败', e)
  }
}

const loadSchedules = async () => {
  try {
    scheduleList.value = await scheduleApi.getAll()
    offeringList.value = await courseApi.getOfferings()
  } catch (e) {
    console.error('加载排课看板失败', e)
  }
}

const loadIndicatorsForSelectedCourse = async () => {
  if (!selectedCourseIdForIndicator.value) {
    indicatorList.value = []
    return
  }
  try {
    const res = await syllabusApi.getIndicators(Number(selectedCourseIdForIndicator.value))
    indicatorList.value = Array.isArray(res) ? res : []
  } catch (e) {
    console.error('加载指标点失败', e)
    indicatorList.value = []
  }
}

const openAddCourseModal = () => {
  currentCourseForm.value = {
    courseCode: '',
    courseName: '',
    department: '软件工程教研室',
    credits: 3.0,
    hours: 48,
    theoryHours: 36,
    practiceHours: 12,
    courseType: '专业核心课',
    prerequisites: '',
    description: '',
    objectives: '',
    assessmentMethod: ''
  }
  showCourseModal.value = true
}

const editCourse = (c: Course) => {
  currentCourseForm.value = { ...c }
  showCourseModal.value = true
}

const handleSaveCourse = async () => {
  try {
    await courseApi.save(currentCourseForm.value)
    showCourseModal.value = false
    loadCourses()
  } catch (e: any) {
    alert(e.response?.data?.message || '保存失败')
  }
}

const removeCourse = async (id: number) => {
  if (confirm('确认删除该课程档案？')) {
    await courseApi.delete(id)
    loadCourses()
  }
}

const openAddScheduleModal = () => {
  scheduleErrorMessage.value = ''
  currentScheduleForm.value = {
    offeringId: offeringList.value[0]?.id || 1,
    classroom: '文管 A447',
    dayOfWeek: 3,
    startPeriod: 3,
    endPeriod: 4,
    startWeek: 1,
    endWeek: 16
  }
  showScheduleModal.value = true
}

const handleSaveSchedule = async () => {
  scheduleErrorMessage.value = ''
  try {
    await scheduleApi.save(currentScheduleForm.value)
    showScheduleModal.value = false
    loadSchedules()
  } catch (e: any) {
    scheduleErrorMessage.value = e.response?.data?.message || '排课失败，可能存在教室冲突！'
  }
}

const removeSchedule = async (id: number) => {
  if (confirm('确认取消该项排课？')) {
    await scheduleApi.delete(id)
    loadSchedules()
  }
}

const lockCurrentSyllabus = async () => {
  if (!selectedCourseIdForIndicator.value) {
    alert('请先选择需要锁定的课程！')
    return
  }
  try {
    const latest = await syllabusApi.getLatest(Number(selectedCourseIdForIndicator.value))
    if (latest) {
      await syllabusApi.lock(latest.id, '教研室主任 (周宇斌)')
      alert(`《${latest.course?.courseName || '该课程'}》大纲版本审查完成并成功锁定！`)
    } else {
      alert('该课程暂未发布教学大纲，无法进行审查锁定')
    }
  } catch (e) {
    alert('审查锁定失败，请重试')
  }
}

const viewCourseDetail = (c: Course) => {
  alert(`【${c.courseName} (${c.courseCode})】\n\n教学目标：\n${c.objectives || '暂无'}\n\n考核方式：\n${c.assessmentMethod || '暂无'}`)
}

onMounted(() => {
  loadCourses()
  loadSchedules()
})
</script>
