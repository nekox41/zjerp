package top.aameow.system.mapper;
import java.util.List;
import org.apache.ibatis.annotations.*;
import top.aameow.system.domain.UserMaterial;
public interface UserMaterialMapper {
 @Select("select id,user_id as userId,material_key as materialKey,value_type as valueType,content from sys_user_material where user_id=#{userId} order by id")
 List<UserMaterial> list(Long userId);
 @Insert("insert into sys_user_material(user_id,material_key,value_type,content) values(#{userId},#{materialKey},#{valueType},#{content}) on duplicate key update value_type=values(value_type),content=values(content)")
 int save(UserMaterial value);
 @Delete("delete from sys_user_material where user_id=#{userId} and material_key=#{key}")
 int delete(@Param("userId") Long userId,@Param("key") String key);
}
