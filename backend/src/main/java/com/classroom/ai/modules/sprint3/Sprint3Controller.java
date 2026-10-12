package com.classroom.ai.modules.sprint3;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import com.classroom.ai.common.ApiResponse;
import com.classroom.ai.modules.auth.entity.RoleEnum;
import com.classroom.ai.modules.course.repository.CourseRepository;
import com.classroom.ai.modules.resource.dto.MicroTeachingSliceDTO;
import java.util.*;
import java.io.IOException;
@RestController @RequiredArgsConstructor
public class Sprint3Controller {
 private final EvaluationTemplateService templates;private final QualityStatisticsService quality;private final ResourceVersionService versions;private final VideoAssetService video;private final AdviceService advice;private final CourseRepository courses;
 @GetMapping("/api/v1/sprint3/templates") public Object templates(){return ApiResponse.success(templates.published());}
 @GetMapping("/api/v1/admin/templates") public Object drafts(){return ApiResponse.success(templates.drafts());}
 @PostMapping("/api/v1/admin/templates") public Object create(@RequestBody EvaluationTemplateService.Input in){return ApiResponse.success(templates.save(null,in));}
 @PutMapping("/api/v1/admin/templates/{id}") public Object update(@PathVariable Long id,@RequestBody EvaluationTemplateService.Input in){return ApiResponse.success(templates.save(id,in));}
 public record Revision(Long expectedVersion){}
 @PostMapping("/api/v1/admin/templates/{id}/publish") public Object publish(@PathVariable Long id,@RequestBody Revision r){return ApiResponse.success(templates.publish(id,r.expectedVersion));}
 @GetMapping("/api/v1/admin/courses") public Object catalog(){Sprint3Policy.roles(RoleEnum.ADMIN);return ApiResponse.success(courses.findAll().stream().map(c->Map.of("id",c.getId(),"courseCode",c.getCourseCode(),"courseName",c.getCourseName())).toList());}
 @GetMapping("/api/v1/resources/{id}/history") public Object history(@PathVariable Long id){return ApiResponse.success(versions.history(id));}
 public record Current(Long versionId,Long expectedVersion){}
 @GetMapping("/api/v1/resources/series/{id}/current") public Object currentVersion(@PathVariable Long id){return ApiResponse.success(versions.currentVersion(id));}
 @PutMapping("/api/v1/resources/series/{id}/current") public Object current(@PathVariable Long id,@RequestBody Current r){return ApiResponse.success(versions.select(id,r.versionId,r.expectedVersion));}
 @GetMapping({"/api/v1/admin/micro-assets","/api/v1/resources/micro-assets"}) public Object assets(@RequestParam Long courseId){return ApiResponse.success(video.assets(courseId));}
 @PostMapping({"/api/v1/admin/micro-assets","/api/v1/resources/micro-assets"}) public Object upload(@RequestParam Long courseId,@RequestParam MultipartFile file,@RequestParam(required=false) String sourceType,@RequestParam(required=false) String sourceDescription,@RequestParam(required=false) String sourceReference,@RequestParam(required=false) String authorizationDeclaration,@RequestParam(required=false) String bopppsStage)throws IOException{return ApiResponse.success(video.upload(courseId,file,new VideoAssetService.Source(sourceType,sourceDescription,sourceReference,authorizationDeclaration),bopppsStage));}
 @GetMapping("/api/v1/admin/micro-slices") public Object slices(@RequestParam Long courseId){return ApiResponse.success(video.list(courseId));}
 @PostMapping("/api/v1/admin/micro-slices") public Object mount(@RequestBody MicroTeachingSliceDTO in){return ApiResponse.success(video.mount(in));}
 @PutMapping({"/api/v1/admin/micro-slices/{id}","/api/v1/resources/micro-slices/{id}"}) public Object edit(@PathVariable Long id,@RequestBody MicroTeachingSliceDTO in){in.setId(id);return ApiResponse.success(video.mount(in));}
 @DeleteMapping("/api/v1/admin/micro-slices/{id}") public Object remove(@PathVariable Long id){video.remove(id);return ApiResponse.success("片段已移除",null);}
 @PostMapping("/api/v1/admin/micro-slices/{id}/review") public Object review(@PathVariable Long id){return ApiResponse.success(video.review(id));}
 @GetMapping("/api/v1/admin/micro-slices/{id}/video") public Object adminVideo(@PathVariable Long id){return video.video(id);}
 private QualityStatisticsService.Filter filter(String term,String courseType,String keyword,String teacher,boolean uncategorized){return new QualityStatisticsService.Filter(term,courseType,keyword,teacher,uncategorized);}
 @GetMapping("/api/v1/sprint3/quality") public Object quality(@RequestParam(required=false) String term,@RequestParam(required=false) String courseType,@RequestParam(required=false) String keyword,@RequestParam(required=false) String teacher,@RequestParam(defaultValue="false") boolean uncategorized,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){return ApiResponse.success(quality.report(filter(term,courseType,keyword,teacher,uncategorized),page,size,false));}
 @GetMapping("/api/v1/sprint3/radar") public Object radar(@RequestParam(required=false) String term,@RequestParam(required=false) String courseType,@RequestParam(required=false) String keyword){return ApiResponse.success(quality.radar(filter(term,courseType,keyword,null,false)));}
 @GetMapping("/api/v1/sprint3/alerts") public Object alerts(@RequestParam(required=false) String term,@RequestParam(required=false) String courseType,@RequestParam(required=false) String keyword,@RequestParam(required=false) String teacher,@RequestParam(defaultValue="false") boolean uncategorized){return ApiResponse.success(quality.alerts(filter(term,courseType,keyword,teacher,uncategorized)));}
 @GetMapping("/api/v1/sprint3/problems") public Object problems(@RequestParam(required=false) String term,@RequestParam(required=false) String courseType,@RequestParam(defaultValue="false") boolean uncategorized){return ApiResponse.success(quality.problems(filter(term,courseType,null,null,uncategorized)));}
 @GetMapping("/api/v1/sprint3/quality/export") public ResponseEntity<byte[]> export(@RequestParam(defaultValue="xlsx") String format,@RequestParam(required=false) String term,@RequestParam(required=false) String courseType,@RequestParam(required=false) String keyword,@RequestParam(required=false) String teacher,@RequestParam(defaultValue="false") boolean uncategorized)throws IOException{
  Sprint3Policy.roles(RoleEnum.DIRECTOR);if(!Set.of("xlsx","csv").contains(format))throw new IllegalArgumentException("格式须为xlsx/csv");
  return exportResponse(quality.export(filter(term,courseType,keyword,teacher,uncategorized),format),format);
 }
 public static ResponseEntity<byte[]> exportResponse(byte[] bytes,String format){
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=teaching-quality."+format).contentType(MediaType.parseMediaType(format.equals("csv")?"text/csv;charset=UTF-8":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes);
 }
 @GetMapping("/api/v1/supervisions/{id}/suggestions") public Object list(@PathVariable Long id){return ApiResponse.success(advice.list(id));}
 @PostMapping("/api/v1/supervisions/{id}/suggestions") public Object receive(@PathVariable Long id,@RequestBody AdviceService.AdviceInput in){return ApiResponse.success(advice.receive(id,in));}
 @GetMapping("/api/v1/sprint3/suggestions/mine") public Object mine(){return ApiResponse.success(advice.mine());}
 @PutMapping("/api/v1/suggestions/{id}/my-feedback") public Object feedback(@PathVariable Long id,@RequestBody AdviceService.FeedbackInput in){return ApiResponse.success(advice.feedback(id,in));}
}

