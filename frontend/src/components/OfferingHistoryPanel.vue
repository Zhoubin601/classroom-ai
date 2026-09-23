<template>
  <section aria-label="历史开课与人次" class="minimal-card p-6 space-y-5">
    <!-- 头部：标题与筛选表单 -->
    <div class="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-2 border-b border-slate-100">
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-xl bg-indigo-50 border border-indigo-100/80 flex items-center justify-center text-indigo-600 shadow-2xs shrink-0">
          <History class="w-5 h-5" />
        </div>
        <div>
          <div class="flex items-center gap-2">
            <h2 class="text-base font-bold text-slate-800">历史开课与人数、人次</h2>
            <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200/60">US-04 课程归档</span>
          </div>
          <p class="text-xs text-slate-400 mt-0.5">历年开课班次快照归档与教学规模人次核算看板</p>
        </div>
      </div>

      <!-- 筛选栏 -->
      <form class="flex flex-wrap items-center gap-2.5" @submit.prevent="load">
        <div class="relative flex items-center">
          <label class="text-xs text-slate-500 mr-2 font-medium">历史学期</label>
          <div class="relative">
            <Calendar class="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
            <select
              v-model="term"
              aria-label="历史学期"
              class="bg-white border border-slate-200 rounded-lg pl-8 pr-7 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-indigo-500 shadow-2xs cursor-pointer appearance-none transition-all hover:border-slate-300"
              @change="load"
            >
              <option value="">全部学期</option>
              <option v-for="t in availableTerms" :key="t" :value="t">{{ t }}</option>
            </select>
            <div class="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2 text-slate-400">
              <ChevronDown class="w-3.5 h-3.5" />
            </div>
          </div>
        </div>

        <button
          :disabled="loading"
          class="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 active:scale-[0.98] transition disabled:opacity-50 shadow-2xs cursor-pointer"
        >
          <Search class="w-3.5 h-3.5" />
          <span>查询历史</span>
        </button>

        <button
          type="button"
          class="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 hover:text-slate-900 transition shadow-2xs cursor-pointer"
          @click="reset"
        >
          <RotateCcw class="w-3.5 h-3.5 text-slate-400" />
          <span>清空条件</span>
        </button>
      </form>
    </div>

    <!-- 错误警告提示 -->
    <div v-if="error" role="alert" class="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2">
      <AlertCircle class="w-4 h-4 shrink-0" />
      <span>{{ error }}</span>
    </div>

    <!-- 统计指标与口径说明 -->
    <div class="grid grid-cols-1 md:grid-cols-12 gap-3 items-center">
      <div class="md:col-span-5 flex items-center gap-3">
        <!-- 班次指标卡 -->
        <div class="flex-1 p-3 rounded-xl bg-slate-50/80 border border-slate-100 flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-slate-200/70 text-slate-700 flex items-center justify-center shrink-0">
            <Layers class="w-4 h-4" />
          </div>
          <div>
            <div class="text-[11px] text-slate-400">开课班次统计</div>
            <div class="text-sm font-bold text-slate-800">班次 {{ data.totalOfferings }}</div>
          </div>
        </div>

        <!-- 累计人次指标卡 -->
        <div class="flex-1 p-3 rounded-xl bg-indigo-50/50 border border-indigo-100/70 flex items-center gap-3">
          <div class="w-8 h-8 rounded-lg bg-indigo-100 text-indigo-600 flex items-center justify-center shrink-0">
            <Users class="w-4 h-4" />
          </div>
          <div>
            <div class="text-[11px] text-indigo-500 font-medium">累计选课人次</div>
            <div class="text-sm font-bold text-indigo-700">累计人次 {{ data.cumulativePersonTimes }}</div>
          </div>
        </div>
      </div>

      <!-- 口径说明条 -->
      <div class="md:col-span-7 p-3 rounded-xl bg-slate-50/60 border border-slate-100/80 flex items-start gap-2">
        <Info class="w-4 h-4 text-slate-400 shrink-0 mt-0.5" />
        <p class="text-xs text-slate-500 leading-relaxed">
          人数取各班次选课名单；同一学生跨班次分别计入人次，多教师班次只计一次。归档后保留历史快照。
        </p>
      </div>
    </div>

    <!-- 历史表格 -->
    <div class="overflow-x-auto rounded-xl border border-slate-200/80 shadow-2xs bg-white">
      <table class="w-full text-left text-xs border-collapse">
        <thead class="bg-slate-50/90 text-slate-600 font-semibold border-b border-slate-200/80">
          <tr>
            <th class="py-3 px-4 w-32">学期</th>
            <th class="py-3 px-4 min-w-[200px]">课程 / 班次</th>
            <th class="py-3 px-4 min-w-[140px]">教师</th>
            <th class="py-3 px-4 w-32">教室</th>
            <th class="py-3 px-4 w-24 text-center">人数</th>
            <th class="py-3 px-4 w-52">状态 / 归档时间</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
          <!-- 加载中 -->
          <tr v-if="loading">
            <td colspan="6" class="py-10 text-center text-slate-400">
              <div class="flex items-center justify-center gap-2">
                <RefreshCw class="w-4 h-4 animate-spin text-indigo-600" />
                <span class="text-xs text-slate-500">正在加载开课历史记录...</span>
              </div>
            </td>
          </tr>

          <!-- 数据行 -->
          <tr
            v-else-if="data.items.length"
            v-for="item in data.items"
            :key="item.offeringId"
            class="hover:bg-indigo-50/30 transition-colors duration-150"
          >
            <!-- 学期 -->
            <td class="py-3.5 px-4">
              <span class="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-mono font-medium bg-slate-100 text-slate-700 border border-slate-200/60">
                {{ item.academicTerm }}
              </span>
            </td>

            <!-- 课程/班次 -->
            <td class="py-3.5 px-4">
              <div class="font-bold text-slate-800 text-sm flex items-center gap-1.5">
                {{ item.courseName }}
                <span v-if="item.courseCode" class="text-[10px] text-slate-400 font-mono font-normal">({{ item.courseCode }})</span>
              </div>
              <div class="text-xs text-slate-500 font-medium mt-0.5 flex items-center gap-1.5">
                <span class="w-1.5 h-1.5 rounded-full bg-indigo-500"></span>
                {{ item.className }}
              </div>
            </td>

            <!-- 教师 -->
            <td class="py-3.5 px-4">
              <div class="inline-flex items-center gap-1.5 text-slate-700 font-medium">
                <User class="w-3.5 h-3.5 text-slate-400 shrink-0" />
                <span>{{ item.teachers?.join('、') || item.primaryTeacher }}</span>
              </div>
            </td>

            <!-- 教室 -->
            <td class="py-3.5 px-4">
              <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-slate-50 border border-slate-200/70 text-slate-700 font-mono text-xs">
                <MapPin class="w-3 h-3 text-slate-400 shrink-0" />
                {{ item.classroom || '未排定' }}
              </span>
            </td>

            <!-- 人数 -->
            <td class="py-3.5 px-4 text-center">
              <span class="inline-flex items-center justify-center px-2.5 py-0.5 rounded-full bg-indigo-50 border border-indigo-100/80 text-indigo-700 font-mono font-bold text-xs">
                {{ item.studentCount }} 人
              </span>
            </td>

            <!-- 状态 / 归档时间 -->
            <td class="py-3.5 px-4">
              <div class="flex items-center gap-2">
                <span
                  v-if="item.isSnapshotFrozen"
                  class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200/80"
                >
                  <CheckCircle2 class="w-3 h-3 text-emerald-600" />
                  已归档
                </span>
                <span
                  v-else
                  class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold bg-blue-50 text-blue-700 border border-blue-200/80"
                >
                  <Clock class="w-3 h-3 text-blue-600" />
                  在读
                </span>
              </div>
              <div v-if="item.archivedAt" class="text-[11px] text-slate-400 font-mono mt-1 flex items-center gap-1">
                <Calendar class="w-3 h-3 text-slate-300 shrink-0" />
                {{ formatDateTime(item.archivedAt) }}
              </div>
            </td>
          </tr>

          <!-- 空记录状态 -->
          <tr v-if="!loading && !data.items.length">
            <td colspan="6" class="py-12 text-center text-slate-400">
              <div class="flex flex-col items-center justify-center gap-2">
                <div class="w-10 h-10 rounded-full bg-slate-50 flex items-center justify-center text-slate-300 border border-slate-100">
                  <FolderOpen class="w-5 h-5" />
                </div>
                <div class="text-sm font-medium text-slate-600">暂无历史开课记录</div>
                <div class="text-xs text-slate-400">未检索到该学期对应的已归档或开课班次数据</div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  History,
  Calendar,
  ChevronDown,
  Search,
  RotateCcw,
  Layers,
  Users,
  Info,
  User,
  MapPin,
  CheckCircle2,
  Clock,
  FolderOpen,
  RefreshCw,
  AlertCircle
} from 'lucide-vue-next'
import { offeringHistoryApi, courseApi } from '../api'
import type { OfferingHistory } from '../api/types'

const defaultTerms = [
  '2026-2027秋季',
  '2026-2027春季',
  '2025-2026秋季',
  '2025-2026春季',
  '2024-2025秋季',
  '2024-2025春季'
]

const term = ref('')
const loading = ref(false)
const error = ref('')
const availableTerms = ref<string[]>([...defaultTerms])
const data = ref<OfferingHistory>({ totalOfferings: 0, cumulativePersonTimes: 0, items: [] })
let request = 0

function formatDateTime(val?: string) {
  if (!val) return ''
  return val.replace('T', ' ').replace(/\.\d+$/, '')
}

function mergeTerms(newTerms: (string | undefined | null)[]) {
  const valid = newTerms.filter((t): t is string => Boolean(t && t.trim()))
  const set = new Set([...availableTerms.value, ...valid])
  availableTerms.value = Array.from(set).sort((a, b) => b.localeCompare(a, 'zh-Hans-CN', { numeric: true }))
}

async function load() {
  const id = ++request
  loading.value = true
  error.value = ''
  try {
    const result = await offeringHistoryApi.getHistory(term.value || undefined)
    if (id === request) {
      data.value = result
      if (result.items?.length) {
        mergeTerms(result.items.map(item => item.academicTerm))
      }
    }
  } catch (e) {
    if (id === request) {
      error.value = e instanceof Error ? e.message : '历史加载失败'
      data.value = { totalOfferings: 0, cumulativePersonTimes: 0, items: [] }
    }
  } finally {
    if (id === request) loading.value = false
  }
}

function reset() {
  term.value = ''
  load()
}

onMounted(async () => {
  try {
    const offerings = await courseApi.getOfferings()
    if (offerings?.length) {
      mergeTerms(offerings.map(o => o.academicTerm))
    }
  } catch {
    // 忽略预加载班次异常，保持兜底学期列表可用
  }
  load()
})
</script>
