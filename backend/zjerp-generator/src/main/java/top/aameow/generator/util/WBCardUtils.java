package top.aameow.generator.util;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;
import top.aameow.common.config.RuoYiConfig;
import top.aameow.common.exception.ServiceException;
import top.aameow.generator.domain.WBCard;

/** A4 landscape, measured Unicode text; no AcroForms or generated attachment. */
@Component
public class WBCardUtils {
    private static final float SCALE=841.89f/1888f, HEIGHT=595.28f;
    private final Path templatePath,fontPath;
    public WBCardUtils(){this(Paths.get("./wbcard/templates/card.png"),Paths.get("./wbcard/font/NotoSansSC-Regular.ttf"));}
    public WBCardUtils(Path templatePath,Path fontPath){this.templatePath=templatePath;this.fontPath=fontPath;}
    public byte[] render(WBCard card) throws IOException {
        required(card.getName(),"项目名称",255);required(card.getCompany(),"维保机构",255);
        required(card.getLeader(),"负责人",100);required(card.getPhone(),"联系电话",255);
        required(card.getNo(),"维保卡编号",100);required(card.getTime(),"维保日期",200);
        String systems=Objects.toString(card.getSystem1(),"")+ (card.getSystem2()==null?"":"\n"+card.getSystem2());
        required(systems,"维护保养内容",4000);
        try(PDDocument doc=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            PDPage page=new PDPage(new PDRectangle(841.89f,HEIGHT));doc.addPage(page);
            PDType0Font font=PDType0Font.load(doc,fontPath.toFile());
            try(PDPageContentStream stream=new PDPageContentStream(doc,page)) {
                stream.drawImage(PDImageXObject.createFromFile(templatePath.toString(),doc),0,0,841.89f,HEIGHT);
                box(stream,font,card.getName(),390,385,390,100,"项目名称");
                box(stream,font,card.getCompany(),1385,385,380,100,"维保机构");
                box(stream,font,card.getLeader(),445,525,335,85,"负责人");
                flow(stream,font,phone(card.getPhone()),new float[][]{{1110,525,655,85,1}},"联系电话");
                // Each printed underline has its own box; wrap across these measured widths.
                flow(stream,font,systems,new float[][]{{505,650,1260,90},{130,775,1635,90}},"维护保养内容");
                box(stream,font,dates(card.getTime()),505,900,1260,90,"维保日期");
                box(stream,font,card.getNo(),1565,1195,205,45,"维保卡编号");
            }
            doc.getDocumentInformation().setTitle(card.getName()+" - 消防维护保养公示卡");
            doc.getDocumentInformation().setCreator("ZJERP / Apache PDFBox");doc.save(out);return out.toByteArray();
        } catch(IllegalArgumentException ex) {throw new ServiceException("内容包含字体不支持的字符，请替换后重试");}
    }
    static String phone(String value) {
        String personal=value.strip().replaceFirst("^0371-53392119\\s*[/／]\\s*", "");
        return personal.equals("0371-53392119")?personal:"0371-53392119/"+personal;
    }
    static String dates(String value) {
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d{4})[-年](\\d{1,2})[-月](\\d{1,2})日?").matcher(value);
        java.util.List<String> parts=new java.util.ArrayList<>();
        while(m.find()) {java.time.LocalDate day=java.time.LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));parts.add(day.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")));}
        return parts.size()==2?String.join("至",parts):value.replace(" 至 ","至");
    }
    private static void required(String s,String label,int max) {
        if(s==null||s.isBlank()||s.length()>max)throw new ServiceException(label+"不能为空且不能超过"+max+"字");
        if(s.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw new ServiceException(label+"包含无效控制字符");
    }
    static List<String> wrap(PDType0Font font,String text,float size,float width) throws IOException {
        List<String> result=new ArrayList<>();StringBuilder line=new StringBuilder();
        for(int cp:text.replace("\r","").replace('\t',' ').codePoints().toArray()) {
            if(cp=='\n'){result.add(line.toString());line.setLength(0);continue;}
            String next=new String(Character.toChars(cp));
            if(font.getStringWidth(line+next)*size/1000f>width && line.length()>0){result.add(line.toString());line.setLength(0);}
            if(font.getStringWidth(next)*size/1000f>width)throw new ServiceException("文字超出可打印区域");
            line.append(next);
        }
        if(line.length()>0)result.add(line.toString());return result;
    }
    private static void box(PDPageContentStream s,PDType0Font f,String text,float x,float y,float w,float h,String label) throws IOException {
        flow(s,f,text,new float[][]{{x,y,w,h}},label);
    }
    private static void flow(PDPageContentStream s,PDType0Font f,String text,float[][] regions,String label) throws IOException {
        for(float size=14;size>=7;size-=0.5f){
            String remaining=text.replace("\r","").strip();List<List<String>> all=new ArrayList<>();
            for(float[] r:regions){
                List<String> lines=wrap(f,remaining,size,r[2]*SCALE);int capacity=regions.length>1||r.length>4?1:Math.max(1,(int)(r[3]*SCALE/(size*1.25f)));
                int take=Math.min(capacity,lines.size());all.add(new ArrayList<>(lines.subList(0,take)));
                remaining=String.join(regions.length>1?"":"\n",lines.subList(take,lines.size()));
            }
            if(!remaining.isEmpty())continue;
            for(int i=0;i<regions.length;i++){
                float[] r=regions[i];List<String> lines=all.get(i);float top=HEIGHT-r[1]*SCALE;
                float baseline=top-r[3]*SCALE+size*0.22f+(lines.size()-1)*size*1.25f;
                for(String line:lines){s.beginText();s.setFont(f,size);float width=f.getStringWidth(line)*size/1000f;
                    s.newLineAtOffset(r[0]*SCALE+(regions.length>1?0:(r[2]*SCALE-width)/2),baseline);s.showText(line);s.endText();baseline-=size*1.25f;}
            }
            return;
        }
        throw new ServiceException(label+"过长，缩小至最小字号后仍无法完整排版，请精简内容；没有生成截断文件");
    }
    public String genWBCard(WBCard card) throws IOException {
        byte[] pdf=render(card);Path directory=Paths.get(RuoYiConfig.getProfile(),"wbcard",java.time.LocalDate.now().toString());
        Files.createDirectories(directory);Path target=directory.resolve(UUID.randomUUID()+".pdf");Files.write(target,pdf,StandardOpenOption.CREATE_NEW);return target.toString();
    }
}
