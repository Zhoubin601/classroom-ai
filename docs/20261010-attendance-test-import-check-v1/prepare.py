"""Recreate the isolated one-line test configuration comparison; never edit backend/src."""
from pathlib import Path
import shutil

evidence = Path(__file__).resolve().parent
root = evidence.parent.parent
module = evidence / 'temporary-module'
if module.exists():
    raise SystemExit('Temporary module already exists; refusing to overwrite it.')
module.mkdir()
shutil.copy2(root / 'backend/pom.xml', module / 'pom.xml')
shutil.copytree(root / 'backend/src/main', module / 'src/main')
relative = Path('src/test/java/com/classroom/ai/modules/course/AssociationMysqlTest.java')
target = module / relative
target.parent.mkdir(parents=True)
source = (root / 'backend' / relative).read_text(encoding='utf-8')
before = 'AttendanceServiceImpl.class,CourseImportServiceImpl.class'
after = 'AttendanceServiceImpl.class,com.classroom.ai.modules.attendance.service.AttendanceAccessService.class,CourseImportServiceImpl.class'
if source.count(before) != 1:
    raise SystemExit('Expected original configuration no longer matches.')
target.write_text(source.replace(before, after), encoding='utf-8')
print('Isolated test module prepared.')
