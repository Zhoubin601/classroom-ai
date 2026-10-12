"""Copy only CREATE TABLE definitions; never import baseline personal or face data."""
import re
from pathlib import Path

root=Path(__file__).resolve().parents[2]
ddl=re.findall(r"CREATE TABLE.*?;\s*",(root/"initialize.sql").read_text(encoding="utf-8"),re.S)
assert len(ddl)==20
sql="SET FOREIGN_KEY_CHECKS=0;\n"+"".join(ddl)+"\nSET FOREIGN_KEY_CHECKS=1;\n"
sql+="""
INSERT INTO t_course(course_code,course_name,department,major_code,credits,hours) VALUES('UPGRADE-SYN','Synthetic legacy course','Synthetic Dept','SE',1,8);
INSERT INTO t_course_offering(course_id,academic_term,teacher_name,teacher_code,class_name,student_count,status,is_snapshot_frozen)
 SELECT id,'UPGRADE-TEST','Synthetic Teacher','SYN-T1','Synthetic Class',0,'IN_PROGRESS',0 FROM t_course WHERE course_code='UPGRADE-SYN';
INSERT INTO t_course_resource(course_id,chapter,resource_name,file_type,file_url,tag,version,is_public)
 SELECT id,'Ch1','Same legacy name','PDF','/uploads/resources/legacy.pdf','理论','v1',1 FROM t_course WHERE course_code='UPGRADE-SYN';
INSERT INTO t_course_resource(course_id,chapter,resource_name,file_type,file_url,tag,version,is_public)
 SELECT id,'Ch1','Same legacy name','PDF','/uploads/resources/legacy.pdf','理论','v1',1 FROM t_course WHERE course_code='UPGRADE-SYN';
INSERT INTO t_supervision_evaluation(offering_id,evaluate_date,listen_topic,supervisor_name,status,score_attitude,score_content,score_method,score_effect,total_score,suggestions)
 SELECT id,'2026-10-11','Synthetic legacy topic','Synthetic Reviewer','PUBLISHED',20,20,20,20,80,'Preserve entire old suggestion' FROM t_course_offering WHERE academic_term='UPGRADE-TEST';
"""
target=root/"runtime/sprint3-test/legacy-schema.sql";target.parent.mkdir(parents=True,exist_ok=True);target.write_text(sql,encoding="utf-8")
print("20 baseline table definitions; synthetic legacy rows only")
