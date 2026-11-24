package top.aameow.erpnext.business;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Node;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Expands an explicitly prepared prototype row; never guesses where to clone merged records. */
final class InspectionDocxRepeatRows {
    private static final Pattern REPEAT=Pattern.compile("\\{\\{erp\\.(buildings|facilities|records|instruments|summaries)(?:\\[([^\\]\\r\\n]+)\\])?\\.\\*\\.([A-Za-z][A-Za-z0-9]*)\\}\\}");
    private record Selector(String section,String system){}
    private record RowPlan(int table,int row,Selector selector,List<Integer> indices){}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
    private static boolean hasComplex(Node n){
        if(n.getNodeType()==Node.ELEMENT_NODE){
            String local=n.getLocalName();
            if(Set.of("vMerge","hMerge","drawing","pict","object","sdt","fldSimple","fldChar","instrText","hyperlink","bookmarkStart","bookmarkEnd","commentRangeStart","commentRangeEnd","del","ins","tblHeader","footnoteReference","endnoteReference","permStart","permEnd").contains(local))return true;
        }
        for(Node child=n.getFirstChild();child!=null;child=child.getNextSibling())if(hasComplex(child))return true;
        return false;
    }
    private static List<XWPFParagraph> paragraphs(XWPFTableRow row){
        List<XWPFParagraph> result=new ArrayList<>();
        for(XWPFTableCell cell:row.getTableCells()){
            if(!cell.getTables().isEmpty())throw bad("重复行不支持嵌套表格");
            result.addAll(cell.getParagraphs());
        }
        return result;
    }
    private static String rowText(XWPFTableRow row){
        return row.getTableCells().stream().map(XWPFTableCell::getText).reduce("",(a,b)->a+"\n"+b);
    }
    private static Selector selector(XWPFTableRow row){
        String text=rowText(row);
        var m=REPEAT.matcher(text);
        Selector selector=null;
        while(m.find()){
            Selector found=new Selector(m.group(1),m.group(2));
            if(selector!=null&&!selector.equals(found))throw bad("一个重复行只能使用同一资料列表和系统筛选");
            selector=found;
        }
        if(selector==null)return null;
        if(hasComplex(row.getCtRow().getDomNode())||text.matches("(?s).*(签字|签章|盖章|印章|审核人|批准人|报告人|检测人|项目负责人|技术负责人).*"))
            throw bad("重复行含合并、签署或复杂对象，必须使用独立的纯文本模板行");
        // Disallow vertical merges in the entire table: cloning a row may otherwise break a neighbouring merge chain.
        for(var other:row.getTable().getRows())if(hasVerticalMerge(other.getCtRow().getDomNode()))
            throw bad("重复行所在表格含纵向合并；请使用独立明细表或人工绑定");
        if(selector.system()!=null&&!Set.of("records","facilities","summaries").contains(selector.section()))
            throw bad("仅系统相关资料列表支持系统筛选");
        return selector;
    }
    private static boolean hasVerticalMerge(Node n){
        if(n.getNodeType()==Node.ELEMENT_NODE&&"vMerge".equals(n.getLocalName()))return true;
        for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling())if(hasVerticalMerge(c))return true;
        return false;
    }
    private static List<Integer> indices(JsonNode data,Selector selector){
        var list=data.path(selector.section());
        List<Integer> result=new ArrayList<>();
        if(list.isArray())for(int i=0;i<list.size();i++){
            if(selector.system()==null||selector.system().equals(list.get(i).path("system").asText()))result.add(i);
        }
        if(result.isEmpty())throw bad("重复行没有对应资料："+selector.section()+(selector.system()==null?"":" / "+selector.system()));
        return result;
    }
    private static void replaceMarkers(XWPFTableRow row,Selector selector,int index){
        for(XWPFParagraph p:paragraphs(row)){
            String text=p.getText();
            var m=REPEAT.matcher(text);
            List<int[]> spans=new ArrayList<>();
            List<String> replacements=new ArrayList<>();
            while(m.find()){
                if(!selector.equals(new Selector(m.group(1),m.group(2))))throw bad("重复行资料选择不一致");
                spans.add(new int[]{m.start(),m.end()});
                replacements.add("{{erp."+selector.section()+"."+index+"."+m.group(3)+"}}");
            }
            if(spans.isEmpty())continue;
            var runs=p.getRuns();
            List<String> chunks=new ArrayList<>();
            StringBuilder actual=new StringBuilder();
            for(var run:runs){
                if(run.getCTR().sizeOfTArray()>1)throw bad("重复标记含多个复杂文字节点");
                String value=run.getCTR().sizeOfTArray()==1?run.getCTR().getTArray(0).getStringValue():"";
                chunks.add(value);actual.append(value);
            }
            if(!text.contentEquals(actual))throw bad("重复标记必须位于普通文字段落");
            for(int i=spans.size()-1;i>=0;i--){
                int[] span=spans.get(i);int offset=0;boolean inserted=false;
                for(int j=0;j<chunks.size();j++){
                    String chunk=chunks.get(j);int end=offset+chunk.length();
                    if(span[0]<end&&span[1]>offset){
                        int from=Math.max(0,span[0]-offset),to=Math.min(chunk.length(),span[1]-offset);
                        chunks.set(j,chunk.substring(0,from)+(inserted?"":replacements.get(i))+chunk.substring(to));
                        inserted=true;
                    }
                    offset=end;
                }
            }
            for(int j=0;j<runs.size();j++)if(runs.get(j).getCTR().sizeOfTArray()==1)runs.get(j).setText(chunks.get(j),0);
        }
    }
    /** Returns original bytes when there are no repeat prototypes. All expanded rows are preflighted again by the binder. */
    static byte[] prepare(byte[] original,JsonNode data)throws IOException{
        try(var doc=new XWPFDocument(new ByteArrayInputStream(original))){
            var tables=doc.getTables();List<RowPlan> plans=new ArrayList<>();int total=0;
            for(int ti=0;ti<tables.size();ti++){
                var rows=tables.get(ti).getRows();
                for(int ri=0;ri<rows.size();ri++){
                    var select=selector(rows.get(ri));
                    if(select==null)continue;
                    var list=indices(data,select);total+=list.size();
                    if(total>3000)throw bad("重复生成行数超过 3000");
                    plans.add(new RowPlan(ti,ri,select,list));
                }
            }
            if(plans.isEmpty())return original;
            Collections.reverse(plans);
            for(RowPlan plan:plans){
                var table=tables.get(plan.table());var prototype=table.getRow(plan.row());
                CTRow originalRow=(CTRow)prototype.getCtRow().copy();
                // Prepare copies before changing the table; the original document is never written back.
                List<XWPFTableRow> copies=new ArrayList<>();
                for(int index:plan.indices()){
                    var copy=new XWPFTableRow((CTRow)originalRow.copy(),table);
                    replaceMarkers(copy,plan.selector(),index);copies.add(copy);
                }
                table.removeRow(plan.row());
                for(int i=0;i<copies.size();i++)if(!table.addRow(copies.get(i),plan.row()+i))throw bad("重复行插入失败");
            }
            try(var out=new ByteArrayOutputStream()){doc.write(out);return out.toByteArray();}
        }
    }
}
