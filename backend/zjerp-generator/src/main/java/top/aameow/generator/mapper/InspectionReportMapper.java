package top.aameow.generator.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
public interface InspectionReportMapper {
 @Select("select id,report_no reportNo,file_url file,created_by createdBy,created_at createdAt from zj_inspection_report where project_id=#{id} order by id desc limit 100") List<Map<String,Object>> list(Long id);
 @Insert("insert into zj_inspection_report(project_id,report_no,snapshot_json,created_by) values(#{projectId},#{reportNo},#{snapshot},#{actor})") @Options(useGeneratedKeys=true,keyProperty="id") int add(Map<String,Object> v);
 @Update("update zj_inspection_report set file_url=#{file} where id=#{id}") int file(Map<String,Object> v);
}
