<template>
  <section class="space-y-5" aria-label="开课与排课">
    <div class="flex items-center justify-between"><div><h2 class="text-xl font-bold">开课与排课</h2><p class="text-sm text-slate-500">维护任课团队与选课名单，按学期统筹教学安排。</p></div><div class="flex gap-2"><button class="primary" :disabled="loading" @click="openOffering()">新增班次</button><button class="primary" :disabled="loading || !offerings.length" @click="openSchedule()">新增排课</button></div></div>
    <p v-if="message && !modal" role="alert" class="text-red-700">{{ message }}</p>
    <p v-if="loading">正在加载…</p>
    <div class="bg-white border rounded-xl p-4 overflow-x-auto">
      <h3 class="font-semibold mb-3">开课班次</h3>
      <table><thead><tr><th>课程 / 教学班</th><th>学期</th><th>任课教师</th><th>选课人数</th><th>操作</th></tr></thead><tbody>
        <tr v-for="o in offerings" :key="o.id"><td>{{ o.course?.courseName }}<small>{{ o.className }}</small></td><td>{{ o.academicTerm }}</td><td>{{ teacherText(o) }}</td><td>{{ o.studentCount }} 人</td><td><span v-if="o.isSnapshotFrozen">已归档</span><template v-else><button :disabled="busy" @click="openOffering(o)">编辑班次</button><button :disabled="busy" @click="archive(o)">结课归档</button></template></td></tr>
        <tr v-if="!offerings.length"><td colspan="5">暂无开课班次</td></tr>
      </tbody></table>
    </div>
    <form class="flex flex-wrap gap-3 items-end bg-white p-4 border rounded-xl" @submit.prevent="loadSchedules">
      <label>学期<input v-model="filter.term" placeholder="全部学期" list="offering-terms" /></label>
      <datalist id="offering-terms"><option v-for="term in terms" :key="term" :value="term" /></datalist>
      <label>教师<select v-model="filter.teacher" aria-label="教师"><option value="">全部教师</option><option v-for="t in teachers" :key="t.id" :value="t.teacherCode">{{ t.teacherName }} · {{ t.teacherCode }}</option></select></label>
      <label>周次<input v-model="filter.week" type="number" min="1" max="53" placeholder="全部周次" /></label>
      <label>教室<input v-model="filter.classroom" placeholder="例如：文管 A447" /></label>
      <button class="primary" :disabled="querying">筛选排课</button><button type="button" @click="resetFilter">重置</button>
    </form>
    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <article v-for="s in schedules" :key="s.id" class="bg-white rounded-xl border p-5 space-y-2">
        <h3 class="font-bold">{{ s.offering?.course?.courseName }}</h3><p>{{ s.offering?.className }} · {{ s.offering?.studentCount }} 人</p>
        <p>{{ teacherText(s.offering) }}</p><p class="text-slate-500">{{ s.offering?.academicTerm }}</p>
        <p class="font-semibold text-emerald-800">{{ s.classroom }}</p><p>第 {{ s.startWeek }}–{{ s.endWeek }} 周 · 星期{{ s.dayOfWeek }} · 第 {{ s.startPeriod }}–{{ s.endPeriod }} 节</p>
        <div class="flex gap-4"><button :disabled="busy || s.offering.isSnapshotFrozen" @click="openSchedule(s)">编辑排课</button><button :disabled="busy || s.offering.isSnapshotFrozen" @click="removeSchedule(s.id)">删除排课</button></div>
      </article>
      <p v-if="!querying && !schedules.length" class="text-slate-500">当前条件下暂无排课。</p>
    </div>
    <div v-if="modal" class="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
      <form class="bg-white rounded-xl w-full max-w-3xl max-h-[90vh] overflow-auto p-6 space-y-4" role="dialog" aria-modal="true" :aria-label="modal === 'offering' ? '维护班次' : '维护排课'" @submit.prevent="save">
        <h2 class="text-xl font-bold">{{ modal === 'offering' ? (offeringForm.id ? '编辑班次' : '新增班次') : (scheduleForm.id ? '编辑排课' : '新增排课') }}</h2>
        <fieldset :disabled="busy" class="space-y-4">
          <template v-if="modal === 'offering'">
            <div class="grid grid-cols-2 gap-3">
              <label>课程<select aria-label="课程" v-model="offeringForm.courseId" required><option value="">请选择课程</option><option v-for="c in courses" :key="c.id" :value="c.id">{{ c.courseName }} · {{ c.courseCode }}</option></select></label>
              <label>学期<input v-model="offeringForm.academicTerm" required maxlength="32" list="offering-terms" /></label>
              <label>教学班<input v-model="offeringForm.className" required maxlength="64" /></label>
              <label>主讲教师<select aria-label="主讲教师" v-model="offeringForm.primaryTeacherId" required @change="offeringForm.collaboratingTeacherIds = offeringForm.collaboratingTeacherIds.filter((id: number) => id !== offeringForm.primaryTeacherId)"><option value="">请选择教师</option><option v-for="t in teachers" :key="t.id" :value="t.id">{{ t.teacherName }} · {{ t.teacherCode }}</option></select></label>
            </div>
            <fieldset class="border rounded p-3"><legend>协同教师</legend><div class="flex flex-wrap gap-3"><label v-for="t in teachers.filter(t => t.id !== offeringForm.primaryTeacherId)" :key="t.id" class="check"><input v-model="offeringForm.collaboratingTeacherIds" type="checkbox" :value="t.id" />{{ t.teacherName }} · {{ t.teacherCode }}</label></div></fieldset>
            <fieldset class="border rounded p-3 space-y-2"><legend>选课名单 · 已选 {{ offeringForm.studentNumbers.length }} 人</legend>
              <div class="flex gap-2"><input v-model="studentSearch" placeholder="搜索姓名、学号或行政班" aria-label="搜索学生" /><button type="button" @click="selectVisible">全选筛选结果</button><button type="button" @click="offeringForm.studentNumbers = []">清空</button></div>
              <div class="max-h-52 overflow-auto grid grid-cols-2 gap-2"><label v-for="s in visibleStudents" :key="s.studentId" class="check"><input v-model="offeringForm.studentNumbers" type="checkbox" :value="s.studentId" /><span>{{ s.name }} · {{ s.studentId }}<small>{{ s.className }}</small></span></label></div>
            </fieldset>
          </template>
          <div v-else class="grid grid-cols-2 gap-3">
            <label class="col-span-2">开课班次<select aria-label="开课班次" v-model="scheduleForm.offeringId" required :disabled="!!scheduleForm.id"><option value="">请选择班次</option><option v-for="o in offerings" :key="o.id" :value="o.id" :disabled="o.isSnapshotFrozen">{{ o.course?.courseName }} · {{ o.className }} · {{ o.academicTerm }} · {{ teacherText(o) }}</option></select></label>
            <label>教室<input v-model="scheduleForm.classroom" required maxlength="64" placeholder="文管 A447" /></label>
            <label>星期<select aria-label="星期" v-model="scheduleForm.dayOfWeek"><option v-for="d in 7" :key="d" :value="d">星期{{ d }}</option></select></label>
            <label>起始周<input v-model.number="scheduleForm.startWeek" required type="number" min="1" max="53" /></label><label>结束周<input v-model.number="scheduleForm.endWeek" required type="number" :min="scheduleForm.startWeek" max="53" /></label>
            <label>起始节<input v-model.number="scheduleForm.startPeriod" required type="number" min="1" max="10" /></label><label>结束节<input v-model.number="scheduleForm.endPeriod" required type="number" :min="scheduleForm.startPeriod" max="10" /></label>
          </div>
        </fieldset>
        <p v-if="message" role="alert" class="text-red-700">{{ message }}</p>
        <div v-if="conflictRows.length" class="bg-red-50 border border-red-200 rounded p-3 space-y-3" aria-label="冲突详情">
          <article v-for="c in conflictRows" :key="c.id"><strong>{{ c.conflictReasons?.join('；') }}</strong><p>{{ c.offering?.course?.courseName }} · {{ c.offering?.className }} · {{ teacherText(c.offering) }}</p><p>{{ c.classroom }} · {{ c.offering?.academicTerm }} · 第 {{ c.startWeek }}–{{ c.endWeek }} 周 · 星期{{ c.dayOfWeek }} · 第 {{ c.startPeriod }}–{{ c.endPeriod }} 节</p></article>
        </div>
        <div class="flex justify-end gap-3"><button type="button" :disabled="busy" @click="modal = ''">取消</button><button class="primary" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button></div>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { isAxiosError } from 'axios'
import { courseApi, scheduleApi, teacherApi, studentApi } from '../api'
import type { Course, CourseOffering, CourseSchedule, Teacher, Student, ScheduleInput } from '../api/types'

const courses = ref<Course[]>([]), teachers = ref<Teacher[]>([]), students = ref<Student[]>([]), offerings = ref<CourseOffering[]>([]), schedules = ref<CourseSchedule[]>([])
const loading = ref(true), querying = ref(false), busy = ref(false), modal = ref(''), message = ref(''), conflictRows = ref<CourseSchedule[]>([])
const filter = ref({ term: '', teacher: '', week: '', classroom: '' })
type OfferingForm = { id?: number; courseId: number | ''; academicTerm: string; className: string; primaryTeacherId: number | ''; collaboratingTeacherIds: number[]; studentNumbers: string[] }
const offeringForm = ref<OfferingForm>({courseId:'', academicTerm:'', className:'', primaryTeacherId:'', collaboratingTeacherIds:[], studentNumbers:[]})
const scheduleForm = ref<Omit<ScheduleInput,'offeringId'> & {offeringId:number|''}>({offeringId:'',classroom:'',dayOfWeek:1,startWeek:1,endWeek:16,startPeriod:1,endPeriod:2}), studentSearch = ref('')
const terms = computed(() => [...new Set(offerings.value.map(o => o.academicTerm))])
const visibleStudents = computed(() => students.value.filter(s => `${s.name} ${s.studentId} ${s.className}`.includes(studentSearch.value.trim())))
const teacherText = (o?: CourseOffering) => o?.teachers?.length ? o.teachers.map((t) => `${t.teacherName}（${t.roleInOffering === 'PRIMARY' ? '主讲' : '协同'}）`).join('、') : o?.teacherName || '教师未配置'
const clearError = () => { message.value = ''; conflictRows.value = [] }
const fail = (e: unknown) => { message.value = e instanceof Error ? e.message : '操作失败'; conflictRows.value = isAxiosError<{data?:{conflicts?:CourseSchedule[]}}>(e) ? e.response?.data?.data?.conflicts || [] : [] }
let queryId = 0
async function loadSchedules() {
  const id = ++queryId; querying.value = true
  try { const rows = await scheduleApi.getAll({ ...filter.value, week: filter.value.week ? Number(filter.value.week) : undefined }); if (id === queryId) schedules.value = rows }
  catch (e) { if (id === queryId) fail(e) }
  finally { if (id === queryId) querying.value = false }
}
async function resetFilter() { filter.value = { term: '', teacher: '', week: '', classroom: '' }; clearError(); await loadSchedules() }
async function refresh() { offerings.value = await courseApi.getOfferings(); await loadSchedules() }
async function openOffering(o?: CourseOffering) {
  clearError(); studentSearch.value = ''; busy.value = true
  try {
    if (o) {
      const detail = await courseApi.getOfferingDetails(o.id)
      o = detail.offering
      const team = o.teachers || []
      offeringForm.value = { id: o.id, courseId: o.course.id, academicTerm: o.academicTerm, className: o.className,
        primaryTeacherId: team.find((t) => t.roleInOffering === 'PRIMARY')?.teacherId || teachers.value.find(t => t.teacherCode === detail.offering.teacherCode)?.id || '',
        collaboratingTeacherIds: team.filter((t) => t.roleInOffering !== 'PRIMARY').map((t) => t.teacherId), studentNumbers: detail.studentNumbers }
    } else offeringForm.value = { courseId: '', academicTerm: '', className: '', primaryTeacherId: '', collaboratingTeacherIds: [], studentNumbers: [] }
    modal.value = 'offering'
  } catch (e) { fail(e) } finally { busy.value = false }
}
function openSchedule(s?: CourseSchedule) { clearError(); scheduleForm.value = s ? { ...s, offeringId: s.offering.id } : { offeringId: '', classroom: '', dayOfWeek: 1, startWeek: 1, endWeek: 16, startPeriod: 1, endPeriod: 2 }; modal.value = 'schedule' }
function selectVisible() { offeringForm.value.studentNumbers = [...new Set([...offeringForm.value.studentNumbers, ...visibleStudents.value.map(s => s.studentId)])] }
async function save() {
  if (busy.value) return
  busy.value = true; clearError()
  try {
    if (modal.value === 'offering') {
      const { id, ...form } = offeringForm.value
      const data = { courseId: Number(form.courseId), academicTerm: form.academicTerm, className: form.className, primaryTeacherId: Number(form.primaryTeacherId), teacherIds: [Number(form.primaryTeacherId), ...form.collaboratingTeacherIds], studentIds: students.value.filter(s => form.studentNumbers.includes(s.studentId)).map(s => s.id) }
      if (id) await courseApi.updateOffering(id, data); else await courseApi.createOffering(data)
    } else await scheduleApi.save({...scheduleForm.value,offeringId:Number(scheduleForm.value.offeringId)})
    modal.value = ''; await refresh()
  } catch (e) { fail(e) } finally { busy.value = false }
}
async function archive(o: CourseOffering) {
  if (!confirm('结课归档将冻结人数、名单、教师、学期和排课，确认归档？')) return
  busy.value=true; clearError()
  try {await courseApi.archiveOffering(o.id);await refresh()} catch(e){fail(e)} finally{busy.value=false}
}
async function removeSchedule(id: number) {
  if (!confirm('确认删除这条排课？')) return
  busy.value = true; clearError()
  try { await scheduleApi.delete(id); await refresh() } catch (e) { fail(e) } finally { busy.value = false }
}
onMounted(async () => {
  try { [courses.value, teachers.value, students.value] = await Promise.all([courseApi.getAll(), teacherApi.getAll(), studentApi.getStudents()]); await refresh() }
  catch (e) { fail(e) } finally { loading.value = false }
})
</script>

<style scoped>
label { display: flex; flex-direction: column; gap: .35rem; font-size: .875rem; }
input, select { border: 1px solid #cbd5e1; padding: .5rem; border-radius: .4rem; min-width: 0; background: white; }
button { color: #047857; padding: .5rem .7rem; white-space: nowrap; cursor: pointer; }
button.primary { background: #047857; color: white; border-radius: .4rem; }
button:disabled { opacity: .5; cursor: default; }
label.check { flex-direction: row; align-items: center; }
table { width: 100%; text-align: left; font-size: .875rem; }
th, td { padding: .7rem; border-bottom: 1px solid #e2e8f0; }
small { display: block; color: #64748b; }
</style>
