package top.aameow.erpnext.business;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

/**
 * Inserts classified photo groups into the last existing "现场检测照片" block
 * inside each matching system section. It never relies on page numbers or
 * fixed paragraph indexes, and it leaves existing inspection tables/photos intact.
 */
public final class DocxPhotoInserter {
    private static final String NOTE = "注：应附彩色照片并注明日期，日期应如实填写。";
    private static final Map<String,String> ALIASES = Map.of(
        "应急照明和疏散指示系统","消防应急照明和疏散指示系统",
        "消防供电设施","消防供配电设施"
    );
    public static final List<String> SYSTEMS = List.of(
        "火灾自动报警系统","消防给水系统","消火栓系统","自动喷水灭火系统",
        "防烟排烟系统","消防应急照明和疏散指示系统","防火分隔设施",
        "电气火灾监控系统","消防电源监控系统","火灾警报和消防应急广播系统",
        "消防专用电话系统","消防电梯","消防供配电设施","建筑灭火器"
    );

    public record Photo(String systemCode,String filename,byte[] bytes) { }
    private record Anchor(XWPFParagraph heading,XWPFTable tableStyle,XWPFParagraph noteStyle,IBodyElement after,int originalIndex) { }

    private DocxPhotoInserter() { }

    public static byte[] insert(byte[] source,List<Photo> photos) throws IOException {
        if(source==null||source.length==0) throw new IllegalArgumentException("DOCX 模板为空");
        if(photos==null||photos.isEmpty()) throw new IllegalArgumentException("没有待插入照片");
        try(XWPFDocument document=new XWPFDocument(new ByteArrayInputStream(source));
            ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            Map<String,List<Photo>> grouped=new LinkedHashMap<>();
            for(Photo photo:photos) {
                if(!SYSTEMS.contains(photo.systemCode())) throw new IllegalArgumentException("不支持的消防系统分类："+photo.systemCode());
                validateImage(photo.bytes());
                grouped.computeIfAbsent(photo.systemCode(),k->new ArrayList<>()).add(photo);
            }
            Map<String,Anchor> anchors=findAnchors(document);
            for(String system:grouped.keySet()) {
                if(!anchors.containsKey(system))
                    throw new IllegalArgumentException("报告模板中找不到“"+system+"”系统的现场检测照片位置，请选择对应检测范围的报告模板");
            }
            List<Map.Entry<String,List<Photo>>> insertionOrder=new ArrayList<>(grouped.entrySet());
            insertionOrder.sort((a,b)->Integer.compare(
                anchors.get(b.getKey()).originalIndex(),anchors.get(a.getKey()).originalIndex()));
            for(Map.Entry<String,List<Photo>> entry:insertionOrder) {
                Anchor anchor=anchors.get(entry.getKey());
                if(anchor==null) throw new IllegalArgumentException("报告模板中找不到“"+entry.getKey()+"”系统的现场检测照片位置");
                IBodyElement cursorAfter=anchor.after();
                List<Photo> systemPhotos=entry.getValue();
                for(int offset=0;offset<systemPhotos.size();offset+=4) {
                    int end=Math.min(offset+4,systemPhotos.size());
                    cursorAfter=insertPhotoGroup(document,cursorAfter,anchor.heading(),anchor.tableStyle(),anchor.noteStyle(),systemPhotos.subList(offset,end));
                }
            }
            document.write(output);
            return output.toByteArray();
        } catch(org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException("照片格式无法写入 DOCX",e);
        }
    }

    private static Map<String,Anchor> findAnchors(XWPFDocument document) {
        Map<String,Anchor> anchors=new HashMap<>();
        String activeSystem=null;
        List<IBodyElement> body=document.getBodyElements();
        for(int i=0;i<body.size();i++) {
            IBodyElement element=body.get(i);
            if(!(element instanceof XWPFParagraph paragraph)) continue;
            String text=paragraph.getText()==null?"":paragraph.getText().trim();
            String system=systemHeading(text);
            if(system!=null) activeSystem=system;
            if(activeSystem==null||!isPhotoHeading(text)) continue;
            IBodyElement after=element;
            XWPFTable tableStyle=null;
            XWPFParagraph noteStyle=null;
            if(i+1<body.size()&&body.get(i+1) instanceof XWPFTable table) {
                tableStyle=table;
                after=table;
                if(i+2<body.size()&&body.get(i+2) instanceof XWPFParagraph note &&
                    note.getText()!=null&&note.getText().trim().startsWith("注：")) {
                    noteStyle=note;
                    after=note;
                }
            }
            if(tableStyle!=null) anchors.put(activeSystem,new Anchor(paragraph,tableStyle,noteStyle,after,i));
        }
        return anchors;
    }

    private static boolean isPhotoHeading(String text) {
        return text.replaceAll("\\s+","").equals("现场检测照片");
    }

    private static String systemHeading(String text) {
        String normalized=text.replaceAll("\\s+","").trim();
        normalized=normalized.replaceFirst("^[一二三四五六七八九十百]+[、.．]","");
        normalized=normalized.replaceFirst("^\\d+[、.．]","");
        if(ALIASES.containsKey(normalized)) normalized=ALIASES.get(normalized);
        return SYSTEMS.contains(normalized)?normalized:null;
    }

    private static IBodyElement insertPhotoGroup(XWPFDocument document,IBodyElement after,
                                                   XWPFParagraph styleSource,XWPFTable tableStyle,
                                                   XWPFParagraph noteStyle,List<Photo> photos)
        throws IOException,org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        XWPFParagraph heading;
        try(XmlCursor cursor=afterCursor(after)) {
            heading=document.insertNewParagraph(cursor);
        }
        if(heading==null) throw new IOException("无法在报告系统章节中插入照片标题");
        copyParagraphStyle(styleSource,heading);
        XWPFRun headingRun=heading.createRun();
        copyRunStyle(styleSource,headingRun);
        headingRun.setText("现场检测照片");

        XWPFTable table;
        try(XmlCursor cursor=afterCursor(heading)) {
            table=document.insertNewTbl(cursor);
        }
        if(table==null) throw new IOException("无法在报告系统章节中插入照片表格");
        copyTableStyle(tableStyle,table);
        XWPFTableRow first=table.getRow(0);
        ensureTwoCells(first);
        XWPFTableRow second=table.createRow();
        ensureTwoCells(second);
        List<XWPFTableRow> rows=List.of(first,second);
        copyRowStyle(tableStyle,0,first);
        copyRowStyle(tableStyle,Math.min(1,tableStyle.getNumberOfRows()-1),second);
        for(int i=0;i<4;i++) {
            XWPFTableCell cell=rows.get(i/2).getCell(i%2);
            XWPFTableCell sourceCell=tableStyle.getRow(Math.min(i/2,tableStyle.getNumberOfRows()-1)).getCell(Math.min(i%2,tableStyle.getRow(Math.min(i/2,tableStyle.getNumberOfRows()-1)).getTableCells().size()-1));
            if(sourceCell.getCTTc().isSetTcPr()) cell.getCTTc().setTcPr((CTTcPr)sourceCell.getCTTc().getTcPr().copy());
            cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
            XWPFParagraph paragraph=cell.getParagraphs().isEmpty()?cell.addParagraph():cell.getParagraphs().get(0);
            if(!sourceCell.getParagraphs().isEmpty()) copyParagraphStyle(sourceCell.getParagraphs().get(0),paragraph);
            for(int p=paragraph.getRuns().size()-1;p>=0;p--) paragraph.removeRun(p);
            paragraph.setAlignment(ParagraphAlignment.CENTER);
            if(i<photos.size()) {
                Photo photo=photos.get(i);
                ImageSize size=imageSize(photo.bytes());
                XWPFRun run=paragraph.createRun();
                run.addPicture(new ByteArrayInputStream(photo.bytes()),pictureType(photo.bytes()),safeFilename(photo.filename()),
                    Units.toEMU(size.width()),Units.toEMU(size.height()));
            }
        }
        first.setCantSplitRow(true);
        second.setCantSplitRow(true);

        XWPFParagraph note;
        try(XmlCursor cursor=afterCursor(table)) {
            note=document.insertNewParagraph(cursor);
        }
        if(note==null) throw new IOException("无法插入照片说明");
        if(noteStyle!=null) {
            copyParagraphStyle(noteStyle,note);
            XWPFRun noteRun=note.createRun();
            copyRunStyle(noteStyle,noteRun);
            noteRun.setText(NOTE);
        } else note.createRun().setText(NOTE);
        return note;
    }

    private static XmlCursor afterCursor(IBodyElement element) {
        XmlCursor cursor;
        if(element instanceof XWPFParagraph paragraph) cursor=paragraph.getCTP().newCursor();
        else if(element instanceof XWPFTable table) cursor=table.getCTTbl().newCursor();
        else throw new IllegalArgumentException("不支持的 DOCX 元素");
        cursor.toEndToken();
        cursor.toNextToken();
        return cursor;
    }

    private static void copyTableStyle(XWPFTable source,XWPFTable target) {
        if(source.getCTTbl().getTblPr()!=null) target.getCTTbl().setTblPr((CTTblPr)source.getCTTbl().getTblPr().copy());
        if(source.getCTTbl().getTblGrid()!=null) target.getCTTbl().setTblGrid((CTTblGrid)source.getCTTbl().getTblGrid().copy());
    }

    private static void copyRowStyle(XWPFTable source,int sourceIndex,XWPFTableRow target) {
        if(source.getNumberOfRows()==0) return;
        XWPFTableRow original=source.getRow(Math.min(sourceIndex,source.getNumberOfRows()-1));
        if(original.getCtRow().isSetTrPr()) target.getCtRow().setTrPr((CTTrPr)original.getCtRow().getTrPr().copy());
    }

    private static void ensureTwoCells(XWPFTableRow row) {
        while(row.getTableCells().size()<2) row.addNewTableCell();
        while(row.getTableCells().size()>2) row.removeCell(row.getTableCells().size()-1);
    }

    private static void copyParagraphStyle(XWPFParagraph source,XWPFParagraph target) {
        if(source.getCTP().isSetPPr()) target.getCTP().setPPr((CTPPr)source.getCTP().getPPr().copy());
        target.setAlignment(source.getAlignment());
        target.setSpacingBefore(source.getSpacingBefore());
        target.setSpacingAfter(source.getSpacingAfter());
    }

    private static void copyRunStyle(XWPFParagraph source,XWPFRun target) {
        if(source.getRuns().isEmpty()) return;
        XWPFRun original=source.getRuns().get(0);
        if(original.getCTR().isSetRPr()) target.getCTR().setRPr((CTRPr)original.getCTR().getRPr().copy());
    }

    private record ImageSize(int width,int height) { }
    private static ImageSize imageSize(byte[] bytes) throws IOException {
        BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));
        if(image==null) throw new IllegalArgumentException("照片不是有效的 JPG/PNG 图片");
        long pixels=(long)image.getWidth()*image.getHeight();
        if(image.getWidth()<1||image.getHeight()<1||pixels>40000000L)
            throw new IllegalArgumentException("照片尺寸无效或超过 4000 万像素");
        double scale=Math.min(1.0,Math.min(220.0/image.getWidth(),160.0/image.getHeight()));
        return new ImageSize(Math.max(1,(int)Math.round(image.getWidth()*scale)),
            Math.max(1,(int)Math.round(image.getHeight()*scale)));
    }

    private static void validateImage(byte[] bytes) throws IOException {
        if(bytes==null||bytes.length<8||pictureType(bytes)==-1) throw new IllegalArgumentException("仅支持有效的 JPG/PNG 图片");
        imageSize(bytes);
    }

    private static int pictureType(byte[] bytes) {
        if(bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255)
            return Document.PICTURE_TYPE_JPEG;
        if(bytes.length>=8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71&&
            bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10)
            return Document.PICTURE_TYPE_PNG;
        return -1;
    }

    private static String safeFilename(String filename) {
        if(filename==null||filename.isBlank()) return "photo.jpg";
        String safe=filename.replace('\\','/').replaceAll("[\\p{Cntrl}]","_");
        safe=safe.substring(safe.lastIndexOf('/')+1);
        return safe.length()>180?safe.substring(safe.length()-180):safe;
    }
}
