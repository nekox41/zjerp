package top.aameow.erpnext.business;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportPhotoDocxServiceTest {
    private final ReportPhotoDocxService service = new ReportPhotoDocxService();

    private byte[] image(Color color) throws Exception {
        BufferedImage image = new BufferedImage(24, 16, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 24, 16);
        graphics.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }

    private byte[] template() throws Exception {
        byte[] old = image(Color.GRAY);
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("火灾自动报警系统");
            doc.createTable(4, 2).getRow(0).getCell(0).setText("原有检测明细");
            doc.createParagraph().createRun().setText("现场检测照片");
            XWPFTable photos = doc.createTable(2, 2);
            for (XWPFTableRow row : photos.getRows()) for (XWPFTableCell cell : row.getTableCells()) {
                cell.getParagraphs().get(0).createRun().addPicture(new ByteArrayInputStream(old),
                    Document.PICTURE_TYPE_PNG, "old.png", 1000000, 700000);
            }
            doc.createParagraph().createRun().setText("注：应附彩色照片并注明日期，日期应如实填写。");
            doc.createParagraph().createRun().setText("消防给水系统");
            doc.createParagraph().createRun().setText("现场检测照片");
            XWPFTable other = doc.createTable(2, 2);
            for (XWPFTableRow row : other.getRows()) for (XWPFTableCell cell : row.getTableCells()) {
                cell.getParagraphs().get(0).createRun().addPicture(new ByteArrayInputStream(old),
                    Document.PICTURE_TYPE_PNG, "other.png", 1000000, 700000);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private int visibleImageCount(XWPFDocument doc) {
        return doc.getTables().stream().flatMap(t -> t.getRows().stream())
            .flatMap(row -> row.getTableCells().stream()).flatMap(cell -> cell.getParagraphs().stream())
            .mapToInt(p -> p.getRuns().stream().mapToInt(r -> r.getEmbeddedPictures().size()).sum()).sum();
    }

    @Test
    void detectsSystemPhotoGroupsAndAliases() throws Exception {
        List<Map<String,Object>> result = service.inspect(new ByteArrayInputStream(template()));
        Map<String,Object> alarm = result.stream().filter(x -> x.get("systemCode").equals("fire_alarm")).findFirst().orElseThrow();
        Map<String,Object> water = result.stream().filter(x -> x.get("systemCode").equals("fire_water")).findFirst().orElseThrow();
        assertEquals(1, alarm.get("groupCount"));
        assertEquals(4, alarm.get("imageCount"));
        assertEquals(1, water.get("groupCount"));
    }

    @Test
    void replacesOnlySelectedSystemAndPreservesOtherReportContent() throws Exception {
        byte[] output = service.insert(new ByteArrayInputStream(template()),
            Map.of("fire_alarm", List.of(new ReportPhotoDocxService.PhotoImage("a.png", image(Color.RED)),
                                         new ReportPhotoDocxService.PhotoImage("b.png", image(Color.BLUE)))),
            Map.of("fire_alarm", "replace"));
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(output))) {
            assertEquals(6, visibleImageCount(doc));
            String text = doc.getParagraphs().stream().map(XWPFParagraph::getText).reduce("", (a,b) -> a + b);
            assertTrue(text.contains("火灾自动报警系统"));
            assertTrue(text.contains("消防给水系统"));
            assertTrue(text.contains("注：应附彩色照片"));
            XWPFTable first = doc.getTables().get(1);
            int firstImages = first.getRows().stream().flatMap(r -> r.getTableCells().stream())
                .flatMap(c -> c.getParagraphs().stream()).mapToInt(p -> p.getRuns().stream().mapToInt(r -> r.getEmbeddedPictures().size()).sum()).sum();
            assertEquals(2, firstImages);
        }
    }

    @Test
    void appendsPhotosWithoutReplacingExistingImages() throws Exception {
        byte[] output = service.insert(new ByteArrayInputStream(template()),
            Map.of("fire_alarm", List.of(new ReportPhotoDocxService.PhotoImage("new.png", image(Color.GREEN)))),
            Map.of("fire_alarm", "append"));
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(output))) {
            assertEquals(9, visibleImageCount(doc));
        }
    }
}
