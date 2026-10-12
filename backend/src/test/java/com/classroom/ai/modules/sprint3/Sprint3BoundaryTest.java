package com.classroom.ai.modules.sprint3;
import com.classroom.ai.modules.supervision.entity.SupervisionEvaluation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
class Sprint3BoundaryTest {
 final ObjectMapper json=new ObjectMapper();
 TemplateVersion template(String... values){TemplateVersion v=new TemplateVersion();v.setAttitude(new BigDecimal(values[0]));v.setContent(new BigDecimal(values[1]));v.setMethod(new BigDecimal(values[2]));v.setEffect(new BigDecimal(values[3]));return v;}
 @Test void auditAndFeedbackAreSeparate(){var e=new SupervisionEvaluation();e.setStatus("APPROVED_PENDING");e.setReviewedBy(1L);e.setReviewedAt(LocalDateTime.now());e.setPublishTime(LocalDateTime.now().plusHours(24));assertTrue(Sprint3Policy.approved(e));assertFalse(Sprint3Policy.feedbackOpen(e,LocalDateTime.now()));assertTrue(Sprint3Policy.feedbackOpen(e,e.getPublishTime()));e.setReviewedAt(null);assertFalse(Sprint3Policy.approved(e));}
 @Test void forgedPublishedStateNeverOpensFeedback(){var e=new SupervisionEvaluation();e.setStatus("PUBLISHED");assertFalse(Sprint3Policy.feedbackOpen(e,LocalDateTime.now()));}
 @Test void exactDecimalWeights(){EvaluationTemplateService.validate(new BigDecimal("33.333"),new BigDecimal("33.333"),new BigDecimal("33.334"),BigDecimal.ZERO);assertThrows(IllegalArgumentException.class,()->EvaluationTemplateService.validate(new BigDecimal("25"),new BigDecimal("25"),new BigDecimal("25"),new BigDecimal("24.999")));}
 @Test void missingAndZeroAreDifferent(){var v=template("25","25","25","25");assertNull(EvaluationTemplateService.total(new Double[]{null,0d,0d,0d},v,true));assertEquals(0d,EvaluationTemplateService.total(new Double[]{0d,0d,0d,0d},v,false));assertThrows(IllegalArgumentException.class,()->EvaluationTemplateService.total(new Double[]{null,0d,0d,0d},v,false));}
 @Test void zeroWeightDoesNotRequireScore(){var v=template("0","40","30","30");assertEquals(100d,EvaluationTemplateService.total(new Double[]{null,40d,30d,30d},v,false));assertThrows(IllegalArgumentException.class,()->EvaluationTemplateService.total(new Double[]{1d,40d,30d,30d},v,false));}
 @Test void invalidScoresRejected(){var v=template("25","25","25","25");for(double x:new double[]{-1,26,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->EvaluationTemplateService.total(new Double[]{x,0d,0d,0d},v,false));}
 @Test void decimalScoresUseAnExactDecimalSum(){assertEquals(0.6d,EvaluationTemplateService.total(new Double[]{0.1d,0.2d,0.3d,0d},template("25","25","25","25"),false));}
 @Test void clientCannotWriteHistoryOrTeacherIdentity(){for(String extra:List.of("\"history\":[]","\"teacherUserId\":99","\"revision\":999"))assertThrows(Exception.class,()->json.readValue("{\"expectedVersion\":0,\"decision\":\"ACCEPTED\","+extra+"}",AdviceService.FeedbackInput.class));}
 @Test void staleVersionRejected(){Sprint3Policy.version(2L,2L);assertThrows(IllegalStateException.class,()->Sprint3Policy.version(2L,1L));assertThrows(IllegalStateException.class,()->Sprint3Policy.version(2L,null));}
 @Test void sixStagesOnly(){for(String s:Sprint3Policy.STAGES)Sprint3Policy.stage(s);for(String s:Arrays.asList(null,"","P","UNKNOWN"))assertThrows(IllegalArgumentException.class,()->Sprint3Policy.stage(s));}
 @Test void assetSourcesAreMandatory(){for(var s:List.of(new VideoAssetService.Source("PLATFORM_UPLOAD","","","授权"),new VideoAssetService.Source("AUTHORIZED_IMPORT","来源","","授权"),new VideoAssetService.Source("AUTHORIZED_IMPORT","来源","file:///secret","授权"),new VideoAssetService.Source("PLATFORM_UPLOAD","来源","","")))assertThrows(IllegalArgumentException.class,()->VideoAssetService.validateSource(s));VideoAssetService.validateSource(new VideoAssetService.Source("AUTHORIZED_IMPORT","本人合成","https://example.invalid/sample","已授权合成样例"));}
 @Test void millisecondRangeBoundaries(){VideoAssetService.range(0L,1000L,1000L);for(long[] r:new long[][]{{-1,1},{0,0},{1000,1001},{500,400}})assertThrows(IllegalArgumentException.class,()->VideoAssetService.range(r[0],r[1],1000L));}
 @Test void analysisStructureAndLimits()throws Exception{
  String good="{\"schemaVersion\":1,\"source\":\"合成样例\",\"observedAt\":\"2026-10-11T10:00:00+08:00\",\"metrics\":[{\"name\":\"lookUpRate\",\"value\":50,\"unit\":\"percent\",\"startMs\":0,\"endMs\":1000,\"confidence\":null}]}";
  VideoAssetService.validateAnalysis(json.readTree(good),1000);
  for(String bad:List.of(good.replace("percent","seconds"),good.replace("\"value\":50","\"value\":101"),good.replace("\"startMs\":0","\"startMs\":0.5"),good.replace("\"confidence\":null","\"confidence\":2"),good.replace("\"source\":","\"unknown\":1,\"source\":")))assertThrows(IllegalArgumentException.class,()->VideoAssetService.validateAnalysis(json.readTree(bad),1000));
 }
 @Test void problemTagsDeduplicateAndRejectUnknown(){assertEquals(List.of("教学互动"),Sprint3Policy.problems(List.of("教学互动","教学互动")));assertThrows(IllegalArgumentException.class,()->Sprint3Policy.problems(List.of("猜测分类")));}
 @Test void csvEscapingAndFormulaDefense(){assertEquals("\"a,\"\"b\"\"\nc\"",QualityReportWriter.csvCell("a,\"b\"\nc"));assertEquals("\"'   =SUM(1,2)\"",QualityReportWriter.csvCell("   =SUM(1,2)"));assertEquals("\"-2\"",QualityReportWriter.csvCell(-2));}
 @Test void xlsxHasTwoSafeWorksheets()throws Exception{
  var row=new LinkedHashMap<String,Object>();row.put("courseName","=SUM(1,2)<>&\u0001");byte[] bytes=QualityReportWriter.xlsx(List.of(row),List.of(),"2026-10-11");int sheets=0;
  var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
  try(var z=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=z.getNextEntry())!=null){byte[] data=z.readAllBytes();if(e.getName().endsWith(".xml")||e.getName().endsWith(".rels"))factory.newDocumentBuilder().parse(new ByteArrayInputStream(data));if(e.getName().startsWith("xl/worksheets/")){sheets++;String xml=new String(data,java.nio.charset.StandardCharsets.UTF_8);assertFalse(xml.contains("<f>"));assertFalse(xml.contains("\u0001"));}}}
  assertEquals(2,sheets);
 }
}

