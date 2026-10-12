package com.classroom.ai.modules.sprint3;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
public final class QualityReportWriter {
 private QualityReportWriter(){}
 private static final String[] HEAD={"开课ID","课程代码","课程名称","课程类别","任课教师","模板版本","审核样本数","有效总分样本数","总分均值","旧版原分均值","评价编号"};
 public static List<List<Object>> rows(List<Map<String,Object>> data){
  List<List<Object>> rows=new ArrayList<>();rows.add(new ArrayList<>(Arrays.asList(HEAD)));
  for(var r:data)rows.add(Arrays.asList(r.get("offeringId"),r.get("courseCode"),r.get("courseName"),r.get("courseType"),r.get("teacher"),r.get("template"),r.get("evaluationCount"),r.get("validTotalCount"),r.get("average"),r.get("legacyAverage"),r.get("evaluationIds")));
  return rows;
 }
 public static String csvCell(Object o){
  String s=o==null?"":o.toString();String stripped=s.stripLeading();
  if(!stripped.isEmpty()&&"=+-@".indexOf(stripped.charAt(0))>=0&&!(o instanceof Number))s="'"+s;
  return "\"" + s.replace("\"","\"\"") + "\"";
 }
 public static byte[] csv(List<Map<String,Object>> data){
  StringBuilder b=new StringBuilder("\uFEFF");for(var row:rows(data))b.append(String.join(",",row.stream().map(QualityReportWriter::csvCell).toList())).append("\r\n");
  return b.toString().getBytes(StandardCharsets.UTF_8);
 }
 public static byte[] csv(List<Map<String,Object>> data,String asOf){
  byte[] content=csv(data);String first="\uFEFF"+csvCell("快照时间")+","+csvCell(asOf)+"\r\n";return (first+new String(content,StandardCharsets.UTF_8).substring(1)).getBytes(StandardCharsets.UTF_8);
 }
 public static String xml(String s){
  StringBuilder b=new StringBuilder();s.codePoints().filter(c->c==9||c==10||c==13||(c>=32&&c<=0xD7FF)||(c>=0xE000&&c<=0xFFFD)||(c>=0x10000&&c<=0x10FFFF)).forEach(b::appendCodePoint);
  return b.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
 }
 public static byte[] xlsx(List<Map<String,Object>> summary,List<Map<String,Object>> details,String asOf)throws IOException{
  List<List<Object>> first=rows(summary);first.add(0,Arrays.asList("快照时间",asOf));
  List<List<Object>> second=new ArrayList<>();second.add(Arrays.asList("快照时间",asOf));second.add(Arrays.asList("评价ID","开课ID","课程代码","课程类别","模板版本","权重快照","四维原分","总分","有效总分","反馈开放","状态"));
  for(var d:details)second.add(Arrays.asList(d.get("evaluationId"),d.get("offeringId"),d.get("courseCode"),d.get("courseType"),d.get("templateVersionId"),d.get("weights"),d.get("scores"),d.get("total"),d.get("validTotal"),d.get("feedbackOpen"),d.get("status")));
  try(var bytes=new ByteArrayOutputStream();var zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)){
   put(zip,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
   put(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
   put(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"汇总\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"评价明细\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
   put(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/></Relationships>");
   put(zip,"xl/worksheets/sheet1.xml",sheet(first));put(zip,"xl/worksheets/sheet2.xml",sheet(second));zip.finish();return bytes.toByteArray();
  }
 }
 private static void put(ZipOutputStream z,String path,String content)throws IOException{z.putNextEntry(new ZipEntry(path));z.write(content.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
 private static String sheet(List<List<Object>> rows){
  StringBuilder b=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"2\" topLeftCell=\"A3\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"11\" width=\"24\" customWidth=\"1\"/></cols><sheetData>");
  int r=0;for(var row:rows){r++;b.append("<row r=\"").append(r).append("\">");int c=0;for(Object value:row){String ref=column(++c)+r;if(value instanceof Number)b.append("<c r=\"").append(ref).append("\" t=\"n\"><v>").append(value).append("</v></c>");else b.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value==null?"":value.toString())).append("</t></is></c>");}b.append("</row>");}
  return b.append("</sheetData></worksheet>").toString();
 }
 private static String column(int n){StringBuilder b=new StringBuilder();while(n>0){n--;b.insert(0,(char)('A'+n%26));n/=26;}return b.toString();}
}

