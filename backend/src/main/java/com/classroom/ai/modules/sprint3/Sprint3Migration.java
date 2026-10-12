package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import java.math.BigDecimal;
@Component @RequiredArgsConstructor @Order(30)
public class Sprint3Migration implements ApplicationRunner {
 private final EntityManager em;private final JdbcTemplate jdbc;
 @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.attitude:25}") private BigDecimal a;
 @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.content:25}") private BigDecimal c;
 @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.method:25}") private BigDecimal m;
 @org.springframework.beans.factory.annotation.Value("${classroom.supervision.weights.effect:25}") private BigDecimal e;
 @Transactional public void run(ApplicationArguments args) {
  if(jdbc.queryForObject("SELECT COUNT(*) FROM s3_template_version",Integer.class)==0){
   EvaluationTemplateService.validate(a,c,m,e);TemplateVersion v=new TemplateVersion();v.setFamilyId(0L);v.setDraftId(0L);v.setNumber(1);v.setName("默认四维模板");v.setScenario("随堂督导");v.setDescriptions("教学态度、教学内容、教学方法、教学效果；来源：服务端配置");v.setAttitude(a);v.setContent(c);v.setMethod(m);v.setEffect(e);v.setPublishedAt(java.time.LocalDateTime.now());em.persist(v);
  }
  // Do not infer weights for legacy evaluations.
  for(CourseResource r:em.createQuery("from CourseResource where seriesId is null",CourseResource.class).getResultList()){
   ResourceSeries s=new ResourceSeries();s.setCourseId(r.getCourse().getId());s.setShared(r.getIsPublic());s.setNextNumber(2);s.setAccessEpoch(0L);s.setRemoved(false);em.persist(s);em.flush();r.setSeriesId(s.getId());r.setVersionNumber(1);em.flush();s.setCurrentVersionId(r.getId());em.flush();
  }
  for(SupervisionEvaluation v:em.createQuery("from SupervisionEvaluation",SupervisionEvaluation.class).getResultList()){
   if(v.getSuggestions()!=null&&!v.getSuggestions().isBlank()&&em.createQuery("select count(a) from Advice a where evaluationId=:id",Long.class).setParameter("id",v.getId()).getSingleResult()==0){
    Advice x=new Advice();x.setDefaultGenerated(true);x.setEvaluationId(v.getId());x.setText(v.getSuggestions());x.setCreatedBy(v.getSupervisorUserId());x.setEvidenceJson("[]");x.setStageTagsJson("[]");em.persist(x);
   }
  }
  em.flush();
  jdbc.update("UPDATE s3_template_version SET family_id=id WHERE family_id IS NULL");
  for(String col:new String[]{"score_attitude","score_content","score_method","score_effect","total_score"})
   if("NO".equals(jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_supervision_evaluation' AND COLUMN_NAME=?",String.class,col)))jdbc.execute("ALTER TABLE t_supervision_evaluation MODIFY "+col+" DOUBLE NULL");
  jdbc.update("UPDATE t_supervision_evaluation SET record_version=0 WHERE record_version IS NULL");
  jdbc.update("UPDATE t_micro_teaching_slice SET record_version=0 WHERE record_version IS NULL");
  constraint("s3_resource_seq","ALTER TABLE t_course_resource ADD CONSTRAINT s3_resource_seq UNIQUE(series_id,version_number)");
  constraint("s3_resource_pair","ALTER TABLE t_course_resource ADD CONSTRAINT s3_resource_pair UNIQUE(series_id,id)");
  constraint("s3_current_version","ALTER TABLE s3_resource_series ADD CONSTRAINT s3_current_version FOREIGN KEY(id,current_version_id) REFERENCES t_course_resource(series_id,id)");
 }
 private void constraint(String name,String sql){
  if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND CONSTRAINT_NAME=?",Integer.class,name)==0)jdbc.execute(sql);
 }
}

