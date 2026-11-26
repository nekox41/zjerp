package top.aameow.erpnext.business;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InspectionDocxRepeatRowsTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void repeatOnlySelectedSystemAndBindHeader()throws Exception{
        byte[] original;
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            doc.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("编号 {{erp.report.reportNo}}");
            var table=doc.createTable(2,2);
            table.getRow(0).getCell(0).setText("项目");table.getRow(0).getCell(1).setText("结果");
            var p=table.getRow(1).getCell(0).getParagraphs().get(0);
            p.createRun().setText("{{erp.records[消防给水系统].");p.createRun().setText("*.item}}");
            table.getRow(1).getCell(1).setText("{{erp.records[消防给水系统].*.result}}");
            doc.write(out);original=out.toByteArray();
        }
        var data=mapper.readTree("{\"records\":[{\"system\":\"消防给水系统\",\"item\":\"A\",\"result\":\"正常\"},{\"system\":\"消火栓系统\",\"item\":\"B\",\"result\":\"不适用\"},{\"system\":\"消防给水系统\",\"item\":\"C\",\"result\":\"\"}]}");
        byte[] prepared=InspectionDocxRepeatRows.prepare(original,data);
        try(var doc=new XWPFDocument(new ByteArrayInputStream(prepared))){
            var binder=new InspectionDocxBinder();
            var preview=binder.inspect(doc,data,"CI-123","测试",List.of());
            assertTrue(preview.errors().isEmpty(),preview.errors().toString());
            binder.fill(doc,data,"CI-123","测试",List.of());
            var table=doc.getTables().get(0);
            assertEquals(3,table.getRows().size());
            assertEquals("A",table.getRow(1).getCell(0).getText());
            assertEquals("C",table.getRow(2).getCell(0).getText());
            assertEquals("",table.getRow(2).getCell(1).getText());
            assertTrue(doc.getHeaderList().get(0).getText().contains("CI-123"));
        }
        assertThrows(ResponseStatusException.class,()->InspectionDocxRepeatRows.prepare(original,mapper.readTree("{\"records\":[]}")));
    }
}
