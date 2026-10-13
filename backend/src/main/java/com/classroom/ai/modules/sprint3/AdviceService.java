package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.*;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import com.classroom.ai.modules.resource.entity.*;
import com.classroom.ai.modules.resource.service.ResourceAccessService;
import java.util.*;
import java.time.*;
@Service @RequiredArgsConstructor @Transactional
public class AdviceService {
 private final EntityManager em;private final CourseAuthorizationService authorization;private final ResourceAccessService resourceAccess;private final ObjectMapper json;private final VideoAssetService videos;
 public record AdviceInput(Long id,Long expectedVersion,String text,JsonNode evidenceRefs,List<String> stageTags){
  @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("不允许字段："+key);}
 }
 public record FeedbackInput(Long expectedVersion,String decision,String actionText,String feedbackText,JsonNode evidenceRefs,List<String> stageTags){
  @com.fasterxml.jackson.annotation.JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("不允许客户端设置历史或身份："+key);}
 }
 private SupervisionEvaluation parent(Long id,boolean write){
  Sprint3Policy.roles(RoleEnum.DIRECTOR,RoleEnum.SUPERVISOR,RoleEnum.TEACHER);
  var e=em.find(SupervisionEvaluation.class,id,write?LockModeType.PESSIMISTIC_WRITE:LockModeType.NONE);
  if(e==null)throw new IllegalArgumentException("评价不存在");authorization.validateOfferingRead(e.getOffering());
  var u=AuthContext.getCurrentUser();
  if(write){Sprint3Policy.roles(RoleEnum.SUPERVISOR);if(!Objects.equals(u.getId(),e.getSupervisorUserId())||!Set.of("DRAFT","REJECTED").contains(e.getStatus()))throw new com.classroom.ai.common.exception.ForbiddenException("只能维护本人草稿建议");}
  else if(u.getRole()==RoleEnum.TEACHER&&!Sprint3Policy.feedbackOpen(e,LocalDateTime.now())||
   u.getRole()==RoleEnum.SUPERVISOR&&!Objects.equals(u.getId(),e.getSupervisorUserId())&&!Sprint3Policy.feedbackOpen(e,LocalDateTime.now()))throw new com.classroom.ai.common.exception.ForbiddenException("建议尚未对当前角色开放");
  return e;
 }
 public Advice receive(Long evaluationId,AdviceInput in){
  var e=parent(evaluationId,true);Advice a=in.id==null?new Advice():em.find(Advice.class,in.id,LockModeType.PESSIMISTIC_WRITE);
  if(a==null)throw new IllegalArgumentException("建议不存在");
  if(in.id!=null){if(!Objects.equals(a.getEvaluationId(),evaluationId))throw new IllegalArgumentException("建议不属于评价");Sprint3Policy.version(a.getRevision(),in.expectedVersion);}
  a.setDefaultGenerated(false);a.setTouchedAt(LocalDateTime.now());a.setEvaluationId(evaluationId);a.setText(Sprint3Policy.text(in.text,2000,"建议"));a.setEvidenceJson(evidence(in.evidenceRefs,e,false).toString());a.setStageTagsJson(stages(in.stageTags));a.setCreatedBy(AuthContext.getCurrentUser().getId());
  if(in.id==null)em.persist(a);em.lock(e,LockModeType.OPTIMISTIC_FORCE_INCREMENT);em.flush();return a;
 }
 public void syncDefault(SupervisionEvaluation e){
  if(e.getSuggestions()==null||e.getSuggestions().isBlank())return;
  var list=em.createQuery("from Advice where evaluationId=:id order by id",Advice.class).setParameter("id",e.getId()).getResultList();
  if(list.isEmpty()){Advice a=new Advice();a.setDefaultGenerated(true);a.setEvaluationId(e.getId());a.setText(e.getSuggestions());a.setEvidenceJson("[]");a.setStageTagsJson("[]");a.setCreatedBy(e.getSupervisorUserId());em.persist(a);}
  else list.stream().filter(Advice::isDefaultGenerated).findFirst().ifPresent(a->a.setText(e.getSuggestions()));
 }
 public List<Map<String,Object>> list(Long evaluationId){
  var e=parent(evaluationId,false);
  return em.createQuery("from Advice where evaluationId=:id order by id",Advice.class).setParameter("id",evaluationId).getResultList().stream().map(a->view(a,e)).toList();
 }
 public List<Map<String,Object>> mine(){
  Sprint3Policy.roles(RoleEnum.TEACHER);List<Map<String,Object>> all=new ArrayList<>();
  for(var e:em.createQuery("from SupervisionEvaluation order by id",SupervisionEvaluation.class).getResultList()){
   if(!Sprint3Policy.feedbackOpen(e,LocalDateTime.now()))continue;
   try{authorization.validateOfferingRead(e.getOffering());all.addAll(list(e.getId()));}catch(com.classroom.ai.common.exception.ForbiddenException ignored){}
  }return all;
 }
 private Map<String,Object> view(Advice a,SupervisionEvaluation e){
  var m=new LinkedHashMap<String,Object>();m.put("id",a.getId());m.put("evaluationId",a.getEvaluationId());m.put("recordVersion",a.getRevision());m.put("text",a.getText());m.put("courseName",e.getOffering().getCourse().getCourseName());
  try{m.put("stageTags",json.readTree(a.getStageTagsJson()));m.put("evidenceRefs",evidence(json.readTree(a.getEvidenceJson()),e,true));}catch(Exception ex){m.put("evidenceRefs",List.of(Map.of("message","证据不可访问")));}
  if(AuthContext.getCurrentUser().getRole()==RoleEnum.TEACHER){
   var f=em.createQuery("from ActionFeedback where suggestionId=:sid and teacherUserId=:uid",ActionFeedback.class).setParameter("sid",a.getId()).setParameter("uid",AuthContext.getCurrentUser().getId()).getResultList();
   m.put("feedback",f.isEmpty()?null:feedbackView(f.get(0),e));
   m.put("history",f.isEmpty()?List.of():em.createQuery("from FeedbackRevision where feedbackId=:id order by recordVersion",FeedbackRevision.class).setParameter("id",f.get(0).getId()).getResultList().stream().map(r->historyView(r,e)).toList());
  }return m;
 }
 private Map<String,Object> feedbackView(ActionFeedback f,SupervisionEvaluation e){
  Map<String,Object> out=json.convertValue(f,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
  try{out.put("evidenceJson",evidence(json.readTree(f.getEvidenceJson()),e,true).toString());}catch(Exception ex){out.put("evidenceJson","[{\"message\":\"证据不可访问\"}]");}return out;
 }
 private Map<String,Object> historyView(FeedbackRevision r,SupervisionEvaluation e){
  var out=new LinkedHashMap<String,Object>();out.put("recordVersion",r.getRecordVersion());out.put("occurredAt",r.getOccurredAt());
  try{var data=json.readTree(r.getSnapshotJson());((com.fasterxml.jackson.databind.node.ObjectNode)data).put("evidenceJson",evidence(json.readTree(data.path("evidenceJson").asText("[]")),e,true).toString());out.put("snapshotJson",data.toString());}catch(Exception ex){out.put("snapshotJson","证据不可访问");}return out;
 }
 public ActionFeedback feedback(Long id,FeedbackInput in){
  Sprint3Policy.roles(RoleEnum.TEACHER);Advice a=em.find(Advice.class,id,LockModeType.PESSIMISTIC_WRITE);if(a==null)throw new IllegalArgumentException("建议不存在");
  var e=parent(a.getEvaluationId(),false);Long uid=AuthContext.getCurrentUser().getId();
  if(!Set.of("ACCEPTED","ADJUSTED").contains(in.decision==null?"":in.decision))throw new IllegalArgumentException("请选择接受或调整");
  String action=Sprint3Policy.text(in.actionText,2000,"行动内容");if(in.feedbackText!=null&&in.feedbackText.length()>4000)throw new IllegalArgumentException("实践反馈最多4000字");
  String refs=evidence(in.evidenceRefs,e,false).toString(),stages=stages(in.stageTags);
  var existing=em.createQuery("from ActionFeedback where suggestionId=:sid and teacherUserId=:uid",ActionFeedback.class).setParameter("sid",id).setParameter("uid",uid).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
  ActionFeedback f=existing.isEmpty()?new ActionFeedback():existing.get(0);Sprint3Policy.version(f.getRevision(),in.expectedVersion);
  f.setTouchedAt(LocalDateTime.now());f.setSuggestionId(id);f.setTeacherUserId(uid);f.setDecision(in.decision);f.setActionText(action);f.setFeedbackText(in.feedbackText);f.setEvidenceJson(refs);f.setStageTagsJson(stages);
  if(existing.isEmpty()){f.setRevision(1L);em.persist(f);}em.flush();
  FeedbackRevision rev=new FeedbackRevision();rev.setFeedbackId(f.getId());rev.setRecordVersion(f.getRevision());rev.setActorId(uid);rev.setOccurredAt(LocalDateTime.now());
  try{rev.setSnapshotJson(json.writeValueAsString(f));}catch(Exception ex){throw new IllegalStateException(ex);}em.persist(rev);return f;
 }
 private String stages(List<String> list){if(list==null)return "[]";if(list.size()>6)throw new IllegalArgumentException("教学环节过多");list.forEach(Sprint3Policy::stage);try{return json.writeValueAsString(list.stream().distinct().toList());}catch(Exception ex){throw new IllegalArgumentException(ex);}}
 @Transactional(readOnly=true)
 public List<Map<String,Object>> candidates(Long evaluationId,String type,String keyword){
  var e=parent(evaluationId,false);Long course=e.getOffering().getCourse().getId();
  if(!Set.of("EVALUATION","RESOURCE_VERSION","MICRO_SLICE").contains(type))throw new IllegalArgumentException("非法证据类型");
  String search=Objects.toString(keyword,"").trim().toLowerCase(Locale.ROOT);
  List<Map<String,Object>> result=new ArrayList<>();
  List<?> rows=switch(type){
   case "EVALUATION" -> em.createQuery("from SupervisionEvaluation where offering.course.id=:course order by id",SupervisionEvaluation.class).setParameter("course",course).getResultList();
   case "RESOURCE_VERSION" -> em.createQuery("from CourseResource where course.id=:course order by id",CourseResource.class).setParameter("course",course).getResultList();
   default -> em.createQuery("from MicroTeachingSlice where course.id=:course order by id",MicroTeachingSlice.class).setParameter("course",course).getResultList();
  };
  for(Object row:rows){
   Long id;String label;Map<String,Object> item=new LinkedHashMap<>();
   if(row instanceof SupervisionEvaluation r){id=r.getId();label="评价"+id+" · "+Objects.toString(r.getListenTopic(),"未填写主题");}
   else if(row instanceof CourseResource r){id=r.getId();label=r.getResourceName()+" · "+r.getVersion();}
   else {var r=(MicroTeachingSlice)row;id=r.getId();label=r.getVideoTitle();item.put("startMs",r.getStartMs());item.put("endMs",r.getEndMs());}
   var probe=json.createArrayNode();probe.addObject().put("type",type).put("id",id);
   try{evidence(probe,e,false);}catch(RuntimeException denied){continue;}
   if(!label.toLowerCase(Locale.ROOT).contains(search))continue;
   item.put("type",type);item.put("id",id);item.put("label",label);result.add(item);
  }
  return result;
 }
 private JsonNode evidence(JsonNode refs,SupervisionEvaluation e,boolean redact){
  var out=json.createArrayNode();if(refs==null)return out;if(!refs.isArray()||refs.size()>10||refs.toString().length()>10000)throw new IllegalArgumentException("证据最多10项");
  for(var r:refs){try{
   if(!r.isObject()||!r.path("id").isIntegralNumber())throw new IllegalArgumentException("证据须引用合法ID");
   r.fieldNames().forEachRemaining(k->{if(!Set.of("type","id","startMs","endMs").contains(k))throw new IllegalArgumentException("非法证据字段");});
   Long course=e.getOffering().getCourse().getId();long id=r.path("id").asLong();
   switch(r.path("type").asText()){
    case "EVALUATION" -> {var other=parent(id,false);if(!Objects.equals(other.getOffering().getCourse().getId(),course))throw new IllegalArgumentException("证据不属于课程");}
    case "RESOURCE_VERSION" -> {var res=em.find(CourseResource.class,id);if(res==null||!Objects.equals(res.getCourse().getId(),course))throw new IllegalArgumentException("证据不属于课程");resourceAccess.requireRead(res);}
    case "MICRO_SLICE" -> {var slice=em.find(MicroTeachingSlice.class,id);if(slice==null||!Objects.equals(slice.getCourse().getId(),course)||slice.getAssetId()==null)throw new IllegalArgumentException("视频证据未验证或不属于课程");authorization.validateCourseRead(slice.getCourse());videos.video(id);if(r.has("startMs")||r.has("endMs")){if(!r.path("startMs").isIntegralNumber()||!r.path("endMs").isIntegralNumber()||r.path("startMs").asLong()<slice.getStartMs())throw new IllegalArgumentException("非法引用区间");VideoAssetService.range(r.path("startMs").asLong(),r.path("endMs").asLong(),slice.getEndMs());}}
    default -> throw new IllegalArgumentException("证据类型须为EVALUATION/RESOURCE_VERSION/MICRO_SLICE");
   }out.add(r);
  }catch(RuntimeException ex){if(!redact)throw ex;out.addObject().put("message","证据不可访问");}}
  return out;
 }
}

