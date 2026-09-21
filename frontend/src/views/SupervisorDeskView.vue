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
        <span class="px-3 py-1.5 rounded-xl bg-slate-50 border border-slate-200 text-slate-800 font-semibold shadow-subtle">{{ loggedUser?.realName || '身份加载中' }}（授权 {{ authorizedMajors.map(m => m.majorCode).join('；') || '暂无' }}）</span>
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
        :class="['px-3.5 py-1.5 rounded-xl text-xs font-medium transition flex items-center gap-1.5 cursor-pointer', 
                 activeTab === tab.key ? 'bg-amber-50 text-amber-800 border border-amber-200 font-semibold shadow-subtle' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100']"
      >
        <component :is="tab.iconComp" class="w-3.5 h-3.5" :class="activeTab === tab.key ? 'text-amber-600' : 'text-slate-400'" />
        {{ tab.label }}
      </button>
    </div>

    <!-- Tab 0: 全院开课总课表 / 听课日程看板 (US-03/06) -->
    <div v-if="activeTab === 'timetable'" class="space-y-4">
      <div class="minimal-card p-5">
        <!-- 头部标题与视图切换开关 -->
        <div class="flex flex-wrap items-center justify-between gap-4 mb-4 pb-3 border-b border-slate-100">
          <div>
            <h2 class="text-base font-bold text-slate-900 flex items-center gap-2">
              <Calendar class="w-5 h-5 text-amber-600" /> 全院督导听课总课表与排课日程看板 (US-03/06)
            </h2>
            <p class="text-xs text-slate-500 mt-1">
              全院各专业开课周次、教室与主讲教师分布一览，专供教学督导专家遴选听课时间并进班随堂打分
            </p>
          </div>

          <!-- 双视图模式切换器 -->
          <div class="flex items-center p-1 bg-slate-100 rounded-xl border border-slate-200">
            <button
              @click="timetableMode = 'matrix'"
              :class="[
                'px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer',
                timetableMode === 'matrix' ? 'bg-white text-indigo-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'
              ]"
            >
              <LayoutGrid class="w-3.5 h-3.5" /> 高校周历矩阵课表
            </button>
            <button
              @click="timetableMode = 'cards'"
              :class="[
                'px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition cursor-pointer',
                timetableMode === 'cards' ? 'bg-white text-indigo-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'
              ]"
            >
              <ListFilter class="w-3.5 h-3.5" /> 多维日程卡片看板
            </button>
          </div>
        </div>

        <!-- 多维快捷筛选工具栏 (周几、节次、课程性质、教师、教室) -->
        <div class="p-3.5 bg-slate-50 border border-slate-200 rounded-xl mb-5 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div class="flex flex-wrap items-center gap-2.5">
            <span class="font-semibold text-slate-700 flex items-center gap-1">
              <Filter class="w-3.5 h-3.5 text-amber-600" /> 课表筛选：
            </span>

            <!-- 星期几筛选 -->
            <select v-model="scheduleFilter.dayOfWeek" @change="schedCurrentPage = 1" class="bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-amber-500 shadow-xs">
              <option value="">全部星期 (周一至周日)</option>
              <option :value="1">星期一 (Mon)</option>
              <option :value="2">星期二 (Tue)</option>
              <option :value="3">星期三 (Wed)</option>
              <option :value="4">星期四 (Thu)</option>
              <option :value="5">星期五 (Fri)</option>
              <option :value="6">星期六 (Sat)</option>
              <option :value="7">星期日 (Sun)</option>
            </select>

            <!-- 节次区间筛选 -->
            <select v-model="scheduleFilter.period" @change="schedCurrentPage = 1" class="bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-amber-500 shadow-xs">
              <option value="">全部节次时段</option>
              <option value="1-2">第1-2节 (08:00 - 09:35)</option>
              <option value="3-4">第3-4节 (10:05 - 11:40)</option>
              <option value="5-6">第5-6节 (13:30 - 15:05)</option>
              <option value="7-8">第7-8节 (15:35 - 17:10)</option>
              <option value="9-10">第9-10节 (18:30 - 20:05)</option>
            </select>

            <!-- 课程性质筛选 -->
            <select v-model="scheduleFilter.courseType" @change="schedCurrentPage = 1" class="bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-amber-500 shadow-xs">
              <option value="">全部课程性质</option>
              <option value="专业核心课">专业核心课</option>
              <option value="专业基础课">专业基础课</option>
              <option value="通识必修课">通识必修课</option>
              <option value="专业选修课">专业选修课</option>
            </select>

            <!-- 授课教师筛选 (突显教师第一视觉) -->
            <div class="relative">
              <input 
                v-model="scheduleFilter.teacher" 
                @input="schedCurrentPage = 1" 
                placeholder="按任课教师筛选 (如 郭军)..." 
                class="bg-white border border-amber-200 rounded-lg pl-7 pr-2.5 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-amber-500 w-44 shadow-xs font-medium"
              />
              <User class="w-3.5 h-3.5 text-amber-600 absolute left-2 top-2.5" />
            </div>

            <!-- 教室筛选 -->
            <select v-model="scheduleFilter.classroom" @change="schedCurrentPage = 1" class="bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-amber-500 shadow-xs">
              <option value="">全部教学教室</option>
              <option v-for="cr in availableClassrooms" :key="cr" :value="cr">{{ cr }}</option>
            </select>

            <!-- 一键重置 -->
            <button 
              @click="resetScheduleFilter" 
              class="px-2.5 py-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-100 text-slate-600 text-xs font-medium transition cursor-pointer flex items-center gap-1"
            >
              <RotateCcw class="w-3 h-3 text-slate-500" /> 重置
            </button>
          </div>

          <span class="text-xs text-slate-500 font-medium">
            共匹配到 <b class="text-amber-700 font-bold">{{ filteredSchedules.length }}</b> 节开课排课
          </span>
        </div>

        <!-- 视图 1：大学周历矩阵课表 (Weekly Matrix) -->
        <div v-if="timetableMode === 'matrix'" class="overflow-x-auto">
          <table class="w-full border-collapse border border-slate-200 text-xs min-w-[960px] rounded-xl overflow-hidden shadow-subtle">
            <thead>
              <tr class="bg-slate-100/80 text-slate-700 text-center font-semibold">
                <th class="border border-slate-200 py-3 px-2 w-28 bg-slate-100">节次 / 时段</th>
                <th v-for="d in weekDays" :key="d.day" class="border border-slate-200 py-3 px-3">
                  <div class="text-slate-900 font-bold">{{ d.name }}</div>
                  <div class="text-[10px] text-slate-400 font-mono font-normal">{{ d.en }}</div>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="slot in periodSlots" :key="slot.key" class="border-b border-slate-200">
                <!-- 节次标题列 -->
                <td class="border border-slate-200 p-2.5 text-center bg-slate-50/70">
                  <div class="font-bold text-slate-800">{{ slot.label }}</div>
                  <span class="inline-block mt-0.5 px-1.5 py-0.2 rounded text-[10px] bg-slate-200/80 text-slate-600 font-medium">
                    {{ slot.tag }}
                  </span>
                  <div class="text-[10px] text-slate-400 font-mono mt-1">{{ slot.time }}</div>
                </td>

                <!-- 星期一至星期日单元格 -->
                <td 
                  v-for="d in weekDays" 
                  :key="d.day" 
                  class="border border-slate-200 p-2 align-top bg-white hover:bg-amber-50/20 transition duration-150 min-h-[110px]"
                >
                  <div v-if="getSchedulesForSlot(d.day, slot).length === 0" class="h-full min-h-[90px] flex items-center justify-center text-slate-300 text-[11px]">
                    -
                  </div>
                  <div v-else class="space-y-2">
                    <div 
                      v-for="s in getSchedulesForSlot(d.day, slot)" 
                      :key="s.id" 
                      class="p-2.5 rounded-xl border border-indigo-200 bg-gradient-to-br from-indigo-50/70 via-white to-amber-50/30 hover:shadow-card hover:border-amber-400 transition shadow-subtle space-y-2"
                    >
                      <!-- 主讲教师突出第一视觉 -->
                      <div class="flex items-center justify-between gap-1">
                        <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-indigo-100/80 text-indigo-900 font-bold text-[11px]">
                          <User class="w-3 h-3 text-indigo-600" /> {{ s.offering?.teacherName }}
                        </span>
                        <span class="text-[10px] px-1.5 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200 font-medium">
                          {{ s.classroom }}
                        </span>
                      </div>

                      <!-- 课程与代码 -->
                      <div>
                        <div class="font-bold text-slate-900 text-xs leading-tight">
                          {{ s.offering?.course?.courseName }}
                        </div>
                        <div class="text-[10px] text-slate-500 font-mono mt-0.5">
                          {{ s.offering?.className }} · {{ s.offering?.studentCount }}人
                        </div>
                        <div class="text-[10px] text-slate-400 font-mono">
                          {{ s.weekRange }}
                        </div>
                      </div>

                      <!-- 督导快捷听课操作按钮 -->
                      <div class="pt-1.5 border-t border-slate-100 flex items-center justify-between gap-1">
                        <button 
                          @click="openPreviewResources(s.offering.course.id)" 
                          class="text-[10px] text-slate-500 hover:text-indigo-600 flex items-center gap-0.5 font-medium cursor-pointer"
                          title="课件免密预审"
                        >
                          <FileText class="w-3 h-3" /> 课件
                        </button>
                        <button 
                          @click="enterLiveSupervision(s.offering.id, s)" 
                          :class="[
                            'text-[10px] flex items-center gap-0.5 font-semibold cursor-pointer px-1.5 py-0.5 rounded transition',
                            isScheduleInSession(s).inSession
                              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100'
                              : 'text-slate-400 hover:text-slate-600'
                          ]"
                          :title="isScheduleInSession(s).inSession ? '当前时段正在授课中，点击进班' : '非当前授课时段 (时段拦截)'"
                        >
                          <Video v-if="isScheduleInSession(s).inSession" class="w-3 h-3 text-emerald-600" />
                          <Lock v-else class="w-2.5 h-2.5 text-slate-400" />
                          {{ isScheduleInSession(s).inSession ? '授课中' : '进班' }}
                        </button>
                        <button 
                          @click="openEvaluateForm(s.offering)" 
                          class="px-2 py-0.5 bg-amber-600 hover:bg-amber-700 text-white rounded-md text-[10px] font-bold shadow-xs flex items-center gap-0.5 transition cursor-pointer"
                          title="随堂量化听评课打分"
                        >
                          <Edit3 class="w-3 h-3" /> 评课
                        </button>
                      </div>
                    </div>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 视图 2：排课日程卡片看板 (含卡片流 + 分页控制) -->
        <div v-else class="space-y-4">
          <div v-if="filteredSchedules.length === 0" class="py-12 text-center text-slate-400 bg-slate-50/50 rounded-xl border border-dashed border-slate-200">
            <Calendar class="w-8 h-8 text-slate-300 mx-auto mb-2" />
            <span>没有检索到符合当前筛选条件 (周几/节次/课程性质/教师/教室) 的开课排课记录</span>
          </div>
          <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div 
              v-for="s in pagedSchedules" 
              :key="s.id" 
              class="bg-white border border-slate-200 rounded-xl p-4 hover:border-amber-300 hover:shadow-card transition shadow-subtle flex flex-col justify-between space-y-3"
            >
              <div>
                <!-- 顶部：突出任课教师第一视觉 -->
                <div class="flex items-start justify-between pb-2.5 border-b border-slate-100">
                  <div>
                    <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-indigo-50 border border-indigo-200 text-indigo-900 font-bold text-xs">
                      <User class="w-3.5 h-3.5 text-indigo-600" />
                      主讲教师：{{ s.offering?.teacherName }}
                    </span>
                    <div class="mt-2">
                      <span class="text-[10px] px-2 py-0.5 rounded bg-slate-100 text-slate-600 border border-slate-200 font-mono">
                        {{ s.offering?.course?.courseCode }}
                      </span>
                      <h3 class="text-sm font-bold text-slate-900 mt-1">{{ s.offering?.course?.courseName }}</h3>
                    </div>
                  </div>
                  <div class="flex flex-col items-end gap-1">
                    <span class="text-[10px] px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 font-medium">
                      {{ s.offering?.course?.courseType || '专业课' }}
                    </span>
                    <span class="text-xs px-2 py-0.5 bg-slate-100 text-slate-600 border border-slate-200 rounded-lg font-mono">
                      {{ s.offering?.studentCount }} 人额
                    </span>
                  </div>
                </div>

                <!-- 教学班级与教室时段 -->
                <div class="mt-3 space-y-1.5 text-xs text-slate-600">
                  <p>
                    授课班级：<span class="text-indigo-600 font-semibold">{{ s.offering?.className }}</span>
                    <span class="text-slate-400 ml-1">({{ s.offering?.academicTerm }})</span>
                  </p>
                  <div class="p-2.5 bg-slate-50 border border-slate-200 rounded-lg flex items-center justify-between text-xs">
                    <div class="flex items-center gap-1.5">
                      <span class="text-emerald-700 font-semibold bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200 flex items-center gap-1">
                        <MapPin class="w-3.5 h-3.5 text-emerald-600" /> {{ s.classroom }}
                      </span>
                    </div>
                    <div class="text-right">
                      <span class="text-slate-800 font-semibold flex items-center gap-1">
                        <Clock class="w-3 h-3 text-indigo-600" /> 周{{ s.dayOfWeek }} 第{{ s.startPeriod }}-{{ s.endPeriod }}节
                      </span>
                      <div class="text-[10px] text-slate-400 font-mono">({{ s.weekRange }})</div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 督导业务操作栏 -->
              <div class="pt-2 border-t border-slate-100 flex items-center justify-between gap-2">
                <button 
                  @click="openPreviewResources(s.offering.course.id)" 
                  class="text-xs text-slate-600 hover:text-indigo-600 flex items-center gap-1 font-medium cursor-pointer"
                >
                  <FileText class="w-3.5 h-3.5" /> 预审课件
                </button>
                <div class="flex items-center gap-2">
                  <button 
                    @click="enterLiveSupervision(s.offering.id, s)" 
                    :class="[
                      'px-2.5 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1 transition cursor-pointer',
                      isScheduleInSession(s).inSession
                        ? 'bg-emerald-600 hover:bg-emerald-700 text-white shadow-xs'
                        : 'bg-slate-100 hover:bg-slate-200 text-slate-500 border border-slate-200'
                    ]"
                    :title="isScheduleInSession(s).inSession ? '当前时段授课中，点击进班随堂实时监控' : '非当前授课时段 (受时段拦截)'"
                  >
                    <Video v-if="isScheduleInSession(s).inSession" class="w-3.5 h-3.5" />
                    <Lock v-else class="w-3 h-3 text-slate-400" />
                    {{ isScheduleInSession(s).inSession ? '进班监控 (授课中)' : '非授课时段' }}
                  </button>
                  <button 
                    @click="openEvaluateForm(s.offering)" 
                    class="px-3 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-semibold shadow-subtle flex items-center gap-1 transition cursor-pointer"
                    title="随堂量化听评课打分"
                  >
                    <Edit3 class="w-3.5 h-3.5" /> 随堂评课
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 排课卡片分页控制栏 (P1 级要求) -->
          <div class="mt-4 pt-4 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500">
            <div class="flex items-center gap-2">
              <span>共 <b class="text-slate-800">{{ filteredSchedules.length }}</b> 节排课</span>
              <span>·</span>
              <span>第 <b class="text-amber-700">{{ schedCurrentPage }}</b> / {{ totalSchedPages }} 页</span>
              <div class="flex items-center gap-1 ml-2">
                <span>每页显示</span>
                <select 
                  v-model.number="schedPageSize" 
                  @change="schedCurrentPage = 1" 
                  class="bg-white border border-slate-200 rounded-lg px-2 py-1 text-xs text-slate-700 focus:outline-none focus:border-amber-500"
                >
                  <option :value="3">3 节</option>
                  <option :value="6">6 节</option>
                  <option :value="9">9 节</option>
                </select>
              </div>
            </div>

            <div class="flex items-center gap-1.5">
              <button
                @click="schedCurrentPage = Math.max(1, schedCurrentPage - 1)"
                :disabled="schedCurrentPage <= 1"
                class="px-2.5 py-1 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition font-medium cursor-pointer"
              >
                上一页
              </button>
              <div class="flex items-center gap-1">
                <button
                  v-for="p in totalSchedPages"
                  :key="p"
                  @click="schedCurrentPage = p"
                  :class="[
                    'w-7 h-7 rounded-lg text-xs font-semibold flex items-center justify-center transition cursor-pointer',
                    schedCurrentPage === p
                      ? 'bg-amber-600 text-white shadow-xs'
                      : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                  ]"
                >
                  {{ p }}
                </button>
              </div>
              <button
                @click="schedCurrentPage = Math.min(totalSchedPages, schedCurrentPage + 1)"
                :disabled="schedCurrentPage >= totalSchedPages"
                class="px-2.5 py-1 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition font-medium cursor-pointer"
              >
                下一页
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 1: 待督导课程复合检索与听评课 (US-06 / US-13) -->
    <div v-if="activeTab === 'search'" class="space-y-4">
      <div class="minimal-card p-5">
        <div class="flex flex-wrap items-center justify-between gap-4 mb-4">
          <!-- 复合检索条件 (US-06) - 突显教师检索优先 -->
          <div class="flex flex-wrap items-center gap-2.5">
            <label class="text-xs">授权专业<select aria-label="授权专业" v-model="filterParams.majorId" @change="handleFilterChange" class="border rounded p-2"><option value="">全部授权专业</option><option v-for="m in authorizedMajors" :key="m.id" :value="m.id">{{ m.majorName }}</option></select></label>
            <label class="text-xs">任课教师<select aria-label="任课教师" v-model="filterParams.teacherId" @change="handleFilterChange" class="border rounded p-2"><option value="">全部教师</option><option v-for="t in searchTeachers" :key="t.id" :value="t.id">{{ t.teacherName }} · {{ t.teacherCode }}</option></select></label>
            <!-- 课程名 / 代码 -->
            <div class="relative">
              <input 
                v-model="filterParams.keyword" 
                @input="handleFilterChange" 
                placeholder="按课程名 / 代码检索..." 
                class="bg-white border border-slate-200 rounded-xl pl-8 pr-3 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 w-44 shadow-subtle"
              />
              <Search class="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2.5" />
            </div>

            <!-- 学期选择 -->
            <select aria-label="检索学期" v-model="filterParams.term" @change="handleFilterChange" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-indigo-500 shadow-subtle">
              <option value="">全部学期</option>
              <option v-for="t in availableTerms" :key="t" :value="t">{{ t }}</option>
            </select>

            <!-- 班级选择 -->
            <select v-model="filterParams.className" @change="supCurrentPage = 1" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-indigo-500 shadow-subtle">
              <option value="">全部教学班级</option>
              <option v-for="c in availableClasses" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <button class="border rounded p-2 text-xs" @click="resetSearch">清空检索条件</button>
          <p v-if="searchError" role="alert" class="text-red-700">{{ searchError }}</p>
          <span class="text-xs text-slate-500">共检索到 <b class="text-indigo-600 font-bold">{{ filteredOfferings.length }}</b> 门待督导开课</span>
        </div>

        <!-- 课程开课列表 (突出任课教师第一视觉) -->
        <div v-if="pagedOfferings.length === 0" class="py-12 text-center text-slate-400 bg-slate-50/50 rounded-xl border border-dashed border-slate-200">
          未检索到符合条件的待督导开课信息
        </div>
        <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div v-for="off in pagedOfferings" :key="off.id" class="p-4 bg-white border border-slate-200 rounded-xl hover:border-indigo-300 hover:shadow-card transition shadow-subtle space-y-3 flex flex-col justify-between">
            <div>
              <!-- 顶部核心高亮：主讲/任课教师标识 -->
              <div class="flex items-center justify-between pb-2 border-b border-slate-100">
                <div class="flex items-center gap-2">
                  <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-indigo-50 border border-indigo-200 text-indigo-900 font-bold text-xs">
                    <User class="w-3.5 h-3.5 text-indigo-600" />
                    主讲教师：{{ off.teacherName }}
                  </span>
                  <span class="text-[11px] text-slate-500 font-mono">{{ off.course.courseCode }}</span>
                </div>
                <span class="px-2 py-0.5 rounded-full text-[10px] bg-slate-100 text-slate-600 border border-slate-200">
                  {{ off.course.courseType }}
                </span>
              </div>

              <!-- 课程基本信息 -->
              <div class="mt-2">
                <h3 class="text-sm font-bold text-slate-900 flex items-center gap-2">
                  {{ off.course.courseName }}
                  <span class="text-[10px] px-2 py-0.5 rounded bg-slate-100 text-slate-600 font-normal font-mono">{{ off.academicTerm }}</span>
                </h3>
                <p class="text-xs text-slate-500 mt-1">
                  授课班级：<span class="text-indigo-600 font-semibold">{{ off.className }}</span> · 选课班额：<b class="text-slate-800">{{ off.studentCount }}</b> 人
                </p>

                <!-- 关联排课时段与地点 (周几第几节 / 教室) -->
                <div class="mt-2.5 p-2 bg-slate-50 border border-slate-200 rounded-lg text-xs space-y-1">
                  <div v-if="getSchedulesForOffering(off.id).length > 0" class="space-y-1">
                    <div v-for="sch in getSchedulesForOffering(off.id)" :key="sch.id" class="flex flex-wrap items-center justify-between gap-1 text-[11px]">
                      <span class="text-emerald-700 font-semibold flex items-center gap-1 bg-emerald-50 px-1.5 py-0.5 rounded border border-emerald-200">
                        <MapPin class="w-3 h-3 text-emerald-600" /> {{ sch.classroom }}
                      </span>
                      <span class="text-slate-700 font-medium flex items-center gap-1">
                        <Clock class="w-3 h-3 text-indigo-600" /> 周{{ sch.dayOfWeek }} 第{{ sch.startPeriod }}-{{ sch.endPeriod }}节 <span class="text-slate-400 font-mono">({{ sch.weekRange }})</span>
                      </span>
                    </div>
                  </div>
                  <div v-else class="text-[11px] text-slate-400 flex items-center gap-1">
                    <Clock class="w-3 h-3 text-slate-400" /> 待教研室统筹排课
                  </div>
                </div>
              </div>
            </div>

            <!-- 操作按钮组 -->
            <div class="flex items-center justify-between pt-2 border-t border-slate-100 gap-2">
              <button @click="openPreviewResources(off.course.id)" class="text-xs text-indigo-600 hover:text-indigo-800 flex items-center gap-1 font-medium cursor-pointer">
                <FileText class="w-3.5 h-3.5" /> 课件免密预审
              </button>
              <div class="flex items-center gap-2">
                <button 
                  @click="enterLiveSupervision(off.id)" 
                  :class="[
                    'px-3 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1 transition-all cursor-pointer',
                    checkOfferingInSession(off.id).inSession
                      ? 'bg-emerald-600 hover:bg-emerald-700 text-white shadow-xs'
                      : 'bg-slate-100 hover:bg-slate-200 text-slate-500 border border-slate-200'
                  ]"
                  :title="checkOfferingInSession(off.id).inSession ? '当前处于授课时段，允许进班随堂实时监控' : '非当前授课时段 (时段拦截)'"
                >
                  <Video v-if="checkOfferingInSession(off.id).inSession" class="w-3.5 h-3.5" />
                  <Lock v-else class="w-3 h-3 text-slate-400" />
                  {{ checkOfferingInSession(off.id).inSession ? '进班实时督导 (授课中)' : '非授课时段' }}
                </button>
                <button @click="openEvaluateForm(off)" class="px-3.5 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-semibold shadow-subtle flex items-center gap-1 transition cursor-pointer">
                  <Edit3 class="w-3.5 h-3.5" /> 随堂评价 (US-13)
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页控制条 (P1 级要求) -->
        <div class="mt-4 pt-4 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500">
          <div class="flex items-center gap-2">
            <span>共 <b class="text-slate-800">{{ filteredOfferings.length }}</b> 门开课</span>
            <span>·</span>
            <span>第 <b class="text-amber-700">{{ supCurrentPage }}</b> / {{ totalSupPages }} 页</span>
            <div class="flex items-center gap-1 ml-2">
              <span>每页</span>
              <select 
                v-model.number="supPageSize" 
                @change="supCurrentPage = 1" 
                class="bg-white border border-slate-200 rounded-lg px-2 py-1 text-xs text-slate-700 focus:outline-none focus:border-amber-500"
              >
                <option :value="2">2 门</option>
                <option :value="4">4 门</option>
                <option :value="8">8 门</option>
              </select>
            </div>
          </div>

          <div class="flex items-center gap-1.5">
            <button
              @click="supCurrentPage = Math.max(1, supCurrentPage - 1)"
              :disabled="supCurrentPage <= 1"
              class="px-2.5 py-1 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition font-medium cursor-pointer"
            >
              上一页
            </button>
            <div class="flex items-center gap-1">
              <button
                v-for="p in totalSupPages"
                :key="p"
                @click="supCurrentPage = p"
                :class="[
                  'w-7 h-7 rounded-lg text-xs font-semibold flex items-center justify-center transition cursor-pointer',
                  supCurrentPage === p
                    ? 'bg-amber-600 text-white shadow-xs'
                    : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                ]"
              >
                {{ p }}
              </button>
            </div>
            <button
              @click="supCurrentPage = Math.min(totalSupPages, supCurrentPage + 1)"
              :disabled="supCurrentPage >= totalSupPages"
              class="px-2.5 py-1 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition font-medium cursor-pointer"
            >
              下一页
            </button>
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
                  <h4 class="text-sm font-bold text-slate-900">{{ alert.courseName }}</h4>
                  <span class="text-xs font-mono text-slate-500">({{ alert.courseCode }})</span>
                  <span :class="['px-2 py-0.5 rounded text-[10px] font-bold', 
                                alert.alertLevel === 'RED' ? 'bg-rose-600 text-white' : 'bg-amber-500 text-white']">
                    {{ alert.alertLevel === 'RED' ? '低分红标预警' : '零覆盖黄标预警' }}
                  </span>
                </div>
                <p class="text-xs text-slate-600 mt-1 leading-relaxed">{{ alert.alertMessage }}</p>
                <div class="flex items-center gap-4 mt-2 text-[11px] text-slate-500">
                  <span>任课教师：<b class="text-slate-800">{{ alert.teacherName || '郭军' }}</b></span>
                  <span>教研室：{{ alert.department }}</span>
                  <span v-if="alert.currentScore">当前评分：<b class="text-rose-600 font-mono">{{ alert.currentScore }}</b> 分</span>
                </div>
              </div>
            </div>
            <button 
              @click="activeTab = 'search'; filterParams.keyword = alert.courseCode; loadOfferings()" 
              class="px-3 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-semibold shrink-0 shadow-subtle transition cursor-pointer"
            >
              前去督导
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗：随堂听评课打分 (US-13) -->
    <div v-if="showEvaluateModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-xl p-6 shadow-modal space-y-4 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between border-b border-slate-200 pb-3">
          <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
            <Edit3 class="w-4 h-4 text-amber-600" /> 随堂听评课量化打分表 (US-13)
          </h3>
          <button @click="showEvaluateModal = false" class="text-slate-400 hover:text-slate-600 text-lg cursor-pointer">✕</button>
        </div>

        <div class="bg-slate-50 p-3 rounded-xl text-xs space-y-1">
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
              <input v-model.number="evalForm.scoreAttitude" type="range" min="0" max="25" step="0.5" class="w-full accent-indigo-600 cursor-pointer" />
            </div>
            <div>
              <div class="flex justify-between text-slate-600 mb-1">
                <span>2. 教学内容 (0-25分)</span>
                <b class="text-indigo-600 font-mono">{{ evalForm.scoreContent }} 分</b>
              </div>
              <input v-model.number="evalForm.scoreContent" type="range" min="0" max="25" step="0.5" class="w-full accent-indigo-600 cursor-pointer" />
            </div>
            <div>
              <div class="flex justify-between text-slate-600 mb-1">
                <span>3. 教学方法 (0-25分)</span>
                <b class="text-indigo-600 font-mono">{{ evalForm.scoreMethod }} 分</b>
              </div>
              <input v-model.number="evalForm.scoreMethod" type="range" min="0" max="25" step="0.5" class="w-full accent-indigo-600 cursor-pointer" />
            </div>
            <div>
              <div class="flex justify-between text-slate-600 mb-1">
                <span>4. 教学效果 (0-25分)</span>
                <b class="text-indigo-600 font-mono">{{ evalForm.scoreEffect }} 分</b>
              </div>
              <input v-model.number="evalForm.scoreEffect" type="range" min="0" max="25" step="0.5" class="w-full accent-indigo-600 cursor-pointer" />
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
          <button @click="showEvaluateModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition cursor-pointer">取消</button>
          <button @click="handleSaveEvaluation(true)" class="px-4 py-2 bg-slate-200 hover:bg-slate-300 text-slate-800 rounded-xl text-xs font-semibold transition cursor-pointer">暂存草稿 (US-13)</button>
          <button @click="handleSaveEvaluation(false)" class="px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition cursor-pointer">正式提交 (开启24h脱敏)</button>
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
          <button @click="showPreviewModal = false" class="text-slate-400 hover:text-slate-600 text-lg cursor-pointer">✕</button>
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
          <button @click="showPreviewModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold transition cursor-pointer">关闭</button>
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
  AlertCircle,
  User,
  Calendar,
  Clock,
  LayoutGrid,
  ListFilter,
  Filter,
  RotateCcw,
  MapPin,
  Lock
} from 'lucide-vue-next'
import { courseApi, supervisionApi, resourceApi, scheduleApi, majorApi, teacherApi, authApi } from '../api'
import type { CourseOffering, SupervisionDashboardVO, SupervisionAlertVO, CourseResource, CourseSchedule, Major, Teacher, UserVO } from '../api/types'
import { isScheduleInSession } from '../utils/scheduleTime'

const emit = defineEmits<{
  (e: 'jump-to-attendance', offeringId: number): void
}>()

const loggedUser = ref<UserVO | null>(null)
const activeTab = ref('timetable')
const tabs = [
  { key: 'timetable', label: '全院开课总课表 / 听课日程看板 (US-03/06)', iconComp: Calendar },
  { key: 'search', label: '待督导目标课程多维检索 (US-06)', iconComp: Search },
  { key: 'alerts', label: '教学质量预警中心 (US-16)', iconComp: AlertTriangle }
]

// 课表排课列表与视图模式
const scheduleList = ref<CourseSchedule[]>([])
const timetableMode = ref<'matrix' | 'cards'>('matrix')

// 排课看板组合快捷筛选 (周几、节次、课程性质、教师、教室)
const scheduleFilter = ref({
  dayOfWeek: '',
  period: '',
  courseType: '',
  teacher: '',
  classroom: ''
})

const resetScheduleFilter = () => {
  scheduleFilter.value = {
    dayOfWeek: '',
    period: '',
    courseType: '',
    teacher: '',
    classroom: ''
  }
  schedCurrentPage.value = 1
}

const availableClassrooms = computed(() => {
  return Array.from(new Set(scheduleList.value.map(s => s.classroom).filter(Boolean)))
})

const filteredSchedules = computed(() => {
  return scheduleList.value.filter(s => {
    // 星期筛选
    if (scheduleFilter.value.dayOfWeek && String(s.dayOfWeek) !== String(scheduleFilter.value.dayOfWeek)) {
      return false
    }
    // 节次筛选
    if (scheduleFilter.value.period) {
      const parts = scheduleFilter.value.period.split('-').map(Number)
      if (parts.length === 2) {
        const [pStart, pEnd] = parts
        if (s.startPeriod > pEnd || s.endPeriod < pStart) {
          return false
        }
      }
    }
    // 课程性质
    if (scheduleFilter.value.courseType && s.offering?.course?.courseType !== scheduleFilter.value.courseType) {
      return false
    }
    // 授课教师筛选 (突显教师)
    if (scheduleFilter.value.teacher && scheduleFilter.value.teacher.trim()) {
      const q = scheduleFilter.value.teacher.trim().toLowerCase()
      const t = (s.offering?.teacherName || '').toLowerCase()
      if (!t.includes(q)) {
        return false
      }
    }
    // 教室筛选
    if (scheduleFilter.value.classroom && s.classroom !== scheduleFilter.value.classroom) {
      return false
    }
    return true
  })
})

// 排课卡片看板分页 (P1 级要求)
const schedCurrentPage = ref(1)
const schedPageSize = ref(6)

const totalSchedPages = computed(() => {
  return Math.ceil(filteredSchedules.value.length / schedPageSize.value) || 1
})

const pagedSchedules = computed(() => {
  const start = (schedCurrentPage.value - 1) * schedPageSize.value
  return filteredSchedules.value.slice(start, start + schedPageSize.value)
})

// 周历矩阵课表结构定义
const weekDays = [
  { day: 1, name: '星期一', en: 'Mon' },
  { day: 2, name: '星期二', en: 'Tue' },
  { day: 3, name: '星期三', en: 'Wed' },
  { day: 4, name: '星期四', en: 'Thu' },
  { day: 5, name: '星期五', en: 'Fri' },
  { day: 6, name: '星期六', en: 'Sat' },
  { day: 7, name: '星期日', en: 'Sun' }
]

const periodSlots = [
  { key: '1-2', label: '第1-2节', time: '08:00 - 09:35', start: 1, end: 2, tag: '上午' },
  { key: '3-4', label: '第3-4节', time: '10:05 - 11:40', start: 3, end: 4, tag: '上午' },
  { key: '5-6', label: '第5-6节', time: '13:30 - 15:05', start: 5, end: 6, tag: '下午' },
  { key: '7-8', label: '第7-8节', time: '15:35 - 17:10', start: 7, end: 8, tag: '下午' },
  { key: '9-10', label: '第9-10节', time: '18:30 - 20:05', start: 9, end: 10, tag: '晚间' }
]

const getSchedulesForSlot = (day: number, slot: typeof periodSlots[0]) => {
  return filteredSchedules.value.filter(s => {
    if (s.dayOfWeek !== day) return false
    return !(s.startPeriod > slot.end || s.endPeriod < slot.start)
  })
}

const getSchedulesForOffering = (offeringId: number) => {
  return scheduleList.value.filter(s => s.offering?.id === offeringId)
}

const loadSchedules = async () => {
  try {
    const list = await scheduleApi.getAll()
    scheduleList.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('加载排课列表失败', e)
  }
}

const offeringList = ref<CourseOffering[]>([])
const filterParams = ref<{keyword:string; teacher:string; term:string; className:string; majorId:number|''; teacherId:number|''}>({ keyword: '', teacher: '', term: '', className: '', majorId: '', teacherId: '' })
const authorizedMajors=ref<Major[]>([]), searchTeachers=ref<Teacher[]>([]), searchError=ref('')
function resetSearch(){filterParams.value={keyword:'',teacher:'',term:'',className:'',majorId:'',teacherId:''};handleFilterChange()}
let searchRequest=0
onMounted(async()=>{try{[authorizedMajors.value,searchTeachers.value,loggedUser.value]=await Promise.all([majorApi.getAll(),teacherApi.getAll(),authApi.getMe()])}catch(e){searchError.value=e instanceof Error?e.message:'字典加载失败'}})
const availableTerms = ref<string[]>([])

// 督导开课分页 (P1 级要求)
const supCurrentPage = ref(1)
const supPageSize = ref(4)

const handleFilterChange = () => {
  supCurrentPage.value = 1
  loadOfferings()
}

const availableClasses = computed(() => {
  return Array.from(new Set(offeringList.value.map(o => o.className).filter(Boolean)))
})

const filteredOfferings = computed(() => {
  return offeringList.value.filter(o => {
    if (filterParams.value.className && o.className !== filterParams.value.className) return false
    return true
  })
})

const totalSupPages = computed(() => {
  return Math.ceil(filteredOfferings.value.length / supPageSize.value) || 1
})

const pagedOfferings = computed(() => {
  const start = (supCurrentPage.value - 1) * supPageSize.value
  return filteredOfferings.value.slice(start, start + supPageSize.value)
})

const checkOfferingInSession = (offeringId: number) => {
  const schedules = getSchedulesForOffering(offeringId)
  if (schedules.length === 0) return { inSession: false, reason: '该课程尚未在教务系统排课', periodText: '未排课' }
  const active = schedules.find(s => isScheduleInSession(s).inSession)
  const weekNames = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
  if (active) return { inSession: true, schedule: active, periodText: `${weekNames[active.dayOfWeek] || '周' + active.dayOfWeek} 第${active.startPeriod}-${active.endPeriod}节` }
  const first = schedules[0]
  const check = isScheduleInSession(first)
  return { inSession: false, schedule: first, reason: check.reason, periodText: check.periodRangeText }
}

const enterLiveSupervision = (offeringId: number, schedule?: CourseSchedule) => {
  if (schedule) {
    const schedCheck = isScheduleInSession(schedule)
    if (!schedCheck.inSession) {
      const reason = schedCheck.reason || '当前非授课时间'
      alert(`【教学督导时段严格拦截】\n\n您点击的排课时段：${schedCheck.periodRangeText}\n拦截原因：${reason}\n\n根据督导纪律规范：督导专家仅能在当前时间段处于正在授课状态的班级进入随堂实时督导！`)
      return
    }
  } else {
    const check = checkOfferingInSession(offeringId)
    if (!check.inSession) {
      const reason = check.reason || '当前非授课时间'
      alert(`【教学督导时段严格拦截】\n\n该班级授课时段：${check.periodText}\n拦截原因：${reason}\n\n根据督导纪律规范：督导专家仅能在当前时间段处于正在授课状态的班级进入随堂实时督导！`)
      return
    }
  }
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
  const request=++searchRequest; searchError.value=''
  try {
    const list = await courseApi.getOfferings(
      filterParams.value.term || undefined,
      filterParams.value.teacher || undefined,
      filterParams.value.keyword || undefined,
      {majorId:filterParams.value.majorId || undefined, teacherId:filterParams.value.teacherId || undefined}
    )
    if(request !== searchRequest) return
    offeringList.value = list
    if (availableTerms.value.length === 0 && list.length > 0) {
      availableTerms.value = Array.from(new Set(list.map(o => o.academicTerm).filter(Boolean)))
    }
  } catch (e) {
    if(request===searchRequest){offeringList.value=[];searchError.value=e instanceof Error?e.message:'检索失败'}
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
      supervisorName: loggedUser.value?.realName || '',
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
  loadSchedules()
  loadOfferings()
  loadAnalytics()
})
</script>
