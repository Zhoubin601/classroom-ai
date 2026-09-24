import type { Course, UserVO, CourseOffering, OfferingDetails, OfferingInput, OfferingQuery, OfferingHistory, ScheduleInput, ContentRevisionInput, Major, Teacher, ImportPreviewVO } from './types'
import axios from 'axios'
import { checkApiResponse, rejectApiError } from './response.mjs'
import type {
  ApiResponse,
  DashboardOverviewVO,
  FocusTrendPointVO,
  StudentRealtimeStatusVO,
  Student,
  FaceRegisterDTO,
  FaceSearchDTO,
  FaceMatchVO,
  ClassroomStreamDTO,
  CourseSchedule,
  CourseContentRevision
} from './types'

// 在开发环境下通过 Vite 代理连接后端，也可直连 localhost:8080
const client = axios.create({
  baseURL: '',
  timeout: 15000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
})

client.interceptors.request.use((config) => {
  const jwtToken = localStorage.getItem('jwtToken')
  if (jwtToken && config.headers) {
    config.headers['Authorization'] = `Bearer ${jwtToken}`
  }
  const token = sessionStorage.getItem('csrfToken')
  if (token && config.headers) {
    config.headers['X-CSRF-TOKEN'] = token
  }
  return config
})

// Some legacy endpoints return HTTP 200 with a business error code.
client.interceptors.response.use(checkApiResponse, rejectApiError)

// ==================== 1. 大屏可视化 API ====================
export const visualApi = {
  // 获取大屏实时宏观看板指标
  getOverview: async (offeringId?: number): Promise<DashboardOverviewVO> => {
    const res = await client.get<ApiResponse<DashboardOverviewVO>>('/api/visual/overview', {
      params: offeringId ? { offeringId } : undefined
    })
    return res.data.data
  },

  // 获取 ECharts 抬头率与专注度时序曲线数据
  getTrend: async (): Promise<FocusTrendPointVO[]> => {
    const res = await client.get<ApiResponse<FocusTrendPointVO[]>>('/api/visual/trend')
    return res.data.data
  },

  // 获取班级学生实时状态卡片列表
  getStudentsStatus: async (offeringId?: number): Promise<StudentRealtimeStatusVO[]> => {
    const res = await client.get<ApiResponse<StudentRealtimeStatusVO[]>>('/api/visual/students/status', {
      params: offeringId ? { offeringId } : undefined
    })
    return res.data.data
  },

  // 接收 Python 视觉端/机器人上报推断流数据
  reportStream: async (data: ClassroomStreamDTO): Promise<string> => {
    const res = await client.post<ApiResponse<string>>('/api/visual/report/stream', data)
    return res.data.data
  },

  // 启动桌面端 classroom_monitor.py 视觉督导推断流
  startMonitor: async (): Promise<string> => {
    const res = await client.post<ApiResponse<any>>('/api/visual/start-monitor')
    for (let attempt = 0; attempt < 90; attempt++) {
      const status = await client.get<ApiResponse<{ running: boolean; starting: boolean; error?: string | null }>>('/api/visual/monitor-status')
      if (status.data.data?.running) return '摄像头已就绪'
      if (!status.data.data?.starting) {
        const detail = status.data.data?.error
        throw new Error(detail ? `视觉进程已退出: ${detail}` : '视觉进程已退出，请检查Python依赖、模型和摄像头')
      }
      await new Promise(resolve => setTimeout(resolve, 1000))
    }
    await client.post('/api/visual/stop-monitor')
    throw new Error('摄像头初始化超时')
  },

  // 停止桌面端视觉督导推断流并复位清理大屏缓存
  stopMonitor: async (): Promise<string> => {
    const res = await client.post<ApiResponse<any>>('/api/visual/stop-monitor')
    try {
      await client.post('/api/visual/reset')
    } catch {}
    return res.data.message || '督导已停止'
  },

  // 主动重置清理大屏实时推断缓存
  resetStream: async (offeringId?: number): Promise<void> => {
    await client.post('/api/visual/reset', null, { params: { offeringId } })
  },

  // 获取视觉督导运行状态
  getMonitorStatus: async (): Promise<boolean> => {
    try {
      const res = await client.get<ApiResponse<{ running: boolean }>>('/api/visual/monitor-status')
      return !!res.data.data?.running
    } catch {
      return false
    }
  }
}

// ==================== 2. 人脸特征库 API ====================
export const faceApi = {
  // 注册或更新学生人脸数据
  registerFace: async (dto: FaceRegisterDTO): Promise<any> => {
    const res = await client.post<ApiResponse<any>>('/api/face/register', dto)
    return res.data.data
  },

  // 全量获取所有人脸特征库
  getAllFaces: async (): Promise<FaceRegisterDTO[]> => {
    const res = await client.get<ApiResponse<FaceRegisterDTO[]>>('/api/face/all')
    return res.data.data
  },

  // 1:N 云端人脸检索比对
  searchFace: async (dto: FaceSearchDTO): Promise<FaceMatchVO> => {
    const res = await client.post<ApiResponse<FaceMatchVO>>('/api/face/search', dto)
    return res.data.data
  },

  // 上传人脸底库快照图片
  uploadFaceImage: async (file: File): Promise<string> => {
    const formData = new FormData()
    formData.append('file', file)
    const res = await client.post<ApiResponse<string>>('/api/face/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return res.data.data
  },

  // 调起桌面本地 Python face_register.py 摄像头录入进程
  launchCameraRegister: async (data: { studentId: string; name: string; className?: string }): Promise<string> => {
    const res = await client.post<ApiResponse<string>>('/api/face/launch-register', data)
    return res.data.message || '已调起摄像头'
  },

  // 网页内置摄像头快照实时录入（通过 InsightFace 提取 512 维特征并同步 MySQL + Redis）
  registerWebcamFace: async (data: { studentId: string; name: string; className?: string; gender?: string; imageBase64: string }): Promise<any> => {
    const res = await client.post<ApiResponse<any>>('/api/face/register-webcam', data, { timeout: 120000 })
    return res.data
  },

  // 网页内置摄像头快照现场刷脸认证（通过 InsightFace 提取 512 维特征并在云端 1:N 检索）
  verifyWebcamFace: async (data: { imageBase64: string; threshold?: number }): Promise<any> => {
    const res = await client.post<ApiResponse<any>>('/api/face/verify-webcam', data, { timeout: 120000 })
    return res.data
  },

  // 调起独立桌面窗口实时刷脸比对进程 (OpenCV + InsightFace)
  launchCameraVerify: async (): Promise<string> => {
    const res = await client.post<ApiResponse<string>>('/api/face/launch-verify', {})
    return res.data.message || '已调起桌面实时比对窗口'
  },

  // 删除人脸数据
  deleteFace: async (studentId: string): Promise<boolean> => {
    const res = await client.delete<ApiResponse<boolean>>(`/api/face/${studentId}`)
    return res.data.data
  }
}

// ==================== 3. 学生档案管理 API ====================
export const studentApi = {
  // 获取所有学生档案
  getStudents: async (): Promise<Student[]> => {
    const res = await client.get<ApiResponse<Student[]>>('/api/student/list')
    return res.data.data
  },

  // 获取单个学生详情
  getStudent: async (studentId: string): Promise<Student> => {
    const res = await client.get<ApiResponse<Student>>(`/api/student/${studentId}`)
    return res.data.data
  },

  // 保存或修改学生信息
  saveStudent: async (student: Partial<Student>): Promise<Student> => {
    const res = await client.post<ApiResponse<Student>>('/api/student', student)
    return res.data.data
  },

  // 删除学生档案
  deleteStudent: async (studentId: string): Promise<boolean> => {
    const res = await client.delete<ApiResponse<boolean>>(`/api/student/${studentId}`)
    return res.data.data
  },

  // 调整学生所属班级
  updateStudentClass: async (studentId: string, className: string): Promise<Student> => {
    const res = await client.put<ApiResponse<Student>>(`/api/student/${studentId}/class`, null, {
      params: { className }
    })
    return res.data.data
  }
}

// ==================== 4. 查课程与排课 API (courseApi & scheduleApi) ====================
export const courseApi = {
  // 获取所有课程或检索
  getAll: async (keyword?: string, courseType?: string): Promise<Course[]> => {
    const params: Record<string, string> = {}
    if (keyword && keyword.trim()) params.keyword = keyword.trim()
    if (courseType && courseType.trim()) params.courseType = courseType.trim()
    const res = await client.get<ApiResponse<Course[]>>('/api/v1/courses', { params })
    return res.data.data
  },

  // 获取课程详情
  getById: async (id: number): Promise<Course> => {
    const res = await client.get<ApiResponse<Course>>(`/api/v1/courses/${id}`)
    return res.data.data
  },

  // 保存课程
  save: async (data: Partial<Course>): Promise<Course> => {
    const res = await client.post<ApiResponse<Course>>('/api/v1/courses', data)
    return res.data.data
  },

  // 删除课程
  delete: async (id: number): Promise<void> => {
    await client.delete(`/api/v1/courses/${id}`)
  },

  // 导出标准化课程档案 CSV (带 UTF-8 BOM，支持当前角色管辖范围过滤)
  exportCourses: async (): Promise<Blob> => {
    const res = await client.get('/api/v1/courses/export', {
      responseType: 'blob'
    })
    return res.data
  },

  // 查询开课班次 (支持学期/教师/关键字筛选)
  getOfferings: async (term?: string, teacher?: string, keyword?: string, query: OfferingQuery = {}): Promise<CourseOffering[]> => {
    const params = { term, teacher, keyword, ...query }
    const res = await client.get<ApiResponse<CourseOffering[]>>('/api/v1/courses/offerings', { params })
    return res.data.data
  },
  archiveOffering: async (id: number): Promise<CourseOffering> => {
    const res = await client.post<ApiResponse<CourseOffering>>(`/api/v1/courses/offerings/${id}/archive`)
    return res.data.data
  },

  // 获取开课班级已有学生与候选可选学生
  getOfferingStudents: async (offeringId: number): Promise<{ enrolled: Student[]; available: Student[] }> => {
    const res = await client.get<ApiResponse<{ enrolled: Student[]; available: Student[] }>>(`/api/v1/courses/offerings/${offeringId}/students`)
    return res.data.data
  },

  // 批量选人加入班级
  addStudentsToOffering: async (offeringId: number, studentIds: string[]): Promise<CourseOffering> => {
    const res = await client.post<ApiResponse<CourseOffering>>(`/api/v1/courses/offerings/${offeringId}/students/add`, studentIds)
    return res.data.data
  },

  // 从开课班级移出学生
  removeStudentFromOffering: async (offeringId: number, studentId: string): Promise<CourseOffering> => {
    const res = await client.post<ApiResponse<CourseOffering>>(`/api/v1/courses/offerings/${offeringId}/students/remove/${studentId}`)
    return res.data.data
  },

  // 建立最小班次维护能力：创建开课班次
  getOfferingDetails: async (id: number): Promise<OfferingDetails> => {
    const res = await client.get(`/api/v1/courses/offerings/${id}`)
    return res.data.data
  },
  createOffering: async (offering: OfferingInput): Promise<CourseOffering> => {
    const res = await client.post('/api/v1/courses/offerings', offering)
    return res.data.data
  },

  // 建立最小班次维护能力：更新开课班次
  updateOffering: async (id: number, data: OfferingInput): Promise<CourseOffering> => {
    const res = await client.put<ApiResponse<CourseOffering>>(`/api/v1/courses/offerings/${id}`, data)
    return res.data.data
  },

  // 建立最小班次维护能力：删除开课班次
  deleteOffering: async (id: number): Promise<void> => {
    await client.delete(`/api/v1/courses/offerings/${id}`)
  }
}

export const scheduleApi = {
  // 获取所有排课
  getAll: async (arg1?: number | { offeringId?: number; classroom?: string; term?: string; teacher?: string; week?: number }, classroom?: string): Promise<CourseSchedule[]> => {
    let params: Record<string, any> = {}
    if (typeof arg1 === 'number') {
      params.offeringId = arg1
      if (classroom && classroom.trim()) params.classroom = classroom.trim()
    } else if (arg1 && typeof arg1 === 'object') {
      params = { ...arg1 }
    } else if (classroom && classroom.trim()) {
      params.classroom = classroom.trim()
    }
    const res = await client.get<ApiResponse<CourseSchedule[]>>('/api/v1/schedules', { params })
    return res.data.data
  },

  // 保存排课并执行冲突检测
  save: async (data: ScheduleInput): Promise<CourseSchedule> => {
    const res = await client.post<ApiResponse<CourseSchedule>>('/api/v1/schedules', data)
    return res.data.data
  },

  // 删除排课
  delete: async (id: number): Promise<void> => {
    await client.delete(`/api/v1/schedules/${id}`)
  },

  // 手动探测排课冲突
  checkConflict: async (params: { classroom: string; dayOfWeek: number; startWeek?: number; endWeek?: number; startPeriod: number; endPeriod: number; excludeId?: number }): Promise<CourseSchedule[]> => {
    const res = await client.get<ApiResponse<CourseSchedule[]>>('/api/v1/schedules/check-conflict', { params })
    return res.data.data
  }
}

export const syllabusApi = {
  // 获取课程最新大纲
  getLatest: async (courseId: number): Promise<any> => {
    const res = await client.get<ApiResponse<any>>(`/api/v1/syllabus/course/${courseId}/latest`)
    return res.data.data
  },

  // 获取当前大纲版本的毕业要求指标点映射矩阵
  getIndicators: async (courseId: number): Promise<any[]> => {
    const res = await client.get<ApiResponse<any[]>>(`/api/v1/syllabus/course/${courseId}/indicators`)
    return res.data.data
  },
  getPlanIndicators: async (majorCode: string, version: string): Promise<any[]> => {
    const res = await client.get<ApiResponse<any[]>>(`/api/v1/syllabus/plans/${encodeURIComponent(majorCode)}/${encodeURIComponent(version)}/indicators`)
    return res.data.data
  },
  importPlanIndicators: async (majorCode: string, version: string, items: any[]): Promise<any[]> => {
    const res = await client.put<ApiResponse<any[]>>(`/api/v1/syllabus/plans/${encodeURIComponent(majorCode)}/${encodeURIComponent(version)}/indicators`, items)
    return res.data.data
  },
  createFromPlan: async (courseId: number, syllabusVersion: string, planVersion: string): Promise<any> => {
    const res = await client.post<ApiResponse<any>>(`/api/v1/syllabus/course/${courseId}/from-plan`, { syllabusVersion, planVersion })
    return res.data.data
  },

  // 保存大纲与指标点矩阵
  save: async (data: any): Promise<any> => {
    const res = await client.post<ApiResponse<any>>('/api/v1/syllabus', data)
    return res.data.data
  },

  // 锁定大纲版本 (US-05)
  lock: async (syllabusId: number, lockedBy: string): Promise<any> => {
    const res = await client.post<ApiResponse<any>>(`/api/v1/syllabus/${syllabusId}/lock`, null, {
      params: { lockedBy }
    })
    return res.data.data
  },

  // 新增单个毕业要求指标点 (持久化到 MySQL)
  addIndicator: async (courseId: number, data: any): Promise<any> => {
    const res = await client.post<ApiResponse<any>>(`/api/v1/syllabus/course/${courseId}/indicators`, data)
    return res.data.data
  },

  // 修改毕业要求指标点 (更新到 MySQL)
  updateIndicator: async (id: number, data: any): Promise<any> => {
    const res = await client.put<ApiResponse<any>>(`/api/v1/syllabus/indicators/${id}`, data)
    return res.data.data
  },

  // 删除毕业要求指标点 (从 MySQL 移除)
  deleteIndicator: async (id: number): Promise<void> => {
    await client.delete<ApiResponse<any>>(`/api/v1/syllabus/indicators/${id}`)
  }
}

// ==================== 5. 教学资源与微格切片 API ====================
export const resourceApi = {
  // 检索课件与教案资源
  search: async (params: { courseId?: number; tag?: string; isPublic?: boolean; keyword?: string }): Promise<any[]> => {
    const cleanParams: Record<string, any> = {}
    if (params.courseId) cleanParams.courseId = params.courseId
    if (params.tag && params.tag.trim()) cleanParams.tag = params.tag.trim()
    if (params.isPublic !== undefined) cleanParams.isPublic = params.isPublic
    if (params.keyword && params.keyword.trim()) cleanParams.keyword = params.keyword.trim()
    const res = await client.get<ApiResponse<any[]>>('/api/v1/resources', { params: cleanParams })
    return res.data.data
  },

  // 上传真实课件文件 (PPTX/DOCX/PDF)
  uploadFile: async (file: File, data: { courseId: number; chapter: string; resourceName: string; tags: string[]; isPublic: boolean }): Promise<any> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('courseId', String(data.courseId))
    formData.append('chapter', data.chapter)
    formData.append('resourceName', data.resourceName)
    formData.append('tags', data.tags.join(','))
    formData.append('isPublic', String(data.isPublic))
    const res = await client.post<ApiResponse<any>>('/api/v1/resources/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return res.data.data
  },
  getPreviewPdf: async (id: number): Promise<Blob> => {
    const res = await client.post<ApiResponse<{url: string}>>(`/api/v1/resources/${id}/preview-ticket`)
    try {
      const pdf = await client.get<Blob>(res.data.data.url, { responseType: 'blob', timeout: 120000 })
      if (!String(pdf.headers['content-type'] || '').includes('application/pdf')) {
        throw new Error('预览服务未返回 PDF 文件')
      }
      return pdf.data
    } catch (error: any) {
      if (error.response?.data instanceof Blob && String(error.response.headers?.['content-type'] || '').includes('application/json')) {
        try {
          const body = JSON.parse(await error.response.data.text())
          throw new Error(body.message || '预览失败')
        } catch (parseError) {
          if (parseError instanceof SyntaxError) throw error
          throw parseError
        }
      }
      throw error
    }
  },
  download: async (id: number): Promise<Blob> => {
    const res = await client.get(`/api/v1/resources/${id}/download`, { responseType: 'blob' })
    return res.data
  },

  // 保存课件资源 (100MB 限制与水印)
  save: async (data: any): Promise<any> => {
    const res = await client.post<ApiResponse<any>>('/api/v1/resources', data)
    return res.data.data
  },

  // 删除资源
  delete: async (id: number): Promise<void> => {
    await client.delete(`/api/v1/resources/${id}`)
  },

  // 获取课程微格教学切片 (US-12)
  getMicroSlices: async (courseId: number): Promise<any[]> => {
    const res = await client.get<ApiResponse<any[]>>(`/api/v1/resources/micro-slices/course/${courseId}`)
    return res.data.data
  },

  // 挂载微格切片元数据
  mountMicroSlice: async (data: any): Promise<any> => {
    const res = await client.post<ApiResponse<any>>('/api/v1/resources/micro-slices', data)
    return res.data.data
  }
}

// ==================== 6. 善督导与综合分析 API ====================
export const supervisionApi = {
  // 获取所有评价或按教师查询
  getAll: async (params?: { offeringId?: number; teacherName?: string }): Promise<any[]> => {
    const res = await client.get<ApiResponse<any[]>>('/api/v1/supervisions', { params })
    return res.data.data
  },

  // 提交 BOPPPS 评价或暂存草稿
  submit: async (data: any): Promise<any> => {
    const res = await client.post<ApiResponse<any>>('/api/v1/supervisions', data)
    return res.data
  },
  getWeights: async (): Promise<{attitude: number; content: number; method: number; effect: number}> => {
    const res = await client.get<ApiResponse<{attitude: number; content: number; method: number; effect: number}>>('/api/v1/supervisions/weights')
    return res.data.data
  },
  review: async (id: number, approved: boolean, note = ''): Promise<any> => {
    const res = await client.post<ApiResponse<any>>(`/api/v1/supervisions/${id}/review`, { approved, note })
    return res.data.data
  },
  getCoverage: async (term?: string): Promise<any[]> => {
    const res = await client.get<ApiResponse<any[]>>('/api/v1/supervisions/analytics/coverage', { params: term ? { term } : undefined })
    return res.data.data
  },

  // 全院覆盖率动态监控仪表盘 (US-15)
  getDashboard: async (term?: string): Promise<any> => {
    const res = await client.get<ApiResponse<any>>('/api/v1/supervisions/analytics/dashboard', { params: term ? { term } : undefined })
    return res.data.data
  },

  // 预警中心：红黄预警清单 (US-16)
  getAlerts: async (): Promise<any[]> => {
    const res = await client.get<ApiResponse<any[]>>('/api/v1/supervisions/analytics/alerts')
    return res.data.data
  },

  // 任课教师教学质量 4 维雷达图 (US-17)
  getRadar: async (teacherName: string): Promise<any> => {
    const res = await client.get<ApiResponse<any>>('/api/v1/supervisions/analytics/radar', {
      params: { teacherName }
    })
    return res.data.data
  },

  // 导出年度质量分析 CSV 报表下载链接
  getExportReportUrl: (): string => {
    return '/api/v1/supervisions/analytics/export-report'
  },

  // 导出年度质量分析 CSV 报表（携带 JWT 鉴权头并下载 Blob）
  exportReport: async (): Promise<Blob> => {
    const res = await client.get('/api/v1/supervisions/analytics/export-report', {
      responseType: 'blob'
    })
    return res.data
  }
}

// ==================== 7. 智能考勤与教务联动 API ====================
export const attendanceApi = {
  // 启动考勤
  start: async (data: { offeringId: number; weekNumber?: number; classroom?: string; operatorName?: string; operatorRole?: string; operatorTitle?: string }): Promise<any> => {
    const res = await client.post<ApiResponse<any>>('/api/v1/attendance/start', data)
    return res.data.data
  },

  // 结束下课并归档
  finish: async (data: { sessionId: number; actualCount?: number; avgLookupRate?: number; absentStudentIds?: string[]; operatorName?: string; operatorRole?: string; operatorTitle?: string }): Promise<any> => {
    const res = await client.post<ApiResponse<any>>('/api/v1/attendance/finish', data)
    return res.data.data
  },

  // 获取当前正在进行的考勤
  getCurrent: async (): Promise<any> => {
    const res = await client.get<ApiResponse<any>>('/api/v1/attendance/current')
    return res.data.data
  },

  // 获取某门课历史考勤记录
  getByOffering: async (offeringId: number): Promise<any[]> => {
    const res = await client.get<ApiResponse<any[]>>(`/api/v1/attendance/offering/${offeringId}`)
    return res.data.data
  }
}

// ==================== 8. 身份认证与会话 API ====================
export const authApi = {
  login: async (dto: { username: string; password: string }): Promise<any> => {
    const res = await client.post<ApiResponse<any>>('/api/v1/auth/login', dto)
    if (res.data.data?.token) {
      localStorage.setItem('jwtToken', res.data.data.token)
    }
    return res.data.data
  },
  registerSupervisor: async (dto: {
    username: string
    password: string
    realName: string
    department?: string
    authorizedMajors?: string
  }): Promise<any> => {
    const res = await client.post<ApiResponse<any>>('/api/v1/auth/register-supervisor', dto)
    if (res.data.data?.token) {
      localStorage.setItem('jwtToken', res.data.data.token)
    }
    return res.data.data
  },
  logout: async (): Promise<void> => {
    try {
      await client.post('/api/v1/auth/logout')
    } catch {}
    localStorage.removeItem('jwtToken')
    sessionStorage.removeItem('csrfToken')
  },
  getMe: async (): Promise<UserVO> => {
    const res = await client.get<ApiResponse<UserVO>>('/api/v1/auth/me')
    return res.data.data
  },
  getCsrf: async (): Promise<{ csrfToken: string }> => {
    const res = await client.get<ApiResponse<{ csrfToken: string }>>('/api/v1/auth/csrf')
    if (res.data.data?.csrfToken) {
      sessionStorage.setItem('csrfToken', res.data.data.csrfToken)
    }
    return res.data.data
  }
}

// ==================== 9. 课程内容草稿与发布 API (US-02) ====================
export const courseContentApi = {
  getDraft: async (courseId: number): Promise<CourseContentRevision> => {
    const res = await client.get<ApiResponse<CourseContentRevision>>(`/api/v1/courses/${courseId}/content/draft`)
    return res.data.data
  },
  saveDraft: async (courseId: number, dto: ContentRevisionInput): Promise<CourseContentRevision> => {
    const res = await client.put<ApiResponse<CourseContentRevision>>(`/api/v1/courses/${courseId}/content/draft`, dto)
    return res.data.data
  },
  publish: async (courseId: number, dto: ContentRevisionInput): Promise<CourseContentRevision> => {
    const res = await client.post<ApiResponse<CourseContentRevision>>(`/api/v1/courses/${courseId}/content/publish`, dto)
    return res.data.data
  },
  getPublished: async (courseId: number): Promise<CourseContentRevision | null> => {
    const res = await client.get<ApiResponse<CourseContentRevision | null>>(`/api/v1/courses/${courseId}/content/published`)
    return res.data.data
  }
}

// ==================== 10. 课程两阶段批量导入 API (US-01) ====================
export const courseImportApi = {
  downloadTemplate: async (): Promise<Blob> => {
    const res = await client.get('/api/v1/courses/import/template', {
      responseType: 'blob'
    })
    return res.data
  },
  preview: async (file: File): Promise<ImportPreviewVO> => {
    const formData = new FormData()
    formData.append('file', file)
    const res = await client.post<ApiResponse<ImportPreviewVO>>('/api/v1/courses/import/preview', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return res.data.data
  },
  confirm: async (batchId: string): Promise<{ importedCount: number; message?: string }> => {
    const res = await client.post<ApiResponse<{ importedCount: number; message?: string }>>('/api/v1/courses/import/confirm', { batchId })
    return res.data.data
  }
}

// ==================== 11. 专业与教师字典 API ====================
export const majorApi = {
  getAll: async (): Promise<Major[]> => {
    const res = await client.get<ApiResponse<Major[]>>('/api/v1/majors')
    return res.data.data
  }
}

export const teacherApi = {
  getAll: async (): Promise<Teacher[]> => {
    const res = await client.get<ApiResponse<Teacher[]>>('/api/v1/teachers')
    return res.data.data
  }
}

// ==================== 12. 历史开课与学生人次统计 API (US-04) ====================
export const offeringHistoryApi = {
  getHistory: async (term?: string): Promise<OfferingHistory> => {
    const res = await client.get<ApiResponse<OfferingHistory>>('/api/v1/courses/offerings/history', {
      params: term ? { term } : undefined
    })
    return res.data.data
  }
}

// ==================== 13. 教研室主任督导管理 API (US-07) ====================
export const directorApi = {
  // 获取当前主任管辖专业列表
  getManagedMajors: async (): Promise<Major[]> => {
    const res = await client.get<ApiResponse<Major[]>>('/api/v1/director/managed-majors')
    return res.data.data
  },
  // 获取所有督导专家列表
  getSupervisors: async (): Promise<UserVO[]> => {
    const res = await client.get<ApiResponse<UserVO[]>>('/api/v1/director/supervisors')
    return res.data.data
  },
  // 主任创建督导专家账号并授权管辖专业 (限定管辖专业)
  createSupervisor: async (dto: {
    username: string
    password: string
    realName: string
    department?: string
    authorizedMajors?: string
  }): Promise<UserVO> => {
    const res = await client.post<ApiResponse<UserVO>>('/api/v1/director/supervisors', dto)
    return res.data.data
  },
  // 主任更新督导专业授权 (限定管辖专业)
  updateSupervisorMajors: async (id: number, authorizedMajors: string): Promise<UserVO> => {
    const res = await client.put<ApiResponse<UserVO>>(`/api/v1/director/supervisors/${id}/majors`, {
      authorizedMajors
    })
    return res.data.data
  }
}


