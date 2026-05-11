package top.aameow.generator.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import top.aameow.common.annotation.Anonymous;
import top.aameow.common.annotation.Log;
import top.aameow.common.config.RuoYiConfig;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.core.page.TableDataInfo;
import top.aameow.common.enums.BusinessType;
import top.aameow.common.utils.DateUtils;
import top.aameow.generator.domain.Template;
import top.aameow.generator.domain.WBCard;
import top.aameow.generator.service.TemplateService;
import top.aameow.generator.util.WBCardUtils;
import top.aameow.management.domain.zjWBCard;
import top.aameow.management.service.IzjWBCardService;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/wbcard/gen")
public class WBCardController extends BaseController {

    @Autowired
    private WBCardUtils wbCardUtils;

    @Autowired
    private IzjWBCardService zjWBCardService;

    @Autowired
    private TemplateService templateService;

    @Anonymous
    @Log(title = "WBCard", businessType = BusinessType.OTHER)
    @PostMapping("/create")
    public AjaxResult createWBCard(@RequestBody WBCard card) {
        String cardPath;
        String attachmentPath;
        try {
            cardPath = wbCardUtils.genWBCard(card);
            attachmentPath = wbCardUtils.genAttachment(card.getName(), card.getLeader());
        } catch (IOException e) {
            log.error("生成维保卡失败", e);
            return AjaxResult.error("生成维保卡失败");
        }
        zjWBCard card1 = new zjWBCard();
        card1.setCode(card.getNo());
        card1.setTitle(card.getName());
        if (cardPath != null && attachmentPath != null) {
            cardPath = cardPath.replace(RuoYiConfig.getProfile(), "/profile");
            attachmentPath =attachmentPath.replace(RuoYiConfig.getProfile(), "/profile");
        }
        card1.setFile(cardPath + "," + attachmentPath);
        card1.setPrintCheck("N");
        card1.setSendCheck("N");
        card1.setCreateTime(DateUtils.getNowDate());
        card1.setUpdateTime(DateUtils.getNowDate());
        card1.setCreateBy(getNickname());
        card1.setUpdateBy(getNickname());
        zjWBCardService.insertzjWBCard(card1);
        return AjaxResult.success("已创建维保卡。");
    }

    /**
     * 获取所有的维保卡模板
     */
    @Anonymous
    @GetMapping("/listtemplate")
    public TableDataInfo getTemplateList(String name) {
        startPage();
        List<Template> templateList = templateService.getTemplateList(name);
        return getDataTable(templateList);
    }
}
