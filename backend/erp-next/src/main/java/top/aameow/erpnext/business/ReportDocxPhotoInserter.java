package top.aameow.erpnext.business;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Replaces the existing sample-photo grids inside each system section.
 * It deliberately uses the template's own 2x2 photo tables instead of
 * appending an unrelated gallery at the end of the report.
 */
@Service
public class ReportDocxPhotoInserter {
    private static final List<String> SYSTEMS = List.of(
        "火灾自动报警系统", "消防给水系统", "消火栓系统", "自动喷水灭火系统",
        "防烟排烟系统", "消防应急照明和疏散指示系统", "防火分隔设施",
        "电气火灾监控系统", "消防电源监控系统", "火灾警报和消防应急广播系统",
        "消防专用电话系统", "消防电梯", "消防供配电设施", "建筑灭火器"
    );
    private static final Pattern PREFIX = Pattern.compile("^(?:[（(]\\s*[一二三四五六七八九十百]+\\s*[）)]|[一二三四五六七八九十百]+[、.．])\\s*");
    public record Photo(String system, byte[] bytes, String filename, String takenOn, String caption) { }
    private record Block(int headingIndex, int tableIndex, int noteIndex, XWPFTable table) { }

    public Set<String> supportedSystems() { return new LinkedHashSet<>(SYSTEMS); }

    public void insert(XWPFDocument document, List<Photo> photos) { insert(document,photos,true); }

    public void insert(XWPFDocument document, List<Photo> photos, boolean replaceExisting) {
        Map<String,List<Photo>> bySystem = new LinkedHashMap<>();
        for (Photo photo : photos) {
            String system = normalize(photo.system());
            if (system == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"存在无效的消防系统分类");
            bySystem.computeIfAbsent(system,k->new ArrayList<>()).add(photo);
        }

        List<IBodyElement> body = new ArrayList<>(document.getBodyElements());
        String activeSystem = null;
        Map<String,List<Block>> blocks = new LinkedHashMap<>();
        Map<String,Integer> sectionOrder = new LinkedHashMap<>();
        for (int i=0;i<body.size();i++) {
            IBodyElement element=body.get(i);
            if (element instanceof XWPFParagraph paragraph) {
                String text=paragraph.getText()==null?"":paragraph.getText().trim();
                String system=normalize(text);
                if (system!=null) {
                    activeSystem=system;
                    sectionOrder.putIfAbsent(system,i);
                } else if ("现场检测照片".equals(text) && activeSystem!=null) {
                    int tableIndex=i+1;
                    int noteIndex=i+2;
                    if (tableIndex<body.size() && body.get(tableIndex) instanceof XWPFTable table
                        && !table.getRows().isEmpty()) {
                        blocks.computeIfAbsent(activeSystem,k->new ArrayList<>())
                            .add(new Block(i,tableIndex,noteIndex,table));
                    }
                }
            }
        }

        // A category must exist as a real system heading in this template.
        for (String system : bySystem.keySet()) {
            if (!sectionOrder.containsKey(system))
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "所选 DOCX 模板中没有“"+system+"”章节，不能安全插入该分类照片");
            if (!blocks.containsKey(system) || blocks.get(system).isEmpty())
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "模板“"+system+"”章节没有可识别的现场照片表格，暂不能自动插入");
        }

        if(!replaceExisting) {
            for(Map.Entry<String,List<Photo>> entry:bySystem.entrySet()) {
                List<Block> found=blocks.get(entry.getKey());
                if(found==null||found.isEmpty())throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "模板“"+entry.getKey()+"”章节没有可识别的现场照片表格，暂不能自动插入");
                XWPFTable target=found.get(found.size()-1).table();
                List<Photo> assigned=entry.getValue();
                for(int i=0;i<assigned.size();) {
                    XWPFTableRow row=target.createRow();
                    ensureTwoCells(row);
                    fillCell(row.getCell(0),assigned.get(i++));
                    if(i<assigned.size())fillCell(row.getCell(1),assigned.get(i++));
                    else clearCell(row.getCell(1));
                }
            }
            return;
        }

        // Remove all unused photo groups first (descending indexes), then fill
        // the retained native tables. This avoids leaking template/sample photos.
        List<Integer> removals=new ArrayList<>();
        for (Map.Entry<String,List<Block>> entry : blocks.entrySet()) {
            List<Photo> assigned=bySystem.getOrDefault(entry.getKey(),List.of());
            int required=(assigned.size()+3)/4;
            List<Block> found=entry.getValue();
            for(int n=required;n<found.size();n++) {
                Block b=found.get(n);
                removals.add(b.headingIndex()); removals.add(b.tableIndex());
                if (b.noteIndex()<body.size() && body.get(b.noteIndex()) instanceof XWPFParagraph p
                    && p.getText().startsWith("注：应附彩色照片")) removals.add(b.noteIndex());
            }
        }
        removals.stream().distinct().sorted(Comparator.reverseOrder()).forEach(document::removeBodyElement);

        // Re-read the current body after removals and re-associate remaining
        // photo grids with the active system heading.
        body=new ArrayList<>(document.getBodyElements());
        activeSystem=null;
        Map<String,List<XWPFTable>> retained=new LinkedHashMap<>();
        for(int i=0;i<body.size();i++) {
            IBodyElement element=body.get(i);
            if(element instanceof XWPFParagraph p) {
                String text=p.getText()==null?"":p.getText().trim();
                String system=normalize(text);
                if(system!=null) activeSystem=system;
                else if("现场检测照片".equals(text) && activeSystem!=null && i+1<body.size()
                    && body.get(i+1) instanceof XWPFTable table) {
                    retained.computeIfAbsent(activeSystem,k->new ArrayList<>()).add(table);
                }
            }
        }

        for(String system:sectionOrder.keySet()) {
            List<Photo> assigned=bySystem.getOrDefault(system,List.of());
            List<XWPFTable> tables=retained.getOrDefault(system,List.of());
            if(assigned.isEmpty()) continue;
            if(tables.isEmpty()) continue; // checked above
            int capacity=tables.size()*4;
            XWPFTable last=tables.get(tables.size()-1);
            while(assigned.size()>capacity) {
                XWPFTableRow row=last.createRow();
                ensureTwoCells(row);
                capacity+=2;
            }
            int index=0;
            for(XWPFTable table:tables) {
                for(XWPFTableRow row:table.getRows()) {
                    ensureTwoCells(row);
                    for(int col=0;col<2;col++) {
                        XWPFTableCell cell=row.getCell(col);
                        if(index<assigned.size()) fillCell(cell,assigned.get(index++));
                        else clearCell(cell);
                    }
                }
            }
            for(XWPFTableRow row:last.getRows()) {
                // no-op: capacity was established above; rows are filled in table order
            }
        }
    }

    private static void ensureTwoCells(XWPFTableRow row) {
        while(row.getTableCells().size()<2) row.addNewTableCell();
    }

    private static void fillCell(XWPFTableCell cell,Photo photo) {
        clearCell(cell);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        XWPFParagraph image=cell.getParagraphs().get(0);
        image.setAlignment(ParagraphAlignment.CENTER);
        try {
            java.awt.image.BufferedImage decoded=javax.imageio.ImageIO.read(new ByteArrayInputStream(photo.bytes()));
            if(decoded==null) throw new IllegalArgumentException("invalid image");
            int type=photo.bytes().length>3 && (photo.bytes()[0]&255)==255 ? Document.PICTURE_TYPE_JPEG : Document.PICTURE_TYPE_PNG;
            double scale=Math.min(1.0,Math.min(245.0/decoded.getWidth(),160.0/decoded.getHeight()));
            int width=Math.max(1,(int)Math.round(decoded.getWidth()*scale));
            int height=Math.max(1,(int)Math.round(decoded.getHeight()*scale));
            image.createRun().addPicture(new ByteArrayInputStream(photo.bytes()),type,photo.filename(),Units.toEMU(width),Units.toEMU(height));
        } catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"照片无法写入 DOCX："+photo.filename(),e);
        }
        String date=photo.takenOn()==null?"":photo.takenOn().replace("-",".");
        String label=(date+(photo.caption()==null||photo.caption().isBlank()?"":"  "+photo.caption())).trim();
        if(!label.isBlank()) {
            XWPFParagraph caption=cell.addParagraph();
            caption.setAlignment(ParagraphAlignment.CENTER);
            caption.createRun().setText(label);
        }
    }

    private static void clearCell(XWPFTableCell cell) {
        while(!cell.getParagraphs().isEmpty()) cell.removeParagraph(0);
        cell.addParagraph();
    }

    private static String normalize(String text) {
        if(text==null) return null;
        String value=PREFIX.matcher(text.trim()).replaceFirst("").trim();
        if("消防应急照明及疏散指示系统".equals(value)) value="消防应急照明和疏散指示系统";
        if("消防应急照明和疏散指示系统".equals(value) || "消防应急照明及疏散指示系统".equals(value)) return value;
        return SYSTEMS.contains(value)?value:null;
    }
}
