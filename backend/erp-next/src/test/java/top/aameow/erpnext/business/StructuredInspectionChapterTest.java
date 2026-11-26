package top.aameow.erpnext.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StructuredInspectionChapterTest {
    @Test void expandsOnlySelectedSystemAndKeepsBlankManualResults()throws Exception{
        String system="测试系统甲";
        byte[] chapter=StructuredInspectionChapter.create(system);
        assertEquals(1,InspectionTemplateComposer.inspect(chapter,false).tables());
        byte[] base;
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            doc.createParagraph().createRun().setText(InspectionTemplateComposer.MARKER);
            doc.write(out);base=out.toByteArray();
        }
        var mapper=new ObjectMapper();var snapshot=mapper.createObjectNode();
        var rows=snapshot.putArray("records");
        rows.addObject().put("system","另一个测试系统").put("sequence","1").put("item","不得混入").put("standard","测试").put("location","").put("result","").put("judgment","");
        rows.addObject().put("system",system).put("sequence","1").put("item","测试项目一").put("standard","测试摘要一").put("location","A区").put("result","人工记录").put("judgment","人工判定");
        rows.addObject().put("system",system).put("sequence","2").put("item","测试项目二").put("standard","测试摘要二").put("location","B区").put("result","").put("judgment","");
        byte[] composed=InspectionTemplateComposer.compose(base,List.of(new InspectionTemplateComposer.Section(system,chapter)));
        byte[] expanded=InspectionDocxRepeatRows.prepare(composed,snapshot);
        try(var doc=new XWPFDocument(new ByteArrayInputStream(expanded));var out=new ByteArrayOutputStream()){
            var binder=new InspectionDocxBinder();
            var preflight=binder.inspect(doc,snapshot,"TEST-ONLY","测试",List.of());
            assertTrue(preflight.errors().isEmpty(),preflight.errors().toString());
            binder.fill(doc,snapshot,"TEST-ONLY","测试",List.of());
            doc.write(out);
            try(var saved=new XWPFDocument(new ByteArrayInputStream(out.toByteArray()))){
                var table=saved.getTables().get(0);
                assertEquals(3,table.getRows().size());
                assertEquals("测试项目一",table.getRow(1).getCell(1).getText());
                assertEquals("测试项目二",table.getRow(2).getCell(1).getText());
                assertEquals("",table.getRow(2).getCell(5).getText());
                assertFalse(table.getText().contains("不得混入"));
            }
        }
    }
}
