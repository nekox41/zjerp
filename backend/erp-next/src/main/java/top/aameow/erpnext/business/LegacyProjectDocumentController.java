package top.aameow.erpnext.business;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/business/projects/{id}/legacy-documents")
@PreAuthorize("hasAuthority('project:read') and hasAuthority('report:read')")
public class LegacyProjectDocumentController {
    private final JdbcTemplate jdbc;
    public LegacyProjectDocumentController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping
    public Map<String,Object> list(@PathVariable long id,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="50") int limit){
        if(offset<0||limit<1||limit>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效分页参数");
        if(jdbc.queryForList("SELECT id FROM erp_project WHERE id=?",id).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"项目不存在");
        var rows=jdbc.queryForList("SELECT id,title,document_kind,report_no,source_table,source_key,original_created_at,original_status,archive_note,archived_at FROM erp_legacy_project_document WHERE project_id=? ORDER BY original_created_at DESC,id DESC LIMIT ? OFFSET ?",id,limit+1,offset);
        boolean more=rows.size()>limit;
        var page=more?rows.subList(0,limit):rows;
        for(var row:page)row.put("files",jdbc.queryForList("SELECT f.id file_id,f.original_name,f.media_type,f.byte_size,f.sha256 FROM erp_legacy_project_document_file d JOIN erp_file_object f ON f.id=d.file_id WHERE d.document_id=? ORDER BY f.id",row.get("id")));
        return Map.of("rows",page,"offset",offset,"hasMore",more);
    }
}
