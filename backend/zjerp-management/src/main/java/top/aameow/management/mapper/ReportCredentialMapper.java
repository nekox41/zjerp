package top.aameow.management.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
public interface ReportCredentialMapper {
 String COLS="c.id,c.kind,c.title,coalesce(e.name,c.holder) holder,c.certificate_no certificateNo,date_format(c.valid_until,'%Y-%m-%d') validUntil,c.files,c.version,c.employee_id employeeId,c.value_type valueType,c.content";
 @Select("select "+COLS+" from zj_report_credential c left join zj_employee e on e.id=c.employee_id order by c.kind,c.id") List<Map<String,Object>> list();
 @Insert("insert into zj_report_credential(kind,title,holder,certificate_no,valid_until,files,employee_id,value_type,content,created_by) values(#{kind},#{title},#{holder},#{certificateNo},#{validUntil},#{files},#{employeeId},#{valueType},#{content},#{actor})") @Options(useGeneratedKeys=true,keyProperty="id") int add(Map<String,Object> v);
 @Update("update zj_report_credential set employee_id=#{employeeId},value_type=#{valueType},content=#{content},kind=#{kind},title=#{title},holder=#{holder},certificate_no=#{certificateNo},valid_until=#{validUntil},files=#{files},version=version+1 where id=#{id} and version=#{version}") int edit(Map<String,Object> v);
}
