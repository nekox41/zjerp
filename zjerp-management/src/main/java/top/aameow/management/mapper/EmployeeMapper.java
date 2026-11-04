package top.aameow.management.mapper;
import java.util.*;import org.apache.ibatis.annotations.*;
public interface EmployeeMapper {
 String C="id,name,employee_type employeeType,active,remark,version";
 @Select("<script>select "+C+" from zj_employee <where><if test='name!=null and name!=&quot;&quot;'>and (name like concat('%',#{name},'%') or employee_type like concat('%',#{name},'%'))</if><if test='active!=null and active!=&quot;&quot;'>and active=#{active}</if></where> order by active desc,id desc</script>") List<Map<String,Object>> list(Map<String,Object> q);
 @Select("select "+C+" from zj_employee where id=#{id}") Map<String,Object> get(Long id);
 @Select("select id,name,employee_type employeeType from zj_employee where active=1 and (name like concat('%',#{name},'%') or employee_type like concat('%',#{name},'%')) order by name,id limit 50") List<Map<String,Object>> options(String name);
 @Insert("insert into zj_employee(name,employee_type,active,remark,created_by) values(#{name},#{employeeType},#{active},#{remark},#{actor})") @Options(useGeneratedKeys=true,keyProperty="id") int add(Map<String,Object> v);
 @Update("update zj_employee set name=#{name},employee_type=#{employeeType},active=#{active},remark=#{remark},version=version+1,updated_at=now() where id=#{id} and version=#{version}") int edit(Map<String,Object> v);
}
