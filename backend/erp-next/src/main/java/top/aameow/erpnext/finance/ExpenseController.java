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
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/finance/expenses")
public class ExpenseController {
    private final JdbcTemplate jdbc;
    public ExpenseController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private long actor(Authentication a){return ((CurrentUser)a.getPrincipal()).id();}
    private boolean canApprove(Authentication a){return a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("expense:approve"));}
    private boolean canPay(Authentication a){return a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("expense:pay"));}
    private String required(Map<String,Object>b,String k){Object v=b.get(k);if(v==null||v.toString().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k+" 不能为空");return v.toString().trim();}
    private Map<String,Object> one(String sql,Object... args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"报账单不存在");return rows.get(0);}
    private Map<String,Object> lock(long id){return one("SELECT * FROM erp_expense_claim WHERE id=? FOR UPDATE",id);}
    private long applicant(Map<String,Object> r){return ((Number)r.get("applicant_identity_id")).longValue();}
    private void requireOwner(Map<String,Object>r,Authentication a){if(applicant(r)!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能维护本人报账单");}
    private void requireVersion(Map<String,Object>r,Map<String,Object>b){Object v=b.get("expectedVersionNo");if(v==null||((Number)r.get("version_no")).intValue()!=Integer.parseInt(v.toString()))throw new ResponseStatusException(HttpStatus.CONFLICT,"报账单版本冲突，请刷新");}
    private void requireStatus(Map<String,Object>r,String... allowed){if(!Arrays.asList(allowed).contains(r.get("status").toString()))throw new ResponseStatusException(HttpStatus.CONFLICT,"当前报账状态不允许此操作");}
    private void audit(long id,long who,String from,String to,String reason){
        jdbc.update("INSERT INTO erp_expense_event(claim_id,from_status,to_status,actor_identity_id,reason) VALUES(?,?,?,?,?)",id,from,to,who,reason);
        jdbc.update("INSERT INTO erp_audit_event(actor_identity_id,action,resource_type,resource_id,request_id,summary_json) VALUES(?,?,?,?,?,JSON_OBJECT('from',?,'to',?,'reason',?))",who,"expense."+to,"erp_expense_claim",Long.toString(id),UUID.randomUUID().toString(),from,to,reason);
    }
    private Map<String,Object> detail(long id){
        Map<String,Object> r=new LinkedHashMap<>(one("SELECT * FROM erp_expense_claim WHERE id=?",id));
        r.put("lines",jdbc.queryForList("SELECT * FROM erp_expense_line WHERE claim_id=? ORDER BY sort_order,id",id));
        r.put("attachments",jdbc.queryForList("SELECT a.id,a.file_id,f.original_name,f.byte_size file_size,f.sha256 FROM erp_expense_attachment a JOIN erp_file_object f ON f.id=a.file_id WHERE a.claim_id=? ORDER BY a.id",id));
        r.put("events",jdbc.queryForList("SELECT e.*,i.display_name actor_name FROM erp_expense_event e JOIN erp_identity i ON i.id=e.actor_identity_id WHERE e.claim_id=? ORDER BY e.id",id));
        return r;
    }
    private void readAccess(Map<String,Object>r,Authentication a){
        if(applicant(r)!=actor(a)&&!canApprove(a)&&!canPay(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无权查看其他员工报账");
    }
    private BigDecimal amount(Object x){
        try{
            BigDecimal n=new BigDecimal(x.toString());
            if(n.scale()>2||n.signum()<=0||n.precision()>18)throw new NumberFormatException();
            return n.setScale(2,RoundingMode.UNNECESSARY);
        }catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"金额必须是大于零的两位以内小数");}
    }
    private void replaceLines(long id,Object raw){
        if(!(raw instanceof List<?> items)||items.isEmpty()||items.size()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"必须填写 1 至 100 条费用明细");
        jdbc.update("DELETE FROM erp_expense_line WHERE claim_id=?",id);
        BigDecimal total=BigDecimal.ZERO;
        int i=0;
        for(Object item:items){
            if(!(item instanceof Map<?,?> m))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"费用明细格式错误");
            Map<String,Object>b=new HashMap<>();m.forEach((k,v)->b.put(k.toString(),v));
            String description=required(b,"description"),category=required(b,"category"),date=required(b,"incurredOn");
            if(description.length()>500||category.length()>60||!date.matches("\\d{4}-\\d{2}-\\d{2}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"费用明细格式错误");
            try{java.time.LocalDate.parse(date);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"费用日期无效");}
            BigDecimal value=amount(b.get("amount"));total=total.add(value);
            if(total.precision()>18)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"金额总计超出限制");
            jdbc.update("INSERT INTO erp_expense_line(claim_id,description,category,incurred_on,amount,sort_order) VALUES(?,?,?,?,?,?)",id,description,category,date,value,i++);
        }
        jdbc.update("UPDATE erp_expense_claim SET total_amount=? WHERE id=?",total,id);
    }
    @GetMapping @PreAuthorize("hasAuthority('expense:read')")
    public List<Map<String,Object>> list(Authentication a,@RequestParam(defaultValue="") String status){
        if(!status.isBlank()&&!Set.of("draft","submitted","approved","returned","rejected","paid").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效状态");
        if(canApprove(a)||canPay(a))return jdbc.queryForList("SELECT * FROM erp_expense_claim WHERE (?='' OR status=?) ORDER BY id DESC LIMIT 500",status,status);
        return jdbc.queryForList("SELECT * FROM erp_expense_claim WHERE applicant_identity_id=? AND (?='' OR status=?) ORDER BY id DESC LIMIT 500",actor(a),status,status);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('expense:read')")
    public Map<String,Object> get(@PathVariable long id,Authentication a){Map<String,Object>r=detail(id);readAccess(r,a);return r;}
    @PostMapping @PreAuthorize("hasAuthority('expense:write')") @Transactional
    public Map<String,Object> create(@RequestBody Map<String,Object>b,Authentication a){
        String no=required(b,"claimNo"),title=required(b,"title");
        if(no.length()>80||title.length()>240)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"编号或标题过长");
        Long project=b.get("projectId")==null?null:Long.valueOf(b.get("projectId").toString());
        if(project!=null)one("SELECT id FROM erp_project WHERE id=?",project);
        jdbc.update("INSERT INTO erp_expense_claim(claim_no,title,applicant_identity_id,project_id) VALUES(?,?,?,?)",no,title,actor(a),project);
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        replaceLines(id,b.get("lines"));audit(id,actor(a),null,"draft","创建费用报账单");return detail(id);
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('expense:write')") @Transactional
    public Map<String,Object> update(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object>r=lock(id);requireOwner(r,a);requireStatus(r,"draft","returned");requireVersion(r,b);
        String title=required(b,"title");
        if(title.length()>240)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"标题过长");
        replaceLines(id,b.get("lines"));
        jdbc.update("UPDATE erp_expense_claim SET title=?,status='draft',return_reason=NULL,reviewer_identity_id=NULL,reviewed_at=NULL,version_no=version_no+1 WHERE id=?",title,id);
        audit(id,actor(a),r.get("status").toString(),"draft","更新费用明细");return detail(id);
    }
    @PostMapping("/{id}/attachments") @PreAuthorize("hasAuthority('expense:write')") @Transactional
    public Map<String,Object> attach(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object>r=lock(id);requireOwner(r,a);requireStatus(r,"draft","returned");
        long fileId=Long.parseLong(required(b,"fileId"));
        Map<String,Object>f=one("SELECT uploaded_by FROM erp_file_object WHERE id=?",fileId);
        if(!(f.get("uploaded_by") instanceof Number n)||n.longValue()!=actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能关联自己上传的附件");
        jdbc.update("INSERT INTO erp_expense_attachment(claim_id,file_id,added_by) VALUES(?,?,?)",id,fileId,actor(a));
        audit(id,actor(a),r.get("status").toString(),r.get("status").toString(),"添加费用附件");return detail(id);
    }
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('expense:write')") @Transactional
    public Map<String,Object> submit(@PathVariable long id,Authentication a){
        Map<String,Object>r=lock(id);requireOwner(r,a);requireStatus(r,"draft");
        jdbc.update("UPDATE erp_expense_claim SET status='submitted',submitted_at=CURRENT_TIMESTAMP(3) WHERE id=?",id);
        audit(id,actor(a),"draft","submitted","提交财务审批");return detail(id);
    }
    @PostMapping("/{id}/approve") @PreAuthorize("hasAuthority('expense:approve')") @Transactional
    public Map<String,Object> approve(@PathVariable long id,Authentication a){
        Map<String,Object>r=lock(id);requireStatus(r,"submitted");
        if(applicant(r)==actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"报账申请人不能自行审批");
        jdbc.update("UPDATE erp_expense_claim SET status='approved',reviewer_identity_id=?,reviewed_at=CURRENT_TIMESTAMP(3) WHERE id=?",actor(a),id);
        audit(id,actor(a),"submitted","approved","财务审批通过");return detail(id);
    }
    @PostMapping("/{id}/return") @PreAuthorize("hasAuthority('expense:approve')") @Transactional
    public Map<String,Object> returnClaim(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object>r=lock(id);requireStatus(r,"submitted");
        if(applicant(r)==actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"报账申请人不能自行退回");
        String reason=required(b,"reason");if(reason.length()>1000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"退回理由过长");
        jdbc.update("UPDATE erp_expense_claim SET status='returned',return_reason=?,reviewer_identity_id=?,reviewed_at=CURRENT_TIMESTAMP(3) WHERE id=?",reason,actor(a),id);
        audit(id,actor(a),"submitted","returned",reason);return detail(id);
    }
    @PostMapping("/{id}/pay") @PreAuthorize("hasAuthority('expense:pay')") @Transactional
    public Map<String,Object> pay(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        Map<String,Object>r=lock(id);requireStatus(r,"approved");
        if(applicant(r)==actor(a))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"报账申请人不能登记自己的付款");
        String reference=required(b,"paymentReference");
        if(reference.length()>120)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"付款流水号过长");
        Integer existing=jdbc.queryForObject("SELECT COUNT(*) FROM erp_expense_claim WHERE payment_reference=?",Integer.class,reference);
        if(existing!=null&&existing>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"付款流水号已被使用");
        BigDecimal paid=amount(b.get("paidAmount"));
        if(paid.compareTo((BigDecimal)r.get("total_amount"))!=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"当前仅支持全额付款，付款金额须等于批准金额");
        jdbc.update("UPDATE erp_expense_claim SET status='paid',payment_reference=?,paid_amount=?,paid_at=CURRENT_TIMESTAMP(3),payer_identity_id=? WHERE id=?",reference,paid,actor(a),id);
        audit(id,actor(a),"approved","paid","付款登记："+reference);return detail(id);
    }
}
