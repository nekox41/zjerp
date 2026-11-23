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
    private long pictureReferences(XWPFDocument doc) {
        return doc.getTables().stream().flatMap(t->t.getRows().stream())
            .flatMap(row->row.getTableCells().stream())
            .flatMap(cell->cell.getParagraphs().stream())
            .flatMap(p->p.getRuns().stream())
            .mapToLong(run->run.getEmbeddedPictures().size()).sum();
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
            assertEquals(2,pictureReferences(doc));
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
            assertEquals(5,pictureReferences(doc));
            assertEquals(3,doc.getTables().get(0).getRows().size());
        }
    }
    @Test
    void appendModeKeepsExistingTemplatePicturesAndAddsNewRows() throws Exception {
        try(XWPFDocument doc=new XWPFDocument()){
            section(doc,"火灾自动报警系统",1);
            var cell=doc.getTables().get(0).getRow(0).getCell(0);
            cell.getParagraphs().get(0).createRun().addPicture(new java.io.ByteArrayInputStream(png()),
                Document.PICTURE_TYPE_PNG,"template.png",org.apache.poi.util.Units.toEMU(40),org.apache.poi.util.Units.toEMU(30));
            new ReportDocxPhotoInserter().insert(doc,List.of(
                new ReportDocxPhotoInserter.Photo("火灾自动报警系统",png(),"new.png","2026-08-12","")
            ),false);
            assertEquals(2,pictureReferences(doc));
            assertEquals(3,doc.getTables().get(0).getRows().size());
            assertTrue(doc.getTables().get(0).getRow(0).getCell(0).getText().contains("模板旧内容"));
        }
    }
    @Test
    void recognizesParenthesizedSectionNumbersUsedByBothSuppliedTemplates() throws Exception {
        try(XWPFDocument doc=new XWPFDocument()){
            section(doc,"（一）火灾自动报警系统",1);
            section(doc,"（十四）建筑灭火器",1);
            new ReportDocxPhotoInserter().insert(doc,List.of(
                new ReportDocxPhotoInserter.Photo("火灾自动报警系统",png(),"alarm.png","2026-08-11",""),
                new ReportDocxPhotoInserter.Photo("建筑灭火器",png(),"extinguisher.png","2026-08-11","")
            ));
            assertEquals(2,pictureReferences(doc));
            assertEquals(2,doc.getTables().size());
        }
    }
    @Test
    void recognizesTheAbbreviatedEmergencyLightingHeadingInTheZhenggaoTemplate() throws Exception {
        try(XWPFDocument doc=new XWPFDocument()){
            section(doc,"（四）应急照明和疏散指示系统",1);
            new ReportDocxPhotoInserter().insert(doc,List.of(
                new ReportDocxPhotoInserter.Photo("消防应急照明和疏散指示系统",png(),"lighting.png","2026-08-11","")
            ));
            assertEquals(1,pictureReferences(doc));
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
