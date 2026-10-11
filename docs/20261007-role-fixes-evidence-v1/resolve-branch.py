from pathlib import Path
import subprocess, re
root=Path(__file__).resolve().parents[2]
target=Path('C:/Users/a3185/.codex/worktrees/8810/classroom-ai-demo')
def original(path):return subprocess.check_output(['git','show','e2f0950:'+path],cwd=target).decode('utf-8')
def write(path,text):(target/path).write_text(text,encoding='utf-8')
for path in ['backend/src/main/java/com/classroom/ai/controller/VisualDashboardController.java',
 'backend/src/main/java/com/classroom/ai/service/impl/VisualDashboardServiceImpl.java',
 'backend/src/test/java/com/classroom/ai/RegressionTest.java','frontend/src/api/index.ts','frontend/src/views/AttendanceDashboardView.vue']:
    write(path,(root/path).read_text(encoding='utf-8'))
p='frontend/src/views/AttendanceDashboardView.vue'
s=(target/p).read_text(encoding='utf-8').replace('!!selectedOffering.value && !selectedOffering.value.isSnapshotFrozen', "!!selectedOffering.value && !selectedOffering.value.isSnapshotFrozen && selectedOffering.value.status !== 'FINISHED'")
write(p,s)
p='backend/src/main/java/com/classroom/ai/service/impl/VisualDashboardServiceImpl.java'
s=(target/p).read_text(encoding='utf-8').replace('write && Boolean.TRUE.equals(offering.getIsSnapshotFrozen())', 'write && (Boolean.TRUE.equals(offering.getIsSnapshotFrozen()) || "FINISHED".equals(offering.getStatus()))')
write(p,s)
p='backend/src/main/java/com/classroom/ai/modules/attendance/repository/AttendanceSessionRepository.java'
s=original(p).replace('    List<AttendanceSession> findByStatusOrderByCreatedAtDescIdDesc(String status);','    List<AttendanceSession> findByStatusOrderByCreatedAtDescIdDesc(String status);\n    List<AttendanceSession> findByStatusOrderByCreatedAtDesc(String status);')
write(p,s)
p='backend/src/main/java/com/classroom/ai/modules/attendance/service/AttendanceAccessService.java'
s=original(p).replace('    public CourseOffering requireRead(Long id) {', '    @jakarta.persistence.PersistenceContext\n    private jakarta.persistence.EntityManager entityManager;\n\n    public CourseOffering requireRead(Long id) {')
s=s.replace('AuthContext.requireRole(RoleEnum.DIRECTOR, RoleEnum.TEACHER, RoleEnum.SUPERVISOR);','requireRole();')
s=s.replace('        authorization.validateOfferingRead(offering);\n        if (Boolean.TRUE', '        if (entityManager != null) entityManager.refresh(offering);\n        authorization.validateOfferingRead(offering);\n        if (Boolean.TRUE')
s=s.replace('\n}\n','''
    private void requireRole() {
        var user = authorization.requireCurrentUser();
        if (user.getRole() != RoleEnum.DIRECTOR && user.getRole() != RoleEnum.TEACHER && user.getRole() != RoleEnum.SUPERVISOR)
            throw new com.classroom.ai.common.exception.ForbiddenException("当前角色不能操作考勤");
    }
}
''')
write(p,s)
p='backend/src/main/java/com/classroom/ai/modules/attendance/service/impl/AttendanceServiceImpl.java'
s=original(p).replace('@Transactional\n','@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)\n')
pos=s.index('    @Override')
s=s[:pos]+'''    public AttendanceServiceImpl(AttendanceSessionRepository sessionRepository,
                                 CourseOfferingRepository offeringRepository,
                                 CourseScheduleRepository scheduleRepository,
                                 com.classroom.ai.modules.course.repository.OfferingStudentEnrollmentRepository enrollmentRepository,
                                 CourseAuthorizationService authorization) {
        this(sessionRepository, offeringRepository, scheduleRepository, enrollmentRepository,
                new AttendanceAccessService(offeringRepository, authorization), authorization);
    }

'''+s[pos:]
s=s.replace('    public AttendanceSession getCurrentActiveSession() {\n        authorization.requireCurrentUser();', '''    public AttendanceSession getCurrentActiveSession() {
        return getCurrentActiveSession(null);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceSession getCurrentActiveSession(Long offeringId) {
        authorization.requireCurrentUser();
        if (offeringId != null) {
            CourseOffering offering = access.requireRead(offeringId);
            if (Boolean.TRUE.equals(offering.getIsSnapshotFrozen()) || "FINISHED".equals(offering.getStatus())) return null;
            return sessionRepository.findFirstByOfferingIdAndStatusOrderByCreatedAtDesc(offeringId, "ACTIVE").orElse(null);
        }''')
s=s.replace('        AuthContext.requireAuthenticated();','        authorization.requireCurrentUser();')
write(p,s)
p='backend/src/test/java/com/classroom/ai/modules/attendance/AttendanceServiceTest.java'
write(p,original(p))
p='backend/src/test/java/com/classroom/ai/RegressionTest.java'
s=(target/p).read_text(encoding='utf-8')
s=s.replace('        when(offerings.findForUpdate(any())).thenReturn(Optional.of(new CourseOffering()));', '        when(offerings.findForUpdate(any())).thenReturn(Optional.of(new CourseOffering()));\n        when(sessions.findOfferingId(1L)).thenReturn(Optional.of(100L));')
write(p,s)
p='backend/src/test/java/com/classroom/ai/modules/attendance/AttendanceHttpAuthorizationTest.java'
s=(target/p).read_text(encoding='utf-8').replace('CourseAuthorizationService.class,AttendanceServiceImpl.class', 'CourseAuthorizationService.class,AttendanceServiceImpl.class,com.classroom.ai.modules.attendance.service.AttendanceAccessService.class')
s=s.replace('    @Autowired AttendanceServiceImpl attendance;', '    @Autowired AttendanceServiceImpl attendance;\n    @Autowired com.classroom.ai.modules.attendance.service.AttendanceAccessService access;')
s=s.replace('setField(attendance,"entityManager",null)', 'setField(access,"entityManager",null)')
s=s.replace('        when(sessions.findForUpdate(51L)).thenReturn(Optional.of(session));', '        when(sessions.findForUpdate(51L)).thenReturn(Optional.of(session));\n        when(sessions.findOfferingId(51L)).thenReturn(Optional.of(101L));')
write(p,s)
for p in ['docs/analysis-notes.md','docs/plan.md','docs/questions.md','docs/source-notes.md']:
    text=(target/p).read_text(encoding='utf-8')
    text=re.sub(r'<<<<<<<[^\n]*\n(.*?)=======\n(.*?)>>>>>>>[^\n]*\n',lambda m:m[1]+'\n'+m[2],text,flags=re.S)
    write(p,text.rstrip()+'\n')
print('Combined target branch permissions/parent-first locks with current association, roster and realtime fixes')
