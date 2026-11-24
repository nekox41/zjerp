package top.aameow.erpnext.business;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InspectionDocxBinderTest {
    private final InspectionDocxBinder binder=new InspectionDocxBinder();
    private final ObjectMapper mapper=new ObjectMapper();
    private com.fasterxml.jackson.databind.JsonNode snapshot()throws Exception{
        return mapper.readTree("{\"general\":{\"projectName\":\"测试工程\",\"address\":\"测试地址\"},\"buildings\":[{\"name\":\"1号楼\"},{\"name\":\"车库一期\"}],\"records\":[{\"judgment\":\"不合格\"}],\"conclusion\":{\"text\":\"待审核\"}}");
    }
    @Test void splitRunMarkerAndMultipleFieldsPreserveUnchangedRuns()throws Exception{
        try(var doc=new XWPFDocument()){
            var p=doc.createParagraph();
            p.createRun().setText("工程：{{erp.general.");
            p.createRun().setText("projectName}}，");
            var end=p.createRun();end.setBold(true);end.setText("{{erp.general.address}}。");
            var cell=doc.createTable(1,2).getRow(0).getCell(1);
            cell.setText("{{erp.buildings.1.name}}");
            var signed=doc.createParagraph();signed.createRun().setText("审核人：手签");
            var before=binder.inspect(doc,snapshot(),"CI-001","报告",List.of());
            assertTrue(before.errors().isEmpty(),before.errors().toString());
            assertEquals(2,before.changes().size());
            assertTrue(p.getText().contains("{{erp."));
            binder.fill(doc,snapshot(),"CI-001","报告",List.of());
            assertEquals("工程：测试工程，测试地址。",p.getText());
            assertTrue(end.isBold());
            assertEquals("车库一期",cell.getText());
            assertEquals("审核人：手签",signed.getText());
        }
    }
    @Test void manualBindingChecksExactOriginalAndDoesNotChangeOtherCells()throws Exception{
        try(var doc=new XWPFDocument()){
            var table=doc.createTable(1,2);table.getRow(0).getCell(0).setText("工程名称");table.getRow(0).getCell(1).setText("旧名称");
            var binding=new InspectionDocxBinder.Binding("T1/R1/C2/P1","旧名称","general.projectName");
            binder.fill(doc,snapshot(),"CI-001","报告",List.of(binding));
            assertEquals("工程名称",table.getRow(0).getCell(0).getText());
            assertEquals("测试工程",table.getRow(0).getCell(1).getText());
            assertThrows(ResponseStatusException.class,()->binder.fill(doc,snapshot(),"CI-001","报告",List.of(binding)));
        }
    }
    @Test void emptyCellAndMultiLineAreSupported()throws Exception{
        try(var doc=new XWPFDocument()){
            var cell=doc.createTable(1,1).getRow(0).getCell(0);
            var data=mapper.readTree("{\"general\":{\"overview\":\"第一行\\n第二行\"}}");
            var binding=new InspectionDocxBinder.Binding("T1/R1/C1/P1","","general.overview");
            binder.fill(doc,data,"CI","报告",List.of(binding));
            assertEquals("第一行\n第二行",cell.getParagraphs().get(0).getText());
        }
    }
    @Test void missingAndProtectedTargetsFailWithoutPartialMutation()throws Exception{
        try(var doc=new XWPFDocument()){
            var a=doc.createParagraph();a.createRun().setText("{{erp.general.projectName}}");
            var b=doc.createParagraph();b.createRun().setText("{{erp.buildings.9.name}}");
            var c=doc.createParagraph();c.createRun().setText("批准人：旧签名");
            assertThrows(ResponseStatusException.class,()->binder.fill(doc,snapshot(),"CI","报告",List.of(new InspectionDocxBinder.Binding("P3","批准人：旧签名","general.projectName"))));
            assertEquals("{{erp.general.projectName}}",a.getText());
            assertEquals("批准人：旧签名",c.getText());
        }
    }
}
