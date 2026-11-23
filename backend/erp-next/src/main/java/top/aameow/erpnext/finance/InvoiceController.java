package top.aameow.erpnext.finance;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/finance/invoices")
public class InvoiceController {
    private final JdbcTemplate jdbc;
    public InvoiceController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private long actor(Authentication a){return ((CurrentUser)a.getPrincipal()).id();}
    private String required(Map<String,Object>b,String k){Object v=b.get(k);if(v==null||v.toString().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" 不能为空");return v.toString().trim();}
    private String bounded(Map<String,Object>b,String k,int max){String s=required(b,k);if(s.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" 太长");return s;}
    private BigDecimal money(Object raw){try{BigDecimal n=new BigDecimal(raw.toString());if(n.signum()<=0||n.scale()>2||n.precision()>18)throw new NumberFormatException();return n.setScale(2);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"金额必须为大于零的两位以内小数");}}
    private LocalDate date(Map<String,Object>b,String k){try{return LocalDate.parse(required(b,k));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" 日期格式错误");}}
    private Long optionalId(Map<String,Object>b,String k){try{return b.get(k)==null||b.get(k).toString().isBlank()?null:Long.valueOf(b.get(k).toString());}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" 无效");}}
    private Map<String,Object> one(String sql,Object... args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在");return rows.get(0);}
    private void event(long id,Authentication a,String action,String detail){
        jdbc.update("INSERT INTO erp_invoice_event(invoice_id,action,actor_identity_id,details) VALUES(?,?,?,?)",id,action,actor(a),detail);
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('detail',?))",actor(a),"invoice."+action,"erp_invoice",Long.toString(id),UUID.randomUUID().toString(),detail);
    }
    private void status(Map<String,Object>r,String... allowed){if(!Arrays.asList(allowed).contains(r.get("status").toString()))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前发票状态不允许此操作");}
    private void links(Long customer,Long contract,Long project){
        if(customer!=null)one("SELECT id FROM erp_customer WHERE id=?",customer);
        if(contract!=null){Map<String,Object>c=one("SELECT customer_id FROM erp_contract WHERE id=?",contract);
            if(customer==null||((Number)c.get("customer_id")).longValue()!=customer)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"合同与客户不匹配");}
        if(project!=null){Map<String,Object>p=one("SELECT customer_id,contract_id FROM erp_project WHERE id=?",project);
            if(customer==null||((Number)p.get("customer_id")).longValue()!=customer)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目与客户不匹配");
            if(contract!=null&&(p.get("contract_id")==null||((Number)p.get("contract_id")).longValue()!=contract))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"项目与合同不匹配");}
    }
    private Map<String,Object> detail(long id){
        Map<String,Object>r=new LinkedHashMap<>(one("SELECT * FROM erp_invoice WHERE id=?",id));
        r.put("outstanding_amount",((BigDecimal)r.get("amount")).subtract((BigDecimal)r.get("settled_amount")));
        r.put("settlements",jdbc.queryForList("SELECT * FROM erp_invoice_settlement WHERE invoice_id=? ORDER BY id DESC",id));
        r.put("events",jdbc.queryForList("SELECT * FROM erp_invoice_event WHERE invoice_id=? ORDER BY id DESC",id));
        return r;
    }
    @GetMapping @PreAuthorize("hasAuthority('invoice:read')")
    public List<Map<String,Object>> list(@RequestParam(defaultValue="") String direction,@RequestParam(defaultValue="") String status){
        if(!direction.isBlank()&&!Set.of("receivable","payable").contains(direction))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效类型");
        if(!status.isBlank()&&!Set.of("draft","open","partial","settled","void").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效状态");
        return jdbc.queryForList("SELECT i.*,i.amount-i.settled_amount outstanding_amount FROM erp_invoice i WHERE (?='' OR direction=?) AND (?='' OR status=?) ORDER BY id DESC LIMIT 500",direction,direction,status,status);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('invoice:read')")
    public Map<String,Object> get(@PathVariable long id){return detail(id);}
    @PostMapping @PreAuthorize("hasAuthority('invoice:write')") @Transactional
    public Map<String,Object> create(@RequestBody Map<String,Object>b,Authentication a){
        String no=bounded(b,"invoiceNo",100),direction=required(b,"direction"),name=bounded(b,"counterpartyName",240),title=bounded(b,"title",240);
        if(!Set.of("receivable","payable").contains(direction))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"类型无效");
        Long customer=optionalId(b,"customerId"),contract=optionalId(b,"contractId"),project=optionalId(b,"projectId");links(customer,contract,project);
        BigDecimal amount=money(b.get("amount"));LocalDate issued=date(b,"issuedOn");
        LocalDate due=b.get("dueOn")==null||b.get("dueOn").toString().isBlank()?null:date(b,"dueOn");
        if(due!=null&&due.isBefore(issued))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"到期日不能早于开票日");
        String currency=b.get("currency")==null?"CNY":b.get("currency").toString();
        if(!currency.matches("[A-Z]{3}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"币种代码无效");
        jdbc.update("INSERT INTO erp_invoice(invoice_no,direction,counterparty_name,customer_id,contract_id,project_id,title,currency,amount,issued_on,due_on,notes,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",no,direction,name,customer,contract,project,title,currency,amount,issued,due,b.get("notes"),actor(a));
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);event(id,a,"created","发票登记");return detail(id);
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('invoice:write')") @Transactional
    public Map<String,Object> update(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object>r=one("SELECT * FROM erp_invoice WHERE id=? FOR UPDATE",id);status(r,"draft");
        Long customer=optionalId(b,"customerId"),contract=optionalId(b,"contractId"),project=optionalId(b,"projectId");links(customer,contract,project);
        BigDecimal amount=money(b.get("amount"));LocalDate issued=date(b,"issuedOn");
        LocalDate due=b.get("dueOn")==null||b.get("dueOn").toString().isBlank()?null:date(b,"dueOn");
        if(due!=null&&due.isBefore(issued))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"到期日不能早于开票日");
        jdbc.update("UPDATE erp_invoice SET counterparty_name=?,title=?,customer_id=?,contract_id=?,project_id=?,amount=?,issued_on=?,due_on=?,notes=? WHERE id=?",bounded(b,"counterpartyName",240),bounded(b,"title",240),customer,contract,project,amount,issued,due,b.get("notes"),id);
        event(id,a,"updated","草稿编辑");return detail(id);
    }
    @PostMapping("/{id}/approve") @PreAuthorize("hasAuthority('invoice:approve')") @Transactional
    public Map<String,Object> approve(@PathVariable long id,Authentication a){
        Map<String,Object>r=one("SELECT * FROM erp_invoice WHERE id=? FOR UPDATE",id);status(r,"draft");
        if(((Number)r.get("created_by")).longValue()==actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"不得自行审核发票");
        jdbc.update("UPDATE erp_invoice SET status='open',approved_by=?,approved_at=CURRENT_TIMESTAMP(3) WHERE id=?",actor(a),id);
        event(id,a,"approved","审核入账");return detail(id);
    }
    @PostMapping("/{id}/void") @PreAuthorize("hasAuthority('invoice:approve')") @Transactional
    public Map<String,Object> voidInvoice(@PathVariable long id,Authentication a){
        Map<String,Object>r=one("SELECT * FROM erp_invoice WHERE id=? FOR UPDATE",id);status(r,"draft","open");
        if(((BigDecimal)r.get("settled_amount")).signum()!=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"已有核销记录不能作废");
        jdbc.update("UPDATE erp_invoice SET status='void' WHERE id=?",id);event(id,a,"void","发票作废");return detail(id);
    }
    @PostMapping("/{id}/settlements") @PreAuthorize("hasAuthority('invoice:settle')") @Transactional
    public Map<String,Object> settle(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object>r=one("SELECT * FROM erp_invoice WHERE id=? FOR UPDATE",id);status(r,"open","partial");
        String reference=bounded(b,"paymentReference",120);BigDecimal amount=money(b.get("amount"));
        BigDecimal outstanding=((BigDecimal)r.get("amount")).subtract((BigDecimal)r.get("settled_amount"));
        if(amount.compareTo(outstanding)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"核销金额超出未结余额");
        Integer used=jdbc.queryForObject("SELECT COUNT(*) FROM erp_invoice_settlement WHERE payment_reference=?",Integer.class,reference);
        if(used!=null&&used>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"流水号已使用");
        LocalDate settledOn=date(b,"settledOn");
        jdbc.update("INSERT INTO erp_invoice_settlement(invoice_id,payment_reference,amount,settled_on,notes,created_by) VALUES(?,?,?,?,?,?)",id,reference,amount,settledOn,b.get("notes"),actor(a));
        jdbc.update("UPDATE erp_invoice SET settled_amount=settled_amount+?,status=? WHERE id=?",amount,amount.compareTo(outstanding)==0?"settled":"partial",id);
        event(id,a,"settlement",reference+" / "+amount);return detail(id);
    }
    @GetMapping("/summary") @PreAuthorize("hasAuthority('invoice:read')")
    public List<Map<String,Object>> summary(){
        return jdbc.queryForList("SELECT direction,currency,COUNT(*) invoice_count,SUM(amount) total_amount,SUM(settled_amount) settled_amount,SUM(amount-settled_amount) outstanding_amount FROM erp_invoice WHERE status IN ('open','partial','settled') GROUP BY direction,currency ORDER BY direction,currency");
    }
}
