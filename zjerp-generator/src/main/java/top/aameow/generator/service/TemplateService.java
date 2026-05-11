package top.aameow.generator.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.aameow.generator.domain.Template;
import top.aameow.generator.mapper.TemplateMapper;

import java.util.List;

@Service
public class TemplateService {
    @Autowired
    private TemplateMapper templateMapper;

    public Template getTemplateByName(String name) {
        return templateMapper.getTemplateByName(name);
    }

    public List<Template> getTemplateList(String name) {
        return templateMapper.getTemplateList(name);
    }
}
