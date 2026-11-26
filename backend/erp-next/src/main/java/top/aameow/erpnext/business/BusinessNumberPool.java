package top.aameow.erpnext.business;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

final class BusinessNumberPool {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private static final Map<String,String> PREFIXES=Map.of(
        "maintenance","ZJWB",
        "fire_inspection","ZJJC",
        "fire_assessment","ZJPD",
        "fire_evaluation","ZJPG",
        "other","ZJFW"
    );
    private BusinessNumberPool(){}
    static String allocate(JdbcTemplate jdbc,String serviceType){
        String prefix=PREFIXES.get(serviceType);
        if(prefix==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无法为该服务类别分配编号");
        int year=LocalDate.now(BUSINESS_ZONE).getYear();
        Integer sequence=jdbc.queryForObject("""
            INSERT INTO erp_number_pool(service_type,number_year,last_number)
            VALUES(?,?,1)
            ON CONFLICT(service_type,number_year)
            DO UPDATE SET last_number=erp_number_pool.last_number+1,updated_at=CURRENT_TIMESTAMP(3)
            RETURNING last_number
            """,Integer.class,serviceType,year);
        if(sequence==null)throw new IllegalStateException("编号池没有返回序号");
        return String.format(java.util.Locale.ROOT,"%s-%d-%03d",prefix,year,sequence);
    }
}