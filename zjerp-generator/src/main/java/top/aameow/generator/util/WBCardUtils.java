package top.aameow.generator.util;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.aameow.common.config.RuoYiConfig;
import top.aameow.common.constant.Constants;
import top.aameow.common.utils.DateUtils;
import top.aameow.generator.domain.Template;
import top.aameow.generator.domain.WBCard;
import top.aameow.generator.service.TemplateService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class WBCardUtils {
    @Autowired
    private TemplateService templateService;

    private static final String WB_CARD_ROOT_PATH = RuoYiConfig.getProfile() + "/wbcard/";
    private static final String TEMPLATE = "./wbcard/templates/wbcard.pdf";
    private static final String FONT_PATH = "./wbcard/font/SourceHanSerifSC-Medium.otf";

    public String genWBCard(WBCard card) throws IOException {
        // 创建维保卡并返回文件路径
        String rootPath = WB_CARD_ROOT_PATH + card.getName();
        Path path = Paths.get(rootPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            System.out.println("文件夹创建成功: " + rootPath);
        } else {
            System.out.println("文件夹已存在: " + rootPath);
        }
        String filePath = rootPath + "/card_" + DateUtils.dateTimeNow() + ".pdf";
        PdfDocument doc = new PdfDocument(new PdfReader(TEMPLATE),
                new PdfWriter(filePath));

        // 2. 填充表单
        PdfAcroForm form = PdfAcroForm.getAcroForm(doc, false);
        if (form == null) {
            throw new IOException("表单不存在");
        }

        fill(card.getName(),
                form.getField("name"),
                TextAlignment.CENTER);

        fill(card.getCompany(),
                form.getField("company"),
                TextAlignment.CENTER);

        fill(card.getLeader(),
                form.getField("leader"),
                TextAlignment.CENTER);

        fill(card.getPhone(),
                form.getField("phone"),
                TextAlignment.CENTER);

        fill(card.getSystem1(),
                form.getField("system1"),
                TextAlignment.LEFT);

        fill(card.getSystem2(),
                form.getField("system2"),
                TextAlignment.LEFT);

        fill(card.getTime(),
                form.getField("time"),
                TextAlignment.CENTER);

        fill(card.getNo(),
                form.getField("no"),
                TextAlignment.CENTER);

        form.flattenFields();
        doc.close();
        return filePath;
    }

    public String genAttachment(String name, String leader) throws IOException {
        String rootPath = WB_CARD_ROOT_PATH + name;
        Path path = Paths.get(rootPath);
        String filePath = rootPath + "/attachment.pdf";

        if (!Files.exists(path)) {
            Files.createDirectories(path);
            System.out.println("文件夹创建成功: " + rootPath);
        } else {
            System.out.println("文件夹已存在: " + rootPath);
            if (Files.exists(Paths.get(filePath))) {
                System.out.println("文件已存在: " + filePath);
                return filePath;
            }
        }

        // 根据 leader 决定读取哪个文件
        Template template = templateService.getTemplateByName(leader);

        PdfFont font = PdfFontFactory.createFont(FONT_PATH, "Identity-H", PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);

        // 读取文件并创建生成
        PdfDocument doc = new PdfDocument(new PdfReader(template.getFile()), new PdfWriter(filePath));
        Document document = new Document(doc);
        PdfPage firstPage = doc.getFirstPage();
        float width = firstPage.getPageSize().getWidth();
        float height = firstPage.getPageSize().getHeight();


        Paragraph paragraph = new Paragraph("仅限" + name + "项目使用  再次复印无效");
        paragraph.setOpacity(0.3f);
//        paragraph.setRotationAngle(-45.0f);
        paragraph.setFont(font);
        paragraph.setFontSize(32);
        paragraph.setWidth(500);

        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
            document.showTextAligned(paragraph,
                    width / 2,
                    height / 2,
                    i,
                    TextAlignment.CENTER,
                    VerticalAlignment.MIDDLE,
                    -45);
        }
        document.close();
        doc.close();

        return filePath;
    }


    private static void fill(String value, PdfFormField field, TextAlignment justification) throws IOException {
        PdfFont font = PdfFontFactory.createFont(FONT_PATH, "Identity-H", PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        field.setFont(font);
        field.setFontSize(14);
        field.setJustification(justification);
        field.setValue(value);
        field.setReadOnly(true);
    }
}
