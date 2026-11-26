package top.aameow.erpnext.business;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

/** A4 landscape, measured Unicode text; no AcroForms or generated attachment. */
@Component
public class MaintenanceCardRenderer {
    private static final float SCALE=841.89f/1888f, HEIGHT=595.28f;
    public record Card(String name,String company,String leader,String phone,String no,String time,String system1) {}
    private static org.springframework.web.server.ResponseStatusException invalid(String message){
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,message);
    }
    private InputStream resource(String name) throws IOException {
        return new org.springframework.core.io.ClassPathResource("wbcard/"+name).getInputStream();
    }
    public byte[] render(Card card) throws IOException {
        required(card.name(),"项目名称",255);required(card.company(),"维保机构",255);
        required(card.leader(),"负责人",100);required(card.phone(),"联系电话",255);
        required(card.no(),"维保卡编号",100);required(card.time(),"维保日期",200);
        String systems=Objects.toString(card.system1(),"");
        required(systems,"维护保养内容",4000);
        try(PDDocument doc=new PDDocument(); ByteArrayOutputStream out=new ByteArrayOutputStream(); InputStream fontBytes=resource("NotoSansSC-Regular.ttf"); InputStream imageBytes=resource("card.png")) {
            PDPage page=new PDPage(new PDRectangle(841.89f,HEIGHT));doc.addPage(page);
            PDType0Font font=PDType0Font.load(doc,fontBytes);
            try(PDPageContentStream stream=new PDPageContentStream(doc,page)) {
                stream.drawImage(PDImageXObject.createFromByteArray(doc,imageBytes.readAllBytes(),"card.png"),0,0,841.89f,HEIGHT);
                box(stream,font,card.name(),390,385,390,100,"项目名称");
                box(stream,font,card.company(),1385,385,380,100,"维保机构");
                box(stream,font,card.leader(),445,525,335,85,"负责人");
                flow(stream,font,phone(card.phone()),new float[][]{{1110,525,655,85,1}},"联系电话");
                // Each printed underline has its own box; wrap across these measured widths.
                flow(stream,font,systems,new float[][]{{505,650,1260,90},{130,775,1635,90}},"维护保养内容");
                box(stream,font,dates(card.time()),505,900,1260,90,"维保日期");
                box(stream,font,card.no(),1565,1195,205,45,"维保卡编号");
            }
            doc.getDocumentInformation().setTitle(card.name()+" - 消防维护保养公示卡");
            doc.getDocumentInformation().setCreator("ZJERP / Apache PDFBox");doc.save(out);return out.toByteArray();
        } catch(IllegalArgumentException ex) {throw invalid("内容包含字体不支持的字符，请替换后重试");}
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
        if(s==null||s.isBlank()||s.length()>max)throw invalid(label+"不能为空且不能超过"+max+"字");
        if(s.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw invalid(label+"包含无效控制字符");
    }
    static List<String> wrap(PDType0Font font,String text,float size,float width) throws IOException {
        List<String> result=new ArrayList<>();StringBuilder line=new StringBuilder();
        for(int cp:text.replace("\r","").replace('\t',' ').codePoints().toArray()) {
            if(cp=='\n'){result.add(line.toString());line.setLength(0);continue;}
            String next=new String(Character.toChars(cp));
            if(font.getStringWidth(line+next)*size/1000f>width && line.length()>0){result.add(line.toString());line.setLength(0);}
            if(font.getStringWidth(next)*size/1000f>width)throw invalid("文字超出可打印区域");
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
        throw invalid(label+"过长，缩小至最小字号后仍无法完整排版，请精简内容；没有生成截断文件");
    }
}
