package top.aameow.management.service;

import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import top.aameow.management.mapper.FinanceLedgerMapper;
import top.aameow.management.mapper.ReimbursementMapper;
import top.aameow.common.exception.ServiceException;
import top.aameow.common.utils.SecurityUtils;

@Service
public class FinanceLedgerService {
 @Autowired private FinanceLedgerMapper mapper;
 @Autowired private ReimbursementMapper claims;
 @Autowired private BusinessFiles files;
 public FinanceLedgerMapper data(){return mapper;}
 private static String text(Map<String,?> v,String k){return WorkProjectService.text(v,k);}
 private static Long id(Object v){return WorkProjectService.number(v);}
 private static ServiceException error(String message){return new ServiceException(message);}
 private static BigDecimal amount(Object v,boolean positive,boolean signed){
  try{BigDecimal n=new BigDecimal(Objects.toString(v,""));if(n.scale()>2||n.abs().compareTo(new BigDecimal("9999999999999999.99"))>0||(!signed&&n.signum()<0)||(positive&&n.signum()<=0))throw new Exception();return n.setScale(2);}
  catch(Exception ex){throw error("金额格式无效：最多两位小数"+(positive?"且必须大于0":""));}
 }
 private Map<String,Object> fields(Map<String,Object> input,String... names){Map<String,Object> v=new HashMap<>();for(String k:names){String s=text(input,k);int max=k.equals("files")?20000:k.equals("purpose")||k.equals("remark")?2000:k.equals("buyerName")||k.equals("counterparty")?200:120;if(s.length()>max)throw error("字段过长："+k);v.put(k,s);}return v;}
 private void date(Map<String,Object> v,String key){try{v.put(key,LocalDate.parse(text(v,key)).toString());}catch(Exception e){throw error("请填写有效日期");}}
 private void required(Map<String,Object> v,String... names){for(String key:names)if(text(v,key).isEmpty())throw error("请完整填写必填信息");}
 private void identifiers(Map<String,Object> v,Map<String,Object> input,String... names){for(String k:names){Long value=id(input.get(k));if(value!=null&&value<=0)throw error("无效关联编号");v.put(k,value);}}
 private void token(Map<String,Object> v,Map<String,Object> input){String token=text(input,"clientToken");try{if(!UUID.fromString(token).toString().equalsIgnoreCase(token))throw new Exception();}catch(Exception e){throw error("提交标识失效，请重新打开表单");}v.put("clientToken",token);}
 private String hash(Map<String,Object> v){try{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new TreeMap<>(v)));}catch(Exception e){throw error("无法生成提交标识");}}
 private Long replay(Map<String,Object> old,String hash){if(old==null)return null;if(!hash.equals(old.get("requestHash")))throw error("此提交已记录且内容不同，请核对原记录");return id(old.get("id"));}
 private Map<String,Object> lockAccount(Long account){if(account==null)throw error("请选择资金账户");Map<String,Object> row=mapper.lockAccount(account);if(row==null)throw error("资金账户不存在");return row;}
 private boolean active(Object v){return "true".equalsIgnoreCase(Objects.toString(v))||"1".equals(Objects.toString(v));}
 private void links(Map<String,Object> v){
  Long project=id(v.get("projectId")),contract=id(v.get("contractId"));
  if(project!=null){Map<String,Object> p=mapper.project(project);if(p==null)throw error("关联项目不存在");Long owned=id(p.get("contractId"));if(contract==null){contract=owned;v.put("contractId",owned);}else if(!contract.equals(owned))throw error("所选项目与合同不一致，项目尚未关联合同的请先完善项目");}
  if(contract!=null&&mapper.contract(contract)==null)throw error("关联合同不存在");
 }
 public void filter(Map<String,Object> q){for(String key:List.of("accountId","contractId","projectId","invoiceId"))q.put(key,id(q.get(key)));for(String key:List.of("dateFrom","dateTo"))if(!text(q,key).isEmpty())date(q,key);if(!text(q,"dateFrom").isEmpty()&&!text(q,"dateTo").isEmpty()&&text(q,"dateFrom").compareTo(text(q,"dateTo"))>0)throw error("日期范围无效");if(text(q,"name").length()>200)throw error("搜索内容过长");}
 public Map<String,Object> overview(){Map<String,Object> result=new HashMap<>();result.put("cash",mapper.cashSummary());result.put("invoices",mapper.invoiceSummary());result.put("claims",claims.summary(Map.of()));result.put("accounts",mapper.accounts(Map.of()));result.put("months",mapper.months());return result;}
 @Transactional(rollbackFor=Exception.class)
 public Long account(Map<String,Object> input){
  Map<String,Object> v=fields(input,"name","holder","accountNumber","remark");required(v,"name");if(text(v,"name").length()>120||text(v,"holder").length()>120)throw error("账户名称过长");v.put("openingBalance",amount(input.getOrDefault("openingBalance","0"),false,true));String enabled=Objects.toString(input.getOrDefault("active",true));if(!List.of("true","false","1","0").contains(enabled))throw error("账户状态无效");v.put("active",active(enabled));v.put("actor",SecurityUtils.getUsername());Long key=id(input.get("id"));
  if(key==null){mapper.addAccount(v);return id(v.get("id"));}
  lockAccount(key);Map<String,Object> previous=mapper.account(key);if(mapper.accountEntries(key)>0&&new BigDecimal(previous.get("openingBalance").toString()).compareTo((BigDecimal)v.get("openingBalance"))!=0)throw error("已有流水的账户不能修改期初余额，请登记调整流水");
  v.put("id",key);v.put("version",id(input.get("version")));if(mapper.editAccount(v)!=1)throw error("账户已被修改，请刷新");return key;
 }
 @Transactional(rollbackFor=Exception.class)
 public Long invoice(Map<String,Object> input){
  Map<String,Object> v=fields(input,"invoiceNo","buyerName","buyerTaxId","invoiceType","invoiceDate","files","remark");required(v,"invoiceNo","buyerName");if(text(v,"invoiceNo").length()>100||text(v,"buyerTaxId").length()>100||text(v,"invoiceType").length()>100)throw error("发票信息过长");date(v,"invoiceDate");v.put("amount",amount(input.get("amount"),true,false));v.put("taxAmount",amount(input.getOrDefault("taxAmount","0"),false,false));if(((BigDecimal)v.get("taxAmount")).compareTo((BigDecimal)v.get("amount"))>0)throw error("税额不能大于价税合计");identifiers(v,input,"contractId","projectId");token(v,input);String fingerprint=hash(v);Long old=replay(mapper.invoiceToken(text(v,"clientToken")),fingerprint);if(old!=null)return old;links(v);v.put("requestHash",fingerprint);v.put("actor",SecurityUtils.getUsername());
  try{mapper.addInvoice(v);}catch(DuplicateKeyException ex){throw error("发票号码或提交记录已存在，请核对");}Long key=id(v.get("id"));files.save("finance_invoice",key,text(v,"files"));return key;
 }
 @Transactional(rollbackFor=Exception.class)
 public Long transaction(Map<String,Object> input){
  Map<String,Object> v=fields(input,"direction","transactionDate","purpose","counterparty","files","remark");required(v,"purpose");date(v,"transactionDate");String direction=text(v,"direction");if(!List.of("income","expense","transfer").contains(direction))throw error("请选择收入、支出或内部转账");v.put("amount",amount(input.get("amount"),true,false));identifiers(v,input,"accountId","toAccountId","contractId","projectId","reimbursementId","invoiceId");token(v,input);String fingerprint=hash(v);Long old=replay(mapper.transactionToken(text(v,"clientToken")),fingerprint);if(old!=null)return old;
  Long account=id(v.get("accountId")),to=id(v.get("toAccountId"));if(account==null)throw error("请选择账户");if(direction.equals("transfer")&&(to==null||to.equals(account)))throw error("转出与转入账户必须不同");if(!direction.equals("transfer")&&to!=null)throw error("普通收支不能填写转入账户");
  TreeSet<Long> accounts=new TreeSet<>();accounts.add(account);if(to!=null)accounts.add(to);for(Long key:accounts)if(!active(lockAccount(key).get("active")))throw error("停用账户不能登记流水");
  Long invoice=id(v.get("invoiceId")),claim=id(v.get("reimbursementId"));if(direction.equals("transfer")&&(invoice!=null||claim!=null||v.get("contractId")!=null||v.get("projectId")!=null))throw error("内部转账不能计入合同、项目、报账或发票收支");
  if(invoice!=null){if(!direction.equals("income"))throw error("开票记录只能关联收款");Map<String,Object> inv=mapper.lockInvoice(invoice);if(inv==null||!"issued".equals(inv.get("status")))throw error("开票记录不存在或已作废");Map<String,Object> info=mapper.invoice(invoice);for(String k:List.of("contractId","projectId")){Long expected=id(info.get(k)),actual=id(v.get(k));if(actual==null)v.put(k,expected);else if(!actual.equals(expected))throw error("流水与发票的合同或项目不一致");}}
  if(claim!=null){if(!direction.equals("expense"))throw error("报账只能关联支出");Map<String,Object> c=mapper.lockClaim(claim);if(c==null||!"pending".equals(c.get("status"))||mapper.claimEntries(claim)>0)throw error("报账不存在、已支付或已退回");if(new BigDecimal(c.get("amount").toString()).compareTo((BigDecimal)v.get("amount"))!=0)throw error("报账支付金额必须等于原单金额");Long p=id(c.get("projectId"));if(v.get("projectId")!=null&&!Objects.equals(id(v.get("projectId")),p))throw error("支付流水与报账项目不一致");v.put("projectId",p);}
  links(v);v.put("requestHash",fingerprint);v.put("actor",SecurityUtils.getUsername());v.put("transferGroup",direction.equals("transfer")?UUID.randomUUID().toString():null);v.put("direction",direction.equals("transfer")?"transfer_out":direction);
  try{mapper.addTransaction(v);}catch(DuplicateKeyException ex){throw error("此流水正在处理，请重试原提交");}Long key=id(v.get("id"));files.save("finance_transaction",key,text(v,"files"));
  if(direction.equals("transfer")){Map<String,Object> incoming=new HashMap<>(v);incoming.put("id",null);incoming.put("accountId",to);incoming.put("direction","transfer_in");incoming.put("clientToken",UUID.randomUUID().toString());incoming.put("files","");mapper.addTransaction(incoming);}
  if(claim!=null)mapper.claimStatus(claim,"paid");return key;
 }
 @Transactional(rollbackFor=Exception.class)
 public void voidInvoice(Long key,Map<String,Object> input){if(mapper.lockInvoice(key)==null)throw error("发票不存在");if(mapper.invoiceEntries(key)>0)throw error("请先作废已关联收款流水，再作废发票记录");Map<String,Object> v=voidFields(key,input);if(mapper.voidInvoice(v)!=1)throw error("发票已被修改或作废，请刷新");}
 private Map<String,Object> voidFields(Long key,Map<String,Object> input){String reason=text(input,"reason");if(reason.isEmpty()||reason.length()>500)throw error("请填写作废原因（最多500字）");Map<String,Object> v=new HashMap<>();v.put("id",key);v.put("version",id(input.get("version")));v.put("reason",reason);v.put("actor",SecurityUtils.getUsername());return v;}
 @Transactional(rollbackFor=Exception.class)
 public void voidTransaction(Long key,Map<String,Object> input){
  Map<String,Object> original=mapper.transaction(key);if(original==null)throw error("流水不存在");String group=Objects.toString(original.get("transferGroup"),"");List<Map<String,Object>> members=group.isEmpty()?List.of(original):mapper.transferDetails(group);
  TreeSet<Long> accounts=new TreeSet<>();for(Map<String,Object> row:members)accounts.add(id(row.get("accountId")));for(Long account:accounts)lockAccount(account);
  Long invoice=id(original.get("invoiceId")),claim=id(original.get("reimbursementId"));if(invoice!=null)mapper.lockInvoice(invoice);if(claim!=null)mapper.lockClaim(claim);Map<String,Object> v=voidFields(key,input);List<Map<String,Object>> locked=group.isEmpty()?List.of(mapper.lockTransaction(key)):mapper.transfer(group);
  Map<String,Object> selected=locked.stream().filter(x->key.equals(id(x.get("id")))).findFirst().orElseThrow(()->error("流水不存在"));if(!Objects.equals(id(selected.get("version")),id(v.get("version"))))throw error("流水已被修改，请刷新");
  for(Map<String,Object> row:locked){Map<String,Object> change=new HashMap<>(v);change.put("id",row.get("id"));change.put("version",row.get("version"));if(mapper.voidTransaction(change)!=1)throw error("流水已作废，请刷新");}if(claim!=null)mapper.claimStatus(claim,"pending");
 }
}
