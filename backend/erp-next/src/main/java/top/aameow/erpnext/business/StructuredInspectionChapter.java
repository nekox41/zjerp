package top.aameow.erpnext.business;

import org.apache.poi.xwpf.usermodel.*;
import java.io.*;

/** Standard six-column native Word chapter; no imported styles or predetermined outcomes. */
final class StructuredInspectionChapter {
    private StructuredInspectionChapter(){}
    static byte[] create(String system)throws IOException{
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            var table=doc.createTable(2,6);
            table.setWidth(9600);
            String[] headers={"序号","检验项目","标准要求","检测部位","检验结果","单项判定"};
            String[] fields={"sequence","item","standard","location","result","judgment"};
            int[] widths={540,1380,3060,1380,2220,1020};
            var ct=table.getCTTbl();
            var grid=ct.getTblGrid();if(grid==null)grid=ct.addNewTblGrid();
            while(grid.sizeOfGridColArray()>0)grid.removeGridCol(0);
            for(int width:widths)grid.addNewGridCol().setW(java.math.BigInteger.valueOf(width));
            var pr=ct.getTblPr();var layout=pr.isSetTblLayout()?pr.getTblLayout():pr.addNewTblLayout();
            layout.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType.FIXED);
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
