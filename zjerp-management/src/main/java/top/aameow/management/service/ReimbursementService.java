package top.aameow.management.service;
import java.util.*;import java.time.LocalDate;import java.math.BigDecimal;
import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.multipart.MultipartFile;
import top.aameow.management.mapper.ReimbursementMapper;import top.aameow.system.service.ISysFilePlusService;import top.aameow.common.exception.ServiceException;import top.aameow.common.utils.SecurityUtils;
@Service
public class ReimbursementService {
 @Autowired private ReimbursementMapper mapper;@Autowired private ISysFilePlusService files;
 public ReimbursementMapper data(){return mapper;}
 public static String text(Map<String,?> m,String k){return Objects.toString(m.get(k),"").trim();}
 public void filter(Map<String,Object> q){for(String k:List.of("projectId","userId")){String s=text(q,k);q.put(k,s.isEmpty()?null:WorkProjectService.number(s));}for(String k:List.of("dateFrom","dateTo")){String s=text(q,k);if(!s.isEmpty())try{LocalDate.parse(s);}catch(Exception ex){throw new ServiceException("日期格式错误");}}if(!text(q,"dateFrom").isEmpty()&&!text(q,"dateTo").isEmpty()&&text(q,"dateFrom").compareTo(text(q,"dateTo"))>0)throw new ServiceException("结束日期不能早于开始日期");}
 @Transactional(rollbackFor=Exception.class)
 public String submit(Map<String,Object> v,List<MultipartFile> invoices,boolean anonymous) throws Exception {
  String token=text(v,"clientToken");try{if(!UUID.fromString(token).toString().equalsIgnoreCase(token))throw new Exception();}catch(Exception ex){throw new ServiceException("提交标识无效，请刷新页面");}

  BigDecimal amount;try{amount=new BigDecimal(text(v,"amount"));if(amount.signum()<=0||amount.scale()>2||amount.compareTo(new BigDecimal("9999999999999999.99"))>0)throw new Exception();}catch(Exception ex){throw new ServiceException("金额必须大于0，最多两位小数");}
  String purpose=text(v,"purpose"),remark=text(v,"remark");if(purpose.isEmpty()||purpose.length()>2000||remark.length()>2000)throw new ServiceException("用途必填且不得超过2000字");
  try{LocalDate.parse(text(v,"expenseDate"));}catch(Exception ex){throw new ServiceException("请填写有效报账日期");}
  Long uid=WorkProjectService.number(v.get("userId"));String name=uid==null?null:mapper.userName(uid);if(name==null)throw new ServiceException("请选择有效报账人");
  Long pid=text(v,"projectId").isEmpty()?null:WorkProjectService.number(v.get("projectId"));if(pid!=null&&mapper.project(pid)==null)throw new ServiceException("关联项目不存在");
  List<MultipartFile> incoming=invoices==null?List.of():invoices;if(incoming.size()>5)throw new ServiceException("最多上传5张发票");
  Map<String,Object> fingerprint=new TreeMap<>();fingerprint.put("amount",amount.stripTrailingZeros().toPlainString());fingerprint.put("purpose",purpose);fingerprint.put("remark",remark);fingerprint.put("date",text(v,"expenseDate"));fingerprint.put("user",uid);fingerprint.put("project",pid);fingerprint.put("public",anonymous);
  List<String> hashes=new ArrayList<>();for(MultipartFile f:incoming)if(!f.isEmpty()){if(f.getSize()>10L*1024*1024)throw new ServiceException("每个发票文件不能超过10MB");try(var in=f.getInputStream()){hashes.add(f.getOriginalFilename()+":"+org.apache.commons.codec.digest.DigestUtils.sha256Hex(in));}}
  fingerprint.put("files",hashes);String hash=org.apache.commons.codec.digest.DigestUtils.sha256Hex(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fingerprint));
  Map<String,Object> old=mapper.receipt(token);if(old!=null){if(!hash.equals(old.get("requestHash")))throw new ServiceException("此笔提交已受理且内容不同，请先核对原单，勿重复录入");return old.get("receipt").toString();}v.put("requestHash",hash);
  v.put("receipt",UUID.randomUUID().toString());v.put("clientToken",token);v.put("amount",amount);v.put("purpose",purpose);v.put("remark",remark);v.put("userId",uid);v.put("projectId",pid);v.put("claimantName",name);v.put("source",anonymous?"public":"staff");v.put("actor",anonymous?"public-reimbursement":SecurityUtils.getUsername());try{mapper.add(v);}catch(org.springframework.dao.DuplicateKeyException e){throw new ServiceException("此笔报账正在处理，请稍后重试原提交");}
  List<String> urls=new ArrayList<>();for(MultipartFile file:incoming)if(!file.isEmpty())urls.add(files.uploadInvoice(file,WorkProjectService.number(v.get("id")),v.get("actor").toString()).getFileUrl());mapper.invoices(WorkProjectService.number(v.get("id")),String.join(",",urls));return v.get("receipt").toString();
 }
 @Transactional(rollbackFor=Exception.class)
 public void status(Long id,Map<String,Object> v){if(!List.of("pending","paid","rejected").contains(text(v,"status")))throw new ServiceException("无效状态");v.put("id",id);v.put("version",WorkProjectService.number(v.get("version")));if(mapper.status(v)!=1)throw new ServiceException("记录已被修改，请刷新重试");}
}
