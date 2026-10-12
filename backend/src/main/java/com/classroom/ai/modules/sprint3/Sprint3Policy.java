package com.classroom.ai.modules.sprint3;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import java.time.LocalDateTime;
import java.util.*;
public final class Sprint3Policy {
 private Sprint3Policy() {}
 public static final List<String> PROBLEMS=List.of("目标说明","内容组织","教学互动","教学方法","学习效果","其他");
 public static final Set<String> STAGES=Set.of("B","O","P1","P2","P3","S");
 public static void roles(RoleEnum... roles) { AuthContext.requireRole(roles); }
 public static boolean approved(SupervisionEvaluation e) {
  return e.getReviewedBy()!=null && e.getReviewedAt()!=null &&
   Set.of("APPROVED_PENDING","PUBLISHED").contains(e.getStatus()==null?"":e.getStatus());
 }
 public static boolean feedbackOpen(SupervisionEvaluation e,LocalDateTime now) {
  return approved(e) && e.getPublishTime()!=null && !e.getPublishTime().isAfter(now);
 }
 public static String text(String s,int max,String label) {
  if(s==null || s.isBlank() || s.length()>max) throw new IllegalArgumentException(label+"必填，最多"+max+"字");
  return s.trim();
 }
 public static void stage(String s) { if(!STAGES.contains(s==null?"":s)) throw new IllegalArgumentException("教学环节必须为B/O/P1/P2/P3/S"); }
 public static void version(Long actual,Long expected) {
  if(expected==null || !Objects.equals(actual,expected)) throw new IllegalStateException("记录已变化，请刷新后重试");
 }
 public static java.util.List<String> problems(java.util.List<String> input) {
  if(input==null) return List.of();
  if(input.size()>20 || input.stream().anyMatch(s->!PROBLEMS.contains(s))) throw new IllegalArgumentException("非法问题标签");
  return input.stream().distinct().toList();
 }
}

