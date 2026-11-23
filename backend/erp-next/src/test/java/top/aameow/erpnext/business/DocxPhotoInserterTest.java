package top.aameow.erpnext.business;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocxPhotoInserterTest {
    private static byte[] png() throws Exception {
        BufferedImage image=new BufferedImage(32,24,BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0,0,32,24); graphics.dispose();
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image,"png",out));
            return out.toByteArray();
        }
    }

    private static byte[] template() throws Exception {
        try(XWPFDocument document=new XWPFDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("火灾自动报警系统");
            document.createTable(2,6);
            document.createParagraph().createRun().setText("现场检测照片");
            document.createTable(2,2);
            document.createParagraph().createRun().setText("注：应附彩色照片并注明日期，日期应如实填写。");
            document.createParagraph().createRun().setText("消防给水系统");
            document.createTable(2,6);
            document.createParagraph().createRun().setText("现场检测照片");
            document.createTable(2,2);
            document.createParagraph().createRun().setText("注：应附彩色照片并注明日期，日期应如实填写。");
            document.createParagraph().createRun().setText("结论");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void insertsClassifiedPhotosIntoMatchingSystemAndPreservesOtherSections() throws Exception {
        byte[] image=png();
        List<DocxPhotoInserter.Photo> photos=List.of(
            new DocxPhotoInserter.Photo("火灾自动报警系统","a.png",image),
            new DocxPhotoInserter.Photo("火灾自动报警系统","b.png",image),
            new DocxPhotoInserter.Photo("火灾自动报警系统","c.png",image),
            new DocxPhotoInserter.Photo("火灾自动报警系统","d.png",image),
            new DocxPhotoInserter.Photo("火灾自动报警系统","e.png",image),
            new DocxPhotoInserter.Photo("消防给水系统","f.png",image)
        );
        byte[] output=DocxPhotoInserter.insert(template(),photos);
        try(XWPFDocument document=new XWPFDocument(new java.io.ByteArrayInputStream(output))) {
            assertTrue(document.getParagraphs().stream().anyMatch(p->p.getText().equals("结论")));
            long photoHeadings=document.getParagraphs().stream().filter(p->p.getText().trim().equals("现场检测照片")).count();
            assertEquals(5,photoHeadings); // 2 original anchors + 3 inserted groups
            assertEquals(1,document.getAllPictures().size(),"POI deduplicates identical image bytes");
            assertEquals(6,((org.w3c.dom.Element)document.getDocument().getBody().getDomNode())
                .getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","drawing").getLength(),
                "all six photo placements must be present in the DOCX body");
            assertEquals(5,document.getTables().size()); // 2 original tables + 3 inserted tables
        }
    }

    @Test
    void acceptsKnownTemplateAliasAndRejectsMissingSystemSection() throws Exception {
        byte[] image=png();
        var aliasPhoto=new DocxPhotoInserter.Photo("消防应急照明和疏散指示系统","light.png",image);
        try(XWPFDocument document=new XWPFDocument(new java.io.ByteArrayInputStream(template()))) {
            // The alias itself is tested by building a template whose heading uses the older wording.
        }
        byte[] aliasTemplate;
        try(XWPFDocument doc=new XWPFDocument();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("应急照明和疏散指示系统");
            doc.createParagraph().createRun().setText("现场检测照片");
            doc.createTable(2,2);
            doc.write(out); aliasTemplate=out.toByteArray();
        }
        assertDoesNotThrow(()->DocxPhotoInserter.insert(aliasTemplate,List.of(aliasPhoto)));
        assertThrows(IllegalArgumentException.class,()->DocxPhotoInserter.insert(template(),
            List.of(new DocxPhotoInserter.Photo("消防电梯","elevator.png",image))));
    }
}
