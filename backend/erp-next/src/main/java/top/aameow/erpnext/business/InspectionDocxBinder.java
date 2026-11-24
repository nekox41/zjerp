package top.aameow.erpnext.business;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Node;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Explicit DOCX body bindings. Paths refer to actual paragraphs/cells, not grid columns.
 * Only plain text runs are eligible; existing pictures, fields, signatures and other
 * complex objects are not edited. The template is always opened as a new copy.
 */
@Component
public class InspectionDocxBinder {
    private static final Pattern TOKEN=Pattern.compile("\\{\\{erp\\.([A-Za-z][A-Za-z0-9]*(?:\\[[^\\]\\r\\n{}]+\\])?(?:\\.(?:[A-Za-z][A-Za-z0-9]*|[0-9]+))+)\\}\\}");
    private static final Pattern SCOPED=Pattern.compile("(records|facilities|summaries)\\[([^\\]\\r\\n{}]+)\\]\\.([0-9]+)\\.([A-Za-z][A-Za-z0-9]*)");
    private String resolveScoped(String key,JsonNode snapshot){
        var m=SCOPED.matcher(key);if(!m.matches())return key;
        int wanted;try{wanted=Integer.parseInt(m.group(3));}catch(NumberFormatException e){return key;}
        var array=snapshot.path(m.group(1));int pos=0;
        if(array.isArray())for(int i=0;i<array.size();i++){
            if(m.group(2).equals(array.get(i).path("system").asText())&&pos++==wanted)return m.group(1)+"."+i+"."+m.group(4);
        }
        return key;
    }
    private static final Set<String> SECTIONS=Set.of("general","buildings","facilities","records","instruments","summaries","conclusion");
    private static final Set<String> PROTECTED=Set.of("签字","签章","盖章","印章","批准人","审核人","检测人","报告人","项目负责人","技术负责人","骑缝章","营业执照","资格证");
    private static final int MAX_TARGETS=20000;
    public record Binding(String target,String expectedText,String field){}
    public record Candidate(String target,String text){}
    public record Field(String key,String value){}
    public record Change(String target,String before,String after,List<String> fields){}
    public record Inspection(List<Field> fields,List<Candidate> candidates,List<Change> changes,List<String> errors,List<String> unmappedFields,List<Binding> suggestedBindings){}
    private record Target(String key,XWPFParagraph paragraph,boolean protectedArea){}
    private record Edit(int start,int end,String value){}
    private record Planned(Target target,List<Edit> edits,Change change){}
    private record Plan(Inspection inspection,List<Planned> edits){}

    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
    private void walk(List<IBodyElement> elements,String prefix,List<Target> out,boolean protectedArea){
        int p=0,t=0;
        for(IBodyElement element:elements){
            if(element instanceof XWPFParagraph paragraph){
                out.add(new Target(prefix+"P"+(++p),paragraph,protectedArea));
                if(out.size()>MAX_TARGETS)throw bad("模板正文段落过多，暂不支持自动填充");
            }else if(element instanceof XWPFTable table){
                String key=prefix+"T"+(++t)+"/";
                int row=0;
                for(XWPFTableRow tr:table.getRows()){
                    row++;int cell=0;
                    boolean signatureRow=protectedArea||tr.getTableCells().stream().anyMatch(c->protectedText(c.getText()));
                    for(XWPFTableCell tc:tr.getTableCells())walk(tc.getBodyElements(),key+"R"+row+"/C"+(++cell)+"/",out,signatureRow);
                }
            }
        }
    }
    private boolean plain(XWPFParagraph p){
        Node n=p.getCTP().getDomNode();
        for(Node child=n.getFirstChild();child!=null;child=child.getNextSibling()){
            if(child.getNodeType()!=Node.ELEMENT_NODE)continue;
            String local=child.getLocalName();
            if("pPr".equals(local))continue;
            if(!"r".equals(local))return false;
            for(Node run=child.getFirstChild();run!=null;run=run.getNextSibling()){
                if(run.getNodeType()!=Node.ELEMENT_NODE)continue;
                if(!Set.of("rPr","t").contains(run.getLocalName()))return false;
            }
        }
        return p.getRuns().stream().allMatch(r->r.getCTR().sizeOfTArray()<=1);
    }
    private String text(XWPFParagraph p){
        StringBuilder sb=new StringBuilder();
        for(XWPFRun run:p.getRuns())if(run.getCTR().sizeOfTArray()==1)sb.append(run.getCTR().getTArray(0).getStringValue());
        return sb.toString();
    }
    private boolean protectedText(String text){
        String compact=text.replaceAll("\\s+","");
        return PROTECTED.stream().anyMatch(compact::contains);
    }
    private void flatten(String prefix,JsonNode node,Map<String,String> result){
        if(node==null)return;
        if(node.isTextual()){
            String value=node.asText();
            result.put(prefix,value);
        }else if(node.isArray()){
            for(int i=0;i<node.size();i++)flatten(prefix+"."+i,node.get(i),result);
        }else if(node.isObject()){
            node.fields().forEachRemaining(e->{
                if(e.getKey().matches("[A-Za-z][A-Za-z0-9]*"))flatten(prefix+"."+e.getKey(),e.getValue(),result);
            });
        }
    }
    private Map<String,String> fields(JsonNode snapshot,String reportNo,String title){
        Map<String,String> result=new TreeMap<>();
        if(snapshot==null||!snapshot.isObject())throw bad("请先绑定检测资料版本");
        for(String section:SECTIONS)flatten(section,snapshot.get(section),result);
        result.put("report.reportNo",reportNo);result.put("report.title",title);
        return result;
    }
    private String normalized(String label){return label.replaceAll("\\s+","");}
    private void suggest(List<Binding> result,Map<String,Candidate> candidates,Map<String,String> values,String target,String field){
        Candidate c=candidates.get(target);
        if(c!=null&&values.containsKey(field))result.add(new Binding(target,c.text(),field));
    }
    // The two reviewed samples share the same logical labels and eleven physical data cells,
    // despite having different underlying Word grid widths. Only suggest; never write implicitly.
    private List<Binding> suggestEngineering(XWPFDocument doc,List<Candidate> candidates,Map<String,String> values){
        Map<String,Candidate> byTarget=new HashMap<>();
        for(Candidate c:candidates)byTarget.put(c.target(),c);
        List<Binding> result=new ArrayList<>();
        Map<String,String> labels=Map.ofEntries(Map.entry("工程名称","projectName"),Map.entry("工程地址","address"),Map.entry("工程类别","projectCategory"),
            Map.entry("建设单位","constructionUnit"),Map.entry("设计单位","designUnit"),Map.entry("施工单位","contractor"),Map.entry("监理单位","supervisionUnit"),
            Map.entry("建筑总面积","totalArea"),Map.entry("检测日期","inspectionDates"),Map.entry("委托单位","commissioningUnit"),
            Map.entry("委托时间","commissionedOn"),Map.entry("委托/联系人","contact"),Map.entry("联系电话","phone"));
        Map<String,String> qualification=Map.of("设计单位","designQualification","施工单位","contractorQualification","监理单位","supervisionQualification");
        String[] buildingFields={"name","structure","use","fireResistance","aboveGroundFloors","belowGroundFloors","height","length","footprint","aboveGroundArea","belowGroundArea"};
        int tableNo=0;
        for(XWPFTable table:doc.getTables()){
            tableNo++;
            var rows=table.getRows();
            if(rows.size()<13||rows.get(0).getTableCells().size()!=2||rows.get(1).getTableCells().size()<2||!"工程名称".equals(normalized(rows.get(0).getCell(0).getText()))
                ||!"工程地址".equals(normalized(rows.get(1).getCell(0).getText())))continue;
            int buildingHeader=-1;
            for(int i=0;i<rows.size();i++){
                var cells=rows.get(i).getTableCells();
                if(cells.isEmpty())continue;
                String first=normalized(cells.get(0).getText());
                if("单体建筑名称".equals(first)){buildingHeader=i;break;}
                if("单体建筑工程信息".equals(first))continue;
                for(int j=0;j+1<cells.size();j++){
                    String label=normalized(cells.get(j).getText()),field=labels.get(label);
                    if("资质等级".equals(label))field=qualification.get(first);
                    if(field!=null&&cells.get(j+1).getParagraphs().size()==1)
                        suggest(result,byTarget,values,"T"+tableNo+"/R"+(i+1)+"/C"+(j+2)+"/P1","general."+field);
                }
            }
            if(buildingHeader<0||buildingHeader+2>=rows.size())continue;
            var header=rows.get(buildingHeader).getTableCells();
            var sub=rows.get(buildingHeader+1).getTableCells();
            if(header.size()!=9||sub.size()!=11||!"结构类型".equals(normalized(header.get(1).getText()))
                ||!"使用性质".equals(normalized(header.get(2).getText()))||!"耐火等级".equals(normalized(header.get(3).getText()))
                ||!"地上".equals(normalized(sub.get(4).getText()))||!"地下".equals(normalized(sub.get(5).getText()))
                ||!"地上".equals(normalized(sub.get(9).getText()))||!"地下".equals(normalized(sub.get(10).getText())))continue;
            for(int i=buildingHeader+2;i<rows.size();i++){
                var cells=rows.get(i).getTableCells();if(cells.size()!=11)break;
                int idx=i-buildingHeader-2;
                for(int j=0;j<buildingFields.length;j++)if(cells.get(j).getParagraphs().size()==1)
                    suggest(result,byTarget,values,"T"+tableNo+"/R"+(i+1)+"/C"+(j+1)+"/P1","buildings."+idx+"."+buildingFields[j]);
            }
        }
        return result;
    }
    private Plan plan(XWPFDocument doc,JsonNode snapshot,String reportNo,String title,List<Binding> bindings){
        Map<String,String> values=fields(snapshot,reportNo,title);
        List<Target> targets=new ArrayList<>();
        walk(doc.getBodyElements(),"",targets,false);
        List<String> errors=new ArrayList<>();
        // Actual part ordering is stable for a particular template SHA256.
        int part=0;
        for(var h:doc.getHeaderList())walk(h.getBodyElements(),"H"+(++part)+"/",targets,false);
        part=0;
        for(var f:doc.getFooterList())walk(f.getBodyElements(),"F"+(++part)+"/",targets,false);
        Map<String,Binding> manual=new LinkedHashMap<>();
        if(bindings!=null){
            if(bindings.size()>MAX_TARGETS)throw bad("手动绑定过多");
            for(Binding b:bindings){
                if(b==null||b.target()==null||b.field()==null||b.expectedText()==null||manual.putIfAbsent(b.target(),b)!=null)
                    errors.add("存在无效或重复的手动绑定");
            }
        }
        List<Candidate> candidates=new ArrayList<>();
        List<Planned> planned=new ArrayList<>();
        Set<String> used=new HashSet<>();
        for(Target target:targets){
            XWPFParagraph p=target.paragraph();
            String visible=p.getText();
            Binding binding=manual.remove(target.key());
            boolean token=visible.contains("{{erp.");
            boolean eligible=!target.protectedArea()&&plain(p)&&!protectedText(visible)&&visible.length()<=12000;
            if(!eligible){
                if(token||binding!=null)errors.add(target.key()+"：含复杂对象、签署相关文字或不支持的内容");
                continue;
            }
            String before=text(p);
            if((!before.isBlank()||target.key().contains("/C"))&&!token)candidates.add(new Candidate(target.key(),before));
            List<Edit> edits=new ArrayList<>();
            List<String> keys=new ArrayList<>();
            if(binding!=null){
                if(token){errors.add(target.key()+"：不能同时使用标记与手动绑定");continue;}
                if(!before.equals(binding.expectedText())||(before.isBlank()&&!target.key().contains("/C"))){errors.add(target.key()+"：模板原文不匹配");continue;}
                String value=values.get(binding.field());
                if(value==null){errors.add(target.key()+"：资料字段不存在或为空："+binding.field());continue;}
                edits.add(new Edit(0,before.length(),value));keys.add(binding.field());
            }else if(token){
                var matcher=TOKEN.matcher(before);
                while(matcher.find()){
                    String key=resolveScoped(matcher.group(1),snapshot),value=values.get(key);
                    if(value==null){errors.add(target.key()+"：资料字段不存在或为空："+key);continue;}
                    edits.add(new Edit(matcher.start(),matcher.end(),value));keys.add(key);
                }
                if(TOKEN.matcher(before).replaceAll("").contains("{{erp."))errors.add(target.key()+"：ERP 标记格式错误或不完整");
            }
            if(edits.isEmpty())continue;
            StringBuilder after=new StringBuilder(before);
            for(int i=edits.size()-1;i>=0;i--){Edit e=edits.get(i);after.replace(e.start(),e.end(),e.value());}
            // Normal line breaks and tabs are rendered as Word runs, never raw control characters.
            if(edits.stream().anyMatch(e->e.value().contains("{{erp.")||e.value().codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))){
                errors.add(target.key()+"：字段含不支持的控制字符或嵌套 ERP 标记");continue;
            }
            used.addAll(keys);
            planned.add(new Planned(target,edits,new Change(target.key(),before,after.toString(),keys)));
        }
        manual.keySet().forEach(key->errors.add(key+"：未找到对应正文段落"));
        if(planned.isEmpty())errors.add("没有可执行的正文绑定，请在模板中设置 ERP 标记或手动选择正文段落");
        return new Plan(new Inspection(values.entrySet().stream().map(e->new Field(e.getKey(),e.getValue())).toList(),candidates,
            planned.stream().map(Planned::change).toList(),errors,values.keySet().stream().filter(k->!used.contains(k)).toList(),
            suggestEngineering(doc,candidates,values)),planned);
    }
    public Inspection inspect(XWPFDocument doc,JsonNode snapshot,String reportNo,String title,List<Binding> bindings){
        return plan(doc,snapshot,reportNo,title,bindings).inspection();
    }
    public Inspection fill(XWPFDocument doc,JsonNode snapshot,String reportNo,String title,List<Binding> bindings){
        Plan plan=plan(doc,snapshot,reportNo,title,bindings);
        if(!plan.inspection().errors().isEmpty())throw bad(String.join("；",plan.inspection().errors().stream().limit(8).toList()));
        for(Planned p:plan.edits()){
            if(p.edits().size()==1&&p.edits().get(0).start()==0&&p.edits().get(0).end()==0){
                XWPFParagraph paragraph=p.target().paragraph();
                XWPFRun run=paragraph.getRuns().isEmpty()?paragraph.createRun():paragraph.getRuns().get(0);
                writeText(run,p.edits().get(0).value());continue;
            }
            List<XWPFRun> runs=p.target().paragraph().getRuns();
            List<String> chunks=new ArrayList<>();
            for(XWPFRun run:runs)chunks.add(run.getCTR().sizeOfTArray()==0?"":run.getCTR().getTArray(0).getStringValue());
            // Apply ranges in reverse order, so the offsets of earlier ranges remain valid.
            for(int i=p.edits().size()-1;i>=0;i--){
                Edit edit=p.edits().get(i);int offset=0;boolean inserted=false;
                for(int j=0;j<chunks.size();j++){
                    String chunk=chunks.get(j);int next=offset+chunk.length();
                    if(edit.start()<next&&edit.end()>offset){
                        int from=Math.max(0,edit.start()-offset),to=Math.min(chunk.length(),edit.end()-offset);
                        chunks.set(j,chunk.substring(0,from)+(inserted?"":edit.value())+chunk.substring(to));
                        inserted=true;
                    }
                    offset=next;
                }
            }
            for(int j=0;j<runs.size();j++)if(runs.get(j).getCTR().sizeOfTArray()==1){
                if(!Objects.equals(runs.get(j).getCTR().getTArray(0).getStringValue(),chunks.get(j)))writeText(runs.get(j),chunks.get(j));
            }
        }
        return plan.inspection();
    }
    private void writeText(XWPFRun run,String value){
        String content=value.replace("\r\n","\n").replace('\r','\n');
        String[] parts=content.split("(?=[\\n\\t])|(?<=[\\n\\t])",-1);
        if(run.getCTR().sizeOfTArray()==1)run.setText("",0);
        for(String part:parts){
            if("\n".equals(part))run.addBreak();
            else if("\t".equals(part))run.addTab();
            else if(!part.isEmpty())run.setText(part);
        }
    }
}
