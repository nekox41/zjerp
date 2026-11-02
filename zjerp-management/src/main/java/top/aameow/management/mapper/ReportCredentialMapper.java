package top.aameow.management.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
public interface ReportCredentialMapper {
 String COLS="id,kind,title,holder,certificate_no certificateNo,date_format(valid_until,'%Y-%m-%d') validUntil,files,version";
 @Select("select "+COLS+" from zj_report_credential order by kind,id") List<Map<String,Object>> list();
 @Insert("insert into zj_report_credential(kind,title,holder,certificate_no,valid_until,files,created_by) values(#{kind},#{title},#{holder},#{certificateNo},#{validUntil},#{files},#{actor})") @Options(useGeneratedKeys=true,keyProperty="id") int add(Map<String,Object> v);
 @Update("update zj_report_credential set kind=#{kind},title=#{title},holder=#{holder},certificate_no=#{certificateNo},valid_until=#{validUntil},files=#{files},version=version+1 where id=#{id} and version=#{version}") int edit(Map<String,Object> v);
}
