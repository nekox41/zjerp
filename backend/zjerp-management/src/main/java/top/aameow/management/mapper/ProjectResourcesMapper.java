package top.aameow.management.mapper;
import java.util.*;import org.apache.ibatis.annotations.*;
public interface ProjectResourcesMapper {
 @Select("select id,project_id projectId,name,kind,files,legacy_report_id legacyReportId,legacy_snapshot legacySnapshot,created_at createdAt from zj_project_document where project_id=#{id} order by id desc") List<Map<String,Object>> documents(Long id);
 @Insert("insert into zj_project_document(project_id,name,kind,files,created_by) values(#{projectId},#{name},#{kind},#{files},#{actor})") @Options(useGeneratedKeys=true,keyProperty="id") int add(Map<String,Object> v);
 @Update("update zj_project_document set name=#{name},kind=#{kind},files=#{files},updated_at=NOW() where id=#{id} and project_id=#{projectId}") int edit(Map<String,Object> v);
 @Delete("delete from zj_project_document where id=#{id} and project_id=#{project}") int remove(@Param("project") Long p,@Param("id") Long id);
 @Select("select p.project_id projectId,p.material_id materialId,m.name materialName,m.file materialFile,p.card_id cardId,c.code cardCode,c.title cardName,coalesce((select group_concat(f.file_url order by f.file_id) from sys_file f where f.biz_type='wbcard' and f.biz_id=c.id and f.del_flag='0'),c.file) cardFile,p.version from zj_project_material p left join zj_wbcard_material m on m.id=p.material_id left join zj_wbcard c on c.id=p.card_id where p.project_id=#{id}") Map<String,Object> materials(Long id);
 @Insert("insert ignore into zj_project_material(project_id) values(#{id})") int ensureMaterial(Long id);
 @Update("update zj_project_material set material_id=#{materialId},card_id=#{cardId},version=version+1 where project_id=#{projectId} and version=#{version}") int material(Map<String,Object> v);
 @Select("select id,name,file from zj_wbcard_material where name like concat('%',#{name},'%') order by id desc limit 20") List<Map<String,Object>> materialOptions(String name);
 @Select("select id,title name,code from zj_wbcard where title like concat('%',#{name},'%') or code like concat('%',#{name},'%') order by id desc limit 20") List<Map<String,Object>> cardOptions(String name);
 @Select("select id from zj_wbcard where id=#{id}") Long card(Long id);
 @Select("select id from zj_wbcard_material where id=#{id}") Long attachment(Long id);
 @Insert("insert ignore into zj_wbcard_sequence(sequence_key,sequence_value) values(1,0)") int ensureSequence();
 @Select("select sequence_value from zj_wbcard_sequence where sequence_key=1 for update") Long lockSequence();
 @Select("select greatest((select coalesce(max(cast(code as unsigned)),0) from zj_wbcard where code regexp '^[0-9]{1,18}$'),(select coalesce(max(cast(code as unsigned)),0) from zj_wbcard_number where code regexp '^[0-9]{1,18}$'))") Long maxCode();
 @Update("update zj_wbcard_sequence set sequence_value=#{n} where sequence_key=1") int sequence(Long n);
 @Select("select (select count(*) from zj_wbcard where code=#{code})+(select count(*) from zj_wbcard_number where code=#{code})") int usedCode(String code);
 @Insert("insert into zj_wbcard_number(code,card_id) values(#{code},#{id})") int reserve(@Param("code") String code,@Param("id") Long id);
}
