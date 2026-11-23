package top.aameow.erpnext.business;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A deliberately stateless first slice: keeps the uploaded DOCX and photos only
 * for the duration of the request. Original files and approved report versions
 * are never changed. Classification is reviewed by the operator in the UI.
 */
@RestController
@RequestMapping("/api/report-photos")
public class ReportPhotoController {
    private static final List<String> SYSTEMS = List.of(
        "火灾自动报警系统", "消防给水系统", "消火栓系统", "自动喷水灭火系统",
        "防烟排烟系统", "消防应急照明和疏散指示系统", "防火分隔设施",
        "电气火灾监控系统", "消防电源监控系统", "火灾警报和消防应急广播系统",
        "消防专用电话系统", "消防电梯", "消防供配电设施", "建筑灭火器",
        "其他"
    );
    private final ObjectMapper mapper;

    public ReportPhotoController(ObjectMapper mapper) { this.mapper = mapper; }

    public record PhotoEntry(String filename, String system, byte[] data, int type, int width, int height) { }

    @PostMapping(value = "/docx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('report:write')")
    public ResponseEntity<byte[]> exportDocx(
        @RequestPart("template") MultipartFile template,
        @RequestPart("photos") List<MultipartFile> photos,
        @RequestParam("categories") String categories
    ) throws IOException {
        if (template == null || template.isEmpty() || template.getSize() > 25L * 1024 * 1024 ||
            !template.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 25MB 以内的 DOCX 报告");
        }
        if (photos == null || photos.isEmpty() || photos.size() > 300) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "一次最多可插入 300 张照片");
        }
        List<String> assigned;
        try { assigned = mapper.readValue(categories, new TypeReference<List<String>>() {}); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类数据不是有效的 JSON 数组"); }
        if (assigned.size() != photos.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "照片与分类数量不一致");
        }
        List<PhotoEntry> entries = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < photos.size(); i++) {
            MultipartFile part = photos.get(i);
            String system = assigned.get(i);
            if (!SYSTEMS.contains(system)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "存在尚未分类或无效系统的照片");
            }
            if (part.isEmpty() || part.getSize() > 8L * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单张照片须小于 8MB");
            }
            total += part.getSize();
            if (total > 220L * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "照片合计超过 220MB");
            }
            byte[] bytes = part.getBytes();
            String filename = Optional.ofNullable(part.getOriginalFilename()).orElse("photo-" + (i + 1));
            if (filename.length() > 180) filename = filename.substring(filename.length() - 180);
            int pictureType = pictureType(bytes);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "照片内容不是有效的图片");
            }
            entries.add(new PhotoEntry(filename, system, bytes, pictureType, image.getWidth(), image.getHeight()));
        }

        try (XWPFDocument document = new XWPFDocument(template.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            // Phase one: insert editable grouped photo pages at the end without
            // disturbing the existing inspection tables, declarations or signatures.
            XWPFParagraph page = document.createParagraph();
            page.setPageBreak(true);
            page.setStyle("Heading1");
            page.createRun().setText("现场检测照片");
            for (String system : SYSTEMS) {
                List<PhotoEntry> group = entries.stream().filter(e -> e.system().equals(system)).toList();
                if (group.isEmpty()) continue;
                XWPFParagraph heading = document.createParagraph();
                heading.setStyle("Heading2");
                heading.createRun().setText(system);
                XWPFTable table = document.createTable();
                table.removeRow(0);
                for (int i = 0; i < group.size(); i += 2) {
                    XWPFTableRow row = table.createRow();
                    // createRow() initializes the number of cells from table grid;
                    // ensure precisely two cells for a clean two-column photo layout.
                    while (row.getTableCells().size() < 2) row.addNewTableCell();
                    for (int col = 0; col < 2 && i + col < group.size(); col++) {
                        PhotoEntry item = group.get(i + col);
                        XWPFTableCell cell = row.getCell(col);
                        XWPFParagraph photo = cell.getParagraphs().get(0);
                        photo.setAlignment(ParagraphAlignment.CENTER);
                        XWPFRun run = photo.createRun();
                        double ratio = Math.min(1.0, Math.min(260.0 / item.width(), 185.0 / item.height()));
                        int w = Math.max(1, (int)Math.round(item.width() * ratio));
                        int h = Math.max(1, (int)Math.round(item.height() * ratio));
                        run.addPicture(new ByteArrayInputStream(item.data()), item.type(), item.filename(),
                            Units.toEMU(w), Units.toEMU(h));
                        XWPFParagraph label = cell.addParagraph();
                        label.setAlignment(ParagraphAlignment.CENTER);
                        label.createRun().setText(item.filename());
                    }
                }
            }
            document.write(output);
            String original = template.getOriginalFilename();
            String name = original.substring(0, original.length() - 5) + "-已整理照片.docx";
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(name, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(output.toByteArray());
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException | org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOCX 文件无效或已损坏", e);
        } catch (org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请使用 DOCX 而不是旧版 DOC 格式", e);
        }
    }

    private static int pictureType(byte[] bytes) {
        if (bytes.length > 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216)
            return Document.PICTURE_TYPE_JPEG;
        if (bytes.length > 8 && (bytes[0] & 255) == 137 && bytes[1] == 80 &&
            bytes[2] == 78 && bytes[3] == 71)
            return Document.PICTURE_TYPE_PNG;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 JPG 和 PNG 照片");
    }
}
