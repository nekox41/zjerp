package top.aameow.erpnext.migration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/** Explicitly invoked, administrator-only inventory of an offline legacy copy. No source mutations. */
@RestController
@RequestMapping("/api/migration")
@PreAuthorize("hasAuthority('identity:write')")
public class LegacyInventoryController {
    private final JdbcTemplate target;
    private final String url,user,password,schema;
    private final boolean enabled;
    private static final Pattern NAME=Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Set<String> SAFE_PREVIEW=Set.of("zj_employee","zj_intermediary","biz_agent","zj_work_project","zj_contract_maintenance",
        "zj_contract_inspection","zj_contract_rating","zj_contract_assessment","zj_contract_other","zj_contract_identity","zj_wbcard",
        "zjerp_wbcard_phone","zj_wbcard_material","zj_work_project_issue","zj_report_credential","sys_file","zj_inspection_report","zj_report","project");
    public LegacyInventoryController(JdbcTemplate target,
        @Value("${ERP_LEGACY_SCAN_ENABLED:false}") boolean enabled,
        @Value("${ERP_LEGACY_DB_URL:}") String url,
        @Value("${ERP_LEGACY_DB_USER:}") String user,
        @Value("${ERP_LEGACY_DB_PASSWORD:}") String password,
        @Value("${ERP_LEGACY_DB_SCHEMA:server_migration}") String schema){
        this.target=target;this.enabled=enabled;this.url=url;this.user=user;this.password=password;this.schema=schema;
    }
    private ResponseStatusException bad(HttpStatus status,String message){return new ResponseStatusException(status,message);}
    private void admin(Authentication a){
        long id=((CurrentUser)a.getPrincipal()).id();
        Integer count=target.queryForObject("SELECT COUNT(*) FROM erp_identity_role ir JOIN erp_role r ON r.id=ir.role_id JOIN erp_identity i ON i.id=ir.identity_id WHERE ir.identity_id=? AND r.code='admin' AND i.status='active'",Integer.class,id);
        if(count==null||count==0)throw bad(HttpStatus.FORBIDDEN,"仅系统管理员可访问迁移控制台");
    }
    private boolean configured(){return enabled&&!url.isBlank()&&!user.isBlank()&&!password.isBlank()&&schema.matches("server_[A-Za-z0-9_]{1,56}")&&url.startsWith("jdbc:mysql://");}
    @GetMapping("/config")
    public Map<String,Object> config(Authentication a){
        admin(a);return Map.of("enabled",enabled,"configured",configured(),"sourceSchema",schema,"targetSchema",Objects.toString(target.queryForObject("SELECT DATABASE()",String.class),""),"mode","offline-copy-read-only","previewLimit",20);
    }
    private Connection connect()throws SQLException{
        if(!configured())throw bad(HttpStatus.CONFLICT,"请在服务端配置隔离的 server_ 开头数据库副本和仅 SELECT 账户");
        if(!"erp_next".equals(target.queryForObject("SELECT DATABASE()",String.class)))throw bad(HttpStatus.CONFLICT,"目标数据库不是隔离的 erp_next");
        Properties props=new Properties();
        props.setProperty("user",user);props.setProperty("password",password);
        props.setProperty("connectTimeout","5000");props.setProperty("socketTimeout","20000");
        props.setProperty("allowMultiQueries","false");props.setProperty("allowLoadLocalInfile","false");
        Connection c=DriverManager.getConnection(url,props);
        try{
            if(!schema.equals(c.getCatalog()))throw bad(HttpStatus.CONFLICT,"源连接数据库与配置不一致");
            c.setReadOnly(true);c.setAutoCommit(false);
            try(Statement s=c.createStatement()){s.setQueryTimeout(5);s.execute("START TRANSACTION READ ONLY");}
            return c;
        }catch(SQLException|RuntimeException e){c.close();throw e;}
    }
    private String name(String s){
        if(s==null||!NAME.matcher(s).matches())throw bad(HttpStatus.BAD_REQUEST,"不支持的表或列名称");
        return "\u0060"+s+"\u0060";
    }
    private String disposition(String table){
        if(SAFE_PREVIEW.contains(table))return "review_mapping";
        if(Set.of("sys_user","sys_role","sys_user_role","sys_role_menu","sys_menu","sys_user_material").contains(table))return "security_review";
        if(table.startsWith("zj_finance_")||table.contains("reimbursement")||table.contains("expense"))return "deferred_archive";
        return "preserve_raw";
    }
    private List<Map<String,Object>> tables(Connection c)throws SQLException{
        List<Map<String,Object>> rows=new ArrayList<>();
        try(PreparedStatement s=c.prepareStatement("SELECT TABLE_NAME,TABLE_TYPE,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=? ORDER BY TABLE_NAME")){
            s.setString(1,schema);s.setQueryTimeout(10);
            try(ResultSet rs=s.executeQuery()){
                while(rs.next()){
                    if(rows.size()>=500)throw bad(HttpStatus.UNPROCESSABLE_ENTITY,"源表数量超过 500，须使用离线备份工具处理");
                    Map<String,Object> row=new LinkedHashMap<>();
                    String t=rs.getString(1);row.put("table",t);row.put("type",rs.getString(2));row.put("engine",rs.getString(3));
                    row.put("disposition",disposition(t));row.put("previewAllowed",SAFE_PREVIEW.contains(t)&&"BASE TABLE".equals(rs.getString(2)));
                    rows.add(row);
                }
            }
        }
        return rows;
    }
    private long count(Connection c,String sql)throws SQLException{
        try(Statement s=c.createStatement()){s.setQueryTimeout(10);try(ResultSet rs=s.executeQuery(sql)){rs.next();return rs.getLong(1);}}
    }
    private void check(Connection c,Set<String> tables,List<Map<String,Object>> result,String key,String description,String table,String predicate){
        if(!tables.contains(table))return;
        try{result.add(Map.of("key",key,"description",description,"count",count(c,"SELECT COUNT(*) FROM "+name(table)+" WHERE "+predicate)));}
        catch(SQLException e){result.add(Map.of("key",key,"description",description,"error","字段结构不匹配或查询失败；需人工核查"));}
    }
    @GetMapping("/scan")
    public Map<String,Object> scan(Authentication a){
        admin(a);
        try(Connection c=connect()){
            List<Map<String,Object>> rows=tables(c);
            Set<String> names=new HashSet<>();
            long total=0;int errors=0;
            for(Map<String,Object> row:rows){
                String table=row.get("table").toString();
                if(!"BASE TABLE".equals(row.get("type")))continue;
                names.add(table);
                try{long n=count(c,"SELECT COUNT(*) FROM "+name(table));row.put("rows",n);total+=n;}
                catch(SQLException|ResponseStatusException e){row.put("error","计数失败；不可视作空表");errors++;}
            }
            List<Map<String,Object>> conflicts=new ArrayList<>();
            check(c,names,conflicts,"project_client_missing","项目缺少甲方名称","zj_work_project","client_name IS NULL OR TRIM(client_name)=''");
            check(c,names,conflicts,"project_classification_pending","项目分类待人工确认","zj_work_project","classification_pending=TRUE OR number_pending=TRUE");
            check(c,names,conflicts,"project_leaders_missing","项目双负责人待确认","zj_work_project","leader_employee_id IS NULL OR technical_employee_id IS NULL OR leader_employee_id=technical_employee_id");
            for(String t:List.of("zj_contract_maintenance","zj_contract_inspection","zj_contract_rating","zj_contract_assessment","zj_contract_other")){
                check(c,names,conflicts,t+"_review","合同缺编号、归属待确认或金额精度需核对",t,"contract_id IS NULL OR TRIM(contract_id)='' OR ownership<>'company' OR classification_pending=TRUE OR amount IS NULL OR amount<0 OR amount<>ROUND(amount,2) OR amount>=10000000000000000");
            }
            check(c,names,conflicts,"file_hash_missing","历史文件缺 SHA256，需要读取原件验证","sys_file","file_hash IS NULL OR TRIM(file_hash)=''");
            c.rollback();
            return Map.of("tables",rows,"conflicts",conflicts,"countedRows",total,"failedCounts",errors,"baseTables",names.size(),"sourceSchema",schema);
        }catch(SQLException e){throw bad(HttpStatus.SERVICE_UNAVAILABLE,"无法读取旧库副本，请检查连接、只读授权与数据库服务；未写入源库");}
    }
    @GetMapping("/tables/{table}")
    public Map<String,Object> preview(@PathVariable String table,Authentication a){
        admin(a);
        if(!SAFE_PREVIEW.contains(table))throw bad(HttpStatus.FORBIDDEN,"该表仅统计和离线保全，不提供网页原始数据预览");
        try(Connection c=connect()){
            if(tables(c).stream().noneMatch(t->table.equals(t.get("table"))&&"BASE TABLE".equals(t.get("type"))))
                throw bad(HttpStatus.NOT_FOUND,"源表不存在");
            List<String> columns=new ArrayList<>();List<Map<String,Object>> rows=new ArrayList<>();
            String order=table.equals("sys_file")?"file_id":"id";
            try(Statement s=c.createStatement()){
                s.setQueryTimeout(10);s.setMaxRows(20);
                try(ResultSet rs=s.executeQuery("SELECT * FROM "+name(table)+" ORDER BY "+name(order)+" LIMIT 20")){
                    var meta=rs.getMetaData();
                    for(int i=1;i<=meta.getColumnCount();i++){
                        String col=meta.getColumnLabel(i);
                        if(!col.toLowerCase(Locale.ROOT).matches(".*(password|passwd|token|secret|credential_hash|file_path|file_url|snapshot_json|details_json|registration_json).*"))columns.add(col);
                    }
                    while(rs.next()){
                        Map<String,Object> row=new LinkedHashMap<>();
                        for(String col:columns){String value=rs.getString(col);row.put(col,value==null?null:(value.length()>400?value.substring(0,400)+"…":value));}
                        rows.add(row);
                    }
                }
            }
            c.rollback();return Map.of("table",table,"columns",columns,"rows",rows,"limit",20);
        }catch(SQLException e){throw bad(HttpStatus.UNPROCESSABLE_ENTITY,"源表预览失败，可能为历史结构差异；请使用离线备份检查");}
    }
}
