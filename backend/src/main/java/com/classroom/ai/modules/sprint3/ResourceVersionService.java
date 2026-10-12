package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.classroom.ai.modules.resource.entity.CourseResource;
import com.classroom.ai.modules.resource.repository.CourseResourceRepository;
import com.classroom.ai.modules.resource.service.ResourceAccessService;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.config.UploadPaths;
import java.nio.file.*;
import java.security.*;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class ResourceVersionService {
 private final EntityManager em;private final CourseResourceRepository resources;private final ResourceAccessService access;
 @org.springframework.beans.factory.annotation.Value("${classroom.upload-dir:}") private String uploadDir;
 public CourseResource attach(CourseResource r,Long seriesId,Long expected) {
  access.requireWrite(r);
  ResourceSeries s;
  if(seriesId==null){s=new ResourceSeries();s.setCourseId(r.getCourse().getId());s.setShared(r.getIsPublic());s.setAccessEpoch(0L);s.setNextNumber(1);s.setRemoved(false);em.persist(s);em.flush();}
  else {
   s=em.find(ResourceSeries.class,seriesId,LockModeType.PESSIMISTIC_WRITE);
   if(s==null||Boolean.TRUE.equals(s.getRemoved()))throw new IllegalArgumentException("资源系列不存在");
   Sprint3Policy.version(s.getRevision(),expected);
   if(!Objects.equals(s.getCourseId(),r.getCourse().getId()))throw new IllegalArgumentException("版本必须属于同一课程");
   access.requireWrite(resources.findById(s.getCurrentVersionId()).orElseThrow());
  }
  r.setSeriesId(s.getId());r.setVersionNumber(s.getNextNumber());r.setVersion("v"+s.getNextNumber());r.setIsPublic(s.getShared());
  try {if(r.getFileUrl()==null||!r.getFileUrl().startsWith("/uploads/resources/"))throw new IllegalArgumentException("须上传受控文件");String name=r.getFileUrl().substring("/uploads/resources/".length());Path root=UploadPaths.resolveResources(uploadDir).toAbsolutePath().normalize();Path path=root.resolve(name).normalize();
   if(name.contains("/")||name.contains("\\")||!path.startsWith(root)||!Files.isRegularFile(path))throw new IllegalArgumentException("新版本受控文件不存在");r.setContentHash(hash(path));
  }catch(Exception ex){throw new IllegalArgumentException("无法读取新版本文件",ex);}
  resources.saveAndFlush(r);s.setCurrentVersionId(r.getId());s.setNextNumber(s.getNextNumber()+1);em.flush();return r;
 }
 public static String hash(Path p) throws java.io.IOException {
  try{MessageDigest md=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p)){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)md.update(b,0,n);}return HexFormat.of().formatHex(md.digest());}
  catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
 }
 public ResourceSeries policy(Long id) { var s=em.find(ResourceSeries.class,id);if(s==null)throw new IllegalArgumentException("系列不存在");return s; }
 public boolean current(CourseResource r) {if(r.getSeriesId()==null)return true;var s=policy(r.getSeriesId());return !Boolean.TRUE.equals(s.getRemoved())&&Objects.equals(s.getCurrentVersionId(),r.getId());}
 public CourseResource currentVersion(Long seriesId){var s=policy(seriesId);if(Boolean.TRUE.equals(s.getRemoved())||s.getCurrentVersionId()==null)throw new IllegalArgumentException("资源系列不可用");var r=resources.findById(s.getCurrentVersionId()).orElseThrow();access.requireRead(r);return r;}
 public Map<String,Object> history(Long id) {
  CourseResource r=resources.findById(id).orElseThrow();access.requireRead(r);
  ResourceSeries s=policy(r.getSeriesId());
  var list=em.createQuery("from CourseResource where seriesId=:id order by versionNumber",CourseResource.class).setParameter("id",s.getId()).getResultList();
  return Map.of("series",s,"versions",list);
 }
 public ResourceSeries select(Long id,Long versionId,Long expected) {
  Sprint3Policy.roles(RoleEnum.DIRECTOR);
  ResourceSeries s=em.find(ResourceSeries.class,id,LockModeType.PESSIMISTIC_WRITE);if(s==null)throw new IllegalArgumentException("系列不存在");
  Sprint3Policy.version(s.getRevision(),expected);
  CourseResource r=resources.findById(versionId).orElseThrow();access.requireWrite(r);
  if(Boolean.TRUE.equals(s.getRemoved())||!Objects.equals(r.getSeriesId(),id))throw new IllegalArgumentException("版本不属于有效系列");
  s.setTouchedAt(java.time.LocalDateTime.now());s.setCurrentVersionId(versionId);em.flush();return s;
 }
 public void sharing(CourseResource r,Boolean shared) {
  if(r.getSeriesId()==null)return;
  var s=em.find(ResourceSeries.class,r.getSeriesId(),LockModeType.PESSIMISTIC_WRITE);
  if(!Objects.equals(s.getShared(),shared)){s.setShared(shared);s.setAccessEpoch(s.getAccessEpoch()+1);}
 }
 public void remove(Long id) {
  var r=resources.findById(id).orElseThrow();access.requireWrite(r);
  if(r.getSeriesId()==null)throw new IllegalStateException("旧资源尚未初始化版本系列");
  var s=em.find(ResourceSeries.class,r.getSeriesId(),LockModeType.PESSIMISTIC_WRITE);s.setRemoved(true);s.setAccessEpoch(s.getAccessEpoch()+1);
 }
}

