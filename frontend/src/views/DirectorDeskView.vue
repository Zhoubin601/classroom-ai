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
            <p class="text-xs text-slate-500 mt-1">负责本专业全量课程档案规范底座、统筹排课冲突防范、工程教育认证指标点动态维护与年度质量分析报表导出</p>
          </div>
        </div>
      </div>
      <div class="flex items-center gap-2.5">
        <button
          @click="handleExportCourses"
          :disabled="isExportingCourses"
          class="px-3.5 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-semibold flex items-center gap-1.5 shadow-subtle transition cursor-pointer disabled:opacity-50"
          title="导出当前教研室全部课程档案（支持编辑后重新导入或留底）"
        >
          <Download class="w-3.5 h-3.5" /> {{ isExportingCourses ? '正在导出课程...' : '导出课程档案 (CSV)' }}
        </button>
        <button
          @click="handleExportReport"
          :disabled="isExporting"
          class="px-3.5 py-2 bg-white hover:bg-slate-50 text-emerald-700 border border-emerald-300 rounded-xl text-xs font-semibold flex items-center gap-1.5 shadow-subtle transition cursor-pointer disabled:opacity-50"
          title="导出含督导打分和班额统计的年度质量分析报表"
        >
          <FileText class="w-3.5 h-3.5 text-emerald-600" /> {{ isExporting ? '正在导出报表...' : '导出年度质量报表 (CSV)' }}
        </button>
        <button @click="openImportModal" class="px-3.5 py-2 bg-white hover:bg-slate-50 text-indigo-700 border border-indigo-200 rounded-xl text-xs font-semibold flex items-center gap-1.5 shadow-subtle transition cursor-pointer">
          <UploadCloud class="w-3.5 h-3.5 text-indigo-600" /> 批量导入课程 (CSV)
        </button>
        <button @click="openAddCourseModal" class="px-3.5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold flex items-center gap-1.5 shadow-subtle transition cursor-pointer">
          <Plus class="w-3.5 h-3.5" /> 新增专业课程档案
        </button>
      </div>
    </div>

    <!-- 子导航标签 -->
    <div class="flex items-center gap-1.5 border-b border-slate-200 pb-3">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        @click="activeTab = tab.key; if (tab.key === 'reviews') loadPendingEvaluations()"
        :class="['px-3.5 py-1.5 rounded-xl text-xs font-medium transition flex items-center gap-1.5 cursor-pointer',
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
                @input="handleFilterChange"
                placeholder="搜索课程名称 / 代码 / 教师 / 先修..."
                class="bg-white border border-slate-200 rounded-xl pl-8 pr-3 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 w-64 shadow-subtle"
              />
              <Search class="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2.5" />
            </div>
            <select
              v-model="courseFilter.courseType"
              @change="handleFilterChange"
              class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-700 focus:outline-none focus:border-indigo-500 shadow-subtle"
            >
              <option value="">全部课程性质</option>
              <option value="专业核心课">专业核心课</option>
              <option value="专业基础课">专业基础课</option>
              <option value="通识必修课">通识必修课</option>
              <option value="专业选修课">专业选修课</option>
            </select>
          </div>
          <span class="text-xs text-slate-500">共检索到 <b class="text-indigo-600 font-bold">{{ courseList.length }}</b> 门标准化课程档案 (MySQL 实时数据)</span>
        </div>

        <!-- 课程表格 -->
        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs text-slate-700">
            <thead class="bg-slate-50 text-slate-600 uppercase font-semibold border-b border-slate-200">
              <tr>
                <th class="py-3 px-4">课程代码</th>
                <th class="py-3 px-4">课程名称</th>
                <th class="py-3 px-4">所属专业 (编码)</th>
                <th class="py-3 px-4">主讲 / 任课教师</th>
                <th class="py-3 px-4">院系教研室</th>
                <th class="py-3 px-4">学分 / 学时</th>
                <th class="py-3 px-4">课程性质</th>
                <th class="py-3 px-4">先修关系</th>
                <th class="py-3 px-4 text-right">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr v-if="pagedCourses.length === 0">
                <td colspan="9" class="py-10 text-center text-slate-400">未找到符合条件的课程档案</td>
              </tr>
              <tr v-for="c in pagedCourses" :key="c.id" class="hover:bg-slate-50/80 transition-colors">
                <td class="py-3 px-4 font-mono font-semibold text-indigo-600">{{ c.courseCode }}</td>
                <td class="py-3 px-4 font-semibold text-slate-900">
                  {{ c.courseName }}
                </td>
                <td class="py-3 px-4">
                  <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] bg-indigo-50 border border-indigo-200 text-indigo-700 font-mono font-bold">
                    {{ c.majorCode || '待补全' }}
                  </span>
                  <span class="ml-1 text-[11px] text-slate-500">{{ getMajorNameByCode(c.majorCode) }}</span>
                </td>
                <td class="py-3 px-4">
                  <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-indigo-50 border border-indigo-200 text-indigo-700 font-semibold text-xs">
                    <User class="w-3.5 h-3.5 text-indigo-500" />
                    {{ c.teacherName || '郭军 (教授)' }}
                  </span>
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
                  <button @click="viewCourseDetail(c)" class="text-indigo-600 hover:text-indigo-800 font-medium cursor-pointer">大纲</button>
                  <button @click="editCourse(c)" class="text-amber-600 hover:text-amber-800 font-medium cursor-pointer">编辑</button>
                  <button @click="removeCourse(c.id)" class="text-rose-600 hover:text-rose-800 font-medium cursor-pointer">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页控制栏 (P1 级要求) -->
        <div class="mt-4 pt-4 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500">
          <div class="flex items-center gap-2">
            <span>共 <b class="text-slate-800">{{ courseList.length }}</b> 门课程</span>
            <span>·</span>
            <span>第 <b class="text-indigo-600">{{ courseCurrentPage }}</b> / {{ totalCoursePages }} 页</span>
            <div class="flex items-center gap-1 ml-2">
              <span>每页显示</span>
              <select
                v-model.number="coursePageSize"
                @change="courseCurrentPage = 1"
                class="bg-white border border-slate-200 rounded-lg px-2 py-1 text-xs text-slate-700 focus:outline-none focus:border-indigo-500"
              >
                <option :value="5">5 条</option>
                <option :value="10">10 条</option>
                <option :value="20">20 条</option>
              </select>
            </div>
          </div>

          <div class="flex items-center gap-1.5">
            <button
              @click="courseCurrentPage = Math.max(1, courseCurrentPage - 1)"
              :disabled="courseCurrentPage <= 1"
              class="px-2.5 py-1 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition font-medium"
            >
              上一页
            </button>
            <div class="flex items-center gap-1">
              <button
                v-for="p in totalCoursePages"
                :key="p"
                @click="courseCurrentPage = p"
                :class="[
                  'w-7 h-7 rounded-lg text-xs font-semibold flex items-center justify-center transition cursor-pointer',
                  courseCurrentPage === p
                    ? 'bg-indigo-600 text-white shadow-xs'
                    : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                ]"
              >
                {{ p }}
              </button>
            </div>
            <button
              @click="courseCurrentPage = Math.min(totalCoursePages, courseCurrentPage + 1)"
              :disabled="courseCurrentPage >= totalCoursePages"
              class="px-2.5 py-1 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition font-medium"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 2: 集中排课统筹与冲突检测看板 (US-03) -->
    <OfferingScheduleBoard v-if="activeTab === 'schedules'" />

    <!-- Tab 3: 按专业与版本管理的毕业要求指标点矩阵 (US-05) -->
    <div v-if="activeTab === 'indicators'" class="space-y-4">
      <div class="minimal-card p-5">
        <div class="flex flex-wrap items-center justify-between gap-4 mb-4">
          <div>
            <h2 class="text-sm font-semibold text-slate-900 flex items-center gap-2">
              <Target class="w-4 h-4 text-indigo-600" /> 东北大学工程教育专业认证毕业要求指标点矩阵
            </h2>
            <p class="text-xs text-slate-500 mt-0.5">指标点具体细化与分解已交由对应主讲教师在【任课教师工作台】填报；教研室主任在此统筹审查各门课程大纲并进行基线锁定 (US-05)</p>
          </div>
          <div class="flex flex-wrap items-center gap-2.5">
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
                  {{ c.courseCode }} - {{ c.courseName }} ({{ c.teacherName || '任课教师' }})
                </option>
              </select>
            </div>

            <!-- 新增指标点按钮 -->
            <button @click="openAddIndicatorModal" class="px-3.5 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition flex items-center gap-1.5 cursor-pointer">
              <Plus class="w-3.5 h-3.5" /> 新增认证指标点
            </button>

            <!-- 审查锁定按钮 -->
            <button @click="lockCurrentSyllabus" class="px-3.5 py-1.5 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition flex items-center gap-1.5 cursor-pointer">
              <Lock class="w-3.5 h-3.5" /> 审查锁定本版大纲
            </button>
          </div>
        </div>

        <div class="border border-indigo-100 rounded-xl p-3 mb-4 space-y-2 text-xs">
          <div class="font-semibold">导入培养方案指标目录（每行：编号 | 类别 | 描述）</div>
          <div class="flex gap-2">
            <select v-model="planMajorCode" aria-label="培养方案专业" class="border rounded p-1"><option value="">选择专业</option><option v-for="m in managedMajors" :key="m.majorCode" :value="m.majorCode">{{ m.majorName }}</option></select>
            <input v-model="planImportVersion" aria-label="培养方案版本" placeholder="如 2026版" class="border rounded p-1" />
          </div>
          <textarea v-model="planImportText" aria-label="培养方案指标目录" rows="4" class="w-full border rounded p-2" placeholder="1-1 | 工程知识 | 指标描述"></textarea>
          <button @click="savePlanCatalog" class="px-3 py-1.5 bg-indigo-600 text-white rounded">导入目录</button>
          <span class="text-slate-500 ml-2">以实际培养方案为准；不会清除旧版本</span>
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
                <th class="py-3 px-4 text-right">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr v-if="indicatorList.length === 0">
                <td colspan="6" class="py-12 text-center text-slate-400">
                  <div class="flex flex-col items-center justify-center gap-2">
                    <FileText class="w-6 h-6 text-slate-300" />
                    <span>该课程暂未录入毕业要求指标点，请点击右上角【新增认证指标点】进行录入</span>
                  </div>
                </td>
              </tr>
              <tr v-for="ind in indicatorList" :key="ind.id" class="hover:bg-slate-50/80 transition-colors">
                <td class="py-3 px-4 font-mono font-semibold text-indigo-600">{{ ind.indicatorCode }}</td>
                <td class="py-3 px-4 text-slate-900 font-medium">{{ ind.requirementCategory }}</td>
                <td class="py-3 px-4 text-slate-600 leading-relaxed max-w-md">{{ ind.indicatorDescription }}</td>
                <td class="py-3 px-4">
                  <span :class="['px-2.5 py-1 rounded-md text-[10px] font-bold font-mono',
                                ind.supportWeight === 'H' ? 'bg-rose-50 text-rose-700 border border-rose-200' :
                                (ind.supportWeight === 'M' ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'bg-slate-100 text-slate-600 border border-slate-200')]">
                    {{ ind.supportWeight }} ({{ ind.supportWeight === 'H' ? '强支撑' : (ind.supportWeight === 'M' ? '中等' : '弱支撑') }})
                  </span>
                </td>
                <td class="py-3 px-4 text-slate-500 font-mono">{{ ind.targetGoal || '目标1' }}</td>
                <td class="py-3 px-4 text-right space-x-2">
                  <button @click="openEditIndicatorModal(ind)" class="text-indigo-600 hover:text-indigo-800 font-semibold cursor-pointer">编辑</button>
                  <button @click="confirmDeleteIndicator(ind)" class="text-rose-600 hover:text-rose-800 font-semibold cursor-pointer">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Tab 4: 督导账号建档与专业授权 (US-07) -->
    <div v-if="activeTab === 'supervisors'" class="space-y-4">
      <div class="minimal-card p-5">
        <!-- 头部说明与安全边界政策提示 -->
        <div class="flex flex-wrap items-center justify-between gap-4 mb-4">
          <div>
            <h2 class="text-sm font-semibold text-slate-900 flex items-center gap-2">
              <ShieldCheck class="w-4 h-4 text-indigo-600" />
              教学质量督导账号建档与专业授权管理
            </h2>
            <p class="text-xs text-slate-500 mt-0.5">
              教研室主任专属管理通道：仅允许为督导专家分配本教研室管辖的专业。严禁跨教研室授权，督导权限实时由数据库装载校验。
            </p>
          </div>
          <div class="flex items-center gap-2.5">
            <button
              @click="openCreateSupervisorModal"
              class="px-3.5 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition flex items-center gap-1.5 cursor-pointer"
            >
              <Plus class="w-3.5 h-3.5" /> 新建督导专家账号
            </button>
          </div>
        </div>

        <!-- 主任管辖专业公示条 -->
        <div class="p-3 bg-indigo-50/70 border border-indigo-100 rounded-xl mb-4 flex flex-wrap items-center justify-between gap-3 text-xs">
          <div class="flex items-center gap-2 text-indigo-900">
            <Users class="w-4 h-4 text-indigo-600 shrink-0" />
            <span class="font-semibold">当前主任管辖范围：</span>
            <span v-if="managedMajors.length === 0" class="text-slate-500">正在获取管辖专业...</span>
            <div v-else class="flex flex-wrap gap-1.5">
              <span
                v-for="m in managedMajors"
                :key="m.majorCode"
                class="px-2 py-0.5 rounded-md bg-white border border-indigo-200 text-indigo-700 font-mono font-medium shadow-2xs"
              >
                {{ m.majorName }} ({{ m.majorCode }})
              </span>
            </div>
          </div>
          <span class="text-[11px] text-indigo-700/80 font-medium">
            防越权保护生效中 · 公开注册入口已关闭
          </span>
        </div>

        <!-- 督导账号列表表格 -->
        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs text-slate-700">
            <thead class="bg-slate-50 text-slate-600 uppercase font-semibold border-b border-slate-200">
              <tr>
                <th class="py-3 px-4">专家姓名</th>
                <th class="py-3 px-4">登录账号</th>
                <th class="py-3 px-4">所属单位 / 督导团</th>
                <th class="py-3 px-4">当前已授权专业</th>
                <th class="py-3 px-4">角色权限</th>
                <th class="py-3 px-4 text-right">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
              <tr v-if="supervisorsList.length === 0">
                <td colspan="6" class="py-10 text-center text-slate-400">暂无教学督导账号，请点击右上角新建</td>
              </tr>
              <tr v-for="sup in supervisorsList" :key="sup.id" class="hover:bg-slate-50/80 transition-colors">
                <td class="py-3 px-4 font-semibold text-slate-900 flex items-center gap-2">
                  <div class="w-6 h-6 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center text-[10px] font-bold">
                    {{ sup.realName ? sup.realName.slice(0, 1) : '督' }}
                  </div>
                  {{ sup.realName }}
                </td>
                <td class="py-3 px-4 font-mono font-medium text-slate-700">{{ sup.username }}</td>
                <td class="py-3 px-4 text-slate-500">{{ sup.department || '校教学质量督导团' }}</td>
                <td class="py-3 px-4">
                  <div class="flex flex-wrap gap-1">
                    <span
                      v-if="!sup.authorizedMajors"
                      class="px-2 py-0.5 rounded text-[11px] bg-slate-100 text-slate-400 border border-slate-200"
                    >
                      未授权专业
                    </span>
                    <span
                      v-else
                      v-for="code in sup.authorizedMajors.split(';').filter(Boolean)"
                      :key="code"
                      class="px-2 py-0.5 rounded text-[11px] bg-emerald-50 text-emerald-700 border border-emerald-200 font-mono font-medium"
                    >
                      {{ code }}
                    </span>
                  </div>
                </td>
                <td class="py-3 px-4">
                  <span class="px-2 py-0.5 rounded-full text-[10px] bg-amber-50 text-amber-700 border border-amber-200 font-medium">
                    只读审查权限 (Deny-by-Default)
                  </span>
                </td>
                <td class="py-3 px-4 text-right space-x-2">
                  <button
                    @click="openEditMajorsModal(sup)"
                    class="text-indigo-600 hover:text-indigo-800 font-medium cursor-pointer"
                  >
                    调整专业授权
                  </button>
                </td>
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
            <label class="text-slate-600 block mb-1">课程代码 (唯一) *</label>
            <input v-model="currentCourseForm.courseCode" :readonly="!!currentCourseForm.id" placeholder="如 CS3001" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle uppercase" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">课程名称 *</label>
            <input v-model="currentCourseForm.courseName" placeholder="如 软件项目管理" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">所属专业编码 *</label>
            <select v-if="managedMajors.length > 0" v-model="currentCourseForm.majorCode" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle">
              <option v-for="m in managedMajors" :key="m.majorCode" :value="m.majorCode">
                {{ m.majorName }} ({{ m.majorCode }})
              </option>
            </select>
            <input v-else v-model="currentCourseForm.majorCode" placeholder="如 SE, CS" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle uppercase" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">教研室 *</label>
            <input v-model="currentCourseForm.department" placeholder="如 软件工程教研室" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">主讲 / 任课教师</label>
            <input v-model="currentCourseForm.teacherName" placeholder="如 郭军 (教授)" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">学分 *</label>
            <input v-model.number="currentCourseForm.credits" type="number" step="0.5" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">总学时 * (需等于理论+实验)</label>
            <input v-model.number="currentCourseForm.hours" type="number" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">理论学时</label>
            <input v-model.number="currentCourseForm.theoryHours" @input="syncTotalHours" type="number" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">实验 / 上机学时</label>
            <input v-model.number="currentCourseForm.practiceHours" @input="syncTotalHours" type="number" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div>
            <label class="text-slate-600 block mb-1">课程性质 *</label>
            <select v-model="currentCourseForm.courseType" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle">
              <option value="专业核心课">专业核心课</option>
              <option value="专业基础课">专业基础课</option>
              <option value="专业选修课">专业选修课</option>
              <option value="通识必修课">通识必修课</option>
            </select>
          </div>
          <div class="col-span-2">
            <label class="text-slate-600 block mb-1">先修关系编码 (多个用分号隔开，如 CS1001;CS2001)</label>
            <input v-model="currentCourseForm.prerequisites" placeholder="如 CS1001;CS2001" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle" />
          </div>
          <div class="col-span-2">
            <label class="text-slate-600 block mb-1">课程简介与教学目标 (US-02)</label>
            <textarea v-model="currentCourseForm.description" rows="3" class="w-full bg-white border border-slate-200 rounded-xl p-3 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"></textarea>
          </div>
        </div>

        <div v-if="courseErrorMessage" class="p-2.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-center gap-2">
          <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
          <span>{{ courseErrorMessage }}</span>
        </div>

        <div class="flex items-center justify-end gap-2.5 pt-2">
          <button @click="showCourseModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition cursor-pointer">取消</button>
          <button @click="handleSaveCourse" class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition cursor-pointer">保存并入库 (MySQL)</button>
        </div>
      </div>
    </div>

    <!-- 弹窗：课程 CSV 批量规范导入 (US-01) -->
    <div v-if="showImportModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-2xl p-6 shadow-modal space-y-4 max-h-[90vh] flex flex-col">
        <!-- 弹窗头部 -->
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <div class="flex items-center gap-2.5">
            <div class="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <FileSpreadsheet class="w-4 h-4" />
            </div>
            <div>
              <h3 class="text-base font-bold text-slate-900">课程 CSV 批量规范导入 (US-01)</h3>
              <p class="text-xs text-slate-500">遵循两阶段校验与整批事务原子提交，支持批次内先修课程互相引用</p>
            </div>
          </div>
          <button @click="showImportModal = false" class="text-slate-400 hover:text-slate-600 text-sm cursor-pointer">✕</button>
        </div>

        <!-- 内容区域 (滚动) -->
        <div class="space-y-4 overflow-y-auto pr-1 flex-1 text-xs">
          <!-- 步骤 1：模板下载与课程导出说明 -->
          <div class="p-3.5 bg-slate-50 border border-slate-200 rounded-xl flex flex-wrap items-center justify-between gap-3">
            <div>
              <div class="font-semibold text-slate-800 flex items-center gap-1.5">
                <span>标准课程档案 CSV (带 UTF-8 BOM)</span>
                <span class="text-[10px] px-1.5 py-0.2 rounded bg-emerald-50 text-emerald-700 border border-emerald-200 font-medium">双向闭环</span>
              </div>
              <div class="text-slate-500 text-[11px] mt-0.5">支持导出当前教研室已有课程作为参照，或下载空白模板规范录入新课程</div>
            </div>
            <div class="flex items-center gap-2">
              <button
                @click="handleExportCourses"
                :disabled="isExportingCourses"
                class="px-3 py-1.5 bg-emerald-50 hover:bg-emerald-100 border border-emerald-300 text-emerald-800 rounded-lg font-semibold text-xs flex items-center gap-1.5 shadow-subtle cursor-pointer transition disabled:opacity-50"
              >
                <Download class="w-3.5 h-3.5 text-emerald-600" /> {{ isExportingCourses ? '导出中...' : '导出已有课程 (CSV)' }}
              </button>
              <button
                @click="handleDownloadTemplate"
                class="px-3 py-1.5 bg-white hover:bg-slate-100 border border-slate-300 rounded-lg text-slate-700 font-semibold text-xs flex items-center gap-1.5 shadow-subtle cursor-pointer transition"
              >
                <Download class="w-3.5 h-3.5 text-indigo-600" /> 下载空白模板
              </button>
            </div>
          </div>

          <!-- 核心指引：专业编码速查字典与说明 -->
          <div class="p-3.5 bg-indigo-50/70 border border-indigo-200 rounded-xl space-y-2">
            <div class="flex items-center justify-between">
              <span class="font-bold text-indigo-900 flex items-center gap-1.5">
                <BookOpen class="w-3.5 h-3.5 text-indigo-600" /> 专业编码速查字典 (CSV必填字段)
              </span>
              <span class="text-[11px] text-indigo-600 font-medium">CSV 第4列【专业编码】需填写对应英文代码</span>
            </div>
            <div class="flex flex-wrap gap-2 pt-0.5">
              <span
                v-for="m in (allMajors.length > 0 ? allMajors : managedMajors)"
                :key="m.majorCode"
                class="px-2.5 py-1 rounded-lg bg-white border border-indigo-200 text-slate-800 text-xs shadow-xs flex items-center gap-1.5"
              >
                <b class="font-mono text-indigo-700 font-bold">{{ m.majorCode }}</b>
                <span class="text-slate-700 font-medium">{{ m.majorName }}</span>
                <span v-if="m.department" class="text-[10px] text-slate-400">({{ m.department }})</span>
              </span>
            </div>
            <div class="text-[11px] text-indigo-700 leading-relaxed pt-1">
              💡 <b>专业编码是什么？</b> 专业编码是学校开设各专业的官方英文字符代号。例如数据科学教研室对应 <b>DS</b>（数据科学与大数据技术），软件工程对应 <b>SE</b>，计算机对应 <b>CS</b>，人工智能对应 <b>AI</b>，网络安全对应 <b>SEC</b>。
            </div>
          </div>

          <!-- 提示条：区分课程档案与质量报表，以及防重规则说明 -->
          <div class="p-2.5 bg-amber-50 border border-amber-200 rounded-xl text-[11px] text-amber-900 flex items-start gap-2">
            <AlertCircle class="w-4 h-4 shrink-0 text-amber-600 mt-0.5" />
            <div class="space-y-0.5">
              <div><b>新课程录入防重规则</b>：本功能用于批量录入未入库的新课程档案。若使用【导出已有课程】的 CSV，请修改课程编码为新编码（如 DS9001）；若保持已有编码（如 DS2001），系统将按防重保护机制提示错误。</div>
              <div class="text-slate-600">先修课程支持填写课程代码（如 CS2001）或中文课程名（如 数据结构与算法）。请勿上传包含督导打分和班额的【年度质量分析报表】。</div>
            </div>
          </div>

          <!-- 上传文件选择区 -->
          <div class="border-2 border-dashed border-slate-200 hover:border-indigo-400 rounded-xl p-5 text-center transition bg-white cursor-pointer relative">
            <input
              type="file"
              accept=".csv"
              @change="handleFileChange"
              class="absolute inset-0 opacity-0 cursor-pointer w-full h-full"
            />
            <div class="flex flex-col items-center justify-center gap-2">
              <UploadCloud class="w-8 h-8 text-indigo-500" />
              <div>
                <span class="font-semibold text-indigo-600">点击上传</span> 或拖拽 CSV 文件至此处
              </div>
              <div class="text-slate-400 text-[11px]">
                {{ selectedFile ? `已选择: ${selectedFile.name} (${(selectedFile.size / 1024).toFixed(1)} KB)` : '仅支持 CSV 文件，每批次限 1,000 行、5MB 内，30分钟有效期' }}
              </div>
            </div>
          </div>

          <!-- 正在解析 Spinner -->
          <div v-if="isUploading" class="py-6 text-center text-slate-500 space-y-2">
            <div class="inline-block animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600"></div>
            <div>正在全维度校验数据 (学时守恒、专业有效性、先修引用、编码唯一)...</div>
          </div>

          <!-- 阶段 1 解析预览结果 -->
          <div v-if="importPreview && !isUploading" class="space-y-3">
            <!-- 统计指标 -->
            <div class="grid grid-cols-3 gap-3">
              <div class="p-3 bg-slate-50 rounded-xl border border-slate-200 text-center">
                <div class="text-slate-500 text-[11px]">总解析行数</div>
                <div class="text-lg font-bold text-slate-900 mt-0.5">{{ importPreview.totalCount }}</div>
              </div>
              <div class="p-3 bg-emerald-50 rounded-xl border border-emerald-200 text-center">
                <div class="text-emerald-700 text-[11px] font-medium">有效课程数</div>
                <div class="text-lg font-bold text-emerald-600 mt-0.5">{{ importPreview.successCount }}</div>
              </div>
              <div class="p-3 bg-rose-50 rounded-xl border border-rose-200 text-center">
                <div class="text-rose-700 text-[11px] font-medium">校验错误数</div>
                <div class="text-lg font-bold text-rose-600 mt-0.5">{{ importPreview.errorCount }}</div>
              </div>
            </div>

            <!-- 存在错误时：展示错误清单并警示整批回滚 -->
            <div v-if="importPreview.errorCount > 0" class="space-y-2">
              <div class="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs flex items-center gap-2">
                <XCircle class="w-4 h-4 shrink-0 text-rose-600" />
                <span>检测到 <b>{{ importPreview.errorCount }}</b> 处校验错误。系统实行整批回滚保护原则，禁止部分入库，请修正后重新上传：</span>
              </div>
              <div class="border border-rose-200 rounded-xl overflow-hidden max-h-48 overflow-y-auto">
                <table class="w-full text-left text-xs">
                  <thead class="bg-rose-100/60 text-rose-800 uppercase font-semibold">
                    <tr>
                      <th class="py-2 px-3 w-16">行号</th>
                      <th class="py-2 px-3 w-28">问题字段</th>
                      <th class="py-2 px-3">错误原因</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-rose-100 bg-white">
                    <tr v-for="(err, idx) in importPreview.errors" :key="idx" class="hover:bg-rose-50/50">
                      <td class="py-2 px-3 font-mono font-semibold text-rose-600">第 {{ err.rowNumber }} 行</td>
                      <td class="py-2 px-3 font-semibold text-slate-700">{{ err.field }}</td>
                      <td class="py-2 px-3 text-rose-700">{{ err.reason }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- 无错误且有有效行：展示数据预览并允许确认入库 -->
            <div v-else-if="importPreview.successCount > 0" class="space-y-2">
              <div class="p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-800 text-xs flex items-center gap-2">
                <CheckCircle2 class="w-4 h-4 shrink-0 text-emerald-600" />
                <span>全量数据校验通过！批次已就绪 (ID: <span class="font-mono">{{ importPreview.batchId?.slice(0, 8) }}...</span>)，可安全整批原子提交入库。</span>
              </div>
              <div class="border border-slate-200 rounded-xl overflow-hidden max-h-48 overflow-y-auto">
                <table class="w-full text-left text-xs">
                  <thead class="bg-slate-50 text-slate-700 font-semibold border-b border-slate-200">
                    <tr>
                      <th class="py-2 px-3">课程编码</th>
                      <th class="py-2 px-3">课程名称</th>
                      <th class="py-2 px-3">专业</th>
                      <th class="py-2 px-3">学分/学时</th>
                      <th class="py-2 px-3">性质</th>
                      <th class="py-2 px-3">先修课程</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-slate-100 bg-white">
                    <tr v-for="row in importPreview.validRows.slice(0, 5)" :key="row.courseCode" class="hover:bg-slate-50">
                      <td class="py-2 px-3 font-mono font-semibold text-indigo-600">{{ row.courseCode }}</td>
                      <td class="py-2 px-3 font-semibold text-slate-800">{{ row.courseName }}</td>
                      <td class="py-2 px-3 text-slate-600">{{ row.majorCode }}</td>
                      <td class="py-2 px-3 text-slate-600">{{ row.credits }}分 / {{ row.hours }}h (理{{ row.theoryHours }}+实{{ row.practiceHours }})</td>
                      <td class="py-2 px-3 text-slate-600">{{ row.courseType }}</td>
                      <td class="py-2 px-3 text-slate-500">{{ row.prerequisites || '无' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-if="importPreview.validRows.length > 5" class="text-slate-400 text-center text-[11px]">
                ... 仅预览展示前 5 条，共计 {{ importPreview.validRows.length }} 门课程将被整批入库
              </div>
            </div>
          </div>

          <!-- 通用错误反馈 -->
          <div v-if="importErrorMessage" class="p-2.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-center gap-2">
            <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
            <span>{{ importErrorMessage }}</span>
          </div>
        </div>

        <!-- 弹窗底部操作 -->
        <div class="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
          <button @click="showImportModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition cursor-pointer">关闭</button>
          <button
            @click="handleConfirmImport"
            :disabled="!canConfirmImport || isConfirming"
            class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed text-white rounded-xl text-xs font-semibold shadow-subtle transition cursor-pointer flex items-center gap-1.5"
          >
            <span v-if="isConfirming" class="inline-block animate-spin rounded-full h-3.5 w-3.5 border-b-2 border-white"></span>
            <span>{{ isConfirming ? '正在整批事务入库...' : '确认导入并整批入库 (US-01)' }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗：新增 / 编辑认证指标点 (写进 MySQL) -->
    <div v-if="showIndicatorModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-lg p-6 shadow-modal space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
            <Target class="w-4 h-4 text-indigo-600" /> {{ indicatorForm.id ? '修改毕业要求指标点' : '新增毕业要求指标点' }}
          </h3>
          <span class="text-[11px] px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200 font-mono">
            直接持久化至 MySQL
          </span>
        </div>

        <div class="space-y-3 text-xs">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="text-slate-600 block mb-1">指标点编号 (如 1-1, 11-1)</label>
              <select v-model="indicatorForm.indicatorCode" @change="selectPlanIndicator" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2">
                <option value="">请选择</option>
                <option v-for="item in planCatalog" :key="item.indicatorCode" :value="item.indicatorCode">{{ item.indicatorCode }}</option>
              </select>
            </div>
            <div>
              <label class="text-slate-600 block mb-1">支撑权重 (H强/M中/L弱)</label>
              <select
                v-model="indicatorForm.supportWeight"
                class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle font-semibold"
              >
                <option value="H">H (强支撑)</option>
                <option value="M">M (中等支撑)</option>
                <option value="L">L (弱支撑)</option>
              </select>
            </div>
          </div>

          <div>
            <label class="text-slate-600 block mb-1">毕业要求大项</label>
            <select
              v-model="indicatorForm.requirementCategory"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle"
            >
              <option v-for="cat in standardIndicatorCategories" :key="cat" :value="cat">
                {{ cat }}
              </option>
            </select>
          </div>

          <div>
            <label class="text-slate-600 block mb-1">对应课程目标 (如 目标1, 目标2)</label>
            <input
              v-model="indicatorForm.targetGoal"
              placeholder="如 目标1"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"
            />
          </div>

          <div>
            <label class="text-slate-600 block mb-1">指标点分解内容表述</label>
            <textarea
              v-model="indicatorForm.indicatorDescription"
              rows="3"
              placeholder="请输入该指标点在课程中的分解细化要求与能力观测点..."
              class="w-full bg-white border border-slate-200 rounded-xl p-3 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"
            ></textarea>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-100">
          <button @click="showIndicatorModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition cursor-pointer">取消</button>
          <button @click="handleSaveIndicator" class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition cursor-pointer">保存并写入 MySQL</button>
        </div>
      </div>
    </div>

    <!-- 弹窗：主任新建督导专家账号 -->
    <div v-if="showCreateSupervisorModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-md p-6 shadow-modal space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
            <ShieldCheck class="w-4 h-4 text-indigo-600" /> 新建督导专家账号
          </h3>
          <span class="text-[11px] px-2 py-0.5 rounded bg-indigo-50 text-indigo-700 border border-indigo-200 font-mono">
            主任权限分配
          </span>
        </div>

        <div class="space-y-3 text-xs">
          <div>
            <label class="text-slate-600 block mb-1">登录账号 (Username) *</label>
            <input
              v-model="createSupervisorForm.username"
              placeholder="如 supervisor.zhao"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle font-mono"
            />
          </div>

          <div>
            <label class="text-slate-600 block mb-1">初始密码 (Password) *</label>
            <input
              v-model="createSupervisorForm.password"
              type="password"
              placeholder="请输入至少6位初始密码"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"
            />
          </div>

          <div>
            <label class="text-slate-600 block mb-1">专家真实姓名 *</label>
            <input
              v-model="createSupervisorForm.realName"
              placeholder="如 赵督导 (教授)"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"
            />
          </div>

          <div>
            <label class="text-slate-600 block mb-1">所属单位 / 督导部门</label>
            <input
              v-model="createSupervisorForm.department"
              placeholder="校教学质量监控与督导评估中心"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"
            />
          </div>

          <div>
            <label class="text-slate-600 block mb-1.5 font-semibold text-slate-800">授权管辖专业 (严格限定主任管辖范围)</label>
            <div class="p-3 bg-slate-50 border border-slate-200 rounded-xl space-y-2">
              <div v-if="managedMajors.length === 0" class="text-slate-400 text-xs">
                未检索到当前管辖专业
              </div>
              <label
                v-for="m in managedMajors"
                :key="m.majorCode"
                class="flex items-center gap-2 cursor-pointer text-slate-700 hover:text-slate-900"
              >
                <input
                  type="checkbox"
                  :value="m.majorCode"
                  v-model="createSupervisorForm.selectedMajors"
                  class="rounded text-indigo-600 focus:ring-indigo-500 border-slate-300"
                />
                <span class="font-medium">{{ m.majorName }} ({{ m.majorCode }})</span>
              </label>
            </div>
          </div>
        </div>

        <div v-if="supervisorErrorMessage" class="p-2.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-center gap-2">
          <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
          <span>{{ supervisorErrorMessage }}</span>
        </div>

        <div class="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-100">
          <button @click="showCreateSupervisorModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition cursor-pointer">取消</button>
          <button @click="handleCreateSupervisor" class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition cursor-pointer">创建并授权</button>
        </div>
      </div>
    </div>

    <!-- 弹窗：主任更新已有督导的专业授权 -->
    <div v-if="showEditMajorsModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-md p-6 shadow-modal space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
            <ShieldCheck class="w-4 h-4 text-indigo-600" /> 调整督导专业授权
          </h3>
          <span class="text-[11px] px-2 py-0.5 rounded bg-indigo-50 text-indigo-700 border border-indigo-200 font-mono">
            {{ editingSupervisor?.realName }} ({{ editingSupervisor?.username }})
          </span>
        </div>

        <div class="space-y-3 text-xs">
          <p class="text-slate-500">
            勾选当前主任管辖范围内的专业以授予该督导只读审查权限；取消勾选将即刻撤销，服务端在当次请求中生效。
          </p>

          <div class="p-3 bg-slate-50 border border-slate-200 rounded-xl space-y-2">
            <div v-if="managedMajors.length === 0" class="text-slate-400 text-xs">
              未检索到当前管辖专业
            </div>
            <label
              v-for="m in managedMajors"
              :key="m.majorCode"
              class="flex items-center gap-2 cursor-pointer text-slate-700 hover:text-slate-900"
            >
              <input
                type="checkbox"
                :value="m.majorCode"
                v-model="editSupervisorSelectedMajors"
                class="rounded text-indigo-600 focus:ring-indigo-500 border-slate-300"
              />
              <span class="font-medium">{{ m.majorName }} ({{ m.majorCode }})</span>
            </label>
          </div>
        </div>

        <div v-if="supervisorErrorMessage" class="p-2.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-center gap-2">
          <AlertCircle class="w-4 h-4 shrink-0 text-rose-500" />
          <span>{{ supervisorErrorMessage }}</span>
        </div>

        <div class="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-100">
          <button @click="showEditMajorsModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition cursor-pointer">取消</button>
          <button @click="handleUpdateSupervisorMajors" class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition cursor-pointer">更新授权</button>
        </div>
      </div>
    </div>
    <div v-if="activeTab === 'reviews'" class="minimal-card p-5 space-y-3">
      <div class="flex justify-between"><h2 class="font-semibold">待审核督导评价</h2><button @click="loadPendingEvaluations" class="text-indigo-600 text-xs">刷新</button></div>
      <p v-if="pendingEvaluations.length === 0" class="text-xs text-slate-500">暂无待审核评价</p>
      <div v-for="item in pendingEvaluations" :key="item.id" class="border rounded-xl p-3 text-xs space-y-2">
        <div class="font-semibold">{{ item.offering?.course?.courseName }} · {{ item.offering?.teacherName }} · {{ item.totalScore }} 分</div>
        <div>听课主题：{{ item.listenTopic }}</div><div>亮点：{{ item.highlights }}</div><div>建议：{{ item.suggestions }}</div>
        <div class="flex gap-2"><button @click="reviewEvaluation(item.id, true)" class="px-3 py-1 bg-emerald-600 text-white rounded">通过</button><button @click="reviewEvaluation(item.id, false)" class="px-3 py-1 bg-rose-600 text-white rounded">退回</button></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import OfferingScheduleBoard from '../components/OfferingScheduleBoard.vue'
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
  AlertCircle,
  User,
  Filter,
  RotateCcw,
  ShieldCheck,
  Users,
  Check,
  UploadCloud,
  FileSpreadsheet,
  CheckCircle2,
  XCircle
} from 'lucide-vue-next'
import { courseApi, syllabusApi, supervisionApi, directorApi, courseImportApi, majorApi } from '../api'
import type { Course, GraduationIndicator, Major, UserVO, ImportPreviewVO } from '../api/types'

const allMajors = ref<Major[]>([])
const loadAllMajors = async () => {
  try {
    const list = await majorApi.getAll()
    allMajors.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('加载专业字典失败', e)
  }
}

const getMajorNameByCode = (code?: string) => {
  if (!code) return ''
  const m = allMajors.value.find(item => item.majorCode?.toUpperCase() === code.toUpperCase())
  return m ? m.majorName : ''
}

const activeTab = ref('courses')
const tabs = [
  { key: 'courses', label: '专业全量课程档案 (US-01)', iconComp: BookOpen },
  { key: 'schedules', label: '开课排课统筹看板 (US-03)', iconComp: Calendar },
  { key: 'indicators', label: '毕业要求指标点矩阵 (US-05)', iconComp: Target },
  { key: 'supervisors', label: '督导建档与专业授权 (US-07)', iconComp: ShieldCheck },
  { key: 'reviews', label: '督导评价审核 (US-13)', iconComp: CheckCircle2 }
]

const planMajorCode = ref('')
const planImportVersion = ref('')
const planImportText = ref('')
const pendingEvaluations = ref<any[]>([])

const savePlanCatalog = async () => {
  try {
    if (!planMajorCode.value || !planImportVersion.value.trim()) throw new Error('请选择专业并输入培养方案版本')
    const rows = planImportText.value.split(/\r?\n/).map(line => line.trim()).filter(Boolean).map(line => {
      const parts = line.split('|').map(v => v.trim())
      if (parts.length < 3 || !parts[0] || !parts[1] || !parts[2]) throw new Error('每行需包含编号、类别、描述')
      return { indicatorCode: parts[0], requirementCategory: parts[1], indicatorDescription: parts.slice(2).join('|') }
    })
    if (!rows.length) throw new Error('请填写指标目录')
    await syllabusApi.importPlanIndicators(planMajorCode.value, planImportVersion.value, rows)
    alert(`已导入 ${rows.length} 条培养方案指标`)
  } catch (e: any) { alert(e.response?.data?.message || e.message || '导入失败') }
}

const loadPendingEvaluations = async () => {
  try { pendingEvaluations.value = (await supervisionApi.getAll()).filter(e => e.status === 'PENDING_REVIEW') }
  catch (e) { console.error('加载待审核评价失败', e) }
}
const reviewEvaluation = async (id: number, approved: boolean) => {
  try {
    await supervisionApi.review(id, approved)
    await loadPendingEvaluations()
  } catch (e: any) { alert(e.response?.data?.message || '审核失败') }
}

const courseList = ref<Course[]>([])
const indicatorList = ref<GraduationIndicator[]>([])

const courseFilter = ref({ keyword: '', courseType: '' })
const selectedCourseIdForIndicator = ref<number | ''>('')

// 分页状态管理 (P1 级要求)
const courseCurrentPage = ref(1)
const coursePageSize = ref(5)

const handleFilterChange = () => {
  courseCurrentPage.value = 1
  loadCourses()
}

const totalCoursePages = computed(() => {
  return Math.ceil(courseList.value.length / coursePageSize.value) || 1
})

const pagedCourses = computed(() => {
  const start = (courseCurrentPage.value - 1) * coursePageSize.value
  return courseList.value.slice(start, start + coursePageSize.value)
})

const showCourseModal = ref(false)
const currentCourseForm = ref<any>({})
const courseErrorMessage = ref('')

const syncTotalHours = () => {
  const th = Number(currentCourseForm.value.theoryHours) || 0
  const ph = Number(currentCourseForm.value.practiceHours) || 0
  currentCourseForm.value.hours = th + ph
}

// ==================== 课程批量导入相关 (US-01) ====================
const showImportModal = ref(false)
const selectedFile = ref<File | null>(null)
const isUploading = ref(false)
const isConfirming = ref(false)
const importPreview = ref<ImportPreviewVO | null>(null)
const importErrorMessage = ref('')

const canConfirmImport = computed(() => {
  return (
    importPreview.value &&
    importPreview.value.batchId &&
    importPreview.value.errorCount === 0 &&
    importPreview.value.successCount > 0
  )
})

const openImportModal = () => {
  selectedFile.value = null
  importPreview.value = null
  importErrorMessage.value = ''
  isUploading.value = false
  isConfirming.value = false
  showImportModal.value = true
}

const isExportingCourses = ref(false)
const handleExportCourses = async () => {
  if (isExportingCourses.value) return
  isExportingCourses.value = true
  try {
    const blob = await courseApi.exportCourses()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', 'courses_export.csv')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (e: any) {
    alert('导出课程档案失败：' + (e.response?.data?.message || e.message))
  } finally {
    isExportingCourses.value = false
  }
}

const isExporting = ref(false)
const handleExportReport = async () => {
  if (isExporting.value) return
  isExporting.value = true
  try {
    const blob = await supervisionApi.exportReport()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', '2026-Northeastern-University-Teaching-Quality-Report.csv')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (e: any) {
    alert('导出年度质量报表失败：' + (e.response?.data?.message || e.message))
  } finally {
    isExporting.value = false
  }
}

const handleDownloadTemplate = async () => {
  try {
    const blob = await courseImportApi.downloadTemplate()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', 'course_import_template.csv')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (e: any) {
    alert('下载模板失败：' + (e.response?.data?.message || e.message))
  }
}

const handleFileChange = async (e: Event) => {
  const target = e.target as HTMLInputElement
  if (!target.files || target.files.length === 0) return
  const file = target.files[0]
  if (!file.name.toLowerCase().endsWith('.csv')) {
    importErrorMessage.value = '请上传标准 CSV 文件 (.csv)'
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    importErrorMessage.value = '文件超出 5MB 大小限制'
    return
  }

  selectedFile.value = file
  importErrorMessage.value = ''
  importPreview.value = null
  isUploading.value = true

  try {
    const previewData = await courseImportApi.preview(file)
    importPreview.value = previewData
  } catch (err: any) {
    importErrorMessage.value = err.response?.data?.message || err.message || 'CSV 解析校验失败'
  } finally {
    isUploading.value = false
    target.value = ''
  }
}

const handleConfirmImport = async () => {
  if (!canConfirmImport.value) return
  isConfirming.value = true
  importErrorMessage.value = ''
  try {
    const res = await courseImportApi.confirm(importPreview.value.batchId)
    alert(res.message || `成功批量导入 ${res.importedCount} 门课程档案！`)
    showImportModal.value = false
    await loadCourses()
  } catch (err: any) {
    importErrorMessage.value = err.response?.data?.message || err.message || '确认导入失败，批次可能已失效或被消费'
  } finally {
    isConfirming.value = false
  }
}


// 指标点管理相关 (新增/修改/删除 MySQL 持久化)
const showIndicatorModal = ref(false)
const indicatorForm = ref<any>({
  id: null,
  indicatorCode: '',
  requirementCategory: '1. 工程知识',
  indicatorDescription: '',
  supportWeight: 'H',
  targetGoal: '目标1'
})

const planCatalog = ref<GraduationIndicator[]>([])
const standardIndicatorCategories = computed(() => Array.from(new Set(planCatalog.value.map(i => i.requirementCategory))))

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

const loadIndicatorsForSelectedCourse = async () => {
  if (!selectedCourseIdForIndicator.value) {
    indicatorList.value = []
    return
  }
  try {
    const res = await syllabusApi.getIndicators(Number(selectedCourseIdForIndicator.value))
    indicatorList.value = Array.isArray(res) ? res : []
    const course = courseList.value.find(c => c.id === selectedCourseIdForIndicator.value)
    const latest = await syllabusApi.getLatest(Number(selectedCourseIdForIndicator.value))
    planCatalog.value = course?.majorCode && latest?.planVersion
      ? await syllabusApi.getPlanIndicators(course.majorCode, latest.planVersion) : []
  } catch (e) {
    console.error('加载指标点失败', e)
    indicatorList.value = []
    planCatalog.value = []
  }
}

const openAddCourseModal = () => {
  courseErrorMessage.value = ''
  const defaultDept = managedMajors.value.length > 0 && managedMajors.value[0].department
    ? managedMajors.value[0].department
    : '软件工程教研室'
  const defaultMajor = managedMajors.value.length > 0 ? managedMajors.value[0].majorCode : 'SE'

  currentCourseForm.value = {
    courseCode: '',
    courseName: '',
    department: defaultDept,
    majorCode: defaultMajor,
    teacherName: '',
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
  courseErrorMessage.value = ''
  currentCourseForm.value = { ...c }
  if (currentCourseForm.value.theoryHours === undefined && currentCourseForm.value.practiceHours === undefined && currentCourseForm.value.hours) {
    currentCourseForm.value.theoryHours = currentCourseForm.value.hours
    currentCourseForm.value.practiceHours = 0
  }
  showCourseModal.value = true
}

const handleSaveCourse = async () => {
  courseErrorMessage.value = ''
  const form = currentCourseForm.value
  if (!form.courseCode || !form.courseCode.trim()) {
    courseErrorMessage.value = '课程编码不能为空'
    return
  }
  if (!form.courseName || !form.courseName.trim()) {
    courseErrorMessage.value = '课程名称不能为空'
    return
  }
  if (!form.majorCode || !form.majorCode.trim()) {
    courseErrorMessage.value = '所属专业不能为空'
    return
  }
  if (!form.department || !form.department.trim()) {
    courseErrorMessage.value = '教研室不能为空'
    return
  }
  if (!form.credits || form.credits <= 0) {
    courseErrorMessage.value = '学分必须大于 0'
    return
  }
  if (!form.hours || form.hours <= 0) {
    courseErrorMessage.value = '总学时必须大于 0'
    return
  }
  const total = Number(form.hours)
  const missingTheory = form.theoryHours == null || form.theoryHours === ''
  const missingPractice = form.practiceHours == null || form.practiceHours === ''
  const th = missingTheory ? (missingPractice ? total : total - Number(form.practiceHours)) : Number(form.theoryHours)
  const ph = missingPractice ? total - th : Number(form.practiceHours)
  if (!Number.isInteger(th) || !Number.isInteger(ph) || th < 0 || ph < 0 || th + ph !== Number(form.hours)) {
    courseErrorMessage.value = `学时守恒校验失败：理论学时(${th}) + 实验学时(${ph}) 必须等于总学时(${form.hours})`
    return
  }

  try {
    await courseApi.save({ ...form, theoryHours: th, practiceHours: ph })
    showCourseModal.value = false
    loadCourses()
  } catch (e: any) {
    courseErrorMessage.value = e.response?.data?.message || e.message || '保存课程档案失败'
  }
}

const removeCourse = async (id: number) => {
  if (confirm('确认删除该课程档案？')) {
    await courseApi.delete(id)
    loadCourses()
  }
}

// 指标点新增、编辑与删除方法
const openAddIndicatorModal = () => {
  if (!selectedCourseIdForIndicator.value) {
    alert('请先选择一门课程！')
    return
  }
  indicatorForm.value = {
    id: null,
    indicatorCode: planCatalog.value[0]?.indicatorCode || '',
    requirementCategory: planCatalog.value[0]?.requirementCategory || '',
    indicatorDescription: '',
    supportWeight: 'H',
    targetGoal: '目标1'
  }
  showIndicatorModal.value = true
}

const selectPlanIndicator = () => {
  const found = planCatalog.value.find(i => i.indicatorCode === indicatorForm.value.indicatorCode)
  if (found) {
    indicatorForm.value.requirementCategory = found.requirementCategory
    indicatorForm.value.indicatorDescription = found.indicatorDescription
  }
}

const openEditIndicatorModal = (ind: GraduationIndicator) => {
  indicatorForm.value = {
    id: ind.id,
    indicatorCode: ind.indicatorCode,
    requirementCategory: ind.requirementCategory,
    indicatorDescription: ind.indicatorDescription,
    supportWeight: ind.supportWeight,
    targetGoal: ind.targetGoal
  }
  showIndicatorModal.value = true
}

const handleSaveIndicator = async () => {
  if (!indicatorForm.value.indicatorCode || !indicatorForm.value.indicatorDescription) {
    alert('请填写指标点编号与分解表述！')
    return
  }
  try {
    if (indicatorForm.value.id) {
      await syllabusApi.updateIndicator(indicatorForm.value.id, indicatorForm.value)
    } else {
      await syllabusApi.addIndicator(Number(selectedCourseIdForIndicator.value), indicatorForm.value)
    }
    showIndicatorModal.value = false
    await loadIndicatorsForSelectedCourse()
    alert('指标点已成功保存并实时写入 MySQL！')
  } catch (e: any) {
    alert(e.response?.data?.message || '指标点保存失败')
  }
}

const confirmDeleteIndicator = async (ind: GraduationIndicator) => {
  if (confirm(`确认删除指标点【${ind.indicatorCode}】？此操作将直接同步删除 MySQL 中的数据。`)) {
    try {
      await syllabusApi.deleteIndicator(ind.id)
      await loadIndicatorsForSelectedCourse()
      alert('指标点已成功从 MySQL 中删除！')
    } catch (e: any) {
      alert(e.response?.data?.message || '删除指标点失败')
    }
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
      await syllabusApi.lock(latest.id, '')
      alert(`《${latest.course?.courseName || '该课程'}》大纲版本审查完成并成功锁定！`)
    } else {
      alert('该课程暂未发布教学大纲，无法进行审查锁定')
    }
  } catch (e) {
    alert('审查锁定失败，请重试')
  }
}

const viewCourseDetail = (c: Course) => {
  alert(`【${c.courseName} (${c.courseCode})】\n主讲教师：${c.teacherName || '未指定'}\n\n教学目标：\n${c.objectives || '暂无'}\n\n考核方式：\n${c.assessmentMethod || '暂无'}`)
}

// ==================== 督导建档与专业授权 (US-07) ====================
const supervisorsList = ref<UserVO[]>([])
const managedMajors = ref<Major[]>([])
const showCreateSupervisorModal = ref(false)
const createSupervisorForm = ref({
  username: '',
  password: '',
  realName: '',
  department: '校教学质量督导团',
  selectedMajors: [] as string[]
})
const showEditMajorsModal = ref(false)
const editingSupervisor = ref<UserVO | null>(null)
const editSupervisorSelectedMajors = ref<string[]>([])
const supervisorErrorMessage = ref('')

const loadSupervisors = async () => {
  try {
    const list = await directorApi.getSupervisors()
    supervisorsList.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('加载督导专家列表失败', e)
  }
}

const loadManagedMajors = async () => {
  try {
    const list = await directorApi.getManagedMajors()
    managedMajors.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('加载主任管辖专业失败', e)
  }
}

const openCreateSupervisorModal = () => {
  createSupervisorForm.value = {
    username: '',
    password: '',
    realName: '',
    department: '校教学质量督导团',
    selectedMajors: managedMajors.value.length > 0 ? [managedMajors.value[0].majorCode] : []
  }
  supervisorErrorMessage.value = ''
  showCreateSupervisorModal.value = true
}

const handleCreateSupervisor = async () => {
  if (!createSupervisorForm.value.username || !createSupervisorForm.value.password || !createSupervisorForm.value.realName) {
    supervisorErrorMessage.value = '请填写登录账号、初始密码和专家真实姓名'
    return
  }
  try {
    supervisorErrorMessage.value = ''
    await directorApi.createSupervisor({
      username: createSupervisorForm.value.username.trim(),
      password: createSupervisorForm.value.password.trim(),
      realName: createSupervisorForm.value.realName.trim(),
      department: createSupervisorForm.value.department.trim(),
      authorizedMajors: createSupervisorForm.value.selectedMajors.join(';')
    })
    showCreateSupervisorModal.value = false
    await loadSupervisors()
    alert('督导专家账号建档及专业授权成功！')
  } catch (err: any) {
    supervisorErrorMessage.value = err.response?.data?.message || err.message || '创建督导专家失败'
  }
}

const openEditMajorsModal = (sup: UserVO) => {
  editingSupervisor.value = sup
  const existing = sup.authorizedMajors ? sup.authorizedMajors.split(';').map((s: string) => s.trim()).filter(Boolean) : []
  editSupervisorSelectedMajors.value = existing.filter(code => managedMajors.value.some(m => m.majorCode === code))
  supervisorErrorMessage.value = ''
  showEditMajorsModal.value = true
}

const handleUpdateSupervisorMajors = async () => {
  if (!editingSupervisor.value) return
  try {
    supervisorErrorMessage.value = ''
    await directorApi.updateSupervisorMajors(editingSupervisor.value.id, editSupervisorSelectedMajors.value.join(';'))
    showEditMajorsModal.value = false
    await loadSupervisors()
    alert('督导专业授权已更新并实时生效！')
  } catch (err: any) {
    supervisorErrorMessage.value = err.response?.data?.message || err.message || '更新专业授权失败'
  }
}

// ==================== 最小班次维护能力 (CourseOffering) ====================
onMounted(() => {
  loadCourses()
  loadSupervisors()
  loadManagedMajors()
  loadAllMajors()
  loadPendingEvaluations()
})
</script>
