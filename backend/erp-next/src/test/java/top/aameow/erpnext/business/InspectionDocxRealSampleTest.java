package top.aameow.erpnext.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, private real-sample acceptance; never runs automatically and never commits customer originals. */
@EnabledIfEnvironmentVariable(named="ERP_INSPECTION_SAMPLE_DIR",matches=".+")
class InspectionDocxRealSampleTest {
    @Test void generateCopiesForManualWordAcceptance()throws Exception{
        Path input=Paths.get(System.getenv("ERP_INSPECTION_SAMPLE_DIR")).toRealPath();
        String outputSetting=System.getenv("ERP_INSPECTION_OUTPUT_DIR");
        assertNotNull(outputSetting,"Set ERP_INSPECTION_OUTPUT_DIR to a private directory outside the repository");
        Path output=Paths.get(outputSetting).toAbsolutePath().normalize();
        assertFalse(output.startsWith(input),"Output directory must be separate from sample source");
        Files.createDirectories(output);
        try(var stream=Files.list(input)){
            var samples=stream.filter(p->p.getFileName().toString().endsWith(".docx")).filter(p->p.getFileName().toString().contains("知苑")||p.getFileName().toString().contains("牧云台")).sorted().toList();
            assertEquals(2,samples.size(),"Expected the two original sample DOCX files");
            var mapper=new ObjectMapper();var binder=new InspectionDocxBinder();var photos=new ReportDocxPhotoInserter();
            byte[] testPhoto;
            try(var image=new ByteArrayOutputStream()){
                var bitmap=new BufferedImage(160,100,BufferedImage.TYPE_INT_RGB);
                var g=bitmap.createGraphics();
                g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,160,100);g.setColor(java.awt.Color.BLACK);g.drawString("TEST ONLY",20,50);g.dispose();
                ImageIO.write(bitmap,"png",image);testPhoto=image.toByteArray();
            }
            for(int i=0;i<samples.size();i++){
                Path sample=samples.get(i);
                byte[] source=Files.readAllBytes(sample);
                String before=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
                var profile=mapper.createObjectNode();
                profile.putObject("general").put("projectName","仅供版式验收－不得签发").put("address","验收测试地址").put("projectCategory","测试");
                var buildings=profile.putArray("buildings");
                for(int n=1;n<=5;n++)buildings.addObject().put("name","测试单体 "+n);
                Path generated=output.resolve("TEST-ONLY-不得签发-"+(i+1)+".docx");
                assertFalse(Files.exists(generated),"Do not overwrite existing acceptance copies");
                try(var doc=new XWPFDocument(new ByteArrayInputStream(source))){
                    var first=binder.inspect(doc,profile,"TEST-ONLY","版式验收",List.of());
                    var suggested=first.suggestedBindings();
                    assertTrue(suggested.stream().anyMatch(b->b.field().equals("general.projectName")),"Actual engineering name must be recognized");
                    assertTrue(suggested.stream().anyMatch(b->b.field().equals("general.address")),"Actual engineering address must be recognized");
                    assertTrue(suggested.stream().anyMatch(b->b.field().startsWith("buildings.")),"Actual building rows must be recognized");
                    var preflight=binder.inspect(doc,profile,"TEST-ONLY","版式验收",suggested);
                    assertTrue(preflight.errors().isEmpty(),preflight.errors().toString());
                    binder.fill(doc,profile,"TEST-ONLY","版式验收",suggested);
                    photos.insert(doc,List.of(new ReportDocxPhotoInserter.Photo("消防给水系统",testPhoto,"TEST-ONLY.png","2026-11-24","验收测试")),false);
                    var note=doc.createParagraph();note.createRun().setText("仅供内部版式验收；含原样本证照及历史资料，禁止作为正式报告签发、交付或外传。");
                    try(var out=Files.newOutputStream(generated,StandardOpenOption.CREATE_NEW)){doc.write(out);}
                }
                try(var result=new XWPFDocument(Files.newInputStream(generated))){
                    assertTrue(result.getTables().stream().flatMap(t->t.getRows().stream()).flatMap(row->row.getTableCells().stream()).anyMatch(cell->cell.getText().contains("仅供版式验收")));
                }
                assertEquals(before,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(sample))),"Original sample must remain byte-identical");
                System.out.println("Manual Word acceptance copy: "+generated);
            }
        }
    }
}
