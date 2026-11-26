package top.aameow.erpnext.business;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class BusinessNumberPool {
    static final int RESERVATION_TTL_SECONDS = 30 * 60;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Map<String,String> PREFIXES = Map.of(
        "maintenance", "ZJWB",
        "fire_inspection", "ZJJC",
        "fire_assessment", "ZJPD",
        "fire_evaluation", "ZJPG",
        "other", "ZJFW"
    );

    private BusinessNumberPool() {}

    record Reservation(UUID id, String number, String serviceType, String purpose, int year, int sequence) {}

    static Reservation reserve(JdbcTemplate jdbc, String serviceType, String purpose, long actor) {
        String prefix = prefix(serviceType);
        requirePurpose(serviceType, purpose);
        int year = LocalDate.now(BUSINESS_ZONE).getYear();

        jdbc.update("""
            INSERT INTO erp_number_pool(service_type,number_year,last_number)
            VALUES(?,?,0)
            ON CONFLICT(service_type,number_year) DO NOTHING
            """, serviceType, year);
        Integer current = jdbc.queryForObject(
            "SELECT last_number FROM erp_number_pool WHERE service_type=? AND number_year=? FOR UPDATE",
            Integer.class, serviceType, year);
        if (current == null) throw new IllegalStateException("编号池初始化失败");

        jdbc.update("""
            UPDATE erp_number_reservation
            SET status='released', released_at=CURRENT_TIMESTAMP
            WHERE service_type=? AND number_year=? AND status='reserved'
              AND expires_at<=CURRENT_TIMESTAMP
            """, serviceType, year);

        String pattern = "^" + prefix + "-" + year + "-([0-9]+)$";
        Integer usedMax = jdbc.queryForObject("""
            SELECT COALESCE(MAX(sequence_no),0) FROM (
              SELECT (regexp_match(contract_no, ?))[1]::INTEGER AS sequence_no
              FROM erp_contract WHERE service_type=? AND contract_no ~ ?
              UNION ALL
              SELECT (regexp_match(project_no, ?))[1]::INTEGER AS sequence_no
              FROM erp_project WHERE service_type=? AND project_no ~ ?
            ) used_numbers
            """, Integer.class, pattern, serviceType, pattern, pattern, serviceType, pattern);
        int highWater = Math.max(current, usedMax == null ? 0 : usedMax);
        jdbc.update("""
            UPDATE erp_number_pool SET last_number=GREATEST(last_number,?),updated_at=CURRENT_TIMESTAMP(3)
            WHERE service_type=? AND number_year=?
            """, highWater, serviceType, year);

        Integer firstFree = jdbc.queryForObject("""
            SELECT MIN(candidate.sequence_no)::INTEGER
            FROM generate_series(1, ?) AS candidate(sequence_no)
            WHERE NOT EXISTS (
              SELECT 1 FROM erp_number_reservation r
              WHERE r.service_type=? AND r.number_year=? AND r.sequence_no=candidate.sequence_no
                AND r.status IN ('reserved','consumed')
            )
            AND NOT EXISTS (
              SELECT 1 FROM erp_contract c
              WHERE c.contract_no = ? || '-' || CAST(? AS TEXT) || '-' || LPAD(candidate.sequence_no::TEXT,3,'0')
            )
            AND NOT EXISTS (
              SELECT 1 FROM erp_project p
              WHERE p.project_no = ? || '-' || CAST(? AS TEXT) || '-' || LPAD(candidate.sequence_no::TEXT,3,'0')
            )
            """, Integer.class, highWater, serviceType, year, prefix, year, prefix, year);

        int sequence = firstFree == null ? highWater + 1 : firstFree;
        jdbc.update("""
            UPDATE erp_number_pool SET last_number=GREATEST(last_number,?),updated_at=CURRENT_TIMESTAMP(3)
            WHERE service_type=? AND number_year=?
            """, sequence, serviceType, year);

        UUID id = UUID.randomUUID();
        String number = format(prefix, year, sequence);
        jdbc.update("""
            INSERT INTO erp_number_reservation(
              reservation_id,service_type,number_year,sequence_no,reservation_no,
              purpose,reserved_by,status,expires_at
            ) VALUES(?,?,?,?,?,?,?,'reserved',CURRENT_TIMESTAMP + INTERVAL '30 minutes')
            """, id, serviceType, year, sequence, number, purpose, actor);
        return new Reservation(id, number, serviceType, purpose, year, sequence);
    }

    static Reservation lock(JdbcTemplate jdbc, String rawId, String serviceType, String purpose, long actor) {
        UUID id = parseId(rawId);
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT reservation_id,reservation_no,service_type,number_year,sequence_no,purpose,
                   reserved_by,status,(expires_at>CURRENT_TIMESTAMP) AS not_expired
            FROM erp_number_reservation WHERE reservation_id=? FOR UPDATE
            """, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "编号预留不存在，请重新打开新建窗口");
        Map<String,Object> row = rows.get(0);
        if (((Number)row.get("reserved_by")).longValue() != actor)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能使用其他用户预留的编号");
        if (!Objects.equals(row.get("service_type"), serviceType) || !Objects.equals(row.get("purpose"), purpose))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "预留编号与当前业务不匹配，请重新打开新建窗口");
        if (!"reserved".equals(row.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该编号已被使用或释放，请重新打开新建窗口");
        if (!Boolean.TRUE.equals(row.get("not_expired")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "编号预留已过期，请重新打开新建窗口");
        return new Reservation(
            id,
            Objects.toString(row.get("reservation_no")),
            serviceType,
            purpose,
            ((Number)row.get("number_year")).intValue(),
            ((Number)row.get("sequence_no")).intValue()
        );
    }

    static void consume(JdbcTemplate jdbc, String rawId, long actor, Long contractId, Long projectId) {
        UUID id = parseId(rawId);
        int changed = jdbc.update("""
            UPDATE erp_number_reservation
            SET status='consumed',contract_id=?,project_id=?,consumed_at=CURRENT_TIMESTAMP
            WHERE reservation_id=? AND reserved_by=? AND status='reserved'
              AND expires_at>CURRENT_TIMESTAMP
            """, contractId, projectId, id, actor);
        if (changed != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "编号预留已失效，请重新打开新建窗口");
    }

    static boolean release(JdbcTemplate jdbc, String rawId, long actor) {
        UUID id = parseId(rawId);
        return jdbc.update("""
            UPDATE erp_number_reservation
            SET status='released',released_at=CURRENT_TIMESTAMP
            WHERE reservation_id=? AND reserved_by=? AND status='reserved'
            """, id, actor) == 1;
    }

    private static String prefix(String serviceType) {
        String prefix = PREFIXES.get(serviceType);
        if (prefix == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法为该服务类别分配编号");
        return prefix;
    }

    private static void requirePurpose(String serviceType, String purpose) {
        if (!List.of("contract", "project", "maintenance_renewal").contains(purpose))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "编号预留用途无效");
        if ("maintenance_renewal".equals(purpose) && !"maintenance".equals(serviceType))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有维保业务可以预留续签编号");
    }

    private static UUID parseId(String rawId) {
        try { return UUID.fromString(rawId); }
        catch (RuntimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "编号预留标识无效"); }
    }

    private static String format(String prefix, int year, int sequence) {
        return String.format(java.util.Locale.ROOT, "%s-%d-%03d", prefix, year, sequence);
    }
}
