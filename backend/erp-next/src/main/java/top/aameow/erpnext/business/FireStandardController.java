package top.aameow.erpnext.business;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.aameow.erpnext.auth.CurrentUser;

@RestController
@RequestMapping("/api/fire-standards")
public class FireStandardController {
    private final JdbcTemplate jdbc;
    public FireStandardController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    private static ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
    private long actor(Authentication a){return ((CurrentUser)a.getPrincipal()).id();}
    private String val(Map<String,Object>b,String k,int max,boolean required){
        Object raw=b.get(k);if(raw!=null&&!(raw instanceof String))throw bad(k+" 格式错误");
        String v=Objects.toString(raw,"").trim();
        if(v.length()>max||(required&&v.isEmpty())||v.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))throw bad(k+" 格式错误或过长");
        return v;
    }
    static String source(String raw,boolean access){
        try{
            URI u=URI.create(raw);String host=u.getHost();
            if(!"https".equalsIgnoreCase(u.getScheme())||host==null||u.getRawUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))throw bad("仅允许 HTTPS 来源");
            host=host.toLowerCase(Locale.ROOT);
            if(!host.equals("1190119.com")&&!host.endsWith(".1190119.com")&&!(access&&host.equals("gf.cabr-fire.com")))throw bad("仅允许消防资源网及其指引中的消防规范网入口");
            return u.toASCIIString();
        }catch(IllegalArgumentException e){throw bad("来源网址无效");}
    }
    private LocalDate date(String raw){
        if(raw.isBlank())return null;
        try{return LocalDate.parse(raw);}catch(RuntimeException e){throw bad("日期无效");}
    }
    private Map<String,Object> one(long id){
        var rows=jdbc.queryForList("SELECT * FROM erp_fire_standard WHERE id=?",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"消防规范版本不存在");
        return rows.get(0);
    }
    @GetMapping @PreAuthorize("hasAuthority('report:read') or hasAuthority('report:standard:write')")
    public Map<String,Object> list(Authentication a,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String validity,@RequestParam(defaultValue="") String referenceDate,@RequestParam(defaultValue="false") boolean includeInactive,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="50") int limit){
        if(q.length()>200||category.length()>100||offset<0||limit<1||limit>100||(!validity.isEmpty()&&!Set.of("unknown","current","partial","historical","withdrawn").contains(validity)))throw bad("无效查询");
        boolean manager=a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("report:standard:write"));
        LocalDate reference=date(referenceDate);
        String like="%"+q.trim()+"%",compact="%"+q.replaceAll("\\s+","")+"%";
        var rows=jdbc.queryForList("SELECT s.*,f.original_name,f.sha256 file_sha256 FROM erp_fire_standard s LEFT JOIN erp_file_object f ON f.id=s.file_id WHERE (?=1 OR s.active=TRUE) AND (?='' OR s.category=?) AND (?='' OR s.validity=?) AND (s.title ILIKE ? OR REPLACE(s.standard_no,' ','') ILIKE ? OR s.aliases ILIKE ? OR s.edition ILIKE ?) ORDER BY CASE WHEN CAST(? AS date) IS NULL THEN 0 WHEN s.effective_on IS NULL THEN 1 WHEN s.effective_on<=? AND (s.withdrawn_on IS NULL OR s.withdrawn_on>?) THEN 0 ELSE 2 END,s.category,s.standard_no,s.edition,s.revision_no DESC LIMIT ? OFFSET ?",manager&&includeInactive?1:0,category,category,validity,validity,like,compact,like,like,reference,reference,reference,limit+1,offset);
        return Map.of("rows",rows.size()>limit?rows.subList(0,limit):rows,"hasMore",rows.size()>limit);
    }
    @GetMapping("/categories") @PreAuthorize("hasAuthority('report:read') or hasAuthority('report:standard:write')")
    public List<String> categories(){return jdbc.query("SELECT DISTINCT category FROM erp_fire_standard ORDER BY category",(rs,i)->rs.getString(1));}
    @PostMapping @PreAuthorize("hasAuthority('report:standard:write')") @Transactional
    public Map<String,Object> create(@RequestBody Map<String,Object>b,Authentication a){
        String no=val(b,"standardNo",80,true).replaceAll("\\s+"," ").toUpperCase(Locale.ROOT),edition=val(b,"edition",80,false);
        // A standard number without whitespace and with whitespace denotes the same catalog family.
        String compactNo=no.replaceAll("\\s+","");
        no=compactNo.replaceFirst("^(GB/T|GB|XF|GA|JGJ/T|JGJ|DB)(?=\\d)","$1 ");
        String title=val(b,"title",240,true),category=val(b,"category",100,true),validity=val(b,"validity",24,true);
        if(!Set.of("unknown","current","partial","historical","withdrawn").contains(validity))throw bad("规范状态无效");
        String source=source(val(b,"sourceUrl",1000,true),false),access=val(b,"accessUrl",1000,false);
        if(!access.isEmpty())access=source(access,true);
        String notes=val(b,"notes",2000,false),aliases=val(b,"aliases",240,false);
        Integer year=null;String yearText=val(b,"publicationYear",4,false);
        if(!yearText.isEmpty()){try{year=Integer.parseInt(yearText);}catch(NumberFormatException e){throw bad("发布年份无效");}if(year<1800||year>2200)throw bad("发布年份无效");}
        LocalDate effective=date(val(b,"effectiveOn",10,false)),withdrawn=date(val(b,"withdrawnOn",10,false));
        if(effective!=null&&withdrawn!=null&&withdrawn.isBefore(effective))throw bad("废止日期早于实施日期");
        boolean verified=Boolean.TRUE.equals(b.get("verified"));
        if(verified&&(notes.isBlank()||URI.create(source).getPath().matches("/?")||source.contains("/dropdown/")))throw bad("核验需具体来源页面及人工核验说明，不可仅使用站点首页或目录");
        Long file=null;
        if(b.get("fileId")!=null){
            if(a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals("file:upload")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"无文件上传权限");
            try{file=Long.parseLong(b.get("fileId").toString());}catch(RuntimeException e){throw bad("文件ID无效");}
            Integer found=jdbc.queryForObject("SELECT COUNT(*) FROM erp_file_object WHERE id=? AND uploaded_by=? AND media_type='application/pdf'",Integer.class,file,actor(a));
            if(found==null||found!=1)throw bad("仅支持本人上传的获准保留的PDF原件");
        }
        if(verified&&access.isBlank()&&file==null)throw bad("核验需提供规范原文访问入口或获准保存的PDF");
        jdbc.queryForObject("SELECT id FROM erp_permission WHERE code='report:standard:write' FOR UPDATE",Long.class);
        int next=jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0)+1 FROM erp_fire_standard WHERE REPLACE(standard_no,' ','')=? AND edition=?",Integer.class,compactNo,edition);
        jdbc.update("INSERT INTO erp_fire_standard(standard_no,edition,title,category,publication_year,effective_on,withdrawn_on,validity,aliases,source_url,access_url,notes,verified,revision_no,file_id,verified_by,verified_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CASE WHEN ?=1 THEN LOCALTIMESTAMP(3) ELSE NULL END)",no,edition,title,category,year,effective,withdrawn,validity,aliases,source,access.isBlank()?null:access,notes,verified,next,file,verified?actor(a):null,verified?1:0);
        long id=jdbc.queryForObject("SELECT lastval()",Long.class);
        return one(id);
    }
    @PutMapping("/{id}/active") @PreAuthorize("hasAuthority('report:standard:write')") @Transactional
    public Map<String,Object> active(@PathVariable long id,@RequestBody Map<String,Object>b,Authentication a){
        if(!(b.get("active") instanceof Boolean state))throw bad("active 无效");one(id);
        jdbc.update("UPDATE erp_fire_standard SET active=? WHERE id=?",state,id);return one(id);
    }
}
