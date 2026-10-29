package top.aameow.generator.util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;
import top.aameow.generator.domain.WBCard;
import top.aameow.common.exception.ServiceException;
class WBCardPdfTest {
 private final Path root=Paths.get("..").toAbsolutePath().normalize();
 private WBCardUtils renderer(){return new WBCardUtils(root.resolve("wbcard/templates/card.png"),root.resolve("wbcard/font/NotoSansSC-Regular.ttf"));}
 private WBCard sample(){WBCard c=new WBCard();c.setNo("202610290001");c.setName("郑州市高新技术产业开发区综合服务中心一号楼及地下停车场消防设施维护保养项目");c.setCompany("河南众基消防技术有限公司");c.setLeader("张三、李四");c.setPhone("0371-12345678 / 13800138000");c.setSystem1("火灾自动报警系统、火灾应急照明及疏散指示系统、消防电话系统、消防广播系统、消防电梯系统、消防供电设施、消防供水系统、自动喷水灭火系统、消火栓系统、机械排烟系统、机械加压送风系统、防火分隔设施、电气火灾监控系统、气体灭火系统、灭火器（检查）");c.setTime("2026年10月29日 至 2027年10月28日");return c;}
 @Test void fullUnicodeTextSurvivesMeasuredWrapping() throws Exception {
  WBCard c=sample();byte[] bytes=renderer().render(c);Path out=Paths.get("target/wbcard-proof");Files.createDirectories(out);Files.write(out.resolve("long-content.pdf"),bytes);
  try(var pdf=Loader.loadPDF(bytes)){assertEquals(1,pdf.getNumberOfPages());assertNull(pdf.getDocumentCatalog().getAcroForm());String text=new PDFTextStripper().getText(pdf).replaceAll("\\s","");for(String v:new String[]{c.getName(),c.getCompany(),c.getLeader(),c.getPhone(),c.getSystem1(),c.getTime(),c.getNo()})assertTrue(text.contains(v.replaceAll("\\s","")),v);ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0,120),"png",out.resolve("long-content.png").toFile());}
 }
 @Test void refusesOverflowInsteadOfDroppingText(){WBCard c=sample();c.setName("非常长的项目名称".repeat(30));assertThrows(ServiceException.class,()->renderer().render(c));}
 @Test void rejectsUnrenderableCharacters(){WBCard c=sample();c.setName("项目😀");assertThrows(ServiceException.class,()->renderer().render(c));}
 @Test void twoRendersDoNotReuseMutablePdfState() throws Exception {WBCard c=sample();assertTrue(renderer().render(c).length>1000);c.setName("第二个项目");try(var doc=Loader.loadPDF(renderer().render(c))){String text=new PDFTextStripper().getText(doc);assertTrue(text.contains("第二个项目"));assertFalse(text.contains("综合服务中心"));}}
}
