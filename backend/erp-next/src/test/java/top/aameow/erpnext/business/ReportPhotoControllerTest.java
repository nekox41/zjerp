package top.aameow.erpnext.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ReportPhotoControllerTest {
    private static MockMultipartFile template() throws Exception {
        try (XWPFDocument original = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            original.createParagraph().createRun().setText("原始报告正文");
            original.write(out);
            return new MockMultipartFile("template", "sample.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", out.toByteArray());
        }
    }

    private static MockMultipartFile photo() throws Exception {
        BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 20, 10);
        graphics.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, "png", out));
            return new MockMultipartFile("photos", "test.png", "image/png", out.toByteArray());
        }
    }

    @Test
    void preservesOriginalAndAppendsPhotosByFirstLevelSystem() throws Exception {
        var controller = new ReportPhotoController(new ObjectMapper());
        var result = controller.exportDocx(template(), java.util.List.of(photo()),
            "[\"火灾自动报警系统\"]");
        assertNotNull(result.getBody());
        try (var report = new XWPFDocument(new ByteArrayInputStream(result.getBody()))) {
            String content = report.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(content.contains("原始报告正文"));
            assertTrue(content.contains("火灾自动报警系统"));
            assertEquals(1, report.getAllPictures().size());
            assertFalse(report.getTables().isEmpty());
        }
    }

    @Test
    void rejectsUnclassifiedPhotos() throws Exception {
        var controller = new ReportPhotoController(new ObjectMapper());
        assertThrows(ResponseStatusException.class,
            () -> controller.exportDocx(template(), java.util.List.of(photo()), "[\"\"]"));
    }
}
