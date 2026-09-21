<template>
  <div class="space-y-6">
    <OfferingHistoryPanel />
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
          <p class="text-xs text-slate-500 mt-1">负责{{ currentCourse?.courseName ? `《${currentCourse.courseName}》` : '课程' }}大纲目标发布、12项毕业要求指标点维护、课件教案上传与听课复盘</p>
        </div>
      </div>

      <!-- 快速切换任课教师视角 / 锁定当前认证教师 -->
      <div class="flex flex-wrap items-center gap-2">
        <div v-if="!props.loggedInUser || props.loggedInUser.role !== 'TEACHER'" class="flex items-center gap-2">
          <span class="text-xs text-slate-500">切换教师视角：</span>
          <select v-model="currentTeacher" @change="onTeacherChange" class="bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-800 shadow-subtle focus:outline-none focus:border-indigo-500 cursor-pointer">
            <option v-for="t in teacherList" :key="t.name" :value="t.name">
              {{ t.name }} 老师 ({{ t.courseName }})
            </option>
          </select>
        </div>
        <div v-else class="flex items-center gap-2 text-xs bg-slate-50 border border-slate-200 px-3 py-1.5 rounded-xl shadow-subtle">
          <span class="text-slate-500">已认证教师身份：</span>
          <span class="font-bold text-indigo-700 font-mono">{{ props.loggedInUser.realName }}</span>
          <span class="text-slate-400 text-[11px]">({{ props.loggedInUser.department || '软件工程教研室' }})</span>
        </div>

        <!-- 针对该教师负责的多门主讲课程提供快速切换 (US: 一个老师有多门课程分布在不同时间) -->
        <div v-if="myOfferings.length > 1" class="flex items-center gap-1.5 bg-indigo-50 border border-indigo-200 px-3 py-1.5 rounded-xl shadow-subtle text-xs">
          <span class="text-indigo-800 font-semibold flex items-center gap-1">
            <BookOpen class="w-3.5 h-3.5 text-indigo-600" /> 当前主讲课程：
          </span>
          <select v-model="selectedOfferingId" @change="onOfferingSelectChange" class="bg-white border border-indigo-300 rounded-lg px-2.5 py-1 text-xs text-indigo-900 font-bold shadow-xs focus:outline-none focus:border-indigo-500 cursor-pointer">
            <option v-for="off in myOfferings" :key="off.id" :value="off.id">
              《{{ off.course?.courseName }}》- {{ off.className }}
            </option>
          </select>
        </div>
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
            <button @click="showUploadModal = true" class="px-3.5 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition flex items-center gap-1 cursor-pointer">
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
              :class="['px-2.5 py-1 rounded-lg text-xs font-medium transition cursor-pointer', (selectedTag === tag || (!selectedTag && tag === '全部')) ? 'bg-indigo-600 text-white shadow-subtle' : 'bg-slate-100 text-slate-600 hover:bg-slate-200']"
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
                <div
                  class="w-9 h-9 rounded-lg border flex items-center justify-center flex-shrink-0"
                  :class="res.fileType === 'PPTX' ? 'bg-amber-50 border-amber-200 text-amber-600' : (res.fileType === 'DOCX' ? 'bg-blue-50 border-blue-200 text-blue-600' : 'bg-indigo-50 border-indigo-200 text-indigo-600')"
                >
                  <FileText class="w-4 h-4" />
                </div>
                <div>
                  <div class="flex items-center gap-2">
                    <span class="text-xs font-bold text-slate-900">{{ res.resourceName }}</span>
                    <span
                      class="text-[10px] px-1.5 py-0.5 rounded font-mono font-semibold"
                      :class="res.fileType === 'PPTX' ? 'bg-amber-50 text-amber-700 border border-amber-200' : (res.fileType === 'DOCX' ? 'bg-blue-50 text-blue-700 border border-blue-200' : 'bg-indigo-50 text-indigo-700 border border-indigo-200')"
                    >
                      {{ res.fileType || 'PPTX' }}
                    </span>
                    <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 border border-slate-200 font-mono">{{ res.version }}</span>
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
                <button @click="previewResource(res)" class="px-2.5 py-1 bg-slate-100 hover:bg-slate-200 text-indigo-700 border border-slate-200 rounded-lg text-xs font-medium transition flex items-center gap-1 cursor-pointer">
                  <Eye class="w-3 h-3" /> 免密预览
                </button>
                <button @click="confirmDeleteResource(res)" class="px-2.5 py-1 bg-rose-50 hover:bg-rose-100 text-rose-600 border border-rose-200 rounded-lg text-xs font-medium transition flex items-center gap-1 cursor-pointer" title="删除该课件挂载与文件">
                  <Trash2 class="w-3 h-3" /> 删除
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 在线课程简介、考核方式与教学目标发布 (US-02) -->
        <div class="minimal-card p-5 space-y-4 border-l-4 border-indigo-500">
          <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
            <div class="space-y-1">
              <div class="flex items-center gap-2">
                <h2 class="text-sm font-bold text-slate-900 flex items-center gap-1.5">
                  <BookOpen class="w-4 h-4 text-indigo-600" /> 课程简介、考核方式与教学目标 (US-02)
                </h2>
                <!-- 已发布状态徽章 -->
                <span
                  v-if="publishedRevision?.publishVersion"
                  class="text-[11px] px-2 py-0.5 rounded-full font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1"
                >
                  <ShieldCheck class="w-3 h-3 text-emerald-600" /> 已发布 v{{ publishedRevision.publishVersion }}
                </span>
                <span
                  v-else
                  class="text-[11px] px-2 py-0.5 rounded-full font-medium bg-slate-100 text-slate-600 border border-slate-200"
                >
                  未发布正式版本
                </span>
                <!-- 草稿并发锁版本徽章 -->
                <span class="text-[10px] px-1.5 py-0.5 rounded font-mono font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
                  并发锁 lock-v{{ contentForm.lockVersion }}
                </span>
              </div>
              <div class="text-[11px] text-slate-500 flex items-center gap-3">
                <span v-if="publishedRevision?.publisherName">发布人：{{ publishedRevision.publisherName }}</span>
                <span v-if="publishedRevision?.publishedAt">发布时间：{{ formatDateTime(publishedRevision.publishedAt) }}</span>
                <span v-if="draftRevision?.editorName">草稿最后保存人：{{ draftRevision.editorName }}</span>
                <span v-if="draftRevision?.updatedAt">草稿更新时间：{{ formatDateTime(draftRevision.updatedAt) }}</span>
              </div>
            </div>

            <!-- 操作按钮组 -->
            <div class="flex items-center gap-2">
              <button
                @click="handleSaveDraft"
                :disabled="isSavingDraft || isPublishingContent || !draftRevision || !!contentConflictMsg"
                class="px-3 py-1.5 bg-white hover:bg-slate-50 text-indigo-700 border border-indigo-200 rounded-lg text-xs font-semibold shadow-xs transition cursor-pointer disabled:opacity-50"
              >
                {{ isSavingDraft ? '正在暂存...' : '暂存草稿' }}
              </button>
              <button
                @click="handlePublishContent"
                :disabled="isSavingDraft || isPublishingContent || !draftRevision || !!contentConflictMsg"
                class="px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-xs font-semibold shadow-subtle transition cursor-pointer disabled:opacity-50 flex items-center gap-1"
              >
                <Sparkles class="w-3.5 h-3.5" /> {{ isPublishingContent ? '正在发布...' : '正式发布' }}
              </button>
            </div>
          </div>

          <!-- 409 并发冲突告警条 -->
          <div v-if="contentConflictMsg" class="p-3 bg-rose-50 border border-rose-200 rounded-xl flex items-center justify-between gap-3 text-xs text-rose-700">
            <div class="flex items-center gap-2">
              <AlertCircle class="w-4 h-4 text-rose-600 flex-shrink-0" />
              <span>{{ contentConflictMsg }}</span>
            </div>
            <button
              @click="loadCourseContent"
              class="px-2.5 py-1 bg-white hover:bg-rose-100 text-rose-700 border border-rose-300 rounded-lg text-xs font-medium transition cursor-pointer flex-shrink-0"
            >
              拉取最新草稿
            </button>
          </div>

          <!-- 1. 课程简介 -->
          <div>
            <label class="text-xs text-slate-700 font-semibold block mb-1">
              课程简介 <span class="text-rose-500 font-normal">*正式发布必填</span>
            </label>
            <textarea
              v-model="contentForm.description"
              :disabled="!draftRevision || isSavingDraft || isPublishingContent"
              rows="3"
              class="w-full bg-white border border-slate-200 rounded-xl p-3 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"
              placeholder="请输入课程背景、学科定位、主要授课内容概括..."
            ></textarea>
          </div>

          <!-- 2. 考核与成绩评定方式 -->
          <div>
            <label class="text-xs text-slate-700 font-semibold block mb-1">
              考核与成绩评定方式 <span class="text-rose-500 font-normal">*正式发布必填</span>
            </label>
            <input
              v-model="contentForm.assessmentMethod"
              :disabled="!draftRevision || isSavingDraft || isPublishingContent"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"
              placeholder="例如：平时作业与实验 30% + 课程答辩与大作业 30% + 期末闭卷考试 40%"
            />
          </div>

          <!-- 3. 教学目标说明 -->
          <div>
            <label class="text-xs text-slate-700 font-semibold block mb-1">
              教学目标说明 <span class="text-rose-500 font-normal">*正式发布必填 (支撑毕业要求指标点)</span>
            </label>
            <textarea
              v-model="contentForm.objectives"
              :disabled="!draftRevision || isSavingDraft || isPublishingContent"
              rows="3"
              class="w-full bg-white border border-slate-200 rounded-xl p-3 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle"
              placeholder="明确说明本门课程培养的知识目标、工程能力目标以及价值素质目标..."
            ></textarea>
          </div>

          <div class="text-[11px] text-slate-400 flex items-center justify-between pt-1 border-t border-slate-100">
            <span>规则：草稿可不完整；正式发布三项必填且仅限关联任课教师操作；旧窗口提交冲突时请拉取最新草稿。</span>
            <span class="font-mono text-indigo-500">草稿仅任课教师可见</span>
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

    <!-- 底部整宽卡片：工程教育认证 12 项毕业要求指标点矩阵 (由任课教师在教师工作台负责填报与维护) (US-05) -->
    <div class="minimal-card p-6 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-4">
        <div>
          <div class="flex items-center gap-2">
            <div class="w-8 h-8 rounded-lg bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600 shadow-xs">
              <Target class="w-4 h-4" />
            </div>
            <h2 class="text-base font-bold text-slate-900">
              工程教育专业认证 · 12项毕业要求指标点矩阵维护
            </h2>
            <span class="text-xs px-2.5 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200 font-medium">
              任课教师专责维护 · MySQL 实时持久化
            </span>
          </div>
          <p class="text-xs text-slate-500 mt-1">
            任课教师对主讲课程《{{ currentCourse?.courseName || '当前课程' }}》负责填报指标点分解、支撑权重 (H强/M中/L弱) 与课程目标对应关系，支持随时新增、编辑与删除
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-2.5">
          <!-- 快捷套用12项国标推荐模板 -->
          <button
            @click="applyDefaultIndicators"
            :disabled="loadingIndicators"
            class="px-3.5 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-xl text-xs font-semibold shadow-subtle transition flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
            title="快速为该课程导入工程教育专业认证 12 项标准指标点基线，导入后可随时修改"
          >
            <Sparkles class="w-3.5 h-3.5 text-indigo-600" /> 一键套用 12 项国标推荐模板
          </button>

          <!-- 新增指标点按钮 -->
          <button
            @click="openAddIndicatorModal"
            class="px-3.5 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition flex items-center gap-1.5 cursor-pointer"
          >
            <Plus class="w-3.5 h-3.5" /> 新增认证指标点
          </button>
        </div>
      </div>

      <!-- 指标点矩阵表格 -->
      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs text-slate-700">
          <thead class="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200">
            <tr>
              <th class="py-3 px-4">指标点编号</th>
              <th class="py-3 px-4">毕业要求大项 (12项认证标准)</th>
              <th class="py-3 px-4">指标点分解表述</th>
              <th class="py-3 px-4">支撑权重</th>
              <th class="py-3 px-4">对应课程目标</th>
              <th class="py-3 px-4 text-right">任课教师操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-if="indicators.length === 0">
              <td colspan="6" class="py-12 text-center text-slate-400">
                <div class="flex flex-col items-center justify-center gap-2">
                  <FileText class="w-6 h-6 text-slate-300" />
                  <span>该课程暂未录入毕业要求指标点，请点击右上角【一键套用 12 项国标推荐模板】或【新增认证指标点】录入</span>
                </div>
              </td>
            </tr>
            <tr v-for="ind in indicators" :key="ind.id" class="hover:bg-slate-50/80 transition-colors">
              <td class="py-3 px-4 font-mono font-semibold text-indigo-600">{{ ind.indicatorCode }}</td>
              <td class="py-3 px-4 text-slate-900 font-medium">{{ ind.requirementCategory }}</td>
              <td class="py-3 px-4 text-slate-600 leading-relaxed max-w-md">{{ ind.indicatorDescription }}</td>
              <td class="py-3 px-4">
                <span :class="[
                  'px-2.5 py-1 rounded-md text-[10px] font-bold font-mono',
                  ind.supportWeight === 'H' ? 'bg-rose-50 text-rose-700 border border-rose-200' :
                  (ind.supportWeight === 'M' ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'bg-slate-100 text-slate-600 border border-slate-200')
                ]">
                  {{ ind.supportWeight }} ({{ ind.supportWeight === 'H' ? '强支撑' : (ind.supportWeight === 'M' ? '中等' : '弱支撑') }})
                </span>
              </td>
              <td class="py-3 px-4 text-slate-500 font-mono">{{ ind.targetGoal || '目标1' }}</td>
              <td class="py-3 px-4 text-right space-x-2">
                <button @click="openEditIndicatorModal(ind)" class="text-indigo-600 hover:text-indigo-800 font-semibold cursor-pointer">修改</button>
                <button @click="confirmDeleteIndicator(ind)" class="text-rose-600 hover:text-rose-800 font-semibold cursor-pointer">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 弹窗：任课教师新增/编辑认证指标点 (写进 MySQL) -->
    <div v-if="showIndicatorModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-lg p-6 shadow-modal space-y-4">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
            <Target class="w-4 h-4 text-indigo-600" /> {{ indicatorForm.id ? '修改毕业要求指标点 (任课教师)' : '新增毕业要求指标点 (任课教师)' }}
          </h3>
          <span class="text-[11px] px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200 font-mono">
            直接持久化至 MySQL
          </span>
        </div>

        <div class="space-y-3 text-xs">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="text-slate-600 block mb-1">指标点编号 (如 1-1, 11-1, 12-1)</label>
              <input
                v-model="indicatorForm.indicatorCode"
                placeholder="如 12-1"
                class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-subtle font-mono"
              />
            </div>
            <div>
              <label class="text-slate-600 block mb-1">支撑权重 (H强/M中/L弱)</label>
              <select
                v-model="indicatorForm.supportWeight"
                class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle font-semibold cursor-pointer"
              >
                <option value="H">H (强支撑)</option>
                <option value="M">M (中等支撑)</option>
                <option value="L">L (弱支撑)</option>
              </select>
            </div>
          </div>

          <div>
            <label class="text-slate-600 block mb-1">毕业要求大项 (通用认证12项)</label>
            <select
              v-model="indicatorForm.requirementCategory"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle cursor-pointer"
            >
              <option v-for="cat in standardIndicatorCategories" :key="cat" :value="cat">
                {{ cat }}
              </option>
            </select>
          </div>

          <div>
            <label class="text-slate-600 block mb-1">对应课程目标 (如 目标1, 目标2, 目标3)</label>
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
          <button @click="handleSaveIndicator" class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition cursor-pointer">保存并更新 MySQL</button>
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
          <!-- 真实课件文件选择与拖拽区 -->
          <div>
            <label class="text-slate-700 font-semibold block mb-1">选择真实课件文件 (.pptx / .docx / .pdf)</label>
            <input
              type="file"
              ref="resourceFileInputRef"
              @change="onResourceFileSelected"
              accept=".pptx,.ppt,.docx,.doc,.pdf"
              class="hidden"
            />
            <div
              @click="triggerResourceFileInput"
              @dragover.prevent
              @drop.prevent="onResourceFileDrop"
              class="border-2 border-dashed rounded-xl p-4 text-center cursor-pointer transition"
              :class="selectedResourceFile ? 'border-indigo-500 bg-indigo-50/40' : 'border-slate-300 hover:border-indigo-400 bg-slate-50/70 hover:bg-slate-50'"
            >
              <div v-if="!selectedResourceFile" class="flex flex-col items-center justify-center gap-1.5 text-slate-500">
                <Upload class="w-6 h-6 text-indigo-600" />
                <span class="font-medium text-slate-700 text-xs">点击选择本地真实 PPTX / DOCX / PDF 文件</span>
                <span class="text-[10px] text-slate-400">支持拖拽文件至此处，单文件上限 100MB</span>
              </div>
              <div v-else class="flex items-center justify-between">
                <div class="flex items-center gap-2.5 text-left">
                  <div
                    class="w-8 h-8 rounded-lg flex items-center justify-center font-bold text-xs"
                    :class="uploadForm.fileType === 'PPTX' ? 'bg-amber-100 text-amber-700' : (uploadForm.fileType === 'DOCX' ? 'bg-blue-100 text-blue-700' : 'bg-indigo-100 text-indigo-700')"
                  >
                    {{ uploadForm.fileType }}
                  </div>
                  <div>
                    <div class="font-semibold text-slate-800 text-xs truncate max-w-[280px]">{{ selectedResourceFile.name }}</div>
                    <div class="text-[10px] text-slate-500">{{ uploadForm.fileSize }} · 真实文件就绪</div>
                  </div>
                </div>
                <button
                  type="button"
                  @click.stop="clearSelectedResourceFile"
                  class="text-rose-500 hover:text-rose-700 text-xs px-2 py-1 rounded hover:bg-rose-50 cursor-pointer"
                >
                  更换文件
                </button>
              </div>
            </div>
          </div>

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
                <option value="DOCX">DOCX 教案大纲</option>
                <option value="PDF">PDF 电子文档</option>
              </select>
            </div>
            <div>
              <label class="text-slate-600 block mb-1">环节标签 (US-08)</label>
              <select v-model="uploadForm.tag" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-subtle">
                <option value="理论">理论授课</option>
                <option value="实验">实验实训</option>
                <option value="研讨">研讨互动</option>
                <option value="教案大纲">教案大纲</option>
                <option value="讲义课件">讲义课件</option>
              </select>
            </div>
          </div>
          <div class="p-3 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-[11px] flex items-center gap-1.5">
            <AlertCircle class="w-4 h-4 text-amber-600 flex-shrink-0" />
            <span>单文件上限 100MB · 挂载后自动注入东北大学动态只读防盗水印</span>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2.5 pt-2">
          <button @click="showUploadModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-medium transition cursor-pointer">取消</button>
          <button
            @click="handleSaveResource"
            :disabled="isUploadingResource"
            class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-subtle transition cursor-pointer flex items-center gap-1.5"
          >
            <span v-if="isUploadingResource" class="inline-block w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
            {{ isUploadingResource ? '正在上传真实课件...' : '立即挂载' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗：免密在线预览与动态水印 (US-09 / US-11) -->
    <div v-if="previewModalVisible" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div class="bg-white border border-slate-200 rounded-2xl w-full max-w-3xl p-6 shadow-modal space-y-4">
        <div class="flex items-center justify-between border-b border-slate-200 pb-3">
          <div class="flex items-center gap-2.5">
            <div
              class="w-8 h-8 rounded-lg flex items-center justify-center font-bold text-xs"
              :class="currentPreviewRes?.fileType === 'PPTX' ? 'bg-amber-100 text-amber-700' : (currentPreviewRes?.fileType === 'DOCX' ? 'bg-blue-100 text-blue-700' : 'bg-indigo-100 text-indigo-700')"
            >
              {{ currentPreviewRes?.fileType || 'PPTX' }}
            </div>
            <div>
              <h3 class="text-sm font-bold text-slate-900">{{ currentPreviewRes?.resourceName }}</h3>
              <p class="text-[11px] text-slate-500">{{ currentPreviewRes?.chapter }} · {{ currentPreviewRes?.tag }}环节 · {{ currentPreviewRes?.fileSize }}</p>
            </div>
          </div>
          <button @click="previewModalVisible = false" class="text-slate-400 hover:text-slate-600 text-lg cursor-pointer">✕</button>
        </div>

        <!-- 只读预览窗口与动态水印 -->
        <div class="relative w-full min-h-80 bg-slate-50 border border-slate-200 rounded-xl p-6 flex flex-col justify-between overflow-hidden">
          <!-- 动态防扩散水印层 (US-11) -->
          <div class="absolute inset-0 flex flex-wrap items-center justify-center gap-14 opacity-20 pointer-events-none select-none text-xs text-indigo-500 font-mono rotate-[-22deg] z-10">
            <span v-for="n in 12" :key="n">{{ currentPreviewRes?.dynamicWatermark || '东北大学软件学院 · 爱教学只读凭证 · 郭军' }}</span>
          </div>

          <!-- 内容预览区 -->
          <div class="relative z-0 space-y-4">
            <!-- 头部文件信息卡 -->
            <div class="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex items-center justify-between">
              <div class="space-y-1">
                <div class="flex items-center gap-2">
                  <span class="text-xs font-bold text-slate-800">真实文件格式：</span>
                  <span
                    class="text-xs px-2 py-0.5 rounded font-mono font-semibold"
                    :class="currentPreviewRes?.fileType === 'PPTX' ? 'bg-amber-50 text-amber-700 border border-amber-200' : (currentPreviewRes?.fileType === 'DOCX' ? 'bg-blue-50 text-blue-700 border border-blue-200' : 'bg-indigo-50 text-indigo-700 border border-indigo-200')"
                  >
                    {{ currentPreviewRes?.fileType === 'PPTX' ? 'Microsoft PowerPoint 演示文稿 (.pptx)' : (currentPreviewRes?.fileType === 'DOCX' ? 'Microsoft Word 教案大纲 (.docx)' : 'PDF 电子文档 (.pdf)') }}
                  </span>
                </div>
                <div class="text-[11px] text-slate-500 flex items-center gap-4">
                  <span>文件大小：<b class="text-slate-700">{{ currentPreviewRes?.fileSize }}</b></span>
                  <span>版本状态：<b class="text-indigo-600 font-mono">{{ currentPreviewRes?.version || 'v1.0' }}</b></span>
                  <span>主讲教师：<b class="text-slate-700">{{ currentPreviewRes?.uploaderTeacher || currentTeacher }}</b></span>
                </div>
              </div>
              <div class="text-right">
                <span class="text-[11px] px-2.5 py-1 rounded-lg bg-emerald-50 text-emerald-700 border border-emerald-200 font-medium inline-flex items-center gap-1">
                  <ShieldCheck class="w-3.5 h-3.5 text-emerald-600" /> 只读水印已激活
                </span>
              </div>
            </div>

            <!-- PPT / DOCX / PDF 结构化内容预览大纲 -->
            <div v-if="currentPreviewRes?.fileType === 'PPTX'" class="bg-white p-4 rounded-xl border border-slate-200 shadow-xs space-y-3">
              <div class="text-xs font-bold text-slate-800 flex items-center gap-1.5 border-b border-slate-100 pb-2">
                <Folder class="w-3.5 h-3.5 text-amber-600" /> PPT 幻灯片大纲与授课核心要点
              </div>
              <div class="grid grid-cols-2 gap-2 text-xs">
                <div class="p-2.5 bg-slate-50 rounded-lg border border-slate-100">
                  <div class="font-semibold text-slate-700 mb-1">幻灯片 1：封面与课程导引</div>
                  <div class="text-[11px] text-slate-500">{{ currentPreviewRes?.resourceName }} · 软件项目管理</div>
                </div>
                <div class="p-2.5 bg-slate-50 rounded-lg border border-slate-100">
                  <div class="font-semibold text-slate-700 mb-1">幻灯片 2：教学目标与核心概念</div>
                  <div class="text-[11px] text-slate-500">支撑工程认证毕业要求指标点分解</div>
                </div>
                <div class="p-2.5 bg-slate-50 rounded-lg border border-slate-100">
                  <div class="font-semibold text-slate-700 mb-1">幻灯片 3：核心算法与工程范式</div>
                  <div class="text-[11px] text-slate-500">工期估算 / 关键路径CPM / 燃尽图追踪</div>
                </div>
                <div class="p-2.5 bg-slate-50 rounded-lg border border-slate-100">
                  <div class="font-semibold text-slate-700 mb-1">幻灯片 4：实战演练与总结</div>
                  <div class="text-[11px] text-slate-500">团队实操、代码评审与答辩准备</div>
                </div>
              </div>
            </div>

            <div v-else-if="currentPreviewRes?.fileType === 'DOCX'" class="bg-white p-4 rounded-xl border border-slate-200 shadow-xs space-y-2">
              <div class="text-xs font-bold text-slate-800 flex items-center gap-1.5 border-b border-slate-100 pb-2">
                <FileText class="w-3.5 h-3.5 text-blue-600" /> Word 教案大纲与考核矩阵
              </div>
              <p class="text-xs text-slate-600 leading-relaxed">
                《{{ currentPreviewRes?.resourceName }}》真实 Word 教案已由教师挂载，包含东北大学教学大纲统一规范、12 条工程认证指标点矩阵、周次教学计划及平时/答辩/期末考核评定细则。
              </p>
            </div>

            <div v-else class="bg-white p-4 rounded-xl border border-slate-200 shadow-xs space-y-2">
              <div class="text-xs font-bold text-slate-800 flex items-center gap-1.5 border-b border-slate-100 pb-2">
                <FileText class="w-3.5 h-3.5 text-indigo-600" /> PDF 电子教案概览
              </div>
              <p class="text-xs text-slate-600 leading-relaxed">
                标准只读 PDF 格式，支持免密在线审阅与双面打印，防篡改动态水印已就绪。
              </p>
            </div>
          </div>
        </div>

        <div class="flex items-center justify-between pt-2 border-t border-slate-100">
          <div class="text-[11px] text-slate-500">
            支持下载真实文件后使用本地 Microsoft Office / WPS 打开查验
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="downloadResourceFile(currentPreviewRes)"
              class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-subtle transition flex items-center gap-1.5 cursor-pointer"
            >
              <Download class="w-3.5 h-3.5" /> 下载课件原件 ({{ currentPreviewRes?.fileType || '文件' }})
            </button>
            <button @click="previewModalVisible = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold transition cursor-pointer">
              关闭预览
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import OfferingHistoryPanel from '../components/OfferingHistoryPanel.vue'
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
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
  AlertCircle,
  Sparkles,
  Trash2,
  Download
} from 'lucide-vue-next'
import * as echarts from 'echarts'
import { resourceApi, supervisionApi, courseApi, syllabusApi, scheduleApi, courseContentApi } from '../api'
import type { CourseResource, CourseOffering, CourseSchedule, TeacherQualityRadarVO, GraduationIndicator, CourseContentRevision } from '../api/types'

const props = defineProps<{
  loggedInUser?: any
}>()

// US-02 课程简介草稿与发布 (与工程认证大纲 US-05 解耦)
const draftRevision = ref<CourseContentRevision | null>(null)
const publishedRevision = ref<CourseContentRevision | null>(null)
const contentForm = ref({
  description: '',
  assessmentMethod: '',
  objectives: '',
  lockVersion: 0,
  publishVersion: undefined as number | undefined
})
const contentConflictMsg = ref('')
const isSavingDraft = ref(false)
const isPublishingContent = ref(false)

const formatDateTime = (val?: string) => {
  if (!val) return ''
  return val.replace('T', ' ').substring(0, 19)
}

const teacherList = ref<{ name: string; courseName: string }[]>([])
const currentTeacher = ref('郭军')
const myOfferings = ref<CourseOffering[]>([])
const selectedOfferingId = ref<number | null>(null)
const currentOffering = ref<CourseOffering | null>(null)
const currentCourse = ref<any>(null)
const currentSchedule = ref<CourseSchedule | null>(null)
const currentSyllabus = ref<any>(null)
const selectedTag = ref('')
const myResources = ref<CourseResource[]>([])
const radarData = ref<TeacherQualityRadarVO | null>(null)
const radarChartRef = ref<HTMLDivElement | null>(null)
let radarChart: echarts.ECharts | null = null

// 毕业要求指标点矩阵 (任课教师专责维护)
const indicators = ref<GraduationIndicator[]>([])
const loadingIndicators = ref(false)
const showIndicatorModal = ref(false)
const indicatorForm = ref({
  id: null as number | null,
  indicatorCode: '',
  requirementCategory: '1. 工程知识',
  indicatorDescription: '',
  supportWeight: 'H',
  targetGoal: '目标1'
})

const standardIndicatorCategories = [
  '1. 工程知识',
  '2. 问题分析',
  '3. 设计/开发解决方案',
  '4. 研究',
  '5. 使用现代工具',
  '6. 工程与社会',
  '7. 环境和可持续发展',
  '8. 职业规范',
  '9. 个人和团队',
  '10. 沟通',
  '11. 项目管理',
  '12. 终身学习'
]

const defaultIndicatorTemplates = [
  { indicatorCode: '1-1', requirementCategory: '1. 工程知识', indicatorDescription: '能够将数学、自然科学、工程基础和专业知识用于解决软件系统工程中的复杂工程问题', supportWeight: 'H', targetGoal: '目标1' },
  { indicatorCode: '2-1', requirementCategory: '2. 问题分析', indicatorDescription: '能够应用工程科学基本原理，识别、表达并通过文献研究分析软件工程复杂问题以获得有效结论', supportWeight: 'H', targetGoal: '目标1' },
  { indicatorCode: '3-1', requirementCategory: '3. 设计/开发解决方案', indicatorDescription: '能够设计针对复杂软件工程问题的解决方案，设计满足特定需求的系统模块并在设计环节中体现创新意识', supportWeight: 'H', targetGoal: '目标2' },
  { indicatorCode: '4-1', requirementCategory: '4. 研究', indicatorDescription: '能够基于科学原理并采用科学方法对软件工程复杂问题进行研究，包括实验设计、数据分析与解释', supportWeight: 'M', targetGoal: '目标2' },
  { indicatorCode: '5-1', requirementCategory: '5. 使用现代工具', indicatorDescription: '能够针对复杂软件工程问题，开发、选择与使用恰当的技术、资源、现代工程工具和信息技术工具', supportWeight: 'H', targetGoal: '目标3' },
  { indicatorCode: '6-1', requirementCategory: '6. 工程与社会', indicatorDescription: '能够基于软件工程相关背景知识进行合理分析，评价软件工程实践对社会、健康、安全、法律及文化的影响', supportWeight: 'L', targetGoal: '目标3' },
  { indicatorCode: '7-1', requirementCategory: '7. 环境和可持续发展', indicatorDescription: '能够理解和评价针对复杂软件工程问题的工程实践对环境、社会可持续发展的影响', supportWeight: 'L', targetGoal: '目标3' },
  { indicatorCode: '8-1', requirementCategory: '8. 职业规范', indicatorDescription: '具有人文社会科学素养、社会责任感，能够在软件工程实践中理解并遵守工程职业道德和规范', supportWeight: 'M', targetGoal: '目标4' },
  { indicatorCode: '9-1', requirementCategory: '9. 个人和团队', indicatorDescription: '能够在多学科背景下的软件研发团队中承担个体、团队成员以及负责人的角色', supportWeight: 'H', targetGoal: '目标4' },
  { indicatorCode: '10-1', requirementCategory: '10. 沟通', indicatorDescription: '能够就复杂软件工程问题与业界同行及社会公众进行有效沟通和交流，包括撰写报告和陈述发言', supportWeight: 'M', targetGoal: '目标4' },
  { indicatorCode: '11-1', requirementCategory: '11. 项目管理', indicatorDescription: '理解并掌握软件工程项目管理原理与经济决策方法，并能在多学科环境中应用', supportWeight: 'H', targetGoal: '目标1' },
  { indicatorCode: '12-1', requirementCategory: '12. 终身学习', indicatorDescription: '具有自主学习和终身学习的意识，有不断学习和适应软件工程与人工智能新技术发展的能力', supportWeight: 'H', targetGoal: '目标4' }
]

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
const selectedResourceFile = ref<File | null>(null)
const resourceFileInputRef = ref<HTMLInputElement | null>(null)
const isUploadingResource = ref(false)

const uploadForm = ref({
  courseId: 1,
  chapter: '第一章 课程概论',
  resourceName: '',
  fileType: 'PPTX',
  tag: '理论',
  fileUrl: '/uploads/resources/sample.pptx',
  fileSize: '15.6 MB',
  fileSizeBytes: 16357785,
  uploaderTeacher: '郭军'
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

    // 如果外部传入了已登录任课教师姓名，优先锁定该教师
    if (props.loggedInUser?.realName) {
      currentTeacher.value = props.loggedInUser.realName
    } else if (teacherList.value.length > 0 && !teacherList.value.some(t => t.name === currentTeacher.value)) {
      currentTeacher.value = teacherList.value[0].name
    }
  } catch (e) {
    console.error('加载教师列表失败', e)
  }
}

const onTeacherChange = async () => {
  selectedOfferingId.value = null
  await loadCourse()
  await Promise.all([loadMyResources(), loadRadar(), loadIndicators(), loadCourseContent()])
}

const onOfferingSelectChange = async () => {
  const off = myOfferings.value.find(o => o.id === selectedOfferingId.value)
  if (off) {
    currentOffering.value = off
    currentCourse.value = off.course ?? null
    draftRevision.value = null
    contentConflictMsg.value = ''
    uploadForm.value.chapter = currentCourse.value ? `第一章 ${currentCourse.value.courseName}概论` : '第一章 概论'
    try {
      const schedules = await scheduleApi.getAll(off.id)
      currentSchedule.value = schedules && schedules.length > 0 ? schedules[0] : null
    } catch (err) {
      console.warn('获取排课信息失败', err)
    }
    await Promise.all([loadMyResources(), loadRadar(), loadIndicators(), loadCourseContent()])
  }
}

const loadCourse = async () => {
  draftRevision.value = null
  contentConflictMsg.value = ''
  currentCourse.value = null
  currentOffering.value = null
  currentSchedule.value = null
  currentSyllabus.value = null
  syllabusForm.value = { objectives: '', assessmentMethod: '' }

  try {
    const offerings = await courseApi.getOfferings()
    myOfferings.value = offerings
    if (offerings.length > 0) {
      let off = offerings.find(o => o.id === selectedOfferingId.value)
      if (!off) {
        off = offerings[0]
        selectedOfferingId.value = off.id
      }
      currentOffering.value = off
      currentCourse.value = off.course ?? null
      uploadForm.value.chapter = currentCourse.value ? `第一章 ${currentCourse.value.courseName}概论` : '第一章 概论'

      // 加载真实排课教室与时段
      try {
        const schedules = await scheduleApi.getAll(off.id)
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

}

const loadIndicators = async () => {
  if (!currentCourse.value?.id) {
    indicators.value = []
    return
  }
  loadingIndicators.value = true
  try {
    indicators.value = await syllabusApi.getIndicators(currentCourse.value.id)
  } catch (e) {
    console.error('加载指标点失败', e)
  } finally {
    loadingIndicators.value = false
  }
}

const openAddIndicatorModal = () => {
  indicatorForm.value = {
    id: null,
    indicatorCode: '',
    requirementCategory: standardIndicatorCategories[0],
    indicatorDescription: '',
    supportWeight: 'H',
    targetGoal: '目标1'
  }
  showIndicatorModal.value = true
}

const openEditIndicatorModal = (ind: GraduationIndicator) => {
  indicatorForm.value = {
    id: ind.id,
    indicatorCode: ind.indicatorCode,
    requirementCategory: ind.requirementCategory,
    indicatorDescription: ind.indicatorDescription,
    supportWeight: ind.supportWeight,
    targetGoal: ind.targetGoal || '目标1'
  }
  showIndicatorModal.value = true
}

const handleSaveIndicator = async () => {
  if (!currentCourse.value?.id) {
    alert('当前未关联有效课程')
    return
  }
  if (!indicatorForm.value.indicatorCode.trim() || !indicatorForm.value.indicatorDescription.trim()) {
    alert('请填写指标点编号与分解表述')
    return
  }

  try {
    if (indicatorForm.value.id) {
      await syllabusApi.updateIndicator(indicatorForm.value.id, indicatorForm.value)
      alert('指标点修改成功并已更新至 MySQL！')
    } else {
      await syllabusApi.addIndicator(currentCourse.value.id, indicatorForm.value)
      alert('指标点新增成功并已写入 MySQL！')
    }
    showIndicatorModal.value = false
    await loadIndicators()
  } catch (err: any) {
    alert(err.response?.data?.message || err.message || '保存指标点失败')
  }
}

const confirmDeleteIndicator = async (ind: GraduationIndicator) => {
  if (!confirm(`确定要从 MySQL 中移除指标点 [${ind.indicatorCode}] 吗？`)) return
  try {
    await syllabusApi.deleteIndicator(ind.id)
    await loadIndicators()
    alert('指标点已成功从 MySQL 中移除！')
  } catch (err: any) {
    alert(err.response?.data?.message || err.message || '删除指标点失败')
  }
}

const applyDefaultIndicators = async () => {
  if (!currentCourse.value?.id) {
    alert('当前教师暂未关联开课记录')
    return
  }
  if (indicators.value.length > 0) {
    if (!confirm('当前课程已录有指标点，继续套用将追加 12 项国标推荐标准，是否继续？')) {
      return
    }
  }

  loadingIndicators.value = true
  try {
    for (const tpl of defaultIndicatorTemplates) {
      await syllabusApi.addIndicator(currentCourse.value.id, tpl)
    }
    await loadIndicators()
    alert(`已成功为课程《${currentCourse.value.courseName}》批量写入 12 项工程认证指标点至 MySQL！`)
  } catch (err: any) {
    alert(err.response?.data?.message || err.message || '导入标准模板失败')
  } finally {
    loadingIndicators.value = false
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

const triggerResourceFileInput = () => {
  resourceFileInputRef.value?.click()
}

const onResourceFileSelected = (e: Event) => {
  const target = e.target as HTMLInputElement
  if (target.files && target.files[0]) {
    setResourceFile(target.files[0])
  }
}

const onResourceFileDrop = (e: DragEvent) => {
  if (e.dataTransfer?.files && e.dataTransfer.files[0]) {
    setResourceFile(e.dataTransfer.files[0])
  }
}

const setResourceFile = (file: File) => {
  if (file.size > 100 * 1024 * 1024) {
    alert('单文件大小不能超过 100MB 限制！')
    return
  }
  selectedResourceFile.value = file
  uploadForm.value.resourceName = file.name
  uploadForm.value.fileSizeBytes = file.size
  if (file.size >= 1024 * 1024) {
    uploadForm.value.fileSize = `${(file.size / (1024 * 1024)).toFixed(1)} MB`
  } else {
    uploadForm.value.fileSize = `${Math.max(1, Math.round(file.size / 1024))} KB`
  }
  const ext = file.name.split('.').pop()?.toUpperCase() || ''
  if (ext === 'PPT' || ext === 'PPTX') {
    uploadForm.value.fileType = 'PPTX'
    uploadForm.value.tag = '讲义课件'
  } else if (ext === 'DOC' || ext === 'DOCX') {
    uploadForm.value.fileType = 'DOCX'
    uploadForm.value.tag = '教案大纲'
  } else if (ext === 'PDF') {
    uploadForm.value.fileType = 'PDF'
    uploadForm.value.tag = '教案大纲'
  }
}

const clearSelectedResourceFile = () => {
  selectedResourceFile.value = null
  if (resourceFileInputRef.value) {
    resourceFileInputRef.value.value = ''
  }
}

const confirmDeleteResource = async (res: CourseResource) => {
  if (!confirm(`确定要从平台彻底删除课件资源《${res.resourceName}》吗？删除后将无法恢复。`)) return
  try {
    await resourceApi.delete(res.id)
    alert(`课件《${res.resourceName}》已成功删除！`)
    await loadMyResources()
  } catch (err: any) {
    alert(err.response?.data?.message || err.message || '删除课件失败')
  }
}

const downloadResourceFile = (res: CourseResource | null) => {
  if (!res || !res.fileUrl) {
    alert('暂无该课件文件下载地址')
    return
  }
  const link = document.createElement('a')
  link.href = res.fileUrl
  link.download = res.resourceName || '课件'
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const previewResource = (res: CourseResource) => {
  currentPreviewRes.value = res
  previewModalVisible.value = true
}

const handleSaveResource = async () => {
  try {
    if (!currentCourse.value) throw new Error('该教师暂无开课记录')
    if (!uploadForm.value.resourceName.trim()) {
      alert('请填写课件名称或选择本地课件文件')
      return
    }

    isUploadingResource.value = true

    // 如果选取了本地真实文件，先上传至后端 /api/v1/resources/upload
    if (selectedResourceFile.value) {
      const uploaded = await resourceApi.uploadFile(selectedResourceFile.value)
      uploadForm.value.fileUrl = uploaded.fileUrl
      uploadForm.value.fileSize = uploaded.fileSize
      uploadForm.value.fileSizeBytes = uploaded.fileSizeBytes
      uploadForm.value.fileType = uploaded.fileType
    }

    uploadForm.value.courseId = currentCourse.value.id
    uploadForm.value.uploaderTeacher = currentTeacher.value
    await resourceApi.save(uploadForm.value)
    showUploadModal.value = false
    clearSelectedResourceFile()
    await loadMyResources()
    alert(`课件《${uploadForm.value.resourceName}》真实挂载成功！已自动添加东北大学动态只读水印`)
  } catch (e: any) {
    alert(e.response?.data?.message || e.message || '上传挂载失败')
  } finally {
    isUploadingResource.value = false
  }
}

let contentLoadSequence = 0
const loadCourseContent = async () => {
  const requestSequence = ++contentLoadSequence
  const courseId = currentCourse.value?.id
  draftRevision.value = null
  publishedRevision.value = null
  contentConflictMsg.value = ''
  contentForm.value = { description: '', assessmentMethod: '', objectives: '', lockVersion: 0, publishVersion: undefined }
  if (!courseId) return
  try {
    const [draft, published] = await Promise.all([
      courseContentApi.getDraft(courseId), courseContentApi.getPublished(courseId)
    ])
    if (requestSequence !== contentLoadSequence || currentCourse.value?.id !== courseId) return
    draftRevision.value = draft
    publishedRevision.value = published
    contentForm.value = {
      description: draft.description || '', assessmentMethod: draft.assessmentMethod || '',
      objectives: draft.objectives || '', lockVersion: draft.lockVersion ?? 0,
      publishVersion: draft.publishVersion ?? 0
    }
  } catch (error: any) {
    if (requestSequence !== contentLoadSequence) return
    contentConflictMsg.value = error.response?.data?.message || error.message || '草稿加载失败，请重新拉取'
  }
}

const handleSaveDraft = async () => {
  if (!currentCourse.value?.id) {
    alert('请先选择有效课程')
    return
  }
  if (!draftRevision.value || draftRevision.value.courseId !== currentCourse.value.id || contentConflictMsg.value) return
  const courseId = currentCourse.value.id
  isSavingDraft.value = true
  contentConflictMsg.value = ''
  try {
    const updated = await courseContentApi.saveDraft(courseId, {
      description: contentForm.value.description,
      assessmentMethod: contentForm.value.assessmentMethod,
      objectives: contentForm.value.objectives,
      lockVersion: contentForm.value.lockVersion,
      draftId: draftRevision.value!.id,
      publishVersion: contentForm.value.publishVersion ?? 0
    })
    if (currentCourse.value?.id !== courseId) return
    draftRevision.value = updated
    contentForm.value.lockVersion = updated.lockVersion ?? updated.version ?? 0
    alert('草稿暂存成功！并发锁版本已同步为: ' + contentForm.value.lockVersion)
  } catch (err: any) {
    if (currentCourse.value?.id !== courseId) return
    if (err.response?.status === 409 || err.status === 409) {
      contentConflictMsg.value = err.response?.data?.message || err.message || '检测到并发修改冲突(版本不一致)，请重新拉取最新草稿'
    } else {
      alert(err.response?.data?.message || err.message || '草稿暂存失败')
    }
  } finally {
    isSavingDraft.value = false
  }
}

const handlePublishContent = async () => {
  if (!currentCourse.value?.id) {
    alert('请先选择有效课程')
    return
  }

  const desc = contentForm.value.description?.trim()
  const assess = contentForm.value.assessmentMethod?.trim()
  const objs = contentForm.value.objectives?.trim()

  if (!desc || !assess || !objs) {
    alert('正式发布失败：课程简介、考核方式和教学目标三项必须全部填写齐全！')
    return
  }

  if (!draftRevision.value || draftRevision.value.courseId !== currentCourse.value.id || contentConflictMsg.value) return
  const courseId = currentCourse.value.id
  isPublishingContent.value = true
  contentConflictMsg.value = ''
  try {
    const published = await courseContentApi.publish(courseId, {
      description: desc,
      assessmentMethod: assess,
      objectives: objs,
      lockVersion: contentForm.value.lockVersion,
      draftId: draftRevision.value!.id,
      publishVersion: contentForm.value.publishVersion ?? 0
    })
    if (currentCourse.value?.id !== courseId) return
    publishedRevision.value = published
    draftRevision.value = null
    currentCourse.value.description = published.description
    currentCourse.value.assessmentMethod = published.assessmentMethod
    currentCourse.value.objectives = published.objectives
    alert(`课程简介、考核方式与教学目标发布成功！正式版本: v${published.publishVersion || 1}`)
    await loadCourseContent()
  } catch (err: any) {
    if (currentCourse.value?.id !== courseId) return
    if (err.response?.status === 409 || err.status === 409) {
      contentConflictMsg.value = err.response?.data?.message || err.message || '检测到并发发布冲突(版本不一致)，请重新拉取最新草稿'
    } else if (err.response?.status === 403 || err.status === 403) {
      alert(err.response?.data?.message || '越权拦截：只有该课程关联任课教师才能发布')
    } else {
      alert(err.response?.data?.message || err.message || '发布失败')
    }
  } finally {
    isPublishingContent.value = false
  }
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

watch(() => props.loggedInUser, (newUser) => {
  if (newUser?.realName) {
    currentTeacher.value = newUser.realName
    onTeacherChange()
  }
})

onUnmounted(() => { radarChart?.dispose(); radarChart = null })
</script>
