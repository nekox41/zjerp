package top.aameow.generator.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
@Mapper
public interface WbCardResourcesMapper {
 @Select("SELECT id,name,file,remark,created_at AS createdAt,updated_at AS updatedAt FROM zj_wbcard_material WHERE name LIKE CONCAT('%',#{name},'%') ORDER BY id DESC") List<Map<String,Object>> materials(String name);
 @Select("SELECT id,name,file,remark FROM zj_wbcard_material WHERE id=#{id}") Map<String,Object> material(Long id);
 @Insert("INSERT INTO zj_wbcard_material(name,file,remark) VALUES(#{name},#{file},#{remark})") @Options(useGeneratedKeys=true,keyProperty="id") int addMaterial(Map<String,Object> v);
 @Update("UPDATE zj_wbcard_material SET name=#{name},file=#{file},remark=#{remark},updated_at=NOW() WHERE id=#{id}") int editMaterial(Map<String,Object> v);
 @Delete("DELETE FROM zj_wbcard_material WHERE id=#{id}") int removeMaterial(Long id);
 @Select("SELECT id,name,phone FROM zjerp_wbcard_phone WHERE name LIKE CONCAT('%',#{name},'%') OR phone LIKE CONCAT('%',#{name},'%') ORDER BY id DESC") List<Map<String,Object>> contacts(String name);
 @Select("SELECT id,name,phone FROM zjerp_wbcard_phone WHERE id=#{id}") Map<String,Object> contact(Long id);
 @Insert("INSERT INTO zjerp_wbcard_phone(name,phone) VALUES(#{name},#{phone})") @Options(useGeneratedKeys=true,keyProperty="id") int addContact(Map<String,Object> v);
 @Update("UPDATE zjerp_wbcard_phone SET name=#{name},phone=#{phone} WHERE id=#{id}") int editContact(Map<String,Object> v);
 @Delete("DELETE FROM zjerp_wbcard_phone WHERE id=#{id}") int removeContact(Long id);
}
