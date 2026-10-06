<template>
  <div class="space-y-6">
    <!-- 顶部督导工作台状态栏 -->
    <div class="pro-card p-6 bg-gradient-to-r from-white via-slate-50/60 to-amber-50/30 border border-slate-200/80 shadow-card-hover rounded-2xl flex flex-wrap items-center justify-between gap-5">
      <div class="flex items-center gap-4">
        <div class="w-13 h-13 rounded-2xl bg-gradient-to-br from-amber-500 to-amber-700 flex items-center justify-center text-white shadow-md shadow-amber-500/20 ring-4 ring-amber-50">
          <ShieldCheck class="w-6 h-6" />
        </div>
        <div>
          <div class="flex items-center gap-3">
            <h1 class="text-xl font-bold tracking-tight text-slate-900">教学督导工作台</h1>
            <span class="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded-full bg-amber-50 text-amber-800 border border-amber-200/80 font-semibold shadow-2xs">
              <span class="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
              校/院两级督导专家专属
            </span>
          </div>
          <p class="text-xs text-slate-500 mt-1.5">负责待督导课程复合检索、课件教案授权预审、BOPPPS随堂打分、全院覆盖率巡检与红黄质量预警</p>
        </div>
      </div>
      <div class="flex items-center gap-2 text-xs bg-white/90 border border-slate-200/80 px-3.5 py-2 rounded-xl shadow-xs">
        <span class="text-slate-500 font-medium">当前督导身份：</span>
        <span class="font-bold text-amber-900 font-mono tracking-tight">{{ loggedUser?.realName || '身份加载中' }}（授权 {{ authorizedMajors.map(m => m.majorCode).join('；') || '暂无' }}）</span>
      </div>
    </div>

    <!-- 全院督导覆盖率动态大屏指标卡片 (US-15) -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
      <div class="pro-card p-5 group hover:border-slate-300 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs text-slate-500 font-medium">所选学期有效课程总数</span>
          <div class="w-8 h-8 rounded-lg bg-slate-100 text-slate-600 flex items-center justify-center">
            <Calendar class="w-4 h-4" />
          </div>
        </div>
        <div class="text-2xl font-bold text-slate-900 font-mono mt-2 tracking-tight">{{ dashboardMetrics?.totalCourses ?? 0 }} 门</div>
        <span class="text-[11px] text-slate-500 mt-1 block">按授权专业与有效开课去重</span>
      </div>

      <div class="pro-card p-5 group hover:border-emerald-300 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs text-slate-500 font-medium">已督导听课覆盖门数</span>
          <div class="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
            <ShieldCheck class="w-4 h-4" />
          </div>
        </div>
        <div class="text-2xl font-bold text-emerald-600 font-mono mt-2 tracking-tight">{{ dashboardMetrics?.supervisedCourses ?? 0 }} 门</div>
        <span class="text-[11px] text-emerald-700 font-semibold mt-1 block">累计开展听课 {{ dashboardMetrics?.totalEvaluations ?? 0 }} 次</span>
      </div>

      <div class="pro-card p-5 group hover:border-indigo-300 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs text-slate-500 font-medium">督导覆盖率动态百分比 (US-15)</span>
          <div class="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
            <LayoutGrid class="w-4 h-4" />
          </div>
        </div>
        <div class="text-3xl font-bold text-indigo-600 font-mono mt-2 tracking-tight">{{ dashboardMetrics ? (dashboardMetrics.coverageRate ?? 0).toFixed(1) : '0.0' }}%</div>
        <div class="w-full bg-slate-100 h-2 rounded-full mt-2.5 overflow-hidden p-0.5">
          <div class="bg-gradient-to-r from-indigo-500 to-indigo-600 h-full rounded-full transition-all duration-500 shadow-2xs" :style="{ width: `${dashboardMetrics?.coverageRate ?? 0}%` }"></div>
        </div>
      </div>

      <div class="pro-card p-5 group hover:border-amber-300 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs text-slate-500 font-medium">待巡检覆盖课程</span>
          <div class="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
            <AlertCircle class="w-4 h-4" />
          </div>
        </div>
        <div class="text-2xl font-bold text-amber-600 font-mono mt-2 tracking-tight">{{ dashboardMetrics?.pendingCourses ?? 0 }} 门</div>
        <span class="text-[11px] text-amber-700 font-semibold mt-1 block">需督导组优先排期进班</span>
      </div>
    </div>

    <!-- 覆盖率明细折叠面板 -->
    <details class="pro-card p-4 text-xs group">
      <summary class="cursor-pointer font-bold text-slate-800 flex items-center justify-between select-none">
        <span>覆盖率明细 · {{ dashboardMetrics?.academicTerm || '暂无学期' }}</span>
        <span class="text-[11px] text-indigo-600 group-open:rotate-180 transition-transform">▼</span>
      </summary>
      <div class="mt-3 pt-3 border-t border-slate-100 divide-y divide-slate-100 max-h-60 overflow-y-auto">
        <div v-for="item in coverageDetails" :key="item.courseId" class="flex justify-between items-center py-2.5 px-1 hover:bg-slate-50 rounded-lg transition">
          <span class="font-medium text-slate-800">{{ item.courseCode }} {{ item.courseName }}</span>
          <span :class="item.covered ? 'text-emerald-700 font-semibold bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200' : 'text-slate-400 bg-slate-100 px-2 py-0.5 rounded'">
            {{ item.covered ? `已审核评价 ${item.evaluationIds.join('、')}` : '未覆盖' }}
          </span>
        </div>
      </div>
    </details>

    <!-- 子导航标签 -->
    <div class="flex items-center gap-2 border-b border-slate-200/80 pb-3">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        @click="activeTab = tab.key"
        :class="['px-4 py-2 rounded-xl text-xs font-semibold transition flex items-center gap-2 cursor-pointer',
                 activeTab === tab.key ? 'bg-amber-50 text-amber-900 border border-amber-200/80 shadow-xs' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100']"
      >
        <component :is="tab.iconComp" class="w-4 h-4" :class="activeTab === tab.key ? 'text-amber-600' : 'text-slate-400'" />
        {{ tab.label }}
      </button>
    </div>

    <!-- Tab 0: 全院开课总课表 / 听课日程看板 (US-03/06) -->
    <div v-if="activeTab === 'timetable'" class="space-y-4">
      <div class="pro-card p-6">
        <!-- 头部标题与视图切换开关 -->
        <div class="flex flex-wrap items-center justify-between gap-4 mb-5 pb-4 border-b border-slate-100">
          <div>
            <h2 class="text-base font-bold text-slate-900 flex items-center gap-2">
              <div class="w-7 h-7 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                <Calendar class="w-4 h-4" />
              </div>
              全院督导听课总课表与排课日程看板 (US-03/06)
            </h2>
            <p class="text-xs text-slate-500 mt-1">
              全院各专业开课周次、教室与主讲教师分布一览，专供教学督导专家遴选听课时间并进班随堂打分
            </p>
          </div>

          <!-- 双视图模式切换器 -->
          <div class="flex items-center p-1 bg-slate-100 rounded-xl border border-slate-200/80 shadow-2xs">
            <button
              @click="timetableMode = 'matrix'"
              :class="[
                'px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition cursor-pointer',
                timetableMode === 'matrix' ? 'bg-white text-indigo-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'
              ]"
            >
              <LayoutGrid class="w-3.5 h-3.5" /> 高校周历矩阵课表
            </button>
            <button
              @click="timetableMode = 'cards'"
              :class="[
                'px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition cursor-pointer',
                timetableMode === 'cards' ? 'bg-white text-indigo-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'
              ]"
            >
              <ListFilter class="w-3.5 h-3.5" /> 多维日程卡片看板
            </button>
          </div>
        </div>

        <!-- 多维快捷筛选工具栏 (周几、节次、课程性质、教师、教室) -->
        <div class="p-4 bg-slate-50/80 border border-slate-200/80 rounded-2xl mb-5 flex flex-wrap items-center justify-between gap-3 text-xs shadow-2xs">
          <div class="flex flex-wrap items-center gap-2.5">
            <span class="font-bold text-slate-700 flex items-center gap-1.5">
              <Filter class="w-3.5 h-3.5 text-amber-600" /> 课表筛选：
            </span>

            <!-- 星期几筛选 -->
            <select v-model="scheduleFilter.dayOfWeek" @change="schedCurrentPage = 1" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 shadow-2xs font-medium cursor-pointer">
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
            <select v-model="scheduleFilter.period" @change="schedCurrentPage = 1" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 shadow-2xs font-medium cursor-pointer">
              <option value="">全部节次时段</option>
              <option value="1-2">第1-2节 (08:00 - 09:35)</option>
              <option value="3-4">第3-4节 (10:05 - 11:40)</option>
              <option value="5-6">第5-6节 (13:30 - 15:05)</option>
              <option value="7-8">第7-8节 (15:35 - 17:10)</option>
              <option value="9-10">第9-10节 (18:30 - 20:05)</option>
            </select>

            <!-- 课程性质筛选 -->
            <select v-model="scheduleFilter.courseType" @change="schedCurrentPage = 1" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 shadow-2xs font-medium cursor-pointer">
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
                class="bg-white border border-amber-200 rounded-xl pl-7 pr-3 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 w-44 shadow-2xs font-medium"
              />
              <User class="w-3.5 h-3.5 text-amber-600 absolute left-2.5 top-2.5" />
            </div>

            <!-- 教室筛选 -->
            <select v-model="scheduleFilter.classroom" @change="schedCurrentPage = 1" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 shadow-2xs font-medium cursor-pointer">
              <option value="">全部教学教室</option>
              <option v-for="cr in availableClassrooms" :key="cr" :value="cr">{{ cr }}</option>
            </select>

            <!-- 一键重置 -->
            <button
              @click="resetScheduleFilter"
              class="px-3 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-100 text-slate-600 text-xs font-semibold transition cursor-pointer flex items-center gap-1 shadow-2xs"
            >
              <RotateCcw class="w-3 h-3 text-slate-500" /> 重置
            </button>
          </div>

          <span class="text-xs text-slate-500 font-medium">
            共匹配到 <b class="text-amber-800 font-bold">{{ filteredSchedules.length }}</b> 节开课排课
          </span>
        </div>

        <!-- 视图 1：大学周历矩阵课表 (Weekly Matrix) -->
        <div v-if="timetableMode === 'matrix'" class="overflow-x-auto border border-slate-200/80 rounded-2xl shadow-subtle">
          <table class="w-full border-collapse border border-slate-200 text-xs min-w-[960px] overflow-hidden">
            <thead>
              <tr class="bg-slate-100/80 text-slate-700 text-center font-bold">
                <th class="border border-slate-200 py-3.5 px-3 w-32 bg-slate-100">节次 / 时段</th>
                <th v-for="d in weekDays" :key="d.day" class="border border-slate-200 py-3.5 px-3">
                  <div class="text-slate-900 font-bold">{{ d.name }}</div>
                  <div class="text-[10px] text-slate-400 font-mono font-normal">{{ d.en }}</div>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="slot in periodSlots" :key="slot.key" class="border-b border-slate-200">
                <!-- 节次标题列 -->
                <td class="border border-slate-200 p-3 text-center bg-slate-50/70">
                  <div class="font-bold text-slate-900">{{ slot.label }}</div>
                  <span class="inline-block mt-1 px-2 py-0.5 rounded text-[10px] bg-slate-200/80 text-slate-700 font-semibold">
                    {{ slot.tag }}
                  </span>
                  <div class="text-[10px] text-slate-500 font-mono mt-1">{{ slot.time }}</div>
                </td>

                <!-- 星期一至星期日单元格 -->
                <td
                  v-for="d in weekDays"
                  :key="d.day"
                  class="border border-slate-200 p-2.5 align-top bg-white hover:bg-amber-50/20 transition duration-150 min-h-[110px]"
                >
                  <div v-if="getSchedulesForSlot(d.day, slot).length === 0" class="h-full min-h-[90px] flex items-center justify-center text-slate-300 text-[11px]">
                    -
                  </div>
                  <div v-else class="space-y-2.5">
                    <div
                      v-for="s in getSchedulesForSlot(d.day, slot)"
                      :key="s.id"
                      class="p-3 rounded-xl border border-indigo-200 bg-gradient-to-br from-indigo-50/60 via-white to-amber-50/20 hover:shadow-card hover:border-amber-400 transition shadow-2xs space-y-2"
                    >
                      <!-- 主讲教师突出第一视觉 -->
                      <div class="flex items-center justify-between gap-1">
                        <span class="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-lg bg-indigo-100/90 text-indigo-950 font-bold text-[11px]">
                          <User class="w-3 h-3 text-indigo-600" /> {{ s.offering?.teacherName }}
                        </span>
                        <span class="text-[10px] px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200 font-semibold">
                          {{ s.classroom }}
                        </span>
                      </div>

                      <!-- 课程与代码 -->
                      <div>
                        <div class="font-bold text-slate-900 text-xs leading-tight">
                          {{ s.offering?.course?.courseName }}
                        </div>
                        <div class="text-[10px] text-slate-500 font-mono mt-1">
                          {{ s.offering?.className }} · {{ s.offering?.studentCount }}人
                        </div>
                        <div class="text-[10px] text-slate-400 font-mono">
                          {{ s.weekRange }}
                        </div>
                      </div>

                      <!-- 督导快捷听课操作按钮 -->
                      <div class="pt-2 border-t border-slate-100 flex items-center justify-between gap-1.5">
                        <button
                          @click="openPreviewResources(s.offering.course.id)"
                          class="text-[11px] text-slate-500 hover:text-indigo-600 flex items-center gap-0.5 font-semibold cursor-pointer"
                          title="课件免密预审"
                        >
                          <FileText class="w-3 h-3" /> 课件
                        </button>
                        <button
                          @click="enterLiveSupervision(s.offering.id, s)"
                          :class="[
                            'text-[11px] flex items-center gap-0.5 font-bold cursor-pointer px-2 py-0.5 rounded-lg transition',
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
                          class="px-2.5 py-1 bg-amber-600 hover:bg-amber-700 text-white rounded-lg text-[10px] font-bold shadow-2xs flex items-center gap-0.5 transition cursor-pointer"
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
          <div v-if="filteredSchedules.length === 0" class="py-14 text-center text-slate-400 bg-slate-50/60 rounded-2xl border border-dashed border-slate-200">
            <Calendar class="w-8 h-8 text-slate-300 mx-auto mb-2" />
            <span>没有检索到符合当前筛选条件 (周几/节次/课程性质/教师/教室) 的开课排课记录</span>
          </div>
          <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div
              v-for="s in pagedSchedules"
              :key="s.id"
              class="bg-white border border-slate-200/80 rounded-2xl p-5 hover:border-amber-300 hover:shadow-card transition shadow-subtle flex flex-col justify-between space-y-4"
            >
              <div>
                <!-- 顶部：突出任课教师第一视觉 -->
                <div class="flex items-start justify-between pb-3 border-b border-slate-100">
                  <div>
                    <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-indigo-50 border border-indigo-200 text-indigo-900 font-bold text-xs">
                      <User class="w-3.5 h-3.5 text-indigo-600" />
                      主讲教师：{{ s.offering?.teacherName }}
                    </span>
                    <div class="mt-2.5">
                      <span class="text-[10px] px-2 py-0.5 rounded bg-slate-100 text-slate-600 border border-slate-200 font-mono">
                        {{ s.offering?.course?.courseCode }}
                      </span>
                      <h3 class="text-sm font-bold text-slate-900 mt-1.5">{{ s.offering?.course?.courseName }}</h3>
                    </div>
                  </div>
                  <div class="flex flex-col items-end gap-1.5">
                    <span class="text-[10px] px-2.5 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 font-semibold">
                      {{ s.offering?.course?.courseType || '专业课' }}
                    </span>
                    <span class="text-xs px-2.5 py-0.5 bg-slate-100 text-slate-600 border border-slate-200 rounded-lg font-mono">
                      {{ s.offering?.studentCount }} 人额
                    </span>
                  </div>
                </div>

                <!-- 教学班级与教室时段 -->
                <div class="mt-3.5 space-y-2 text-xs text-slate-600">
                  <p>
                    授课班级：<span class="text-indigo-600 font-bold">{{ s.offering?.className }}</span>
                    <span class="text-slate-400 ml-1">({{ s.offering?.academicTerm }})</span>
                  </p>
                  <div class="p-3 bg-slate-50 border border-slate-200/80 rounded-xl flex items-center justify-between text-xs">
                    <div class="flex items-center gap-1.5">
                      <span class="text-emerald-700 font-semibold bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200 flex items-center gap-1">
                        <MapPin class="w-3.5 h-3.5 text-emerald-600" /> {{ s.classroom }}
                      </span>
                    </div>
                    <div class="text-right">
                      <span class="text-slate-800 font-bold flex items-center gap-1">
                        <Clock class="w-3 h-3 text-indigo-600" /> 周{{ s.dayOfWeek }} 第{{ s.startPeriod }}-{{ s.endPeriod }}节
                      </span>
                      <div class="text-[10px] text-slate-400 font-mono mt-0.5">({{ s.weekRange }})</div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 督导业务操作栏 -->
              <div class="pt-3 border-t border-slate-100 flex items-center justify-between gap-2">
                <button
                  @click="openPreviewResources(s.offering.course.id)"
                  class="text-xs text-slate-600 hover:text-indigo-600 flex items-center gap-1 font-semibold cursor-pointer"
                >
                  <FileText class="w-3.5 h-3.5" /> 预审课件
                </button>
                <div class="flex items-center gap-2">
                  <button
                    @click="enterLiveSupervision(s.offering.id, s)"
                    :class="[
                      'px-3 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1 transition cursor-pointer',
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
                    class="px-3.5 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-semibold shadow-xs flex items-center gap-1 transition cursor-pointer"
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
                class="px-3 py-1 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition font-medium cursor-pointer"
              >
                上一页
              </button>
              <div class="flex items-center gap-1">
                <button
                  v-for="p in totalSchedPages"
                  :key="p"
                  @click="schedCurrentPage = p"
                  :class="[
                    'w-7 h-7 rounded-xl text-xs font-bold flex items-center justify-center transition cursor-pointer',
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
                class="px-3 py-1 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition font-medium cursor-pointer"
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
      <div class="pro-card p-6">
        <div class="flex flex-wrap items-center justify-between gap-4 mb-5 pb-4 border-b border-slate-100">
          <!-- 复合检索条件 (US-06) - 突显教师检索优先 -->
          <div class="flex flex-wrap items-center gap-3">
            <label class="text-xs font-semibold text-slate-700 flex items-center gap-1.5">
              授权专业
              <select aria-label="授权专业" v-model="filterParams.majorId" @change="handleFilterChange" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-800 focus:outline-none focus:border-indigo-500 shadow-2xs font-normal">
                <option value="">全部授权专业</option>
                <option v-for="m in authorizedMajors" :key="m.id" :value="m.id">{{ m.majorName }}</option>
              </select>
            </label>
            <label class="text-xs font-semibold text-slate-700 flex items-center gap-1.5">
              任课教师
              <select aria-label="任课教师" v-model="filterParams.teacherId" @change="handleFilterChange" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-800 focus:outline-none focus:border-indigo-500 shadow-2xs font-normal">
                <option value="">全部教师</option>
                <option v-for="t in searchTeachers" :key="t.id" :value="t.id">{{ t.teacherName }} · {{ t.teacherCode }}</option>
              </select>
            </label>
            <!-- 课程名 / 代码 -->
            <div class="relative">
              <input
                v-model="filterParams.keyword"
                @input="handleFilterChange"
                placeholder="按课程名 / 代码检索..."
                class="bg-white border border-slate-200 rounded-xl pl-8 pr-3 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 w-48 shadow-2xs font-medium"
              />
              <Search class="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2.5" />
            </div>

            <!-- 学期选择 -->
            <select aria-label="检索学期" v-model="filterParams.term" @change="handleFilterChange(); loadAnalytics()" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-indigo-500 shadow-2xs cursor-pointer">
              <option value="">全部学期</option>
              <option v-for="t in availableTerms" :key="t" :value="t">{{ t }}</option>
            </select>

            <!-- 班级选择 -->
            <select v-model="filterParams.className" @change="supCurrentPage = 1" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-indigo-500 shadow-2xs cursor-pointer">
              <option value="">全部教学班级</option>
              <option v-for="c in availableClasses" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="flex items-center gap-3">
            <button class="px-3 py-1.5 border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 rounded-xl text-xs font-semibold shadow-2xs transition cursor-pointer" @click="resetSearch">清空检索条件</button>
            <span class="text-xs text-slate-500 font-medium">共检索到 <b class="text-indigo-600 font-bold">{{ filteredOfferings.length }}</b> 门待督导开课</span>
          </div>
        </div>
        <p v-if="searchError" role="alert" class="text-xs text-rose-600 mb-3 bg-rose-50 p-2.5 rounded-lg border border-rose-200">{{ searchError }}</p>

        <!-- 课程开课列表 (突出任课教师第一视觉) -->
        <div v-if="pagedOfferings.length === 0" class="py-14 text-center text-slate-400 bg-slate-50/60 rounded-2xl border border-dashed border-slate-200">
          未检索到符合条件的待督导开课信息
        </div>
        <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div v-for="off in pagedOfferings" :key="off.id" :data-testid="`offering-${off.id}`" class="p-5 bg-white border border-slate-200/80 rounded-2xl hover:border-indigo-300 hover:shadow-card transition shadow-subtle space-y-4 flex flex-col justify-between">
            <div>
              <!-- 顶部核心高亮：主讲/任课教师标识 -->
              <div class="flex items-center justify-between pb-3 border-b border-slate-100">
                <div class="flex items-center gap-2">
                  <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-indigo-50 border border-indigo-200 text-indigo-900 font-bold text-xs">
                    <User class="w-3.5 h-3.5 text-indigo-600" />
                    主讲教师：{{ off.teacherName }}
                  </span>
                  <span class="text-[11px] text-slate-500 font-mono">{{ off.course.courseCode }}</span>
                </div>
                <span class="px-2.5 py-0.5 rounded-full text-[10px] bg-slate-100 text-slate-600 border border-slate-200 font-medium">
                  {{ off.course.courseType }}
                </span>
              </div>

              <!-- 课程基本信息 -->
              <div class="mt-3">
                <h3 class="text-sm font-bold text-slate-900 flex items-center gap-2">
                  {{ off.course.courseName }}
                  <span class="text-[10px] px-2 py-0.5 rounded bg-slate-100 text-slate-600 font-normal font-mono">{{ off.academicTerm }}</span>
                </h3>
                <p class="text-xs text-slate-500 mt-1.5">
                  授课班级：<span class="text-indigo-600 font-bold">{{ off.className }}</span> · 选课班额：<b class="text-slate-800">{{ off.studentCount }}</b> 人
                </p>

                <!-- 关联排课时段与地点 (周几第几节 / 教室) -->
                <div class="mt-3 p-3 bg-slate-50 border border-slate-200/80 rounded-xl text-xs space-y-1.5">
                  <div v-if="getSchedulesForOffering(off.id).length > 0" class="space-y-1.5">
                    <div v-for="sch in getSchedulesForOffering(off.id)" :key="sch.id" class="flex flex-wrap items-center justify-between gap-1 text-[11px]">
                      <span class="text-emerald-700 font-semibold flex items-center gap-1 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
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
            <div class="flex items-center justify-between pt-3 border-t border-slate-100 gap-2">
              <button @click="openPreviewResources(off.course.id)" class="text-xs text-indigo-600 hover:text-indigo-800 flex items-center gap-1 font-semibold cursor-pointer">
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
                <button @click="openEvaluateForm(off)" class="px-3.5 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-semibold shadow-xs flex items-center gap-1 transition cursor-pointer">
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
              class="px-3 py-1 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition font-medium cursor-pointer"
            >
              上一页
            </button>
            <div class="flex items-center gap-1">
              <button
                v-for="p in totalSupPages"
                :key="p"
                @click="supCurrentPage = p"
                :class="[
                  'w-7 h-7 rounded-xl text-xs font-bold flex items-center justify-center transition cursor-pointer',
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
              class="px-3 py-1 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition font-medium cursor-pointer"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 2: 质量预警中心 (US-16: 零覆盖黄标 / 低分红标) -->
    <div v-if="activeTab === 'alerts'" class="space-y-4">
      <div class="pro-card p-6">
        <div class="flex items-center justify-between mb-5 pb-4 border-b border-slate-100">
          <div>
            <h2 class="text-sm font-bold text-slate-900 flex items-center gap-2">
              <div class="w-7 h-7 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center">
                <AlertTriangle class="w-4 h-4" />
              </div>
              教学质量预警中心 (US-16)
            </h2>
            <p class="text-xs text-slate-500 mt-1">
              系统自动根据督导覆盖率（&lt;30% 标黄）与综合听课均分（&lt;75分 标红）触发预警提示
            </p>
          </div>
          <span class="text-xs px-3 py-1 bg-rose-50 text-rose-700 border border-rose-200 rounded-full font-bold shadow-2xs">
            当前存在 {{ alertList.length }} 项需关注预警
          </span>
        </div>

        <div class="space-y-3.5">
          <div
            v-for="alert in alertList"
            :key="alert.courseCode"
            :class="['p-5 rounded-2xl border flex items-start justify-between gap-4 transition shadow-subtle',
                     alert.alertLevel === 'RED' ? 'bg-rose-50/50 border-rose-200/80 hover:border-rose-300' : 'bg-amber-50/50 border-amber-200/80 hover:border-amber-300']"
          >
            <div class="flex items-start gap-3.5">
              <div class="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0 mt-0.5 shadow-2xs"
                   :class="alert.alertLevel === 'RED' ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-800'">
                <AlertCircle class="w-5 h-5" />
              </div>
              <div>
                <div class="flex items-center gap-2.5 flex-wrap">
                  <h4 class="text-sm font-bold text-slate-900">{{ alert.courseName }}</h4>
                  <span class="text-xs font-mono text-slate-500">({{ alert.courseCode }})</span>
                  <span :class="['px-2.5 py-0.5 rounded-full text-[10px] font-bold shadow-2xs',
                                alert.alertLevel === 'RED' ? 'bg-rose-600 text-white' : 'bg-amber-500 text-white']">
                    {{ alert.alertLevel === 'RED' ? '低分红标预警' : '零覆盖黄标预警' }}
                  </span>
                </div>
                <p class="text-xs text-slate-600 mt-1.5 leading-relaxed">{{ alert.alertMessage }}</p>
                <div class="flex items-center gap-4 mt-2.5 text-[11px] text-slate-500 flex-wrap">
                  <span>任课教师：<b class="text-slate-800">{{ alert.teacherName || '郭军' }}</b></span>
                  <span>教研室：{{ alert.department }}</span>
                  <span v-if="alert.currentScore">当前评分：<b class="text-rose-600 font-mono font-bold">{{ alert.currentScore }}</b> 分</span>
                </div>
              </div>
            </div>
            <button
              @click="activeTab = 'search'; filterParams.keyword = alert.courseCode; loadOfferings()"
              class="px-3.5 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 text-xs font-bold shrink-0 shadow-2xs transition cursor-pointer"
            >
              前去督导
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗：随堂听评课打分 (US-13) -->
    <Teleport to="body">
    <div v-if="showEvaluateModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-[100] flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-xl p-6 shadow-modal space-y-4 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
            <Edit3 class="w-4 h-4 text-amber-600" /> 随堂听评课量化打分表 (US-13)
          </h3>
          <button @click="showEvaluateModal = false" class="text-slate-400 hover:text-slate-600 text-lg cursor-pointer">✕</button>
        </div>

        <div class="bg-slate-50 p-3.5 rounded-xl text-xs space-y-1 border border-slate-200/80">
          <p class="text-slate-900 font-bold">{{ currentOfferingForEval?.course.courseName }} - {{ currentOfferingForEval?.teacherName }} 老师</p>
          <p class="text-slate-500">听课班级：{{ currentOfferingForEval?.className }} · 班额：{{ currentOfferingForEval?.studentCount }} 人</p>
        </div>

        <p v-if="evaluationDraftId" class="text-xs text-amber-700">已恢复暂存评价，可继续编辑后提交。</p>

        <!-- 基本信息输入 -->
        <div class="grid grid-cols-2 gap-3 text-xs">
          <div>
            <label class="text-slate-700 font-semibold block mb-1">听课教学章节/主题</label>
            <input v-model="evalForm.listenTopic" placeholder="如 第三讲：需求估算与WBS分解" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 shadow-2xs" />
          </div>
          <div>
            <label class="text-slate-700 font-semibold block mb-1">听课日期</label>
            <input v-model="evalForm.evaluateDate" type="date" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 shadow-2xs" />
          </div>
        </div>

        <!-- BOPPPS 四维 100 分打分项 (每项 0-25 分) -->
        <div class="space-y-3.5 bg-slate-50 p-4 rounded-xl border border-slate-200/80">
          <h4 class="text-xs font-bold text-indigo-700 flex items-center justify-between">
            <span>BOPPPS 四维打分项（配置权重合计 100 分）</span>
            <span class="text-sm font-bold text-slate-900 font-mono">当前总计：{{ calcTotalScore }} / 100 分</span>
          </h4>

          <div class="grid grid-cols-2 gap-3.5 text-xs">
            <div>
              <div class="flex justify-between text-slate-700 font-medium mb-1">
                <span>1. 教学态度 (0-{{ scoreWeights.attitude }}分)</span>
                <b class="text-indigo-600 font-mono font-bold">{{ evalForm.scoreAttitude }} 分</b>
              </div>
              <input v-model.number="evalForm.scoreAttitude" type="range" min="0" :max="scoreWeights.attitude" step="0.5" class="w-full accent-indigo-600 cursor-pointer" />
            </div>
            <div>
              <div class="flex justify-between text-slate-700 font-medium mb-1">
                <span>2. 教学内容 (0-{{ scoreWeights.content }}分)</span>
                <b class="text-indigo-600 font-mono font-bold">{{ evalForm.scoreContent }} 分</b>
              </div>
              <input v-model.number="evalForm.scoreContent" type="range" min="0" :max="scoreWeights.content" step="0.5" class="w-full accent-indigo-600 cursor-pointer" />
            </div>
            <div>
              <div class="flex justify-between text-slate-700 font-medium mb-1">
                <span>3. 教学方法 (0-{{ scoreWeights.method }}分)</span>
                <b class="text-indigo-600 font-mono font-bold">{{ evalForm.scoreMethod }} 分</b>
              </div>
              <input v-model.number="evalForm.scoreMethod" type="range" min="0" :max="scoreWeights.method" step="0.5" class="w-full accent-indigo-600 cursor-pointer" />
            </div>
            <div>
              <div class="flex justify-between text-slate-700 font-medium mb-1">
                <span>4. 教学效果 (0-{{ scoreWeights.effect }}分)</span>
                <b class="text-indigo-600 font-mono font-bold">{{ evalForm.scoreEffect }} 分</b>
              </div>
              <input v-model.number="evalForm.scoreEffect" type="range" min="0" :max="scoreWeights.effect" step="0.5" class="w-full accent-indigo-600 cursor-pointer" />
            </div>
          </div>
        </div>

        <!-- 质性评语 (US-14) -->
        <div class="space-y-3 text-xs">
          <div>
            <label class="text-slate-700 font-semibold block mb-1">课堂教学亮点 (至少3条，每条一行，合计限500字)</label>
            <textarea v-model="evalForm.highlights" rows="2" maxlength="500" placeholder="例如：教学组织严密，能够结合实际敏捷项目案例启发学生..." class="w-full bg-white border border-slate-200 rounded-xl p-3 text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 shadow-2xs"></textarea>
          </div>
          <div>
            <label class="text-slate-700 font-semibold block mb-1">BOPPPS 针对性改进建议 (限 500 字)</label>
            <p class="text-[11px] text-slate-500 mb-1">可针对导入、目标、前测、参与式学习、后测、总结中的具体环节提出建议。</p>
            <textarea v-model="evalForm.suggestions" rows="2" maxlength="500" placeholder="例如：建议在课后作业中进一步增加甘特图与工期缓冲池实训演练..." class="w-full bg-white border border-slate-200 rounded-xl p-3 text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 shadow-2xs"></textarea>
          </div>
        </div>

        <!-- 24 小时脱敏流转规则提醒 (US-14) -->
        <div class="p-3 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-[11px] flex items-center gap-2">
          <ShieldCheck class="w-4 h-4 text-amber-600 flex-shrink-0" />
          <span>正式提交后先由教研室主任审核；审核通过后进入可配置的反馈延迟期，届时教师可查看匿名反馈。</span>
        </div>

        <!-- 按钮操作组 -->
        <div class="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
          <button @click="showEvaluateModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold transition cursor-pointer">取消</button>
          <button @click="handleSaveEvaluation(true)" class="px-4 py-2 bg-slate-200 hover:bg-slate-300 text-slate-800 rounded-xl text-xs font-semibold transition cursor-pointer">暂存草稿 (US-13)</button>
          <button @click="handleSaveEvaluation(false)" class="px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-semibold shadow-xs transition cursor-pointer">正式提交（待审核）</button>
        </div>
      </div>
    </div>

    </Teleport>

    <!-- 弹窗：听课前课件在线免密预览 (US-09) -->
    <Teleport to="body">
    <div v-if="showPreviewModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-[100] flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl max-h-[90vh] overflow-auto w-full max-w-2xl p-6 shadow-modal space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
            <FileText class="w-4 h-4 text-indigo-600" /> 听课前课件大纲免密预审
          </h3>
          <button @click="closePreview" class="text-slate-400 hover:text-slate-600 text-lg cursor-pointer">✕</button>
        </div>

        <div v-if="courseResources.length === 0" class="p-8 text-center text-xs text-slate-400">
          该课程任课教师尚未挂载课件资源
        </div>
        <div v-else class="space-y-2.5 max-h-72 overflow-y-auto">
          <div v-for="r in courseResources" :key="r.id" class="p-3 bg-slate-50 border border-slate-200/80 rounded-xl flex items-center justify-between text-xs">
            <div>
              <span class="font-bold text-slate-900">{{ r.resourceName }}</span>
              <div class="text-[11px] text-slate-500 mt-1">章节：{{ r.chapter }} · 环节：{{ r.tag }} · 大小：{{ r.fileSize }}</div>
            </div>
            <button @click="previewResource(r)" class="px-3 py-1 bg-emerald-50 hover:bg-emerald-100 text-emerald-700 text-[10px] font-bold rounded-lg border border-emerald-200 transition">
              打开限时水印预览
            </button>
          </div>
        </div>

        <ProtectedPdfPreview v-if="previewUrl" :src="previewUrl" />
        <div class="text-right pt-2">
          <button @click="closePreview" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold transition cursor-pointer">关闭</button>
        </div>
      </div>
    </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import ProtectedPdfPreview from '../components/ProtectedPdfPreview.vue'
import { ref, computed, onMounted, onUnmounted } from 'vue'
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
const coverageDetails = ref<any[]>([])
const scoreWeights = ref({attitude: 25, content: 25, method: 25, effect: 25})
const alertList = ref<SupervisionAlertVO[]>([])

const showEvaluateModal = ref(false)
const currentOfferingForEval = ref<CourseOffering | null>(null)
const evalForm = ref({
  listenTopic: '',
  evaluateDate: new Date().toISOString().split('T')[0],
  scoreAttitude: 0,
  scoreContent: 0,
  scoreMethod: 0,
  scoreEffect: 0,
  highlights: '',
  suggestions: ''
})

const calcTotalScore = computed(() => {
  const f = evalForm.value
  return (f.scoreAttitude + f.scoreContent + f.scoreMethod + f.scoreEffect).toFixed(1)
})

const showPreviewModal = ref(false)
const courseResources = ref<CourseResource[]>([])
const previewUrl = ref('')
let previewRequest = 0
const clearPreviewPdf = () => {
  previewRequest++
  if (previewUrl.value.startsWith('blob:')) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}
const closePreview = () => {
  clearPreviewPdf()
  showPreviewModal.value = false
}
onUnmounted(closePreview)

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
    dashboardMetrics.value = await supervisionApi.getDashboard(filterParams.value.term || undefined)
    coverageDetails.value = await supervisionApi.getCoverage(filterParams.value.term || undefined)
    alertList.value = await supervisionApi.getAlerts()
  } catch (e) {
    console.error('加载督导分析失败', e)
  }
}

const openPreviewResources = async (courseId: number) => {
  try {
    closePreview()
    courseResources.value = await resourceApi.search({ courseId })
    showPreviewModal.value = true
  } catch (e) {
    alert('获取课件资源失败')
  }
}

const previewResource = async (resource: CourseResource) => {
  clearPreviewPdf()
  const request = previewRequest
  try {
    const pdf = await resourceApi.getPreviewPdf(resource.id)
    if (request !== previewRequest || !showPreviewModal.value) return
    previewUrl.value = URL.createObjectURL(pdf)
  } catch (e: any) {
    if (request === previewRequest) alert(e.message || '预览失败')
  }
}

const evaluationDraftId = ref<number | undefined>()
let evaluationRequest = 0
const openEvaluateForm = async (off: CourseOffering) => {
  const request = ++evaluationRequest
  showEvaluateModal.value = false
  evaluationDraftId.value = undefined
  currentOfferingForEval.value = off
  evalForm.value = {
    listenTopic: '',
    evaluateDate: new Date().toISOString().split('T')[0],
    scoreAttitude: 0,
    scoreContent: 0,
    scoreMethod: 0,
    scoreEffect: 0,
    highlights: '',
    suggestions: ''
  }
  try {
    const evaluations = await supervisionApi.getAll({offeringId: off.id})
    if (request !== evaluationRequest) return
    const draft = evaluations.sort((a, b) => b.id - a.id)
      .find(item => item.status === 'DRAFT' || item.status === 'REJECTED')
    if (draft) {
      evaluationDraftId.value = draft.id
      evalForm.value = {
        listenTopic: draft.listenTopic || '',
        evaluateDate: draft.evaluateDate || evalForm.value.evaluateDate,
        scoreAttitude: draft.scoreAttitude ?? 0,
        scoreContent: draft.scoreContent ?? 0,
        scoreMethod: draft.scoreMethod ?? 0,
        scoreEffect: draft.scoreEffect ?? 0,
        highlights: draft.highlights || '',
        suggestions: draft.suggestions || ''
      }
    }
    showEvaluateModal.value = true
  } catch (e: any) {
    if (request === evaluationRequest) alert(e.message || '评价草稿读取失败，请重试')
  }
}

const handleSaveEvaluation = async (isDraft: boolean) => {
  if (!isDraft && new Set(evalForm.value.highlights.split(/[\r\n；;]+/)
      .map(line => line.replace(/^\s*(?:[0-9０-９]+[.、)）．:]|[-•])\s*/, '').trim()).filter(Boolean)).size < 3) {
    alert('正式提交须填写至少3条不同的教学亮点，每条单独一行'); return
  }
  try {
    const payload = {
      id: evaluationDraftId.value,
      offeringId: currentOfferingForEval.value?.id,
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
  supervisionApi.getWeights().then(v => { scoreWeights.value = v }).catch(() => {})
})
</script>
