package top.aameow.erpnext.business;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * Edits the actual photo-group tables inside the selected report template.
 * It intentionally does not synthesize report conclusions or alter inspection records.
 */
@Service
public class ReportPhotoDocxService {
    public static final List<SystemDefinition> SYSTEMS = List.of(
        new SystemDefinition("fire_alarm","火灾自动报警系统",List.of("火灾自动报警系统")),
        new SystemDefinition("fire_water","消防给水系统",List.of("消防给水系统")),
        new SystemDefinition("hydrant","消火栓系统",List.of("消火栓系统")),
        new SystemDefinition("sprinkler","自动喷水灭火系统",List.of("自动喷水灭火系统")),
        new SystemDefinition("smoke_control","防烟排烟系统",List.of("防烟排烟系统")),
        new SystemDefinition("emergency_lighting","消防应急照明和疏散指示系统",List.of("消防应急照明和疏散指示系统","应急照明和疏散指示系统")),
        new SystemDefinition("fire_separation","防火分隔设施",List.of("防火分隔设施")),
        new SystemDefinition("electrical_fire","电气火灾监控系统",List.of("电气火灾监控系统")),
        new SystemDefinition("fire_power_monitoring","消防电源监控系统",List.of("消防电源监控系统")),
        new SystemDefinition("fire_alarm_broadcast","火灾警报和消防应急广播系统",List.of("火灾警报和消防应急广播系统")),
        new SystemDefinition("fire_phone","消防专用电话系统",List.of("消防专用电话系统")),
        new SystemDefinition("fire_elevator","消防电梯",List.of("消防电梯")),
        new SystemDefinition("fire_distribution","消防供配电设施",List.of("消防供配电设施")),
        new SystemDefinition("extinguisher","建筑灭火器",List.of("建筑灭火器"))
    );

    public record SystemDefinition(String code,String label,List<String> aliases) {}
    public record PhotoImage(String filename,byte[] bytes) {}
    private record PhotoGroup(XWPFTable table) {}

    public List<Map<String,Object>> inspect(InputStream template) {
        try(XWPFDocument document=new XWPFDocument(template)) {
            Map<String,List<PhotoGroup>> groups=scan(document);
            List<Map<String,Object>> result=new ArrayList<>();
            for(SystemDefinition system:SYSTEMS) {
                List<PhotoGroup> list=groups.getOrDefault(system.code(),List.of());
                int imageCount=list.stream().mapToInt(g->imageCount(g.table())).sum();
                result.add(Map.of("systemCode",system.code(),"label",system.label(),
                    "headingFound",!list.isEmpty(),"groupCount",list.size(),"imageCount",imageCount,
                    "capacity",list.stream().mapToInt(g->cellCount(g.table())).sum()));
            }
            return result;
        } catch(IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无法读取 DOCX 模板",e);
        }
    }

    public byte[] insert(InputStream template,Map<String,List<PhotoImage>> photos,Map<String,String> modes) {
        try(XWPFDocument document=new XWPFDocument(template);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            Map<String,List<PhotoGroup>> groups=scan(document);
            for(SystemDefinition system:SYSTEMS) {
                List<PhotoImage> images=photos.getOrDefault(system.code(),List.of());
                if(images.isEmpty()) continue;
                List<PhotoGroup> section=groups.getOrDefault(system.code(),List.of());
                if(section.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "报告模板中未找到“"+system.label()+"”章节的“现场检测照片”位置");
                String mode=modes.getOrDefault(system.code(),"replace");
                if(!Set.of("replace","append").contains(mode)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效的照片插入方式");
                if("replace".equals(mode)) replaceSection(section,images);
                else appendSection(section.get(section.size()-1).table(),images);
            }
            document.write(out);
            return out.toByteArray();
        } catch(ResponseStatusException e) { throw e; }
        catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片写入 DOCX 失败",e); }
    }

    private Map<String,List<PhotoGroup>> scan(XWPFDocument document) {
        Map<String,List<PhotoGroup>> result=new LinkedHashMap<>();
        for(SystemDefinition system:SYSTEMS) result.put(system.code(),new ArrayList<>());
        String currentSystem=null;
        List<IBodyElement> body=document.getBodyElements();
        for(int i=0;i<body.size();i++) {
            IBodyElement element=body.get(i);
            if(!(element instanceof XWPFParagraph paragraph)) continue;
            String normalized=normalize(paragraph.getText());
            String matched=systemForHeading(normalized);
            if(matched!=null) currentSystem=matched;
            if(currentSystem!=null && "现场检测照片".equals(normalized)) {
                for(int j=i+1;j<Math.min(body.size(),i+4);j++) {
                    if(body.get(j) instanceof XWPFTable table) {
                        result.get(currentSystem).add(new PhotoGroup(table));
                        break;
                    }
                    if(body.get(j) instanceof XWPFParagraph p && !normalize(p.getText()).isEmpty()) break;
                }
            }
        }
        return result;
    }

    private String systemForHeading(String normalized) {
        if(normalized.isEmpty()) return null;
        for(SystemDefinition system:SYSTEMS) {
            for(String alias:system.aliases()) {
                if(normalized.equals(normalize(alias)) || normalized.endsWith(normalize(alias))) return system.code();
            }
        }
        return null;
    }

    private String normalize(String text) {
        if(text==null) return "";
        return text.replaceAll("[\\s\\p{Punct}，。；：、（）()【】《》#_—-]+","").trim();
    }

    private void replaceSection(List<PhotoGroup> groups,List<PhotoImage> images) throws IOException {
        List<XWPFTableCell> cells=new ArrayList<>();
        for(PhotoGroup group:groups) {
            for(XWPFTableRow row:group.table().getRows()) cells.addAll(row.getTableCells());
        }
        int slot=0;
        for(XWPFTableCell cell:cells) clearCell(cell);
        for(PhotoImage image:images) {
            if(slot<cells.size()) putImage(cells.get(slot++),image);
            else break;
        }
        if(slot<images.size()) appendRows(groups.get(groups.size()-1).table(),images.subList(slot,images.size()));
    }

    private void appendSection(XWPFTable lastTable,List<PhotoImage> images) throws IOException {
        appendRows(lastTable,images);
    }

    private void appendRows(XWPFTable table,List<PhotoImage> images) throws IOException {
        int index=0;
        while(index<images.size()) {
            XWPFTableRow row=table.createRow();
            while(row.getTableCells().size()<2) row.addNewTableCell();
            List<XWPFTableCell> cells=row.getTableCells();
            putImage(cells.get(0),images.get(index++));
            if(index<images.size()) putImage(cells.get(1),images.get(index++));
        }
    }

    private int cellCount(XWPFTable table) {
        return table.getRows().stream().mapToInt(r->r.getTableCells().size()).sum();
    }

    private int imageCount(XWPFTable table) {
        int count=0;
        for(XWPFTableRow row:table.getRows()) for(XWPFTableCell cell:row.getTableCells())
            for(XWPFParagraph p:cell.getParagraphs()) for(XWPFRun run:p.getRuns()) count+=run.getEmbeddedPictures().size();
        return count;
    }

    private void clearCell(XWPFTableCell cell) {
        List<XWPFParagraph> paragraphs=cell.getParagraphs();
        for(int p=paragraphs.size()-1;p>=1;p--) cell.removeParagraph(p);
        if(cell.getParagraphs().isEmpty()) cell.addParagraph();
        XWPFParagraph paragraph=cell.getParagraphs().get(0);
        for(int i=paragraph.getRuns().size()-1;i>=0;i--) paragraph.removeRun(i);
        paragraph.setAlignment(ParagraphAlignment.CENTER);
    }

    private void putImage(XWPFTableCell cell,PhotoImage image) throws IOException {
        clearCell(cell);
        BufferedImage decoded=ImageIO.read(new ByteArrayInputStream(image.bytes()));
        if(decoded==null || decoded.getWidth()<1 || decoded.getHeight()<1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片无法解码："+image.filename());
        int type=pictureType(image.bytes());
        double scale=Math.min(1.0,Math.min(245.0/decoded.getWidth(),165.0/decoded.getHeight()));
        int width=Math.max(1,(int)Math.round(decoded.getWidth()*scale));
        int height=Math.max(1,(int)Math.round(decoded.getHeight()*scale));
        XWPFRun run=cell.getParagraphs().get(0).createRun();
        try {
            run.addPicture(new ByteArrayInputStream(image.bytes()),type,image.filename(),Units.toEMU(width),Units.toEMU(height));
        } catch(org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException("不支持的图片格式："+image.filename(),e);
        }
    }

    private int pictureType(byte[] bytes) {
        if(bytes.length>3&&(bytes[0]&255)==255&&(bytes[1]&255)==216) return Document.PICTURE_TYPE_JPEG;
        if(bytes.length>8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71) return Document.PICTURE_TYPE_PNG;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅支持 JPG 和 PNG 照片");
    }
}
