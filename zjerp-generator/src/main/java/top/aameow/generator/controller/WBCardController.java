package top.aameow.generator.controller;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import top.aameow.common.core.controller.BaseController;
import top.aameow.common.core.domain.AjaxResult;
import top.aameow.common.config.RuoYiConfig;
import top.aameow.common.annotation.Log;
import top.aameow.common.enums.BusinessType;
import top.aameow.generator.domain.WBCard;
import top.aameow.generator.util.WBCardUtils;
import top.aameow.management.domain.zjWBCard;
import top.aameow.management.service.IzjWBCardService;

@RestController @RequestMapping("/wbcard/gen")
public class WBCardController extends BaseController {
    @Autowired private WBCardUtils renderer;
    @Autowired private IzjWBCardService cards;
    @PostMapping("/preview") @PreAuthorize("@ss.hasPermi('management:wbcard:add')")
    public ResponseEntity<byte[]> preview(@RequestBody WBCard card) throws IOException {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=maintenance-card.pdf").body(renderer.render(card));
    }
    @PostMapping("/create") @PreAuthorize("@ss.hasPermi('management:wbcard:add')")
    @Log(title="维保卡生成",businessType=BusinessType.INSERT)
    public AjaxResult create(@RequestBody WBCard card) throws IOException {
        Path path=Paths.get(renderer.genWBCard(card)).toAbsolutePath().normalize();
        Path base=Paths.get(RuoYiConfig.getProfile()).toAbsolutePath().normalize();
        String url="/profile/"+base.relativize(path).toString().replace('\\','/');
        zjWBCard record=new zjWBCard();record.setCode(card.getNo());record.setTitle(card.getName());record.setFile(url);
        record.setPrintCheck("N");record.setSendCheck("N");record.setCreateBy(getUsername());record.setUpdateBy(getUsername());
        try {cards.insertzjWBCard(record);} catch(RuntimeException ex){Files.deleteIfExists(path);throw ex;}
        String canonical=cards.selectzjWBCardById(record.getId()).getFile();
        // The unified file service has copied the PDF into its managed storage.
        Files.deleteIfExists(path);
        return success(Map.of("id",record.getId(),"file",canonical));
    }
}
