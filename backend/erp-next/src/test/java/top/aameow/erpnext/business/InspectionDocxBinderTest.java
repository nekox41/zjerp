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
    private void cells(XWPFTableRow row,String...texts){
        for(int i=0;i<texts.length;i++){
            var cell=i==0?row.getCell(0):row.addNewTableCell();
            if(!texts[i].isEmpty())cell.setText(texts[i]);
        }
    }
    @Test void suggestsLogicalLabelsAndBlankBuildingRowWithoutMutatingTemplate()throws Exception{
        try(var doc=new XWPFDocument()){
            var table=doc.createTable(19,1);
            cells(table.getRow(0),"工程名称","旧名称");
            cells(table.getRow(1),"工程地址","旧地址");
            cells(table.getRow(2),"工程类别","新建");
            cells(table.getRow(3),"建设单位","旧建设单位");
            cells(table.getRow(4),"设计单位","旧设计单位","资质等级","甲级");
            cells(table.getRow(5),"施工单位","旧施工单位","资质等级","甲级");
            cells(table.getRow(6),"监理单位","旧监理单位","资质等级","甲级");
            cells(table.getRow(7),"建筑总面积","1㎡","检测日期","旧日期");
            cells(table.getRow(8),"委托单位","旧委托单位","委托时间","旧日期");
            cells(table.getRow(9),"委托/联系人","旧联系人","联系电话","旧电话");
            cells(table.getRow(10),"单体建筑工程信息");
            cells(table.getRow(11),"单体建筑名称","结构类型","使用性质","耐火等级","层数","高度","长度","占地面积","建筑面积");
            cells(table.getRow(12),"","","","","地上","地下","","","","地上","地下");
            cells(table.getRow(13),"旧楼栋","","","","","","","","","","");
            cells(table.getRow(14),"","","","","","","","","","","");
            var result=binder.inspect(doc,snapshot(),"CI","报告",List.of());
            assertTrue(result.suggestedBindings().stream().anyMatch(x->x.target().equals("T1/R1/C2/P1")&&x.field().equals("general.projectName")));
            assertTrue(result.suggestedBindings().stream().anyMatch(x->x.target().equals("T1/R15/C1/P1")&&x.field().equals("buildings.1.name")&&x.expectedText().isEmpty()));
            assertEquals("旧名称",table.getRow(0).getCell(1).getText());
            binder.fill(doc,snapshot(),"CI","报告",result.suggestedBindings());
            assertEquals("测试工程",table.getRow(0).getCell(1).getText());
            assertEquals("车库一期",table.getRow(14).getCell(0).getText());
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
