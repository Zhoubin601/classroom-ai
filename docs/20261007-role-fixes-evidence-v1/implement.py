from pathlib import Path
root = Path(__file__).resolve().parents[2]
p = root / 'backend/src/main/java/com/classroom/ai/service/impl/VisualDashboardServiceImpl.java'
s = p.read_text(encoding='utf-8')
s = s.replace('import org.springframework.stereotype.Service;', 'import org.springframework.stereotype.Service;\nimport com.classroom.ai.modules.course.service.CourseAuthorizationService;\nimport org.springframework.transaction.annotation.Transactional;\nimport org.springframework.transaction.annotation.Isolation;')
s = s.replace('private final AttendanceSessionRepository attendanceSessionRepository;', 'private final CourseAuthorizationService authorization;\n    @jakarta.persistence.PersistenceContext\n    private jakarta.persistence.EntityManager entityManager;')
s = s.replace('AttendanceSessionRepository attendanceSessionRepository) {', 'AttendanceSessionRepository attendanceSessionRepository,\n                                       CourseAuthorizationService authorization) {')
s = s.replace('this.attendanceSessionRepository = attendanceSessionRepository;', 'this.authorization = authorization;')
start = s.index('    public VisualDashboardServiceImpl(', s.index('this.authorization = authorization;'))
end = s.index('    private static final String KEY_REALTIME_OVERVIEW', start)
s = s[:start] + s[end:]
start = s.index('            if (offeringId != null) {', s.index('private boolean isStreamActive'))
end = s.index('            return true;', start)
s = s[:start] + s[end:]
s = s.replace('    public void processClassroomStream(ClassroomStreamDTO streamDTO) {\n        if (streamDTO == null) return;', '    @Transactional(isolation = Isolation.READ_COMMITTED)\n    public void processClassroomStream(ClassroomStreamDTO streamDTO) {\n        if (streamDTO == null) throw new IllegalArgumentException("推流数据不能为空");\n        Long offeringId = streamDTO.getOfferingId();\n        CourseOffering offering = authorizedOffering(offeringId, true);')
start = s.index('        // 1. 确定当前推断流归属')
end = s.index('        // 2. 获取本班正式选课', start)
s = s[:start] + s[end:]
start = s.index('        if (enrolledStudentIds.isEmpty() && offeringId == null')
end = s.index('        // 3.', start)
s = s[:start] + s[end:]
start = s.index('        int total = 0;', s.index('public DashboardOverviewVO getOverview(Long'))
end = s.index('        // 核心时效性校验', start)
s = s[:start] + '        authorizedOffering(offeringId, false);\n        int total = Math.toIntExact(enrollmentRepository.countByOfferingId(offeringId));\n\n' + s[end:]
s = s.replace('    public List<FocusTrendPointVO> getTrend() {\n        if (!isStreamActive(null)) {', '    public List<FocusTrendPointVO> getTrend() {\n        return getTrend(null);\n    }\n\n    @Override\n    public List<FocusTrendPointVO> getTrend(Long offeringId) {\n        authorizedOffering(offeringId, false);\n        if (!isStreamActive(offeringId)) {')
s = s.replace('    public List<StudentRealtimeStatusVO> getStudentsRealtimeStatus(Long offeringId) {', '    public List<StudentRealtimeStatusVO> getStudentsRealtimeStatus(Long offeringId) {\n        authorizedOffering(offeringId, false);')
s = s.replace('        } else {\n            students = studentRepository != null ? studentRepository.findAll() : Collections.emptyList();\n        }', '        }')
start = s.index('            Optional<Student> studentOpt = studentRepository.findByStudentId(aid);')
end = s.index('            Object poseVal', start)
s = s[:start] + '            // 未选课身份只展示识别编号，不能借上报学号查询其他班学生档案。\n            String name = "旁听/未知学生 (" + aid + ")";\n            String className = "非本班/旁听";\n            String avatar = null;\n\n' + s[end:]
s = s.replace('    public void clearRealtimeStreamData(Long offeringId) {\n        try {', '    @Transactional(isolation = Isolation.READ_COMMITTED)\n    public void clearRealtimeStreamData(Long offeringId) {\n        authorizedOffering(offeringId, true);\n        try {')
# All realtime keys are tied to the explicit, authorized offering, including reset and trend.
import re
s = re.sub(r'(?<!String )(KEY_[A-Z_]+)(?![A-Z_])(?!\s*=)', r'key(\1, offeringId)', s)
s = s.replace('.courseName(streamDTO.getCourseName() != null ? streamDTO.getCourseName() : "智能课堂分析")', '.courseName(offering.getCourse().getCourseName())')
s = s.replace('.className(streamDTO.getClassName() != null ? streamDTO.getClassName() : "软件工程班级")', '.className(offering.getClassName())')
pos = s.index('    private boolean isStreamActive')
s = s[:pos] + '''    private static String key(String base, Long offeringId) {
        return base + ":" + offeringId;
    }

    private CourseOffering authorizedOffering(Long offeringId, boolean write) {
        authorization.requireCurrentUser();
        if (offeringId == null || offeringId <= 0) {
            throw new IllegalArgumentException("请明确指定开课班次 offeringId");
        }
        CourseOffering offering = (write ? courseOfferingRepository.findForUpdate(offeringId)
                : courseOfferingRepository.findById(offeringId))
                .orElseThrow(() -> new IllegalArgumentException("未找到开课班次: " + offeringId));
        // Refresh after acquiring the same row lock used by archive, including an entity
        // previously loaded by OpenEntityManagerInView. Freeze and stream writes serialize.
        if (write && entityManager != null) entityManager.refresh(offering);
        authorization.validateOfferingRead(offering);
        if (write && Boolean.TRUE.equals(offering.getIsSnapshotFrozen())) {
            throw new IllegalStateException("历史班次已冻结，不能修改实时考勤数据");
        }
        return offering;
    }

''' + s[pos:]
p.write_text(s, encoding='utf-8')
p=root/'backend/src/main/java/com/classroom/ai/service/VisualDashboardService.java'
s=p.read_text(encoding='utf-8').replace('List<FocusTrendPointVO> getTrend();', 'List<FocusTrendPointVO> getTrend();\n    List<FocusTrendPointVO> getTrend(Long offeringId);')
p.write_text(s,encoding='utf-8')
# Frontend controls and simulation lifecycle use the same frozen/role boundaries.
p=root/'frontend/src/views/AttendanceDashboardView.vue'
s=p.read_text(encoding='utf-8')
s=s.replace('const isSimulating = ref(false)', 'const isSimulating = ref(false)\nconst isSimulationStarting = ref(false)\nlet simulationGeneration = 0\nconst stopSimulation = () => {\n  simulationGeneration++\n  if (simulationTimer) clearInterval(simulationTimer)\n  simulationTimer = null\n  isSimulating.value = false\n  isSimulationStarting.value = false\n}')
s=s.replace('const activeOfferingList = computed(() => offeringList.value)', '''const activeOfferingList = computed(() => offeringList.value)
const selectedOffering = computed(() => offeringList.value.find(o => o.id === selectedOfferingId.value))
const canWriteAttendance = computed(() => !!selectedOffering.value && !selectedOffering.value.isSnapshotFrozen)
const canManageRoster = computed(() => currentUser.value?.role === 'DIRECTOR' && canWriteAttendance.value)''')
s=s.replace('({{ off.studentCount }}人)', '({{ off.studentCount }}人){{ off.isSnapshotFrozen ? " · 已冻结，只读" : "" }}')
s=s.replace('班级成员选拔 ({{ overview.totalRegistered }}人)', '{{ canManageRoster ? "班级成员选拔" : "班级学生名单" }} ({{ overview.totalRegistered }}人)')
s=s.replace(":title=\"selectedOfferingId ? '查看班级花名册或从总库选入/移出学生' : '当前无正在授课班级'\"", ':title="canManageRoster ? \'查看和维护班级学生名单\' : \'查看本班已选学生\'"')
s=s.replace(':disabled="!selectedOfferingId || isMonitorStarting"', ':disabled="!canWriteAttendance || isMonitorStarting || isSimulationStarting || isSimulating"')
s=s.replace(':disabled="!selectedOfferingId || (!currentSessionId', ':disabled="!canWriteAttendance || isMonitorStarting || isSimulationStarting || (!currentSessionId')
s=s.replace('selectedOfferingId && (currentSessionId', 'canWriteAttendance && (currentSessionId')
s=s.replace(':disabled="!selectedOfferingId || isMonitoring"', ':disabled="!canWriteAttendance || isMonitoring || isMonitorStarting || isSimulationStarting"')
s=s.replace("            !selectedOfferingId\n              ? 'bg-slate-100", "            !canWriteAttendance\n              ? 'bg-slate-100")
s=s.replace(":title=\"!selectedOfferingId ? '当前无正在授课班级，无法开启课堂考勤' : ''\"", ':title="!canWriteAttendance ? \'请选择未冻结的授课班级\' : \'\'"')
s=s.replace(":title=\"!selectedOfferingId ? '当前无正在授课班级' : ''\"", ':title="!canWriteAttendance ? \'请选择未冻结的授课班级\' : \'\'"')
s=s.replace("{{ isSimulating ? '暂停模拟' : '演示模拟流' }}", "{{ isSimulationStarting ? '正在启动模拟...' : isSimulating ? '暂停模拟' : '演示模拟流' }}")
s=s.replace('班级学生花名册与选拔管理', '{{ canManageRoster ? "班级学生花名册与选拔管理" : "班级学生花名册" }}')
s=s.replace('从人脸底库中自由挑选学生加入本课程班级，或移出班级。数据实时与后端数据库严格同步。', '{{ canManageRoster ? "选择学生加入本班，或将学生移出本班。" : "查看本班已选学生；名单由教研室主任维护。" }}')
s=s.replace('<button\n            @click="activeModalTab = \'add\'"', '<button\n            v-if="canManageRoster"\n            @click="activeModalTab = \'add\'"')
s=s.replace('当前班级暂无学生，请切换至【从总档案库选入学生】进行挑选', '{{ canManageRoster ? "当前班级暂无学生，可从总档案库选入学生。" : "当前班级暂无已选学生。" }}')
s=s.replace('<button\n              @click="handleRemoveStudentFromClass', '<button\n              v-if="canManageRoster"\n              @click="handleRemoveStudentFromClass')
s=s.replace('v-if="activeModalTab === \'add\'"', 'v-if="canManageRoster && activeModalTab === \'add\'"')
s=s.replace('  currentSessionId.value = null\n  archivedSessions.value = []', '  stopSimulation()\n  showStudentModal.value = false\n  currentSessionId.value = null\n  archivedSessions.value = []')
s=s.replace('    await visualApi.resetStream(offeringId)', '    if (canWriteAttendance.value) await visualApi.resetStream(offeringId)')
s=s.replace('visualApi.getTrend(),','visualApi.getTrend(offeringId),')
s=s.replace('const toggleMonitor = async () => {\n  if (!selectedOfferingId.value) return', 'const toggleMonitor = async () => {\n  if (!canWriteAttendance.value || isMonitorStarting.value || isSimulationStarting.value || isSimulating.value) return')
s=s.replace('  if (!selectedOfferingId.value || selectedStudentsToAdd.value.length === 0) return', '  if (!canManageRoster.value || !selectedOfferingId.value || selectedStudentsToAdd.value.length === 0) return')
s=s.replace('const handleRemoveStudentFromClass = async (studentId: string, studentName: string) => {\n  if (!selectedOfferingId.value) return', 'const handleRemoveStudentFromClass = async (studentId: string, studentName: string) => {\n  if (!canManageRoster.value || !selectedOfferingId.value) return')
s=s.replace('const finishAndArchiveAttendance = async () => {\n  if (!selectedOfferingId.value) return', 'const finishAndArchiveAttendance = async () => {\n  if (!canWriteAttendance.value || !selectedOfferingId.value) return')
start=s.index('const toggleSimulation = async () => {')
end=s.index('\nconst onVideoFeedError',start)
s=s[:start]+'''const toggleSimulation = async () => {
  if (isSimulating.value) { stopSimulation(); return }
  if (!canWriteAttendance.value || !selectedOfferingId.value || isSimulationStarting.value || isMonitorStarting.value || isMonitoring.value) return
  const offeringId = selectedOfferingId.value
  const generation = ++simulationGeneration
  const stillSelected = () => generation === simulationGeneration && selectedOfferingId.value === offeringId
  isSimulationStarting.value = true
  try {
    if (!currentSessionId.value) {
      const session = await attendanceApi.start({ offeringId, weekNumber: 2, classroom: getCurrentClassroom() })
      if (!stillSelected()) return
      if (!session?.id) throw new Error('未能建立考勤会话')
      currentSessionId.value = session.id
    }
    const roster = await courseApi.getOfferingStudents(offeringId)
    if (!stillSelected()) return
    const enrolledPresent = (roster.enrolled || []).slice(0, 4).map((s: any) => s.studentId)
    const simulatedPresent = [...enrolledPresent, '20249999']
    const offering = selectedOffering.value
    isSimulating.value = true
    let reporting = false
    simulationTimer = window.setInterval(async () => {
      if (!stillSelected() || reporting) return
      reporting = true
      try {
        const detected = simulatedPresent.length
        const lookup = Math.floor(detected * (0.8 + Math.random() * 0.15))
        const poses: Record<string, string> = {}
        simulatedPresent.forEach(id => { poses[id] = Math.random() > 0.25 ? 'UP' : 'DOWN' })
        await visualApi.reportStream({
          sessionId: 'sim-' + currentSessionId.value,
          offeringId,
          courseName: offering?.course?.courseName || '',
          className: offering?.className || '',
          detectedPersonCount: detected,
          lookupCount: lookup,
          lookdownCount: detected - lookup,
          lookupRate: lookupRatio(lookup, detected),
          presentStudentIds: simulatedPresent,
          studentPoses: poses
        })
      } catch (error: any) {
        if (stillSelected()) {
          stopSimulation()
          await loadOfferings()
          alert('模拟推流已停止: ' + (error.message || '请检查班级权限和冻结状态'))
        }
      } finally { reporting = false }
    }, 1500)
  } catch (error: any) {
    if (stillSelected()) {
      stopSimulation()
      await loadOfferings()
      alert('启动模拟失败: ' + (error.message || '未知错误'))
    }
  } finally {
    if (generation === simulationGeneration) isSimulationStarting.value = false
  }
}
''' +s[end:]
s=s.replace('if (!isMonitoring.value) {\n      try {', 'if (!isMonitoring.value && canWriteAttendance.value) {\n      try {')
s=s.replace('onUnmounted(() => {\n  if (pollTimer)', 'onUnmounted(() => {\n  stopSimulation()\n  if (pollTimer)')
s=s.replace('  if (selectedOfferingId.value) {\n    visualApi.resetStream', '  if (selectedOfferingId.value && canWriteAttendance.value) {\n    visualApi.resetStream')
p.write_text(s,encoding='utf-8')
p=root/'backend/src/main/java/com/classroom/ai/controller/VisualDashboardController.java'
s=p.read_text(encoding='utf-8').replace('getTrend() {','getTrend(@RequestParam(required = false) Long offeringId) {').replace('visualDashboardService.getTrend();','visualDashboardService.getTrend(offeringId);')
p.write_text(s,encoding='utf-8')
p=root/'backend/src/main/java/com/classroom/ai/modules/course/service/impl/CourseServiceImpl.java'
s=p.read_text(encoding='utf-8')
pos=s.index('        List<com.classroom.ai.modules.course.entity.OfferingStudentEnrollment> enrollments',s.index('public List<com.classroom.ai.entity.Student> getAvailableStudentsForOffering'))
s=s[:pos]+'''        var user = com.classroom.ai.modules.auth.context.AuthContext.getCurrentUser();
        if (user == null) throw new com.classroom.ai.common.exception.UnauthorizedException("请先登录");
        if (user.getRole() != com.classroom.ai.modules.auth.entity.RoleEnum.DIRECTOR) return List.of();
        CourseArchiveRules.validateDepartment(offering.getCourse().getDepartment());
        if (Boolean.TRUE.equals(offering.getIsSnapshotFrozen())) return List.of();
''' + s[pos:]
p.write_text(s,encoding='utf-8')
p=root/'backend/src/main/java/com/classroom/ai/modules/resource/controller/CourseResourceController.java'
s=p.read_text(encoding='utf-8').replace('.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=', '.contentType(org.springframework.http.MediaTypeFactory.getMediaType(path.getFileName().toString())\n                        .orElse(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM))\n                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=')
p.write_text(s,encoding='utf-8')
p=root/'frontend/src/api/index.ts'
s=p.read_text(encoding='utf-8').replace('getTrend: async ():','getTrend: async (offeringId: number):').replace("('/api/visual/trend')", "('/api/visual/trend', { params: { offeringId } })")
# Camera reset must be scoped by its caller; omit the obsolete global reset.
s=s.replace("    try {\n      await client.post('/api/visual/reset')\n    } catch {}\n",'')
p.write_text(s,encoding='utf-8')
