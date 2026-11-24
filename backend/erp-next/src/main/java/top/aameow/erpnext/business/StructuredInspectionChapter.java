package top.aameow.erpnext.business;

import org.apache.poi.xwpf.usermodel.*;
import java.io.*;

/** Standard six-column native Word chapter; no imported styles or predetermined outcomes. */
final class StructuredInspectionChapter {
    private StructuredInspectionChapter(){}
    static byte[] create(String system)throws IOException{
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            var table=doc.createTable(2,6);
            table.setWidth(9840);
            String[] headers={"序号","检验项目","标准要求","检测部位","检验结果","单项判定"};
            String[] fields={"sequence","item","standard","location","result","judgment"};
            int[] widths={600,1500,3240,1500,2100,900};
            for(int i=0;i<6;i++){
                var h=table.getRow(0).getCell(i);h.setWidth(Integer.toString(widths[i]));
                var p=h.getParagraphs().get(0);p.setAlignment(ParagraphAlignment.CENTER);
                var run=p.createRun();run.setBold(true);run.setFontSize(10);run.setText(headers[i]);
                var cell=table.getRow(1).getCell(i);cell.setWidth(Integer.toString(widths[i]));
                var content=cell.getParagraphs().get(0).createRun();content.setFontSize(10);
                content.setText("{{erp.records["+system+"].*."+fields[i]+"}}");
            }
            table.getRow(0).setRepeatHeader(true);
            doc.write(out);return out.toByteArray();
        }
    }
}
