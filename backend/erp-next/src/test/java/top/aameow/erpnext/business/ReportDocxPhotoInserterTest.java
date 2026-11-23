package top.aameow.erpnext.business;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportDocxPhotoInserterTest {
    private byte[] png() throws Exception {
        BufferedImage image=new BufferedImage(24,16,BufferedImage.TYPE_INT_RGB);
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            assertTrue(javax.imageio.ImageIO.write(image,"png",out));return out.toByteArray();
        }
    }
    private void section(XWPFDocument doc,String name,int blocks) {
        doc.createParagraph().createRun().setText(name);
        for(int i=0;i<blocks;i++){
            doc.createParagraph().createRun().setText("现场检测照片");
            XWPFTable table=doc.createTable(2,2);
            for(XWPFTableRow row:table.getRows())for(XWPFTableCell cell:row.getTableCells())cell.setText("模板旧内容");
            doc.createParagraph().createRun().setText("注：应附彩色照片并注明日期，日期应如实填写。");
        }
    }
    @Test
    void placesConfirmedPhotosIntoTheirSystemPhotoTablesAndRemovesUnusedTemplateBlocks() throws Exception {
        try(XWPFDocument doc=new XWPFDocument()){
            doc.createParagraph().createRun().setText("报告正文");
            section(doc,"火灾自动报警系统",2);
            section(doc,"消防给水系统",1);
            new ReportDocxPhotoInserter().insert(doc,List.of(
                new ReportDocxPhotoInserter.Photo("火灾自动报警系统",png(),"a.png","2026-08-11",""),
                new ReportDocxPhotoInserter.Photo("消防给水系统",png(),"b.png","2026-08-12","")
            ));
            assertEquals(2,doc.getAllPictures().size());
            assertEquals(2,doc.getTables().size()); // one retained photo table per populated system
            String text=doc.getParagraphs().stream().map(XWPFParagraph::getText).reduce("",String::concat);
            assertTrue(text.contains("报告正文"));
            assertEquals(2,doc.getBodyElements().stream().filter(x->x instanceof XWPFParagraph p && "现场检测照片".equals(p.getText())).count());
        }
    }
    @Test
    void expandsTheLastPhotoTableWhenThereAreMoreThanTemplateSlots() throws Exception {
        try(XWPFDocument doc=new XWPFDocument()){
            section(doc,"消防给水系统",1);
            var photos=new java.util.ArrayList<ReportDocxPhotoInserter.Photo>();
            for(int i=0;i<5;i++)photos.add(new ReportDocxPhotoInserter.Photo("消防给水系统",png(),i+".png","2026-08-11",""));
            new ReportDocxPhotoInserter().insert(doc,photos);
            assertEquals(5,doc.getAllPictures().size());
            assertEquals(3,doc.getTables().get(0).getRows().size());
        }
    }
    @Test
    void refusesCategoriesMissingFromTheSelectedTemplate() throws Exception {
        try(XWPFDocument doc=new XWPFDocument()){
            section(doc,"火灾自动报警系统",1);
            assertThrows(ResponseStatusException.class,()->new ReportDocxPhotoInserter().insert(doc,List.of(
                new ReportDocxPhotoInserter.Photo("自动喷水灭火系统",png(),"x.png","2026-08-11","")
            )));
        }
    }
}
