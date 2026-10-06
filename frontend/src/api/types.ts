export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface DashboardOverviewVO {
  totalRegistered: number
  currentPresent: number
  currentAbsent: number
  attendanceRate: number
  realtimeLookupRate: number
  lookdownCount: number
  focusLevel: string
  auditingCount?: number
  auditingStudentIds?: string[]
  lastUpdateTime: string
}

export interface FocusTrendPointVO {
  time: string
  lookupRate: number
  presentCount: number
}

export interface StudentRealtimeStatusVO {
  studentId: string
  name: string
  className: string
  avatarUrl: string | null
  present: boolean
  poseState: 'UP' | 'DOWN' | 'ABSENT' | string
  isAuditing?: boolean
  lastSeenTime: string
}

export interface Student {
  id?: number
  studentId: string
  name: string
  gender?: string
  className?: string
  avatarUrl?: string
  createdAt?: string
  updatedAt?: string
}

export interface FaceRegisterDTO {
  studentId: string
  name: string
  gender?: string
  className?: string
  featureVector: number[]
  imagePath?: string
}

export interface FaceSearchDTO {
  featureVector: number[]
  threshold?: number
  topK?: number
}

export interface FaceMatchVO {
  studentId: string
  name: string
  className: string
  avatarUrl: string | null
  similarity: number
  matched: boolean
}

export interface ClassroomStreamDTO {
  sessionId: string
  offeringId?: number
  courseName?: string
  className?: string
  detectedPersonCount: number
  lookupCount: number
  lookdownCount: number
  lookupRate: number
  presentStudentIds: string[]
  studentPoses: Record<string, string>
}

// ================= 爱教学业务领域类型 =================

export interface Course {
  id: number
  courseCode: string
  courseName: string
  teacherName?: string
  department: string
  majorCode?: string
  credits: number
  hours: number
  theoryHours?: number
  practiceHours?: number
  courseType: string
  prerequisites?: string
  description?: string
  objectives?: string
  assessmentMethod?: string
}

export interface CourseOffering {
  teachers?: OfferingTeacher[]
  majorId?: number
  isSnapshotFrozen?: boolean
  snapshotStudentCount?: number
  archivedAt?: string
  archivedBy?: string
  id: number
  course: Course
  academicTerm: string
  teacherName: string
  teacherCode?: string
  className: string
  majorCode?: string
  studentCount: number
  status: string
}

export interface CourseSchedule {
  conflictReasons?: string[]
  id: number
  offering: CourseOffering
  classroom: string
  weekRange: string
  startWeek: number
  endWeek: number
  dayOfWeek: number
  startPeriod: number
  endPeriod: number
}

export interface GraduationIndicator {
  id?: number
  indicatorCode: string
  requirementCategory: string
  indicatorDescription: string
  supportWeight: string
  targetGoal?: string
}

export interface CourseSyllabus {
  id: number
  course: Course
  version: string
  status: string
  authorTeacher?: string
  lockedBy?: string
  courseGoals?: string
}

  export interface CourseResource {
  id: number
  course: Course
  chapter: string
  resourceName: string
  fileType: string
  fileUrl: string
  fileSize?: string
  fileSizeBytes?: number
    tag: string
    tags?: string[]
  version: string
  isPublic: boolean
  dynamicWatermark?: string
  uploaderTeacher?: string
  createdAt?: string
}

export interface MicroTeachingSlice {
  id: number
  course: Course
  videoTitle: string
  bopppsStage: string
  durationSeconds: number
  sliceUrl: string
  coverUrl?: string
  recordedDate?: string
  classroom?: string
  sourceAgent?: string
}

export interface SupervisionEvaluation {
  id: number
  offering: CourseOffering
  supervisorName: string
  evaluateDate: string
  listenTopic: string
  scoreAttitude: number
  scoreContent: number
  scoreMethod: number
  scoreEffect: number
  totalScore: number
  highlights?: string
  suggestions?: string
  status: 'DRAFT' | 'PENDING_DESENSITIZE' | 'PUBLISHED' | string
  submitTime?: string
  publishTime?: string
}

  export interface SupervisionDashboardVO {
    academicTerm?: string
  totalCourses: number
  supervisedCourses: number
  coverageRate: number
  totalEvaluations: number
  collegeAvgScore: number
  pendingCourses: number
}

export interface SupervisionAlertVO {
  courseId: number
  courseCode: string
  courseName: string
  teacherName: string
  department: string
  alertLevel: 'YELLOW' | 'RED' | string
  alertType: string
  alertMessage: string
  currentScore: number
  evaluationCount: number
  suggestAction: string
}

export interface TeacherQualityRadarVO {
  teacherName: string
  courseName: string
  evaluationCount: number
  overallScore: number
  attitudeScore: number
  contentScore: number
  methodScore: number
  effectScore: number
  highlightList: string[]
  suggestionList: string[]
  wordCloud: Array<{ name: string; value: number }>
}

export interface AttendanceSession {
  id: number
  offering: CourseOffering
  weekNumber: number
  classroom: string
  expectedCount: number
  actualCount: number
  attendanceRate: number
  avgLookupRate: number
  status: 'ACTIVE' | 'FINISHED' | string
  startTime?: string
  endTime?: string
  absentStudentIds?: string
}

export interface UserVO {
  id: number
  username: string
  realName: string
  role: 'DIRECTOR' | 'TEACHER' | 'SUPERVISOR'
  department?: string
  teacherCode?: string
  authorizedMajors?: string
}

export interface Major {
  id: number
  majorCode: string
  majorName: string
  department?: string
}

export interface Teacher {
  id: number
  teacherCode: string
  teacherName: string
  department?: string
  title?: string
}

export interface ImportRowError {
  rowNumber: number
  field: string
  reason: string
}

export interface CourseImportRowDTO {
  rowNumber: number
  courseCode: string
  courseName: string
  department: string
  majorCode: string
  credits: number
  hours: number
  theoryHours?: number
  practiceHours?: number
  courseType: string
  prerequisites?: string
  description?: string
}

export interface ImportPreviewVO {
  batchId: string
  totalCount: number
  successCount: number
  errorCount: number
  errors: ImportRowError[]
  validRows: CourseImportRowDTO[]
}

export interface CourseContentRevision {
  id: number
  courseId: number
  description?: string
  assessmentMethod?: string
  objectives?: string
  publishVersion?: number
  lockVersion?: number
  version?: number
  status: 'DRAFT' | 'PUBLISHED'
  editorName?: string
  publisherName?: string
  publisherCode?: string
  publishedAt?: string
  createdAt?: string
  updatedAt?: string
}


export interface OfferingTeacher { id?: number; offeringId?: number; teacherId: number; teacherCode: string; teacherName: string; roleInOffering: 'PRIMARY' | 'ASSISTANT' }
export interface OfferingInput { courseId: number; academicTerm: string; className: string; primaryTeacherId: number; teacherIds: number[]; studentIds: number[] }
export interface OfferingDetails { offering: CourseOffering; studentIds: number[]; studentNumbers: string[] }
export interface OfferingQuery { term?: string; teacher?: string; keyword?: string; majorCode?: string; majorId?: number; teacherId?: number }
export interface OfferingHistoryItem { offeringId: number; courseCode: string; courseName: string; academicTerm: string; primaryTeacher: string; teachers: string[]; className: string; classroom: string; studentCount: number; status: string; isSnapshotFrozen: boolean; archivedAt?: string; archivedBy?: string }
export interface OfferingHistory { term?: string; totalOfferings: number; cumulativePersonTimes: number; items: OfferingHistoryItem[] }
export interface ScheduleInput { id?: number; offeringId: number; classroom: string; dayOfWeek: number; startWeek: number; endWeek: number; startPeriod: number; endPeriod: number }
export interface ContentRevisionInput { draftId: number; lockVersion: number; publishVersion: number; description?: string; assessmentMethod?: string; objectives?: string }
