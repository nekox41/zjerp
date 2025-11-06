package top.aameow.generator.mapper;

import org.apache.ibatis.annotations.Mapper;
import top.aameow.generator.domain.Template;

@Mapper
public interface TemplateMapper {
    public Template getTemplateByName(String name);
}
