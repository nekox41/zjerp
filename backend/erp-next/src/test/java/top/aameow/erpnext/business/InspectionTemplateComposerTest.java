package top.aameow.erpnext.business;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InspectionTemplateComposerTest {
    byte[] word(String label,boolean section)throws Exception{
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            if(section){
                var table=doc.createTable(2,2);table.getRow(0).getCell(0).setText("标准要求");table.getRow(0).getCell(1).setText("结果");
                table.getRow(1).getCell(0).setText(label);table.getRow(1).getCell(1).setText("");
            }else{
                doc.createParagraph().createRun().setText("公共封面");
                doc.createParagraph().createRun().setText(label);
                doc.createParagraph().createRun().setText("公共附录");
            }
            doc.write(out);return out.toByteArray();
        }
    }
    @Test void compositionKeepsOrderAndNativeTables()throws Exception{
        byte[] base=word(InspectionTemplateComposer.MARKER,false);
        var first=new InspectionTemplateComposer.Section("自定义消防系统甲",word("检测标准A",true));
        var second=new InspectionTemplateComposer.Section("自定义消防系统乙",word("检测标准B",true));
        byte[] output=InspectionTemplateComposer.compose(base,List.of(second,first));
        try(var doc=new XWPFDocument(new ByteArrayInputStream(output))){
            var p=doc.getParagraphs().stream().map(XWPFParagraph::getText).toList();
            assertTrue(p.contains(InspectionTemplateComposer.heading(1,second.system())));
            assertTrue(p.indexOf(InspectionTemplateComposer.heading(1,second.system()))<p.indexOf(InspectionTemplateComposer.heading(2,first.system())));
            assertFalse(p.contains(InspectionTemplateComposer.MARKER));
            assertEquals(4,doc.getTables().size());
            assertEquals("检测标准B",doc.getTables().get(0).getRow(1).getCell(0).getText());
            new ReportDocxPhotoInserter().insert(doc,List.of(),true,Set.of(first.system(),second.system()));
            assertEquals(2,doc.getTables().size(),"Empty photo grids are removed, detection tables are retained");
        }
        assertThrows(ResponseStatusException.class,()->InspectionTemplateComposer.compose(base,List.of(first,first)));
        assertThrows(ResponseStatusException.class,()->InspectionTemplateComposer.compose(word("无插入标记",false),List.of(first)));
    }
    @Test void rejectsChapterWithoutNativeTable()throws Exception{
        assertThrows(ResponseStatusException.class,()->InspectionTemplateComposer.inspect(word("无表格",false),false));
    }
}
