package top.aameow.management.mapper;
import java.util.List;
import org.apache.ibatis.annotations.*;
import top.aameow.management.domain.Intermediary;
public interface IntermediaryMapper {
 @Select("select * from zj_intermediary where name like concat('%',#{name},'%') order by id desc") List<Intermediary> list(String name);
 @Select("select * from zj_intermediary where id=#{id}") Intermediary get(Long id);
 @Insert("insert into zj_intermediary(name,phone,company,email,remark) values(#{name},#{phone},#{company},#{email},#{remark})") @Options(useGeneratedKeys=true,keyProperty="id") int add(Intermediary item);
 @Update("update zj_intermediary set name=#{name},phone=#{phone},company=#{company},email=#{email},remark=#{remark} where id=#{id}") int edit(Intermediary item);
 @Delete("delete from zj_intermediary where id=#{id}") int remove(Long id);
}
