package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.auth.context.AuthContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class EvaluationTemplateService {
 private final EntityManager em;
 public record Input(Long expectedVersion,String name,String scenario,String descriptions,BigDecimal attitude,BigDecimal content,BigDecimal method,BigDecimal effect,Long basedOnVersionId) {}
 public List<TemplateVersion> published() {
  Sprint3Policy.roles(RoleEnum.ADMIN,RoleEnum.SUPERVISOR,RoleEnum.DIRECTOR,RoleEnum.TEACHER);
  return em.createQuery("from TemplateVersion order by id",TemplateVersion.class).getResultList();
 }
 public List<TemplateDraft> drafts() { Sprint3Policy.roles(RoleEnum.ADMIN); return em.createQuery("from TemplateDraft order by id",TemplateDraft.class).getResultList(); }
 public TemplateDraft save(Long id,Input in) {
  Sprint3Policy.roles(RoleEnum.ADMIN); validate(in.attitude,in.content,in.method,in.effect);
  TemplateDraft d=id==null?new TemplateDraft():em.find(TemplateDraft.class,id,LockModeType.PESSIMISTIC_WRITE);
  if(d==null) throw new IllegalArgumentException("模板草稿不存在");
  if(id!=null) { Sprint3Policy.version(d.getRevision(),in.expectedVersion); if(d.getPublishedId()!=null) throw new IllegalStateException("已发布草稿不可修改，请创建新草稿"); }
  d.setTouchedAt(LocalDateTime.now());d.setName(Sprint3Policy.text(in.name,128,"名称")); d.setScenario(Sprint3Policy.text(in.scenario,128,"场景"));
  d.setDescriptions(Sprint3Policy.text(in.descriptions,2000,"四维说明"));
  d.setAttitude(in.attitude);d.setContent(in.content);d.setMethod(in.method);d.setEffect(in.effect);
  if(id==null){if(in.basedOnVersionId!=null)resolve(in.basedOnVersionId);d.setBasedOnVersionId(in.basedOnVersionId);em.persist(d);} em.flush();return d;
 }
 public TemplateVersion publish(Long id,Long expected) {
  Sprint3Policy.roles(RoleEnum.ADMIN);
  TemplateDraft d=em.find(TemplateDraft.class,id,LockModeType.PESSIMISTIC_WRITE);
  if(d==null) throw new IllegalArgumentException("草稿不存在");
  Sprint3Policy.version(d.getRevision(),expected);
  if(d.getPublishedId()!=null) throw new IllegalStateException("模板已发布");
  validate(d.getAttitude(),d.getContent(),d.getMethod(),d.getEffect());
  Long family=-id;
  if(d.getBasedOnVersionId()!=null){var parent=resolve(d.getBasedOnVersionId());family=parent.getFamilyId();var root=em.createQuery("from TemplateVersion where familyId=:family order by number",TemplateVersion.class).setParameter("family",family).getResultList().get(0);em.lock(root,LockModeType.PESSIMISTIC_WRITE);}
  int number=em.createQuery("select coalesce(max(number),0) from TemplateVersion where familyId=:family",Integer.class).setParameter("family",family).getSingleResult()+1;
  TemplateVersion v=new TemplateVersion();v.setFamilyId(family);v.setDraftId(id);v.setNumber(number);v.setName(d.getName());v.setScenario(d.getScenario());v.setDescriptions(d.getDescriptions());
  v.setAttitude(d.getAttitude());v.setContent(d.getContent());v.setMethod(d.getMethod());v.setEffect(d.getEffect());
  v.setPublishedAt(LocalDateTime.now());v.setPublishedBy(AuthContext.getCurrentUser().getId());em.persist(v);em.flush();d.setPublishedId(v.getId());em.flush();return v;
 }
 public TemplateVersion resolve(Long id) {
  if(id==null) throw new IllegalArgumentException("请选择已发布模板版本");
  TemplateVersion v=em.find(TemplateVersion.class,id);
  if(v==null) throw new IllegalArgumentException("已发布模板版本不存在");
  return v;
 }
 public static void validate(BigDecimal... w) {
  if(w.length!=4 || Arrays.stream(w).anyMatch(x->x==null || x.signum()<0 || x.scale()>3) ||
    Arrays.stream(w).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(new BigDecimal("100"))!=0)
   throw new IllegalArgumentException("四维权重必须非负、最多三位小数且精确合计100%");
 }
 public static Double total(Double[] scores,TemplateVersion v,boolean draft) {
  BigDecimal[] weights={v.getAttitude(),v.getContent(),v.getMethod(),v.getEffect()};
  boolean complete=true; BigDecimal total=BigDecimal.ZERO;
  for(int i=0;i<4;i++) {
   Double s=scores[i]; BigDecimal w=weights[i];
   if(s==null){if(w.signum()>0)complete=false;continue;}
   if(!Double.isFinite(s)||s<0||BigDecimal.valueOf(s).compareTo(w)>0) throw new IllegalArgumentException("评分超出所引用模板权重范围");
   total=total.add(BigDecimal.valueOf(s));
  }
  if(!draft&&!complete) throw new IllegalArgumentException("正式提交须填写全部正权重维度，0分合法");
  return complete?total.doubleValue():null;
 }
 public Map<String,BigDecimal> weights(TemplateVersion v) { return Map.of("attitude",v.getAttitude(),"content",v.getContent(),"method",v.getMethod(),"effect",v.getEffect()); }
}

