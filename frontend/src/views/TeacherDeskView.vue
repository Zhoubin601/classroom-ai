<template>
  <div class="space-y-6" ref="viewRoot">


    <!-- 顶部任课教师身份卡 -->
    <div class="pro-card p-6 bg-gradient-to-r from-white via-slate-50/60 to-indigo-50/30 border border-slate-200/80 shadow-card-hover rounded-2xl flex flex-wrap items-center justify-between gap-5">
      <div class="flex items-center gap-4">
        <div class="w-13 h-13 rounded-2xl bg-gradient-to-br from-indigo-500 to-navy-700 flex items-center justify-center text-white shadow-md shadow-indigo-500/20 ring-4 ring-indigo-50">
          <BookOpen class="w-6 h-6" />
        </div>
        <div>
          <div class="flex items-center gap-3">
            <h1 class="text-xl font-bold tracking-tight text-slate-900">{{ currentTeacher }} 老师工作台</h1>
            <span class="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200/80 font-semibold shadow-xs">
              <span class="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
              {{ currentOffering?.className ? `${currentOffering.className} 主讲教师` : '专业任课教师' }}
            </span>
          </div>
          <p class="text-xs text-slate-500 mt-1.5 flex items-center gap-2">
            <span>负责{{ currentCourse?.courseName ? `《${currentCourse.courseName}》` : '课程' }}大纲目标发布、培养方案指标点维护、课件教案上传与听课复盘</span>
          </p>
        </div>
      </div>

      <!-- 快速切换任课教师视角 / 锁定当前认证教师 -->
      <div class="flex flex-wrap items-center gap-3">
        <div v-if="!props.loggedInUser || props.loggedInUser.role !== 'TEACHER'" class="flex items-center gap-2 bg-white/90 px-3.5 py-1.5 rounded-xl border border-slate-200/80 shadow-xs">
          <span class="text-xs text-slate-500 font-medium">切换教师视角：</span>
          <select :value="currentTeacher" @change="requestTeacherChange($event)" data-filter class="bg-transparent border-0 text-xs font-semibold text-slate-800 focus:outline-none focus:ring-0 cursor-pointer pr-4">
            <option v-for="t in teacherList" :key="t.name" :value="t.name">
              {{ t.name }} 老师 ({{ t.courseName }})
            </option>
          </select>
        </div>
        <div v-else class="flex items-center gap-2 text-xs bg-white/90 border border-slate-200/80 px-3.5 py-2 rounded-xl shadow-xs">
          <span class="text-slate-500">已认证教师身份：</span>
          <span class="font-bold text-indigo-700 font-mono tracking-tight">{{ props.loggedInUser.realName }}</span>
          <span class="text-slate-400 text-[11px]">({{ props.loggedInUser.department || '软件工程教研室' }})</span>
        </div>

        <!-- 针对该教师负责的多门主讲课程提供快速切换 (US: 一个老师有多门课程分布在不同时间) -->
        <div v-if="myOfferings.length > 1" class="flex items-center gap-2 bg-indigo-50/80 border border-indigo-200/80 px-3.5 py-1.5 rounded-xl shadow-xs text-xs">
          <span class="text-indigo-900 font-semibold flex items-center gap-1.5">
            <BookOpen class="w-3.5 h-3.5 text-indigo-600" /> 当前主讲课程：
          </span>
          <select :value="selectedOfferingId" @change="requestOfferingChange($event)" data-filter aria-label="当前主讲课程" class="bg-white border border-indigo-200 rounded-lg px-2.5 py-1 text-xs text-indigo-950 font-bold shadow-xs focus:outline-none focus:ring-2 focus:ring-indigo-500/20 cursor-pointer">
            <option v-for="off in myOfferings" :key="off.id" :value="off.id">
              《{{ off.course?.courseName }}》- {{ off.className }}
            </option>
          </select>
        </div>
      </div>
    </div>

    <!-- 顶部授课人次与学期宏观数据 -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
      <div class="pro-card p-5 group hover:border-indigo-300 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs text-slate-500 font-medium">当前主讲课程</span>
          <div class="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
            <BookOpen class="w-4 h-4" />
          </div>
        </div>
        <div class="text-base font-bold text-slate-900 mt-2 truncate">{{ currentCourse?.courseName ? `《${currentCourse.courseName}》` : '暂无主讲课程' }}</div>
        <span class="text-[11px] text-indigo-600 font-medium mt-1 block truncate">{{ scheduleDisplayText }}</span>
      </div>

      <div class="pro-card p-5 group hover:border-emerald-300 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs text-slate-500 font-medium">本学期授课班额</span>
          <div class="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
            <Sparkles class="w-4 h-4" />
          </div>
        </div>
        <div class="text-2xl font-bold text-emerald-600 font-mono mt-2 tracking-tight">{{ currentOffering ? `${currentOffering.studentCount} 人次` : '0 人次' }}</div>
        <span class="text-[11px] text-slate-500 mt-1 block truncate">{{ currentOffering?.className || '暂无班级' }}</span>
      </div>

      <div class="pro-card p-5 group hover:border-indigo-300 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs text-slate-500 font-medium">督导听课综合得分</span>
          <div class="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
            <Target class="w-4 h-4" />
          </div>
        </div>
        <div class="text-2xl font-bold text-indigo-600 font-mono mt-2 tracking-tight">{{ radarData?.overallScore != null ? radarData.overallScore + ' 分' : '暂无可比总分' }}</div>
        <span class="text-[11px] text-emerald-700 font-semibold mt-1 block">已公开评价 {{ radarData?.evaluationCount ?? 0 }} 次</span>
      </div>

      <div class="pro-card p-5 group hover:border-amber-300 transition-all">
        <div class="flex items-center justify-between">
          <span class="text-xs text-slate-500 font-medium">课件资源总挂载量</span>
          <div class="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
            <Folder class="w-4 h-4" />
          </div>
        </div>
        <div class="text-2xl font-bold text-amber-600 font-mono mt-2 tracking-tight">{{ myResources.length }} 份</div>
        <span class="text-[11px] text-slate-500 mt-1 block">含 PPTX/PDF 多版本</span>
      </div>
    </div>

    <FeatureSelector v-model="feature" :items="features" id="teacher" label="教师工作功能" />
    <section v-if="feature==='history'" role="tabpanel" id="teacher-panel-history" aria-labelledby="teacher-tab-history"><OfferingHistoryPanel /></section>
    <div v-if="latestContentSnapshot" class="ui-alert"><p>最新服务端草稿已取得，本地输入仍保留。</p><button class="ui-button" @click="adoptLatestContent">放弃本地修改并采用最新草稿</button></div>
    <!-- 主体：左右分栏 -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <!-- 左栏：教学资源多版本上传与大纲在线编辑 (占 7 列) -->
      <div v-show="feature==='materials'||feature==='shared'" class="lg:col-span-12 space-y-6" role="tabpanel" :id="`teacher-panel-${feature}`" :aria-labelledby="`teacher-tab-${feature}`">
        <!-- 课件教案上传管理 (US-07 / US-08 / US-11) -->
        <div v-show="feature==='materials'" class="pro-card p-6">
          <div class="flex flex-wrap items-center justify-between gap-3 mb-5 pb-4 border-b border-slate-100">
            <div>
              <h2 class="text-sm font-bold text-slate-900 flex items-center gap-2">
                <div class="w-7 h-7 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
                  <Folder class="w-4 h-4" />
                </div>
                教学资源按章挂载与版本演进
              </h2>
              <p class="text-xs text-slate-500 mt-1">单文件上限 100MB，支持 PDF/PPT/Word 批量按章挂载与只读水印</p>
            </div>
            <button @click="showUploadModal = true" class="px-4 py-2 bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-700 hover:to-indigo-800 text-white rounded-xl text-xs font-semibold shadow-xs hover:shadow-subtle transition flex items-center gap-1.5 cursor-pointer">
              <Plus class="w-3.5 h-3.5" /> 上传新课件/教案
            </button>
          </div>

          <!-- 环节标签过滤 -->
          <div class="flex items-center gap-2 mb-4">
            <span class="text-xs text-slate-500 font-medium">环节标签过滤：</span>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="tag in ['全部', '理论', '实验', '讨论', '研讨', '未标注']"
                :key="tag"
                @click="selectedTag = tag === '全部' ? '' : tag; loadMyResources()"
                :class="['px-3 py-1 rounded-lg text-xs font-medium transition cursor-pointer', (selectedTag === tag || (!selectedTag && tag === '全部')) ? 'bg-indigo-600 text-white shadow-xs font-semibold' : 'bg-slate-100 text-slate-600 hover:bg-slate-200/80']"
              >
                {{ tag }}
              </button>
            </div>
          </div>

          <!-- 资源列表 -->
          <div class="space-y-2.5" data-testid="my-resource-list">
            <div v-if="myResources.length === 0" class="py-10 text-center text-slate-400 text-xs bg-slate-50/60 border border-dashed border-slate-200 rounded-2xl">
              该环节暂无课件教案资源，请点击右上角上传挂载
            </div>
            <div v-for="res in myResources" :key="res.id" class="p-3.5 bg-white border border-slate-200/80 rounded-xl flex items-center justify-between hover:border-indigo-300 hover:shadow-sm transition-all">
              <div class="flex items-center gap-3.5 min-w-0">
                <div
                  class="w-10 h-10 rounded-xl border flex items-center justify-center flex-shrink-0 shadow-xs"
                  :class="res.fileType === 'PPTX' ? 'bg-amber-50 border-amber-200 text-amber-600' : (res.fileType === 'DOCX' ? 'bg-blue-50 border-blue-200 text-blue-600' : 'bg-indigo-50 border-indigo-200 text-indigo-600')"
                >
                  <FileText class="w-5 h-5" />
                </div>
                <div class="min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="text-xs font-bold text-slate-900 truncate max-w-[220px]">{{ res.resourceName }}</span>
                    <span
                      class="text-[10px] px-2 py-0.5 rounded font-mono font-bold"
                      :class="res.fileType === 'PPTX' ? 'bg-amber-50 text-amber-700 border border-amber-200' : (res.fileType === 'DOCX' ? 'bg-blue-50 text-blue-700 border border-blue-200' : 'bg-indigo-50 text-indigo-700 border border-indigo-200')"
                    >
                      {{ res.fileType || 'PPTX' }}
                    </span>
                    <span class="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 border border-slate-200 font-mono">{{ res.version }}</span>
                    <span class="text-[10px] px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200 font-medium">{{ res.tags?.join('、') || '未标注' }}</span>
                  </div>
                  <div class="text-[11px] text-slate-500 mt-1.5 flex items-center gap-3 flex-wrap">
                    <span>章节：{{ res.chapter }}</span>
                    <span>大小：{{ res.fileSize }}</span>
                    <span class="text-emerald-600 flex items-center gap-1 font-semibold">
                      <ShieldCheck class="w-3.5 h-3.5" /> 动态水印防护已就绪
                    </span>
                  </div>
                </div>
              </div>
              <div class="flex items-center gap-2 flex-shrink-0 ml-3">
                <button @click="openResourceEdit(res)" class="px-2.5 py-1.5 bg-white hover:bg-slate-50 text-slate-700 border border-slate-200 rounded-lg text-xs font-medium transition cursor-pointer shadow-2xs">编辑标签与共享</button>
                <button @click="previewResource(res)" class="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200/80 rounded-lg text-xs font-semibold transition flex items-center gap-1 cursor-pointer">
                  <Eye class="w-3.5 h-3.5" /> 授权预览
                </button>
                <button @click="confirmDeleteResource(res)" class="px-2.5 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-600 border border-rose-200 rounded-lg text-xs font-medium transition flex items-center gap-1 cursor-pointer" title="删除该课件挂载与文件">
                  <Trash2 class="w-3.5 h-3.5" /> 删除
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- US-10：跨课程、跨教师的教研室共享备课库 -->
        <div v-show="feature==='shared'" class="pro-card p-6" data-testid="shared-resource-library">
          <div class="flex flex-wrap items-end justify-between gap-3 mb-4 pb-3 border-b border-slate-100">
            <div>
              <h2 class="text-sm font-bold text-slate-900 flex items-center gap-2">
                <div class="w-7 h-7 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                  <BookOpen class="w-4 h-4" />
                </div>
                教研室共享备课库
              </h2>
              <p class="text-xs text-slate-500 mt-1">检索本教研室教师允许共享的资源；预览仍需服务端授权。</p>
            </div>
            <div class="flex items-center gap-2">
              <input v-model="sharedKeyword" @keyup.enter="loadSharedResources" aria-label="共享资源关键词" placeholder="搜索课程、章节或资源名" class="w-52 bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 shadow-2xs" />
              <button @click="loadSharedResources" class="px-3.5 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-xs transition cursor-pointer">检索共享资源</button>
            </div>
          </div>
          <p v-if="sharedError" role="alert" class="text-xs text-rose-600 mb-3 bg-rose-50 p-2.5 rounded-lg border border-rose-200">{{ sharedError }}</p>
          <p v-if="sharedLoading" class="text-xs text-slate-500 py-4 text-center">正在加载共享资源...</p>
          <p v-else-if="sharedResources.length === 0" class="text-xs text-slate-500 py-6 text-center bg-slate-50/60 rounded-xl border border-slate-100">暂无符合条件的共享资源</p>
          <div v-else class="space-y-2.5">
            <div v-for="res in sharedResources" :key="res.id" class="flex items-center justify-between gap-3 p-3.5 bg-white border border-slate-200/80 rounded-xl hover:border-slate-300 transition" :data-testid="`shared-resource-${res.id}`">
              <div class="min-w-0">
                <p class="text-xs font-bold text-slate-900 truncate">{{ res.resourceName }}</p>
                <p class="text-[11px] text-slate-500 mt-1">{{ res.course?.courseName }} · {{ res.chapter }} · 上传者：{{ res.uploaderTeacher || '未记录' }}</p>
                <p class="text-[11px] text-slate-500 mt-0.5">环节：{{ res.tags?.join('、') || '未标注' }}</p>
              </div>
              <button @click="previewResource(res)" class="shrink-0 px-3.5 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200/80 rounded-xl text-xs font-semibold transition cursor-pointer">授权预览</button>
            </div>
          </div>
        </div>

        <!-- 在线课程简介、考核方式与教学目标发布 -->
        <div v-show="feature==='materials'" class="pro-card p-6 space-y-4 border-l-4 border-l-indigo-600">
          <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-4">
            <div class="space-y-1.5">
              <div class="flex items-center gap-2 flex-wrap">
                <h2 class="text-sm font-bold text-slate-900 flex items-center gap-1.5">
                  <BookOpen class="w-4 h-4 text-indigo-600" /> 课程简介、考核方式与教学目标
                </h2>
                <!-- 已发布状态徽章 -->
                <span
                  v-if="publishedRevision?.publishVersion"
                  class="text-[11px] px-2.5 py-0.5 rounded-full font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 flex items-center gap-1 shadow-2xs"
                >
                  <ShieldCheck class="w-3.5 h-3.5 text-emerald-600" /> 已发布 v{{ publishedRevision.publishVersion }}
                </span>
                <span
                  v-else
                  class="text-[11px] px-2.5 py-0.5 rounded-full font-medium bg-slate-100 text-slate-600 border border-slate-200"
                >
                  未发布正式版本
                </span>
                <!-- 草稿并发锁版本徽章 -->
                <span class="text-[10px] px-2 py-0.5 rounded-full font-mono font-bold bg-indigo-50 text-indigo-700 border border-indigo-200">
                  草稿可编辑
                </span>
              </div>
              <div class="text-[11px] text-slate-500 flex items-center gap-3 flex-wrap">
                <span v-if="publishedRevision?.publisherName">发布人：{{ publishedRevision.publisherName }}</span>
                <span v-if="publishedRevision?.publishedAt">发布时间：{{ formatDateTime(publishedRevision.publishedAt) }}</span>
                <span v-if="draftRevision?.editorName">草稿最后保存人：{{ draftRevision.editorName }}</span>
                <span v-if="draftRevision?.updatedAt">草稿更新时间：{{ formatDateTime(draftRevision.updatedAt) }}</span>
              </div>
            </div>

            <!-- 操作按钮组 -->
            <div class="flex items-center gap-2.5">
              <button
                @click="handleSaveDraft"
                :disabled="isSavingDraft || isPublishingContent || !draftRevision || hasVersionConflict"
                class="px-3.5 py-1.5 bg-white hover:bg-slate-50 text-indigo-700 border border-indigo-200 rounded-xl text-xs font-semibold shadow-2xs transition cursor-pointer disabled:opacity-50"
              >
                {{ isSavingDraft ? '正在暂存...' : '暂存草稿' }}
              </button>
              <button
                @click="handlePublishContent"
                :disabled="isSavingDraft || isPublishingContent || !draftRevision || hasVersionConflict"
                class="px-4 py-1.5 bg-gradient-to-r from-emerald-600 to-emerald-700 hover:from-emerald-700 hover:to-emerald-800 text-white rounded-xl text-xs font-semibold shadow-xs transition cursor-pointer disabled:opacity-50 flex items-center gap-1.5"
              >
                <Sparkles class="w-3.5 h-3.5" /> {{ isPublishingContent ? '正在发布...' : '正式发布' }}
              </button>
            </div>
          </div>

          <!-- 409 并发冲突告警条 -->
          <div v-if="contentConflictMsg" class="p-3.5 bg-rose-50 border border-rose-200 rounded-xl flex items-center justify-between gap-3 text-xs text-rose-700">
            <div class="flex items-center gap-2">
              <AlertCircle class="w-4 h-4 text-rose-600 flex-shrink-0" />
              <span class="font-medium">{{ contentConflictMsg }}</span>
            </div>
            <button
              @click="refreshCourseContent"
              class="px-3 py-1 bg-white hover:bg-rose-100 text-rose-700 border border-rose-300 rounded-lg text-xs font-semibold transition cursor-pointer flex-shrink-0"
            >
              拉取最新草稿
            </button>
          </div>

          <!-- 1. 课程简介 -->
          <div>
            <label class="text-xs text-slate-700 font-bold block mb-1.5">
              课程简介 <span class="text-rose-500 font-normal">*正式发布必填</span>
            </label>
            <textarea
              data-edit-managed v-model="contentForm.description"
              :disabled="!draftRevision || isPublishingContent"
              rows="3"
              class="w-full bg-white border border-slate-200 rounded-xl p-3 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 shadow-2xs transition"
              placeholder="请输入课程背景、学科定位、主要授课内容概括..."
            ></textarea>
          </div>

          <!-- 2. 考核与成绩评定方式 -->
          <div>
            <label class="text-xs text-slate-700 font-bold block mb-1.5">
              考核与成绩评定方式 <span class="text-rose-500 font-normal">*正式发布必填</span>
            </label>
            <input
              data-edit-managed v-model="contentForm.assessmentMethod"
              :disabled="!draftRevision || isPublishingContent"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 shadow-2xs transition"
              placeholder="例如：平时作业与实验 30% + 课程答辩与大作业 30% + 期末闭卷考试 40%"
            />
          </div>

          <!-- 3. 教学目标说明 -->
          <div>
            <label class="text-xs text-slate-700 font-bold block mb-1.5">
              教学目标说明 <span class="text-rose-500 font-normal">*正式发布必填 (支撑毕业要求指标点)</span>
            </label>
            <textarea
              data-edit-managed v-model="contentForm.objectives"
              :disabled="!draftRevision || isPublishingContent"
              rows="3"
              class="w-full bg-white border border-slate-200 rounded-xl p-3 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 shadow-2xs transition"
              placeholder="明确说明本门课程培养的知识目标、工程能力目标以及价值素质目标..."
            ></textarea>
          </div>

          <div class="text-[11px] text-slate-400 flex items-center justify-between pt-2 border-t border-slate-100">
            <span>规则：草稿可不完整；正式发布三项必填且仅限关联任课教师操作；旧窗口提交冲突时请拉取最新草稿。</span>
            <span class="font-mono text-indigo-600 font-medium">草稿仅任课教师可见</span>
          </div>
        </div>
      </div>

      <!-- 右栏：教学质量自我复盘 4 维雷达图与评语词云 (占 5 列) (US-17 / US-21) -->
      <div v-if="feature==='feedback'" class="lg:col-span-12 space-y-6" role="tabpanel" id="teacher-panel-feedback" aria-labelledby="teacher-tab-feedback">
        <div class="pro-card p-6">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-sm font-bold text-slate-900 flex items-center gap-2">
              <div class="w-7 h-7 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
                <Target class="w-4 h-4" />
              </div>
              督导随堂评价 4 维雷达图
            </h2>
            <span class="text-[11px] text-emerald-700 bg-emerald-50 px-2.5 py-0.5 rounded-full border border-emerald-200 font-semibold shadow-2xs">BOPPPS 教学模型</span>
          </div>

          <!-- 雷达图容器 -->
          <p class="text-sm text-slate-600">归一化维度均分换算至25分刻度；不等同于总分。</p><p v-if="!radarData || [radarData.attitudeScore,radarData.contentScore,radarData.methodScore,radarData.effectScore].some(v=>v==null)" class="ui-empty">缺少有效维度，请查看可用维度数据；不补零。</p><div ref="radarChartRef" class="w-full h-64"></div>

          <!-- 4 维均分指标条 -->
          <div class="grid grid-cols-2 gap-2.5 text-xs mt-3 pt-3 border-t border-slate-100">
            <div class="bg-slate-50/80 p-3 rounded-xl border border-slate-200/80 hover:border-indigo-200 transition">
              <span class="text-slate-500 block text-[10px]">教学态度 (归一化25分刻度)</span>
              <span class="text-sm font-bold text-indigo-700 font-mono tracking-tight">{{ radarData?.attitudeScore ?? '暂无' }} 分</span>
            </div>
            <div class="bg-slate-50/80 p-3 rounded-xl border border-slate-200/80 hover:border-indigo-200 transition">
              <span class="text-slate-500 block text-[10px]">教学内容 (归一化25分刻度)</span>
              <span class="text-sm font-bold text-indigo-700 font-mono tracking-tight">{{ radarData?.contentScore ?? '暂无' }} 分</span>
            </div>
            <div class="bg-slate-50/80 p-3 rounded-xl border border-slate-200/80 hover:border-indigo-200 transition">
              <span class="text-slate-500 block text-[10px]">教学方法 (归一化25分刻度)</span>
              <span class="text-sm font-bold text-indigo-700 font-mono tracking-tight">{{ radarData?.methodScore ?? '暂无' }} 分</span>
            </div>
            <div class="bg-slate-50/80 p-3 rounded-xl border border-slate-200/80 hover:border-indigo-200 transition">
              <span class="text-slate-500 block text-[10px]">教学效果 (归一化25分刻度)</span>
              <span class="text-sm font-bold text-indigo-700 font-mono tracking-tight">{{ radarData?.effectScore ?? '暂无' }} 分</span>
            </div>
          </div>
        </div>

        <!-- 督导脱敏评价与 BOPPPS 改进建议推送 (US-14 / US-21) -->
        <div class="pro-card p-6 space-y-4">
          <div>
            <h2 class="text-sm font-bold text-slate-900 flex items-center gap-2">
              <div class="w-7 h-7 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
                <Award class="w-4 h-4" />
              </div>
              督导质性反馈与持续改进建议
            </h2>
            <p class="text-[11px] text-slate-500 mt-1">已执行 24 小时脱敏归档，保障客观公正交流</p>
          </div>

          <!-- 教学亮点 -->
          <div class="p-4 bg-emerald-50/70 border border-emerald-200/80 rounded-xl">
            <span class="text-xs font-bold text-emerald-900 block mb-1.5 flex items-center gap-1.5">
              <Sparkles class="w-3.5 h-3.5 text-emerald-600" /> 课堂教学亮点
            </span>
            <ul class="text-xs text-slate-700 space-y-1.5 list-disc list-inside">
              <li v-for="(hl, i) in (radarData?.highlightList || [])" :key="i">{{ hl }}</li>
            </ul>
          </div>

          <!-- 改进建议 -->
          <div class="p-4 bg-indigo-50/70 border border-indigo-200/80 rounded-xl">
            <span class="text-xs font-bold text-indigo-900 block mb-1.5 flex items-center gap-1.5">
              <Target class="w-3.5 h-3.5 text-indigo-600" /> BOPPPS 进阶教改建议
            </span>
            <ul class="text-xs text-slate-700 space-y-1.5 list-disc list-inside">
              <li v-for="(sg, i) in (radarData?.suggestionList || [])" :key="i">{{ sg }}</li>
            </ul>
          </div>

          <!-- 评语词云标签 -->
          <div>
            <span class="text-xs text-slate-600 font-semibold block mb-2">督导高频评价热词 (词云统计)</span>
            <div class="flex flex-wrap gap-1.5">
              <span v-for="w in (radarData?.wordCloud || [])" :key="w.name" class="px-2.5 py-1 bg-slate-100/90 text-indigo-700 rounded-lg text-xs font-semibold border border-slate-200/80 shadow-2xs">
                # {{ w.name }} ({{ w.value }})
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部整宽卡片：培养方案毕业要求指标点映射 -->
    <div v-show="feature==='indicators'" role="tabpanel" id="teacher-panel-indicators" aria-labelledby="teacher-tab-indicators" class="pro-card p-6 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-4">
        <div>
          <div class="flex items-center gap-2.5">
            <div class="w-8 h-8 rounded-xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600 shadow-xs">
              <Target class="w-4 h-4" />
            </div>
            <h2 class="text-base font-bold text-slate-900">
              工程教育专业认证 · 毕业要求指标点矩阵维护
            </h2>
            <span class="text-xs px-2.5 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200 font-semibold shadow-2xs">
              任课教师专责维护 · 课程指标维护
            </span>
          </div>
          <p class="text-xs text-slate-500 mt-1.5">
            任课教师对主讲课程《{{ currentCourse?.courseName || '当前课程' }}》负责填报指标点分解、支撑权重 (H强/M中/L弱) 与课程目标对应关系，支持随时新增、编辑与删除
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-2.5">
          <input v-model="planVersion" aria-label="培养方案版本" placeholder="培养方案版本" class="border border-slate-200 rounded-xl px-3 py-1.5 text-xs w-28 bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 shadow-2xs" />
          <input v-model="syllabusVersion" aria-label="新大纲版本" placeholder="新大纲版本" class="border border-slate-200 rounded-xl px-3 py-1.5 text-xs w-28 bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 shadow-2xs" />
          <!-- 只使用教研室已导入的培养方案目录 -->
          <button
            @click="applyDefaultIndicators"
            :disabled="loadingIndicators"
            class="px-3.5 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-xl text-xs font-semibold shadow-2xs transition flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
            title="按所选培养方案版本创建课程大纲"
          >
            <Sparkles class="w-3.5 h-3.5 text-indigo-600" /> 从培养方案创建大纲
          </button>

          <button @click="applyRecommendedTemplate" :disabled="loadingIndicators" class="px-3 py-2 rounded-xl border text-xs text-indigo-700 disabled:opacity-50">
            套用推荐示例模板（12类）
          </button>
          <!-- 新增指标点按钮 -->
          <button
            @click="openAddIndicatorModal"
            :disabled="!canEditIndicators"
            class="px-4 py-1.5 bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-700 hover:to-indigo-800 text-white rounded-xl text-xs font-semibold shadow-xs transition flex items-center gap-1.5 cursor-pointer"
          >
            <Plus class="w-3.5 h-3.5" /> 新增认证指标点
          </button>
        </div>
      </div>

      <div v-if="syllabusVersions.length" class="flex flex-wrap items-center gap-3 text-xs">
        <label for="teacher-syllabus-version" class="font-semibold">查看大纲版本</label>
        <select id="teacher-syllabus-version" v-model="selectedSyllabusId" aria-label="查看大纲版本" :disabled="loadingIndicators" @change="loadIndicators(selectedSyllabusId ?? undefined)" class="border border-slate-200 rounded-xl px-3 py-2 bg-white">
          <option v-for="version in syllabusVersions" :key="version.id" :value="version.id">{{ version.version }}{{ version.id === latestSyllabusId ? '（当前版）' : '（历史版）' }}</option>
        </select>
        <span v-if="isHistoricalSyllabus" role="status" class="text-amber-700">历史版本只读，可查看当时的指标、支撑权重和课程目标。</span>
        <span v-else-if="currentSyllabus?.status === 'LOCKED'" class="text-amber-700">当前大纲已锁定，请创建新版本后修改。</span>
      </div>
      <p v-if="indicatorError" class="text-xs text-rose-700" role="alert">{{ indicatorError }} <button @click="loadIndicators()" class="underline">重试加载</button></p>
      <p v-else-if="!loadingIndicators && !currentSyllabus" class="text-xs text-amber-700">当前课程尚未创建大纲，请填写新大纲版本，从已导入目录创建或套用推荐示例模板。</p>
      <p v-else-if="!loadingIndicators && planIndicators.length === 0" class="text-xs text-amber-700">当前大纲目录暂无指标，请联系专业牵头教研室导入，或选择已有目录创建新大纲版本。</p>
      <p v-if="planVersion === 'RECOMMENDED-12'" class="text-xs text-amber-700" role="status">当前为推荐示例草案，供实验演示；请按课程内容修订，不代表专业正式培养方案。</p>
      <!-- 指标点矩阵表格 -->
      <div class="overflow-x-auto border border-slate-200/80 rounded-xl">
        <table class="w-full text-left text-xs text-slate-700">
          <thead class="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200">
            <tr>
              <th class="py-3.5 px-4 font-semibold">指标点编号</th>
              <th class="py-3.5 px-4 font-semibold">毕业要求大项</th>
              <th class="py-3.5 px-4 font-semibold">指标点分解表述</th>
              <th class="py-3.5 px-4 font-semibold">支撑权重</th>
              <th class="py-3.5 px-4 font-semibold">对应课程目标</th>
              <th class="py-3.5 px-4 text-right font-semibold">任课教师操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-if="indicators.length === 0">
              <td colspan="6" class="py-12 text-center text-slate-400 bg-white">
                <div class="flex flex-col items-center justify-center gap-2">
                  <FileText class="w-6 h-6 text-slate-300" />
                  <span>{{ isHistoricalSyllabus ? '该历史版本暂无指标映射' : '该课程暂未录入毕业要求指标点，请点击右上角【从培养方案创建大纲】或【新增认证指标点】录入' }}</span>
                </div>
              </td>
            </tr>
            <tr v-for="ind in indicators" :key="ind.id" class="hover:bg-slate-50/80 bg-white transition-colors">
              <td class="py-3.5 px-4 font-mono font-bold text-indigo-600">{{ ind.indicatorCode }}</td>
              <td class="py-3.5 px-4 text-slate-900 font-semibold">{{ ind.requirementCategory }}</td>
              <td class="py-3.5 px-4 text-slate-600 leading-relaxed max-w-md">{{ ind.indicatorDescription }}</td>
              <td class="py-3.5 px-4">
                <span :class="[
                  'px-2.5 py-1 rounded-md text-[10px] font-bold font-mono inline-block shadow-2xs',
                  ind.supportWeight === 'H' ? 'bg-rose-50 text-rose-700 border border-rose-200' :
                  (ind.supportWeight === 'M' ? 'bg-amber-50 text-amber-700 border border-amber-200' : 'bg-slate-100 text-slate-600 border border-slate-200')
                ]">
                  {{ ind.supportWeight }} ({{ ind.supportWeight === 'H' ? '强支撑' : (ind.supportWeight === 'M' ? '中等' : '弱支撑') }})
                </span>
              </td>
              <td class="py-3.5 px-4 text-slate-600 font-mono">{{ ind.targetGoal || '未设置' }}</td>
              <td class="py-3.5 px-4 text-right space-x-3">
                <button @click="openEditIndicatorModal(ind)" :disabled="!canEditIndicators" class="text-indigo-600 hover:text-indigo-800 font-bold cursor-pointer">修改</button>
                <button @click="confirmDeleteIndicator(ind)" :disabled="!canEditIndicators" class="text-rose-600 hover:text-rose-800 font-bold cursor-pointer">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 弹窗：任课教师新增/编辑认证指标点 (写进 MySQL) -->
    <UiModal :open="!!(showIndicatorModal)" title="指标点编辑" @close="showIndicatorModal = false">
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
            <Target class="w-4 h-4 text-indigo-600" /> {{ indicatorForm.id ? '修改毕业要求指标点 (任课教师)' : '新增毕业要求指标点 (任课教师)' }}
          </h3>
          <span class="text-[11px] px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200 font-mono font-semibold">
            保存至平台
          </span>
        </div>

        <div class="space-y-3.5 text-xs">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="text-slate-700 font-semibold block mb-1">指标点编号 (如 1-1, 11-1, 12-1)</label>
              <select v-model="indicatorForm.indicatorCode" aria-label="指标点编号" @change="selectPlanIndicator" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-2xs">
                <option value="">请选择</option>
                <option v-for="item in planIndicators" :key="item.indicatorCode" :value="item.indicatorCode">{{ item.indicatorCode }}</option>
              </select>
            </div>
            <div>
              <label class="text-slate-700 font-semibold block mb-1">支撑权重 (H强/M中/L弱)</label>
              <select
                v-model="indicatorForm.supportWeight"
                class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-2xs font-semibold cursor-pointer"
              >
                <option value="H">H (强支撑)</option>
                <option value="M">M (中等支撑)</option>
                <option value="L">L (弱支撑)</option>
              </select>
            </div>
          </div>

          <div>
            <label class="text-slate-700 font-semibold block mb-1">毕业要求大项（培养方案目录）</label>
            <select
              v-model="indicatorForm.requirementCategory"
              aria-label="毕业要求大项（培养方案目录）"
              @change="selectPlanCategory"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-2xs cursor-pointer"
            >
              <option v-for="cat in standardIndicatorCategories" :key="cat" :value="cat">
                {{ cat }}
              </option>
            </select>
            <p class="mt-1 text-[11px] text-slate-500">选择大项会同步对应指标编号；编号与大项均以当前目录为准。</p>
          </div>

          <div>
            <label class="text-slate-700 font-semibold block mb-1">对应课程目标 (如 目标1, 目标2, 目标3)</label>
            <input
              v-model="indicatorForm.targetGoal"
              placeholder="如 目标1"
              class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-2xs"
            />
          </div>

          <div>
            <label class="text-slate-700 font-semibold block mb-1">指标点分解内容表述</label>
            <textarea
              v-model="indicatorForm.indicatorDescription"
              rows="3"
              placeholder="请输入该指标点在课程中的分解细化要求与能力观测点..."
              class="w-full bg-white border border-slate-200 rounded-xl p-3 text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 shadow-2xs"
            ></textarea>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
          <button @click="nav.request(()=>{showIndicatorModal = false})" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold transition cursor-pointer">取消</button>
          <button @click="handleSaveIndicator" class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold shadow-xs transition cursor-pointer">保存指标</button>
        </div>
      </UiModal>

    <!-- 编辑资源标签与共享范围弹窗 -->
    <UiModal :open="!!(editingResource)" title="编辑资源标签与共享范围" @close="editingResource = null">
        <h3 class="font-bold text-slate-900 text-sm">编辑资源标签与共享范围</h3>
        <p class="text-xs text-slate-600 font-mono bg-slate-50 p-2 rounded-lg border border-slate-100">{{ editingResource.resourceName }}</p>
        <div class="flex flex-wrap gap-3 text-xs">
          <label v-for="tag in ['理论', '实验', '讨论', '研讨']" :key="tag" class="flex items-center gap-1.5 cursor-pointer font-medium text-slate-700">
            <input v-model="editResourceTags" type="checkbox" :value="tag" class="rounded text-indigo-600" />{{ tag }}
          </label>
        </div>
        <label class="flex items-center gap-2 text-xs font-medium text-slate-800 cursor-pointer">
          <input v-model="editResourcePublic" type="checkbox" class="rounded text-indigo-600" />教研室共享
        </label>
        <div class="flex justify-end gap-2 pt-2 border-t border-slate-100">
          <button @click="nav.request(()=>{editingResource = null})" class="px-3.5 py-1.5 bg-slate-100 hover:bg-slate-200 rounded-xl text-xs font-medium text-slate-700 cursor-pointer">取消</button>
          <button @click="saveResourceMetadata" :disabled="savingResourceMetadata" class="px-3.5 py-1.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-xs cursor-pointer">保存修改</button>
        </div>
      </UiModal>

    <!-- 弹窗：上传教学资源 -->
    <UiModal :open="!!(showUploadModal)" title="上传教学课件/教案" @close="showUploadModal = false">
        <h3 class="text-base font-bold text-slate-900 flex items-center gap-2">
          <Upload class="w-4 h-4 text-indigo-600" /> 上传教学课件/教案
        </h3>
        <div class="space-y-3.5 text-xs">
          <!-- 真实课件文件选择与拖拽区 -->
          <div>
            <label class="text-slate-700 font-bold block mb-1">选择真实课件文件 (.pptx / .docx / .pdf)</label>
            <input
              type="file"
              ref="resourceFileInputRef"
              @change="onResourceFileSelected"
              accept=".pptx,.ppt,.docx,.doc,.pdf"
              class="hidden"
            />
            <div
              role="button" tabindex="0" aria-label="选择课件文件" @keydown.enter.prevent="triggerResourceFileInput" @keydown.space.prevent="triggerResourceFileInput" @click="triggerResourceFileInput"
              @dragover.prevent
              @drop.prevent="onResourceFileDrop"
              class="border-2 border-dashed rounded-xl p-4 text-center cursor-pointer transition"
              :class="selectedResourceFile ? 'border-indigo-500 bg-indigo-50/40' : 'border-slate-300 hover:border-indigo-400 bg-slate-50/70 hover:bg-slate-50'"
            >
              <div v-if="!selectedResourceFile" class="flex flex-col items-center justify-center gap-1.5 text-slate-500">
                <Upload class="w-6 h-6 text-indigo-600" />
                <span class="font-semibold text-slate-700 text-xs">点击选择本地真实 PPTX / DOCX / PDF 文件</span>
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
                  class="text-rose-500 hover:text-rose-700 text-xs px-2 py-1 rounded hover:bg-rose-50 cursor-pointer font-medium"
                >
                  更换文件
                </button>
              </div>
            </div>
          </div>

          <div>
            <label class="text-slate-700 font-semibold block mb-1">所属章节 (按章挂载)</label>
            <input v-model="uploadForm.chapter" placeholder="如 第一章 软件项目管理概论" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 shadow-2xs" />
          </div>
          <div>
            <label class="text-slate-700 font-semibold block mb-1">资源文件名称</label>
            <input v-model="uploadForm.resourceName" placeholder="如 第1讲-需求估算与甘特图.pptx" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 shadow-2xs" />
          </div>
          <div class="grid grid-cols-2 gap-2.5">
            <div>
              <label class="text-slate-700 font-semibold block mb-1">文件格式</label>
              <select v-model="uploadForm.fileType" class="w-full bg-white border border-slate-200 rounded-xl px-3 py-2 text-slate-800 focus:outline-none focus:border-indigo-500 shadow-2xs">
                <option value="PPTX">PPTX 演示文稿</option>
                <option value="DOCX">DOCX 教案大纲</option>
                <option value="PDF">PDF 电子文档</option>
              </select>
            </div>
            <div>
              <label class="text-slate-700 font-semibold block mb-1">环节标签</label>
              <div class="flex flex-wrap gap-2 pt-1">
                <label v-for="tag in ['理论', '实验', '讨论', '研讨']" :key="tag" class="inline-flex gap-1 items-center font-medium text-slate-700 cursor-pointer">
                  <input v-model="uploadForm.tags" type="checkbox" :value="tag" class="rounded text-indigo-600" />{{ tag }}
                </label>
                <span class="text-slate-400 text-[10px] self-center">不选即未标注</span>
              </div>
            </div>
          </div>
          <label class="flex items-center gap-2 text-xs font-semibold text-slate-800 cursor-pointer"><input v-model="uploadForm.isPublic" type="checkbox" class="rounded text-indigo-600" />教研室共享</label>
          <div class="p-3 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-[11px] flex items-center gap-2">
            <AlertCircle class="w-4 h-4 text-amber-600 flex-shrink-0" />
            <span>单文件上限 100MB · 挂载后自动注入东北大学动态只读防盗水印</span>
          </div>
        </div>

        <div class="flex items-center justify-end gap-2.5 pt-3 border-t border-slate-100">
          <button @click="nav.request(()=>{showUploadModal = false})" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold transition cursor-pointer">取消</button>
          <button
            @click="handleSaveResource"
            :disabled="isUploadingResource"
            class="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-xl text-xs font-semibold shadow-xs transition cursor-pointer flex items-center gap-1.5"
          >
            <span v-if="isUploadingResource" class="inline-block w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
            {{ isUploadingResource ? '正在上传真实课件...' : '立即挂载' }}
          </button>
        </div>
      </UiModal>

    <!-- 授权后展示后端转换、加水印的真实 PDF -->
    <Teleport to="body">
    <UiModal :open="!!(previewModalVisible)" title="· 限时水印预览" @close="closePreview()">
        <div class="flex justify-between items-center border-b border-slate-100 pb-3">
          <h3 class="font-bold text-slate-900 text-sm flex items-center gap-2">
            <Eye class="w-4 h-4 text-indigo-600" />
            {{ currentPreviewRes?.resourceName }} · 限时水印预览
          </h3>
          <button @click="closePreview" class="text-slate-500 hover:text-slate-700 px-3 py-1 bg-slate-100 hover:bg-slate-200 rounded-lg text-xs font-semibold transition cursor-pointer">关闭</button>
        </div>
        <ProtectedPdfPreview v-if="previewUrl" :src="previewUrl" />
        <p v-else class="text-sm text-slate-500 py-12 text-center">正在准备预览...</p>

      </UiModal>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import FeatureSelector from '../components/FeatureSelector.vue'
import UiModal from '../components/UiModal.vue'
import { useNavigation, usePreference, useEditGuard, useFeedback, useDomEdits } from '../ui/context'
import { useRadar } from '../ui/useRadar'
import type { SaveResult } from '../ui/context'

import ProtectedPdfPreview from '../components/ProtectedPdfPreview.vue'
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
import type { CourseSyllabus, CourseResource, CourseOffering, CourseSchedule, TeacherQualityRadarVO, GraduationIndicator, CourseContentRevision } from '../api/types'

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
const hasVersionConflict=ref(false)
const isSavingDraft = ref(false)
const isPublishingContent = ref(false)

const formatDateTime = (val?: string) => {
  if (!val) return ''
  return val.replace('T', ' ').substring(0, 19)
}

const teacherList = ref<{ name: string; courseName: string }[]>([])
const currentTeacher = ref('郭军')
const myOfferings = ref<CourseOffering[]>([])
const selectedOfferingId = usePreference<number | null>('teacher','offering',null)
const currentOffering = ref<CourseOffering | null>(null)
const currentCourse = ref<any>(null)
const currentSchedule = ref<CourseSchedule | null>(null)
const currentSyllabus = ref<any>(null)
const syllabusVersions = ref<CourseSyllabus[]>([])
const selectedSyllabusId = ref<number | null>(null)
const latestSyllabusId = ref<number | null>(null)
const isHistoricalSyllabus = computed(() => !!selectedSyllabusId.value && selectedSyllabusId.value !== latestSyllabusId.value)
let indicatorRequest = 0
let courseLoadGeneration = 0
const isCurrentCourseLoad = (generation: number) => !disposed && generation === courseLoadGeneration
const selectedTag = ref('')
const myResources = ref<CourseResource[]>([])
const sharedResources = ref<CourseResource[]>([])
const sharedKeyword = ref('')
const sharedLoading = ref(false)
const sharedError = ref('')
const editingResource = ref<CourseResource | null>(null)
const editResourceTags = ref<string[]>([])
const editResourcePublic = ref(false)
const savingResourceMetadata = ref(false)
const radarData = ref<TeacherQualityRadarVO | null>(null)
const radarChartRef = ref<HTMLDivElement | null>(null)
let radarChart: echarts.ECharts | null = null
const managedRadar=useRadar(radarChartRef, chart=>{radarChart=chart;renderRadarChart()})

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

const planIndicators = ref<GraduationIndicator[]>([])
const indicatorError = ref('')
const canEditIndicators = computed(() => !loadingIndicators.value && !indicatorError.value &&
  !!currentSyllabus.value && !isHistoricalSyllabus.value && currentSyllabus.value.status !== 'LOCKED' && planIndicators.value.length > 0)
const planVersion = ref('')
const syllabusVersion = ref('')
const standardIndicatorCategories = computed(() => Array.from(new Set(planIndicators.value.map(i => i.requirementCategory))))

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
  tags: [] as string[],
  isPublic: false,
  fileSize: '15.6 MB',
  fileSizeBytes: 0,
})

const previewModalVisible = ref(false)
const currentPreviewRes = ref<CourseResource | null>(null)
const previewUrl = ref('')
let previewRequest = 0
let previewTimer: ReturnType<typeof setInterval> | undefined
const closePreview = () => {
  previewRequest++
  if (previewTimer) clearInterval(previewTimer); previewTimer = undefined
  if (previewUrl.value.startsWith('blob:')) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
  previewModalVisible.value = false
}

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
  const generation = ++courseLoadGeneration
  ++indicatorRequest
  await loadCourse(generation)
  if (!isCurrentCourseLoad(generation)) return
  await Promise.all([loadMyResources(), loadSharedResources(), loadRadar(), loadIndicators(), loadCourseContent()])
}

const onOfferingSelectChange = async () => {
  const generation = ++courseLoadGeneration
  ++indicatorRequest
  const off = myOfferings.value.find(o => o.id === selectedOfferingId.value)
  if (off) {
    currentOffering.value = off
    currentCourse.value = off.course ?? null
    currentSchedule.value = null
    currentSyllabus.value = null
    indicators.value = []
    syllabusVersions.value = []
    selectedSyllabusId.value = null
    latestSyllabusId.value = null
    showIndicatorModal.value = false
    loadingIndicators.value = true
    draftRevision.value = null
    contentConflictMsg.value = ''
    uploadForm.value.chapter = currentCourse.value ? `第一章 ${currentCourse.value.courseName}概论` : '第一章 概论'
    try {
      const schedules = await scheduleApi.getAll(off.id)
      if (!isCurrentCourseLoad(generation)) return
      currentSchedule.value = schedules && schedules.length > 0 ? schedules[0] : null
    } catch (err) {
      if (!isCurrentCourseLoad(generation)) return
      console.warn('获取排课信息失败', err)
    }
    if (!isCurrentCourseLoad(generation)) return
    await Promise.all([loadMyResources(), loadSharedResources(), loadRadar(), loadIndicators(), loadCourseContent()])
  }
}

const loadCourse = async (generation: number) => {
  draftRevision.value = null
  contentConflictMsg.value = ''
  currentCourse.value = null
  currentOffering.value = null
  currentSchedule.value = null
  currentSyllabus.value = null
  indicators.value = []
  syllabusVersions.value = []
  selectedSyllabusId.value = null
  latestSyllabusId.value = null
  showIndicatorModal.value = false
  loadingIndicators.value = true
  syllabusForm.value = { objectives: '', assessmentMethod: '' }

  try {
    const offerings = await courseApi.getOfferings()
    if (!isCurrentCourseLoad(generation)) return
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
        if (!isCurrentCourseLoad(generation)) return
        if (schedules && schedules.length > 0) {
          currentSchedule.value = schedules[0]
        }
      } catch (err) {
        if (!isCurrentCourseLoad(generation)) return
        console.warn('获取排课信息失败', err)
      }
    }
  } catch (e) {
    if (!isCurrentCourseLoad(generation)) return
    console.error('获取教师开课记录失败', e)
  }
}

const loadIndicators = async (versionId?: number) => {
  const request = ++indicatorRequest
  const course = currentCourse.value
  const generation = courseLoadGeneration
  const isCurrent = () => request === indicatorRequest && isCurrentCourseLoad(generation) && course?.id === currentCourse.value?.id
  planIndicators.value = []
  indicators.value = []
  indicatorError.value = ''
  showIndicatorModal.value = false
  currentSyllabus.value = null
  loadingIndicators.value = true
  try {
    if (!course?.id) {
      syllabusVersions.value = []
      selectedSyllabusId.value = null
      latestSyllabusId.value = null
      return
    }
    const [versions, latest] = await Promise.all([
      syllabusApi.getVersions(course.id), syllabusApi.getLatest(course.id)
    ])
    if (!isCurrent()) return
    const selected = versionId ? versions.find(v => v.id === versionId) : latest
    if (versionId && !selected) throw new Error('所选大纲版本不存在，请重新加载')
    const selectedPlan = selected?.planVersion || selected?.version || ''
    const [mappings, catalog] = await Promise.all([
      syllabusApi.getIndicators(course.id, selected?.id),
      selectedPlan && course.majorCode ? syllabusApi.getPlanIndicators(course.majorCode, selectedPlan) : Promise.resolve([])
    ])
    if (!isCurrent()) return
    syllabusVersions.value = versions
    latestSyllabusId.value = latest?.id ?? null
    selectedSyllabusId.value = selected?.id ?? null
    currentSyllabus.value = selected
    indicators.value = mappings
    planIndicators.value = catalog
    planVersion.value = selectedPlan
    if (versionId === undefined) syllabusVersion.value = ''
  } catch (e: any) {
    if (!isCurrent()) return
    indicatorError.value = e.response?.data?.message || e.message || '加载指标目录失败'
    console.error('加载指标点失败', e)
  } finally {
    if (isCurrent()) loadingIndicators.value = false
  }
}

const openAddIndicatorModal = () => {
  if (!canEditIndicators.value) return
  indicatorForm.value = {
    id: null,
    indicatorCode: planIndicators.value[0]?.indicatorCode || '',
    requirementCategory: standardIndicatorCategories.value[0] || '',
    indicatorDescription: '',
    supportWeight: 'H',
    targetGoal: '目标1'
  }
  showIndicatorModal.value = true
}

const selectPlanIndicator = () => {
  const found = planIndicators.value.find(i => i.indicatorCode === indicatorForm.value.indicatorCode)
  if (found) {
    indicatorForm.value.requirementCategory = found.requirementCategory
    indicatorForm.value.indicatorDescription = found.indicatorDescription
  }
}

const selectPlanCategory = () => {
  const previous = planIndicators.value.find(i => i.indicatorCode === indicatorForm.value.indicatorCode)
  if (previous?.requirementCategory === indicatorForm.value.requirementCategory) return
  const found = planIndicators.value.find(i => i.requirementCategory === indicatorForm.value.requirementCategory)
  indicatorForm.value.indicatorCode = found?.indicatorCode || ''
  if (!indicatorForm.value.indicatorDescription.trim() || indicatorForm.value.indicatorDescription === previous?.indicatorDescription) {
    indicatorForm.value.indicatorDescription = found?.indicatorDescription || ''
  }
}

const openEditIndicatorModal = (ind: GraduationIndicator) => {
  if (!canEditIndicators.value) return
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

const handleSaveIndicator = async ():Promise<SaveResult> => {
 const courseId=currentCourse.value?.id;if(!canEditIndicators.value||!courseId||!indicatorForm.value.indicatorCode.trim()||!indicatorForm.value.indicatorDescription.trim())return {status:'invalid'}
 const submitted=indicatorGuard.capture(),finish=nav.startSaving()
 try{if(indicatorForm.value.id)await syllabusApi.updateIndicator(indicatorForm.value.id,indicatorForm.value);else await syllabusApi.addIndicator(courseId,indicatorForm.value);if(disposed||currentCourse.value?.id!==courseId)return {status:'stale'};indicatorGuard.saved(submitted);if(!indicatorGuard.dirty())showIndicatorModal.value=false;await loadIndicators();notify('指标点已保存');return {status:'success'}}catch(e:any){notify(e.message||'指标点保存失败');return {status:e.response?.status===409?'conflict':'failed'}}finally{finish()}
}

const confirmDeleteIndicator = async (ind: GraduationIndicator) => {
  if (!canEditIndicators.value) return
  if (!await confirm(`确定要从 移除指标点 [${ind.indicatorCode}] 吗？`)) return
  try {
    await syllabusApi.deleteIndicator(ind.id)
    await loadIndicators()
    notify('指标点已成功从 移除！')
  } catch (err: any) {
    notify(err.response?.data?.message || err.message || '删除指标点失败')
  }
}

const applyRecommendedTemplate = async () => {
  if (!currentCourse.value?.id || !syllabusVersion.value.trim()) {
    notify('请选择课程并填写新大纲版本'); return
  }
  loadingIndicators.value = true
  try {
    await syllabusApi.createFromPlan(currentCourse.value.id, syllabusVersion.value.trim(), 'RECOMMENDED-12')
    await loadIndicators()
    notify('已创建12项推荐草案，请按课程实际情况修订并交主任审核')
  } catch (err: any) { notify(err.response?.data?.message || err.message || '套用模板失败') }
  finally { loadingIndicators.value = false }
}

const applyDefaultIndicators = async () => {
  if (!currentCourse.value?.id || !currentCourse.value.majorCode || !planVersion.value || !syllabusVersion.value) {
    notify('请选择课程并填写培养方案版本和新大纲版本')
    return
  }
  loadingIndicators.value = true
  try {
    await syllabusApi.createFromPlan(currentCourse.value.id, syllabusVersion.value, planVersion.value)
    await loadIndicators()
    notify('已按培养方案目录创建大纲版本')
  } catch (err: any) {
    notify(err.response?.data?.message || err.message || '创建大纲失败')
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
    managedRadar.render()
  } catch (e) {
    console.error('加载雷达图数据失败', e)
  }
}

const renderRadarChart = () => {
  if (!radarChartRef.value) return
  if (!radarChart) return

  const d = radarData.value
  if (!d || [d.attitudeScore,d.contentScore,d.methodScore,d.effectScore].some(v=>v==null)) { radarChart.clear(); return }
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
        fontSize: 11,
        fontWeight: 'bold'
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
              color: 'rgba(79, 70, 229, 0.22)'
            },
            lineStyle: {
              color: '#4f46e5',
              width: 2.5
            },
            itemStyle: {
              color: '#4f46e5',
              borderColor: '#ffffff',
              borderWidth: 2
            }
          }
        ]
      }
    ]
  }

  radarChart.setOption({...option,animation:false},true)
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
    notify('单文件大小不能超过 100MB 限制！')
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
  } else if (ext === 'DOC' || ext === 'DOCX') {
    uploadForm.value.fileType = 'DOCX'
  } else if (ext === 'PDF') {
    uploadForm.value.fileType = 'PDF'
  }
}

const clearSelectedResourceFile = () => {
  selectedResourceFile.value = null
  if (resourceFileInputRef.value) {
    resourceFileInputRef.value.value = ''
  }
}

const confirmDeleteResource = async (res: CourseResource) => {
  if (!await confirm(`确定要从平台彻底删除课件资源《${res.resourceName}》吗？删除后将无法恢复。`)) return
  try {
    await resourceApi.delete(res.id)
    notify(`课件《${res.resourceName}》已成功删除！`)
    await loadMyResources()
  } catch (err: any) {
    notify(err.response?.data?.message || err.message || '删除课件失败')
  }
}

const previewResource = async (res: CourseResource) => {
  closePreview()
  const request = previewRequest
  currentPreviewRes.value = res
  previewModalVisible.value = true
  try {
    const pdf = await resourceApi.getPreviewPdf(res.id)
    if (request !== previewRequest || !previewModalVisible.value) return
    previewUrl.value = URL.createObjectURL(pdf)
    previewTimer = setInterval(async () => { try { await resourceApi.checkAccess(res.id) } catch { if(request === previewRequest) closePreview() } }, 5000)
  } catch (e: any) {
    if (request !== previewRequest) return
    closePreview()
    notify(e.message || '预览失败')
  }
}

const loadSharedResources = async () => {
  sharedLoading.value = true
  sharedError.value = ''
  try {
    sharedResources.value = await resourceApi.search({ isPublic: true, keyword: sharedKeyword.value.trim() })
  } catch (e: any) {
    sharedResources.value = []
    sharedError.value = e.response?.data?.message || e.message || '共享资源加载失败'
  } finally {
    sharedLoading.value = false
  }
}

const openResourceEdit = (res: CourseResource) => {
  editingResource.value = res
  editResourceTags.value = [...(res.tags || [])]
  editResourcePublic.value = res.isPublic
}

const saveResourceMetadata = async ():Promise<SaveResult> => {
 const resource=editingResource.value;if(!resource||savingResourceMetadata.value)return {status:'invalid'}
 const submitted=resourceGuard.capture(),finish=nav.startSaving();savingResourceMetadata.value=true
 try{await resourceApi.save({id:resource.id,courseId:resource.course.id,chapter:resource.chapter,resourceName:resource.resourceName,tags:editResourceTags.value,isPublic:editResourcePublic.value});if(disposed)return {status:'stale'};resourceGuard.saved(submitted);if(!resourceGuard.dirty())editingResource.value=null;await Promise.all([loadMyResources(),loadSharedResources()]);notify('资源信息已保存');return {status:'success'}}catch(e:any){notify(e.message||'资源信息保存失败');return {status:e.response?.status===409?'conflict':'failed'}}finally{savingResourceMetadata.value=false;finish()}
}

const handleSaveResource = async ():Promise<SaveResult> => {
 const courseId=currentCourse.value?.id,file=selectedResourceFile.value;if(!courseId||!file||!uploadForm.value.resourceName.trim()||isUploadingResource.value){notify('请选择文件并填写课件名称');return {status:'invalid'}}
 const submitted=uploadGuard.capture(),finish=nav.startSaving();isUploadingResource.value=true
 try{await resourceApi.uploadFile(file,{courseId,chapter:uploadForm.value.chapter,resourceName:uploadForm.value.resourceName,tags:uploadForm.value.tags,isPublic:uploadForm.value.isPublic});if(disposed||currentCourse.value?.id!==courseId)return {status:'stale'};uploadGuard.saved(submitted);if(!uploadGuard.dirty()){showUploadModal.value=false;clearSelectedResourceFile()};await Promise.all([loadMyResources(),loadSharedResources()]);notify('课件上传成功，可通过授权预览查看水印');return {status:'success'}}catch(e:any){notify(e.message||'上传失败');return {status:e.response?.status===409?'conflict':'failed'}}finally{isUploadingResource.value=false;finish()}
}

let contentLoadSequence = 0
const loadCourseContent = async (adopt = false) => {
  const requestSequence=++contentLoadSequence,courseId=currentCourse.value?.id
  if(!courseId)return
  try {
    const [draft,published]=await Promise.all([courseContentApi.getDraft(courseId),courseContentApi.getPublished(courseId)])
    if(disposed || requestSequence!==contentLoadSequence || currentCourse.value?.id!==courseId)return
    const next={description:draft.description||'',assessmentMethod:draft.assessmentMethod||'',objectives:draft.objectives||'',lockVersion:draft.lockVersion??0,publishVersion:draft.publishVersion??0}
    if(contentGuard.dirty()&&!adopt){latestContentSnapshot.value={draft,published,form:next};notify('已取得最新草稿，本地编辑完整保留');return}
    draftRevision.value=draft;publishedRevision.value=published;contentForm.value=next;contentConflictMsg.value='';hasVersionConflict.value=false;latestContentSnapshot.value=null;contentGuard.reset()
  } catch(error:any){if(!disposed&&requestSequence===contentLoadSequence)contentConflictMsg.value=error.response?.data?.message||error.message||'草稿加载失败'}
}

const handleSaveDraft = async ():Promise<SaveResult> => {
  const courseId=currentCourse.value?.id
  if(isSavingDraft.value||isPublishingContent.value||!courseId||!draftRevision.value||draftRevision.value.courseId!==courseId||hasVersionConflict.value)return {status:'invalid'}
  const submitted=contentGuard.capture(),finishSaving=nav.startSaving();isSavingDraft.value=true
  try{
    const updated=await courseContentApi.saveDraft(courseId,{...contentForm.value,draftId:draftRevision.value.id})
    if(disposed||currentCourse.value?.id!==courseId)return {status:'stale'}
    hasVersionConflict.value=false;contentConflictMsg.value='';draftRevision.value=updated;contentForm.value.lockVersion=updated.lockVersion??updated.version??0;contentGuard.saved(submitted);notify('课程草稿已保存');return {status:'success'}
  }catch(err:any){const conflict=err.response?.status===409||err.status===409;hasVersionConflict.value=conflict;contentConflictMsg.value=err.response?.data?.message||err.message||'草稿保存失败';notify(contentConflictMsg.value);if(conflict)await loadCourseContent();return {status:conflict?'conflict':'failed'}}
  finally{isSavingDraft.value=false;finishSaving()}
}

const handlePublishContent = async () => {
  if (!currentCourse.value?.id) {
    notify('请先选择有效课程')
    return
  }

  const desc = contentForm.value.description?.trim()
  const assess = contentForm.value.assessmentMethod?.trim()
  const objs = contentForm.value.objectives?.trim()

  if (!desc || !assess || !objs) {
    notify('正式发布失败：课程简介、考核方式和教学目标三项必须全部填写齐全！')
    return
  }

  if (!draftRevision.value || draftRevision.value.courseId !== currentCourse.value.id || hasVersionConflict.value) return
  const courseId = currentCourse.value.id
  const submitted=contentGuard.capture(),finishPublishing=nav.startSaving()
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
    if (disposed || currentCourse.value?.id !== courseId) return
    contentGuard.saved(submitted)
    publishedRevision.value = published
    draftRevision.value = null
    currentCourse.value.description = published.description
    currentCourse.value.assessmentMethod = published.assessmentMethod
    currentCourse.value.objectives = published.objectives
    notify(`课程简介、考核方式与教学目标发布成功！正式版本: v${published.publishVersion || 1}`)
    await loadCourseContent()
  } catch (err: any) {
    if (disposed || currentCourse.value?.id !== courseId) return
    if (err.response?.status === 409 || err.status === 409) {
      hasVersionConflict.value=true
      contentConflictMsg.value = err.response?.data?.message || err.message || '检测到并发发布冲突，请查看最新草稿'
      await loadCourseContent()
    } else if (err.response?.status === 403 || err.status === 403) {
      notify(err.response?.data?.message || '越权拦截：只有该课程关联任课教师才能发布')
    } else {
      notify(err.response?.data?.message || err.message || '发布失败')
    }
  } finally {
    isPublishingContent.value = false
    finishPublishing()
  }
}


const nav=useNavigation(),{notify,confirm}=useFeedback(),feature=usePreference('teacher','feature','materials'),viewRoot=ref<HTMLElement>()
const fallbackEdits=useDomEdits(viewRoot,'教师工作台表单')
const features=[{key:'materials',label:'课程资料',description:'编辑简介、考核与目标，维护课件',iconComp:BookOpen},{key:'shared',label:'共享备课',description:'检索教研室可访问的备课资源',iconComp:Folder},{key:'indicators',label:'指标矩阵',description:'查看大纲版本和培养方案支撑',iconComp:Target},{key:'feedback',label:'教学反馈',description:'查看维度评分与匿名教学建议',iconComp:Target},{key:'history',label:'开课历史',description:'追溯历史班次与授课人次',iconComp:BookOpen}]
let disposed=false
const latestContentSnapshot=ref<any>(null)
const contentGuard=useEditGuard('课程资料',()=>({description:contentForm.value.description,assessmentMethod:contentForm.value.assessmentMethod,objectives:contentForm.value.objectives}),value=>{Object.assign(contentForm.value,value)},()=>true,handleSaveDraft)
const uploadGuard=useEditGuard('课件上传',()=>({form:uploadForm.value,file:selectedResourceFile.value?.name||''}),value=>{uploadForm.value=value.form;clearSelectedResourceFile()},()=>showUploadModal.value,handleSaveResource)
const indicatorGuard=useEditGuard('指标点编辑',()=>indicatorForm.value,value=>{indicatorForm.value=value},()=>showIndicatorModal.value,handleSaveIndicator)
const resourceGuard=useEditGuard('资源标签与共享',()=>({tags:editResourceTags.value,shared:editResourcePublic.value}),value=>{editResourceTags.value=value.tags;editResourcePublic.value=value.shared},()=>!!editingResource.value,saveResourceMetadata)
async function requestOfferingChange(event:Event){const select=event.target as HTMLSelectElement,next=Number(select.value);select.value=String(selectedOfferingId.value||'');await nav.request(async()=>{selectedOfferingId.value=next;await onOfferingSelectChange();fallbackEdits.reset()})}
async function requestTeacherChange(event:Event){const select=event.target as HTMLSelectElement,next=select.value;select.value=currentTeacher.value;await nav.request(async()=>{currentTeacher.value=next;selectedOfferingId.value=null;await onTeacherChange();fallbackEdits.reset()})}
async function refreshCourseContent(){await loadCourseContent()}
async function adoptLatestContent(){await nav.request(async()=>{if(latestContentSnapshot.value){const {draft,published,form}=latestContentSnapshot.value;draftRevision.value=draft;publishedRevision.value=published;contentForm.value=form;latestContentSnapshot.value=null;contentConflictMsg.value='';hasVersionConflict.value=false;contentGuard.reset()}else await loadCourseContent(true)})}

onMounted(async () => {
  const generation = courseLoadGeneration
  try {
    await loadTeacherList()
    if (!isCurrentCourseLoad(generation)) return
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

onUnmounted(() => { disposed=true; courseLoadGeneration++; indicatorRequest++; contentLoadSequence++; closePreview(); radarChart = null })
</script>
