package top.aameow.management.service;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import top.aameow.management.mapper.WorkProjectMapper;
import top.aameow.common.exception.ServiceException;
import top.aameow.common.utils.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
@Service
public class WorkProjectService {
 @Autowired private WorkProjectMapper mapper;
 @Autowired private BusinessFiles files;
 private final ObjectMapper json=new ObjectMapper();
 public WorkProjectMapper data(){return mapper;}
 public static String text(Map<String,?> m,String key){Object v=m.get(key);return v==null?"":v.toString().trim();}
 public static Long number(Object v){if(v==null||v.toString().isBlank())return null;try{return Long.valueOf(v.toString());}catch(Exception e){throw new ServiceException("无效的编号或用户ID");}}
 public void type(String value){if(!Arrays.asList("WB","JC","PD","PG","QT").contains(value))throw new ServiceException("项目类型无效");}
 public Map<String,Object> get(Long id){Map<String,Object> v=mapper.get(id);if(v==null)throw new ServiceException("项目不存在");return v;}
 private void required(Map<String,Object> v,String key,int max){if(text(v,key).isEmpty()||text(v,key).length()>max)throw new ServiceException(key+"不能为空或超出长度限制");v.put(key,text(v,key));}
 private void limit(Map<String,Object> v,String key,int max){if(text(v,key).length()>max)throw new ServiceException(key+"超出长度限制");}
 private void date(Map<String,Object> v,String key){String value=text(v,key);if(value.isEmpty()){v.put(key,null);return;}try{LocalDate.parse(value);}catch(Exception e){throw new ServiceException("日期格式应为YYYY-MM-DD");}}
 private void dates(Map<String,Object> v,String start,String end){date(v,start);date(v,end);if(v.get(start)!=null&&v.get(end)!=null&&text(v,end).compareTo(text(v,start))<0)throw new ServiceException("结束日期不能早于开始日期");}
 private void choice(Map<String,Object> v,String key,String... values){if(!Arrays.asList(values).contains(text(v,key)))throw new ServiceException("无效的"+key);}
 private void user(Map<String,Object> v,String key){Long id=number(v.get(key));if(id!=null&&mapper.user(id)==null)throw new ServiceException("所选用户不存在或已停用");v.put(key,id);}
 private void basic(Map<String,Object> v,Map<String,Object> old){
  required(v,"projectName",255);type(text(v,"projectType"));
  Long contractId=number(v.get("contractId"));
  if(contractId!=null && contractId==0L)contractId=null;
  if(contractId==null && !"0".equals(text(v,"contractId")) && (old==null || old.get("contractId")!=null))throw new ServiceException("请选择所属合同");
  if(contractId!=null){Map<String,Object> c=mapper.contract(contractId);if(c==null||!text(c,"projectType").equals(text(v,"projectType")))throw new ServiceException("请选择本项目类型的合同");}
  v.put("contractId",contractId);user(v,"leaderUserId");user(v,"technicalUserId");dates(v,"startDate","endDate");
  limit(v,"clientName",255);limit(v,"siteAddress",500);limit(v,"contactName",100);limit(v,"contactPhone",50);
 }
 private String allocate(String type){
  LocalDate day=LocalDate.now(ZoneId.of("Asia/Shanghai"));int year=day.getYear();mapper.ensureSequence(year,type);long next=mapper.lockSequence(year,type);
  String code;do{next++;code=String.format(java.util.Locale.ROOT,"ZJXF%04d%02d%s%03d",year,day.getMonthValue(),type,next);}while(mapper.reserved(code)>0);
  mapper.sequence(year,type,next);return code;
 }
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> create(Map<String,Object> v){basic(v,null);v.remove("id");String code=text(v,"projectNo");if(code.isEmpty())code=allocate(text(v,"projectType"));else {if(code.length()>255||code.chars().anyMatch(Character::isISOControl))throw new ServiceException("项目编号不能超过255字且不能包含控制字符");if(mapper.reserved(code)>0)throw new ServiceException("项目编号已使用，请核对后重新输入");}v.put("projectNo",code);v.put("actor",SecurityUtils.getUsername());mapper.add(v);mapper.reserve(text(v,"projectNo"),number(v.get("id")));return get(number(v.get("id")));}
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> applyNumber(Long id){Map<String,Object> old=get(id);if(!Boolean.TRUE.equals(old.get("numberPending")) && !"1".equals(text(old,"numberPending")))throw new ServiceException("该项目已有正式编号");String code=allocate(text(old,"projectType"));if(mapper.replaceNumber(id,number(old.get("version")),code,SecurityUtils.getUsername())!=1)throw new ServiceException("项目已修改，请刷新重试");mapper.reserve(code,id);return get(id);}
 @Transactional(rollbackFor=Exception.class)
 public void saveSection(Long id,String section,Map<String,Object> v){
  Map<String,Object> old=get(id);v.put("id",id);v.put("section",section);v.put("actor",SecurityUtils.getUsername());
  if(number(v.get("version"))==null)throw new ServiceException("缺少版本号，请刷新页面");
  if("basic".equals(section)){
   boolean pending=Boolean.TRUE.equals(old.get("classificationPending"))||"1".equals(text(old,"classificationPending"));
   boolean numberPending=Boolean.TRUE.equals(old.get("numberPending"))||"1".equals(text(old,"numberPending"));
   if(!pending||!numberPending)v.put("projectType",old.get("projectType"));
   v.put("classificationPending",pending && !Boolean.FALSE.equals(v.get("classificationPending")));basic(v,old);
  }
  else if("details".equals(section)) v.put("detailsJson",object(v.get("details"),60000));
  else if("registration".equals(section)){
   if(!(v.get("registration") instanceof Map))throw new ServiceException("登记信息格式无效");
   @SuppressWarnings("unchecked") Map<String,Object> r=(Map<String,Object>)v.get("registration");
   choice(r,"visitStatus","unknown","not_started","visited","completed");choice(r,"reportStatus","unknown","not_started","in_progress","completed");date(r,"visitDate");date(r,"reportDate");limit(r,"reportNo",255);limit(r,"notes",5000);v.put("registrationJson",object(r,10000));
  } else if("notes".equals(section)) limit(v,"remark",10000);
  else throw new ServiceException("不支持的项目页面");
  if(mapper.section(v)!=1)throw new ServiceException("项目已被其他人修改，请刷新后重试");
 }
 private String object(Object value,int max){if(!(value instanceof Map))throw new ServiceException("详细信息应为对象");try{String s=json.writeValueAsString(value);if(s.length()>max)throw new ServiceException("内容超出长度限制");return s;}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new ServiceException("信息格式无效");}}
 @Transactional(rollbackFor=Exception.class)
 public void issue(Long project,Map<String,Object> v){get(project);v.put("projectId",project);v.put("actor",SecurityUtils.getUsername());required(v,"title",200);limit(v,"description",10000);limit(v,"files",20000);choice(v,"severity","low","medium","high");choice(v,"status","open","processing","resolved");dates(v,"foundDate","resolvedDate");
  int n=number(v.get("id"))==null?mapper.addIssue(v):mapper.editIssue(v);if(n!=1)throw new ServiceException("问题不存在或不属于该项目");files.save("project_issue",number(v.get("id")),text(v,"files"));
 }
 @Transactional(rollbackFor=Exception.class)
 public void removeIssue(Long project,Long id){get(project);if(mapper.removeIssue(project,id)!=1)throw new ServiceException("问题不存在或不属于该项目");files.save("project_issue",id,"");}
 @Transactional(rollbackFor=Exception.class)
 public void plan(Long project,Map<String,Object> v){get(project);v.put("projectId",project);v.put("actor",SecurityUtils.getUsername());required(v,"title",200);limit(v,"remark",5000);choice(v,"planType","visit","completion","report","other");choice(v,"status","planned","in_progress","done","cancelled");required(v,"startDate",10);dates(v,"startDate","endDate");user(v,"assigneeId");int n=number(v.get("id"))==null?mapper.addPlan(v):mapper.editPlan(v);if(n!=1)throw new ServiceException("计划不存在或不属于该项目");}
 public void removePlan(Long project,Long id){get(project);if(mapper.removePlan(project,id)!=1)throw new ServiceException("计划不存在或不属于该项目");}
}
