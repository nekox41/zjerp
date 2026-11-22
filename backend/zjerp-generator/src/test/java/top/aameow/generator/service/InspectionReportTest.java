package top.aameow.generator.service;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;import java.nio.file.*;
import org.apache.pdfbox.Loader;import org.apache.pdfbox.text.PDFTextStripper;
class InspectionReportTest {
 @Test void rendersLongReportWithoutLosingText()throws Exception{
  System.setProperty("zjerp.report.font","../wbcard/font/NotoSansSC-Regular.ttf"); InspectionReportService service=new InspectionReportService();
  var project=new HashMap<String,Object>();project.put("projectName","测试建筑");project.put("detailsJson","{}");
  var system=new HashMap<String,Object>();system.put("name","消火栓系统");system.put("status","tested");system.put("description","长说明自动分页验证。".repeat(500)+"末尾完整标记");system.put("items",List.of(Map.of("name","消火栓","status","tested","requirement","标准要求","location","一号楼","result","实测内容","verdict","fail")));
  var v=new HashMap<String,Object>();v.put("project",project);v.put("reportNo","TEST-001");v.put("reportDate","2026-11-01");v.put("credentials",List.of());v.put("systems",List.of(system));v.put("photos",List.of());
  byte[] pdf=service.render(v);try(var doc=Loader.loadPDF(pdf)){String text=new PDFTextStripper().getText(doc);assertTrue(doc.getNumberOfPages()>3);assertTrue(text.contains("末尾完整标记"));assertTrue(text.contains("不合格"));assertTrue(text.contains("TEST-001"));assertNull(doc.getDocumentCatalog().getAcroForm());}
  Path out=Paths.get("target/report-sample.pdf");Files.createDirectories(out.getParent());Files.write(out,pdf);
  InspectionReportService withCertificate=org.mockito.Mockito.spy(service);org.mockito.Mockito.doReturn(out).when(withCertificate).source("/certificate.pdf");v.put("credentials",List.of(Map.of("title","资质原件","holder","测试人员","files","/certificate.pdf")));byte[] merged=withCertificate.render(v);
  try(var doc=Loader.loadPDF(merged)){String t=new PDFTextStripper().getText(doc);assertTrue(t.contains("末尾完整标记"));for(int i=0;i<doc.getNumberOfPages();i++)new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(i,30);}

 }
 @Test void refusesUnboundedOrMalformedRows(){assertThrows(RuntimeException.class,()->InspectionReportService.rows(Map.of("systems",List.of("bad")),"systems"));assertThrows(RuntimeException.class,()->InspectionReportService.rows(Map.of("systems",Collections.nCopies(301,Map.of())),"systems"));}
}
