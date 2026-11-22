package top.aameow.management.migrate;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.aameow.management.service.BusinessFiles;

/** 固定白名单，逐行业务事务，可重试；成功后清空旧列，原磁盘文件保留。 */
@Service
public class FileMigrateService
{
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private BusinessFiles files;

    public Map<String, Object> migrate()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int success = 0;
        String[][] mappings = {
            {"zj_contract", "contract_file", "contract"},
            {"zj_report", "report_file", "report"},
            {"zj_report", "report_attach", "report_attach"},
            {"zj_wbcard", "file", "wbcard"}
        };
        for (String[] mapping : mappings)
        {
            String table = mapping[0], column = mapping[1], type = mapping[2];
            // 不将配置或请求参数拼接进 SQL。
            List<Long> ids = jdbc.queryForList("select id from " + table + " where " + column
                    + " is not null and " + column + " != ''", Long.class);
            for (Long id : ids)
            {
                try
                {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        String urls = jdbc.queryForObject("select " + column + " from " + table + " where id = ? for update", String.class, id);
                        if (urls == null || urls.isBlank()) return;
                        files.save(type, id, urls);
                        jdbc.update("update " + table + " set " + column + " = null where id = ?", id);
                    });
                    success++;
                }
                catch (Exception e) { errors.add(table + "." + column + " id=" + id + ": " + e.getMessage()); }
            }
        }
        result.put("success", success);
        result.put("failed", errors.size());
        result.put("errors", errors);
        return result;
    }
}
