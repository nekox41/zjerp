package top.aameow.generator.service;

import top.aameow.generator.domain.Template;

public interface ITemplateService {
    Template getTemplateByName(String name);
}
