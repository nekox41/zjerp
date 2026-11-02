package top.aameow.generator.service;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import top.aameow.common.config.RuoYiConfig;
import top.aameow.common.exception.ServiceException;
import top.aameow.common.utils.SecurityUtils;
import top.aameow.management.service.*;
import top.aameow.system.service.ISysFilePlusService;
import top.aameow.system.domain.SysFilePlus;
import top.aameow.generator.mapper.InspectionReportMapper;
@Service
public class InspectionReportService {
 @Autowired private WorkProjectService projects;
 @Autowired private ReportCredentialService credentials;
 @Autowired private ISysFilePlusService files;
 @Autowired private InspectionReportMapper mapper;
 private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 public List<Map<String,Object>> list(Long id){projects.get(id);return mapper.list(id);}
 static String text(Map<String,Object> v,String key){return Objects.toString(v.get(key),"").trim();}
 @SuppressWarnings("unchecked") static List<Map<String,Object>> rows(Map<String,Object> v,String key){Object o=v.get(key);if(!(o instanceof List<?> l)||l.size()>300)throw new ServiceException(key+"格式无效或数量过多");for(Object r:l)if(!(r instanceof Map))throw new ServiceException(key+"格式无效");return (List<Map<String,Object>>)o;}
 Path source(String url) throws Exception {
  SysFilePlus f=files.getByUrl(url);if(f==null||!"0".equals(f.getDelFlag()))throw new ServiceException("附件未登记或已删除，请重新上传");
  Path base=Paths.get(RuoYiConfig.getProfile()).toRealPath(),p=base.resolve(f.getFilePath()).normalize().toRealPath();
  if(!p.startsWith(base)||Files.size(p)>30L*1024*1024)throw new ServiceException("附件路径无效或超过30MB");return p;
 }
 Map<String,Object> prepare(Map<String,Object> input) throws Exception {
  if(json.writeValueAsBytes(input).length>500000)throw new ServiceException("报告内容过多");
  Map<String,Object> v=new LinkedHashMap<>(input);Long id=WorkProjectService.number(v.get("projectId"));Map<String,Object> p=projects.get(id);
  if(!"JC".equals(p.get("projectType")))throw new ServiceException("请选择检测项目");v.put("project",p);
  if(text(v,"reportNo").isEmpty()||text(v,"reportNo").length()>100)throw new ServiceException("请填写报告编号（最多100字）");
  try{LocalDate.parse(text(v,"reportDate"));}catch(Exception e){throw new ServiceException("请填写有效报告日期");}
  Object selected=v.get("credentialIds");if(!(selected instanceof List<?> ids)||ids.size()>50)throw new ServiceException("请选择资质");
  if(new HashSet<>(ids).size()!=ids.size())throw new ServiceException("资质不能重复选择");long totalBytes=0;List<Map<String,Object>> certs=new ArrayList<>();for(Object key:ids){Map<String,Object> c=credentials.list().stream().filter(r->r.get("id").toString().equals(key.toString())).findFirst().orElseThrow(()->new ServiceException("资质已不存在"));String until=text(c,"validUntil");if(!until.isEmpty()&&until.compareTo(text(v,"reportDate"))<0)throw new ServiceException("资质在报告日期已过期："+c.get("title"));for(String url:text(c,"files").split(",")){Path file=source(url);if(!file.toString().toLowerCase(Locale.ROOT).matches(".*\\.(pdf|png|jpe?g)$"))throw new ServiceException("资质仅支持PDF、PNG或JPEG");totalBytes+=Files.size(file);}certs.add(c);}v.put("credentials",certs);
  List<Map<String,Object>> systems=rows(v,"systems");if(systems.isEmpty()||systems.size()>30)throw new ServiceException("请登记检测系统");Set<String> names=new HashSet<>();
  for(Map<String,Object> s:systems){if(text(s,"name").isEmpty()||text(s,"name").length()>100||!names.add(text(s,"name")))throw new ServiceException("系统名称为空、重复或过长");if(!Arrays.asList("tested","absent","untested").contains(text(s,"status")))throw new ServiceException("请确认各系统状态");if(text(s,"description").length()>10000)throw new ServiceException("系统说明过长");for(Map<String,Object> r:rows(s,"items")){if(text(r,"name").isEmpty())throw new ServiceException("请填写设备名称");for(String key:List.of("name","requirement","location","result"))if(text(r,key).length()>2000)throw new ServiceException("检测字段过长");if(!Arrays.asList("tested","absent","untested").contains(text(r,"status")))throw new ServiceException("请确认设备状态");if(!Arrays.asList("pending","pass","fail","na").contains(text(r,"verdict")))throw new ServiceException("检测判定无效");if(!"tested".equals(r.get("status"))&&Arrays.asList("pass","fail").contains(text(r,"verdict")))throw new ServiceException("未检测或不存在的设备不能判定合格/不合格");}}
  for(Map<String,Object> photo:rows(v,"photos")){if(!names.contains(text(photo,"system")))throw new ServiceException("请为所有照片指定系统");Map<String,Object> s=systems.stream().filter(r->text(r,"name").equals(text(photo,"system"))).findFirst().orElseThrow();if(!"tested".equals(s.get("status")))throw new ServiceException("照片只能关联已检测系统");try{LocalDate.parse(text(photo,"date"));}catch(Exception e){throw new ServiceException("请填写照片拍摄日期");}if(text(photo,"caption").length()>80)throw new ServiceException("照片说明不能超过80字");Path image=source(text(photo,"url"));totalBytes+=Files.size(image);if(!image.toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g)$"))throw new ServiceException("照片仅支持PNG或JPEG");}
  if(totalBytes>150L*1024*1024)throw new ServiceException("本次报告附件总量超过150MB，请压缩图片后重试");return v;
 }
 public byte[] preview(Map<String,Object> input) throws Exception{return render(prepare(input));}
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> create(Map<String,Object> input) throws Exception {
  Map<String,Object> v=prepare(input);byte[] pdf=render(v);Map<String,Object> record=new HashMap<>();record.put("projectId",v.get("projectId"));record.put("reportNo",v.get("reportNo"));record.put("snapshot",json.writeValueAsString(v));record.put("actor",SecurityUtils.getUsername());mapper.add(record);
  Path base=Paths.get(RuoYiConfig.getProfile()),directory=base.resolve("inspection-reports/"+LocalDate.now(ZoneId.of("Asia/Shanghai")));Files.createDirectories(directory);Path path=directory.resolve(UUID.randomUUID()+".pdf");
  try{Files.write(path,pdf,StandardOpenOption.CREATE_NEW);String url="/profile/"+base.relativize(path).toString().replace('\\','/');SysFilePlus registered=files.registerLegacy(url,"inspection_report",WorkProjectService.number(record.get("id")));record.put("file",registered.getFileUrl());mapper.file(record);return Map.of("id",record.get("id"),"file",record.get("file"));}finally{Files.deleteIfExists(path);}
 }
 static class Pages implements AutoCloseable {
  final PDDocument doc;final PDType0Font font;PDPage page;PDPageContentStream stream;float y;final String number;
  Pages(PDDocument d,String n)throws Exception{doc=d;number=n;font=PDType0Font.load(d,new File(System.getProperty("zjerp.report.font","./wbcard/font/NotoSansSC-Regular.ttf")));}
  void page(String title)throws Exception{if(stream!=null)stream.close();page=new PDPage(PDRectangle.A4);doc.addPage(page);stream=new PDPageContentStream(doc,page);y=800;write("消防设施检测报告   "+number,9,40,y);y-=35;write(title,17,40,y);y-=32;}
  void write(String s,float size,float x,float yy)throws Exception{stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,yy);stream.showText(s);stream.endText();}
  void para(String value)throws Exception{for(String line:wrap(font,value,11,510)){if(y<55)page("检测报告（续页）");write(line,11,42,y);y-=18;}y-=8;}
  void table(List<String> values,float[] widths)throws Exception{
   List<List<String>> cells=new ArrayList<>();int lines=1;for(int i=0;i<values.size();i++){List<String> ls=wrap(font,values.get(i),9,widths[i]-10);cells.add(ls);lines=Math.max(lines,ls.size());}
   int offset=0;while(offset<lines){if(y<85)page("检测记录（续页）");int take=Math.min(lines-offset,(int)((y-55)/13)-1);float h=take*13+12,x=40;for(int i=0;i<widths.length;i++){stream.addRect(x,y-h,widths[i],h);stream.stroke();for(int j=0;j<take&&offset+j<cells.get(i).size();j++)write(cells.get(i).get(offset+j),9,x+5,y-14-j*13);x+=widths[i];}y-=h;offset+=take;}y-=3;
  }
  void image(Path p,float x,float bottom,float w,float h)throws Exception{PDImageXObject image=PDImageXObject.createFromFile(p.toString(),doc);float scale=Math.min(w/image.getWidth(),h/image.getHeight());float iw=image.getWidth()*scale,ih=image.getHeight()*scale;stream.drawImage(image,x+(w-iw)/2,bottom+(h-ih)/2,iw,ih);}
  public void close()throws IOException{if(stream!=null){stream.close();stream=null;}}
 }
 static List<String> wrap(PDType0Font font,String value,float size,float width)throws Exception{List<String> lines=new ArrayList<>();StringBuilder line=new StringBuilder();for(int cp:value.replace("\r","").codePoints().toArray()){if(cp=='\n'){lines.add(line.toString());line.setLength(0);continue;}String next=new String(Character.toChars(cp=='\t'?' ':cp));if(Character.isISOControl(cp))throw new ServiceException("内容包含无效控制字符");if(font.getStringWidth(line+next)*size/1000>width&&line.length()>0){lines.add(line.toString());line.setLength(0);}line.append(next);}if(line.length()>0)lines.add(line.toString());return lines;}
 @SuppressWarnings("unchecked") byte[] render(Map<String,Object> v)throws Exception{
  try(PDDocument doc=new PDDocument();ByteArrayOutputStream out=new ByteArrayOutputStream();Pages p=new Pages(doc,text(v,"reportNo"))){
   Map<String,Object> project=(Map<String,Object>)v.get("project");p.page("建设工程消防设施检测报告");p.para("工程名称："+project.get("projectName"));p.para("委托单位："+Objects.toString(project.get("clientName"),""));p.para("报告编号："+v.get("reportNo"));p.para("报告日期："+v.get("reportDate"));p.para("技术服务机构：河南众基消防技术有限公司");p.para("检测人："+text(v,"inspectors"));p.para("项目负责人："+Objects.toString(project.get("leaderName"),""));p.para("技术负责人："+Objects.toString(project.get("technicalName"),""));p.para("审核人："+text(v,"reviewer")+"    批准人："+text(v,"approver"));
   p.page("编制说明");p.para("检测报告应经有关人员核对、签字并加盖相应印章。检测内容、部位、结果及照片日期应根据实际情况如实填写。未检测、未判定项目不代表合格。");p.para(text(v,"notes"));
   for(Map<String,Object> c:rows(v,"credentials")){for(String url:text(c,"files").split(",")){Path path=source(url);if(path.toString().toLowerCase(Locale.ROOT).endsWith(".pdf")){p.close();try(PDDocument cert=Loader.loadPDF(path.toFile())){if(cert.isEncrypted()||cert.getNumberOfPages()>100)throw new ServiceException("证书PDF应为未加密且不超过100页");if(doc.getNumberOfPages()+cert.getNumberOfPages()>600)throw new ServiceException("报告超过600页，请拆分");for(int ci=0;ci<cert.getNumberOfPages();ci++){p.page(text(c,"title")+" "+text(c,"holder"));var form=new org.apache.pdfbox.multipdf.LayerUtility(doc).importPageAsForm(cert,ci);var box=form.getBBox();float scale=Math.min(515/box.getWidth(),650/box.getHeight());p.stream.saveGraphicsState();p.stream.transform(org.apache.pdfbox.util.Matrix.getTranslateInstance(40+(515-box.getWidth()*scale)/2,70+(650-box.getHeight()*scale)/2));p.stream.transform(org.apache.pdfbox.util.Matrix.getScaleInstance(scale,scale));p.stream.drawForm(form);p.stream.restoreGraphicsState();}}}else{p.page(text(c,"title")+" "+text(c,"holder"));p.image(path,40,70,515,650);}}}
   p.page("建筑概况");p.para("工程名称："+project.get("projectName"));p.para("工程地址："+Objects.toString(project.get("siteAddress"),""));p.para("委托/建设单位："+Objects.toString(project.get("clientName"),""));p.para("联系人："+Objects.toString(project.get("contactName"),"")+"   "+Objects.toString(project.get("contactPhone"),""));
   String details=Objects.toString(project.get("detailsJson"),"{}");Map<String,Object> basic=json.readValue(details.isBlank()?"{}":details,Map.class);
   String[][] fields={{"constructionType","工程类别"},{"constructionUnit","建设单位"},{"designUnit","设计单位"},{"designQualification","设计资质等级"},{"contractor","施工单位"},{"contractorQualification","施工资质等级"},{"supervisor","监理单位"},{"supervisorQualification","监理资质等级"},{"buildingOverview","建筑概况"},{"buildingUse","使用性质"},{"buildingArea","建筑面积（㎡）"},{"buildingHeight","建筑高度（m）"},{"aboveFloors","地上层数"},{"belowFloors","地下层数"},{"structureType","结构类型"},{"fireResistance","耐火等级"},{"reportBasis","检测依据"}};for(String[] f:fields)p.para(f[1]+"："+Objects.toString(basic.get(f[0]),"未填写"));
   for(Map<String,Object> s:rows(v,"systems")){String name=text(s,"name"),status=text(s,"status");p.page(name);p.para("系统状态："+state(status));p.para(text(s,"description"));if(!rows(s,"items").isEmpty())p.table(List.of("检验项目 / 设备","状态","标准要求","检测部位","检验结果","单项判定"),new float[]{85,65,140,75,90,60});for(Map<String,Object> r:rows(s,"items"))p.table(List.of(text(r,"name"),state(text(r,"status")),text(r,"requirement"),text(r,"location"),text(r,"result"),verdict(text(r,"verdict"))),new float[]{85,65,140,75,90,60});

    List<Map<String,Object>> photos=rows(v,"photos").stream().filter(r->name.equals(text(r,"system"))).toList();for(int start=0;start<photos.size();start+=4){p.page(name+" · 现场检测照片");for(int j=0;j<4&&start+j<photos.size();j++){Map<String,Object> ph=photos.get(start+j);float x=40+(j%2)*258,bottom=440-(j/2)*335;p.stream.addRect(x,bottom-40,258,335);p.stream.stroke();p.image(source(text(ph,"url")),x+8,bottom,242,275);List<String> caption=wrap(p.font,text(ph,"caption"),9,235);if(caption.size()>2)throw new ServiceException("照片说明超过两行，请精简");for(int k=0;k<caption.size();k++)p.write(caption.get(k),9,x+8,bottom-13-k*12);p.write(text(ph,"date"),9,x+8,bottom-37);}}}
   p.close();if(doc.getNumberOfPages()>600)throw new ServiceException("报告超过600页，请拆分");for(int i=0;i<doc.getNumberOfPages();i++){PDPage page=doc.getPage(i);try(PDPageContentStream stream=new PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true)){stream.beginText();stream.setFont(p.font,9);stream.newLineAtOffset(40,22);stream.showText("第 "+(i+1)+" 页 / 共 "+doc.getNumberOfPages()+" 页");stream.endText();}}doc.save(out);return out.toByteArray();
  }catch(IllegalArgumentException e){throw new ServiceException("文字包含字体不支持的字符，或附件不是有效图片");}
 }
 static String state(String s){return switch(s){case "tested"->"存在且已检测";case "absent"->"不存在";default->"存在但未检测";};}
 static String verdict(String s){return switch(s){case "pass"->"合格";case "fail"->"不合格";case "na"->"不适用";default->"未判定";};}
}
