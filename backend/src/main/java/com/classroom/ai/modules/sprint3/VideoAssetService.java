package com.classroom.ai.modules.sprint3;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.core.io.FileSystemResource;
import com.classroom.ai.modules.auth.context.AuthContext;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.course.service.CourseAuthorizationService;
import com.classroom.ai.modules.resource.entity.MicroTeachingSlice;
import com.classroom.ai.modules.resource.dto.MicroTeachingSliceDTO;
import com.classroom.ai.config.UploadPaths;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import java.time.*;
import java.util.concurrent.TimeUnit;
@Service @RequiredArgsConstructor @Transactional
public class VideoAssetService {
 private final EntityManager em;private final CourseRepository courses;private final CourseAuthorizationService authorization;private final ObjectMapper json;
 @org.springframework.beans.factory.annotation.Value("${classroom.upload-dir:}") private String uploadDir;
 @org.springframework.beans.factory.annotation.Value("${classroom.video.ffprobe:ffprobe}") private String ffprobe;
 private Path directory(){return UploadPaths.resolve(uploadDir).resolveSibling("micro").toAbsolutePath().normalize();}
 private void course(Long id,boolean write){
  var c=courses.findById(id).orElseThrow(()->new IllegalArgumentException("课程不存在"));
  if(AuthContext.getCurrentUser()!=null&&AuthContext.getCurrentUser().getRole()==RoleEnum.ADMIN)return;
  if(write)authorization.validateCourseWrite(c);else authorization.validateCourseRead(c);
 }
 public List<VideoAsset> assets(Long courseId){course(courseId,false);return em.createQuery("from VideoAsset where courseId=:id order by id",VideoAsset.class).setParameter("id",courseId).getResultList();}
 public record Source(String sourceType,String sourceDescription,String sourceReference,String authorizationDeclaration){}
 public static void validateSource(Source s){
  if(s==null||!Set.of("PLATFORM_UPLOAD","AUTHORIZED_IMPORT").contains(s.sourceType==null?"":s.sourceType))throw new IllegalArgumentException("非法资产来源类型");
  Sprint3Policy.text(s.sourceDescription,128,"来源说明");Sprint3Policy.text(s.authorizationDeclaration,1000,"授权声明");
  if(s.sourceReference!=null&&s.sourceReference.length()>512)throw new IllegalArgumentException("来源引用最多512字");
  if("AUTHORIZED_IMPORT".equals(s.sourceType)){
   String ref=Sprint3Policy.text(s.sourceReference,512,"来源引用");
   if(ref.startsWith("/")||ref.startsWith("\\")||ref.startsWith("./")||ref.startsWith("../")||ref.startsWith("file:")||ref.contains("\\")||ref.matches("^[A-Za-z]:.*")||(!ref.contains("://")&&ref.matches(".*[/].*\\.(?i:mp4|webm)$")))throw new IllegalArgumentException("来源引用不可为本地文件路径");
   if(ref.contains("://")){try{var uri=java.net.URI.create(ref);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new Exception();}catch(Exception ex){throw new IllegalArgumentException("来源URL仅允许HTTP(S)");}}
  }
 }
 public VideoAsset upload(Long courseId,MultipartFile file,Source source,String stage)throws java.io.IOException{
  course(courseId,true);validateSource(source);Sprint3Policy.stage(stage);
  if(file==null||file.isEmpty()||file.getSize()>104857600)throw new IllegalArgumentException("视频不能为空且不超过100MB");
  String name=Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
  String ext=name.endsWith(".mp4")?"mp4":name.endsWith(".webm")?"webm":null;
  if(ext==null)throw new IllegalArgumentException("仅支持MP4/WebM");
  byte[] signature;try(var input=file.getInputStream()){signature=input.readNBytes(16);}
  boolean valid=ext.equals("mp4")?signature.length>=12&&new String(signature,4,4,java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp"):signature.length>=4&&(signature[0]&255)==0x1a&&(signature[1]&255)==0x45&&(signature[2]&255)==0xdf&&(signature[3]&255)==0xa3;
  if(!valid)throw new IllegalArgumentException("文件内容不是有效视频容器");
  Files.createDirectories(directory());Path target=directory().resolve(UUID.randomUUID()+"."+ext);
  try{
   file.transferTo(target);long duration=probe(target);
   VideoAsset a=new VideoAsset();a.setCourseId(courseId);a.setStoredName(target.getFileName().toString());a.setSha256(ResourceVersionService.hash(target));a.setDurationMs(duration);
   a.setBopppsStage(stage);a.setSourceType(source.sourceType);a.setSourceDescription(source.sourceDescription.trim());a.setSourceReference(source.sourceReference);a.setAuthorizationDeclaration(source.authorizationDeclaration.trim());a.setSubmittedBy(AuthContext.getCurrentUser().getId());a.setSubmittedAt(LocalDateTime.now());em.persist(a);em.flush();
   if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{Files.deleteIfExists(target);}catch(Exception ignored){}}});
   return a;
  }catch(java.io.IOException|RuntimeException ex){Files.deleteIfExists(target);throw ex;}
 }
 public long probe(Path file)throws java.io.IOException{
  Process p;
  try{p=new ProcessBuilder(ffprobe,"-v","error","-select_streams","v:0","-show_entries","format=duration:stream=codec_type","-of","json",file.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();}
  catch(java.io.IOException ex){throw new HttpFailure(503,"视频探测工具不可用，请配置CLASSROOM_FFPROBE");}
  try{
   if(!p.waitFor(10,TimeUnit.SECONDS)){p.destroyForcibly();throw new HttpFailure(422,"视频时长探测超时");}
   byte[] result=p.getInputStream().readNBytes(16385);if(result.length>16384||p.exitValue()!=0)throw new HttpFailure(422,"视频无法探测");
   JsonNode node=json.readTree(result);double seconds=node.path("format").path("duration").asDouble(Double.NaN);
   if(!node.path("streams").isArray()||node.path("streams").isEmpty()||!Double.isFinite(seconds)||seconds<=0||seconds>86400)throw new HttpFailure(422,"缺少可信视频时长");
   return (long)Math.floor(seconds*1000);
  }catch(InterruptedException ex){p.destroyForcibly();Thread.currentThread().interrupt();throw new HttpFailure(422,"探测中断");}
 }
 public MicroTeachingSlice mount(MicroTeachingSliceDTO d){
  if(d==null||d.getCourseId()==null)throw new IllegalArgumentException("请指定课程");course(d.getCourseId(),true);Sprint3Policy.stage(d.getBopppsStage());
  if(d.getAssetId()==null)throw new IllegalArgumentException("先导入受控资产，不能直接挂载外链");
  VideoAsset asset=em.find(VideoAsset.class,d.getAssetId());if(asset==null||!Objects.equals(asset.getCourseId(),d.getCourseId()))throw new IllegalArgumentException("资产不存在或不属于课程");
  validateSource(new Source(asset.getSourceType(),asset.getSourceDescription(),asset.getSourceReference(),asset.getAuthorizationDeclaration()));
  range(d.getStartMs(),d.getEndMs(),asset.getDurationMs());verify(asset);
  MicroTeachingSlice s=d.getId()==null?new MicroTeachingSlice():em.find(MicroTeachingSlice.class,d.getId(),LockModeType.PESSIMISTIC_WRITE);
  if(s==null)throw new IllegalArgumentException("片段不存在");
  if(d.getId()!=null){Sprint3Policy.version(s.getRecordVersion(),d.getExpectedVersion());if(!Objects.equals(s.getCourse().getId(),d.getCourseId()))throw new IllegalArgumentException("不可改变片段课程");if(s.getAssetId()!=null&&!Objects.equals(s.getAssetId(),asset.getId()))throw new IllegalArgumentException("替换视频请创建新片段");}
  s.setTouchedAt(LocalDateTime.now());s.setCourse(courses.findById(d.getCourseId()).orElseThrow());s.setVideoTitle(Sprint3Policy.text(d.getVideoTitle(),255,"片段标题"));s.setBopppsStage(d.getBopppsStage());
  s.setAssetId(asset.getId());s.setStartMs(d.getStartMs());s.setEndMs(d.getEndMs());s.setDurationSeconds((int)Math.ceil((d.getEndMs()-d.getStartMs())/1000.0));s.setSliceUrl("/uploads/micro/"+asset.getStoredName());s.setSourceAgent(asset.getSourceDescription());
  if(d.getAnalysisMetadata()!=null){validateAnalysis(d.getAnalysisMetadata(),asset.getDurationMs());s.setAnalysisJson(d.getAnalysisMetadata().toString());s.setAnalysisReviewedBy(null);s.setAnalysisReviewedAt(null);}
  if(d.getId()==null)em.persist(s);em.flush();return view(s);
 }
 public static void range(Long start,Long end,Long duration){if(start==null||end==null||duration==null||start<0||start>=end||end>duration)throw new IllegalArgumentException("区间须满足0≤起点<终点≤服务端时长（毫秒）");}
 public static void validateAnalysis(JsonNode n,long duration){
  if(!n.isObject()||n.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768)throw new IllegalArgumentException("分析元数据须为对象且不超过32KiB");
  fields(n,Set.of("schemaVersion","source","observedAt","metrics","note"));if(!n.path("schemaVersion").isIntegralNumber()||n.path("schemaVersion").asInt()!=1)throw new IllegalArgumentException("未知元数据版本");
  if(!n.path("source").isTextual()||!n.path("observedAt").isTextual()||(n.hasNonNull("note")&&!n.path("note").isTextual()))throw new IllegalArgumentException("来源、观测时间和备注须为文本");
  Sprint3Policy.text(n.path("source").asText(null),128,"分析来源");
  try{OffsetDateTime.parse(n.path("observedAt").asText());}catch(Exception ex){throw new IllegalArgumentException("观测时间需含时区");}
  if(n.path("note").asText("").length()>1000||!n.path("metrics").isArray()||n.path("metrics").size()>100)throw new IllegalArgumentException("元数据项数超限");
  for(JsonNode m:n.path("metrics")){
   fields(m,Set.of("name","value","unit","startMs","endMs","confidence"));double v=m.path("value").asDouble(Double.NaN);
   if(!Set.of("attendanceRate","lookUpRate").contains(m.path("name").asText())||!"percent".equals(m.path("unit").asText())||!m.path("value").isNumber()||!Double.isFinite(v)||v<0||v>100)throw new IllegalArgumentException("非法分析指标");
   if(!m.path("startMs").isIntegralNumber()||!m.path("endMs").isIntegralNumber())throw new IllegalArgumentException("区间需为整数毫秒");
   range(m.path("startMs").asLong(),m.path("endMs").asLong(),duration);
   if(m.hasNonNull("confidence")){double c=m.path("confidence").asDouble(Double.NaN);if(!m.path("confidence").isNumber()||!Double.isFinite(c)||c<0||c>1)throw new IllegalArgumentException("置信度范围0～1");}
  }
 }
 private static void fields(JsonNode n,Set<String> allowed){if(!n.isObject())throw new IllegalArgumentException("元数据项须为对象");n.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw new IllegalArgumentException("未知字段:"+k);});}
 public MicroTeachingSlice review(Long id){Sprint3Policy.roles(RoleEnum.ADMIN);var s=em.find(MicroTeachingSlice.class,id,LockModeType.PESSIMISTIC_WRITE);if(s==null||s.getAnalysisJson()==null)throw new IllegalArgumentException("无分析数据可复核");s.setAnalysisReviewedBy(AuthContext.getCurrentUser().getId());s.setAnalysisReviewedAt(LocalDateTime.now());return s;}
 public MicroTeachingSlice view(MicroTeachingSlice s){MicroTeachingSlice out=new MicroTeachingSlice();org.springframework.beans.BeanUtils.copyProperties(s,out);if(AuthContext.getCurrentUser().getRole()!=RoleEnum.ADMIN&&s.getAnalysisReviewedAt()==null)out.setAnalysisJson(null);out.setAnalysisReviewedBy(null);return out;}
 public List<MicroTeachingSlice> list(Long id){course(id,false);return em.createQuery("from MicroTeachingSlice where course.id=:id order by id",MicroTeachingSlice.class).setParameter("id",id).getResultList().stream().map(this::view).toList();}
 public void remove(Long id){var s=em.find(MicroTeachingSlice.class,id);if(s==null)throw new IllegalArgumentException("片段不存在");course(s.getCourse().getId(),true);em.remove(s);}
 private Path verify(VideoAsset a){
  try{Path p=directory().resolve(a.getStoredName()).normalize();if(!p.startsWith(directory())||!Files.isRegularFile(p)||!Objects.equals(a.getSha256(),ResourceVersionService.hash(p)))throw new IllegalStateException("视频文件变化或不存在，请重新导入");return p;}
  catch(java.io.IOException ex){throw new HttpFailure(422,"无法读取视频文件");}
 }
 public ResponseEntity<FileSystemResource> video(Long id){
  var s=em.find(MicroTeachingSlice.class,id);if(s==null)throw new IllegalArgumentException("片段不存在");course(s.getCourse().getId(),false);
  if(s.getAssetId()==null)throw new HttpFailure(422,"历史视频未验证，请补录并导入");
  var a=em.find(VideoAsset.class,s.getAssetId());validateSource(new Source(a.getSourceType(),a.getSourceDescription(),a.getSourceReference(),a.getAuthorizationDeclaration()));Sprint3Policy.stage(s.getBopppsStage());range(s.getStartMs(),s.getEndMs(),a.getDurationMs());Path p=verify(a);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(a.getStoredName().endsWith(".mp4")?"video/mp4":"video/webm")).header("X-Content-Type-Options","nosniff").body(new FileSystemResource(p));
 }
}

