<template>
  <section class="space-y-6" aria-label="开课与排课">
    <!-- 顶部标题与操作栏 -->
    <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-slate-200/80">
      <div>
        <div class="flex items-center gap-2.5">
          <div class="w-9 h-9 rounded-xl bg-emerald-50 border border-emerald-100 flex items-center justify-center text-emerald-600 shadow-2xs">
            <CalendarCheck class="w-5 h-5" />
          </div>
          <div>
            <h2 class="text-base font-bold text-slate-900 tracking-tight">开课与排课统筹</h2>
            <p class="text-xs text-slate-500 mt-0.5">维护任课团队与选课名单，按学期统筹教学安排，全方位防冲突校验。</p>
          </div>
        </div>
      </div>
      <div class="flex items-center gap-2.5">
        <button
          class="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold rounded-xl bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white shadow-sm shadow-emerald-600/20 transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="loading"
          @click="openOffering()"
        >
          <Plus class="w-3.5 h-3.5" />
          新增班次
        </button>
        <button
          class="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold rounded-xl bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 text-white shadow-sm shadow-indigo-600/20 transition-all cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="loading || !offerings.length"
          @click="openSchedule()"
        >
          <Clock class="w-3.5 h-3.5" />
          新增排课
        </button>
      </div>
    </div>

    <!-- 全局状态提示 -->
    <div v-if="message && !modal" role="alert" class="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2">
      <AlertCircle class="w-4 h-4 shrink-0" />
      <span>{{ message }}</span>
    </div>

    <div v-if="loading" class="p-8 text-center text-xs text-slate-400 font-medium flex items-center justify-center gap-2">
      <span class="w-4 h-4 border-2 border-indigo-600/30 border-t-indigo-600 rounded-full animate-spin"></span>
      正在加载排课数据…
    </div>

    <!-- 开课班次列表卡片 -->
    <div class="pro-card p-5 overflow-hidden">
      <div class="flex items-center justify-between mb-3.5">
        <div class="flex items-center gap-2">
          <div class="w-2 h-2 rounded-full bg-indigo-600"></div>
          <h3 class="text-xs font-bold text-slate-900 tracking-wide uppercase">开课班次 ({{ offerings.length }})</h3>
        </div>
      </div>

      <div class="overflow-x-auto rounded-xl border border-slate-200/80">
        <table class="w-full text-left text-xs">
          <thead class="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200/80">
            <tr>
              <th class="py-2.5 px-3.5">课程 / 教学班</th>
              <th class="py-2.5 px-3.5">学期</th>
              <th class="py-2.5 px-3.5">任课教师</th>
              <th class="py-2.5 px-3.5">选课人数</th>
              <th class="py-2.5 px-3.5 text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="o in offerings" :key="o.id" class="hover:bg-slate-50/70 transition-colors">
              <td class="py-2.5 px-3.5 font-medium text-slate-900">
                <div class="font-bold text-slate-800">{{ o.course?.courseName }}</div>
                <div class="text-[11px] text-slate-400 font-normal mt-0.5">{{ o.className }}</div>
              </td>
              <td class="py-2.5 px-3.5 text-slate-600 font-mono">{{ o.academicTerm }}</td>
              <td class="py-2.5 px-3.5 text-slate-700">
                <span class="px-2 py-0.5 rounded-md bg-slate-100 border border-slate-200/60 font-medium">
                  {{ teacherText(o) }}
                </span>
              </td>
              <td class="py-2.5 px-3.5 text-slate-600 font-mono font-medium">{{ o.studentCount }} 人</td>
              <td class="py-2.5 px-3.5 text-right">
                <span v-if="o.isSnapshotFrozen" class="text-[11px] font-semibold text-slate-400 bg-slate-100 px-2 py-0.5 rounded-full border border-slate-200">已归档</span>
                <div v-else class="inline-flex items-center gap-1.5">
                  <button
                    :disabled="busy"
                    @click="openOffering(o)"
                    class="px-2.5 py-1 text-[11px] font-semibold rounded-lg bg-indigo-50 text-indigo-700 hover:bg-indigo-100 border border-indigo-200/60 transition cursor-pointer disabled:opacity-50"
                  >
                    编辑班次
                  </button>
                  <button
                    :disabled="busy"
                    @click="archive(o)"
                    class="px-2.5 py-1 text-[11px] font-semibold rounded-lg bg-amber-50 text-amber-700 hover:bg-amber-100 border border-amber-200/60 transition cursor-pointer disabled:opacity-50"
                  >
                    结课归档
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!offerings.length">
              <td colspan="5" class="py-6 text-center text-slate-400 text-xs">暂无开课班次</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 筛选排课表单 -->
    <form aria-label="排课筛选" class="pro-card grid grid-cols-1 gap-3 p-4 items-end sm:grid-cols-2 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)_minmax(8rem,0.6fr)_minmax(0,1.2fr)_auto]" @submit.prevent="loadSchedules">
      <div class="min-w-0">
        <label for="schedule-filter-term" class="block text-[11px] font-semibold text-slate-600 mb-1.5">学期</label>
        <input
          id="schedule-filter-term"
          v-model="filter.term"
          aria-label="学期"
          placeholder="全部学期"
          list="offering-terms"
          class="h-10 w-full min-w-0 bg-slate-50 border border-slate-200 rounded-xl px-3 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
        />
        <datalist id="offering-terms"><option v-for="term in terms" :key="term" :value="term" /></datalist>
      </div>

      <div class="min-w-0">
        <label for="schedule-filter-teacher" class="block text-[11px] font-semibold text-slate-600 mb-1.5">教师</label>
        <select
          id="schedule-filter-teacher"
          v-model="filter.teacher"
          aria-label="教师"
          class="h-10 w-full min-w-0 bg-slate-50 border border-slate-200 rounded-xl px-3 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner cursor-pointer"
        >
          <option value="">全部教师</option>
          <option v-for="t in teachers" :key="t.id" :value="t.teacherCode">{{ t.teacherName }} · {{ t.teacherCode }}</option>
        </select>
      </div>

      <div class="min-w-0">
        <label for="schedule-filter-week" class="block text-[11px] font-semibold text-slate-600 mb-1.5">周次</label>
        <input
          id="schedule-filter-week"
          v-model="filter.week"
          aria-label="周次"
          type="number"
          min="1"
          max="53"
          placeholder="全部周次"
          class="h-10 w-full min-w-0 bg-slate-50 border border-slate-200 rounded-xl px-3 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner font-mono"
        />
      </div>

      <div class="min-w-0">
        <label for="schedule-filter-classroom" class="block text-[11px] font-semibold text-slate-600 mb-1.5">教室</label>
        <input
          id="schedule-filter-classroom"
          v-model="filter.classroom"
          aria-label="教室"
          placeholder="例如：文管 A447"
          class="h-10 w-full min-w-0 bg-slate-50 border border-slate-200 rounded-xl px-3 text-xs text-slate-800 focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
        />
      </div>

      <div class="flex items-center justify-end gap-2 sm:col-span-2 xl:col-span-1">
        <button
          type="submit"
          class="h-10 shrink-0 px-4 text-xs font-semibold whitespace-nowrap rounded-xl bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 text-white shadow-sm shadow-indigo-600/20 transition cursor-pointer disabled:opacity-50"
          :disabled="querying"
        >
          筛选排课
        </button>
        <button
          type="button"
          @click="resetFilter"
          class="h-10 shrink-0 px-3.5 text-xs font-medium whitespace-nowrap rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-600 transition cursor-pointer border border-slate-200"
        >
          重置
        </button>
      </div>
    </form>

    <!-- 排课日程网格卡片 -->
    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <article
        v-for="s in schedules"
        :key="s.id"
        class="pro-card p-5 space-y-3 relative hover:border-indigo-200 transition-all group"
      >
        <div class="flex items-start justify-between gap-2">
          <div>
            <h3 class="font-bold text-sm text-slate-900 group-hover:text-indigo-600 transition-colors">{{ s.offering?.course?.courseName }}</h3>
            <div class="flex items-center gap-2 mt-1">
              <span class="text-xs text-slate-500 font-medium">{{ s.offering?.className }}</span>
              <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 font-mono">{{ s.offering?.studentCount }} 人</span>
            </div>
          </div>
          <span class="text-[10px] px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-100 font-medium font-mono">
            {{ s.offering?.academicTerm }}
          </span>
        </div>

        <div class="text-xs text-slate-600 flex items-center gap-1.5">
          <User class="w-3.5 h-3.5 text-slate-400" />
          <span>{{ teacherText(s.offering) }}</span>
        </div>

        <div class="p-2.5 rounded-xl bg-slate-50 border border-slate-100 space-y-1">
          <div class="flex items-center gap-1.5 font-bold text-emerald-800 text-xs">
            <MapPin class="w-3.5 h-3.5 text-emerald-600" />
            <span>{{ s.classroom }}</span>
          </div>
          <div class="text-[11px] text-slate-500 flex items-center gap-1.5 font-mono">
            <Clock class="w-3.5 h-3.5 text-slate-400" />
            <span>第 {{ s.startWeek }}–{{ s.endWeek }} 周 · 星期{{ s.dayOfWeek }} · 第 {{ s.startPeriod }}–{{ s.endPeriod }} 节</span>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
          <button
            class="px-2.5 py-1 text-xs font-medium rounded-lg text-indigo-600 hover:bg-indigo-50 transition cursor-pointer disabled:opacity-40"
            :disabled="busy || s.offering?.isSnapshotFrozen"
            @click="openSchedule(s)"
          >
            编辑排课
          </button>
          <button
            class="px-2.5 py-1 text-xs font-medium rounded-lg text-rose-600 hover:bg-rose-50 transition cursor-pointer disabled:opacity-40"
            :disabled="busy || s.offering?.isSnapshotFrozen"
            @click="removeSchedule(s.id)"
          >
            删除排课
          </button>
        </div>
      </article>
      <div v-if="!querying && !schedules.length" class="col-span-full py-8 text-center text-slate-400 text-xs">
        当前条件下暂无排课。
      </div>
    </div>

    <!-- 弹窗模态框 (维护班次 / 维护排课) -->
    <div v-if="modal" class="fixed inset-0 z-50 bg-slate-950/40 backdrop-blur-xs flex items-center justify-center p-4">
      <form
        class="bg-white rounded-2xl w-full max-w-3xl max-h-[90vh] overflow-y-auto p-6 space-y-5 shadow-2xl border border-slate-200 animate-fade-in"
        role="dialog"
        aria-modal="true"
        :aria-label="modal === 'offering' ? '维护班次' : '维护排课'"
        @submit.prevent="save"
      >
        <div class="flex items-center justify-between pb-3 border-b border-slate-100">
          <h2 class="text-base font-bold text-slate-900">
            {{ modal === 'offering' ? (offeringForm.id ? '编辑班次' : '新增班次') : (scheduleForm.id ? '编辑排课' : '新增排课') }}
          </h2>
          <button type="button" @click="modal = ''" class="text-slate-400 hover:text-slate-600 text-sm cursor-pointer">✕</button>
        </div>

        <fieldset :disabled="busy" class="space-y-4 text-xs">
          <template v-if="modal === 'offering'">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
              <div>
                <label class="block font-semibold text-slate-700 mb-1">课程</label>
                <select
                  aria-label="课程"
                  v-model="offeringForm.courseId"
                  required
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
                >
                  <option value="">请选择课程</option>
                  <option v-for="c in courses" :key="c.id" :value="c.id">{{ c.courseName }} · {{ c.courseCode }}</option>
                </select>
              </div>

              <div>
                <label class="block font-semibold text-slate-700 mb-1">学期</label>
                <input
                  v-model="offeringForm.academicTerm"
                  aria-label="学期"
                  required
                  maxlength="32"
                  list="offering-terms"
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
                />
              </div>

              <div>
                <label class="block font-semibold text-slate-700 mb-1">教学班</label>
                <input
                  v-model="offeringForm.className"
                  aria-label="教学班"
                  required
                  maxlength="64"
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
                />
              </div>

              <div>
                <label class="block font-semibold text-slate-700 mb-1">主讲教师</label>
                <select
                  aria-label="主讲教师"
                  v-model="offeringForm.primaryTeacherId"
                  required
                  @change="offeringForm.collaboratingTeacherIds = offeringForm.collaboratingTeacherIds.filter((id: number) => id !== offeringForm.primaryTeacherId)"
                  class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
                >
                  <option value="">请选择教师</option>
                  <option v-for="t in teachers" :key="t.id" :value="t.id">{{ t.teacherName }} · {{ t.teacherCode }}</option>
                </select>
              </div>
            </div>

            <!-- 协同教师多选 -->
            <fieldset class="border border-slate-200 rounded-xl p-3.5 space-y-2 bg-slate-50/50">
              <legend class="px-1.5 font-semibold text-slate-700">协同教师</legend>
              <div class="flex flex-wrap gap-2.5">
                <label
                  v-for="t in teachers.filter(t => t.id !== offeringForm.primaryTeacherId)"
                  :key="t.id"
                  class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg border bg-white border-slate-200 text-slate-700 cursor-pointer hover:border-indigo-300 transition-colors"
                >
                  <input v-model="offeringForm.collaboratingTeacherIds" type="checkbox" :value="t.id" class="rounded text-indigo-600 focus:ring-0" />
                  <span>{{ t.teacherName }} · {{ t.teacherCode }}</span>
                </label>
              </div>
            </fieldset>

            <!-- 选课名单多选 -->
            <fieldset class="border border-slate-200 rounded-xl p-3.5 space-y-2.5 bg-slate-50/50">
              <legend class="px-1.5 font-semibold text-slate-700">选课名单 · 已选 {{ offeringForm.studentNumbers.length }} 人</legend>
              <div class="flex gap-2 items-center">
                <input
                  v-model="studentSearch"
                  placeholder="搜索姓名、学号或行政班"
                  aria-label="搜索学生"
                  class="flex-1 bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs focus:outline-none focus:border-indigo-500 shadow-inner"
                />
                <button
                  type="button"
                  @click="selectVisible"
                  class="px-2.5 py-1.5 text-xs font-semibold rounded-lg bg-indigo-50 text-indigo-700 border border-indigo-200/80 hover:bg-indigo-100 transition cursor-pointer"
                >
                  全选筛选结果
                </button>
                <button
                  type="button"
                  @click="offeringForm.studentNumbers = []"
                  class="px-2.5 py-1.5 text-xs font-medium rounded-lg bg-slate-100 text-slate-600 border border-slate-200 hover:bg-slate-200 transition cursor-pointer"
                >
                  清空
                </button>
              </div>
              <div class="max-h-52 overflow-y-auto grid grid-cols-1 sm:grid-cols-2 gap-2 p-1">
                <label
                  v-for="s in visibleStudents"
                  :key="s.studentId"
                  class="flex items-center gap-2 p-2 rounded-lg border bg-white border-slate-200 hover:border-indigo-300 cursor-pointer transition-colors"
                >
                  <input v-model="offeringForm.studentNumbers" type="checkbox" :value="s.studentId" class="rounded text-indigo-600 focus:ring-0" />
                  <div class="truncate">
                    <span class="font-semibold text-slate-800">{{ s.name }}</span>
                    <span class="text-slate-400 font-mono ml-1.5">{{ s.studentId }}</span>
                    <span class="block text-[10px] text-slate-400">{{ s.className }}</span>
                  </div>
                </label>
              </div>
            </fieldset>
          </template>

          <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3.5">
            <div class="col-span-full">
              <label class="block font-semibold text-slate-700 mb-1">开课班次</label>
              <select
                aria-label="开课班次"
                v-model="scheduleForm.offeringId"
                required
                :disabled="!!scheduleForm.id"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
              >
                <option value="">请选择班次</option>
                <option v-for="o in offerings" :key="o.id" :value="o.id" :disabled="o.isSnapshotFrozen">
                  {{ o.course?.courseName }} · {{ o.className }} · {{ o.academicTerm }} · {{ teacherText(o) }}
                </option>
              </select>
            </div>

            <div>
              <label class="block font-semibold text-slate-700 mb-1">教室</label>
              <input
                v-model="scheduleForm.classroom"
                aria-label="教室"
                required
                maxlength="64"
                placeholder="文管 A447"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
              />
            </div>

            <div>
              <label class="block font-semibold text-slate-700 mb-1">星期</label>
              <select
                aria-label="星期"
                v-model="scheduleForm.dayOfWeek"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
              >
                <option v-for="d in 7" :key="d" :value="d">星期{{ d }}</option>
              </select>
            </div>

            <div>
              <label class="block font-semibold text-slate-700 mb-1">起始周</label>
              <input
                v-model.number="scheduleForm.startWeek"
                aria-label="起始周"
                required
                type="number"
                min="1"
                max="53"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
              />
            </div>

            <div>
              <label class="block font-semibold text-slate-700 mb-1">结束周</label>
              <input
                v-model.number="scheduleForm.endWeek"
                aria-label="结束周"
                required
                type="number"
                :min="scheduleForm.startWeek"
                max="53"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
              />
            </div>

            <div>
              <label class="block font-semibold text-slate-700 mb-1">起始节</label>
              <input
                v-model.number="scheduleForm.startPeriod"
                aria-label="起始节"
                required
                type="number"
                min="1"
                max="10"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
              />
            </div>

            <div>
              <label class="block font-semibold text-slate-700 mb-1">结束节</label>
              <input
                v-model.number="scheduleForm.endPeriod"
                aria-label="结束节"
                required
                type="number"
                :min="scheduleForm.startPeriod"
                max="10"
                class="w-full bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono focus:bg-white focus:outline-none focus:border-indigo-500 shadow-inner"
              />
            </div>
          </div>
        </fieldset>

        <p v-if="message" role="alert" class="text-rose-700 text-xs bg-rose-50 p-2.5 rounded-xl border border-rose-200">{{ message }}</p>

        <!-- 冲突详情高亮 -->
        <div v-if="conflictRows.length" class="bg-rose-50 border border-rose-200 rounded-xl p-3.5 space-y-2.5" aria-label="冲突详情">
          <div class="font-bold text-xs text-rose-800">检测到排课冲突：</div>
          <article v-for="c in conflictRows" :key="c.id" class="p-2.5 rounded-lg bg-white border border-rose-200/80 text-xs space-y-1">
            <strong class="text-rose-700">{{ c.conflictReasons?.join('；') }}</strong>
            <p class="text-slate-700">{{ c.offering?.course?.courseName }} · {{ c.offering?.className }} · {{ teacherText(c.offering) }}</p>
            <p class="text-slate-500 font-mono text-[11px]">{{ c.classroom }} · {{ c.offering?.academicTerm }} · 第 {{ c.startWeek }}–{{ c.endWeek }} 周 · 星期{{ c.dayOfWeek }} · 第 {{ c.startPeriod }}–{{ c.endPeriod }} 节</p>
          </article>
        </div>

        <div class="flex justify-end gap-3 pt-3 border-t border-slate-100">
          <button
            type="button"
            :disabled="busy"
            @click="modal = ''"
            class="px-4 py-2 text-xs font-medium rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-600 transition cursor-pointer border border-slate-200"
          >
            取消
          </button>
          <button
            class="px-4 py-2 text-xs font-semibold rounded-xl bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white shadow-sm shadow-emerald-600/20 transition cursor-pointer disabled:opacity-50"
            :disabled="busy"
          >
            {{ busy ? '保存中…' : '保存' }}
          </button>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { isAxiosError } from 'axios'
import {
  CalendarCheck,
  Plus,
  Clock,
  AlertCircle,
  MapPin,
  User
} from 'lucide-vue-next'
import { courseApi, scheduleApi, teacherApi, studentApi } from '../api'
import type { Course, CourseOffering, CourseSchedule, Teacher, Student, ScheduleInput } from '../api/types'

const courses = ref<Course[]>([])
const teachers = ref<Teacher[]>([])
const students = ref<Student[]>([])
const offerings = ref<CourseOffering[]>([])
const schedules = ref<CourseSchedule[]>([])
const loading = ref(true)
const querying = ref(false)
const busy = ref(false)
const modal = ref('')
const message = ref('')
const conflictRows = ref<CourseSchedule[]>([])
const filter = ref({ term: '', teacher: '', week: '', classroom: '' })

type OfferingForm = {
  id?: number
  courseId: number | ''
  academicTerm: string
  className: string
  primaryTeacherId: number | ''
  collaboratingTeacherIds: number[]
  studentNumbers: string[]
}

const offeringForm = ref<OfferingForm>({
  courseId: '',
  academicTerm: '',
  className: '',
  primaryTeacherId: '',
  collaboratingTeacherIds: [],
  studentNumbers: []
})

const scheduleForm = ref<Omit<ScheduleInput, 'offeringId'> & { offeringId: number | '' }>({
  offeringId: '',
  classroom: '',
  dayOfWeek: 1,
  startWeek: 1,
  endWeek: 16,
  startPeriod: 1,
  endPeriod: 2
})

const studentSearch = ref('')
const terms = computed(() => [...new Set(offerings.value.map(o => o.academicTerm))])
const visibleStudents = computed(() => students.value.filter(s => `${s.name} ${s.studentId} ${s.className}`.includes(studentSearch.value.trim())))

const teacherText = (o?: CourseOffering) => {
  return o?.teachers?.length
    ? o.teachers.map((t) => `${t.teacherName}（${t.roleInOffering === 'PRIMARY' ? '主讲' : '协同'}）`).join('、')
    : o?.teacherName || '教师未配置'
}

const clearError = () => {
  message.value = ''
  conflictRows.value = []
}

const fail = (e: unknown) => {
  message.value = e instanceof Error ? e.message : '操作失败'
  conflictRows.value = isAxiosError<{ data?: { conflicts?: CourseSchedule[] } }>(e)
    ? e.response?.data?.data?.conflicts || []
    : []
}

let queryId = 0
async function loadSchedules() {
  const id = ++queryId
  querying.value = true
  try {
    const rows = await scheduleApi.getAll({
      ...filter.value,
      week: filter.value.week ? Number(filter.value.week) : undefined
    })
    if (id === queryId) schedules.value = rows
  } catch (e) {
    if (id === queryId) fail(e)
  } finally {
    if (id === queryId) querying.value = false
  }
}

async function resetFilter() {
  filter.value = { term: '', teacher: '', week: '', classroom: '' }
  clearError()
  await loadSchedules()
}

async function refresh() {
  offerings.value = await courseApi.getOfferings()
  await loadSchedules()
}

async function openOffering(o?: CourseOffering) {
  clearError()
  studentSearch.value = ''
  busy.value = true
  try {
    if (o) {
      const detail = await courseApi.getOfferingDetails(o.id)
      o = detail.offering
      const team = o.teachers || []
      offeringForm.value = {
        id: o.id,
        courseId: o.course.id,
        academicTerm: o.academicTerm,
        className: o.className,
        primaryTeacherId: team.find((t) => t.roleInOffering === 'PRIMARY')?.teacherId || teachers.value.find(t => t.teacherCode === detail.offering.teacherCode)?.id || '',
        collaboratingTeacherIds: team.filter((t) => t.roleInOffering !== 'PRIMARY').map((t) => t.teacherId),
        studentNumbers: detail.studentNumbers
      }
    } else {
      offeringForm.value = {
        courseId: '',
        academicTerm: '',
        className: '',
        primaryTeacherId: '',
        collaboratingTeacherIds: [],
        studentNumbers: []
      }
    }
    modal.value = 'offering'
  } catch (e) {
    fail(e)
  } finally {
    busy.value = false
  }
}

function openSchedule(s?: CourseSchedule) {
  clearError()
  scheduleForm.value = s
    ? { ...s, offeringId: s.offering.id }
    : { offeringId: '', classroom: '', dayOfWeek: 1, startWeek: 1, endWeek: 16, startPeriod: 1, endPeriod: 2 }
  modal.value = 'schedule'
}

function selectVisible() {
  offeringForm.value.studentNumbers = [
    ...new Set([...offeringForm.value.studentNumbers, ...visibleStudents.value.map(s => s.studentId)])
  ]
}

async function save() {
  if (busy.value) return
  busy.value = true
  clearError()
  try {
    if (modal.value === 'offering') {
      const { id, ...form } = offeringForm.value
      const data = {
        courseId: Number(form.courseId),
        academicTerm: form.academicTerm,
        className: form.className,
        primaryTeacherId: Number(form.primaryTeacherId),
        teacherIds: [Number(form.primaryTeacherId), ...form.collaboratingTeacherIds],
        studentIds: students.value.filter(s => form.studentNumbers.includes(s.studentId)).map(s => s.id)
      }
      if (id) await courseApi.updateOffering(id, data)
      else await courseApi.createOffering(data)
    } else {
      await scheduleApi.save({
        ...scheduleForm.value,
        offeringId: Number(scheduleForm.value.offeringId)
      })
    }
    modal.value = ''
    await refresh()
  } catch (e) {
    fail(e)
  } finally {
    busy.value = false
  }
}

async function archive(o: CourseOffering) {
  if (!confirm('结课归档将冻结人数、名单、教师、学期和排课，确认归档？')) return
  busy.value = true
  clearError()
  try {
    await courseApi.archiveOffering(o.id)
    await refresh()
  } catch (e) {
    fail(e)
  } finally {
    busy.value = false
  }
}

async function removeSchedule(id: number) {
  if (!confirm('确认删除这条排课？')) return
  busy.value = true
  clearError()
  try {
    await scheduleApi.delete(id)
    await refresh()
  } catch (e) {
    fail(e)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    [courses.value, teachers.value, students.value] = await Promise.all([
      courseApi.getAll(),
      teacherApi.getAll(),
      studentApi.getStudents()
    ])
    await refresh()
  } catch (e) {
    fail(e)
  } finally {
    loading.value = false
  }
})
</script>
