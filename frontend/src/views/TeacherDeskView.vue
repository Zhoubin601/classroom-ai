<template>
  <div class="space-y-6">
    <!-- 顶部任课教师身份卡 -->
    <div class="minimal-card p-6 flex flex-wrap items-center justify-between gap-4">
      <div class="flex items-center gap-4">
        <div class="w-12 h-12 rounded-xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600 shadow-subtle">
          <BookOpen class="w-6 h-6" />
        </div>
        <div>
          <div class="flex items-center gap-3">
            <h1 class="text-xl font-bold text-slate-900">{{ currentTeacher }} 老师工作台</h1>
            <span class="text-xs px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-medium">
              {{ currentOffering?.className ? `${currentOffering.className} 主讲教师` : '专业任课教师' }}
            </span>
          </div>
          <p class="text-xs text-slate-500 mt-1">负责{{ currentCourse?.courseName ? `《${currentCourse.courseName}》` : '课程' }}大纲目标发布、课件教案上传、听课督导反馈复盘与 BOPPPS 改进闭环</p>
        </div>
      </div>

      <!-- 快速切换任课教师模拟 -->
      <div class="flex items-center gap-2">
        <span class="text-xs text-slate-500">切换教师视角：</span>
        <select v-model="currentTeacher" @change="onTeacherChange" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-800 shadow-subtle focus:outline-none focus:border-indigo-500">
          <option v-for="t in teacherList" :key="t.name" :value="t.name">
            {{ t.name }} 老师 ({{ t.courseName }})
          </option>
        </select>
      </div>
    </div>

    <!-- 顶部授课人次与学期宏观数据 (US-04) -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
      <div class="minimal-card p-5">
        <span class="text-xs text-slate-500 font-medium">当前主讲课程</span>
        <div class="text-base font-bold text-slate-900 mt-1 truncate">{{ currentCourse?.courseName ? `《${currentCourse.courseName}》` : '暂无主讲课程' }}</div>
        <span class="text-[11px] text-indigo-600 font-medium mt-1 block truncate">{{ scheduleDisplayText }}</span>
      </div>
      <div class="minimal-card p-5">
        <span class="text-xs text-slate-500 font-medium">本学期授课班额 (US-04)</span>
        <div class="text-2xl font-bold text-emerald-600 font-mono mt-1">{{ currentOffering ? `${currentOffering.studentCount} 人次` : '0 人次' }}</div>
        <span class="text-[11px] text-slate-500 mt-1 block">{{ currentOffering?.className || '暂无班级' }}</span>
      </div>
      <div class="minimal-card p-5">
        <span class="text-xs text-slate-500 font-medium">督导听课综合得分 (US-17)</span>
        <div class="text-2xl font-bold text-indigo-600 font-mono mt-1">{{ radarData?.evaluationCount ? radarData.overallScore + ' 分' : '暂无评价' }}</div>
        <span class="text-[11px] text-emerald-700 font-medium mt-1 block">已公开评价 {{ radarData?.evaluationCount ?? 0 }} 次</span>
      </div>
      <div class="minimal-card p-5">
        <span class="text-xs text-slate-500 font-medium">课件资源总挂载量 (US-07)</span>
        <div class="text-2xl font-bold text-amber-600 font-mono mt-1">{{ myResources.length }} 份</div>
        <span class="text-[11px] text-slate-500 mt-1 block">含 PPTX/PDF 多版本</span>
      </div>
    </div>

    <!-- 主体：左右分栏 -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <!-- 左栏：教学资源多版本上传与大纲在线编辑 (占 7 列) -->
      <div class="lg:col-span-7 space-y-6">
        <!-- 课件教案上传管理 (US-07 / US-08 / US-11) -->
        <div class="minimal-card p-5">
          <div class="flex items-center justify-between mb-4">
            <div>
              <h2 class="text-sm font-semibold text-slate-900 flex items-center gap-2">
                <Folder class="w-4 h-4 text-indigo-600" /> 教学资源按章挂载与版本演进 (US-07)
              </h2>
              <p class="text-xs text-slate-500 mt-0.5">单文件上限 100MB，支持 PDF/PPT/Word 批量按章挂载与只读水印</p>
            </div>
            <button @click="showUploadModal = true" class="px-3.5 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition flex items-center gap-1">
              <Plus class="w-3.5 h-3.5" /> 上传新课件/教案
            </button>
          </div>

          <!-- 环节标签过滤 (US-08) -->
          <div class="flex items-center gap-2 mb-3">
            <span class="text-xs text-slate-500 font-medium">环节标签过滤：</span>
            <button 
              v-for="tag in ['全部', '理论', '实验', '研讨']" 
              :key="tag" 
              @click="selectedTag = tag === '全部' ? '' : tag; loadMyResources()"
              :class="['px-2.5 py-1 rounded-lg text-xs font-medium transition', (selectedTag === tag || (!selectedTag && tag === '全部')) ? 'bg-indigo-600 text-white shadow-subtle' : 'bg-slate-100 text-slate-600 hover:bg-slate-200']"
            >
              {{ tag }}
            </button>
          </div>

          <!-- 资源列表 -->
          <div class="space-y-2">
            <div v-if="myResources.length === 0" class="py-8 text-center text-slate-400 text-xs bg-slate-50 border border-dashed border-slate-200 rounded-xl">
              该环节暂无课件教案资源，请点击右上角上传挂载
            </div>
            <div v-for="res in myResources" :key="res.id" class="p-3 bg-white border border-slate-200 rounded-xl flex items-center justify-between hover:border-indigo-300 hover:shadow-subtle transition shadow-subtle">
              <div class="flex items-center gap-3">
                <div class="w-9 h-9 rounded-lg bg-indigo-50 border border-indigo-100 text-indigo-600 flex items-center justify-center flex-shrink-0">
                  <FileText class="w-4 h-4" />
                </div>
                <div>
                  <div class="flex items-center gap-2">
                    <span class="text-xs font-bold text-slate-900">{{ res.resourceName }}</span>
                    <span class="text-[10px] px-1.5 py-0.5 rounded bg-indigo-50 text-indigo-700 border border-indigo-100 font-mono">{{ res.version }}</span>
                    <span class="text-[10px] px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">{{ res.tag }}环节</span>
                  </div>
                  <div class="text-[11px] text-slate-500 mt-1 flex items-center gap-3">
                    <span>章节：{{ res.chapter }}</span>
                    <span>大小：{{ res.fileSize }}</span>
                    <span class="text-emerald-600 flex items-center gap-1 font-medium">
                      <ShieldCheck class="w-3 h-3" /> 动态水印防护已就绪
                    </span>
                  </div>
                </div>
              </div>
              <div class="flex items-center gap-2">
                <button @click="previewResource(res)" class="px-2.5 py-1 bg-slate-100 hover:bg-slate-200 text-indigo-700 border border-slate-200 rounded-lg text-xs font-medium transition flex items-center gap-1">
                  <Eye class="w-3 h-3" /> 免密预览
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 在线课程大纲与目标发布 (US-02) -->
        <div class="minimal-card p-5 space-y-3">
          <div class="flex items-center justify-between">
            <h2 class="text-sm font-semibold text-slate-900 flex items-center gap-2">
              <BookOpen class="w-4 h-4 text-indigo-600" /> 课程大纲、目标与考核方式在线发布 (US-02)
            </h2>
            <button @click="saveSyllabusText" class="px-3 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold shadow-subtle transition">
              发布保存
            </button>
          </div>
          <div>
            <label class="text-xs text-slate-600 font-medium block mb-1">教学目标说明 (支持工程认证要求关联)</label>
            <textarea v-model="syllabusForm.objectives" rows="3" class="w-full bg-white border border-slate-200 rounded-xl p-3 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" placeholder="描述课程知识与能力目标..."></textarea>
          </div>
          <div>
            <label class="text-xs text-slate-600 font-medium block mb-1">考核与成绩评定方式</label>
            <input v-model="syllabusForm.assessmentMethod" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" placeholder="平时实验 30% + 答辩 30% + 期末 40%" />
          </div>
        </div>
      </div>

      <!-- 右栏：教学质量自我复盘 4 维雷达图与评语词云 (占 5 列) (US-17 / US-21) -->
      <div class="lg:col-span-5 space-y-6">
        <div class="minimal-card p-5">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-sm font-semibold text-slate-900 flex items-center gap-2">
              <Target class="w-4 h-4 text-indigo-600" /> 督导随堂评价 4 维雷达图 (US-17)
            </h2>
            <span class="text-[11px] text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200 font-medium">BOPPPS 教学模型</span>
          </div>

          <!-- 雷达图容器 -->
          <div ref="radarChartRef" class="w-full h-64"></div>

          <!-- 4 维均分指标条 -->
          <div class="grid grid-cols-2 gap-2 text-xs mt-3 pt-3 border-t border-slate-100">
            <div class="bg-slate-50 p-2.5 rounded-xl border border-slate-200">
              <span class="text-slate-500 block text-[10px]">教学态度 (25分满分)</span>
              <span class="text-sm font-bold text-indigo-700 font-mono">{{ radarData?.attitudeScore ?? 0 }} 分</span>
            </div>
            <div class="bg-slate-50 p-2.5 rounded-xl border border-slate-200">
              <span class="text-slate-500 block text-[10px]">教学内容 (25分满分)</span>
              <span class="text-sm font-bold text-indigo-700 font-mono">{{ radarData?.contentScore ?? 0 }} 分</span>
            </div>
            <div class="bg-slate-50 p-2.5 rounded-xl border border-slate-200">
              <span class="text-slate-500 block text-[10px]">教学方法 (25分满分)</span>
              <span class="text-sm font-bold text-indigo-700 font-mono">{{ radarData?.methodScore ?? 0 }} 分</span>
            </div>
            <div class="bg-slate-50 p-2.5 rounded-xl border border-slate-200">
              <span class="text-slate-500 block text-[10px]">教学效果 (25分满分)</span>
              <span class="text-sm font-bold text-indigo-700 font-mono">{{ radarData?.effectScore ?? 0 }} 分</span>
            </div>
          </div>
        </div>

        <!-- 督导脱敏评价与 BOPPPS 改进建议推送 (US-14 / US-21) -->
        <div class="minimal-card p-5 space-y-4">
          <div>
            <h2 class="text-sm font-semibold text-slate-900 flex items-center gap-2">
              <Award class="w-4 h-4 text-indigo-600" /> 督导质性反馈与持续改进建议 (US-21)
            </h2>
            <p class="text-[11px] text-slate-500 mt-0.5">已执行 24 小时脱敏归档，保障客观公正交流</p>
          </div>

          <!-- 教学亮点 -->
          <div class="p-3.5 bg-emerald-50/70 border border-emerald-200 rounded-xl">
            <span class="text-xs font-bold text-emerald-800 block mb-1">课堂教学亮点</span>
            <ul class="text-xs text-slate-700 space-y-1 list-disc list-inside">
              <li v-for="(hl, i) in (radarData?.highlightList || [])" :key="i">{{ hl }}</li>
            </ul>
          </div>

          <!-- 改进建议 -->
          <div class="p-3.5 bg-indigo-50/70 border border-indigo-200 rounded-xl">
            <span class="text-xs font-bold text-indigo-800 block mb-1">BOPPPS 进阶教改建议</span>
            <ul class="text-xs text-slate-700 space-y-1 list-disc list-inside">
              <li v-for="(sg, i) in (radarData?.suggestionList || [])" :key="i">{{ sg }}</li>
            </ul>
          </div>

          <!-- 评语词云标签 -->
          <div>
            <span class="text-xs text-slate-600 font-medium block mb-2">督导高频评价热词 (词云统计)</span>
            <div class="flex flex-wrap gap-1.5">
              <span v-for="w in (radarData?.wordCloud || [])" :key="w.name" class="px-2.5 py-1 bg-slate-100 text-indigo-700 rounded-lg text-xs font-medium border border-slate-200">
                # {{ w.name }} ({{ w.value }})
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗：上传教学资源 -->
    <div v-if="showUploadModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-lg p-6 shadow-modal space-y-4">
        <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
          <Upload class="w-4 h-4 text-indigo-600" /> 上传教学课件/教案 (US-07)
        </h3>
        <div class="space-y-3 text-xs">
          <div>
            <label class="text-slate-600 block mb-1">所属章节 (按章挂载)</label>
            <input v-model="uploadForm.chapter" placeholder="如 第一章 软件项目管理概论" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">资源文件名称</label>
            <input v-model="uploadForm.resourceName" placeholder="如 第1讲-需求估算与甘特图.pptx" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="text-slate-600 block mb-1">文件格式</label>
              <select v-model="uploadForm.fileType" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle">
                <option value="PPTX">PPTX 演示文稿</option>
                <option value="PDF">PDF 文档</option>
                <option value="DOCX">DOCX 教案</option>
              </select>
            </div>
            <div>
              <label class="text-slate-600 block mb-1">环节标签 (US-08)</label>
              <select v-model="uploadForm.tag" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle">
                <option value="理论">理论授课</option>
                <option value="实验">实验实训</option>
                <option value="研讨">研讨互动</option>
              </select>
            </div>
          </div>
          <div class="p-3 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-[11px] flex items-center gap-1.5">
            <AlertCircle class="w-4 h-4 text-amber-600 flex-shrink-0" />
            <span>单文件上限 100MB (已包含自动注入东北大学动态只读水印)</span>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2.5 pt-2">
          <button @click="showUploadModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition">取消</button>
          <button @click="handleSaveResource" class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition">立即挂载</button>
        </div>
      </div>
    </div>

    <!-- 弹窗：免密在线预览与动态水印 (US-09 / US-11) -->
    <div v-if="previewModalVisible" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-3xl p-6 shadow-modal space-y-4">
        <div class="flex items-center justify-between border-b border-slate-200 pb-3">
          <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
            <FileText class="w-4 h-4 text-indigo-600" /> {{ currentPreviewRes?.resourceName }} (免密只读预览)
          </h3>
          <button @click="previewModalVisible = false" class="text-slate-400 hover:text-slate-600 text-lg">✕</button>
        </div>

        <!-- 模拟只读预览窗口与动态水印 -->
        <div class="relative w-full h-80 bg-slate-50 border border-slate-200 rounded-xl flex flex-col items-center justify-center overflow-hidden">
          <div class="absolute inset-0 flex flex-wrap items-center justify-center gap-12 opacity-25 pointer-events-none select-none text-xs text-indigo-400 font-mono rotate-[-25deg]">
            <span v-for="n in 12" :key="n">{{ currentPreviewRes?.dynamicWatermark || '东北大学软件学院 · 爱教学只读凭证' }}</span>
          </div>
          <FileText class="w-12 h-12 text-slate-400" />
          <p class="text-sm font-bold text-slate-900 mt-3">{{ currentPreviewRes?.resourceName }}</p>
          <p class="text-xs text-slate-500 mt-1">章节：{{ currentPreviewRes?.chapter }} · 环节：{{ currentPreviewRes?.tag }}</p>
          <div class="mt-4 px-4 py-1.5 rounded-full bg-emerald-50 text-emerald-700 text-xs border border-emerald-200 font-medium flex items-center gap-1.5">
            <ShieldCheck class="w-3.5 h-3.5 text-emerald-600" /> 免密在线安全翻页预览已就绪 · 防右键下载保护
          </div>
        </div>

        <div class="text-right">
          <button @click="previewModalVisible = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold transition">关闭预览</button>
        </div>
      </div>
    </div>
  </div>
</template>


<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import {
  BookOpen,
  Folder,
  Plus,
  FileText,
  ShieldCheck,
  Eye,
  Target,
  Award,
  Upload,
  AlertCircle
} from 'lucide-vue-next'
import * as echarts from 'echarts'
import { resourceApi, supervisionApi, courseApi, syllabusApi, scheduleApi } from '../api'
import type { CourseResource, CourseOffering, CourseSchedule, TeacherQualityRadarVO } from '../api/types'


const teacherList = ref<{ name: string; courseName: string }[]>([])
const currentTeacher = ref('郭军')
const currentOffering = ref<CourseOffering | null>(null)
const currentCourse = ref<any>(null)
const currentSchedule = ref<CourseSchedule | null>(null)
const currentSyllabus = ref<any>(null)
const selectedTag = ref('')
const myResources = ref<CourseResource[]>([])
const radarData = ref<TeacherQualityRadarVO | null>(null)
const radarChartRef = ref<HTMLDivElement | null>(null)
let radarChart: echarts.ECharts | null = null

const scheduleDisplayText = computed(() => {
  if (!currentSchedule.value) return '暂未排课调度'
  const s = currentSchedule.value
  return `${s.classroom} · 周${s.dayOfWeek} 第${s.startPeriod}-${s.endPeriod}节 (${s.weekRange})`
})

const syllabusForm = ref({
  objectives: '',
  assessmentMethod: ''
})

const showUploadModal = ref(false)
const uploadForm = ref({
  courseId: 1,
  chapter: '第一章 课程概论',
  resourceName: '',
  fileType: 'PPTX',
  tag: '理论',
  fileUrl: 'https://static.neu.edu.cn/sample.pptx',
  fileSize: '18.2 MB',
  fileSizeBytes: 19084083
})

const previewModalVisible = ref(false)
const currentPreviewRes = ref<CourseResource | null>(null)

const loadTeacherList = async () => {
  try {
    const allOfferings = await courseApi.getOfferings()
    const teachersMap = new Map<string, string>()
    allOfferings.forEach(off => {
      if (off.teacherName && !teachersMap.has(off.teacherName)) {
        teachersMap.set(off.teacherName, off.course?.courseName || '主讲课程')
      }
    })
    teacherList.value = Array.from(teachersMap.entries()).map(([name, courseName]) => ({ name, courseName }))
    if (teacherList.value.length > 0 && !teacherList.value.some(t => t.name === currentTeacher.value)) {
      currentTeacher.value = teacherList.value[0].name
    }
  } catch (e) {
    console.error('加载教师列表失败', e)
  }
}

const onTeacherChange = async () => {
  await loadCourse()
  await Promise.all([loadMyResources(), loadRadar()])
}

const loadCourse = async () => {
  currentCourse.value = null
  currentOffering.value = null
  currentSchedule.value = null
  currentSyllabus.value = null
  syllabusForm.value = { objectives: '', assessmentMethod: '' }

  try {
    const offerings = await courseApi.getOfferings(undefined, currentTeacher.value)
    if (offerings.length > 0) {
      currentOffering.value = offerings[0]
      currentCourse.value = offerings[0].course ?? null
      uploadForm.value.chapter = currentCourse.value ? `第一章 ${currentCourse.value.courseName}概论` : '第一章 概论'

      // 加载真实排课教室与时段
      try {
        const schedules = await scheduleApi.getAll(offerings[0].id)
        if (schedules && schedules.length > 0) {
          currentSchedule.value = schedules[0]
        }
      } catch (err) {
        console.warn('获取排课信息失败', err)
      }
    }
  } catch (e) {
    console.error('获取教师开课记录失败', e)
  }

  if (!currentCourse.value) return

  try {
    currentSyllabus.value = await syllabusApi.getLatest(currentCourse.value.id)
    syllabusForm.value = {
      objectives: currentSyllabus.value?.courseGoals ?? currentCourse.value.objectives ?? '',
      assessmentMethod: currentCourse.value.assessmentMethod ?? ''
    }
  } catch (e) {
    console.warn('获取大纲失败', e)
  }
}

const loadMyResources = async () => {
  try {
    const courseId = currentCourse.value?.id
    if (!courseId) { myResources.value = []; return }
    myResources.value = await resourceApi.search({ courseId, tag: selectedTag.value })
  } catch (e) {
    console.error('加载课件失败', e)
  }
}

const loadRadar = async () => {
  try {
    radarData.value = await supervisionApi.getRadar(currentTeacher.value)
    renderRadarChart()
  } catch (e) {
    console.error('加载雷达图数据失败', e)
  }
}

const renderRadarChart = () => {
  if (!radarChartRef.value) return
  if (!radarChart) {
    radarChart = echarts.init(radarChartRef.value)
  }

  const d = radarData.value
  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    radar: {
      indicator: [
        { name: '教学态度', max: 25 },
        { name: '教学内容', max: 25 },
        { name: '教学方法', max: 25 },
        { name: '教学效果', max: 25 }
      ],
      shape: 'polygon',
      splitNumber: 4,
      axisName: {
        color: '#475569',
        fontSize: 11
      },
      splitLine: {
        lineStyle: {
          color: '#e2e8f0'
        }
      },
      splitArea: {
        show: true,
        areaStyle: {
          color: ['#ffffff', '#f8fafc']
        }
      },
      axisLine: {
        lineStyle: {
          color: '#e2e8f0'
        }
      }
    },
    series: [
      {
        name: '督导评分维度',
        type: 'radar',
        data: [
          {
            value: [
              d?.attitudeScore ?? 0,
              d?.contentScore ?? 0,
              d?.methodScore ?? 0,
              d?.effectScore ?? 0
            ],
            name: '得分分析',
            areaStyle: {
              color: 'rgba(79, 70, 229, 0.18)'
            },
            lineStyle: {
              color: '#4f46e5',
              width: 2
            },
            itemStyle: {
              color: '#4f46e5'
            }
          }
        ]
      }
    ]
  }

  radarChart.setOption(option)
}

const previewResource = (res: CourseResource) => {
  currentPreviewRes.value = res
  previewModalVisible.value = true
}

const handleSaveResource = async () => {
  try {
    if (!currentCourse.value) throw new Error('该教师暂无开课记录')
    uploadForm.value.courseId = currentCourse.value.id
    await resourceApi.save(uploadForm.value)
    showUploadModal.value = false
    loadMyResources()
    alert('课件挂载成功！已自动添加动态水印')
  } catch (e: any) {
    alert(e.response?.data?.message || '上传失败')
  }
}

const saveSyllabusText = async () => {
  try {
    if (!currentCourse.value) throw new Error('该教师暂无开课记录')
    currentSyllabus.value = await syllabusApi.save({
      ...currentSyllabus.value, courseId: currentCourse.value.id,
      courseGoals: syllabusForm.value.objectives, authorTeacher: currentTeacher.value
    })
    currentCourse.value = await courseApi.save({ ...currentCourse.value,
      objectives: syllabusForm.value.objectives, assessmentMethod: syllabusForm.value.assessmentMethod })
    alert('课程大纲与教学目标已保存')
  } catch (error: any) { alert(error.message || '保存失败') }
}

onMounted(async () => {
  try {
    await loadTeacherList()
    await onTeacherChange()
  } catch (error) {
    console.error('教师工作台加载失败', error)
  }
  await nextTick()
  renderRadarChart()
})
onUnmounted(() => { radarChart?.dispose(); radarChart = null })
</script>
