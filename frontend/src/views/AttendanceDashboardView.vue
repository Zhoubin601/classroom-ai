<template>
  <div class="space-y-6">
    <!-- 顶部状态栏与课堂排课关联选择器 -->
    <div class="bg-white border border-slate-200/80 rounded-2xl p-5 shadow-card flex flex-wrap items-center justify-between gap-4">
      <div class="flex items-center gap-4">
        <div class="w-11 h-11 rounded-xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600">
          <Video class="w-5 h-5" />
        </div>
        <div>
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-slate-900 flex items-center gap-2.5">
              课堂智能考勤与态势监控大屏
              <span v-if="isMonitoring" class="inline-flex items-center gap-1.5 text-[11px] px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-medium">
                <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
                摄像头推断采集中
              </span>
              <span v-else class="inline-flex items-center gap-1.5 text-[11px] px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-600 border border-slate-200 font-medium">
                <span class="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
                待机就绪
              </span>
            </h1>
          </div>
          <p class="text-xs text-slate-500 mt-0.5 font-sans">
            推断引擎: InsightFace (ArcFace 512维) + MediaPipe (solvePnP) | 毫秒级 1:N 考勤人脸识别
          </p>
        </div>
      </div>

      <!-- 关联当前开课与班级 -->
      <div class="flex flex-wrap items-center gap-3">
        <div class="flex items-center gap-2 bg-slate-50 px-3 py-1.5 rounded-xl border border-slate-200 text-xs text-slate-600">
          <span class="font-semibold text-slate-700 whitespace-nowrap">当前授课班级：</span>
          <select 
            v-if="activeOfferingList.length > 0"
            v-model.number="selectedOfferingId" 
            @change="onOfferingChange" 
            class="bg-white border border-slate-300 text-slate-800 font-semibold focus:outline-none focus:border-indigo-500 cursor-pointer rounded-lg px-2.5 py-1 text-xs shadow-xs min-w-[280px] max-w-md"
          >
            <option 
              v-for="off in activeOfferingList" 
              :key="off.id" 
              :value="Number(off.id)"
              class="bg-white text-slate-800 py-1.5"
            >
              【{{ off.className }}】{{ off.course?.courseName || '课程' }} - {{ off.teacherName }} ({{ off.studentCount }}人)
            </option>
          </select>
          <select 
            v-else 
            disabled 
            class="bg-slate-100 border border-slate-200 text-slate-400 font-semibold rounded-lg px-2.5 py-1 text-xs shadow-xs min-w-[80px] cursor-not-allowed text-center"
          >
            <option selected value="">无</option>
          </select>
        </div>

        <!-- 授课时段徽章状态 -->
        <div
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs border font-medium"
          :class="currentOfferingSessionStatus.inSession ? 'bg-emerald-50 text-emerald-800 border-emerald-200' : 'bg-amber-50 text-amber-800 border-amber-200'"
        >
          <span class="w-2 h-2 rounded-full" :class="currentOfferingSessionStatus.inSession ? 'bg-emerald-500 animate-pulse' : 'bg-amber-500'"></span>
          <span>{{ currentOfferingSessionStatus.inSession ? '正在授课时段中' : '非当前授课时段' }}</span>
          <span class="text-[11px] text-slate-500 font-mono">({{ currentOfferingSessionStatus.periodText }})</span>
        </div>

        <!-- 班级花名册与选人入班按钮 -->
        <button
          @click="openClassStudentModal"
          :disabled="!selectedOfferingId"
          :class="[
            'px-3.5 py-1.5 rounded-xl text-xs font-medium border flex items-center gap-1.5 transition shadow-xs',
            selectedOfferingId
              ? 'bg-slate-100 text-slate-700 border-slate-200 hover:bg-slate-200 cursor-pointer'
              : 'bg-slate-50 text-slate-400 border-slate-200 cursor-not-allowed opacity-60'
          ]"
          :title="selectedOfferingId ? '查看班级花名册或从总库选入/移出学生' : '当前无正在授课班级'"
        >
          <Users class="w-3.5 h-3.5 text-slate-500" />
          班级成员选拔 ({{ overview.totalRegistered }}人)
        </button>

        <!-- 核心按钮：启动/停止摄像头智能考勤 -->
        <button
          @click="toggleMonitor"
          :disabled="!selectedOfferingId || isMonitorStarting"
          :class="[
            'px-4 py-2 rounded-xl text-xs font-medium flex items-center gap-2 transition shadow-xs',
            !selectedOfferingId
              ? 'bg-slate-100 text-slate-400 border border-slate-200 cursor-not-allowed opacity-60'
              : isMonitoring
              ? 'bg-rose-50 text-rose-700 border border-rose-200 hover:bg-rose-100 cursor-pointer'
              : 'bg-indigo-600 hover:bg-indigo-700 text-white cursor-pointer'
          ]"
          :title="!selectedOfferingId ? '当前无正在授课班级，无法开启课堂考勤' : ''"
        >
          <span v-if="isMonitorStarting" class="w-3.5 h-3.5 rounded-full border-2 border-current border-t-transparent animate-spin"></span>
          <Square v-else-if="isMonitoring" class="w-3.5 h-3.5" />
          <Camera v-else class="w-3.5 h-3.5" />
          {{ isMonitorStarting ? '正在拉起推断引擎...' : isMonitoring ? '停止摄像头监控' : '打开摄像头开启考勤' }}
        </button>

        <!-- 一键下课归档考勤结果 -->
        <button
          @click="finishAndArchiveAttendance"
          :disabled="!selectedOfferingId || (!currentSessionId && !isSimulating && !isMonitoring && overview.currentPresent === 0 && lastActiveMetrics.present === 0)"
          :class="[
            'px-3.5 py-2 rounded-xl text-xs font-medium shadow-xs transition flex items-center gap-1.5',
            selectedOfferingId && (currentSessionId || isSimulating || isMonitoring || overview.currentPresent > 0 || lastActiveMetrics.present > 0)
              ? 'bg-slate-900 hover:bg-slate-800 text-white cursor-pointer'
              : 'bg-slate-100 text-slate-400 border border-slate-200 cursor-not-allowed opacity-60'
          ]"
          title="将当前课堂考勤与抬头率数据永久归档入库并下课"
        >
          <Archive class="w-3.5 h-3.5" />
          结束考勤并归档下课
        </button>

        <!-- 备用按钮：模拟推流 -->
        <button
          @click="toggleSimulation"
          :disabled="!selectedOfferingId || isMonitoring"
          :class="[
            'px-3 py-1.5 rounded-xl text-xs font-medium transition border',
            !selectedOfferingId
              ? 'bg-slate-100 text-slate-400 border-slate-200 cursor-not-allowed opacity-60'
              : isSimulating
              ? 'bg-amber-50 text-amber-700 border-amber-200 cursor-pointer'
              : 'bg-slate-100 text-slate-600 border-slate-200 hover:bg-slate-200 cursor-pointer'
          ]"
          :title="!selectedOfferingId ? '当前无正在授课班级' : ''"
        >
          {{ isSimulating ? '暂停模拟' : '演示模拟流' }}
        </button>

        <!-- 查看已归档考勤历史明细 -->
        <button
          @click="openArchiveModal"
          :disabled="!selectedOfferingId"
          :class="[
            'px-3 py-1.5 rounded-xl text-xs font-medium border flex items-center gap-1.5 transition shadow-xs',
            selectedOfferingId
              ? 'bg-white hover:bg-slate-50 text-slate-700 border-slate-200 cursor-pointer'
              : 'bg-slate-50 text-slate-400 border-slate-200 cursor-not-allowed opacity-60'
          ]"
          title="查看本课程在 MySQL 中持久化的历次考勤归档记录"
        >
          <Clock class="w-3.5 h-3.5 text-indigo-600" />
          考勤归档记录 ({{ archivedSessions.filter(s => s.status === 'FINISHED').length }}次)
        </button>
      </div>
    </div>

    <!-- 1. 核心大盘指标卡片 (严格区隔本班出勤率与非本班旁听学生) -->
    <div class="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-4">
      <MetricCard
        title="实到人数 (本班)"
        :value="overview.currentPresent"
        unit="人"
        :subtitle="`出勤率 ${overview.attendanceRate}%`"
        variant="blue"
      />
      <MetricCard
        title="应到总数 (本班)"
        :value="overview.totalRegistered"
        unit="人"
        subtitle="当前排课班额"
        variant="cyan"
      />
      <MetricCard
        title="非本班听课"
        :value="overview.auditingCount ?? 0"
        unit="人"
        subtitle="旁听不计入班级出勤率"
        variant="purple"
      />
      <MetricCard
        title="实时抬头率"
        :value="overview.realtimeLookupRate"
        unit="%"
        :subtitle="overview.focusLevel"
        variant="green"
      />
      <MetricCard
        title="低头走神"
        :value="overview.lookdownCount"
        unit="人"
        subtitle="Pitch 姿态 < -10°"
        :variant="overview.lookdownCount > 0 ? 'danger' : 'green'"
      />
      <MetricCard
        title="未到/缺勤"
        :value="overview.currentAbsent"
        unit="人"
        subtitle="本班缺席学生"
        variant="warning"
      />
      <MetricCard
        title="专注度评级"
        :value="overview.focusLevel.split(' ')[0]"
        subtitle="基于时序加权评估"
        variant="cyan"
      />
    </div>

    <!-- 2. 画中画视频感知视窗 + ECharts 抬头率与注意力流动波形图 -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-5">
      <!-- 左侧：实时摄像头 AI 感知推断视窗 (占 5 列) -->
      <div class="lg:col-span-5 bg-white border border-slate-200/80 rounded-2xl p-4 flex flex-col justify-between h-[380px] relative overflow-hidden shadow-card">
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-2">
            <Video class="w-4 h-4 text-indigo-600" />
            <h3 class="text-xs font-semibold text-slate-900 tracking-wide">摄像头实时人脸考勤与姿态 HUD 视窗</h3>
          </div>
          <span v-if="isMonitoring" class="text-[10px] px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-200 font-mono font-medium">
            LIVE 1:N 识别
          </span>
          <span v-else class="text-[10px] px-2 py-0.5 rounded-md bg-slate-100 text-slate-600 border border-slate-200 font-medium">
            待命中
          </span>
        </div>

        <!-- 监控开启时的实时画面 (MJPEG 流) -->
        <div class="flex-1 w-full bg-slate-950 rounded-xl overflow-hidden relative flex items-center justify-center border border-slate-900 shadow-inner">
          <img
            v-if="isMonitoring"
            :src="videoFeedUrl"
            alt="AI 视觉感知推断流"
            class="w-full h-full object-cover rounded-xl"
            @error="onVideoFeedError"
          />

          <!-- 未开启摄像头时的待机面板 -->
          <div v-else class="flex flex-col items-center justify-center text-center p-6 space-y-3">
            <div class="w-12 h-12 rounded-xl bg-slate-900 border border-slate-800 flex items-center justify-center text-slate-400 shadow-inner">
              <Camera class="w-6 h-6 text-slate-400" />
            </div>
            <div>
              <p class="text-xs font-semibold text-slate-200">
                {{ selectedOfferingId ? '摄像头考勤感知视窗处于就绪状态' : (currentUser?.role === 'TEACHER' ? '您当前暂无处于授课时段的个人课程' : '当前暂无处于授课时段的班级') }}
              </p>
              <p class="text-[11px] text-slate-400 mt-1 max-w-xs">
                {{ selectedOfferingId ? '点击上方【打开摄像头开启考勤】唤起 InsightFace 识别引擎，现场画中画实时渲染人脸框与 3D 姿态角' : (currentUser?.role === 'TEACHER' ? '任课教师仅可在本人授课时段开展随堂考勤与态势监控。若未到授课时间，请前往任课教师工作台备课或查看课表排期。' : '教务排课时间校验生效中，系统仅限处于有效授课时段的课程方可开展随堂视觉考勤。') }}
              </p>
            </div>
          </div>
        </div>

        <!-- 底部推断参数条 -->
        <div class="flex items-center justify-between mt-2 pt-2 border-t border-slate-100 text-[10px] text-slate-500 font-mono">
          <span>分辨率: 1280x720</span>
          <span>FPS: {{ isMonitoring ? '24~30 FPS' : '0' }}</span>
          <span class="text-indigo-600 font-medium">ArcFace 向量比对阈值: 0.42</span>
        </div>
      </div>

      <!-- 右侧：抬头率与在座人数时序折线图 (占 7 列) -->
      <div class="lg:col-span-7 bg-white border border-slate-200/80 rounded-2xl p-4 flex flex-col justify-between h-[380px] shadow-card">
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-2">
            <TrendingUp class="w-4 h-4 text-indigo-600" />
            <h3 class="text-xs font-semibold text-slate-900 tracking-wide">课堂抬头率与出勤人数时序波形 (LookUp Trend)</h3>
          </div>
          <span class="text-[10px] text-slate-400">每 1.2 秒平滑流动更新</span>
        </div>
        <div class="flex-1 w-full min-h-[300px]">
          <FocusTrendChart :data="trendData" />
        </div>
      </div>
    </div>

    <!-- 3. 学生实时在座考勤与姿态网格卡片 -->
    <StudentStatusGrid :students="studentsStatus" />

    <!-- 弹窗：开课班级学生管理与从总库选人入班 -->
    <div v-if="showStudentModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm">
      <div class="bg-white border border-slate-200/80 rounded-2xl w-full max-w-3xl p-6 shadow-modal space-y-4 max-h-[85vh] flex flex-col">
        <!-- 弹窗头部 -->
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <div>
            <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
              <Users class="w-5 h-5 text-indigo-600" />
              班级学生花名册与选拔管理
              <span class="text-xs px-2.5 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200 font-mono">
                {{ offeringList.find(o => o.id === selectedOfferingId)?.className }}
              </span>
            </h3>
            <p class="text-xs text-slate-500 mt-0.5">
              从人脸底库中自由挑选学生加入本课程班级，或移出班级。数据实时与后端数据库严格同步。
            </p>
          </div>
          <button @click="showStudentModal = false" class="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition">
            <X class="w-5 h-5" />
          </button>
        </div>

        <!-- 标签切换 -->
        <div class="flex items-center gap-2 border-b border-slate-200 pb-2 text-xs">
          <button
            @click="activeModalTab = 'enrolled'"
            :class="['px-3 py-1.5 rounded-lg font-medium transition flex items-center gap-1.5', activeModalTab === 'enrolled' ? 'bg-indigo-50 text-indigo-700 border border-indigo-200' : 'text-slate-500 hover:text-slate-800']"
          >
            <span>本班已有学生名单 ({{ currentOfferingStudents.length }}人)</span>
          </button>
          <button
            @click="activeModalTab = 'add'"
            :class="['px-3 py-1.5 rounded-lg font-medium transition flex items-center gap-1.5', activeModalTab === 'add' ? 'bg-indigo-50 text-indigo-700 border border-indigo-200' : 'text-slate-500 hover:text-slate-800']"
          >
            <UserPlus class="w-3.5 h-3.5" />
            <span>从总档案库选入学生 (可选 {{ availableStudents.length }}人)</span>
          </button>
        </div>

        <!-- 内容区域 1: 当前班级学生列表 -->
        <div v-if="activeModalTab === 'enrolled'" class="flex-1 overflow-y-auto space-y-2 pr-1">
          <div v-if="currentOfferingStudents.length === 0" class="py-12 text-center text-slate-400 text-xs">
            当前班级暂无学生，请切换至【从总档案库选入学生】进行挑选
          </div>
          <div
            v-for="s in currentOfferingStudents"
            :key="s.studentId"
            class="flex items-center justify-between p-2.5 bg-slate-50 border border-slate-200/80 rounded-xl hover:border-slate-300 transition"
          >
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-xs overflow-hidden">
                <img v-if="s.avatarUrl" :src="s.avatarUrl" class="w-full h-full object-cover" />
                <span v-else>{{ s.name?.charAt(0) }}</span>
              </div>
              <div>
                <div class="flex items-center gap-2">
                  <span class="text-xs font-bold text-slate-900">{{ s.name }}</span>
                  <span class="text-[10px] text-slate-500">({{ s.gender || '未知' }})</span>
                </div>
                <span class="text-[11px] font-mono text-indigo-600">{{ s.studentId }}</span>
              </div>
            </div>

            <button
              @click="handleRemoveStudentFromClass(s.studentId, s.name)"
              class="px-2.5 py-1 text-xs text-rose-600 hover:text-rose-700 hover:bg-rose-50 rounded-lg transition font-medium"
            >
              移出班级
            </button>
          </div>
        </div>

        <!-- 内容区域 2: 从总库选入学生 -->
        <div v-if="activeModalTab === 'add'" class="flex-1 overflow-y-auto space-y-2 pr-1">
          <div v-if="availableStudents.length === 0" class="py-12 text-center text-slate-400 text-xs">
            总档案库中暂无其他待分配或属于其他班级的候选学生
          </div>
          <div
            v-for="s in availableStudents"
            :key="s.studentId"
            class="flex items-center justify-between p-2.5 bg-slate-50 border border-slate-200/80 rounded-xl hover:border-slate-300 transition"
          >
            <div class="flex items-center gap-3">
              <input
                type="checkbox"
                :value="s.studentId"
                v-model="selectedStudentsToAdd"
                class="rounded border-slate-300 text-indigo-600 focus:ring-0 cursor-pointer"
              />
              <div class="w-8 h-8 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-xs overflow-hidden">
                <img v-if="s.avatarUrl" :src="s.avatarUrl" class="w-full h-full object-cover" />
                <span v-else>{{ s.name?.charAt(0) }}</span>
              </div>
              <div>
                <div class="flex items-center gap-2">
                  <span class="text-xs font-bold text-slate-900">{{ s.name }}</span>
                  <span class="text-[10px] text-slate-500">({{ s.gender || '未知' }})</span>
                  <span class="text-[10px] px-1.5 py-0.2 rounded bg-slate-200 text-slate-600">
                    当前: {{ s.className || '未分配' }}
                  </span>
                </div>
                <span class="text-[11px] font-mono text-indigo-600">{{ s.studentId }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div class="flex items-center justify-between border-t border-slate-100 pt-3 text-xs">
          <span class="text-slate-500">
            班级当前人数：<b class="text-slate-900">{{ currentOfferingStudents.length }}</b> 人
          </span>
          <div class="flex items-center gap-2">
            <button
              v-if="activeModalTab === 'add'"
              @click="handleAddStudentsToClass"
              :disabled="selectedStudentsToAdd.length === 0"
              :class="['px-4 py-2 rounded-xl font-medium transition flex items-center gap-1.5 shadow-xs', selectedStudentsToAdd.length > 0 ? 'bg-indigo-600 hover:bg-indigo-700 text-white' : 'bg-slate-100 text-slate-400 cursor-not-allowed']"
            >
              <Check class="w-3.5 h-3.5" />
              确认选入选中的 {{ selectedStudentsToAdd.length }} 人
            </button>
            <button @click="showStudentModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium rounded-xl transition">
              完成 / 关闭
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗：历史考勤归档记录 (MySQL 持久化档案库) -->
    <div v-if="showArchiveModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm">
      <div class="bg-white border border-slate-200/80 rounded-2xl w-full max-w-6xl p-6 shadow-modal space-y-4 max-h-[88vh] flex flex-col">
        <!-- 弹窗头部 -->
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600">
              <Archive class="w-5 h-5" />
            </div>
            <div>
              <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
                课程考勤历史归档记录 (MySQL 档案库)
                <span class="text-xs px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-mono">
                  已归档 {{ archivedSessions.filter(s => s.status === 'FINISHED').length }} 次
                </span>
              </h3>
              <p class="text-xs text-slate-500 mt-0.5">
                班级：{{ offeringList.find(o => o.id === selectedOfferingId)?.className }} · 课程：{{ offeringList.find(o => o.id === selectedOfferingId)?.course?.courseName }} (主讲：{{ offeringList.find(o => o.id === selectedOfferingId)?.teacherName }})
              </p>
            </div>
          </div>
          <button @click="showArchiveModal = false" class="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition cursor-pointer">
            <X class="w-5 h-5" />
          </button>
        </div>

        <!-- 归档记录表格 -->
        <div class="flex-1 overflow-x-auto overflow-y-auto border border-slate-200/80 rounded-xl shadow-inner">
          <div v-if="isLoadingArchive" class="py-16 text-center text-slate-400 text-xs flex items-center justify-center gap-2">
            <span class="w-4 h-4 rounded-full border-2 border-indigo-600 border-t-transparent animate-spin"></span>
            正在查询数据库归档记录...
          </div>
          <div v-else-if="archivedSessions.length === 0" class="py-16 text-center text-slate-400 text-xs">
            当前课程班级暂无历史考勤归档记录，开展摄像头考勤或演示模拟流并点击【结束考勤并归档下课】后即可在此查验入库数据。
          </div>
          <table v-else class="w-full text-left text-xs text-slate-700 min-w-[960px] border-collapse">
            <thead class="bg-slate-50/90 text-slate-600 uppercase font-semibold border-b border-slate-200 sticky top-0 z-10 backdrop-blur-xs">
              <tr>
                <th class="py-3 px-3.5 text-center whitespace-nowrap">会话ID</th>
                <th class="py-3 px-3.5 text-center whitespace-nowrap">教学周次</th>
                <th class="py-3 px-3.5 text-left whitespace-nowrap">授课教室</th>
                <th class="py-3 px-3.5 text-left whitespace-nowrap">考勤起止时间</th>
                <th class="py-3 px-3.5 text-center whitespace-nowrap">应到人数</th>
                <th class="py-3 px-3.5 text-center whitespace-nowrap">实到人数</th>
                <th class="py-3 px-3.5 text-center whitespace-nowrap">出勤率</th>
                <th class="py-3 px-3.5 text-center whitespace-nowrap">平均抬头率</th>
                <th class="py-3 px-3.5 text-left whitespace-nowrap">考勤人员</th>
                <th class="py-3 px-3.5 text-center whitespace-nowrap">状态</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100 bg-white">
              <tr v-for="s in archivedSessions" :key="s.id" class="hover:bg-slate-50 transition">
                <td class="py-3 px-3.5 text-center font-mono font-bold text-indigo-600 whitespace-nowrap">#{{ s.id }}</td>
                <td class="py-3 px-3.5 text-center whitespace-nowrap font-medium text-slate-700">第 {{ s.weekNumber || 2 }} 周</td>
                <td class="py-3 px-3.5 text-left whitespace-nowrap text-slate-800 font-semibold">{{ s.classroom || getCurrentClassroom() }}</td>
                <td class="py-3 px-3.5 text-left whitespace-nowrap font-mono text-[11px] text-slate-600">
                  <div class="flex items-center gap-1.5 whitespace-nowrap">
                    <span>{{ s.startTime ? s.startTime.replace('T', ' ').substring(0, 16) : '--' }}</span>
                    <span class="text-slate-400">至</span>
                    <span>{{ s.endTime ? s.endTime.replace('T', ' ').substring(0, 16) : '进行中' }}</span>
                  </div>
                </td>
                <td class="py-3 px-3.5 text-center font-mono text-slate-600 whitespace-nowrap">{{ s.expectedCount }} 人</td>
                <td class="py-3 px-3.5 text-center font-mono font-bold text-slate-900 whitespace-nowrap">{{ s.actualCount }} 人</td>
                <td class="py-3 px-3.5 text-center whitespace-nowrap">
                  <span 
                    :class="[
                      'inline-flex items-center justify-center min-w-[54px] px-2 py-0.5 rounded-full text-[11px] font-bold font-mono',
                      s.attendanceRate >= 80 
                        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' 
                        : s.attendanceRate > 0 
                        ? 'bg-amber-50 text-amber-700 border border-amber-200'
                        : 'bg-rose-50 text-rose-700 border border-rose-200'
                    ]"
                  >
                    {{ s.attendanceRate }}%
                  </span>
                </td>
                <td class="py-3 px-3.5 text-center font-mono text-indigo-700 font-semibold text-xs whitespace-nowrap">{{ s.avgLookupRate }}%</td>
                <td class="py-3 px-3.5 text-left whitespace-nowrap">
                  <span 
                    :class="[
                      'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium border shadow-2xs whitespace-nowrap',
                      s.operatorRole === 'DIRECTOR'
                        ? 'bg-amber-50 text-amber-800 border-amber-200'
                        : s.operatorRole === 'SUPERVISOR'
                        ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                        : 'bg-indigo-50 text-indigo-800 border-indigo-200'
                    ]"
                  >
                    <ShieldCheck v-if="s.operatorRole === 'SUPERVISOR'" class="w-3.5 h-3.5 text-emerald-600 shrink-0" />
                    <Briefcase v-else-if="s.operatorRole === 'DIRECTOR'" class="w-3.5 h-3.5 text-amber-600 shrink-0" />
                    <UserCheck v-else class="w-3.5 h-3.5 text-indigo-600 shrink-0" />
                    <span>{{ formatOperatorDisplay(s) }}</span>
                  </span>
                </td>
                <td class="py-3 px-3.5 text-center whitespace-nowrap">
                  <span 
                    :class="[
                      'inline-flex items-center justify-center min-w-[64px] px-2.5 py-1 rounded-md text-xs font-medium whitespace-nowrap',
                      s.status === 'FINISHED' 
                        ? 'bg-slate-100 text-slate-700 border border-slate-200' 
                        : 'bg-emerald-50 text-emerald-700 border border-emerald-200 animate-pulse'
                    ]"
                  >
                    {{ s.status === 'FINISHED' ? '已归档' : '授课中' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 弹窗底部说明 -->
        <div class="pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
          <span class="text-[11px] text-slate-400">
            * 无论通过现场摄像头 AI 识别还是演示模拟流，每次下课归档均原子性写入 MySQL `attendance_session` 数据库，数据真实可信。
          </span>
          <button @click="showArchiveModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium rounded-xl transition cursor-pointer">
            关闭
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { Video, Users, Camera, Archive, Square, TrendingUp, X, UserPlus, Check, Clock, ShieldCheck, Briefcase, UserCheck } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import FocusTrendChart from '../components/FocusTrendChart.vue'
import StudentStatusGrid from '../components/StudentStatusGrid.vue'
import { visualApi, courseApi, attendanceApi, scheduleApi, authApi } from '../api'
import type { DashboardOverviewVO, FocusTrendPointVO, StudentRealtimeStatusVO, CourseOffering, CourseSchedule } from '../api/types'
import { isScheduleInSession } from '../utils/scheduleTime'
import { attendanceMetrics, lookupRatio } from '../api/metrics.mjs'

const props = defineProps<{
  initialOfferingId?: number | null
  loggedInUser?: any
}>()

const internalUser = ref<any>(props.loggedInUser || null)
const currentUser = computed(() => props.loggedInUser || internalUser.value)

const overview = ref<DashboardOverviewVO>({
  totalRegistered: 0,
  currentPresent: 0,
  currentAbsent: 0,
  attendanceRate: 0,
  realtimeLookupRate: 0,
  lookdownCount: 0,
  focusLevel: '待启动',
  auditingCount: 0,
  auditingStudentIds: [],
  lastUpdateTime: ''
})

const trendData = ref<FocusTrendPointVO[]>([])
const studentsStatus = ref<StudentRealtimeStatusVO[]>([])

const offeringList = ref<CourseOffering[]>([])
const scheduleList = ref<CourseSchedule[]>([])
const selectedOfferingId = ref<number | null>(null)
const currentSessionId = ref<number | null>(null)

const isMonitoring = ref(false)
const isMonitorStarting = ref(false)
const isSimulating = ref(false)
const videoFeedUrl = ref('/api/visual/video-feed')

let pollTimer: number | null = null
let simulationTimer: number | null = null

const showStudentModal = ref(false)
const currentOfferingStudents = ref<any[]>([])
const availableStudents = ref<any[]>([])
const selectedStudentsToAdd = ref<string[]>([])
const activeModalTab = ref<'enrolled' | 'add'>('enrolled')

// 考勤历史归档记录 (MySQL 持久化)
const archivedSessions = ref<any[]>([])
const showArchiveModal = ref(false)
const isLoadingArchive = ref(false)

// 锁存本次推流中检测到的最新有效考勤指标（防止下课停止推流时被清零冲刷）
const lastActiveMetrics = ref({
  present: 0,
  rate: 0,
  lookupRate: 0
})

// 获取考勤操作人身份传参（是谁考的勤：教学督导、教研室主任还是任课教师）
const getOperatorParams = () => {
  const user = currentUser.value
  const role = user?.role || 'TEACHER'
  let title = '任课教师'
  if (role === 'SUPERVISOR') {
    title = '教学督导'
  } else if (role === 'DIRECTOR') {
    title = '教研室主任'
  }
  const name = user?.realName || (role === 'TEACHER' ? (offeringList.value.find(o => o.id === selectedOfferingId.value)?.teacherName || '郭军') : '系统操作员')
  return {
    operatorName: name,
    operatorRole: role,
    operatorTitle: title
  }
}

// 格式化展示考勤人员身份
const formatOperatorDisplay = (s: any) => {
  if (s.operatorTitle && s.operatorName) {
    return `${s.operatorTitle} (${s.operatorName})`
  }
  if (s.operatorTitle) return s.operatorTitle
  if (s.operatorRole === 'SUPERVISOR') return `教学督导 (${s.operatorName || '督导专家'})`
  if (s.operatorRole === 'DIRECTOR') return `教研室主任 (${s.operatorName || '教研室主任'})`
  return `任课教师 (${s.operatorName || s.offering?.teacherName || '郭军'})`
}

const loadArchivedSessions = async () => {
  if (!selectedOfferingId.value) {
    archivedSessions.value = []
    return
  }
  try {
    isLoadingArchive.value = true
    const list = await attendanceApi.getByOffering(selectedOfferingId.value)
    archivedSessions.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('加载历史考勤归档失败', e)
  } finally {
    isLoadingArchive.value = false
  }
}

const openArchiveModal = async () => {
  if (!selectedOfferingId.value) return
  await loadArchivedSessions()
  showArchiveModal.value = true
}

// 获取当前授课班级对应的真实排课教室 (优先当前在授时段，次选基础排课教室，如文管 A447)
const getCurrentClassroom = () => {
  if (!selectedOfferingId.value) return '文管 A447'
  const schedules = scheduleList.value.filter(s => s.offering?.id === selectedOfferingId.value)
  const active = schedules.find(s => isScheduleInSession(s).inSession)
  return active?.classroom || (schedules.length > 0 ? schedules[0].classroom : '文管 A447')
}

// 校验某个开课班级当前是否处于排课授课时段中
const isOfferingInSession = (offeringId: number) => {
  const schedules = scheduleList.value.filter(s => s.offering?.id === offeringId)
  if (schedules.length === 0) return false
  return schedules.some(s => isScheduleInSession(s).inSession)
}

// 角色范围过滤：任课教师 (TEACHER) 只能看自己为主讲的课程；管理人员 (督导/主任) 可统筹视导
const allowedOfferingList = computed(() => {
  if (!currentUser.value) return offeringList.value
  const user = currentUser.value
  if (user.role === 'TEACHER') {
    const myName = user.realName?.trim()
    const myCode = user.teacherCode?.trim()
    return offeringList.value.filter(off => {
      if (myCode && off.teacherCode === myCode) return true
      if (myName && (off.teacherName === myName || off.teacherName?.includes(myName))) return true
      return false
    })
  } else if (user.role === 'SUPERVISOR' && user.authorizedMajors) {
    const majors = user.authorizedMajors.split(';').map((m: string) => m.trim()).filter(Boolean)
    if (majors.length > 0) {
      return offeringList.value.filter(off => {
        const major = off.majorCode || off.course?.majorCode
        return !major || majors.includes(major)
      })
    }
  }
  return offeringList.value
})

// 仅过滤出当前时段正在授课的班级列表（非授课时段或教师自身未开课均返回空数组）
const activeOfferingList = computed(() => {
  return allowedOfferingList.value.filter(off => isOfferingInSession(Number(off.id)))
})

// 计算当前所选班级当前的排课时段及授课状态
const currentOfferingSessionStatus = computed(() => {
  if (!selectedOfferingId.value || activeOfferingList.value.length === 0) {
    if (currentUser.value?.role === 'TEACHER') {
      return { inSession: false, periodText: '暂无在授课程', reason: '本人主讲课程当前未开课或处于非授课时段' }
    }
    return { inSession: false, periodText: '暂无在授课程', reason: '当前非授课时段或无排期' }
  }
  const schedules = scheduleList.value.filter(s => s.offering?.id === selectedOfferingId.value)
  if (schedules.length === 0) {
    return { inSession: false, periodText: '未排课', reason: '暂无排课' }
  }
  const active = schedules.find(s => isScheduleInSession(s).inSession)
  const weekNames = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']
  if (active) {
    return { inSession: true, periodText: `${weekNames[active.dayOfWeek] || '周' + active.dayOfWeek} 第${active.startPeriod}-${active.endPeriod}节` }
  }
  const first = schedules[0]
  const check = isScheduleInSession(first)
  return { inSession: false, periodText: check.periodRangeText, reason: check.reason }
})

const syncSelectedOffering = () => {
  if (activeOfferingList.value.length > 0) {
    if (props.initialOfferingId && activeOfferingList.value.some(o => o.id === Number(props.initialOfferingId))) {
      selectedOfferingId.value = Number(props.initialOfferingId)
    } else if (!selectedOfferingId.value || !activeOfferingList.value.some(o => o.id === selectedOfferingId.value)) {
      selectedOfferingId.value = Number(activeOfferingList.value[0].id)
    }
  } else {
    selectedOfferingId.value = null
  }
}

watch([activeOfferingList, () => props.initialOfferingId], () => {
  syncSelectedOffering()
  onOfferingChange()
})

const loadSchedules = async () => {
  try {
    const list = await scheduleApi.getAll()
    scheduleList.value = Array.isArray(list) ? list : []
    syncSelectedOffering()
  } catch (e) {
    console.error('加载排课列表失败', e)
  }
}

const loadOfferings = async () => {
  try {
    offeringList.value = await courseApi.getOfferings()
    syncSelectedOffering()
  } catch (e) {
    console.error('加载开课列表失败', e)
  }
}

const onOfferingChange = async () => {
  if (!selectedOfferingId.value) {
    if (isSimulating.value) toggleSimulation()
    if (isMonitoring.value) {
      await visualApi.stopMonitor()
      isMonitoring.value = false
    }
    currentSessionId.value = null
    overview.value = {
      totalRegistered: 0,
      currentPresent: 0,
      currentAbsent: 0,
      attendanceRate: 0,
      realtimeLookupRate: 0,
      lookdownCount: 0,
      focusLevel: '暂无课程',
      auditingCount: 0,
      auditingStudentIds: [],
      lastUpdateTime: ''
    }
    trendData.value = []
    studentsStatus.value = []
    return
  }

  if (isSimulating.value) toggleSimulation()
  if (isMonitoring.value) {
    if (confirm('正在进行当前课堂的摄像头智能监控，切换至其他班级将自动停止当前推断流，是否继续切换？')) {
      await visualApi.stopMonitor()
      isMonitoring.value = false
      if (currentSessionId.value) {
        await attendanceApi.finish({
          sessionId: currentSessionId.value,
          actualCount: overview.value.currentPresent,
          avgLookupRate: overview.value.realtimeLookupRate
        })
        currentSessionId.value = null
      }
    } else {
      return
    }
  }
  try {
    await visualApi.resetStream(selectedOfferingId.value)
  } catch {}
  try {
    const sessions = await attendanceApi.getByOffering(selectedOfferingId.value)
    archivedSessions.value = Array.isArray(sessions) ? sessions : []
    const active = sessions.find((s: any) => s.status === 'ACTIVE')
    currentSessionId.value = active ? active.id : null
  } catch (e) {
    archivedSessions.value = []
    currentSessionId.value = null
  }
  await fetchDashboardData()
}

const fetchDashboardData = async () => {
  if (!selectedOfferingId.value) {
    overview.value = {
      totalRegistered: 0,
      currentPresent: 0,
      currentAbsent: 0,
      attendanceRate: 0,
      realtimeLookupRate: 0,
      lookdownCount: 0,
      focusLevel: '暂无课程',
      auditingCount: 0,
      auditingStudentIds: [],
      lastUpdateTime: ''
    }
    trendData.value = []
    studentsStatus.value = []
    return
  }
  try {
    const [ov, tr, st] = await Promise.all([
      visualApi.getOverview(selectedOfferingId.value),
      visualApi.getTrend(),
      visualApi.getStudentsStatus(selectedOfferingId.value)
    ])
    if (ov) {
      overview.value = ov
      if (ov.currentPresent > 0 || ov.realtimeLookupRate > 0) {
        lastActiveMetrics.value = {
          present: ov.currentPresent,
          rate: ov.attendanceRate,
          lookupRate: ov.realtimeLookupRate
        }
      }
    }
    if (tr) trendData.value = tr
    if (st) studentsStatus.value = st
  } catch (e) {
    console.error('获取大屏数据失败', e)
  }
}

const openClassStudentModal = async () => {
  if (!selectedOfferingId.value) return
  await loadOfferingStudentData()
  showStudentModal.value = true
}

const loadOfferingStudentData = async () => {
  try {
    const res = await courseApi.getOfferingStudents(selectedOfferingId.value)
    currentOfferingStudents.value = res.enrolled || []
    availableStudents.value = res.available || []
    selectedStudentsToAdd.value = []
  } catch (e) {
    console.error('获取班级学生名册失败', e)
  }
}

const handleAddStudentsToClass = async () => {
  if (selectedStudentsToAdd.value.length === 0) {
    alert('请先勾选需要选入本班的学生！')
    return
  }
  try {
    await courseApi.addStudentsToOffering(selectedOfferingId.value, selectedStudentsToAdd.value)
    alert(`成功选入 ${selectedStudentsToAdd.value.length} 名学生进入本课程班级！`)
    await loadOfferings()
    await loadOfferingStudentData()
    await fetchDashboardData()
    activeModalTab.value = 'enrolled'
  } catch (e: any) {
    alert('选入失败: ' + (e.message || '未知错误'))
  }
}

const handleRemoveStudentFromClass = async (studentId: string, name: string) => {
  if (!confirm(`确认将学生【${name} (${studentId})】从当前课程班级移出？`)) return
  try {
    await courseApi.removeStudentFromOffering(selectedOfferingId.value, studentId)
    await loadOfferings()
    await loadOfferingStudentData()
    await fetchDashboardData()
  } catch (e: any) {
    alert('移出失败: ' + (e.message || '未知错误'))
  }
}

const toggleMonitor = async () => {
  if (!selectedOfferingId.value && !isMonitoring.value) return
  if (isMonitoring.value) {
    // 停止监控
    try {
      await visualApi.stopMonitor()
      isMonitoring.value = false
      if (selectedOfferingId.value) {
        await visualApi.resetStream(selectedOfferingId.value)
      }
      await fetchDashboardData()
    } catch (e) {
      console.error(e)
    }
  } else {
    if (!selectedOfferingId.value) return
    // 启动摄像头考勤
    isMonitorStarting.value = true
    try {
      if (isSimulating.value) toggleSimulation()
      // 1. 在后端创建/关联 AttendanceSession
      const session = await attendanceApi.start({
        offeringId: selectedOfferingId.value,
        weekNumber: 2,
        classroom: getCurrentClassroom(),
        ...getOperatorParams()
      })
      if (session) currentSessionId.value = session.id

      // 2. 调起摄像头视觉监控推断
      await visualApi.startMonitor()
      isMonitoring.value = true
      videoFeedUrl.value = `/api/visual/video-feed?t=${Date.now()}`
    } catch (e: any) {
      alert('启动摄像头监控失败: ' + (e.message || '请检查摄像头连接'))
    } finally {
      isMonitorStarting.value = false
    }
  }
}

const finishAndArchiveAttendance = async () => {
  if (!selectedOfferingId.value) {
    alert('请先选择当前授课班级')
    return
  }

  // 1. 锁存本次随堂考勤最终指标（优先取当前大屏，次优取活跃快照，防止下课清流时被重置为0）
  const finalPresent = overview.value.currentPresent > 0 
    ? overview.value.currentPresent 
    : lastActiveMetrics.value.present
  const finalLookup = overview.value.realtimeLookupRate > 0 
    ? overview.value.realtimeLookupRate 
    : lastActiveMetrics.value.lookupRate
  const finalRate = overview.value.attendanceRate > 0 
    ? overview.value.attendanceRate 
    : lastActiveMetrics.value.rate

  if (!confirm(`确认结束本次课堂考勤并归档下课？\n\n本次下课归档数据预核：\n• 实到人次：${finalPresent} 人\n• 本班出勤率：${finalRate}%\n• 平均抬头率：${finalLookup}%\n\n确认后数据将永久写入教务系统档案，大屏将清空复位等待下节课。`)) {
    return
  }

  try {
    // 2. 停止模拟推流定时器
    if (isSimulating.value) {
      isSimulating.value = false
      if (simulationTimer) {
        clearInterval(simulationTimer)
        simulationTimer = null
      }
    }
    // 3. 停止真实摄像头监控
    if (isMonitoring.value) {
      await visualApi.stopMonitor()
      isMonitoring.value = false
    }

    // 4. 获取或补建 Session ID 并正式提交归档
    let targetSessionId = currentSessionId.value
    if (!targetSessionId) {
      try {
        const session = await attendanceApi.start({
          offeringId: selectedOfferingId.value,
          weekNumber: 2,
          classroom: getCurrentClassroom(),
          ...getOperatorParams()
        })
        if (session) targetSessionId = session.id
      } catch (e) {
        console.error('补建考勤会话失败', e)
      }
    }

    if (targetSessionId) {
      await attendanceApi.finish({
        sessionId: targetSessionId,
        actualCount: finalPresent,
        avgLookupRate: finalLookup,
        ...getOperatorParams()
      })
      currentSessionId.value = null
    }

    // 5. 课堂下课归档后，随堂实时感知大屏复位清空等待下一堂课
    if (selectedOfferingId.value) {
      await visualApi.resetStream(selectedOfferingId.value)
    }
    await fetchDashboardData()
    lastActiveMetrics.value = { present: 0, rate: 0, lookupRate: 0 }

    // 6. 重新拉取历史归档列表
    await loadArchivedSessions()

    alert(`【课堂考勤归档成功】\n\n本次课堂已正式下课并永久归档至教务系统历史数据库：\n• 实到人数：${finalPresent} 人\n• 最终出勤率：${finalRate}%\n• 平均抬头率：${finalLookup}%\n\n大屏已自动复位待机；您可点击【考勤归档记录】随时查验历次入库明细！`)
  } catch (e: any) {
    alert('归档失败: ' + (e.message || '未知错误'))
  }
}

const toggleSimulation = async () => {
  if (!selectedOfferingId.value && !isSimulating.value) return
  isSimulating.value = !isSimulating.value
  if (isSimulating.value) {
    if (!selectedOfferingId.value) {
      isSimulating.value = false
      return
    }

    // 自动为演示模拟流创建/关联 AttendanceSession，确保模拟流拥有官方考勤会话 ID 且随时可归档
    if (!currentSessionId.value) {
      try {
        const session = await attendanceApi.start({
          offeringId: selectedOfferingId.value,
          weekNumber: 2,
          classroom: getCurrentClassroom(),
          ...getOperatorParams()
        })
        if (session) currentSessionId.value = session.id
      } catch (e) {
        console.warn('为模拟流创建考勤会话失败', e)
      }
    }

    // 1. 获取当前所选班级正式名单
    let classEnrolledIds: string[] = []
    try {
      const res = await courseApi.getOfferingStudents(selectedOfferingId.value)
      if (res.enrolled && res.enrolled.length > 0) {
        classEnrolledIds = res.enrolled.map((s: any) => s.studentId)
      }
    } catch (e) {
      console.warn('获取班级名单用于模拟流失败，使用默认学号备用', e)
    }

    if (classEnrolledIds.length === 0) {
      classEnrolledIds = ['20246001', '20246002', '20246003', '20246004']
    }

    // 挑选 4 名本班学生 + 1 名非本班旁听学生 (20249999)
    // 验证：本班学生计入出勤，非本班学生绝对不计入本班出勤率！
    const enrolledPresent = classEnrolledIds.slice(0, 4)
    const externalAuditingId = '20249999'
    const simulatedPresent = [...enrolledPresent, externalAuditingId]

    simulationTimer = window.setInterval(async () => {
      try {
        if (!selectedOfferingId.value) return
        const detected = simulatedPresent.length
        const lookup = Math.floor(detected * (0.8 + Math.random() * 0.15))
        const poses: Record<string, string> = {}
        simulatedPresent.forEach(id => {
          poses[id] = Math.random() > 0.25 ? 'UP' : 'DOWN'
        })

        const currOff = offeringList.value.find(o => o.id === selectedOfferingId.value)

        await visualApi.reportStream({
          sessionId: 'sim-' + Date.now(),
          offeringId: selectedOfferingId.value,
          courseName: currOff?.course?.courseName || '软件项目管理',
          className: currOff?.className || '软件工程2024级2班',
          detectedPersonCount: detected,
          lookupCount: lookup,
          lookdownCount: detected - lookup,
          lookupRate: lookupRatio(lookup, detected),
          presentStudentIds: simulatedPresent,
          studentPoses: poses
        })
      } catch (error) { console.error('模拟推流失败', error) }
    }, 1500)
  } else if (simulationTimer) {
    clearInterval(simulationTimer)
    simulationTimer = null
    // 仅暂停定时器，切勿在暂停时直接 wipe 清零大屏数据，以便用户查验当前帧或直接下课归档
  }
}

const onVideoFeedError = () => {
  // 视频流加载中重试
}

onMounted(async () => {
  if (!props.loggedInUser) {
    try {
      const me = await authApi.getMe()
      internalUser.value = me
    } catch (e) {
      console.warn('获取当前登录用户失败', e)
    }
  }
  await Promise.all([loadOfferings(), loadSchedules()])
  syncSelectedOffering()
  if (selectedOfferingId.value) {
    await loadArchivedSessions()
    try {
      const session = await attendanceApi.getCurrent()
      if (session && !props.initialOfferingId && isOfferingInSession(Number(session.offering?.id))) {
        if (activeOfferingList.value.some(o => o.id === Number(session.offering.id))) {
          currentSessionId.value = session.id
          selectedOfferingId.value = Number(session.offering.id)
        }
      }
      isMonitoring.value = await visualApi.getMonitorStatus()
    } catch (error) { console.error('恢复考勤会话失败', error) }
    
    // 若未开流，主动复位一次 Redis 幽灵残留
    if (!isMonitoring.value) {
      try {
        await visualApi.resetStream(selectedOfferingId.value)
      } catch {}
    }
  }

  await fetchDashboardData()
  pollTimer = window.setInterval(fetchDashboardData, 1200)
})

watch(() => props.loggedInUser, (newUser) => {
  if (newUser) {
    internalUser.value = newUser
    syncSelectedOffering()
    onOfferingChange()
  }
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (simulationTimer) clearInterval(simulationTimer)
  if (selectedOfferingId.value) {
    visualApi.resetStream(selectedOfferingId.value).catch(() => {})
  }
})
</script>
