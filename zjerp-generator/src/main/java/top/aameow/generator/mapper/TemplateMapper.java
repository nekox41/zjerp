package top.aameow.generator.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import top.aameow.generator.domain.Template;

import java.util.List;

@Mapper
public interface TemplateMapper {
    public Template getTemplateByName(String name);
    public List<Template> getTemplateList(@Param("name") String name);
}
