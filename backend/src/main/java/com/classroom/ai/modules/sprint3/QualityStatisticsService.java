package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.course.entity.*;
import com.classroom.ai.modules.course.repository.CourseOfferingRepository;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.LocalDateTime;
@Service @RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class QualityStatisticsService {
 private final EntityManager em;private final CourseOfferingRepository offerings;private final CourseAuthorizationService authorization;private final ObjectMapper json;
 @Value("${classroom.supervision.low-score-threshold:75}") private double lowScore;
 @Value("${classroom.supervision.low-coverage-threshold:30}") private double lowCoverage;
 public record Filter(String term,String courseType,String keyword,String teacher,boolean uncategorized){}
 public record Snapshot(LocalDateTime asOf,String term,List<CourseOffering> offerings,List<SupervisionEvaluation> evaluations){}
 public Snapshot snapshot(Filter f,boolean feedback){
  Sprint3Policy.roles(feedback?new RoleEnum[]{RoleEnum.TEACHER}:new RoleEnum[]{RoleEnum.DIRECTOR,RoleEnum.SUPERVISOR});
  List<CourseOffering> visible=authorization.filterOfferings(offerings.findAll()).stream()
   .filter(o->o.getCourse()!=null&&Set.of("IN_PROGRESS","FINISHED").contains(o.getStatus()==null?"":o.getStatus())).toList();
  String term=f.term!=null&&!f.term.isBlank()?f.term:visible.stream().map(CourseOffering::getAcademicTerm).filter(Objects::nonNull).max(String::compareTo).orElse("");
  visible=visible.stream().filter(o->Objects.equals(term,o.getAcademicTerm()))
   .filter(o->!f.uncategorized||o.getCourse().getCourseType()==null||o.getCourse().getCourseType().isBlank())
   .filter(o->f.courseType==null||f.courseType.isBlank()||Objects.equals(f.courseType,o.getCourse().getCourseType()))
   .filter(o->f.keyword==null||f.keyword.isBlank()||(o.getCourse().getCourseCode()+" "+o.getCourse().getCourseName()).contains(f.keyword))
   .filter(o->f.teacher==null||f.teacher.isBlank()||Objects.toString(o.getTeacherName(),"").contains(f.teacher)).toList();
  Set<Long> ids=new HashSet<>();visible.forEach(o->ids.add(o.getId()));
  LocalDateTime now=LocalDateTime.now();
  List<SupervisionEvaluation> eval=em.createQuery("from SupervisionEvaluation order by id",SupervisionEvaluation.class).getResultList().stream()
   .filter(e->e.getOffering()!=null&&ids.contains(e.getOffering().getId()))
   .filter(e->feedback?Sprint3Policy.feedbackOpen(e,now):Sprint3Policy.approved(e)).toList();
  return new Snapshot(now,term,visible,eval);
 }
 public Map<String,Object> report(Filter f,int page,int size,boolean teacher){
  if(!teacher)Sprint3Policy.roles(RoleEnum.DIRECTOR);
  if(page<1||size<1||size>100)throw new IllegalArgumentException("分页参数范围不正确");
  Snapshot s=snapshot(f,teacher);List<Map<String,Object>> rows=summary(s);
  int start=Math.min(rows.size(),(page-1)*size),end=Math.min(rows.size(),start+size);
  var result=new LinkedHashMap<String,Object>();result.put("asOf",s.asOf);result.put("term",s.term);result.put("total",rows.size());result.put("rows",rows.subList(start,end));result.put("details",details(s,teacher));
  return result;
 }
 public List<Map<String,Object>> summary(Snapshot s){
  List<Map<String,Object>> result=new ArrayList<>();
  for(CourseOffering o:s.offerings){
   var eval=s.evaluations.stream().filter(e->Objects.equals(o.getId(),e.getOffering().getId())).toList();
   Map<String,List<SupervisionEvaluation>> groups=new LinkedHashMap<>();
   for(var e:eval)groups.computeIfAbsent(known(e)?"T"+e.getTemplateVersionId():"LEGACY_UNKNOWN",k->new ArrayList<>()).add(e);
   if(groups.isEmpty())groups.put("NO_EVALUATION",List.of());
   for(var entry:groups.entrySet()){
    var list=entry.getValue();var complete=list.stream().filter(this::completeKnown).toList();
    var row=new LinkedHashMap<String,Object>();
    row.put("offeringId",o.getId());row.put("courseId",o.getCourse().getId());row.put("courseCode",o.getCourse().getCourseCode());row.put("courseName",o.getCourse().getCourseName());row.put("courseType",category(o.getCourse()));row.put("teacher",o.getTeacherName());row.put("template",entry.getKey());row.put("evaluationCount",list.size());row.put("validTotalCount",complete.size());
    row.put("average",complete.isEmpty()?null:complete.stream().mapToDouble(SupervisionEvaluation::getTotalScore).average().orElseThrow());
    row.put("legacyAverage",entry.getKey().equals("LEGACY_UNKNOWN")?list.stream().map(SupervisionEvaluation::getTotalScore).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null):null);
    row.put("evaluationIds",list.stream().map(SupervisionEvaluation::getId).toList());result.add(row);
   }
  }
  return result;
 }
 public List<Map<String,Object>> details(Snapshot s,boolean teacher){
  return s.evaluations.stream().map(e->{var m=new LinkedHashMap<String,Object>();m.put("evaluationId",e.getId());m.put("offeringId",e.getOffering().getId());m.put("courseCode",e.getOffering().getCourse().getCourseCode());m.put("courseType",category(e.getOffering().getCourse()));m.put("templateVersionId",e.getTemplateVersionId());m.put("weights",e.getWeightSnapshot());m.put("scores",Arrays.asList(e.getScoreAttitude(),e.getScoreContent(),e.getScoreMethod(),e.getScoreEffect()));m.put("total",e.getTotalScore());m.put("validTotal",completeKnown(e));m.put("feedbackOpen",Sprint3Policy.feedbackOpen(e,s.asOf));m.put("status",e.getStatus());if(teacher){m.put("highlights",e.getHighlights());m.put("suggestions",e.getSuggestions());}return (Map<String,Object>)m;}).toList();
 }
 public Map<String,Object> coverage(Filter f){
  return coverage(snapshot(f,false));
 }
 private Map<String,Object> coverage(Snapshot s){Map<Long,Course> courses=new LinkedHashMap<>();s.offerings.forEach(o->courses.put(o.getCourse().getId(),o.getCourse()));
  Set<Long> covered=new HashSet<>();s.evaluations.forEach(e->covered.add(e.getOffering().getCourse().getId()));
  List<Map<String,Object>> details=new ArrayList<>();
  courses.forEach((id,c)->details.add(Map.of("courseId",id,"courseCode",c.getCourseCode(),"courseName",c.getCourseName(),"covered",covered.contains(id),"evaluationIds",s.evaluations.stream().filter(e->e.getOffering().getCourse().getId().equals(id)).map(SupervisionEvaluation::getId).toList())));
  return Map.of("asOf",s.asOf,"term",s.term,"total",courses.size(),"covered",covered.size(),"rate",courses.isEmpty()?0.0:100.0*covered.size()/courses.size(),"evaluationCount",s.evaluations.size(),"details",details);
 }
 public List<Map<String,Object>> alerts(Filter f){
  Sprint3Policy.roles(RoleEnum.DIRECTOR,RoleEnum.SUPERVISOR);
  if(!Double.isFinite(lowScore)||lowScore<0||lowScore>100||!Double.isFinite(lowCoverage)||lowCoverage<0||lowCoverage>100)throw new IllegalStateException("预警配置范围0～100");
  var s=snapshot(f,false);var coverage=coverage(s);List<Map<String,Object>> result=new ArrayList<>();
  var details=(List<?>)coverage.get("details");
  for(Object x:details){var m=(Map<?,?>)x;if(Boolean.FALSE.equals(m.get("covered"))){var a=new LinkedHashMap<String,Object>();a.put("courseId",m.get("courseId"));a.put("courseCode",m.get("courseCode"));a.put("courseName",m.get("courseName"));a.put("alertLevel","YELLOW");a.put("alertType","ZERO_SUPERVISION");a.put("evaluationCount",0);a.put("alertMessage","零覆盖，需人工核查");a.put("evaluationIds",List.of());a.put("thresholdSource","零覆盖规则");result.add(a);}}
  if(((Number)coverage.get("total")).intValue()>0&&((Number)coverage.get("rate")).doubleValue()<lowCoverage)
   result.add(Map.of("alertLevel","YELLOW","alertType","LOW_COVERAGE","courseName","所选范围整体覆盖","alertMessage","低覆盖，需人工复核","currentScore",coverage.get("rate"),"threshold",lowCoverage,"thresholdSource","CLASSROOM_LOW_COVERAGE_THRESHOLD（演示配置）","evaluationCount",s.evaluations.size()));
  for(var r:summary(s))if(r.get("average") instanceof Number n&&n.doubleValue()<lowScore){var a=new LinkedHashMap<>(r);a.put("alertLevel","RED");a.put("alertType","LOW_SCORE_WARNING");a.put("alertMessage","此模板版本均分低于演示阈值，需人工复核");a.put("currentScore",n.doubleValue());a.put("threshold",lowScore);a.put("thresholdSource","CLASSROOM_LOW_SCORE_THRESHOLD（演示配置）");result.add(a);}
  return result;
 }
 public Map<String,Object> problems(Filter f){
  Sprint3Policy.roles(RoleEnum.SUPERVISOR);var s=snapshot(f,false);Map<String,List<CourseOffering>> groups=new LinkedHashMap<>();
  s.offerings.forEach(o->groups.computeIfAbsent(category(o.getCourse()),k->new ArrayList<>()).add(o));
  List<Map<String,Object>> out=new ArrayList<>();
  for(var group:groups.entrySet()){
   Set<Long> ids=new HashSet<>();group.getValue().forEach(o->ids.add(o.getId()));
   var eval=s.evaluations.stream().filter(e->ids.contains(e.getOffering().getId())).toList();
   int tagged=0;Map<String,Integer> counts=new LinkedHashMap<>();Sprint3Policy.PROBLEMS.forEach(t->counts.put(t,0));
   List<Map<String,Object>> detail=new ArrayList<>();
   for(var e:eval){List<String> tags=tags(e);if(!tags.isEmpty())tagged++;tags.forEach(t->counts.merge(t,1,Integer::sum));detail.add(Map.of("evaluationId",e.getId(),"courseCode",e.getOffering().getCourse().getCourseCode(),"courseType",group.getKey(),"tags",tags,"classificationVersion",Objects.toString(e.getProblemClassificationVersion(),"未标注")));}
   var row=new LinkedHashMap<String,Object>();row.put("courseType",group.getKey());row.put("courseCount",group.getValue().stream().map(o->o.getCourse().getId()).distinct().count());row.put("sampleCount",eval.size());row.put("taggedCount",tagged);row.put("untaggedCount",eval.size()-tagged);row.put("details",detail);
   row.put("tags",counts.entrySet().stream().map(t->Map.of("name",t.getKey(),"count",t.getValue(),"percentage",eval.isEmpty()?0.0:round(100.0*t.getValue()/eval.size()))).toList());out.add(row);
  }
  var options=snapshot(new Filter(f.term,null,null,null,false),false).offerings.stream().map(o->category(o.getCourse())).distinct().sorted().toList();
  return Map.of("asOf",s.asOf,"term",s.term,"categories",options,"groups",out,"rule","审核集合A；标签占比分母为各课程类别全部审核评价，多标签占比之和可超过100%；仅频次，无排名");
 }
 private List<String> tags(SupervisionEvaluation e){try{return Sprint3Policy.problems(json.readValue(Objects.toString(e.getProblemTagsJson(),"[]"),json.getTypeFactory().constructCollectionType(List.class,String.class)));}catch(Exception ex){return List.of();}}
 public boolean known(SupervisionEvaluation e){
  if(e.getTemplateVersionId()==null||e.getWeightSnapshot()==null)return false;
  try{var w=json.readTree(e.getWeightSnapshot());String[] keys={"attitude","content","method","effect"};java.math.BigDecimal[] weights=new java.math.BigDecimal[4];for(int i=0;i<4;i++){if(!w.path(keys[i]).isNumber())return false;weights[i]=w.path(keys[i]).decimalValue();}EvaluationTemplateService.validate(weights);return true;}catch(Exception ex){return false;}
 }
 public boolean completeKnown(SupervisionEvaluation e){
  if(!known(e)||e.getTotalScore()==null)return false;
  try{var w=json.readTree(e.getWeightSnapshot());Double[] scores={e.getScoreAttitude(),e.getScoreContent(),e.getScoreMethod(),e.getScoreEffect()};String[] keys={"attitude","content","method","effect"};for(int i=0;i<4;i++)if(w.path(keys[i]).asDouble()>0&&scores[i]==null)return false;return true;}catch(Exception ex){return false;}
 }
 public Map<String,Object> radar(Filter f){
  var s=snapshot(f,true);var result=new LinkedHashMap<String,Object>();result.put("asOf",s.asOf);result.put("sampleCount",s.evaluations.size());result.put("groups",summary(s));
  String[] keys={"attitude","content","method","effect"};List<Map<String,Object>> axes=new ArrayList<>();
  for(int i=0;i<4;i++){List<Double> values=new ArrayList<>();for(var e:s.evaluations)if(known(e)){try{double w=json.readTree(e.getWeightSnapshot()).path(keys[i]).asDouble();Double score=new Double[]{e.getScoreAttitude(),e.getScoreContent(),e.getScoreMethod(),e.getScoreEffect()}[i];if(w>0&&score!=null)values.add(score/w*100);}catch(Exception ignored){}}var axis=new LinkedHashMap<String,Object>();axis.put("name",keys[i]);axis.put("sampleCount",values.size());axis.put("value",values.isEmpty()?null:round(values.stream().mapToDouble(Double::doubleValue).average().orElseThrow()));axes.add(axis);}
  result.put("axes",axes);result.put("details",details(s,true));return result;
 }
 public byte[] export(Filter f,String format)throws java.io.IOException {
  Sprint3Policy.roles(RoleEnum.DIRECTOR);var s=snapshot(f,false);var rows=summary(s);var detail=details(s,false);
  return format.equals("csv")?QualityReportWriter.csv(rows,s.asOf.toString()):QualityReportWriter.xlsx(rows,detail,s.asOf.toString());
 }
 public static String category(Course c){return c.getCourseType()==null||c.getCourseType().isBlank()?"未分类":c.getCourseType();}
 private static double round(double v){return Math.round(v*10.0)/10.0;}
}

