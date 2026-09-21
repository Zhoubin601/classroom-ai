<template>
  <section aria-label="历史开课与人次" class="minimal-card p-5 space-y-4">
    <h2 class="font-bold">历史开课与人数、人次</h2>
    <form class="flex gap-3 items-end" @submit.prevent="load"><label>历史学期<input v-model="term" class="border rounded p-2 block" placeholder="全部学期" /></label><button :disabled="loading" class="border rounded p-2">查询历史</button><button type="button" class="border rounded p-2" @click="term=''; load()">清空条件</button></form>
    <p v-if="error" role="alert" class="text-red-700">{{ error }}</p>
    <p>班次 {{ data.totalOfferings }} · 累计人次 {{ data.cumulativePersonTimes }}</p>
    <p class="text-sm text-slate-500">人数取各班次选课名单；同一学生跨班次分别计入人次，多教师班次只计一次。归档后保留历史快照。</p>
    <div class="overflow-x-auto"><table class="w-full text-left text-sm"><thead><tr><th>学期</th><th>课程 / 班次</th><th>教师</th><th>教室</th><th>人数</th><th>状态 / 归档时间</th></tr></thead><tbody>
      <tr v-for="item in data.items" :key="item.offeringId" class="border-t"><td class="p-2">{{ item.academicTerm }}</td><td>{{ item.courseName }}<div>{{ item.className }}</div></td><td>{{ item.teachers.join('、') || item.primaryTeacher }}</td><td>{{ item.classroom || '未排定' }}</td><td>{{ item.studentCount }}</td><td>{{ item.isSnapshotFrozen ? '已归档' : '在读' }}<div>{{ item.archivedAt?.replace('T',' ') }}</div></td></tr>
      <tr v-if="!loading && !data.items.length"><td colspan="6" class="p-4 text-center text-slate-500">暂无历史开课记录</td></tr>
    </tbody></table></div>
  </section>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { offeringHistoryApi } from '../api'
import type { OfferingHistory } from '../api/types'
const term=ref(''),loading=ref(false),error=ref('')
const data=ref<OfferingHistory>({totalOfferings:0,cumulativePersonTimes:0,items:[]})
let request=0
async function load() {
  const id=++request; loading.value=true; error.value=''
  try {const result=await offeringHistoryApi.getHistory(term.value || undefined);if(id===request)data.value=result}
  catch(e) {if(id===request){error.value=e instanceof Error?e.message:'历史加载失败';data.value={totalOfferings:0,cumulativePersonTimes:0,items:[]}}}
  finally {if(id===request)loading.value=false}
}
onMounted(load)
</script>
