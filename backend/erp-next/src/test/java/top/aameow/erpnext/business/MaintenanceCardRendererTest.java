package top.aameow.erpnext.business;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class MaintenanceCardRendererTest {
    private final MaintenanceCardRenderer renderer=new MaintenanceCardRenderer();
    private MaintenanceCardRenderer.Card card(String name){
        return new MaintenanceCardRenderer.Card(name,"河南众基消防技术有限公司","张三","13800138000","0000001","2026-11-01 至 2027-10-31","火灾自动报警系统、自动喷水灭火系统、消防电话系统、消防广播系统、消火栓系统");
    }
    @Test void rendersOriginalLayoutAndText()throws Exception{
        var c=card("郑州市消防设施维护保养项目");
        try(var pdf=Loader.loadPDF(renderer.render(c))){
            assertEquals(1,pdf.getNumberOfPages());
            assertEquals(841.89f,pdf.getPage(0).getMediaBox().getWidth(),0.1);
            assertNull(pdf.getDocumentCatalog().getAcroForm());
            String text=new PDFTextStripper().getText(pdf).replaceAll("\\s","");
            for(String v:new String[]{c.name(),c.company(),c.leader(),c.phone(),c.no(),c.system1(),"2026年11月01日至2027年10月31日"})assertTrue(text.contains(v),v);
        }
    }
    @Test void rejectsOverflowAndUnsupportedGlyphs(){
        assertThrows(ResponseStatusException.class,()->renderer.render(card("非常长的项目名称".repeat(30))));
        assertThrows(ResponseStatusException.class,()->renderer.render(card("项目😀")));
    }
}
