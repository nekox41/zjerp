package top.aameow.erpnext.business;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Compose native Word tables into a clean base. Do not copy relationship-bearing parts or guess style mappings. */
final class InspectionTemplateComposer {
    static final String MARKER="{{erp.systemSections}}";
    private static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final long MAX_EXPANDED=160L*1024*1024;
    record Section(String system,byte[] docx){}
    record Info(int tables,int rows){}
    private static ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,text);}
    private static Document parse(byte[] bytes)throws Exception{
        var f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        f.setXIncludeAware(false);f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    private static Map<String,byte[]> parts(byte[] zip)throws IOException{
        Map<String,byte[]> selected=new HashMap<>();Set<String> names=new HashSet<>();long total=0;int count=0;
        try(var in=new ZipInputStream(new ByteArrayInputStream(zip))){
            for(ZipEntry e;(e=in.getNextEntry())!=null;){
                if(++count>4096||!names.add(e.getName()))throw bad("Word 文件包含过多或重复部件");
                boolean keep=Set.of("word/document.xml","word/styles.xml").contains(e.getName());
                var out=keep?new ByteArrayOutputStream():null;byte[] buf=new byte[8192];long part=0;
                for(int n;(n=in.read(buf))!=-1;){total+=n;part+=n;
                    if(total>MAX_EXPANDED||(keep&&part>24L*1024*1024))throw bad("Word 模板解压体积过大");
                    if(keep)out.write(buf,0,n);
                }
                if(keep)selected.put(e.getName(),out.toByteArray());
            }
        }
        if(!selected.containsKey("word/document.xml"))throw bad("缺少 Word 正文");
        return selected;
    }
    private static Element body(Document d){
        var nodes=d.getElementsByTagNameNS(W,"body");
        if(nodes.getLength()!=1)throw bad("Word 正文结构异常");return (Element)nodes.item(0);
    }
    private static String text(Element e){
        var nodes=e.getElementsByTagNameNS(W,"t");var b=new StringBuilder();
        for(int i=0;i<nodes.getLength();i++)b.append(nodes.item(i).getTextContent());
        return b.toString().trim();
    }
    private static List<Element> children(Element e){
        List<Element> list=new ArrayList<>();for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element x)list.add(x);return list;
    }
    private static Element marker(Element b){
        Element found=null;
        for(var e:children(b)){
            if(W.equals(e.getNamespaceURI())&&"p".equals(e.getLocalName())&&MARKER.equals(text(e))){
                if(found!=null)throw bad("基础模板只能有一个系统章节插入标记");guard(e,Set.of(),false);found=e;
            }else if(text(e).contains(MARKER)||"现场检测照片".equals(text(e)))throw bad("基础模板不能包含其他系统插入标记或原现场照片章节");
        }
        if(found==null)throw bad("基础模板须在独立普通段落中包含 "+MARKER);
        return found;
    }
    private static void guard(Node n,Set<String> styleIds,boolean checkStyles){
        if(n instanceof Element e){
            String local=e.getLocalName();
            if(Set.of("drawing","pict","object","altChunk","sdt","fldSimple","fldChar","instrText","hyperlink","bookmarkStart","bookmarkEnd","commentRangeStart","commentRangeEnd","commentReference","footnoteReference","endnoteReference","numPr","ins","del","moveFrom","moveTo","sectPr","headerReference","footerReference","subDoc","permStart","permEnd").contains(local))
                throw bad("系统模板存在图片、链接、编号、域、批注或其他跨部件对象："+local+"；请使用纯文字和原生表格");
            var attrs=e.getAttributes();
            for(int i=0;i<attrs.getLength();i++)if(R.equals(attrs.item(i).getNamespaceURI()))throw bad("系统模板不能引用其他 Word 部件");
            if(checkStyles&&W.equals(e.getNamespaceURI())&&Set.of("pStyle","rStyle","tblStyle").contains(local)){
                String id=e.getAttributeNS(W,"val");
                if(!styleIds.contains(id))throw bad("基础模板缺少章节所用样式："+id+"；请从同一基础模板制作章节或使用直接格式");
            }
        }
        for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling())guard(c,styleIds,checkStyles);
    }
    private static Info validateSystem(Element b,Set<String> styles,boolean check){
        int tables=0,rows=0;
        for(var e:children(b)){
            if(!W.equals(e.getNamespaceURI()))throw bad("不支持的章节对象");
            String tag=e.getLocalName();
            if("sectPr".equals(tag))continue;
            if(!Set.of("p","tbl").contains(tag))throw bad("章节只支持普通段落和原生表格");
            if("现场检测照片".equals(text(e))||text(e).contains(MARKER))throw bad("照片区域由系统生成，章节模板不得自带照片标题或插入标记");
            guard(e,styles,check);
            if("tbl".equals(tag)){tables++;rows+=e.getElementsByTagNameNS(W,"tr").getLength();}
        }
        if(tables==0||rows>2000)throw bad("系统模板至少需要一个检测表格且最多2000行");
        return new Info(tables,rows);
    }
    static Info inspect(byte[] docx,boolean base)throws Exception{
        Element b=body(parse(parts(docx).get("word/document.xml")));
        if(base){marker(b);return new Info(b.getElementsByTagNameNS(W,"tbl").getLength(),b.getElementsByTagNameNS(W,"tr").getLength());}
        return validateSystem(b,Set.of(),false);
    }
    private static Element w(Document d,String name){return d.createElementNS(W,"w:"+name);}
    private static void attr(Element e,String key,String value){e.setAttributeNS(W,"w:"+key,value);}
    private static Element p(Document d,String value,boolean bold,boolean breakBefore){
        Element p=w(d,"p");
        if(breakBefore||bold){Element pr=w(d,"pPr");p.appendChild(pr);if(breakBefore)pr.appendChild(w(d,"pageBreakBefore"));
            if(bold){Element jc=w(d,"jc");attr(jc,"val","center");pr.appendChild(jc);}}
        Element r=w(d,"r");p.appendChild(r);
        if(bold){Element pr=w(d,"rPr");r.appendChild(pr);pr.appendChild(w(d,"b"));Element sz=w(d,"sz");attr(sz,"val","28");pr.appendChild(sz);}
        Element t=w(d,"t");t.setTextContent(value);r.appendChild(t);return p;
    }
    private static Element grid(Document d){
        Element table=w(d,"tbl"),pr=w(d,"tblPr");table.appendChild(pr);
        Element width=w(d,"tblW");attr(width,"type","dxa");attr(width,"w","9840");pr.appendChild(width);
        Element layout=w(d,"tblLayout");attr(layout,"type","fixed");pr.appendChild(layout);
        Element grid=w(d,"tblGrid");table.appendChild(grid);
        for(int i=0;i<2;i++){Element col=w(d,"gridCol");attr(col,"w","4920");grid.appendChild(col);}
        for(int i=0;i<2;i++){Element row=w(d,"tr");table.appendChild(row);
            for(int j=0;j<2;j++){Element cell=w(d,"tc"),cp=w(d,"tcPr"),cw=w(d,"tcW");row.appendChild(cell);cell.appendChild(cp);cp.appendChild(cw);attr(cw,"w","4920");attr(cw,"type","dxa");cell.appendChild(p(d,"",false,false));}}
        return table;
    }
    static byte[] compose(byte[] base,List<Section> sections)throws Exception{
        if(sections.isEmpty()||sections.size()>40)throw bad("请选择1至40个消防系统");
        var baseParts=parts(base);Document d=parse(baseParts.get("word/document.xml"));Element b=body(d),at=marker(b);
        Set<String> styles=new HashSet<>();styles.add("Normal");styles.add("TableNormal");
        if(baseParts.containsKey("word/styles.xml")){
            var nodes=parse(baseParts.get("word/styles.xml")).getElementsByTagNameNS(W,"style");
            for(int i=0;i<nodes.getLength();i++)styles.add(((Element)nodes.item(i)).getAttributeNS(W,"styleId"));
        }
        Set<String> systems=new HashSet<>();int totalRows=0;
        Set<String> selectedNames=new HashSet<>();for(Section section:sections)selectedNames.add(section.system());
        if(selectedNames.size()!=sections.size())throw bad("同一消防系统只能选择一个章节模板");
        for(Section section:sections){
            if(!systems.add(section.system()))throw bad("同一消防系统只能选择一个章节模板");
            var fragment=body(parse(parts(section.docx()).get("word/document.xml")));
            totalRows+=validateSystem(fragment,styles,true).rows();if(totalRows>10000)throw bad("报告检测表合计超过10000行");
            b.insertBefore(p(d,section.system(),true,true),at);
            for(var e:children(fragment))if(!"sectPr".equals(e.getLocalName())){
                if("p".equals(e.getLocalName())&&selectedNames.contains(text(e)))throw bad("系统章节模板请移除独立系统标题，由组合器统一生成");
                b.insertBefore(d.importNode(e,true),at);
            }
            b.insertBefore(p(d,"现场检测照片",true,true),at);
            b.insertBefore(grid(d),at);
            b.insertBefore(p(d,"注：应附彩色照片并注明日期，日期应如实填写。",false,false),at);
        }
        b.removeChild(at);
        var xml=new ByteArrayOutputStream();TransformerFactory.newInstance().newTransformer().transform(new DOMSource(d),new StreamResult(xml));
        var out=new ByteArrayOutputStream();
        try(var in=new ZipInputStream(new ByteArrayInputStream(base));var zip=new ZipOutputStream(out)){
            for(ZipEntry e;(e=in.getNextEntry())!=null;){
                zip.putNextEntry(new ZipEntry(e.getName()));
                if("word/document.xml".equals(e.getName()))zip.write(xml.toByteArray());else in.transferTo(zip);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
