package top.aameow.erpnext.business;

import java.io.*;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InspectionReportLayoutTest {
    private byte[] png()throws Exception{
        var image=new BufferedImage(120,160,BufferedImage.TYPE_INT_RGB);
        try(var out=new ByteArrayOutputStream()){ImageIO.write(image,"png",out);return out.toByteArray();}
    }
    private byte[] chapter()throws Exception{
        try(var d=new XWPFDocument();var out=new ByteArrayOutputStream()){d.createTable(2,2).getRow(0).getCell(0).setText("测试明细");d.write(out);return out.toByteArray();}
    }
    @Test void starterHasTocAndPageFieldsAndNumberedSystems()throws Exception{
        byte[] base=InspectionBaseStarter.create(Set.of());
        var section=new InspectionTemplateComposer.Section("测试消防系统",chapter());
        byte[] composed=InspectionTemplateComposer.compose(base,List.of(section));
        assertTrue(InspectionTemplateComposer.pendingToc(composed));assertTrue(InspectionTemplateComposer.hasToc(composed));
        try(var doc=new XWPFDocument(new ByteArrayInputStream(composed))){
            String xml=doc.getDocument().xmlText();
            assertTrue(xml.contains("TOC"));assertTrue(xml.contains("outlineLvl"));assertFalse(xml.contains(InspectionTemplateComposer.MARKER));
            assertTrue(doc.getParagraphs().stream().anyMatch(p->p.getText().equals("（一）测试消防系统")));
            assertFalse(doc.getFooterList().isEmpty());
            try(var zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(composed))){
                boolean pageFields=false;
                for(java.util.zip.ZipEntry e;(e=zip.getNextEntry())!=null;){
                    if(e.getName().matches("word/footer[0-9]+\\.xml")){
                        String footer=new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                        pageFields|=footer.contains(" PAGE ")&&footer.contains(" NUMPAGES ");
                    }
                }
                assertTrue(pageFields);
            }
        }
    }
    @Test void longPhotoNotesUseOneRowPerPage()throws Exception{
        String system="测试系统";
        byte[] composed=InspectionTemplateComposer.compose(InspectionBaseStarter.create(Set.of()),List.of(new InspectionTemplateComposer.Section(system,chapter())));
        var photos=List.of(new ReportDocxPhotoInserter.Photo(system,png(),"a.png","2026-01-01","较长的说明".repeat(10)),new ReportDocxPhotoInserter.Photo(system,png(),"b.png","2026-01-01",""),new ReportDocxPhotoInserter.Photo(system,png(),"c.png","2026-01-01",""));
        byte[] paged=InspectionTemplateComposer.paginatePhotos(composed,photos,Set.of(system));
        try(var doc=new XWPFDocument(new ByteArrayInputStream(paged))){
            var body=doc.getBodyElements();int count=0;
            for(int i=0;i+1<body.size();i++)if(body.get(i) instanceof XWPFParagraph p&&"现场检测照片".equals(p.getText())){count++;assertEquals(1,((XWPFTable)body.get(i+1)).getRows().size());}
            assertEquals(2,count);
        }
    }
    @Test void fivePhotosCreateTwoPagesAndEmptySystemDoesNotCreatePhotoPage()throws Exception{
        String system="测试消防系统",empty="其他测试系统";
        byte[] base=InspectionBaseStarter.create(Set.of());
        byte[] composed=InspectionTemplateComposer.compose(base,List.of(new InspectionTemplateComposer.Section(system,chapter()),new InspectionTemplateComposer.Section(empty,chapter())));
        List<ReportDocxPhotoInserter.Photo> photos=new ArrayList<>();
        for(int i=0;i<5;i++)photos.add(new ReportDocxPhotoInserter.Photo(system,png(),i+".png","2026-01-01","测试"));
        byte[] paged=InspectionTemplateComposer.paginatePhotos(composed,photos,Set.of(system,empty));
        try(var doc=new XWPFDocument(new ByteArrayInputStream(paged))){
            new ReportDocxPhotoInserter().insert(doc,photos,true,Set.of(system,empty));
            var photoTables=new ArrayList<XWPFTable>();var body=doc.getBodyElements();
            for(int i=0;i+1<body.size();i++)if(body.get(i) instanceof XWPFParagraph p&&"现场检测照片".equals(p.getText())){
                assertTrue(p.isPageBreak());photoTables.add((XWPFTable)body.get(i+1));
            }
            assertEquals(2,photoTables.size());assertEquals(2,photoTables.get(0).getRows().size());assertEquals(1,photoTables.get(1).getRows().size());
            long pics=photoTables.stream().flatMap(t->t.getRows().stream()).flatMap(row->row.getTableCells().stream()).flatMap(cell->cell.getParagraphs().stream()).flatMap(p->p.getRuns().stream()).mapToLong(run->run.getEmbeddedPictures().size()).sum();
            assertEquals(5,pics);
        }
    }
}
