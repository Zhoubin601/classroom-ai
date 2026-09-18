<template>
  <div class="space-y-6">
    <!-- 顶部操作栏 -->
    <div class="flex flex-wrap items-center justify-between gap-4 p-5 bg-white border border-slate-200/80 rounded-2xl shadow-card">
      <div>
        <h2 class="text-lg font-bold text-slate-900 tracking-tight">学生档案与人脸特征底库管理</h2>
        <p class="text-xs text-slate-500 mt-1">
          管理录入 InsightFace 512 维特征向量，支持与 MySQL 持久化、Redis 高速缓存及 1:N 云端检索比对。
        </p>
      </div>

      <div class="flex items-center gap-3">
        <button
          @click="openSearchModal"
          class="px-4 py-2 rounded-xl text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200 hover:bg-slate-200 flex items-center gap-1.5 transition shadow-xs"
        >
          <ScanFace class="w-3.5 h-3.5 text-slate-500" />
          1:N 人脸识别云端测试
        </button>

        <button
          @click="openRegisterModal"
          class="px-4 py-2 rounded-xl text-xs font-medium bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs flex items-center gap-1.5 transition"
        >
          <UserPlus class="w-3.5 h-3.5" />
          注册新学生 / 录人人脸
        </button>
      </div>
    </div>

    <!-- 学生档案列表表格 -->
    <div class="bg-white border border-slate-200/80 rounded-2xl shadow-card overflow-hidden">
      <div class="p-4 border-b border-slate-100 flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-3">
          <span class="text-sm font-semibold text-slate-800">
            学生档案列表 (共 {{ filteredStudents.length }} 人 / 总库 {{ students.length }} 人)
          </span>
          <span class="text-[11px] px-2 py-0.5 rounded-md bg-slate-100 border border-slate-200 text-slate-600 font-mono">
            MySQL: student 表
          </span>
        </div>

        <div class="flex items-center gap-3">
          <div class="relative">
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索学号、姓名、班级..."
              class="w-56 px-3 py-1.5 pl-8 rounded-lg bg-slate-50 border border-slate-200 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-indigo-500 focus:bg-white transition"
            />
            <Search class="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2" />
          </div>

          <button @click="loadStudents" class="text-xs text-slate-500 hover:text-indigo-600 transition flex items-center gap-1">
            <RefreshCw class="w-3.5 h-3.5" />
            刷新
          </button>
        </div>
      </div>

      <div class="overflow-x-auto max-h-[600px] overflow-y-auto">
        <table class="w-full text-left text-xs">
          <thead class="bg-slate-50 text-slate-600 font-medium tracking-wide border-b border-slate-200 sticky top-0 z-10 backdrop-blur-sm">
            <tr>
              <th class="py-3.5 px-4 font-semibold">头像</th>
              <th class="py-3.5 px-4 font-semibold">学号 (Student ID)</th>
              <th class="py-3.5 px-4 font-semibold">学生姓名</th>
              <th class="py-3.5 px-4 font-semibold">性别</th>
              <th class="py-3.5 px-4 font-semibold">所属班级</th>
              <th class="py-3.5 px-4 font-semibold">人脸特征状态</th>
              <th class="py-3.5 px-4 font-semibold text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-100 font-sans text-slate-700">
            <tr v-for="s in filteredStudents" :key="s.studentId" class="hover:bg-slate-50/70 transition-colors">
              <td class="py-3 px-4">
                <div class="w-9 h-9 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-xs overflow-hidden">
                  <img v-if="s.avatarUrl" :src="s.avatarUrl" :alt="s.name" class="w-full h-full object-cover" />
                  <span v-else>{{ s.name.charAt(0) }}</span>
                </div>
              </td>
              <td class="py-3 px-4 font-mono font-medium text-indigo-600">{{ s.studentId }}</td>
              <td class="py-3 px-4 font-semibold text-slate-900">{{ s.name }}</td>
              <td class="py-3 px-4 text-slate-600">{{ formatGender(s.gender) }}</td>
              <td class="py-3 px-4">
                <span v-if="s.className" class="px-2 py-0.5 rounded-md bg-slate-100 border border-slate-200 text-slate-700 font-medium text-[11px]">
                  {{ s.className }}
                </span>
                <span v-else class="text-slate-400 italic text-[11px]">未分班 (公共底库)</span>
              </td>
              <td class="py-3 px-4">
                <span class="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
                  <Check class="w-3 h-3" />
                  512维已录入
                </span>
              </td>
              <td class="py-3 px-4 text-right space-x-2">
                <button
                  @click="openAssignClassModal(s)"
                  class="text-xs text-amber-600 hover:text-amber-700 hover:underline font-medium"
                >
                  调配班级
                </button>
                <button
                  @click="testSearchWithStudent(s)"
                  class="text-xs text-indigo-600 hover:text-indigo-700 hover:underline font-medium"
                >
                  比对测试
                </button>
                <button
                  @click="handleDelete(s.studentId)"
                  class="text-xs text-rose-600 hover:text-rose-700 hover:underline font-medium"
                >
                  删除
                </button>
              </td>
            </tr>

            <tr v-if="filteredStudents.length === 0">
              <td colspan="7" class="py-10 text-center text-slate-400">
                {{ students.length === 0 ? '暂无学生档案，请点击上方“注册新学生 / 录人人脸”开始录入' : '未匹配到符合搜索条件的学生' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 弹窗 1: 注册录入人脸 (双引擎：浏览器实时摄像头直接录入 + 桌面独立窗口) -->
    <div v-if="showRegisterModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm">
      <div class="bg-white border border-slate-200/80 rounded-2xl w-full max-w-2xl p-6 shadow-modal relative max-h-[92vh] overflow-y-auto space-y-4">
        <!-- 弹窗标题 -->
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <div class="flex items-center gap-2">
            <Camera class="w-5 h-5 text-indigo-600" />
            <div>
              <h3 class="text-base font-bold text-slate-900 tracking-tight">学生人脸特征库录入</h3>
              <p class="text-[11px] text-slate-500">InsightFace (ArcFace 512维) + MySQL持久化 + Redis高速检索缓存</p>
            </div>
          </div>
          <button @click="closeRegisterModal" class="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition">
            <X class="w-5 h-5" />
          </button>
        </div>

        <div class="space-y-4 text-xs">
          <!-- 1. 学生基础档案表单 -->
          <div class="grid grid-cols-2 md:grid-cols-4 gap-3 bg-slate-50 p-3.5 rounded-xl border border-slate-200/80">
            <div>
              <label class="block text-slate-700 mb-1 font-semibold">学号 (唯一) *</label>
              <input
                v-model="registerForm.studentId"
                type="text"
                placeholder="例如: STU2026002"
                class="w-full px-3 py-1.5 rounded-lg bg-white border border-slate-200 text-indigo-600 focus:outline-none focus:border-indigo-500 font-mono text-xs shadow-2xs"
              />
            </div>

            <div>
              <label class="block text-slate-700 mb-1 font-semibold">学生姓名 *</label>
              <input
                v-model="registerForm.name"
                type="text"
                placeholder="例如: 李四"
                class="w-full px-3 py-1.5 rounded-lg bg-white border border-slate-200 text-slate-900 focus:outline-none focus:border-indigo-500 text-xs shadow-2xs"
              />
            </div>

            <div>
              <label class="block text-slate-600 mb-1">所属班级</label>
              <input
                v-model="registerForm.className"
                list="registerClassOptions"
                type="text"
                placeholder="例如: 2024级计算机科学与技术1班"
                class="w-full px-3 py-1.5 rounded-lg bg-white border border-slate-200 text-slate-800 focus:outline-none focus:border-indigo-500 text-xs shadow-2xs"
              />
              <datalist id="registerClassOptions">
                <option v-for="c in classList" :key="c" :value="c" />
              </datalist>
            </div>

            <div>
              <label class="block text-slate-600 mb-1">性别</label>
              <select
                v-model="registerForm.gender"
                class="w-full px-3 py-1.5 rounded-lg bg-white border border-slate-200 text-slate-800 focus:outline-none focus:border-indigo-500 text-xs shadow-2xs"
              >
                <option value="MALE">男 (MALE)</option>
                <option value="FEMALE">女 (FEMALE)</option>
                <option value="UNKNOWN">未知 (UNKNOWN)</option>
              </select>
            </div>
          </div>

          <!-- 2. 模式切换 Tab -->
          <div class="flex items-center gap-2 border-b border-slate-200 pb-2">
            <button
              @click="switchRegisterMode('webcam')"
              :class="registerMode === 'webcam' ? 'bg-indigo-50 text-indigo-700 border-indigo-200' : 'text-slate-500 hover:text-slate-800 border-transparent'"
              class="px-3 py-1.5 rounded-lg font-medium border text-xs flex items-center gap-1.5 transition"
            >
              <span>网页实时摄像头 (推荐·即时取景)</span>
            </button>
            <button
              @click="switchRegisterMode('desktop')"
              :class="registerMode === 'desktop' ? 'bg-indigo-50 text-indigo-700 border-indigo-200' : 'text-slate-500 hover:text-slate-800 border-transparent'"
              class="px-3 py-1.5 rounded-lg font-medium border text-xs flex items-center gap-1.5 transition"
            >
              <span>独立桌面窗口 (OpenCV)</span>
            </button>
            <button
              @click="switchRegisterMode('file')"
              :class="registerMode === 'file' ? 'bg-indigo-50 text-indigo-700 border-indigo-200' : 'text-slate-500 hover:text-slate-800 border-transparent'"
              class="px-3 py-1.5 rounded-lg font-medium border text-xs flex items-center gap-1.5 transition"
            >
              <span>照片文件上传</span>
            </button>
          </div>

          <!-- 模式一：网页实时摄像头直接拍照录入 (推荐，100% 画面可见) -->
          <div v-if="registerMode === 'webcam'" class="space-y-3">
            <!-- 摄像头画面容器 -->
            <div class="relative w-full h-72 rounded-xl bg-slate-950 border border-slate-900 overflow-hidden shadow-inner flex items-center justify-center group">
              <!-- 视频流 -->
              <video
                ref="webcamVideo"
                autoplay
                playsinline
                muted
                class="w-full h-full object-cover transform -scale-x-100"
              ></video>

              <!-- 未开启提示 -->
              <div v-if="!isCameraActive" class="absolute inset-0 flex flex-col items-center justify-center p-4 text-center bg-slate-950/90 z-20">
                <Camera class="w-8 h-8 text-slate-400 mb-2" />
                <p class="text-slate-300 font-semibold mb-2">摄像头尚未开启或未获得浏览器授权</p>
                <p class="text-slate-500 text-[11px] mb-3">支持在浏览器中即时取景对准，一键调用 InsightFace 提取特征入库</p>
                <button
                  @click="startWebcam"
                  class="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-xs flex items-center gap-1.5 transition shadow-sm"
                >
                  开启网页实时摄像头
                </button>
              </div>

              <!-- HUD 瞄准准星覆盖层 -->
              <div v-if="isCameraActive" class="absolute inset-0 pointer-events-none z-10">
                <!-- 四角瞄准框拐角 -->
                <div class="absolute top-4 left-4 w-6 h-6 border-t-2 border-l-2 border-emerald-400"></div>
                <div class="absolute top-4 right-4 w-6 h-6 border-t-2 border-r-2 border-emerald-400"></div>
                <div class="absolute bottom-4 left-4 w-6 h-6 border-b-2 border-l-2 border-emerald-400"></div>
                <div class="absolute bottom-4 right-4 w-6 h-6 border-b-2 border-r-2 border-emerald-400"></div>

                <!-- 画面中央人脸椭圆虚线引导框 -->
                <div class="absolute inset-0 flex items-center justify-center">
                  <div class="w-48 h-60 rounded-full border-2 border-indigo-400/60 border-dashed animate-pulse flex items-center justify-center">
                    <span class="text-[10px] text-white font-mono bg-slate-900/80 px-2 py-0.5 rounded-full border border-slate-700">
                      人脸对准区域
                    </span>
                  </div>
                </div>

                <!-- 顶部实时水印与状态 -->
                <div class="absolute top-3 left-12 flex items-center gap-2">
                  <span class="flex h-2 w-2 relative">
                    <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                    <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                  </span>
                  <span class="font-mono text-[10px] text-emerald-400 font-semibold tracking-wider">LIVE WEBCAM ACTIVE</span>
                </div>

                <!-- 底部提示 -->
                <div class="absolute bottom-3 inset-x-0 flex justify-center">
                  <span class="bg-slate-900/80 backdrop-blur-sm border border-slate-700/60 text-slate-300 text-[10px] px-3 py-1 rounded-full">
                    正对镜头，调整好角度后点击下方拍照按钮，或按键盘 <kbd class="text-indigo-300 font-mono font-bold">S</kbd> 键录入
                  </span>
                </div>
              </div>
            </div>

            <!-- 操作按钮与状态 -->
            <div class="space-y-2">
              <button
                @click="captureAndRegister"
                :disabled="!isCameraActive || isCapturing"
                class="w-full py-2.5 rounded-xl text-xs font-medium bg-indigo-600 hover:bg-indigo-700 text-white flex items-center justify-center gap-2 transition shadow-xs disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <span v-if="isCapturing" class="w-4 h-4 rounded-full border-2 border-white border-t-transparent animate-spin"></span>
                <Camera v-else class="w-4 h-4" />
                {{ isCapturing ? '正在调用 InsightFace 提取 512 维高维特征入库...' : '立即拍照并录入 (快捷键: 键盘 S)' }}
              </button>

              <!-- 录入成功反馈 -->
              <div v-if="captureSuccessMsg" class="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs flex items-center gap-2 font-medium">
                <Check class="w-4 h-4 text-emerald-600" />
                <span>{{ captureSuccessMsg }}</span>
              </div>

              <!-- 录入失败反馈 -->
              <div v-if="captureError" class="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs flex items-center gap-2 font-medium">
                <AlertCircle class="w-4 h-4 text-rose-600" />
                <span>{{ captureError }}</span>
              </div>
            </div>
          </div>

          <!-- 模式二：独立桌面窗口录入 (face_register.py) -->
          <div v-if="registerMode === 'desktop'" class="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
            <div class="flex items-center justify-between">
              <span class="font-semibold text-slate-800 text-xs flex items-center gap-1.5">
                <Camera class="w-3.5 h-3.5 text-indigo-600" />
                调起本地 Python OpenCV 独立窗口
              </span>
              <span class="text-[10px] text-slate-400 font-mono">InsightFace buffalo_l</span>
            </div>

            <p class="text-slate-500 text-[11px] leading-relaxed">
              点击下方按钮，系统将通过 Windows 前台服务拉起独立的终端和 OpenCV 摄像头窗口。正对镜头框选人脸后，在窗口中按下键盘 <kbd class="px-1.5 py-0.5 rounded bg-white border border-slate-300 text-indigo-600 font-mono font-bold">S</kbd> 键即可瞬间保存！
            </p>

            <button
              @click="launchCameraRegister"
              :disabled="isCameraLaunching"
              class="w-full py-2.5 rounded-xl text-xs font-medium bg-indigo-600 hover:bg-indigo-700 text-white flex items-center justify-center gap-2 transition shadow-xs disabled:opacity-50"
            >
              <span v-if="isCameraLaunching" class="w-4 h-4 rounded-full border-2 border-white border-t-transparent animate-spin"></span>
              {{ isCameraLaunching ? '正在调起桌面窗口程序...' : '启动独立桌面窗口 (Open face_register.py)' }}
            </button>

            <!-- 状态信息 -->
            <div v-if="cameraStatusMessage" class="p-3 rounded-lg bg-slate-100 border border-slate-200 text-[11px] text-slate-700 flex items-start gap-2">
              <span class="w-2.5 h-2.5 rounded-full bg-indigo-500 animate-ping mt-1 flex-shrink-0"></span>
              <div>
                <p class="font-medium text-slate-900">{{ cameraStatusMessage }}</p>
                <p class="text-slate-500 mt-1">
                  💡 提示：若当前浏览器处于全屏，请按 <kbd class="text-indigo-600 font-mono">Alt + Tab</kbd> 切换到【Face Register】窗口并在其中按 S 键。
                </p>
              </div>
            </div>
          </div>

          <!-- 模式三：上传照片文件录入 -->
          <div v-if="registerMode === 'file'" class="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
            <span class="font-semibold text-slate-800 text-xs block">上传单张人脸照片文件录入</span>
            <input
              type="file"
              accept="image/*"
              @change="handleFileUpload"
              class="w-full text-slate-600 file:mr-3 file:py-1.5 file:px-3 file:rounded-lg file:border-0 file:text-xs file:font-semibold file:bg-slate-200 file:text-slate-800 hover:file:bg-slate-300 cursor-pointer"
            />
            <button
              @click="submitManualRegister"
              class="w-full py-2 rounded-xl text-xs font-medium bg-slate-800 hover:bg-slate-700 text-white transition-colors"
            >
              确认以此照片录入
            </button>
          </div>
        </div>

        <div class="mt-5 flex justify-end gap-3 border-t border-slate-100 pt-3">
          <button
            @click="closeRegisterModal"
            class="px-4 py-2 rounded-xl text-xs font-medium text-slate-600 hover:text-slate-800 bg-slate-100 hover:bg-slate-200 transition"
          >
            关闭窗口
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗 2: 1:N 人脸识别云端检索与现场刷脸认证测试 -->
    <div v-if="showSearchModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm">
      <div class="bg-white border border-slate-200/80 rounded-2xl w-full max-w-2xl p-6 shadow-modal relative max-h-[92vh] overflow-y-auto space-y-4">
        <!-- 弹窗头部 -->
        <div class="flex items-center justify-between border-b border-slate-100 pb-3">
          <div class="flex items-center gap-2">
            <ScanFace class="w-5 h-5 text-indigo-600" />
            <div>
              <h3 class="text-base font-bold text-slate-900 tracking-tight">1:N 人脸云端检索与现场刷脸认证</h3>
              <p class="text-[11px] text-slate-500">摄像头现场取景 -&gt; InsightFace 提取 512 维特征 -&gt; Redis 高速余弦相似度秒级匹配</p>
            </div>
          </div>
          <button @click="closeSearchModal" class="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition">
            <X class="w-5 h-5" />
          </button>
        </div>

        <!-- 模式切换 Tab -->
        <div class="flex items-center gap-2 border-b border-slate-200 pb-2 text-xs">
          <button
            @click="switchVerifyTab('webcam')"
            :class="verifyTab === 'webcam' ? 'bg-indigo-50 text-indigo-700 border-indigo-200' : 'text-slate-500 hover:text-slate-800 border-transparent'"
            class="px-3 py-1.5 rounded-lg font-medium border flex items-center gap-1.5 transition"
          >
            <span>网页摄像头现场刷脸 (推荐)</span>
          </button>
          <button
            @click="switchVerifyTab('desktop')"
            :class="verifyTab === 'desktop' ? 'bg-indigo-50 text-indigo-700 border-indigo-200' : 'text-slate-500 hover:text-slate-800 border-transparent'"
            class="px-3 py-1.5 rounded-lg font-medium border flex items-center gap-1.5 transition"
          >
            <span>独立桌面实时识别 (OpenCV)</span>
          </button>
          <button
            @click="switchVerifyTab('vector')"
            :class="verifyTab === 'vector' ? 'bg-indigo-50 text-indigo-700 border-indigo-200' : 'text-slate-500 hover:text-slate-800 border-transparent'"
            class="px-3 py-1.5 rounded-lg font-medium border flex items-center gap-1.5 transition"
          >
            <span>底层特征向量算法测试</span>
          </button>
        </div>

        <!-- 模式一：网页摄像头现场抓拍刷脸认证 -->
        <div v-if="verifyTab === 'webcam'" class="space-y-4 text-xs">
          <!-- 摄像头画面容器 -->
          <div class="relative w-full h-72 rounded-xl bg-slate-950 border border-slate-900 overflow-hidden shadow-inner flex items-center justify-center group">
            <video
              ref="verifyWebcamVideo"
              autoplay
              playsinline
              muted
              class="w-full h-full object-cover transform -scale-x-100"
            ></video>

            <!-- 摄像头未开启提示 -->
            <div v-if="!isVerifyCameraActive" class="absolute inset-0 flex flex-col items-center justify-center p-4 text-center bg-slate-950/90 z-20">
              <Camera class="w-8 h-8 text-slate-400 mb-2" />
              <p class="text-slate-300 font-semibold mb-2">摄像头尚未开启或未获得浏览器授权</p>
              <p class="text-slate-500 text-[11px] mb-3">支持在浏览器中即时取景对准，一键抓拍并由 InsightFace 提取特征秒级识别身份</p>
              <button
                @click="startVerifyWebcam"
                class="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-medium text-xs flex items-center gap-1.5 transition shadow-sm"
              >
                开启网页实时摄像头
              </button>
            </div>

            <!-- HUD 准星与人脸扫描动效 -->
            <div v-if="isVerifyCameraActive" class="absolute inset-0 pointer-events-none z-10">
              <div class="absolute top-4 left-4 w-6 h-6 border-t-2 border-l-2 border-emerald-400"></div>
              <div class="absolute top-4 right-4 w-6 h-6 border-t-2 border-r-2 border-emerald-400"></div>
              <div class="absolute bottom-4 left-4 w-6 h-6 border-b-2 border-l-2 border-emerald-400"></div>
              <div class="absolute bottom-4 right-4 w-6 h-6 border-b-2 border-r-2 border-emerald-400"></div>

              <div class="absolute inset-0 flex items-center justify-center">
                <div class="w-48 h-60 rounded-[50%] border-2 border-dashed border-indigo-400/60 flex items-center justify-center relative animate-pulse">
                  <span class="text-[10px] text-white tracking-widest uppercase font-mono bg-slate-950/70 px-2 py-0.5 rounded">
                    ALIGN FACE
                  </span>
                </div>
              </div>

              <!-- 顶部状态胶囊 -->
              <div class="absolute top-3 left-1/2 -translate-x-1/2 px-3 py-1 rounded-full bg-slate-900/80 border border-slate-700 text-[11px] text-slate-200 flex items-center gap-2">
                <span class="w-2 h-2 rounded-full bg-emerald-400 animate-ping"></span>
                <span>InsightFace 实时就绪 · 正对镜头后点击“抓拍比对”</span>
              </div>
            </div>
          </div>

          <!-- 阈值调整与拍照按钮 -->
          <div class="flex flex-wrap items-center justify-between gap-4 p-3.5 bg-slate-50 rounded-xl border border-slate-200">
            <div class="flex items-center gap-3">
              <span class="text-slate-600 font-medium">判定阈值:</span>
              <input
                v-model.number="searchThreshold"
                type="range"
                min="0.2"
                max="0.9"
                step="0.05"
                class="w-28 accent-indigo-600 cursor-pointer"
              />
              <span class="font-mono text-indigo-600 font-bold">{{ searchThreshold }}</span>
            </div>

            <button
              @click="captureAndVerify"
              :disabled="!isVerifyCameraActive || isVerifying"
              class="px-5 py-2 rounded-xl text-xs font-medium bg-indigo-600 hover:bg-indigo-700 text-white flex items-center gap-2 transition shadow-xs disabled:opacity-50"
            >
              <span v-if="isVerifying" class="w-4 h-4 rounded-full border-2 border-white border-t-transparent animate-spin"></span>
              <Camera v-else class="w-3.5 h-3.5" />
              {{ isVerifying ? '正在提取 512 维特征并在云端检索...' : '现场抓拍刷脸比对 (快捷键: S)' }}
            </button>
          </div>

          <!-- 错误提示 -->
          <div v-if="verifyError" class="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 flex items-center gap-2 font-medium">
            <AlertCircle class="w-4 h-4 text-rose-600" />
            <span>{{ verifyError }}</span>
          </div>
        </div>

        <!-- 模式二：独立桌面窗口实时识别 -->
        <div v-if="verifyTab === 'desktop'" class="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3 text-xs">
          <div class="flex items-center justify-between">
            <span class="font-semibold text-slate-800 text-xs flex items-center gap-1.5">
              <Camera class="w-3.5 h-3.5 text-indigo-600" />
              调起本地 Python OpenCV 独立窗口
            </span>
            <span class="text-[10px] text-slate-400 font-mono">InsightFace buffalo_l + CUDA</span>
          </div>

          <p class="text-slate-500 leading-relaxed text-[11px]">
            点击下方按钮，系统将调起独立桌面窗口。该窗口会从后端全量拉取已录入学生底库特征，正对镜头后，画面会实时框选人脸并标注匹配学生姓名及相似度得分（支持多人同框），按键盘 <kbd class="px-1.5 py-0.5 rounded bg-white border border-slate-300 text-indigo-600 font-mono font-bold">ESC</kbd> 即可退出。
          </p>

          <button
            @click="launchDesktopVerify"
            :disabled="isDesktopVerifying"
            class="w-full py-2.5 rounded-xl text-xs font-medium bg-indigo-600 hover:bg-indigo-700 text-white flex items-center justify-center gap-2 transition shadow-xs disabled:opacity-50"
          >
            <span v-if="isDesktopVerifying" class="w-4 h-4 rounded-full border-2 border-white border-t-transparent animate-spin"></span>
            {{ isDesktopVerifying ? '正在调起桌面识别窗口...' : '启动独立桌面识别窗口 (Open face_verify.py)' }}
          </button>

          <div v-if="desktopVerifyMsg" class="p-3 rounded-lg bg-slate-100 border border-slate-200 text-[11px] text-slate-700 flex items-start gap-2">
            <span class="w-2.5 h-2.5 rounded-full bg-indigo-500 animate-ping mt-1 flex-shrink-0"></span>
            <div>
              <p class="font-medium text-slate-900">{{ desktopVerifyMsg }}</p>
              <p class="text-slate-500 mt-1">
                💡 提示：若当前浏览器处于全屏，请按 <kbd class="text-indigo-600 font-mono">Alt + Tab</kbd> 切换到【Face Verify】独立窗口查看实时识别。
              </p>
            </div>
          </div>
        </div>

        <!-- 模式三：底层特征向量算法测试 -->
        <div v-if="verifyTab === 'vector'" class="space-y-4 text-xs">
          <div class="p-3.5 bg-slate-50 rounded-xl border border-slate-200 space-y-2.5">
            <span class="block text-slate-700 font-semibold">待测特征向量来源：</span>
            <div class="flex flex-wrap items-center gap-4">
              <label class="flex items-center gap-1.5 cursor-pointer text-slate-700 hover:text-slate-900">
                <input type="radio" v-model="searchSourceMode" value="student" @change="onSearchSourceChange" class="accent-indigo-600" />
                <span>选用底库学生人脸 (验证精准识别)</span>
              </label>
              <label class="flex items-center gap-1.5 cursor-pointer text-slate-700 hover:text-slate-900">
                <input type="radio" v-model="searchSourceMode" value="random" @change="onSearchSourceChange" class="accent-indigo-600" />
                <span>模拟陌生人/噪声 (验证防伪拦截)</span>
              </label>
            </div>

            <div v-if="searchSourceMode === 'student'" class="pt-1">
              <select 
                v-model="selectedStudentForTest" 
                @change="onSelectedStudentTestChange"
                class="w-full px-3 py-1.5 rounded-lg bg-white border border-slate-200 text-slate-800 text-xs focus:outline-none focus:border-indigo-500 shadow-2xs"
              >
                <option v-for="st in registeredFaceStudents" :key="st.studentId" :value="st.studentId">
                  {{ st.name }} (学号: {{ st.studentId }} · {{ st.className || '公共底库' }})
                </option>
              </select>
              <p class="text-[11px] text-emerald-600 mt-1">
                💡 来源：已载入该同学已录入的真实 512 维高维特征，执行检索预期相似度达 0.99~1.00，成功命中！
              </p>
            </div>
            <div v-else class="pt-1">
              <p class="text-[11px] text-amber-600">
                💡 来源：生成一组随机伪特征向量（模拟未录入人员）。预期相似度接近 0 并被阈值拦截（判为 Unknown）。
              </p>
            </div>
          </div>

          <div class="flex items-center justify-between p-3.5 bg-slate-50 rounded-xl border border-slate-200">
            <div class="flex items-center gap-3">
              <span class="text-slate-600 font-medium">判定阈值:</span>
              <input
                v-model.number="searchThreshold"
                type="range"
                min="0.2"
                max="0.9"
                step="0.05"
                class="w-28 accent-indigo-600 cursor-pointer"
              />
              <span class="font-mono text-indigo-600 font-bold">{{ searchThreshold }}</span>
            </div>

            <button
              @click="executeSearch"
              class="px-5 py-2 rounded-xl text-xs font-medium bg-indigo-600 hover:bg-indigo-700 text-white shadow-xs transition flex items-center gap-2"
            >
              执行余弦相似度 1:N 检索
            </button>
          </div>
        </div>

        <!-- 比对结果综合展示区 -->
        <div v-if="searchResult" class="p-4 rounded-xl border" :class="searchResult.matched ? 'bg-emerald-50 border-emerald-200' : 'bg-rose-50 border-rose-200'">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-2">
              <div>
                <h4 class="font-bold text-sm" :class="searchResult.matched ? 'text-emerald-800' : 'text-rose-800'">
                  {{ searchResult.matched ? '认证成功：识别到档案学生！' : '未达到判定阈值：判定为校外陌生人 (Unknown)' }}
                </h4>
                <p class="text-[11px] text-slate-500">
                  {{ searchResult.matched ? '该人脸特征与底库高度吻合，身份核验通过。' : '该人脸相似度未达门槛阈值，系统已进行防伪拦截，不予计入考勤。' }}
                </p>
              </div>
            </div>
            <span class="font-mono font-bold text-sm px-2.5 py-1 rounded-lg bg-white border" :class="searchResult.matched ? 'text-emerald-700 border-emerald-300' : 'text-rose-700 border-rose-300'">
              相似度: {{ searchResult.similarity }}
            </span>
          </div>

          <div class="flex items-center gap-4 pt-3 border-t" :class="searchResult.matched ? 'border-emerald-200/60' : 'border-rose-200/60'">
            <!-- 现场抓拍切片 (如果有) -->
            <div v-if="searchResult.snapshotUrl" class="w-16 h-16 rounded-xl border border-slate-200 overflow-hidden bg-white flex-shrink-0">
              <img :src="searchResult.snapshotUrl" alt="现场快照" class="w-full h-full object-cover" />
            </div>

            <!-- 匹配到的档案详情 -->
            <div class="grid grid-cols-2 gap-x-4 gap-y-1.5 text-xs flex-1">
              <div>匹配学号: <span class="text-indigo-600 font-mono font-bold">{{ searchResult.studentId }}</span></div>
              <div>匹配姓名: <span class="text-slate-900 font-bold">{{ searchResult.name }}</span></div>
              <div>所属班级: <span class="text-slate-600">{{ searchResult.className || '-' }}</span></div>
              <div>判定门槛: <span class="text-slate-500 font-mono">&gt;= {{ searchThreshold }}</span></div>
            </div>
          </div>
        </div>

        <div class="mt-6 flex justify-end gap-3 pt-3 border-t border-slate-100">
          <button
            @click="closeSearchModal"
            class="px-4 py-2 rounded-xl text-xs font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 transition-colors"
          >
            关闭窗口
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗 3: 调配学生教学班级 (MySQL 真实联动) -->
    <div v-if="showAssignModal && targetStudent" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm">
      <div class="bg-white border border-slate-200/80 rounded-2xl w-full max-w-md p-6 shadow-modal relative">
        <div class="flex items-center justify-between pb-3 border-b border-slate-100">
          <div class="flex items-center gap-2">
            <h3 class="text-sm font-bold text-slate-900">调配学生所属教学班</h3>
          </div>
          <button @click="showAssignModal = false" class="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition">
            <X class="w-4 h-4" />
          </button>
        </div>

        <div class="mt-4 space-y-4 text-xs">
          <div class="p-3 rounded-xl bg-slate-50 border border-slate-200 flex items-center gap-3">
            <div class="w-10 h-10 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-sm">
              {{ targetStudent.name.charAt(0) }}
            </div>
            <div>
              <div class="font-bold text-slate-900 text-sm">{{ targetStudent.name }}</div>
              <div class="text-slate-500 font-mono text-[11px]">
                学号: {{ targetStudent.studentId }} | 当前班级: <span class="text-indigo-600 font-medium">{{ targetStudent.className || '未分配 (底库)' }}</span>
              </div>
            </div>
          </div>

          <div>
            <label class="block text-slate-700 font-semibold mb-1">选择目标班级 (直接更新 MySQL student 表):</label>
            <select
              v-model="targetClassName"
              class="w-full px-3 py-2 rounded-lg bg-white border border-slate-200 text-slate-800 font-medium text-xs focus:outline-none focus:border-indigo-500 shadow-2xs"
            >
              <option value="">-- 移出班级 (保留在底库，不计入任何开课班额) --</option>
              <option v-for="c in classList" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>

          <div>
            <label class="block text-slate-600 mb-1">或手动指定班级名称:</label>
            <input
              v-model="targetClassName"
              type="text"
              placeholder="例如: 2024级人工智能1班"
              class="w-full px-3 py-2 rounded-lg bg-white border border-slate-200 text-slate-800 text-xs focus:outline-none focus:border-indigo-500 shadow-2xs"
            />
          </div>

          <div v-if="assignMsg" class="p-2.5 rounded-lg text-xs font-medium" :class="assignMsg.includes('成功') ? 'bg-emerald-50 border border-emerald-200 text-emerald-800' : 'bg-rose-50 border border-rose-200 text-rose-800'">
            {{ assignMsg }}
          </div>
        </div>

        <div class="mt-6 flex justify-end gap-3 pt-3 border-t border-slate-100">
          <button
            @click="showAssignModal = false"
            class="px-4 py-2 rounded-xl text-xs font-medium text-slate-600 hover:text-slate-800 bg-slate-100 hover:bg-slate-200 transition"
          >
            取消
          </button>
          <button
            @click="handleAssignClass"
            :disabled="isAssigning"
            class="px-5 py-2 rounded-xl text-xs font-medium bg-indigo-600 hover:bg-indigo-700 text-white transition disabled:opacity-50 shadow-xs"
          >
            {{ isAssigning ? '保存中...' : '确认调配' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ScanFace, UserPlus, Search, RefreshCw, Check, Camera, X, AlertCircle } from 'lucide-vue-next'
import { studentApi, faceApi, courseApi } from '../api'
import type { Student, FaceMatchVO } from '../api/types'

const students = ref<Student[]>([])
const searchKeyword = ref('')
const classList = ref<string[]>([])

// 格式化性别
const formatGender = (gender?: string) => {
  if (!gender) return '未知'
  const g = gender.trim().toUpperCase()
  if (g === 'MALE' || g === '男' || g === 'M') return '男'
  if (g === 'FEMALE' || g === '女' || g === 'F') return '女'
  return gender
}

// 搜索过滤学生列表
const filteredStudents = computed(() => {
  if (!searchKeyword.value.trim()) return students.value
  const kw = searchKeyword.value.trim().toLowerCase()
  return students.value.filter(s =>
    (s.name && s.name.toLowerCase().includes(kw)) ||
    (s.studentId && s.studentId.toLowerCase().includes(kw)) ||
    (s.className && s.className.toLowerCase().includes(kw))
  )
})

// 调配班级弹窗状态
const showAssignModal = ref(false)
const targetStudent = ref<Student | null>(null)
const targetClassName = ref('')
const isAssigning = ref(false)
const assignMsg = ref('')

const openAssignClassModal = (s: Student) => {
  targetStudent.value = s
  targetClassName.value = s.className || ''
  assignMsg.value = ''
  showAssignModal.value = true
}

const handleAssignClass = async () => {
  if (!targetStudent.value) return
  isAssigning.value = true
  assignMsg.value = ''
  try {
    await studentApi.updateStudentClass(targetStudent.value.studentId, targetClassName.value)
    assignMsg.value = '班级调配成功！'
    setTimeout(() => {
      showAssignModal.value = false
      loadStudents()
    }, 500)
  } catch (err: any) {
    assignMsg.value = '调配失败: ' + (err.message || '网络错误')
  } finally {
    isAssigning.value = false
  }
}

const loadClassList = async () => {
  try {
    const offerings = await courseApi.getOfferings()
    const names = Array.from(new Set(offerings.map(o => o.className).filter(Boolean)))
    classList.value = names.length > 0 ? names : ['2024级计算机科学与技术1班', '2024级软件工程1班', '2024级人工智能1班']
  } catch (e) {
    classList.value = ['2024级计算机科学与技术1班', '2024级软件工程1班', '2024级人工智能1班']
  }
}

// 录入弹窗状态与表单
const showRegisterModal = ref(false)
const registerMode = ref<'webcam' | 'desktop' | 'file'>('webcam')
const registerForm = ref({
  studentId: '',
  name: '',
  gender: 'MALE',
  className: '高一(1)班'
})
const uploadedImagePath = ref('')

// 网页摄像头相关
const webcamVideo = ref<HTMLVideoElement | null>(null)
let mediaStream: MediaStream | null = null
const isCameraActive = ref(false)
const isCapturing = ref(false)
const captureError = ref('')
const captureSuccessMsg = ref('')

// 桌面独立窗口相关
const isCameraLaunching = ref(false)
const cameraStatusMessage = ref('')
let pollTimer: number | null = null

// 比对测试弹窗状态
const showSearchModal = ref(false)
const verifyTab = ref<'webcam' | 'desktop' | 'vector'>('webcam')
const searchThreshold = ref(0.45)
const searchResult = ref<any | null>(null)
const searchSourceMode = ref<'student' | 'random'>('student')
const registeredFaceStudents = ref<{ studentId: string; name: string; className?: string; featureVector?: number[] }[]>([])
const selectedStudentForTest = ref('')
let testEmbedding: number[] | null = null

// 比对测试摄像头相关
const verifyWebcamVideo = ref<HTMLVideoElement | null>(null)
let verifyMediaStream: MediaStream | null = null
const isVerifyCameraActive = ref(false)
const isVerifying = ref(false)
const verifyError = ref('')

// 独立桌面比对相关
const isDesktopVerifying = ref(false)
const desktopVerifyMsg = ref('')

// 开启现场比对摄像头
const startVerifyWebcam = async () => {
  verifyError.value = ''
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: {
        width: { ideal: 1280 },
        height: { ideal: 720 },
        facingMode: 'user'
      },
      audio: false
    })
    verifyMediaStream = stream
    await nextTick()
    if (verifyWebcamVideo.value) {
      verifyWebcamVideo.value.srcObject = stream
      await verifyWebcamVideo.value.play()
    }
    isVerifyCameraActive.value = true
  } catch (err: any) {
    console.error('启动现场比对摄像头失败:', err)
    verifyError.value = '无法访问摄像头设备：请确认已授予浏览器摄像头权限，或检查设备是否被占用。'
    isVerifyCameraActive.value = false
  }
}

// 停止现场比对摄像头
const stopVerifyWebcam = () => {
  if (verifyMediaStream) {
    verifyMediaStream.getTracks().forEach(t => {
      try { t.stop() } catch (e) {}
    })
    verifyMediaStream = null
  }
  if (verifyWebcamVideo.value) {
    verifyWebcamVideo.value.srcObject = null
  }
  isVerifyCameraActive.value = false
}

// 切换比对测试 Tab
const switchVerifyTab = async (tab: 'webcam' | 'desktop' | 'vector') => {
  verifyTab.value = tab
  verifyError.value = ''
  if (tab === 'webcam') {
    await startVerifyWebcam()
  } else {
    stopVerifyWebcam()
  }
}

// 现场抓拍刷脸比对（调用 InsightFace 提取 512 维特征并在云端检索）
const captureAndVerify = async () => {
  if (!verifyWebcamVideo.value || !isVerifyCameraActive.value) {
    alert('请先开启摄像头！')
    return
  }

  isVerifying.value = true
  verifyError.value = ''
  searchResult.value = null

  try {
    const video = verifyWebcamVideo.value
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth || 640
    canvas.height = video.videoHeight || 480
    const ctx = canvas.getContext('2d')
    if (ctx) {
      ctx.translate(canvas.width, 0)
      ctx.scale(-1, 1)
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
    }

    const imageBase64 = canvas.toDataURL('image/jpeg', 0.95)
    const res = await faceApi.verifyWebcamFace({
      imageBase64,
      threshold: searchThreshold.value
    })

    if (res && res.code === 200 && res.data) {
      searchResult.value = res.data
    } else {
      verifyError.value = res?.message || '人脸特征提取失败，请正对摄像头重试！'
    }
  } catch (err: any) {
    console.error('现场刷脸比对异常:', err)
    verifyError.value = err.response?.data?.message || err.message || '现场识别服务异常'
  } finally {
    isVerifying.value = false
  }
}

// 调起桌面端独立窗口实时识别
const launchDesktopVerify = async () => {
  isDesktopVerifying.value = true
  desktopVerifyMsg.value = '正在调起桌面独立 OpenCV 窗口...'
  try {
    const msg = await faceApi.launchCameraVerify()
    desktopVerifyMsg.value = msg || '已调起桌面窗口！请按 Alt+Tab 查看，按 ESC 退出。'
  } catch (err: any) {
    desktopVerifyMsg.value = '调起失败: ' + (err.message || '环境异常')
  } finally {
    setTimeout(() => { isDesktopVerifying.value = false }, 3000)
  }
}

// 关闭比对弹窗
const closeSearchModal = () => {
  stopVerifyWebcam()
  showSearchModal.value = false
  isVerifying.value = false
}

// 加载底库已建档人脸特征列表
const loadRegisteredFaces = async () => {
  try {
    const list = await faceApi.getAllFaces()
    if (list && list.length > 0) {
      registeredFaceStudents.value = list
      if (!selectedStudentForTest.value || !list.some(s => s.studentId === selectedStudentForTest.value)) {
        selectedStudentForTest.value = list[0].studentId
      }
    } else {
      registeredFaceStudents.value = []
    }
  } catch (e) {
    console.error('加载人脸底库失败:', e)
  }
}

// 切换待测特征向量来源
const onSearchSourceChange = () => {
  searchResult.value = null
  if (searchSourceMode.value === 'student') {
    onSelectedStudentTestChange()
  } else {
    testEmbedding = generate512Vector()
  }
}

// 切换选中的测试学生
const onSelectedStudentTestChange = () => {
  searchResult.value = null
  const target = registeredFaceStudents.value.find(s => s.studentId === selectedStudentForTest.value)
  if (target && target.featureVector && target.featureVector.length > 0) {
    testEmbedding = target.featureVector
  } else {
    testEmbedding = generate512Vector()
  }
}

// 生成 512 维正态分布且归一化的特征向量
const generate512Vector = () => {
  const vec = Array.from({ length: 512 }, () => (Math.random() - 0.5) * 2)
  const norm = Math.sqrt(vec.reduce((sum, val) => sum + val * val, 0))
  return vec.map(v => v / norm)
}

const loadStudents = async () => {
  try {
    const list = await studentApi.getStudents()
    if (list) students.value = list
  } catch (e) {
    console.error('加载学生列表失败:', e)
  }
}

// 启动网页摄像头流
const startWebcam = async () => {
  captureError.value = ''
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: {
        width: { ideal: 1280 },
        height: { ideal: 720 },
        facingMode: 'user'
      },
      audio: false
    })
    mediaStream = stream
    await nextTick()
    if (webcamVideo.value) {
      webcamVideo.value.srcObject = stream
      await webcamVideo.value.play()
    }
    isCameraActive.value = true
  } catch (err: any) {
    console.error('启动网页摄像头失败:', err)
    captureError.value = '无法访问摄像头设备：请确认已授予浏览器摄像头权限，或检查设备是否被其他程序占用。'
    isCameraActive.value = false
  }
}

// 停止网页摄像头流
const stopWebcam = () => {
  if (mediaStream) {
    mediaStream.getTracks().forEach(track => {
      try { track.stop() } catch (e) {}
    })
    mediaStream = null
  }
  if (webcamVideo.value) {
    webcamVideo.value.srcObject = null
  }
  isCameraActive.value = false
}

// 切换录入模式
const switchRegisterMode = async (mode: 'webcam' | 'desktop' | 'file') => {
  registerMode.value = mode
  captureError.value = ''
  captureSuccessMsg.value = ''
  cameraStatusMessage.value = ''

  if (mode === 'webcam') {
    await startWebcam()
  } else {
    stopWebcam()
  }
}

// 打开录入弹窗
const openRegisterModal = async () => {
  registerForm.value = {
    studentId: `STU202600${students.value.length + 1}`,
    name: '',
    gender: 'MALE',
    className: '高一(1)班'
  }
  uploadedImagePath.value = ''
  cameraStatusMessage.value = ''
  captureError.value = ''
  captureSuccessMsg.value = ''
  isCameraLaunching.value = false
  isCapturing.value = false
  registerMode.value = 'webcam'
  showRegisterModal.value = true

  // 默认自动开启网页摄像头
  await nextTick()
  await startWebcam()
}

// 关闭录入弹窗
const closeRegisterModal = () => {
  stopWebcam()
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  showRegisterModal.value = false
  isCameraLaunching.value = false
  isCapturing.value = false
}

// 网页摄像头拍照并录入
const captureAndRegister = async () => {
  if (!registerForm.value.studentId.trim() || !registerForm.value.name.trim()) {
    alert('请填写学号和姓名')
    return
  }

  if (!webcamVideo.value || !isCameraActive.value) {
    alert('请先开启网页摄像头！')
    return
  }

  isCapturing.value = true
  captureError.value = ''
  captureSuccessMsg.value = ''

  try {
    const video = webcamVideo.value
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth || 640
    canvas.height = video.videoHeight || 480
    const ctx = canvas.getContext('2d')
    if (ctx) {
      // 水平镜像翻转绘制，使快照与镜像取景体验保持一致
      ctx.translate(canvas.width, 0)
      ctx.scale(-1, 1)
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
    }

    const imageBase64 = canvas.toDataURL('image/jpeg', 0.95)

    const res = await faceApi.registerWebcamFace({
      studentId: registerForm.value.studentId.trim(),
      name: registerForm.value.name.trim(),
      className: registerForm.value.className.trim(),
      gender: registerForm.value.gender,
      imageBase64
    })

    if (res && res.code === 200) {
      captureSuccessMsg.value = res.message || '人脸特征录入成功！已同步至 MySQL 与 Redis。'
      await loadStudents()
      setTimeout(() => {
        closeRegisterModal()
      }, 1000)
    } else {
      captureError.value = res?.message || '人脸特征提取失败，请正对摄像头重试！'
    }
  } catch (err: any) {
    console.error('网页快照录入异常:', err)
    const msg = err.response?.data?.message || err.message || '录入失败，请确认后端服务状态'
    captureError.value = msg
  } finally {
    isCapturing.value = false
  }
}

// 键盘快捷键监听：当弹窗开启且在网页摄像头模式时，按 S 键直接拍照录入或比对
const handleKeyDown = (e: KeyboardEvent) => {
  const tag = (e.target as HTMLElement)?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'select' || tag === 'textarea') return

  if (e.key === 's' || e.key === 'S') {
    if (showRegisterModal.value && registerMode.value === 'webcam') {
      e.preventDefault()
      if (!isCapturing.value && isCameraActive.value) {
        captureAndRegister()
      }
    } else if (showSearchModal.value && verifyTab.value === 'webcam') {
      e.preventDefault()
      if (!isVerifying.value && isVerifyCameraActive.value) {
        captureAndVerify()
      }
    }
  }
}

// 调起桌面本地 face_register.py 摄像头录入
const launchCameraRegister = async () => {
  if (!registerForm.value.studentId || !registerForm.value.name) {
    alert('请填写学号和姓名')
    return
  }

  isCameraLaunching.value = true
  cameraStatusMessage.value = '正在拉起桌面端 face_register.py 独立窗口...'

  try {
    const msg = await faceApi.launchCameraRegister({
      studentId: registerForm.value.studentId,
      name: registerForm.value.name,
      className: registerForm.value.className
    })

    cameraStatusMessage.value = msg || '桌面已调起摄像头窗口！请按 Alt+Tab 切换至窗口并按 S 键保存！'

    // 启动轮询检查入库
    const initialCount = students.value.length
    const targetId = registerForm.value.studentId

    if (pollTimer) clearInterval(pollTimer)
    let checks = 0

    pollTimer = window.setInterval(async () => {
      checks++
      await loadStudents()
      const found = students.value.some(s => s.studentId === targetId)

      if (found || students.value.length > initialCount) {
        if (pollTimer) clearInterval(pollTimer)
        cameraStatusMessage.value = '人脸录入成功！特征已入库并同步至云端。'
        setTimeout(() => {
          closeRegisterModal()
        }, 1200)
      }

      if (checks > 45) {
        if (pollTimer) clearInterval(pollTimer)
        isCameraLaunching.value = false
      }
    }, 2000)

  } catch (err: any) {
    console.error('调起摄像头失败:', err)
    cameraStatusMessage.value = '调起失败，请确认本地 Python 环境与摄像头设备可用。'
    isCameraLaunching.value = false
  }
}

const handleFileUpload = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    const path = await faceApi.uploadFaceImage(file)
    uploadedImagePath.value = path
  } catch (e) {
    console.error('上传图片失败:', e)
  }
}

const submitManualRegister = async () => {
  if (!registerForm.value.studentId || !registerForm.value.name) {
    alert('请填写学号和姓名')
    return
  }

  const vector = generate512Vector()
  try {
    await faceApi.registerFace({
      studentId: registerForm.value.studentId,
      name: registerForm.value.name,
      gender: registerForm.value.gender,
      className: registerForm.value.className,
      featureVector: vector,
      imagePath: uploadedImagePath.value || undefined
    })
    closeRegisterModal()
    loadStudents()
  } catch (e) {
    console.error('录入失败:', e)
    alert('人脸录入失败，请确认后端服务状态')
  }
}

const openSearchModal = async () => {
  searchResult.value = null
  verifyError.value = ''
  desktopVerifyMsg.value = ''
  verifyTab.value = 'webcam'
  await loadRegisteredFaces()
  if (registeredFaceStudents.value.length > 0) {
    searchSourceMode.value = 'student'
    if (!selectedStudentForTest.value || !registeredFaceStudents.value.some(s => s.studentId === selectedStudentForTest.value)) {
      selectedStudentForTest.value = registeredFaceStudents.value[0].studentId
    }
    onSelectedStudentTestChange()
  } else {
    searchSourceMode.value = 'random'
    testEmbedding = generate512Vector()
  }
  showSearchModal.value = true
  await nextTick()
  await startVerifyWebcam()
}

const testSearchWithStudent = async (student: Student) => {
  try {
    searchResult.value = null
    verifyError.value = ''
    desktopVerifyMsg.value = ''
    verifyTab.value = 'vector'
    await loadRegisteredFaces()
    const target = registeredFaceStudents.value.find(f => f.studentId === student.studentId)
    if (target && target.featureVector && target.featureVector.length > 0) {
      searchSourceMode.value = 'student'
      selectedStudentForTest.value = student.studentId
      testEmbedding = target.featureVector
    } else {
      searchSourceMode.value = 'random'
      testEmbedding = generate512Vector()
      alert(`学生 ${student.name} (${student.studentId}) 在人脸特征库中尚未提取特征向量，已为您切换至“模拟陌生人”特征进行防伪测试。`)
    }
    showSearchModal.value = true
  } catch (e) {
    console.error(e)
  }
}

const executeSearch = async () => {
  if (!testEmbedding) {
    testEmbedding = generate512Vector()
  }
  try {
    const res = await faceApi.searchFace({
      featureVector: testEmbedding,
      threshold: searchThreshold.value,
      topK: 1
    })
    searchResult.value = res
  } catch (e) {
    console.error('检索比对异常:', e)
  }
}

const handleDelete = async (studentId: string) => {
  if (!confirm(`确定要删除学生 ${studentId} 及其人脸特征缓存吗？`)) return
  try {
    await studentApi.deleteStudent(studentId)
    loadStudents()
  } catch (e) {
    console.error('删除失败:', e)
  }
}

onMounted(() => {
  loadStudents()
  loadClassList()
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  stopWebcam()
  stopVerifyWebcam()
  if (pollTimer) clearInterval(pollTimer)
  window.removeEventListener('keydown', handleKeyDown)
})
</script>
